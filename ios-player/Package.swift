// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "QoePlayer",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(
            name: "QoePlayer",
            targets: ["QoePlayer"])
    ],
    dependencies: [],
    targets: [
        .target(
            name: "QoePlayer",
            dependencies: []),
        .testTarget(
            name: "QoePlayerTests",
            dependencies: ["QoePlayer"])
    ]
)
