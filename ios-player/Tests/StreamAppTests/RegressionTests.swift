import XCTest
@testable import StreamApp

/// Regression Tests — edge cases, boundary values, and guards against known issues.
///
/// Scope: exact threshold boundaries for quality tiers, session ID format
/// contract, error-only degradation, zero-error excellent quality, catalog
/// invariants, and concurrent-safe state isolation.
/// Run with: swift test --filter RegressionTests
final class RegressionTests: XCTestCase {

    // MARK: - Quality boundary values (exact thresholds)

    func test_regression_exactly2SecondsBufferingIsGood() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 2.0, errorCount: 0), "good")
    }

    func test_regression_justBelow2SecondsIsExcellent() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 1.9999, errorCount: 0), "excellent")
    }

    func test_regression_exactly5SecondsBufferingIsFair() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 5.0, errorCount: 0), "fair")
    }

    func test_regression_justBelow5SecondsIsGood() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 4.9999, errorCount: 0), "good")
    }

    func test_regression_exactly10SecondsBufferingIsPoor() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 10.0, errorCount: 0), "poor")
    }

    func test_regression_justBelow10SecondsIsFair() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 9.9999, errorCount: 0), "fair")
    }

    // MARK: - Error count boundary values

    func test_regression_exactly2ErrorsMakesFair() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 0.0, errorCount: 2), "fair")
    }

    func test_regression_exactly1ErrorKeepsGood() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 0.0, errorCount: 1), "good")
    }

    func test_regression_exactly5ErrorsMakesPoor() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 0.0, errorCount: 5), "poor")
    }

    func test_regression_exactly4ErrorsKeepsFair() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 0.0, errorCount: 4), "fair")
    }

    // MARK: - Session ID format contract

    func test_regression_sessionIdHasDashSeparatedParts() {
        let collector = QoECollector(videoId: "reg-vid-001")
        let parts = collector.sessionId.split(separator: "-")
        XCTAssertGreaterThanOrEqual(parts.count, 3)
    }

    func test_regression_sessionIdFirstPartIsIos() {
        let collector = QoECollector(videoId: "reg-vid-001")
        XCTAssertEqual(collector.sessionId.split(separator: "-").first.map(String.init), "ios")
    }

    func test_regression_sessionIdSecondPartIsNumeric() {
        let collector = QoECollector(videoId: "reg-vid-001")
        let parts = collector.sessionId.split(separator: "-")
        guard parts.count >= 2 else {
            return XCTFail("Session ID has too few parts")
        }
        XCTAssertNotNil(Int64(parts[1]), "Second part of session ID must be a numeric timestamp")
    }

    // MARK: - Buffering end without start leaves state unchanged

    func test_regression_bufferingEndWithoutStartLeavesTimeAtZero() {
        let collector = QoECollector(videoId: "reg-vid-001")
        collector.recordBufferingEnd()
        XCTAssertEqual(collector.totalBufferingTime, 0.0)
        XCTAssertEqual(collector.bufferingEvents.count, 0)
    }

    func test_regression_duplicateBufferingStart_isIdempotent() {
        let collector = QoECollector(videoId: "reg-vid-001")
        collector.recordBufferingStart()
        Thread.sleep(forTimeInterval: 0.1)
        collector.recordBufferingStart() // ignored — timer is already running
        Thread.sleep(forTimeInterval: 0.1)
        collector.recordBufferingEnd()
        // The total should cover the full 0.2 s period, not just 0.1 s
        XCTAssertGreaterThanOrEqual(collector.totalBufferingTime, 0.18)
    }

    // MARK: - First bitrate does not count as a switch

    func test_regression_firstBitrateObservationIsNotSwitch() {
        let collector = QoECollector(videoId: "reg-vid-001")
        collector.recordBitrateChange(newBitrate: 4500)
        XCTAssertEqual(collector.bitrateSwitches, 0)
    }

    // MARK: - Catalog edge cases

    func test_regression_findVideoIsCaseSensitive() {
        XCTAssertNil(findVideo(id: "Baseline"))
        XCTAssertNil(findVideo(id: "BASELINE"))
        XCTAssertNotNil(findVideo(id: "baseline"))
    }

    func test_regression_findVideoWithEmptyString_returnsNil() {
        XCTAssertNil(findVideo(id: ""))
    }

    func test_regression_catalogVideosHaveUniqueIds() {
        let ids = VIDEO_CATALOG.map { $0.id }
        XCTAssertEqual(ids.count, Set(ids).count)
    }

    // MARK: - Payload JSON round-trip

    func test_regression_payloadRoundTrip() throws {
        let original = QoEMetricPayload(
            platform: "ios",
            videoId: "reg-vid-001",
            sessionId: "ios-99999-abcdefgh",
            timestamp: "2026-04-01T10:00:00Z",
            deviceInfo: DeviceInfo(deviceType: "mobile", os: "iOS 17.4", screenResolution: "390x844"),
            metrics: QoEMetrics(
                playbackState: "playing",
                currentTime: 42.5,
                duration: 180.0,
                bufferingEvents: [BufferingEvent(startTime: 1.0, endTime: 1.5, duration: 0.5)],
                totalBufferingTime: 0.5,
                startupTime: 1234,
                currentBitrate: 4500,
                currentResolution: "1920x1080",
                bitrateSwitches: 2,
                errors: [],
                errorCount: 0,
                playbackQuality: "excellent"
            )
        )

        let encoded = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(QoEMetricPayload.self, from: encoded)

        XCTAssertEqual(decoded.platform, original.platform)
        XCTAssertEqual(decoded.videoId, original.videoId)
        XCTAssertEqual(decoded.metrics.currentTime, original.metrics.currentTime)
        XCTAssertEqual(decoded.metrics.bitrateSwitches, original.metrics.bitrateSwitches)
        XCTAssertEqual(decoded.metrics.bufferingEvents.count, 1)
    }
}
