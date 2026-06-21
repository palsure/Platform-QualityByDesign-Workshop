import SwiftUI
import AVKit

struct VideoPlayerView: View {
    let video: StreamVideo
    @Binding var isPresented: Bool

    @StateObject private var collector: QoECollector
    @State private var player: AVPlayer

    init(video: StreamVideo, isPresented: Binding<Bool>) {
        self.video = video
        self._isPresented = isPresented
        let url = URL(string: video.hlsUrl)!
        let p = AVPlayer(url: url)
        self._player = State(initialValue: p)
        self._collector = StateObject(wrappedValue: QoECollector(videoId: video.id))
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()

            AVPlayerViewControllerRepresentable(player: player)
                .ignoresSafeArea()
                .onAppear {
                    player.play()
                    collector.startCollecting(player: player)
                }
                .onDisappear {
                    player.pause()
                    collector.stopCollecting()
                }

            Button {
                isPresented = false
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.title)
                    .symbolRenderingMode(.palette)
                    .foregroundStyle(Color.white, Color.black.opacity(0.4))
                    .padding(20)
            }
        }
    }
}

// MARK: - AVPlayerViewController wrapper

private struct AVPlayerViewControllerRepresentable: UIViewControllerRepresentable {
    let player: AVPlayer

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let vc = AVPlayerViewController()
        vc.player = player
        vc.showsPlaybackControls = true
        vc.videoGravity = .resizeAspect
        return vc
    }

    func updateUIViewController(_ uiViewController: AVPlayerViewController, context: Context) {
        uiViewController.player = player
    }
}
