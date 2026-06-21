import XCTest
@testable import QoePlayer

/// Build Acceptance Tests — sanity gate that must pass before any deployment.
///
/// Scope: core library types initialise correctly, catalog is structurally
/// sound, session IDs follow the contract. Fast (< 1 s), no network, no AVFoundation.
/// Run with: swift test --filter BATTests
final class BATTests: XCTestCase {

    // MARK: - QoECollector initialises correctly

    func test_bat_collectorCreatesWithVideoId() {
        let collector = QoECollector(videoId: "bat-vid-001")
        XCTAssertNotNil(collector)
    }

    func test_bat_sessionIdIsNotEmpty() {
        let collector = QoECollector(videoId: "bat-vid-001")
        XCTAssertFalse(collector.sessionId.isEmpty)
    }

    func test_bat_sessionIdStartsWithIosPrefix() {
        let collector = QoECollector(videoId: "bat-vid-001")
        XCTAssertTrue(collector.sessionId.hasPrefix("ios-"))
    }

    func test_bat_initialErrorsIsEmpty() {
        let collector = QoECollector(videoId: "bat-vid-001")
        XCTAssertTrue(collector.errors.isEmpty)
    }

    func test_bat_initialBufferingTimeIsZero() {
        let collector = QoECollector(videoId: "bat-vid-001")
        XCTAssertEqual(collector.totalBufferingTime, 0.0)
    }

    // MARK: - Video catalog is structurally sound

    func test_bat_catalogIsNotEmpty() {
        XCTAssertFalse(VIDEO_CATALOG.isEmpty)
    }

    func test_bat_catalogHasExpectedSize() {
        XCTAssertEqual(VIDEO_CATALOG.count, 3)
    }

    func test_bat_baselineVideoExists() {
        XCTAssertNotNil(findVideo(id: "baseline"))
    }

    func test_bat_allCatalogVideosHaveHttpsUrls() {
        VIDEO_CATALOG.forEach { video in
            XCTAssertTrue(video.hlsUrl.hasPrefix("https://"))
        }
    }

    func test_bat_allCatalogVideosHaveM3u8Urls() {
        VIDEO_CATALOG.forEach { video in
            XCTAssertTrue(video.hlsUrl.hasSuffix(".m3u8"))
        }
    }

    // MARK: - Quality calculator returns valid labels

    func test_bat_qualityCalculatorReturnsKnownLabel() {
        let validLabels: Set<String> = ["excellent", "good", "fair", "poor"]
        let label = QoEQualityCalculator.calculate(totalBufferingTime: 0, errorCount: 0)
        XCTAssertTrue(validLabels.contains(label), "Unexpected quality label: \(label)")
    }

    func test_bat_perfectPlaybackIsExcellent() {
        XCTAssertEqual(
            QoEQualityCalculator.calculate(totalBufferingTime: 0, errorCount: 0),
            "excellent"
        )
    }

    // MARK: - Payload model

    func test_bat_payloadModelEncodes() throws {
        let payload = QoEMetricPayload(
            platform: "ios",
            videoId: "bat-vid-001",
            sessionId: "ios-12345-abcd",
            timestamp: "2026-01-01T00:00:00Z",
            deviceInfo: DeviceInfo(deviceType: "mobile", os: "iOS 17", screenResolution: "390x844"),
            metrics: QoEMetrics(
                playbackState: "playing",
                currentTime: 10.0,
                duration: 120.0,
                bufferingEvents: [],
                totalBufferingTime: 0.0,
                startupTime: nil,
                currentBitrate: nil,
                currentResolution: nil,
                bitrateSwitches: 0,
                errors: [],
                errorCount: 0,
                playbackQuality: "excellent"
            )
        )
        let data = try JSONEncoder().encode(payload)
        XCTAssertFalse(data.isEmpty)
    }

    func test_bat_payloadPlatformIsIos() {
        let payload = QoEMetricPayload(
            platform: "ios",
            videoId: "bat-vid-001",
            sessionId: "ios-12345-abcd",
            timestamp: "2026-01-01T00:00:00Z",
            deviceInfo: DeviceInfo(deviceType: "mobile", os: "iOS 17", screenResolution: "390x844"),
            metrics: QoEMetrics(
                playbackState: "idle",
                currentTime: 0,
                duration: 0,
                bufferingEvents: [],
                totalBufferingTime: 0,
                startupTime: nil,
                currentBitrate: nil,
                currentResolution: nil,
                bitrateSwitches: 0,
                errors: [],
                errorCount: 0,
                playbackQuality: "excellent"
            )
        )
        XCTAssertEqual(payload.platform, "ios")
    }
}
