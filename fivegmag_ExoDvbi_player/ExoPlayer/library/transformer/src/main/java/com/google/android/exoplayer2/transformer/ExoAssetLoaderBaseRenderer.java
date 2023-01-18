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

import static com.google.android.exoplayer2.decoder.DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED;
import static com.google.android.exoplayer2.source.SampleStream.FLAG_REQUIRE_FORMAT;
import static com.google.android.exoplayer2.transformer.AssetLoader.SUPPORTED_OUTPUT_TYPE_DECODED;
import static com.google.android.exoplayer2.transformer.AssetLoader.SUPPORTED_OUTPUT_TYPE_ENCODED;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream.ReadDataResult;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.MimeTypes;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/* package */ abstract class ExoAssetLoaderBaseRenderer extends BaseRenderer {

  protected long streamOffsetUs;
  protected @MonotonicNonNull SampleConsumer sampleConsumer;
  protected @MonotonicNonNull Codec decoder;
  protected boolean isEnded;

  private final TransformerMediaClock mediaClock;
  private final AssetLoader.Listener assetLoaderListener;
  private final DecoderInputBuffer decoderInputBuffer;

  private boolean isTransformationRunning;
  private long streamStartPositionUs;

  public ExoAssetLoaderBaseRenderer(
      @C.TrackType int trackType,
      TransformerMediaClock mediaClock,
      AssetLoader.Listener assetLoaderListener) {
    super(trackType);
    this.mediaClock = mediaClock;
    this.assetLoaderListener = assetLoaderListener;
    decoderInputBuffer = new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DISABLED);
  }

  /**
   * Returns whether the renderer supports the track type of the given format.
   *
   * @param format The format.
   * @return The {@link Capabilities} for this format.
   */
  @Override
  public @Capabilities int supportsFormat(Format format) {
    return RendererCapabilities.create(
        MimeTypes.getTrackType(format.sampleMimeType) == getTrackType()
            ? C.FORMAT_HANDLED
            : C.FORMAT_UNSUPPORTED_TYPE);
  }

  @Override
  public MediaClock getMediaClock() {
    return mediaClock;
  }

  @Override
  public boolean isReady() {
    return isSourceReady();
  }

  @Override
  public boolean isEnded() {
    return isEnded;
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) {
    try {
      if (!isTransformationRunning || isEnded() || !ensureConfigured()) {
        return;
      }

      if (sampleConsumer.expectsDecodedData()) {
        while (feedConsumerFromDecoder() || feedDecoderFromInput()) {}
      } else {
        while (feedConsumerFromInput()) {}
      }
    } catch (TransformationException e) {
      isTransformationRunning = false;
      assetLoaderListener.onTransformationError(e);
    }
  }

  @Override
  protected void onStreamChanged(Format[] formats, long startPositionUs, long offsetUs) {
    this.streamStartPositionUs = startPositionUs;
    this.streamOffsetUs = offsetUs;
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream) {
    mediaClock.updateTimeForTrackType(getTrackType(), 0L);
  }

  @Override
  protected void onStarted() {
    isTransformationRunning = true;
  }

  @Override
  protected void onStopped() {
    isTransformationRunning = false;
  }

  @Override
  protected void onReset() {
    if (decoder != null) {
      decoder.release();
    }
  }

  /** Called when the {@link Format} of the samples fed to the renderer is known. */
  protected void onInputFormatRead(Format inputFormat) {}

  /** Initializes {@link #decoder} with an appropriate {@linkplain Codec decoder}. */
  @RequiresNonNull("sampleConsumer")
  protected abstract void initDecoder(Format inputFormat) throws TransformationException;

  /**
   * Preprocesses an encoded {@linkplain DecoderInputBuffer input buffer} and returns whether it
   * should be dropped.
   *
   * <p>The input buffer is cleared if it should be dropped.
   */
  protected boolean shouldDropInputBuffer(DecoderInputBuffer inputBuffer) {
    return false;
  }

  /** Called before a {@link DecoderInputBuffer} is queued to the decoder. */
  protected void onDecoderInputReady(DecoderInputBuffer inputBuffer) {}

  /**
   * Attempts to get decoded data and pass it to the sample consumer.
   *
   * @return Whether it may be possible to read more data immediately by calling this method again.
   * @throws TransformationException If an error occurs in the decoder.
   */
  @RequiresNonNull("sampleConsumer")
  protected abstract boolean feedConsumerFromDecoder() throws TransformationException;

  @EnsuresNonNullIf(expression = "sampleConsumer", result = true)
  private boolean ensureConfigured() throws TransformationException {
    if (sampleConsumer != null) {
      return true;
    }

    FormatHolder formatHolder = getFormatHolder();
    @ReadDataResult
    int result = readSource(formatHolder, decoderInputBuffer, /* readFlags= */ FLAG_REQUIRE_FORMAT);
    if (result != C.RESULT_FORMAT_READ) {
      return false;
    }
    Format inputFormat = checkNotNull(formatHolder.format);
    @AssetLoader.SupportedOutputTypes
    int supportedOutputTypes = SUPPORTED_OUTPUT_TYPE_ENCODED | SUPPORTED_OUTPUT_TYPE_DECODED;
    sampleConsumer =
        assetLoaderListener.onTrackAdded(
            inputFormat, supportedOutputTypes, streamStartPositionUs, streamOffsetUs);
    onInputFormatRead(inputFormat);
    if (sampleConsumer.expectsDecodedData()) {
      initDecoder(inputFormat);
    }
    return true;
  }

  /**
   * Attempts to read input data and pass it to the decoder.
   *
   * @return Whether it may be possible to read more data immediately by calling this method again.
   * @throws TransformationException If an error occurs in the decoder.
   */
  private boolean feedDecoderFromInput() throws TransformationException {
    Codec decoder = checkNotNull(this.decoder);
    if (!decoder.maybeDequeueInputBuffer(decoderInputBuffer)) {
      return false;
    }

    if (!readInput(decoderInputBuffer)) {
      return false;
    }

    if (shouldDropInputBuffer(decoderInputBuffer)) {
      return true;
    }

    onDecoderInputReady(decoderInputBuffer);
    decoder.queueInputBuffer(decoderInputBuffer);
    return true;
  }

  /**
   * Attempts to read input data and pass it to the sample consumer.
   *
   * @return Whether it may be possible to read more data immediately by calling this method again.
   */
  @RequiresNonNull("sampleConsumer")
  private boolean feedConsumerFromInput() {
    @Nullable DecoderInputBuffer sampleConsumerInputBuffer = sampleConsumer.getInputBuffer();
    if (sampleConsumerInputBuffer == null) {
      return false;
    }

    if (!readInput(sampleConsumerInputBuffer)) {
      return false;
    }

    if (shouldDropInputBuffer(sampleConsumerInputBuffer)) {
      return true;
    }

    isEnded = sampleConsumerInputBuffer.isEndOfStream();
    sampleConsumer.queueInputBuffer();
    return !isEnded;
  }

  /**
   * Attempts to populate {@code buffer} with input data.
   *
   * @param buffer The buffer to populate.
   * @return Whether the {@code buffer} has been populated.
   */
  private boolean readInput(DecoderInputBuffer buffer) {
    @ReadDataResult int result = readSource(getFormatHolder(), buffer, /* readFlags= */ 0);
    switch (result) {
      case C.RESULT_BUFFER_READ:
        buffer.flip();
        if (!buffer.isEndOfStream()) {
          mediaClock.updateTimeForTrackType(getTrackType(), buffer.timeUs);
        }
        return true;
      case C.RESULT_FORMAT_READ:
        throw new IllegalStateException("Format changes are not supported.");
      case C.RESULT_NOTHING_READ:
      default:
        return false;
    }
  }
}
