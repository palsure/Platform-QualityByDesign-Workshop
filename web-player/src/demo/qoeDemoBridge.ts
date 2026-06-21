import type { DemoScenarioId } from './scenarios';

// ── HLS quality level ─────────────────────────────────────────────────────────

export interface HlsLevel {
  index:   number;
  bitrate: number;   // bits/s
  width:   number;
  height:  number;
  /** Human-readable label e.g. "720p (2500 kbps)" */
  name:    string;
}

// ── Snapshot (polled by Playwright via window.__QOE_DEMO__.getSnapshot()) ─────

export interface QoeDemoSnapshot {
  // ── Scenario ────────────────────────────────────────────────────
  scenario:             DemoScenarioId;

  // ── QoE collector metrics ────────────────────────────────────────
  timeToFirstFrameMs:   number | null;
  totalBufferingTime:   number;   // seconds
  bufferingEventsCount: number;
  bitrateSwitches:      number;
  lastBitrate:          number | null;  // bits/s
  errorCount:           number;
  visualBlackoutActive: boolean;

  // ── Native video element state ───────────────────────────────────
  currentTime:          number;   // seconds
  duration:             number;   // seconds (NaN until metadata loaded)
  isPaused:             boolean;
  isEnded:              boolean;
  isSeeking:            boolean;

  // ── HLS.js adaptive bitrate state ────────────────────────────────
  /** All available quality levels. Empty until manifest is parsed. */
  hlsLevels:            HlsLevel[];
  /**
   * Index of the currently playing level.
   *  -1 = auto ABR has not yet locked to a level
   *   0 = lowest quality
   *   n = highest quality (hlsLevels.length - 1)
   */
  currentLevelIndex:    number;
  /**
   * HLS.js bandwidth estimate in bits/s.
   * 0 until the first segment has been downloaded.
   */
  bandwidthEstimate:    number;
}

// ── Bridge interface (registered on window.__QOE_DEMO__) ─────────────────────

export interface QoeDemoBridge {
  scenario:    DemoScenarioId;

  /** Read the latest player + QoE state. */
  getSnapshot: () => QoeDemoSnapshot;

  // ── Playback controls ────────────────────────────────────────────
  /** Start or resume playback. Returns the play() Promise. */
  play:        () => Promise<void>;
  /** Pause playback. */
  pause:       () => void;
  /**
   * Seek to an absolute position.
   * @param seconds Target time in seconds. Clamped to [0, duration].
   */
  seek:        (seconds: number) => void;

  // ── ABR controls ─────────────────────────────────────────────────
  /**
   * Force a specific quality level.
   * @param levelIndex Index into hlsLevels. Pass -1 to restore auto ABR.
   */
  setLevel:    (levelIndex: number) => void;
}

// ── Global declaration ────────────────────────────────────────────────────────

declare global {
  interface Window {
    __QOE_DEMO__?: QoeDemoBridge;
  }
}

// ── Registration helpers ──────────────────────────────────────────────────────

export function registerQoeDemoBridge(bridge: QoeDemoBridge): void {
  window.__QOE_DEMO__ = bridge;
}

export function unregisterQoeDemoBridge(): void {
  delete window.__QOE_DEMO__;
}
