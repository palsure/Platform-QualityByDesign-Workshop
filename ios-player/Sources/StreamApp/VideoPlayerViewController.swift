#if os(iOS)
import UIKit
import AVFoundation
import AVKit

public class VideoPlayerViewController: UIViewController {
    private var player: AVPlayer?
    private var playerViewController: AVPlayerViewController?

    public var videoURL: URL?
    public var videoId: String = "ios-demo-1"

    public override func viewDidLoad() {
        super.viewDidLoad()
        setupPlayer()
    }

    private func setupPlayer() {
        guard let url = videoURL else { return }

        let playerItem = AVPlayerItem(url: url)
        player = AVPlayer(playerItem: playerItem)

        playerViewController = AVPlayerViewController()
        playerViewController?.player = player
        playerViewController?.showsPlaybackControls = true

        if let playerViewController = playerViewController {
            addChild(playerViewController)
            view.addSubview(playerViewController.view)
            playerViewController.view.frame = view.bounds
            playerViewController.didMove(toParent: self)
        }

        player?.play()
    }

    public override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        player?.pause()
    }

    deinit {
        player?.pause()
        player = nil
    }
}
#endif
