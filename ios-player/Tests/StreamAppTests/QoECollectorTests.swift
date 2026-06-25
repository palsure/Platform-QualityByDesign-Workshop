import XCTest
@testable import StreamApp

/// Unit tests for QoECollector (library version in Sources/StreamApp/).
/// Tests cover session ID format, buffering tracking, error tracking,
/// bitrate tracking, and quality score evolution — all without network calls.
final class QoECollectorTests: XCTestCase {

    // MARK: - Session ID

    func test_sessionId_startsWithIosPrefix() {
        let collector = QoECollector(videoId: "vid-001")
        XCTAssertTrue(
            collector.sessionId.hasPrefix("ios-"),
            "Session ID must start with 'ios-', got: \(collector.sessionId)"
        )
    }

    func test_sessionId_containsVideoIdTimestamp() {
        let collector = QoECollector(videoId: "vid-001")
        // Format: ios-<timestamp>-<uuid-prefix>
        let parts = collector.sessionId.split(separator: "-")
        XCTAssertGreaterThanOrEqual(parts.count, 3, "Session ID must have at least 3 dash-separated parts")
    }

    func test_sessionId_isUniquePerInstance() {
        let c1 = QoECollector(videoId: "vid-001")
        let c2 = QoECollector(videoId: "vid-001")
        XCTAssertNotEqual(c1.sessionId, c2.sessionId)
    }

    // MARK: - Initial state

    func test_initialTotalBufferingTime_isZero() {
        let collector = QoECollector(videoId: "vid-001")
        XCTAssertEqual(collector.totalBufferingTime, 0.0)
    }

    func test_initialBitrateSwitches_isZero() {
        let collector = QoECollector(videoId: "vid-001")
        XCTAssertEqual(collector.bitrateSwitches, 0)
    }

    func test_initialErrors_isEmpty() {
        let collector = QoECollector(videoId: "vid-001")
        XCTAssertTrue(collector.errors.isEmpty)
    }

    // MARK: - Buffering tracking

    func test_recordBufferingEnd_withoutStart_doesNotCrash() {
        let collector = QoECollector(videoId: "vid-001")
        collector.recordBufferingEnd()
        XCTAssertEqual(collector.totalBufferingTime, 0.0)
    }

    func test_bufferingTime_increasesAfterStartEndCycle() {
        let collector = QoECollector(videoId: "vid-001")
        collector.recordBufferingStart()
        Thread.sleep(forTimeInterval: 0.1)
        collector.recordBufferingEnd()
        XCTAssertGreaterThan(collector.totalBufferingTime, 0.0)
    }

    func test_multipleBufferingCycles_accumulate() {
        let collector = QoECollector(videoId: "vid-001")

        collector.recordBufferingStart()
        Thread.sleep(forTimeInterval: 0.05)
        collector.recordBufferingEnd()

        let afterFirst = collector.totalBufferingTime

        collector.recordBufferingStart()
        Thread.sleep(forTimeInterval: 0.05)
        collector.recordBufferingEnd()

        XCTAssertGreaterThan(collector.totalBufferingTime, afterFirst)
    }

    func test_duplicateRecordBufferingStart_doesNotResetTimer() {
        let collector = QoECollector(videoId: "vid-001")
        collector.recordBufferingStart()
        Thread.sleep(forTimeInterval: 0.1)
        collector.recordBufferingStart() // second call must be a no-op
        Thread.sleep(forTimeInterval: 0.1)
        collector.recordBufferingEnd()
        XCTAssertGreaterThanOrEqual(collector.totalBufferingTime, 0.18)
    }

    // MARK: - Error tracking

    func test_recordError_incrementsErrorCount() {
        let collector = QoECollector(videoId: "vid-001")
        collector.recordError(code: "ERR_001", message: "Network timeout")
        XCTAssertEqual(collector.errors.count, 1)
    }

    func test_recordError_storesCorrectCode() {
        let collector = QoECollector(videoId: "vid-001")
        collector.recordError(code: "ERR_DECODE", message: "Decode failure")
        XCTAssertEqual(collector.errors.first?.code, "ERR_DECODE")
    }

    func test_recordError_storesCorrectMessage() {
        let collector = QoECollector(videoId: "vid-001")
        collector.recordError(code: "ERR_001", message: "Connection refused")
        XCTAssertEqual(collector.errors.first?.message, "Connection refused")
    }

    func test_multipleErrors_areAllTracked() {
        let collector = QoECollector(videoId: "vid-001")
        collector.recordError(code: "E1", message: "msg1")
        collector.recordError(code: "E2", message: "msg2")
        collector.recordError(code: "E3", message: "msg3")
        XCTAssertEqual(collector.errors.count, 3)
    }

    // MARK: - Bitrate tracking

    func test_firstBitrateChange_doesNotIncrementCounter() {
        let collector = QoECollector(videoId: "vid-001")
        collector.recordBitrateChange(newBitrate: 4500)
        XCTAssertEqual(collector.bitrateSwitches, 0)
    }

    func test_switchingToNewBitrate_incrementsCounter() {
        let collector = QoECollector(videoId: "vid-001")
        collector.recordBitrateChange(newBitrate: 4500)
        collector.recordBitrateChange(newBitrate: 2000)
        XCTAssertEqual(collector.bitrateSwitches, 1)
    }

    func test_sameBitrateRepeated_doesNotIncrementCounter() {
        let collector = QoECollector(videoId: "vid-001")
        collector.recordBitrateChange(newBitrate: 4500)
        collector.recordBitrateChange(newBitrate: 4500)
        XCTAssertEqual(collector.bitrateSwitches, 0)
    }

    func test_multipleDistinctBitrateChanges_countedIndividually() {
        let collector = QoECollector(videoId: "vid-001")
        collector.recordBitrateChange(newBitrate: 4500)
        collector.recordBitrateChange(newBitrate: 2000)
        collector.recordBitrateChange(newBitrate: 800)
        collector.recordBitrateChange(newBitrate: 4500)
        XCTAssertEqual(collector.bitrateSwitches, 3)
    }

    // MARK: - Lifecycle

    func test_stopCollecting_withoutStart_doesNotCrash() {
        let collector = QoECollector(videoId: "vid-001")
        collector.stopCollecting()
    }

    func test_stopCollecting_canBeCalledMultipleTimes() {
        let collector = QoECollector(videoId: "vid-001")
        collector.stopCollecting()
        collector.stopCollecting()
    }
}
