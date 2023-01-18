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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
import static com.google.android.exoplayer2.transformer.TransformationException.ERROR_CODE_FAILED_RUNTIME_CHECK;
import static com.google.android.exoplayer2.transformer.TransformationException.ERROR_CODE_UNSPECIFIED;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_NOT_STARTED;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_UNAVAILABLE;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.Math.min;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** An {@link AssetLoader} implementation that uses an {@link ExoPlayer} to load samples. */
public final class ExoPlayerAssetLoader implements AssetLoader {

  /** An {@link AssetLoader.Factory} for {@link ExoPlayerAssetLoader} instances. */
  public static final class Factory implements AssetLoader.Factory {

    private final Context context;
    private final MediaSource.Factory mediaSourceFactory;
    private final Codec.DecoderFactory decoderFactory;
    private final Clock clock;

    private boolean removeAudio;
    private boolean removeVideo;
    private boolean flattenVideoForSlowMotion;

    /**
     * Creates an instance.
     *
     * @param context The {@link Context}.
     * @param mediaSourceFactory The {@link MediaSource.Factory} to use to retrieve the samples to
     *     transform.
     * @param decoderFactory The {@link Codec.DecoderFactory} to use to decode the samples (if
     *     necessary).
     * @param clock The {@link Clock} to use. It should always be {@link Clock#DEFAULT}, except for
     *     testing.
     */
    public Factory(
        Context context,
        MediaSource.Factory mediaSourceFactory,
        Codec.DecoderFactory decoderFactory,
        Clock clock) {
      this.context = context;
      this.mediaSourceFactory = mediaSourceFactory;
      this.decoderFactory = decoderFactory;
      this.clock = clock;
    }

    @Override
    @CanIgnoreReturnValue
    public AssetLoader.Factory setRemoveAudio(boolean removeAudio) {
      this.removeAudio = removeAudio;
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public AssetLoader.Factory setRemoveVideo(boolean removeVideo) {
      this.removeVideo = removeVideo;
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public AssetLoader.Factory setFlattenVideoForSlowMotion(boolean flattenVideoForSlowMotion) {
      this.flattenVideoForSlowMotion = flattenVideoForSlowMotion;
      return this;
    }

    @Override
    public AssetLoader createAssetLoader(MediaItem mediaItem, Looper looper, Listener listener) {
      return new ExoPlayerAssetLoader(
          context,
          mediaItem,
          removeAudio,
          removeVideo,
          flattenVideoForSlowMotion,
          mediaSourceFactory,
          decoderFactory,
          looper,
          listener,
          clock);
    }
  }

  private final MediaItem mediaItem;
  private final CapturingDecoderFactory decoderFactory;
  private final ExoPlayer player;

  private @Transformer.ProgressState int progressState;

  private ExoPlayerAssetLoader(
      Context context,
      MediaItem mediaItem,
      boolean removeAudio,
      boolean removeVideo,
      boolean flattenForSlowMotion,
      MediaSource.Factory mediaSourceFactory,
      Codec.DecoderFactory decoderFactory,
      Looper looper,
      Listener listener,
      Clock clock) {
    this.mediaItem = mediaItem;
    this.decoderFactory = new CapturingDecoderFactory(decoderFactory);

    DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
    trackSelector.setParameters(
        new DefaultTrackSelector.Parameters.Builder(context)
            .setForceHighestSupportedBitrate(true)
            .build());
    // Arbitrarily decrease buffers for playback so that samples start being sent earlier to the
    // pipelines (rebuffers are less problematic for the transformation use case).
    DefaultLoadControl loadControl =
        new DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DEFAULT_MIN_BUFFER_MS,
                DEFAULT_MAX_BUFFER_MS,
                DEFAULT_BUFFER_FOR_PLAYBACK_MS / 10,
                DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / 10)
            .build();
    ExoPlayer.Builder playerBuilder =
        new ExoPlayer.Builder(
                context,
                new RenderersFactoryImpl(
                    removeAudio, removeVideo, flattenForSlowMotion, this.decoderFactory, listener))
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setLooper(looper);
    if (clock != Clock.DEFAULT) {
      // Transformer.Builder#setClock is also @VisibleForTesting, so if we're using a non-default
      // clock we must be in a test context.
      @SuppressWarnings("VisibleForTests")
      ExoPlayer.Builder unusedForAnnotation = playerBuilder.setClock(clock);
    }
    player = playerBuilder.build();
    player.addListener(new PlayerListener(listener));

    progressState = PROGRESS_STATE_NOT_STARTED;
  }

  @Override
  public void start() {
    player.setMediaItem(mediaItem);
    player.prepare();
    progressState = PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
  }

  @Override
  public @Transformer.ProgressState int getProgress(ProgressHolder progressHolder) {
    if (progressState == PROGRESS_STATE_AVAILABLE) {
      long durationMs = player.getDuration();
      long positionMs = player.getCurrentPosition();
      progressHolder.progress = min((int) (positionMs * 100 / durationMs), 99);
    }
    return progressState;
  }

  @Override
  public ImmutableMap<Integer, String> getDecoderNames() {
    ImmutableMap.Builder<Integer, String> decoderNamesByTrackType = new ImmutableMap.Builder<>();
    @Nullable String audioDecoderName = decoderFactory.getAudioDecoderName();
    if (audioDecoderName != null) {
      decoderNamesByTrackType.put(C.TRACK_TYPE_AUDIO, audioDecoderName);
    }
    @Nullable String videoDecoderName = decoderFactory.getVideoDecoderName();
    if (videoDecoderName != null) {
      decoderNamesByTrackType.put(C.TRACK_TYPE_VIDEO, videoDecoderName);
    }
    return decoderNamesByTrackType.buildOrThrow();
  }

  @Override
  public void release() {
    player.release();
    progressState = PROGRESS_STATE_NOT_STARTED;
  }

  private static final class RenderersFactoryImpl implements RenderersFactory {

    private final TransformerMediaClock mediaClock;
    private final boolean removeAudio;
    private final boolean removeVideo;
    private final boolean flattenForSlowMotion;
    private final Codec.DecoderFactory decoderFactory;
    private final Listener assetLoaderListener;

    public RenderersFactoryImpl(
        boolean removeAudio,
        boolean removeVideo,
        boolean flattenForSlowMotion,
        Codec.DecoderFactory decoderFactory,
        Listener assetLoaderListener) {
      this.removeAudio = removeAudio;
      this.removeVideo = removeVideo;
      this.flattenForSlowMotion = flattenForSlowMotion;
      this.decoderFactory = decoderFactory;
      this.assetLoaderListener = assetLoaderListener;
      mediaClock = new TransformerMediaClock();
    }

    @Override
    public Renderer[] createRenderers(
        Handler eventHandler,
        VideoRendererEventListener videoRendererEventListener,
        AudioRendererEventListener audioRendererEventListener,
        TextOutput textRendererOutput,
        MetadataOutput metadataRendererOutput) {
      int rendererCount = removeAudio || removeVideo ? 1 : 2;
      Renderer[] renderers = new Renderer[rendererCount];
      int index = 0;
      if (!removeAudio) {
        renderers[index] =
            new ExoAssetLoaderAudioRenderer(decoderFactory, mediaClock, assetLoaderListener);
        index++;
      }
      if (!removeVideo) {
        renderers[index] =
            new ExoAssetLoaderVideoRenderer(
                flattenForSlowMotion, decoderFactory, mediaClock, assetLoaderListener);
        index++;
      }
      return renderers;
    }
  }

  private final class PlayerListener implements Player.Listener {

    private final Listener assetLoaderListener;

    public PlayerListener(Listener assetLoaderListener) {
      this.assetLoaderListener = assetLoaderListener;
    }

    @Override
    public void onTimelineChanged(Timeline timeline, int reason) {
      if (progressState != PROGRESS_STATE_WAITING_FOR_AVAILABILITY) {
        return;
      }
      Timeline.Window window = new Timeline.Window();
      timeline.getWindow(/* windowIndex= */ 0, window);
      if (!window.isPlaceholder) {
        long durationUs = window.durationUs;
        // Make progress permanently unavailable if the duration is unknown, so that it doesn't jump
        // to a high value at the end of the transformation if the duration is set once the media is
        // entirely loaded.
        progressState =
            durationUs <= 0 || durationUs == C.TIME_UNSET
                ? PROGRESS_STATE_UNAVAILABLE
                : PROGRESS_STATE_AVAILABLE;
        assetLoaderListener.onDurationUs(window.durationUs);
      }
    }

    @Override
    public void onTracksChanged(Tracks tracks) {
      int trackCount = 0;
      if (tracks.isTypeSelected(C.TRACK_TYPE_AUDIO)) {
        trackCount++;
      }
      if (tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) {
        trackCount++;
      }
      if (trackCount == 0) {
        assetLoaderListener.onTransformationError(
            TransformationException.createForAssetLoader(
                new IllegalStateException("The asset loader has no track to output."),
                ERROR_CODE_FAILED_RUNTIME_CHECK));
        return;
      } else {
        assetLoaderListener.onTrackCount(trackCount);
      }
      // Start the renderers after having registered all the tracks to make sure the AssetLoader
      // listener callbacks are called in the right order.
      player.play();
    }

    @Override
    public void onPlayerError(PlaybackException error) {
      @TransformationException.ErrorCode
      int errorCode =
          checkNotNull(
              TransformationException.NAME_TO_ERROR_CODE.getOrDefault(
                  error.getErrorCodeName(), ERROR_CODE_UNSPECIFIED));
      assetLoaderListener.onTransformationError(
          TransformationException.createForAssetLoader(error, errorCode));
    }
  }
}
