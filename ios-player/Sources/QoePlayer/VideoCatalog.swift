import Foundation

public struct CatalogVideo: Equatable, Identifiable {
    public let id: String
    public let title: String
    public let subtitle: String
    public let hlsUrl: String

    public init(id: String, title: String, subtitle: String, hlsUrl: String) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.hlsUrl = hlsUrl
    }
}

public let VIDEO_CATALOG: [CatalogVideo] = [
    CatalogVideo(
        id: "baseline",
        title: "Baseline Stream",
        subtitle: "Multi-bitrate HLS demo",
        hlsUrl: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    ),
    CatalogVideo(
        id: "apple-advanced",
        title: "Apple Advanced HLS",
        subtitle: "fMP4 fragmented stream",
        hlsUrl: "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
    ),
    CatalogVideo(
        id: "apple-basic",
        title: "Apple Basic HLS",
        subtitle: "Classic MPEG-TS stream",
        hlsUrl: "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8"
    ),
]

public func findVideo(id: String) -> CatalogVideo? {
    VIDEO_CATALOG.first { $0.id == id }
}
