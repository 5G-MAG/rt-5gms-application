package com.example.fivegmag;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;

import com.example.fivegmag.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    String videoURL;
    ActivityMainBinding binding;
    boolean playWhenReady = true;
    int currentWindow = 0;
    long playbackPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Go to url when clicking on logo
        ImageView img = (ImageView)binding.logoreferencetools;
        img.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setData(Uri.parse("http://developer.5g-mag.com"));
                startActivity(intent);
            }
        });

        try {
            // Create a player instance.
            ExoPlayer fivegmagplayer = new ExoPlayer.Builder(this).build();

          //  videoURL = "https://rtvelivestream.akamaized.net/segments/24h/24h_main_dvr.m3u8";
          //  videoURL = "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd";
          //  videoURL = "http://localhost/m4d/provisioning-session-d54a1fcc-d411-4e32-807b-2c60dbaeaf5f/BigBuckBunny_4s_simple_2014_05_09.mpd";
            videoURL = "http://10.0.2.2:80/m4d/provisioning-session-d54a1fcc-d411-4e32-807b-2c60dbaeaf5f/playlist.m3u8";

            //OPTION 1: Using MediaItem (https://exoplayer.dev/hls.html or https://exoplayer.dev/dash.html)
            MediaItem mediaItem = MediaItem.fromUri(videoURL);
            fivegmagplayer.setMediaItem(mediaItem);

            //OPTION 2 - HLS: Using hlsMediaSource (https://exoplayer.dev/hls.html)
            // Create a data source factory.
            //DataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
            // Create a HLS media source pointing to a playlist uri.
            //HlsMediaSource mediaSource =
            //        new HlsMediaSource.Factory(dataSourceFactory)
            //                .createMediaSource(MediaItem.fromUri(videoURL));

            //OPTION 2 - DASH: Using dashMediaSource (https://exoplayer.dev/dash.html)
            // Create a data source factory.
            // DataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
            // Create a DASH media source pointing to a DASH manifest uri.
            //DashMediaSource mediaSource =
            //       new DashMediaSource.Factory(dataSourceFactory)
            //               .createMediaSource(MediaItem.fromUri(videoURL));

            // Set the media source to be played.
            //fivegmagplayer.setMediaSource(mediaSource);
            //COMMON TO ALL OPTIONS
            // Prepare the player.
            fivegmagplayer.setPlayWhenReady(playWhenReady);
            fivegmagplayer.seekTo(currentWindow, playbackPosition);
            fivegmagplayer.prepare();

            binding.idExoPlayerVIew.setPlayer(fivegmagplayer);
            binding.textPlaying.setText(videoURL);

        } catch (Exception e) {
            // below line is used for
            // handling our errors.
            Log.e("TAG", "Error : " + e);
        }
    }
}
