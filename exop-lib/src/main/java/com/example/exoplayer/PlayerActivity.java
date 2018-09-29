/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.example.exoplayer;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashChunkSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.Util;


/**
 * A fullscreen activity to play audio or video streams.
 */
public class PlayerActivity extends AppCompatActivity {

    private static final String TAG = PlayerActivity.class.getSimpleName();
    private PlayerView mPlayerView;
    private SimpleExoPlayer player;
    private Boolean playWhenReady = true;
    private int currentWindow = 0;
    private long playbackPosition = 0;
    private static final DefaultBandwidthMeter BANDWIDTH_METER =
            new DefaultBandwidthMeter();
    private ComponentListener componentListener;
    private static MediaSessionCompat mediaSessionCompat;
    private PlaybackStateCompat.Builder mediaPlaybackState;
    private static final String NOTIFICATION_CHANNEL_ID = "music_notification";
    private NotificationManager notificationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        //Reference to Exo player view
        mPlayerView = findViewById(R.id.video_player_view);
        componentListener = new ComponentListener();
        mediaSessionCompat = new MediaSessionCompat(this, TAG);
        mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaPlaybackState = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                );
        mediaSessionCompat.setPlaybackState(mediaPlaybackState.build());
        mediaSessionCompat.setMediaButtonReceiver(null);
        mediaSessionCompat.setCallback(new MediaCallback());
        mediaSessionCompat.setActive(true);

    }

    private void initializePlayer() {
        if (player == null) {
            // a factory to create an AdaptiveVideoTrackSelection
            //This makes sure the bandwidth meter is informed about downloaded bytes
            // and enables to collect data for bandwidth estimation.
            TrackSelection.Factory adaptiveTrackSelectionFactory =
                    new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
            player = ExoPlayerFactory.newSimpleInstance(this,
                    new DefaultRenderersFactory(this),
                    new DefaultTrackSelector(adaptiveTrackSelectionFactory),
                    new DefaultLoadControl());
        }
        mPlayerView.setPlayer(player);
        player.setPlayWhenReady(playWhenReady);
        player.seekTo(currentWindow, playbackPosition);
        //(R.string.media_url_mp3,mp4));
        Uri uri = Uri.parse(getString(R.string.media_url_dash));
        MediaSource mediaSource = buildMediaSource(uri);
        player.prepare(mediaSource, true, false);
        player.addListener(componentListener);
        player.addAnalyticsListener(componentListener);
    }

    /**
     * @param uri uri to media source to be built in extractor media source factory
     * @return returns the media source to be played
     */
    private MediaSource buildMediaSources(Uri uri) {

        // these are reused for both media sources we create below
        DefaultExtractorsFactory extractorsFactory =
                new DefaultExtractorsFactory();
        DefaultHttpDataSourceFactory dataSourceFactory =
                new DefaultHttpDataSourceFactory("user-agent");

        ExtractorMediaSource videoSource =
                new ExtractorMediaSource.Factory(
                        new DefaultHttpDataSourceFactory("exoplayer-codelab")).
                        createMediaSource(uri);

        Uri audioUri = Uri.parse(getString(R.string.media_url_mp3));
        ExtractorMediaSource audioSource =
                new ExtractorMediaSource.Factory(
                        new DefaultHttpDataSourceFactory("exoplayer-codelab")).
                        createMediaSource(audioUri);
//        return new ExtractorMediaSource.Factory(
//                new DefaultHttpDataSourceFactory("exoplayer-codelab")).
//                createMediaSource(uri);
        return new ConcatenatingMediaSource(audioSource, videoSource);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        hideSystemUi();
        if ((Util.SDK_INT <= 23 || player == null)) {
            initializePlayer();
        }
    }

    @SuppressLint("InlinedApi")
    private void hideSystemUi() {
        mPlayerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Util.SDK_INT <= 23) {
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            releasePlayer();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mediaSessionCompat.setActive(false);
    }

    public void displayNotification(PlaybackStateCompat state) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        NotificationChannelSupport notificationChannelSupport = new NotificationChannelSupport();
        notificationChannelSupport.createNotificationChannel(this, NOTIFICATION_CHANNEL_ID);
        int icon;
        String play_pause;
        if (state.getState() == PlaybackStateCompat.STATE_PLAYING) {
            icon = R.drawable.exo_controls_pause;
            play_pause = getString(R.string.pause);
        } else {
            icon = R.drawable.exo_controls_play;
            play_pause = getString(R.string.play);
        }

        NotificationCompat.Action playPauseAction = new NotificationCompat.Action(
                icon,
                play_pause,
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE));
        NotificationCompat.Action restartAction = new NotificationCompat.Action(
                R.drawable.exo_icon_previous,
                getString(R.string.previous),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS));
        NotificationCompat.Action restartActionNext = new NotificationCompat.Action(
                R.drawable.exo_icon_next,
                getString(R.string.next),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT));

        PendingIntent contentPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, PlayerActivity.class), 0);

        builder.setContentTitle("Exo Player Testing")
                .setContentText("Google Glass")
                .setContentIntent(contentPendingIntent)
                .setSmallIcon(R.drawable.exo_notification_small_icon)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(restartAction)
                .addAction(playPauseAction)
                .addAction(restartActionNext)
                .setStyle(new android.support.v4.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSessionCompat.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(0, builder.build());


    }

    private void releasePlayer() {
        if (player != null) {

            playbackPosition = player.getCurrentPosition();
            currentWindow = player.getCurrentWindowIndex();
            playWhenReady = player.getPlayWhenReady();
            player.removeListener(componentListener);
            player.removeVideoListener(null);
            player.removeAnalyticsListener(componentListener);
            player.release();
            if (notificationManager != null) {
                notificationManager.cancelAll();
            }
            player = null;
        }
    }

    /**
     * DASH is a widely used adaptive streaming format.
     * Streaming DASH with ExoPlayer, means building an appropriate adaptive MediaSource.
     * To switch our app to DASH we build a DashMediaSource by changing our buildMediaSource method as follows:
     *
     * @param uri
     * @return
     */
    private MediaSource buildMediaSource(Uri uri) {
        DataSource.Factory manifestDataSourceFactory =
                new DefaultHttpDataSourceFactory("ua");
        DashChunkSource.Factory dashChunkSourceFactory =
                new DefaultDashChunkSource.Factory(
                        new DefaultHttpDataSourceFactory("ua", BANDWIDTH_METER));
        return new DashMediaSource.Factory(dashChunkSourceFactory,
                manifestDataSourceFactory).createMediaSource(uri);
    }

    private class ComponentListener implements Player.EventListener, AnalyticsListener {

        @Override
        public void onTimelineChanged(Timeline timeline, @Nullable Object manifest, int reason) {

        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady,
                                         int playbackState) {
            if (playbackState == Player.STATE_READY && playWhenReady) {
                mediaPlaybackState.setState(PlaybackStateCompat.STATE_PLAYING, player.getContentPosition(), 1f);

            } else if (playbackState == Player.STATE_READY) {
                mediaPlaybackState.setState(PlaybackStateCompat.STATE_PAUSED, player.getContentPosition(), 1f);
            }
            mediaSessionCompat.setPlaybackState(mediaPlaybackState.build());
            displayNotification(mediaPlaybackState.build());
        }
    }

    private class MediaCallback extends MediaSessionCompat.Callback {

        @Override
        public void onPause() {
            super.onPause();
            player.setPlayWhenReady(false);
        }

        @Override
        public void onPlay() {
            super.onPlay();
            player.setPlayWhenReady(true);
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
            player.seekTo(0);
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
            player.seekTo(0);
        }
    }

    public static class MediaButtonNotification extends BroadcastReceiver {
        public MediaButtonNotification() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            MediaButtonReceiver.handleIntent(mediaSessionCompat, intent);
        }
    }
}
