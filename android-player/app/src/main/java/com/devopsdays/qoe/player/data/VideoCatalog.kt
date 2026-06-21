package com.devopsdays.qoe.player.data

data class StreamVideo(
    val id: String,
    val title: String,
    val subtitle: String,
    val hlsUrl: String,
)

val VIDEO_CATALOG: List<StreamVideo> = listOf(
    StreamVideo(
        id = "baseline",
        title = "Baseline Stream",
        subtitle = "Multi-bitrate HLS demo",
        hlsUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
    ),
    StreamVideo(
        id = "apple-advanced",
        title = "Apple Advanced HLS",
        subtitle = "fMP4 fragmented stream",
        hlsUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
    ),
    StreamVideo(
        id = "apple-basic",
        title = "Apple Basic HLS",
        subtitle = "Classic MPEG-TS stream",
        hlsUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8",
    ),
)

fun findVideo(id: String): StreamVideo? = VIDEO_CATALOG.find { it.id == id }
