import Foundation
import AVFoundation

public struct QoEMetricPayload: Codable {
    public let platform: String
    public let videoId: String
    public let sessionId: String
    public let timestamp: String
    public let deviceInfo: DeviceInfo
    public let metrics: QoEMetrics
}

public struct DeviceInfo: Codable {
    public let deviceType: String
    public let os: String
    public let screenResolution: String
}

public struct QoEMetrics: Codable {
    public let playbackState: String
    public let currentTime: Double
    public let duration: Double
    public let bufferingEvents: [BufferingEvent]
    public let totalBufferingTime: Double
    public let startupTime: Int64?
    public let currentBitrate: Int64?
    public let currentResolution: String?
    public let bitrateSwitches: Int
    public let errors: [PlaybackError]
    public let errorCount: Int
    public let playbackQuality: String
}

public struct BufferingEvent: Codable {
    public let startTime: Double
    public let endTime: Double?
    public let duration: Double
}

public struct PlaybackError: Codable {
    public let code: String
    public let message: String
    public let timestamp: Double
}

public class QoECollector {
    private let videoId: String
    // Internal so @testable test targets can verify session ID and state.
    internal let sessionId: String
    internal private(set) var bufferingEvents: [BufferingEvent] = []
    internal private(set) var errors: [PlaybackError] = []
    private var startupTime: Int64?
    private var lastBufferingStart: Date?
    internal private(set) var totalBufferingTime: Double = 0.0
    internal private(set) var bitrateSwitches: Int = 0
    private var lastBitrate: Int64?
    private var collectingTimer: Timer?
    private let apiBaseURL: String

    public init(videoId: String, apiBaseURL: String = "http://localhost:8080/api/v1") {
        self.videoId = videoId
        self.sessionId = "ios-\(Int64(Date().timeIntervalSince1970 * 1000))-\(UUID().uuidString.prefix(8))"
        self.apiBaseURL = apiBaseURL
    }

    public func recordStartup(startupTime: Int64) {
        self.startupTime = startupTime
    }

    public func recordBufferingStart() {
        if lastBufferingStart == nil {
            lastBufferingStart = Date()
        }
    }

    public func recordBufferingEnd() {
        guard let start = lastBufferingStart else { return }
        let end = Date()
        let duration = end.timeIntervalSince(start)
        totalBufferingTime += duration
        bufferingEvents.append(BufferingEvent(
            startTime: start.timeIntervalSince1970,
            endTime: end.timeIntervalSince1970,
            duration: duration
        ))
        lastBufferingStart = nil
    }

    public func recordError(code: String, message: String) {
        errors.append(PlaybackError(
            code: code,
            message: message,
            timestamp: Date().timeIntervalSince1970
        ))
    }

    public func recordBitrateChange(newBitrate: Int64) {
        if let last = lastBitrate, last != newBitrate {
            bitrateSwitches += 1
        }
        lastBitrate = newBitrate
    }

    public func startCollecting(player: AVPlayer, interval: TimeInterval = 5.0) {
        collectingTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { [weak self] _ in
            self?.sendMetrics(player: player)
        }
    }

    public func stopCollecting() {
        collectingTimer?.invalidate()
        collectingTimer = nil
    }

    private func sendMetrics(player: AVPlayer) {
        let currentTime = player.currentTime().seconds
        let duration = player.currentItem?.duration.seconds ?? 0.0
        let playbackState: String
        if let item = player.currentItem {
            switch item.status {
            case .readyToPlay: playbackState = player.rate > 0 ? "playing" : "paused"
            case .failed:      playbackState = "error"
            default:           playbackState = "buffering"
            }
        } else {
            playbackState = "idle"
        }

        // Bitrate and resolution are derived from the current access log entry
        // (iOS-only API — omitted on macOS to keep the library cross-platform).
        let currentBitrate: Int64? = nil
        let currentResolution: String? = nil

        let payload = QoEMetricPayload(
            platform: "ios",
            videoId: videoId,
            sessionId: sessionId,
            timestamp: ISO8601DateFormatter().string(from: Date()),
            deviceInfo: DeviceInfo(
                deviceType: deviceType(),
                os: osVersion(),
                screenResolution: screenResolution()
            ),
            metrics: QoEMetrics(
                playbackState: playbackState,
                currentTime: currentTime,
                duration: duration.isNaN ? 0 : duration,
                bufferingEvents: bufferingEvents,
                totalBufferingTime: totalBufferingTime,
                startupTime: startupTime,
                currentBitrate: currentBitrate,
                currentResolution: currentResolution,
                bitrateSwitches: bitrateSwitches,
                errors: errors,
                errorCount: errors.count,
                playbackQuality: QoEQualityCalculator.calculate(
                    totalBufferingTime: totalBufferingTime,
                    errorCount: errors.count
                )
            )
        )

        sendToAPI(payload: payload)
    }

    private func sendToAPI(payload: QoEMetricPayload) {
        guard let url = URL(string: "\(apiBaseURL)/metrics") else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        do {
            request.httpBody = try JSONEncoder().encode(payload)
        } catch {
            print("QoECollector: encode error: \(error)")
            return
        }
        URLSession.shared.dataTask(with: request) { _, response, error in
            if let error { print("QoECollector: \(error.localizedDescription)") }
            if let http = response as? HTTPURLResponse, http.statusCode != 201 {
                print("QoECollector: HTTP \(http.statusCode)")
            }
        }.resume()
    }

    // MARK: - Device info helpers (platform-conditional)

    private func deviceType() -> String {
#if os(iOS)
        let screenWidth = UIScreen.main.bounds.width
        if screenWidth < 600 { return "mobile" }
        if screenWidth < 1024 { return "tablet" }
        return "tv"
#else
        return "unknown"
#endif
    }

    private func osVersion() -> String {
#if os(iOS)
        return "iOS \(UIDevice.current.systemVersion)"
#elseif os(macOS)
        let v = ProcessInfo.processInfo.operatingSystemVersion
        return "macOS \(v.majorVersion).\(v.minorVersion).\(v.patchVersion)"
#else
        return "unknown"
#endif
    }

    private func screenResolution() -> String {
#if os(iOS)
        let bounds = UIScreen.main.bounds
        return "\(Int(bounds.width))x\(Int(bounds.height))"
#else
        return "0x0"
#endif
    }
}

#if os(iOS)
import UIKit
#endif
