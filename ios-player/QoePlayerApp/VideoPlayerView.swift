import SwiftUI
import AVKit

struct VideoPlayerView: View {
    let video: StreamVideo
    @Binding var isPresented: Bool

    @State private var player: AVPlayer

    init(video: StreamVideo, isPresented: Binding<Bool>) {
        self.video = video
        self._isPresented = isPresented
        let url = URL(string: video.hlsUrl)!
        self._player = State(initialValue: AVPlayer(url: url))
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()

            AVPlayerViewControllerRepresentable(player: player)
                .ignoresSafeArea()
                .onAppear {
                    player.play()
                }
                .onDisappear {
                    player.pause()
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
            .accessibilityLabel("Close player")
        }
    }
}

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
