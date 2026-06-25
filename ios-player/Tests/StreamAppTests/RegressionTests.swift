import XCTest
@testable import StreamApp

final class RegressionTests: XCTestCase {
    func test_catalog_ids_are_unique() {
        let ids = VIDEO_CATALOG.map(\.id)
        XCTAssertEqual(ids.count, Set(ids).count)
    }

    func test_catalog_lookup_is_case_sensitive() {
        XCTAssertNotNil(findVideo(id: "baseline"))
        XCTAssertNil(findVideo(id: "Baseline"))
    }

#if os(iOS)
    func test_player_view_controller_accepts_custom_video_id() {
        let vc = VideoPlayerViewController()
        vc.videoId = "regression-demo"
        XCTAssertEqual(vc.videoId, "regression-demo")
    }
#endif
}
