#if os(iOS)
import UIKit
import AVFoundation
import AVKit

public class VideoPlayerViewController: UIViewController {
    private var player: AVPlayer?
    private var playerViewController: AVPlayerViewController?
    private var qoeCollector: QoECollector?

    public var videoURL: URL?
    public var videoId: String = "ios-demo-1"
    public var apiBaseURL: String = "http://localhost:8080/api/v1"

    public override func viewDidLoad() {
        super.viewDidLoad()
        setupPlayer()
    }

    private func setupPlayer() {
        guard let url = videoURL else { return }

        let startTime = Date()
        qoeCollector = QoECollector(videoId: videoId, apiBaseURL: apiBaseURL)

        let playerItem = AVPlayerItem(url: url)
        player = AVPlayer(playerItem: playerItem)

        if let player = player {
            qoeCollector?.recordStartup(startupTime: Int64(Date().timeIntervalSince(startTime) * 1000))
            qoeCollector?.startCollecting(player: player)
            setupObservers(playerItem: playerItem)
        }

        playerViewController = AVPlayerViewController()
        playerViewController?.player = player

        if let playerViewController = playerViewController {
            addChild(playerViewController)
            view.addSubview(playerViewController.view)
            playerViewController.view.frame = view.bounds
            playerViewController.didMove(toParent: self)
        }
    }

    private func setupObservers(playerItem: AVPlayerItem) {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(playerItemDidPlayToEnd),
            name: .AVPlayerItemDidPlayToEndTime,
            object: playerItem
        )
        playerItem.addObserver(self, forKeyPath: "status",               options: [.new], context: nil)
        playerItem.addObserver(self, forKeyPath: "playbackBufferEmpty",  options: [.new], context: nil)
        playerItem.addObserver(self, forKeyPath: "playbackLikelyToKeepUp", options: [.new], context: nil)
    }

    @objc private func playerItemDidPlayToEnd() {
        qoeCollector?.stopCollecting()
    }

    public override func observeValue(
        forKeyPath keyPath: String?,
        of object: Any?,
        change: [NSKeyValueChangeKey: Any]?,
        context: UnsafeMutableRawPointer?
    ) {
        guard let playerItem = object as? AVPlayerItem else { return }
        switch keyPath {
        case "status":
            if playerItem.status == .failed, let error = playerItem.error {
                qoeCollector?.recordError(code: "PLAYBACK_ERROR", message: error.localizedDescription)
            }
        case "playbackBufferEmpty":
            if playerItem.isPlaybackBufferEmpty { qoeCollector?.recordBufferingStart() }
        case "playbackLikelyToKeepUp":
            if playerItem.isPlaybackLikelyToKeepUp { qoeCollector?.recordBufferingEnd() }
        default:
            break
        }
    }

    deinit {
        qoeCollector?.stopCollecting()
        NotificationCenter.default.removeObserver(self)
    }
}
#endif
