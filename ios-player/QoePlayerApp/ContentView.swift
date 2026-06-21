import SwiftUI

struct StreamVideo: Identifiable {
    let id: String
    let title: String
    let subtitle: String
    let hlsUrl: String
}

private let streamCatalog: [StreamVideo] = [
    StreamVideo(
        id: "baseline",
        title: "Baseline Stream",
        subtitle: "Multi-bitrate HLS demo",
        hlsUrl: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    ),
    StreamVideo(
        id: "apple-advanced",
        title: "Apple Advanced HLS",
        subtitle: "fMP4 fragmented stream",
        hlsUrl: "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
    ),
    StreamVideo(
        id: "apple-basic",
        title: "Apple Basic HLS",
        subtitle: "Classic MPEG-TS stream",
        hlsUrl: "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8"
    ),
]

struct ContentView: View {
    @State private var selectedVideo: StreamVideo?
    @State private var isPlaying = false

    var body: some View {
        NavigationView {
            List(streamCatalog) { video in
                Button {
                    selectedVideo = video
                    isPlaying = true
                } label: {
                    HStack(spacing: 14) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 10)
                                .fill(Color.blue.opacity(0.15))
                                .frame(width: 48, height: 48)
                            Image(systemName: "play.circle.fill")
                                .font(.title2)
                                .foregroundColor(.blue)
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(video.title)
                                .font(.headline)
                                .foregroundColor(.primary)
                            Text(video.subtitle)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 6)
                }
            }
            .navigationTitle("QoE Player")
            .navigationBarTitleDisplayMode(.large)
            .fullScreenCover(isPresented: $isPlaying) {
                if let video = selectedVideo {
                    VideoPlayerView(video: video, isPresented: $isPlaying)
                }
            }
        }
    }
}
