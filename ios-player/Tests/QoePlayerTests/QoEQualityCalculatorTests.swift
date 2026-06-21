import XCTest
@testable import QoePlayer

/// Unit tests for QoEQualityCalculator.
/// These tests are pure logic — no network, no AVFoundation, no UIKit.
final class QoEQualityCalculatorTests: XCTestCase {

    // MARK: - Excellent

    func test_excellent_zeroBufferingNoErrors() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 0.0, errorCount: 0), "excellent")
    }

    func test_excellent_oneSecondBufferingNoErrors() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 1.0, errorCount: 0), "excellent")
    }

    func test_excellent_justBelowTwoSecondThreshold() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 1.999, errorCount: 0), "excellent")
    }

    // MARK: - Good

    func test_good_exactlyTwoSeconds() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 2.0, errorCount: 0), "good")
    }

    func test_good_zeroBufferingOneError() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 0.0, errorCount: 1), "good")
    }

    func test_good_threeSecondsOneError() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 3.0, errorCount: 1), "good")
    }

    func test_good_justBelowFiveSeconds() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 4.999, errorCount: 0), "good")
    }

    // MARK: - Fair

    func test_fair_exactlyFiveSeconds() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 5.0, errorCount: 0), "fair")
    }

    func test_fair_zeroBufferingTwoErrors() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 0.0, errorCount: 2), "fair")
    }

    func test_fair_sevenSecondsThreeErrors() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 7.0, errorCount: 3), "fair")
    }

    func test_fair_justBelowTenSeconds() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 9.999, errorCount: 4), "fair")
    }

    // MARK: - Poor

    func test_poor_exactlyTenSeconds() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 10.0, errorCount: 0), "poor")
    }

    func test_poor_zeroBufferingFiveErrors() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 0.0, errorCount: 5), "poor")
    }

    func test_poor_highBufferingHighErrors() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 999.0, errorCount: 100), "poor")
    }

    // MARK: - Edge cases

    func test_negativeBufferingTreatedAsExcellent() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: -1.0, errorCount: 0), "excellent")
    }

    func test_fiveErrorsMakesPoorRegardlessOfBuffering() {
        XCTAssertEqual(QoEQualityCalculator.calculate(totalBufferingTime: 0.0, errorCount: 5), "poor")
    }
}
