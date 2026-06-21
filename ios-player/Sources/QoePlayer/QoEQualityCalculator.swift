import Foundation

/// Pure quality-score computation — no UIKit or AVFoundation dependencies.
/// Extracted so both the library and test suite can reference the same logic.
public enum QoEQualityCalculator {
    /// Returns a human-readable quality label based on accumulated buffering
    /// time and playback error count.
    ///
    /// - Parameters:
    ///   - totalBufferingTime: Accumulated buffering duration in seconds.
    ///   - errorCount: Number of playback errors recorded in this session.
    /// - Returns: One of `"excellent"`, `"good"`, `"fair"`, or `"poor"`.
    public static func calculate(totalBufferingTime: Double, errorCount: Int) -> String {
        switch (totalBufferingTime, errorCount) {
        case (..<2.0, 0):
            return "excellent"
        case (..<5.0, ..<2):
            return "good"
        case (..<10.0, ..<5):
            return "fair"
        default:
            return "poor"
        }
    }
}
