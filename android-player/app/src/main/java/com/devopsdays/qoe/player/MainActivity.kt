package com.devopsdays.qoe.player

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.devopsdays.qoe.player.data.StreamVideo
import com.devopsdays.qoe.player.data.VIDEO_CATALOG
import com.devopsdays.qoe.player.ui.StreamCatalogAdapter

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val list = findViewById<RecyclerView>(R.id.catalog_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = StreamCatalogAdapter(VIDEO_CATALOG) { video -> openPlayer(video) }
    }

    private fun openPlayer(video: StreamVideo) {
        startActivity(
            Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_VIDEO_ID, video.id)
                putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, video.title)
                putExtra(PlayerActivity.EXTRA_VIDEO_URL, video.hlsUrl)
            }
        )
    }
}
