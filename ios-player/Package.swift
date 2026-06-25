// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "StreamApp",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(
            name: "StreamApp",
            targets: ["StreamApp"])
    ],
    dependencies: [],
    targets: [
        .target(
            name: "StreamApp",
            dependencies: []),
        .testTarget(
            name: "StreamAppTests",
            dependencies: ["StreamApp"])
    ]
)
