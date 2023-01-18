/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.transformerdemo;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.opengl.EGL14;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.effect.GlTextureProcessor;
import com.google.android.exoplayer2.effect.TextureInfo;
import com.google.android.exoplayer2.util.FrameProcessingException;
import com.google.android.exoplayer2.util.LibraryLoader;
import com.google.android.exoplayer2.util.Util;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.framework.AppTextureFrame;
import com.google.mediapipe.framework.TextureFrame;
import com.google.mediapipe.glutil.EglManager;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** Runs a MediaPipe graph on input frames. */
/* package */ final class MediaPipeProcessor implements GlTextureProcessor {

  private static final String THREAD_NAME = "Demo:MediaPipeProcessor";
  private static final long RELEASE_WAIT_TIME_MS = 100;
  private static final long RETRY_WAIT_TIME_MS = 1;

  private static final LibraryLoader LOADER =
      new LibraryLoader("mediapipe_jni") {
        @Override
        protected void loadLibrary(String name) {
          System.loadLibrary(name);
        }
      };

  static {
    // Not all build configurations require OpenCV to be loaded separately, so attempt to load the
    // library but ignore the error if it's not present.
    try {
      System.loadLibrary("opencv_java3");
    } catch (UnsatisfiedLinkError e) {
      // Do nothing.
    }
  }

  private final FrameProcessor frameProcessor;
  private final ConcurrentHashMap<TextureInfo, TextureFrame> outputFrames;
  private final boolean isSingleFrameGraph;
  @Nullable private final ExecutorService singleThreadExecutorService;
  private final Queue<Future<?>> futures;

  private InputListener inputListener;
  private OutputListener outputListener;
  private ErrorListener errorListener;
  private Executor errorListenerExecutor;
  private boolean acceptedFrame;

  /**
   * Creates a new texture processor that wraps a MediaPipe graph.
   *
   * <p>If {@code isSingleFrameGraph} is {@code false}, the {@code MediaPipeProcessor} may waste CPU
   * time by continuously attempting to queue input frames to MediaPipe until they are accepted or
   * waste memory if MediaPipe accepts and stores many frames internally.
   *
   * @param context The {@link Context}.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @param graphName Name of a MediaPipe graph asset to load.
   * @param isSingleFrameGraph Whether the MediaPipe graph will eventually produce one output frame
   *     each time an input frame (and no other input) has been queued.
   * @param inputStreamName Name of the input video stream in the graph.
   * @param outputStreamName Name of the input video stream in the graph.
   */
  public MediaPipeProcessor(
      Context context,
      boolean useHdr,
      String graphName,
      boolean isSingleFrameGraph,
      String inputStreamName,
      String outputStreamName) {
    checkState(LOADER.isAvailable());
    // TODO(b/227624622): Confirm whether MediaPipeProcessor could support HDR colors.
    checkArgument(!useHdr, "MediaPipeProcessor does not support HDR colors.");

    this.isSingleFrameGraph = isSingleFrameGraph;
    singleThreadExecutorService =
        isSingleFrameGraph ? null : Util.newSingleThreadExecutor(THREAD_NAME);
    futures = new ArrayDeque<>();
    inputListener = new InputListener() {};
    outputListener = new OutputListener() {};
    errorListener = (frameProcessingException) -> {};
    errorListenerExecutor = MoreExecutors.directExecutor();
    EglManager eglManager = new EglManager(EGL14.eglGetCurrentContext());
    frameProcessor =
        new FrameProcessor(
            context, eglManager.getNativeContext(), graphName, inputStreamName, outputStreamName);
    outputFrames = new ConcurrentHashMap<>();
    // OnWillAddFrameListener is called on the same thread as frameProcessor.onNewFrame(...), so no
    // synchronization is needed for acceptedFrame.
    frameProcessor.setOnWillAddFrameListener((long timestamp) -> acceptedFrame = true);
  }

  @Override
  public void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
    if (!isSingleFrameGraph || outputFrames.isEmpty()) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  public void setOutputListener(OutputListener outputListener) {
    this.outputListener = outputListener;
    frameProcessor.setConsumer(
        frame -> {
          TextureInfo texture =
              new TextureInfo(
                  frame.getTextureName(),
                  /* fboId= */ C.INDEX_UNSET,
                  frame.getWidth(),
                  frame.getHeight());
          outputFrames.put(texture, frame);
          outputListener.onOutputFrameAvailable(texture, frame.getTimestamp());
        });
  }

  @Override
  public void setErrorListener(Executor executor, ErrorListener errorListener) {
    this.errorListenerExecutor = executor;
    this.errorListener = errorListener;
    frameProcessor.setAsynchronousErrorListener(
        error ->
            errorListenerExecutor.execute(
                () -> errorListener.onFrameProcessingError(new FrameProcessingException(error))));
  }

  @Override
  public void queueInputFrame(TextureInfo inputTexture, long presentationTimeUs) {
    AppTextureFrame appTextureFrame =
        new AppTextureFrame(inputTexture.texId, inputTexture.width, inputTexture.height);
    // TODO(b/238302213): Handle timestamps restarting from 0 when applying effects to a playlist.
    //  MediaPipe will fail if the timestamps are not monotonically increasing.
    //  Also make sure that a MediaPipe graph producing additional frames only starts producing
    //  frames for the next MediaItem after receiving the first frame of that MediaItem as input
    //  to avoid MediaPipe producing extra frames after the last MediaItem has ended.
    appTextureFrame.setTimestamp(presentationTimeUs);
    if (isSingleFrameGraph) {
      boolean acceptedFrame = maybeQueueInputFrameSynchronous(appTextureFrame, inputTexture);
      checkState(
          acceptedFrame,
          "queueInputFrame must only be called when a new input frame can be accepted");
      return;
    }

    // TODO(b/241782273): Avoid retrying continuously until the frame is accepted by using a
    //  currently non-existent MediaPipe API to be notified when MediaPipe has capacity to accept a
    //  new frame.
    queueInputFrameAsynchronous(appTextureFrame, inputTexture);
  }

  private boolean maybeQueueInputFrameSynchronous(
      AppTextureFrame appTextureFrame, TextureInfo inputTexture) {
    acceptedFrame = false;
    frameProcessor.onNewFrame(appTextureFrame);
    try {
      appTextureFrame.waitUntilReleasedWithGpuSync();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      errorListenerExecutor.execute(
          () -> errorListener.onFrameProcessingError(new FrameProcessingException(e)));
    }
    if (acceptedFrame) {
      inputListener.onInputFrameProcessed(inputTexture);
    }
    return acceptedFrame;
  }

  private void queueInputFrameAsynchronous(
      AppTextureFrame appTextureFrame, TextureInfo inputTexture) {
    removeFinishedFutures();
    futures.add(
        checkStateNotNull(singleThreadExecutorService)
            .submit(
                () -> {
                  while (!maybeQueueInputFrameSynchronous(appTextureFrame, inputTexture)) {
                    try {
                      Thread.sleep(RETRY_WAIT_TIME_MS);
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                      if (errorListener != null) {
                        errorListenerExecutor.execute(
                            () ->
                                errorListener.onFrameProcessingError(
                                    new FrameProcessingException(e)));
                      }
                    }
                  }
                  inputListener.onReadyToAcceptInputFrame();
                }));
  }

  @Override
  public void releaseOutputFrame(TextureInfo outputTexture) {
    checkStateNotNull(outputFrames.get(outputTexture)).release();
    if (isSingleFrameGraph) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  public void release() {
    if (isSingleFrameGraph) {
      frameProcessor.close();
      return;
    }

    Queue<Future<?>> futures = checkStateNotNull(this.futures);
    while (!futures.isEmpty()) {
      futures.remove().cancel(/* mayInterruptIfRunning= */ false);
    }
    ExecutorService singleThreadExecutorService =
        checkStateNotNull(this.singleThreadExecutorService);
    singleThreadExecutorService.shutdown();
    try {
      if (!singleThreadExecutorService.awaitTermination(RELEASE_WAIT_TIME_MS, MILLISECONDS)) {
        errorListenerExecutor.execute(
            () ->
                errorListener.onFrameProcessingError(
                    new FrameProcessingException("Release timed out")));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      errorListenerExecutor.execute(
          () -> errorListener.onFrameProcessingError(new FrameProcessingException(e)));
    }

    frameProcessor.close();
  }

  @Override
  public final void signalEndOfCurrentInputStream() {
    if (isSingleFrameGraph) {
      frameProcessor.waitUntilIdle();
      outputListener.onCurrentOutputStreamEnded();
      return;
    }

    removeFinishedFutures();
    futures.add(
        checkStateNotNull(singleThreadExecutorService)
            .submit(
                () -> {
                  frameProcessor.waitUntilIdle();
                  outputListener.onCurrentOutputStreamEnded();
                }));
  }

  private void removeFinishedFutures() {
    while (!futures.isEmpty()) {
      if (!futures.element().isDone()) {
        return;
      }
      try {
        futures.remove().get();
      } catch (ExecutionException e) {
        errorListenerExecutor.execute(
            () -> errorListener.onFrameProcessingError(new FrameProcessingException(e)));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        errorListenerExecutor.execute(
            () -> errorListener.onFrameProcessingError(new FrameProcessingException(e)));
      }
    }
  }
}
