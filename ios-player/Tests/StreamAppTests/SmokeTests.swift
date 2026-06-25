import XCTest
@testable import StreamApp

/// Smoke Tests — broader coverage run after BAT passes.
///
/// Scope: multi-event state tracking, metric accumulation, catalog lookup
/// completeness. Still fast (< 2 s), no network, no AVFoundation.
/// Run with: swift test --filter SmokeTests
final class SmokeTests: XCTestCase {

    // MARK: - Buffering accumulation

    func test_smoke_bufferingAccumulatesAcrossMultipleCycles() {
        let collector = QoECollector(videoId: "smoke-vid-001")

        collector.recordBufferingStart()
        Thread.sleep(forTimeInterval: 0.05)
        collector.recordBufferingEnd()

        collector.recordBufferingStart()
        Thread.sleep(forTimeInterval: 0.05)
        collector.recordBufferingEnd()

        XCTAssertGreaterThan(collector.totalBufferingTime, 0.08)
    }

    func test_smoke_eachBufferingEventIsRecorded() {
        let collector = QoECollector(videoId: "smoke-vid-001")

        collector.recordBufferingStart()
        Thread.sleep(forTimeInterval: 0.02)
        collector.recordBufferingEnd()

        collector.recordBufferingStart()
        Thread.sleep(forTimeInterval: 0.02)
        collector.recordBufferingEnd()

        XCTAssertEqual(collector.bufferingEvents.count, 2)
    }

    func test_smoke_bufferingEventHasPositiveDuration() {
        let collector = QoECollector(videoId: "smoke-vid-001")
        collector.recordBufferingStart()
        Thread.sleep(forTimeInterval: 0.05)
        collector.recordBufferingEnd()
        XCTAssertGreaterThan(collector.bufferingEvents.first?.duration ?? 0, 0)
    }

    // MARK: - Error accumulation

    func test_smoke_multipleErrorsAreAllTracked() {
        let collector = QoECollector(videoId: "smoke-vid-001")
        for i in 1...5 {
            collector.recordError(code: "ERR_\(i)", message: "Error \(i)")
        }
        XCTAssertEqual(collector.errors.count, 5)
    }

    func test_smoke_errorsPreserveOrderOfRecording() {
        let collector = QoECollector(videoId: "smoke-vid-001")
        collector.recordError(code: "E1", message: "First")
        collector.recordError(code: "E2", message: "Second")
        XCTAssertEqual(collector.errors.first?.code, "E1")
        XCTAssertEqual(collector.errors.last?.code, "E2")
    }

    // MARK: - Bitrate switches accumulate

    func test_smoke_multipleBitrateSwitches_countedCorrectly() {
        let collector = QoECollector(videoId: "smoke-vid-001")
        let bitrates: [Int64] = [4500, 2000, 800, 2000, 4500]
        bitrates.forEach { collector.recordBitrateChange(newBitrate: $0) }
        XCTAssertEqual(collector.bitrateSwitches, 4)
    }

    // MARK: - Quality degrades with buffering

    func test_smoke_qualityDropsToGoodWithModeratebuffering() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 3.0, errorCount: 0), "good")
    }

    func test_smoke_qualityDropsToFairWithHighBuffering() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 7.0, errorCount: 0), "fair")
    }

    func test_smoke_qualityDropsToPoorWithVeryHighBuffering() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 12.0, errorCount: 0), "poor")
    }

    func test_smoke_qualityDropsWithErrors() {
        // Single error with minimal buffering → good (not excellent)
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 0.0, errorCount: 1), "good")
    }

    // MARK: - Catalog lookup completeness

    func test_smoke_findVideoSucceedsForAllCatalogEntries() {
        VIDEO_CATALOG.forEach { expected in
            let actual = findVideo(id: expected.id)
            XCTAssertNotNil(actual, "findVideo must find '\(expected.id)'")
            XCTAssertEqual(actual?.hlsUrl, expected.hlsUrl)
        }
    }

    func test_smoke_catalogSubtitlesAreNonBlank() {
        VIDEO_CATALOG.forEach { video in
            XCTAssertFalse(video.subtitle.isEmpty)
        }
    }

    // MARK: - Multiple collectors are independent

    func test_smoke_twoCollectorsDoNotShareState() {
        let c1 = QoECollector(videoId: "vid-A")
        let c2 = QoECollector(videoId: "vid-B")

        c1.recordError(code: "ERR", message: "only for c1")
        c1.recordBufferingStart()
        Thread.sleep(forTimeInterval: 0.05)
        c1.recordBufferingEnd()

        XCTAssertEqual(c2.errors.count, 0)
        XCTAssertEqual(c2.totalBufferingTime, 0.0)
    }
}
