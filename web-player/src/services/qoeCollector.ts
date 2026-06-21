import axios from 'axios';
import type { QoEMetricPayload } from '../../../ops/shared/schema/qoe-metrics.types';
import { recordPageAction } from './newrelic';

/** Same-origin `/api/v1`: Vite dev server proxies `/api` to Spring; Docker web-player nginx does the same. Override with `VITE_API_URL` if needed. */
const API_BASE_URL = import.meta.env.VITE_API_URL || '/api/v1';

export interface QoECollectorOptions {
  /** Skip HTTP ingest (for CI probes without the Spring API). */
  silent?: boolean;
}

export class QoECollector {
  private sessionId: string;
  private videoId: string;
  private silent: boolean;
  private bufferingEvents: Array<{ startTime: number; endTime?: number; duration: number }> = [];
  private errors: Array<{ code: string; message: string; timestamp: number }> = [];
  private playbackRequestMark: number | null = null;
  private firstFrameMark: number | null = null;
  private lastBufferingStart: number | null = null;
  totalBufferingTime = 0;
  private bitrateSwitches = 0;
  lastBitrate: number | null = null;
  private framesDropped = 0;
  private framesRendered = 0;

  constructor(videoId: string, options?: QoECollectorOptions) {
    this.videoId = videoId;
    this.silent = options?.silent ?? false;
    this.sessionId = `web-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
  }

  /**
   * First moment the QoE probe considers the session started (e.g. player mounted).
   * Call once per session so startup-delay scenarios include intentional latency.
   */
  markPlaybackRequested() {
    if (this.playbackRequestMark != null) return;
    this.playbackRequestMark = performance.now();
  }

  /** First decoded frame / playback start from the probe's perspective. */
  markFirstFrameRendered() {
    if (this.firstFrameMark != null || this.playbackRequestMark == null) return;
    this.firstFrameMark = performance.now();
  }

  getTimeToFirstFrameMs(): number | null {
    if (this.playbackRequestMark == null || this.firstFrameMark == null) return null;
    return this.firstFrameMark - this.playbackRequestMark;
  }

  recordBufferingStart() {
    if (this.lastBufferingStart === null) {
      this.lastBufferingStart = Date.now();
    }
  }

  recordBufferingEnd() {
    if (this.lastBufferingStart === null) return;
    const endMs = Date.now();
    const startMs = this.lastBufferingStart;
    const duration = (endMs - startMs) / 1000;
    this.totalBufferingTime += duration;
    this.bufferingEvents.push({
      startTime: startMs / 1000,
      endTime: endMs / 1000,
      duration,
    });
    this.lastBufferingStart = null;
  }

  recordError(code: string, message: string) {
    this.errors.push({
      code,
      message,
      timestamp: Date.now() / 1000,
    });
  }

  recordBitrateChange(newBitrate: number) {
    if (this.lastBitrate !== null && this.lastBitrate !== newBitrate) {
      this.bitrateSwitches++;
    }
    this.lastBitrate = newBitrate;
  }

  recordFrameDrop() {
    this.framesDropped++;
  }

  recordFrameRender() {
    this.framesRendered++;
  }

  getBufferingEventsCount(): number {
    return this.bufferingEvents.length;
  }

  getBitrateSwitches(): number {
    return this.bitrateSwitches;
  }

  getErrorCount(): number {
    return this.errors.length;
  }

  async sendMetrics(metrics: {
    playbackState: string;
    currentTime: number;
    duration: number;
    currentBitrate?: number;
    currentResolution?: string;
    networkSpeed?: number;
    playbackQuality?: string;
  }) {
    if (this.silent) return;

    const startupMs = this.getTimeToFirstFrameMs();

    const payload: QoEMetricPayload = {
      platform: 'web',
      videoId: this.videoId,
      sessionId: this.sessionId,
      timestamp: new Date().toISOString(),
      deviceInfo: {
        deviceType: this.getDeviceType(),
        os: navigator.platform,
        browser: this.getBrowser(),
        screenResolution: `${screen.width}x${screen.height}`,
      },
      metrics: {
        playbackState: metrics.playbackState as QoEMetricPayload['metrics']['playbackState'],
        currentTime: metrics.currentTime,
        duration: metrics.duration,
        bufferingEvents: this.bufferingEvents,
        totalBufferingTime: this.totalBufferingTime,
        startupTime: startupMs != null ? Math.round(startupMs) : undefined,
        currentBitrate: metrics.currentBitrate,
        currentResolution: metrics.currentResolution,
        bitrateSwitches: this.bitrateSwitches,
        errors: this.errors,
        errorCount: this.errors.length,
        framesDropped: this.framesDropped,
        framesRendered: this.framesRendered,
        networkSpeed: metrics.networkSpeed,
        playbackQuality: metrics.playbackQuality as QoEMetricPayload['metrics']['playbackQuality'],
      },
    };

    try {
      await axios.post(`${API_BASE_URL}/metrics`, payload);
    } catch (error) {
      console.error('Failed to send QoE metrics:', error);
    }

    // Mirror the metric to New Relic Insights so we can slice by browser/device
    // without the API path being available (e.g. when the backend is down).
    // Only scalar attributes — Insights drops strings > 4096 chars and arrays
    // are flattened to "[object Object]" otherwise.
    recordPageAction('QoEMetric', {
      platform:           payload.platform,
      videoId:            payload.videoId,
      sessionId:          payload.sessionId,
      playbackState:      metrics.playbackState,
      playbackQuality:    metrics.playbackQuality ?? 'unknown',
      currentTime:        metrics.currentTime,
      duration:           metrics.duration,
      totalBufferingTime: this.totalBufferingTime,
      bufferingEvents:    this.bufferingEvents.length,
      bitrateSwitches:    this.bitrateSwitches,
      currentBitrate:     metrics.currentBitrate ?? 0,
      currentResolution:  metrics.currentResolution ?? '',
      errorCount:         this.errors.length,
      framesDropped:      this.framesDropped,
      framesRendered:     this.framesRendered,
      startupTimeMs:      startupMs ?? 0,
    });
  }

  private getDeviceType(): string {
    const width = screen.width;
    if (width < 768) return 'mobile';
    if (width < 1024) return 'tablet';
    return 'desktop';
  }

  private getBrowser(): string {
    const ua = navigator.userAgent;
    if (ua.includes('Chrome')) return 'Chrome';
    if (ua.includes('Firefox')) return 'Firefox';
    if (ua.includes('Safari')) return 'Safari';
    if (ua.includes('Edge')) return 'Edge';
    return 'Unknown';
  }
}
