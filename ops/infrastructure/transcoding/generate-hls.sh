#!/bin/bash

# Generate HLS streams from input video
# Usage: ./generate-hls.sh input.mp4 output-dir

INPUT=$1
OUTPUT_DIR=$2

if [ -z "$INPUT" ] || [ -z "$OUTPUT_DIR" ]; then
    echo "Usage: $0 <input-video> <output-dir>"
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

# Generate multiple bitrate HLS streams
ffmpeg -i "$INPUT" \
    -c:v libx264 -c:a aac \
    -hls_time 10 -hls_playlist_type vod \
    -hls_segment_filename "$OUTPUT_DIR/segment_%03d.ts" \
    -master_pl_name master.m3u8 \
    -var_stream_map "v:0,a:0 v:1,a:1 v:2,a:2" \
    -map 0:v -map 0:a \
    -b:v:0 5M -b:v:1 2.5M -b:v:2 1M \
    -s:v:0 1920x1080 -s:v:1 1280x720 -s:v:2 854x480 \
    "$OUTPUT_DIR/stream_%v.m3u8"

echo "HLS streams generated in $OUTPUT_DIR"
