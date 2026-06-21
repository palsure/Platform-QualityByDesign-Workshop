import Foundation
import AVFoundation
import UIKit

/// Collects playback quality-of-experience metrics every 5 seconds
/// and submits them to the QoE backend API.
@MainActor
final class QoECollector: ObservableObject {
    private let videoId: String
    private let sessionId: String
    private var timer: Timer?
    private var player: AVPlayer?
    private var bufferingStart: Date?
    private var totalBufferingTime: TimeInterval = 0
    private var bitrateSwitches = 0
    private var lastBitrate: Double = 0
    private var timeObserver: Any?

    // Configurable: update for physical device or staging server
    private let apiBase = "http://localhost:8080/api/v1"

    init(videoId: String) {
        self.videoId = videoId
        self.sessionId = "ios-\(Int(Date().timeIntervalSince1970))-\(UUID().uuidString.prefix(8))"
    }

    func startCollecting(player: AVPlayer) {
        self.player = player
        observeBuffering(player: player)
        timer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.collectAndSend() }
        }
    }

    func stopCollecting() {
        timer?.invalidate()
        timer = nil
        if let obs = timeObserver { player?.removeTimeObserver(obs) }
        timeObserver = nil
        player = nil
    }

    // MARK: - Private

    private func observeBuffering(player: AVPlayer) {
        NotificationCenter.default.addObserver(
            forName: .AVPlayerItemPlaybackStalled,
            object: player.currentItem,
            queue: .main
        ) { [weak self] _ in
            // Dispatch back to the main actor to mutate the @MainActor-isolated property
            Task { @MainActor [weak self] in
                self?.bufferingStart = Date()
            }
        }
    }

    private func collectAndSend() {
        guard let player else { return }

        let currentTime = player.currentTime().seconds
        let duration: Double = {
            guard let d = player.currentItem?.duration, d.isNumeric else { return 0 }
            return d.seconds
        }()
        let isPlaying = player.timeControlStatus == .playing

        if bufferingStart != nil && isPlaying {
            totalBufferingTime += Date().timeIntervalSince(bufferingStart!)
            bufferingStart = nil
        }

        let payload: [String: Any] = [
            "platform": "ios",
            "videoId": videoId,
            "sessionId": sessionId,
            "timestamp": ISO8601DateFormatter().string(from: Date()),
            "deviceInfo": [
                "deviceType": UIDevice.current.userInterfaceIdiom == .pad ? "tablet" : "mobile",
                "os": "iOS \(UIDevice.current.systemVersion)",
                "model": UIDevice.current.model,
            ],
            "metrics": [
                "currentTime": currentTime.isNaN ? 0 : currentTime,
                "duration": duration,
                "isPlaying": isPlaying,
                "totalBufferingTime": totalBufferingTime,
                "bitrateSwitches": bitrateSwitches,
                "playbackQuality": qualityLabel(),
            ],
        ]

        postMetrics(payload)
    }

    private func qualityLabel() -> String {
        switch totalBufferingTime {
        case ..<2:  return "excellent"
        case ..<5:  return "good"
        case ..<10: return "fair"
        default:    return "poor"
        }
    }

    private func postMetrics(_ payload: [String: Any]) {
        guard let url = URL(string: "\(apiBase)/metrics"),
              let body = try? JSONSerialization.data(withJSONObject: payload) else { return }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.httpBody = body
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.timeoutInterval = 10
        URLSession.shared.dataTask(with: req) { _, _, error in
            if let error { print("QoECollector: \(error.localizedDescription)") }
        }.resume()
    }
}
