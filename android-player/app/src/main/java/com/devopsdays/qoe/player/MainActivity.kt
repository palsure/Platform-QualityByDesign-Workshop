package com.devopsdays.qoe.player

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.devopsdays.qoe.player.services.QoECollector
import com.newrelic.agent.android.NewRelic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private var qoeCollector: QoECollector? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ── New Relic Mobile bootstrap ────────────────────────────────────────
        // Token is baked into BuildConfig at build time (see app/build.gradle.kts).
        // Empty token = disabled — keeps local dev / espresso runs from spamming
        // a real NR account. We also disable on emulators just to be safe.
        val nrToken = BuildConfig.NEWRELIC_TOKEN
        if (nrToken.isNotBlank()) {
            try {
                NewRelic.withApplicationToken(nrToken)
                    .withApplicationVersion(BuildConfig.VERSION_NAME)
                    .withLogLevel(com.newrelic.agent.android.logging.AgentLog.INFO)
                    .start(this.applicationContext)
                Log.i("MainActivity", "New Relic Mobile agent started")
            } catch (e: Throwable) {
                // Never let observability bring down the player.
                Log.w("MainActivity", "New Relic init failed", e)
            }
        } else {
            Log.i("MainActivity", "New Relic Mobile disabled (no token)")
        }

        val playerView: PlayerView = findViewById(R.id.player_view)
        playerView.setFullscreenButtonClickListener(null) // disable fullscreen during tests
        val urlInput: EditText = findViewById(R.id.url_input)
        val videoIdInput: EditText = findViewById(R.id.video_id_input)
        val playButton: Button = findViewById(R.id.play_button)
        val statusText: TextView = findViewById(R.id.status_text)

        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            playerView.player = exoPlayer
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            statusText.text = "Buffering..."
                            qoeCollector?.recordBufferingStart()
                        }
                        Player.STATE_READY -> {
                            statusText.text = "Ready"
                            qoeCollector?.recordBufferingEnd()
                        }
                        Player.STATE_ENDED -> {
                            statusText.text = "Ended"
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    statusText.text = "Error: ${error.message}"
                    qoeCollector?.recordError("PLAYBACK_ERROR", error.message ?: "Unknown error")
                }
            })
        }

        playButton.setOnClickListener {
            val url = urlInput.text.toString()
            val videoId = videoIdInput.text.toString().ifEmpty { "android-demo-1" }

            if (url.isNotEmpty()) {
                qoeCollector = QoECollector(videoId, this)
                val mediaItem = MediaItem.fromUri(url)
                player?.setMediaItem(mediaItem)
                player?.prepare()
                player?.play()

                // Start collecting metrics
                scope.launch {
                    qoeCollector?.startCollecting(player!!)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        qoeCollector?.stopCollecting()
    }
}
