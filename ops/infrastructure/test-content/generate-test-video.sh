#!/bin/bash

# Generate a test video using FFmpeg
# Usage: ./generate-test-video.sh output.mp4 duration

OUTPUT=${1:-test-video.mp4}
DURATION=${2:-30}

ffmpeg -f lavfi -i testsrc=duration=$DURATION:size=1920x1080:rate=30 \
    -f lavfi -i sine=frequency=1000:duration=$DURATION \
    -c:v libx264 -preset medium -crf 23 \
    -c:a aac -b:a 192k \
    -pix_fmt yuv420p \
    "$OUTPUT"

echo "Test video generated: $OUTPUT"
