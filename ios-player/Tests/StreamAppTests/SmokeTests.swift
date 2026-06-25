import XCTest
@testable import StreamApp

final class SmokeTests: XCTestCase {
#if os(iOS)
    func test_player_view_controller_initializes_with_url() {
        let vc = VideoPlayerViewController()
        vc.videoURL = URL(string: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
        vc.loadViewIfNeeded()
        XCTAssertNotNil(vc.view)
    }
#endif

    func test_catalog_titles_are_non_empty() {
        for video in VIDEO_CATALOG {
            XCTAssertFalse(video.title.isEmpty)
            XCTAssertFalse(video.subtitle.isEmpty)
        }
    }
}
