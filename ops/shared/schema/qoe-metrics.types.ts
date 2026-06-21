/**
 * QoE Metrics TypeScript Types
 * Shared type definitions for Quality of Experience metrics
 */

export type Platform = 'web' | 'ios' | 'android' | 'roku' | 'apple-tv' | 'android-tv';

export type PlaybackState = 'playing' | 'paused' | 'buffering' | 'ended' | 'error';

export type PlaybackQuality = 'excellent' | 'good' | 'fair' | 'poor';

export interface DeviceInfo {
  deviceType?: string;
  os?: string;
  browser?: string;
  screenResolution?: string;
}

export interface BufferingEvent {
  startTime: number;
  endTime?: number;
  duration: number;
}

export interface PlaybackError {
  code: string;
  message: string;
  timestamp: number;
}

export interface QoEMetrics {
  playbackState: PlaybackState;
  currentTime: number;
  duration: number;
  bufferingEvents?: BufferingEvent[];
  totalBufferingTime?: number;
  startupTime?: number;
  currentBitrate?: number;
  currentResolution?: string;
  bitrateSwitches?: number;
  errors?: PlaybackError[];
  errorCount?: number;
  framesDropped?: number;
  framesRendered?: number;
  networkSpeed?: number;
  playbackQuality?: PlaybackQuality;
}

export interface QoEMetricPayload {
  platform: Platform;
  videoId: string;
  sessionId: string;
  timestamp: string;
  deviceInfo?: DeviceInfo;
  metrics: QoEMetrics;
}

export interface QoEMetricSummary {
  platform: Platform;
  videoId: string;
  sessionId: string;
  totalSessions: number;
  averageStartupTime: number;
  averageBufferingTime: number;
  totalErrors: number;
  averageBitrate: number;
  qualityScore: number;
}

export interface QoETrend {
  timestamp: string;
  platform: Platform;
  averageStartupTime: number;
  averageBufferingTime: number;
  errorRate: number;
  averageBitrate: number;
}
