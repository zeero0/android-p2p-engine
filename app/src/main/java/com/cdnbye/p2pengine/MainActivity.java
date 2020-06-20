package com.cdnbye.p2pengine;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;

import com.cdnbye.sdk.P2pEngine;
import com.cdnbye.sdk.P2pConfig;
import com.cdnbye.sdk.P2pStatisticsListener;
import com.cdnbye.sdk.LogLevel;
import com.cdnbye.sdk.PlayerStatsCallback;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.ui.PlayerView;
//import com.tencent.bugly.crashreport.CrashReport;

public class MainActivity extends Activity {

    private final String VOD = "http://sample.vodobox.com/planete_interdite/planete_interdite_alternate.m3u8";
    private final String LIVE = "http://hefeng.live.tempsource.cjyun.org/videotmp/s10100-hftv.m3u8";

    private PlayerView playerView;
    private SimpleExoPlayer player;

    private Button replayBtn;
    private Button switchBtn;
    private Button vodBtn;
    private Button liveBtn;
    private String currentUrl = VOD;
//    private String currentUrl = LIVE;

    private double totalHttpDownloaded = 0;
    private double totalP2pDownloaded = 0;
    private double totalP2pUploaded = 0;
    private DefaultTrackSelector mTrackSelector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // bugly
//        CrashReport.initCrashReport(getApplicationContext(), "e40b652a35", true);

        setContentView(R.layout.activity_main);
        TextView versionV = findViewById(R.id.version);
        versionV.setText("Version: " + P2pEngine.Version);

        P2pConfig config = new P2pConfig.Builder()
                .logEnabled(true)
                .logLevel(LogLevel.DEBUG)
                .p2pEnabled(true)
                .withTag("exoplayer")
                .build();

        // Instantiate P2pEngine，which is a singleton
        P2pEngine engine = P2pEngine.initEngine(getApplicationContext(), "sj8BMXlWR", config);

        // Recommended while playing living stream
        engine.setPlayStats(new PlayerStatsCallback() {
            @Override
            public long onBufferedDuration() {
                return player.getBufferedPosition() - player.getCurrentPosition();
            }
        });


        engine.addP2pStatisticsListener(new P2pStatisticsListener() {
            @Override
            public void onHttpDownloaded(long value) {
                totalHttpDownloaded += (double) value;
                refreshRatio();

            }

            @Override
            public void onP2pDownloaded(long value) {
                totalP2pDownloaded += (double) value;
                TextView offloadV = findViewById(R.id.offload);
                String text = String.format("Offload: %.2fMB", totalP2pDownloaded / 1024);
                offloadV.setText(text);

                refreshRatio();
            }

            @Override
            public void onP2pUploaded(long value) {
                totalP2pUploaded += (double) value;
                TextView uploadV = findViewById(R.id.upload);
                String text = String.format("Upload: %.2fMB", totalP2pUploaded / 1024);
                uploadV.setText(text);
            }

            @Override
            public void onPeers(List<String> peers) {
                TextView peersV = findViewById(R.id.peers);
                String text = String.format("Peers: %d", peers.size());
                peersV.setText(text);
            }

            @Override
            public void onServerConnected(boolean connected) {
                TextView connectedV = findViewById(R.id.connected);
                String text = String.format("Connected: %s", connected ? "Yes" : "No");
                connectedV.setText(text);
                TextView peerIdV = findViewById(R.id.peerId);
                String text2 = String.format("Peer ID: %s", P2pEngine.getInstance().getPeerId());
                peerIdV.setText(text2);
            }
        });

        startPlay(currentUrl);

        replayBtn = findViewById(R.id.replay);
        switchBtn = findViewById(R.id.switcher);
        vodBtn = findViewById(R.id.vod);
        liveBtn = findViewById(R.id.live);

        replayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 清空数据
                clearData();
                if (player != null && player.isPlaying()) {
                    player.stop();
                }
                startPlay(currentUrl);
            }
        });

        switchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentUrl.equals(VOD)) {
                    currentUrl = LIVE;
                } else {
                    currentUrl = VOD;
                }
                // 清空数据
                clearData();
                startPlay(currentUrl);
            }
        });
        vodBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 清空数据
                clearData();
                currentUrl = VOD;
                startPlay(currentUrl);
            }
        });

        liveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 清空数据
                clearData();
                currentUrl = LIVE;
                startPlay(currentUrl);
            }
        });
    }

    private void startPlay(String url) {

        if (player != null && player.isPlaying()) {
            player.stop(true);
            player.release();
        }

        // Convert original playback address (m3u8) to the address of the local proxy server
        String parsedUrl = P2pEngine.getInstance().parseStreamUrl(url);

        // Create a data source factory.
        DataSource.Factory dataSourceFactory =
                new DefaultHttpDataSourceFactory(
                        Util.getUserAgent(this, "p2p-engine"),
                        8000000,
                        8000000,
                        true   /* allowCrossProtocolRedirects */
                );
        // Create a HLS media source pointing to a playlist uri.
        HlsMediaSource hlsMediaSource =
                new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(parsedUrl));
        // Create a player instance.
//        player = ExoPlayerFactory.newSimpleInstance(this);
        setTrackSelector();
        RenderersFactory renderFactory = ((MyApplication) getApplication()).buildRenderersFactory(false);
        player = new SimpleExoPlayer.Builder(this, renderFactory)
                .setTrackSelector(mTrackSelector)
                .build();
        player.setAudioAttributes(AudioAttributes.DEFAULT,true);
        player.addListener(new Player.EventListener() {
            @Override
            @SuppressWarnings("ReferenceEquality")
            public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
                MappingTrackSelector.MappedTrackInfo mappedTrackInfo = mTrackSelector.getCurrentMappedTrackInfo();

                DefaultTrackNameProvider nameProvider = new DefaultTrackNameProvider(getResources());
                for (int ri = 0; ri < mappedTrackInfo.getRendererCount(); ri++) {
                    TrackGroupArray tracks = mappedTrackInfo.getTrackGroups(ri);
                    for (int ti = 0; ti < tracks.length; ti++) {
                        TrackGroup group = tracks.get(ti);
                        for (int gi = 0; gi < group.length; gi++) {
                            Log.e("PLAYER", ri + " | " + ti + " | " + gi + " - " + "TrackName: " + nameProvider
                                    .getTrackName(group.getFormat(gi)));
                        }
                    }
                }
            }
        });
        // Attach player to the view.
        playerView = findViewById(R.id.player_view);
        playerView.setPlayer(player);
        // Prepare the player with the HLS media source.
        player.prepare(hlsMediaSource);
        // Start play when ready
        player.setPlayWhenReady(true);
    }

    private void setTrackSelector() {
        TrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory();

        DefaultTrackSelector.ParametersBuilder builder = new DefaultTrackSelector.ParametersBuilder(this);
        boolean tunneling = false;
        if (Util.SDK_INT >= 21 && tunneling) {
            builder.setTunnelingAudioSessionId(C.generateAudioSessionIdV21(this));
        }
        mTrackSelector = new DefaultTrackSelector(this, trackSelectionFactory);
        mTrackSelector.setParameters(builder.build());
    }

    private void refreshRatio() {
        double ratio = 0;
        if (totalHttpDownloaded + totalP2pDownloaded != 0) {
            ratio = totalP2pDownloaded / (totalHttpDownloaded + totalP2pDownloaded);
        }
        TextView ratioV = findViewById(R.id.ratio);
        String text = String.format("P2P Ratio: %.0f%%", ratio * 100);
        ratioV.setText(text);
    }

    private void clearData() {
        totalHttpDownloaded = 0;
        totalP2pDownloaded = 0;
        totalP2pUploaded = 0;
        refreshRatio();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        player.release();
    }

}
