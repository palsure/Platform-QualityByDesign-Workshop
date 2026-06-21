import XCTest
@testable import QoePlayer

/// Unit tests for the video catalog — pure data, no network or AVFoundation.
final class VideoCatalogTests: XCTestCase {

    // MARK: - Catalog content

    func test_catalogIsNotEmpty() {
        XCTAssertFalse(VIDEO_CATALOG.isEmpty, "Video catalog must not be empty")
    }

    func test_catalogContainsThreeVideos() {
        XCTAssertEqual(VIDEO_CATALOG.count, 3)
    }

    func test_allVideosHaveNonBlankIds() {
        VIDEO_CATALOG.forEach { video in
            XCTAssertFalse(video.id.isEmpty, "Video id must not be blank: \(video)")
        }
    }

    func test_allVideosHaveNonBlankTitles() {
        VIDEO_CATALOG.forEach { video in
            XCTAssertFalse(video.title.isEmpty, "Video title must not be blank: \(video)")
        }
    }

    func test_allVideosHaveNonBlankHLSUrls() {
        VIDEO_CATALOG.forEach { video in
            XCTAssertFalse(video.hlsUrl.isEmpty, "HLS URL must not be blank: \(video)")
        }
    }

    func test_allHLSUrlsUseHttps() {
        VIDEO_CATALOG.forEach { video in
            XCTAssertTrue(
                video.hlsUrl.hasPrefix("https://"),
                "HLS URL must use HTTPS: \(video.hlsUrl)"
            )
        }
    }

    func test_allHLSUrlsPointToM3u8() {
        VIDEO_CATALOG.forEach { video in
            XCTAssertTrue(
                video.hlsUrl.hasSuffix(".m3u8"),
                "HLS URL must reference an .m3u8 playlist: \(video.hlsUrl)"
            )
        }
    }

    // MARK: - IDs are unique

    func test_catalogVideoIdsAreUnique() {
        let ids = VIDEO_CATALOG.map { $0.id }
        XCTAssertEqual(ids.count, Set(ids).count, "Video IDs must be unique")
    }

    func test_catalogHLSUrlsAreUnique() {
        let urls = VIDEO_CATALOG.map { $0.hlsUrl }
        XCTAssertEqual(urls.count, Set(urls).count, "HLS URLs must be unique")
    }

    // MARK: - findVideo helper

    func test_findVideo_returnsMatchingVideo() {
        let video = findVideo(id: "baseline")
        XCTAssertNotNil(video)
        XCTAssertEqual(video?.id, "baseline")
    }

    func test_findVideo_returnsNilForUnknownId() {
        XCTAssertNil(findVideo(id: "does-not-exist"))
    }

    func test_findVideo_returnsCorrectEntryForEachCatalogItem() {
        VIDEO_CATALOG.forEach { expected in
            let actual = findVideo(id: expected.id)
            XCTAssertNotNil(actual, "findVideo must find '\(expected.id)'")
            XCTAssertEqual(actual, expected)
        }
    }

    func test_baselineVideoHasExpectedHLSUrl() {
        let video = findVideo(id: "baseline")
        XCTAssertEqual(video?.hlsUrl, "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
    }

    func test_appleAdvancedVideoHasExpectedTitle() {
        let video = findVideo(id: "apple-advanced")
        XCTAssertEqual(video?.title, "Apple Advanced HLS")
    }

    // MARK: - Equatable

    func test_catalogVideoEquality() {
        let v1 = CatalogVideo(id: "x", title: "T", subtitle: "S", hlsUrl: "https://x.com/a.m3u8")
        let v2 = CatalogVideo(id: "x", title: "T", subtitle: "S", hlsUrl: "https://x.com/a.m3u8")
        XCTAssertEqual(v1, v2)
    }

    func test_catalogVideoInequality() {
        let v1 = CatalogVideo(id: "x", title: "T", subtitle: "S", hlsUrl: "https://x.com/a.m3u8")
        let v2 = CatalogVideo(id: "y", title: "T", subtitle: "S", hlsUrl: "https://x.com/a.m3u8")
        XCTAssertNotEqual(v1, v2)
    }
}
