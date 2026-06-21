package com.devopsdays.qoe.api.exceptions;

public class VideoNotFoundException extends RuntimeException {
    public VideoNotFoundException(String videoId) {
        super("Video not found: " + videoId);
    }
}
