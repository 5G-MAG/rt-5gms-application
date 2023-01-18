/*
 * Copyright 2021 The Android Open Source Project
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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.decoder.DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Pipeline that muxes encoded samples without any transcoding or transformation. */
/* package */ final class EncodedSamplePipeline extends SamplePipeline {

  private static final int MAX_INPUT_BUFFER_COUNT = 10;

  private final Format format;
  private final Queue<DecoderInputBuffer> availableInputBuffers;
  private final Queue<DecoderInputBuffer> pendingInputBuffers;

  private volatile boolean inputEnded;

  public EncodedSamplePipeline(
      Format format,
      long streamStartPositionUs,
      TransformationRequest transformationRequest,
      MuxerWrapper muxerWrapper,
      FallbackListener fallbackListener) {
    super(format, streamStartPositionUs, muxerWrapper);
    this.format = format;
    availableInputBuffers = new ConcurrentLinkedDeque<>();
    ByteBuffer emptyBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
    for (int i = 0; i < MAX_INPUT_BUFFER_COUNT; i++) {
      DecoderInputBuffer inputBuffer = new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DIRECT);
      inputBuffer.data = emptyBuffer;
      availableInputBuffers.add(inputBuffer);
    }
    pendingInputBuffers = new ConcurrentLinkedDeque<>();
    fallbackListener.onTransformationRequestFinalized(transformationRequest);
  }

  @Override
  public boolean expectsDecodedData() {
    return false;
  }

  @Override
  @Nullable
  public DecoderInputBuffer getInputBuffer() {
    return availableInputBuffers.peek();
  }

  @Override
  public void queueInputBuffer() {
    DecoderInputBuffer inputBuffer = availableInputBuffers.remove();
    if (inputBuffer.isEndOfStream()) {
      inputEnded = true;
    } else {
      pendingInputBuffers.add(inputBuffer);
    }
  }

  @Override
  public void release() {}

  @Override
  protected Format getMuxerInputFormat() {
    return format;
  }

  @Override
  @Nullable
  protected DecoderInputBuffer getMuxerInputBuffer() {
    return pendingInputBuffers.peek();
  }

  @Override
  protected void releaseMuxerInputBuffer() {
    DecoderInputBuffer inputBuffer = pendingInputBuffers.remove();
    inputBuffer.clear();
    inputBuffer.timeUs = 0;
    availableInputBuffers.add(inputBuffer);
  }

  @Override
  protected boolean isMuxerInputEnded() {
    return inputEnded && pendingInputBuffers.isEmpty();
  }
}
