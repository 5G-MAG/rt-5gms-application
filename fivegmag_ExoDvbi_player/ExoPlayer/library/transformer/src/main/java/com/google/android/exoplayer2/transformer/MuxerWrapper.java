/*
 * Copyright 2020 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.os.ParcelFileDescriptor;
import android.util.SparseArray;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * A wrapper around a media muxer.
 *
 * <p>This wrapper can contain at most one video track and one audio track.
 */
/* package */ final class MuxerWrapper {

  public interface Listener {
    void onTrackEnded(
        @C.TrackType int trackType, Format format, int averageBitrate, int sampleCount);

    void onEnded(long durationMs, long fileSizeBytes);

    void onTransformationError(TransformationException transformationException);
  }

  /**
   * The maximum difference between the track positions, in microseconds.
   *
   * <p>The value of this constant has been chosen based on the interleaving observed in a few media
   * files, where continuous chunks of the same track were about 0.5 seconds long.
   */
  private static final long MAX_TRACK_WRITE_AHEAD_US = Util.msToUs(500);

  @Nullable private final String outputPath;
  @Nullable private final ParcelFileDescriptor outputParcelFileDescriptor;
  private final Muxer.Factory muxerFactory;
  private final Listener listener;
  private final SparseArray<TrackInfo> trackTypeToInfo;
  private final ScheduledExecutorService abortScheduledExecutorService;

  private int trackCount;
  private boolean isReady;
  private boolean isEnded;
  private @C.TrackType int previousTrackType;
  private long minTrackTimeUs;
  private long maxEndedTrackTimeUs;
  private @MonotonicNonNull ScheduledFuture<?> abortScheduledFuture;
  private boolean isAborted;
  private @MonotonicNonNull Muxer muxer;

  public MuxerWrapper(
      @Nullable String outputPath,
      @Nullable ParcelFileDescriptor outputParcelFileDescriptor,
      Muxer.Factory muxerFactory,
      Listener listener) {
    if (outputPath == null && outputParcelFileDescriptor == null) {
      throw new NullPointerException("Both output path and ParcelFileDescriptor are null");
    }

    this.outputPath = outputPath;
    this.outputParcelFileDescriptor = outputParcelFileDescriptor;
    this.muxerFactory = muxerFactory;
    this.listener = listener;

    trackTypeToInfo = new SparseArray<>();
    previousTrackType = C.TRACK_TYPE_NONE;
    abortScheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
  }

  /**
   * Sets the number of output tracks.
   *
   * <p>The track count must be set before any track format is {@linkplain #addTrackFormat(Format)
   * added}.
   *
   * @throws IllegalStateException If a track format was {@linkplain #addTrackFormat(Format) added}
   *     before calling this method.
   */
  public void setTrackCount(@IntRange(from = 1) int trackCount) {
    checkState(
        trackTypeToInfo.size() == 0,
        "The track count cannot be changed after adding track formats.");
    this.trackCount = trackCount;
  }

  /** Returns whether the sample {@linkplain MimeTypes MIME type} is supported. */
  public boolean supportsSampleMimeType(@Nullable String mimeType) {
    @C.TrackType int trackType = MimeTypes.getTrackType(mimeType);
    return getSupportedSampleMimeTypes(trackType).contains(mimeType);
  }

  /**
   * Returns the supported {@linkplain MimeTypes MIME types} for the given {@linkplain C.TrackType
   * track type}.
   */
  public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
    return muxerFactory.getSupportedSampleMimeTypes(trackType);
  }

  /**
   * Adds a track format to the muxer.
   *
   * <p>The number of tracks must be {@linkplain #setTrackCount(int) set} before any format is added
   * and all the formats must be added before any samples can be {@linkplain #writeSample(int,
   * ByteBuffer, boolean, long) written}.
   *
   * @param format The {@link Format} to be added.
   * @throws IllegalArgumentException If the format is unsupported.
   * @throws IllegalStateException If the number of formats added exceeds the {@linkplain
   *     #setTrackCount track count}, if {@link #setTrackCount(int)} has not been called or if there
   *     is already a track of that {@link C.TrackType}.
   * @throws Muxer.MuxerException If the underlying {@link Muxer} encounters a problem while adding
   *     the track.
   */
  public void addTrackFormat(Format format) throws Muxer.MuxerException {
    checkState(trackCount > 0, "The track count should be set before the formats are added.");
    checkState(trackTypeToInfo.size() < trackCount, "All track formats have already been added.");
    @Nullable String sampleMimeType = format.sampleMimeType;
    @C.TrackType int trackType = MimeTypes.getTrackType(sampleMimeType);
    checkArgument(
        trackType == C.TRACK_TYPE_AUDIO || trackType == C.TRACK_TYPE_VIDEO,
        "Unsupported track format: " + sampleMimeType);

    // SparseArray.get() returns null by default if the value is not found.
    checkState(
        trackTypeToInfo.get(trackType) == null, "There is already a track of type " + trackType);

    ensureMuxerInitialized();

    TrackInfo trackInfo = new TrackInfo(format, muxer.addTrack(format));
    trackTypeToInfo.put(trackType, trackInfo);
    if (trackTypeToInfo.size() == trackCount) {
      isReady = true;
      resetAbortTimer();
    }
  }

  /**
   * Attempts to write a sample to the muxer.
   *
   * @param trackType The {@link C.TrackType} of the sample.
   * @param data The sample to write.
   * @param isKeyFrame Whether the sample is a key frame.
   * @param presentationTimeUs The presentation time of the sample in microseconds.
   * @return Whether the sample was successfully written. {@code false} if samples of other
   *     {@linkplain C.TrackType track types} should be written first to ensure the files track
   *     interleaving is balanced, or if the muxer hasn't {@linkplain #addTrackFormat(Format)
   *     received a format} for every {@linkplain #setTrackCount(int) track}.
   * @throws IllegalArgumentException If the muxer doesn't have a {@linkplain #endTrack(int)
   *     non-ended} track of the given {@link C.TrackType}.
   * @throws Muxer.MuxerException If the underlying {@link Muxer} fails to write the sample.
   */
  public boolean writeSample(
      @C.TrackType int trackType, ByteBuffer data, boolean isKeyFrame, long presentationTimeUs)
      throws Muxer.MuxerException {
    @Nullable TrackInfo trackInfo = trackTypeToInfo.get(trackType);
    // SparseArray.get() returns null by default if the value is not found.
    checkArgument(
        trackInfo != null, "Could not write sample because there is no track of type " + trackType);

    if (!canWriteSample(trackType, presentationTimeUs)) {
      return false;
    }

    trackInfo.sampleCount++;
    trackInfo.bytesWritten += data.remaining();
    trackInfo.timeUs = max(trackInfo.timeUs, presentationTimeUs);

    checkNotNull(muxer);
    resetAbortTimer();
    muxer.writeSampleData(
        trackInfo.index, data, presentationTimeUs, isKeyFrame ? C.BUFFER_FLAG_KEY_FRAME : 0);
    previousTrackType = trackType;
    return true;
  }

  /**
   * Notifies the muxer that all the samples have been {@linkplain #writeSample(int, ByteBuffer,
   * boolean, long) written} for a given track.
   *
   * @param trackType The {@link C.TrackType}.
   */
  public void endTrack(@C.TrackType int trackType) {
    @Nullable TrackInfo trackInfo = trackTypeToInfo.get(trackType);
    if (trackInfo == null) {
      // SparseArray.get() returns null by default if the value is not found.
      return;
    }

    maxEndedTrackTimeUs = max(maxEndedTrackTimeUs, trackInfo.timeUs);
    listener.onTrackEnded(
        trackType, trackInfo.format, trackInfo.getAverageBitrate(), trackInfo.sampleCount);

    trackTypeToInfo.delete(trackType);
    if (trackTypeToInfo.size() == 0) {
      abortScheduledExecutorService.shutdownNow();
      if (!isEnded) {
        isEnded = true;
        listener.onEnded(Util.usToMs(maxEndedTrackTimeUs), getCurrentOutputSizeBytes());
      }
    }
  }

  /** Returns whether all the tracks are {@linkplain #endTrack(int) ended}. */
  public boolean isEnded() {
    return isEnded;
  }

  /**
   * Finishes writing the output and releases any resources associated with muxing.
   *
   * <p>The muxer cannot be used anymore once this method has been called.
   *
   * @param forCancellation Whether the reason for releasing the resources is the transformation
   *     cancellation.
   * @throws Muxer.MuxerException If the underlying {@link Muxer} fails to finish writing the output
   *     and {@code forCancellation} is false.
   */
  public void release(boolean forCancellation) throws Muxer.MuxerException {
    isReady = false;
    abortScheduledExecutorService.shutdownNow();
    if (muxer != null) {
      muxer.release(forCancellation);
    }
  }

  private boolean canWriteSample(@C.TrackType int trackType, long presentationTimeUs) {
    if (!isReady) {
      return false;
    }
    if (trackTypeToInfo.size() == 1) {
      return true;
    }
    if (trackType != previousTrackType) {
      minTrackTimeUs = getMinTrackTimeUs(trackTypeToInfo);
    }
    return presentationTimeUs - minTrackTimeUs <= MAX_TRACK_WRITE_AHEAD_US;
  }

  @RequiresNonNull("muxer")
  private void resetAbortTimer() {
    long maxDelayBetweenSamplesMs = muxer.getMaxDelayBetweenSamplesMs();
    if (maxDelayBetweenSamplesMs == C.TIME_UNSET) {
      return;
    }
    if (abortScheduledFuture != null) {
      abortScheduledFuture.cancel(/* mayInterruptIfRunning= */ false);
    }
    abortScheduledFuture =
        abortScheduledExecutorService.schedule(
            () -> {
              if (isAborted) {
                return;
              }
              isAborted = true;
              listener.onTransformationError(
                  TransformationException.createForMuxer(
                      new IllegalStateException(
                          "No output sample written in the last "
                              + maxDelayBetweenSamplesMs
                              + " milliseconds. Aborting transformation."),
                      TransformationException.ERROR_CODE_MUXING_FAILED));
            },
            maxDelayBetweenSamplesMs,
            MILLISECONDS);
  }

  @EnsuresNonNull("muxer")
  private void ensureMuxerInitialized() throws Muxer.MuxerException {
    if (muxer == null) {
      if (outputPath != null) {
        muxer = muxerFactory.create(outputPath);
      } else {
        checkNotNull(outputParcelFileDescriptor);
        muxer = muxerFactory.create(outputParcelFileDescriptor);
      }
    }
  }

  /** Returns the current size in bytes of the output, or {@link C#LENGTH_UNSET} if unavailable. */
  private long getCurrentOutputSizeBytes() {
    long fileSize = C.LENGTH_UNSET;

    if (outputPath != null) {
      fileSize = new File(outputPath).length();
    } else if (outputParcelFileDescriptor != null) {
      fileSize = outputParcelFileDescriptor.getStatSize();
    }

    return fileSize > 0 ? fileSize : C.LENGTH_UNSET;
  }

  private static long getMinTrackTimeUs(SparseArray<TrackInfo> trackTypeToInfo) {
    if (trackTypeToInfo.size() == 0) {
      return C.TIME_UNSET;
    }

    long minTrackTimeUs = Long.MAX_VALUE;
    for (int i = 0; i < trackTypeToInfo.size(); i++) {
      minTrackTimeUs = min(minTrackTimeUs, trackTypeToInfo.valueAt(i).timeUs);
    }
    return minTrackTimeUs;
  }

  private static final class TrackInfo {
    public final Format format;
    public final int index;

    public long bytesWritten;
    public int sampleCount;
    public long timeUs;

    public TrackInfo(Format format, int index) {
      this.format = format;
      this.index = index;
    }

    /**
     * Returns the average bitrate of data written to the track, or {@link C#RATE_UNSET_INT} if
     * there is no track data.
     */
    public int getAverageBitrate() {
      if (timeUs <= 0 || bytesWritten <= 0) {
        return C.RATE_UNSET_INT;
      }

      // The number of bytes written is not a timestamp, however this utility method provides
      // overflow-safe multiplication & division.
      return (int)
          Util.scaleLargeTimestamp(
              /* timestamp= */ bytesWritten,
              /* multiplier= */ C.BITS_PER_BYTE * C.MICROS_PER_SECOND,
              /* divisor= */ timeUs);
    }
  }
}
