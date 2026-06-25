import XCTest
@testable import StreamApp

final class BATTests: XCTestCase {
    func test_catalog_has_playable_entries() {
        XCTAssertFalse(VIDEO_CATALOG.isEmpty)
        XCTAssertEqual(VIDEO_CATALOG.count, 3)
    }

    func test_catalog_urls_are_https_hls() {
        for video in VIDEO_CATALOG {
            XCTAssertTrue(video.hlsUrl.hasPrefix("https://"))
            XCTAssertTrue(video.hlsUrl.contains(".m3u8"))
        }
    }

    func test_find_video_by_id() {
        XCTAssertNotNil(findVideo(id: "baseline"))
        XCTAssertNil(findVideo(id: "missing"))
    }
}
