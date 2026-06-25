package com.devopsdays.qoe.player

import android.os.Bundle
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.appbar.MaterialToolbar

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val title = intent.getStringExtra(EXTRA_VIDEO_TITLE).orEmpty()
        val url = intent.getStringExtra(EXTRA_VIDEO_URL).orEmpty()
        val statusText: TextView = findViewById(R.id.status_text)
        val playerView: PlayerView = findViewById(R.id.player_view)
        val toolbar: MaterialToolbar = findViewById(R.id.player_toolbar)

        toolbar.title = title.ifBlank { getString(R.string.now_playing) }
        toolbar.setNavigationOnClickListener { finish() }
        playerView.setFullscreenButtonClickListener(null)

        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            playerView.player = exoPlayer
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    statusText.text = when (playbackState) {
                        Player.STATE_BUFFERING -> "Buffering..."
                        Player.STATE_READY -> if (exoPlayer.isPlaying) "Playing" else "Ready"
                        Player.STATE_ENDED -> "Ended"
                        else -> "Ready"
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (exoPlayer.playbackState == Player.STATE_READY) {
                        statusText.text = if (isPlaying) "Playing" else "Paused"
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    statusText.text = "Error: ${error.message}"
                }
            })

            if (url.isNotBlank()) {
                exoPlayer.setMediaItem(MediaItem.fromUri(url))
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_VIDEO_URL = "video_url"
    }
}
