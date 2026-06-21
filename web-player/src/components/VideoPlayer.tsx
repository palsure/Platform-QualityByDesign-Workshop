import { useCallback, useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import { QoECollector } from '../services/qoeCollector';
import type { DemoScenarioId } from '../demo/scenarios';
import { registerQoeDemoBridge, unregisterQoeDemoBridge } from '../demo/qoeDemoBridge';

interface VideoPlayerProps {
  videoUrl: string;
  videoId: string;
  scenario: DemoScenarioId;
  /** Used by Playwright to avoid clicking Play. */
  autoPlayForE2E?: boolean;
  /** Show debug probe snapshot panel (off by default). */
  showQoePanel?: boolean;
}

const STARTUP_DELAY_MS = 2800;
const BLACKOUT_START_MS = 3500;
const BLACKOUT_DURATION_MS = 1600;
const MID_REBUFFER_AT_MS = 4500;
const MID_REBUFFER_HOLD_MS = 2200;
const BITRATE_STEP_AT_MS = 2000;

export function VideoPlayer({ videoUrl, videoId, scenario, autoPlayForE2E, showQoePanel = false }: VideoPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const qoeCollectorRef = useRef<QoECollector | null>(null);
  const visualBlackoutRef = useRef(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [isBuffering, setIsBuffering] = useState(false);
  const [visualBlackoutActive, setVisualBlackoutActive] = useState(false);
  const [, setProbeRenderTick] = useState(0);
  const scenarioRef = useRef(scenario);
  scenarioRef.current = scenario;

  useEffect(() => {
    const id = window.setInterval(() => setProbeRenderTick((n) => n + 1), 500);
    return () => clearInterval(id);
  }, []);

  const setBlackout = (active: boolean) => {
    visualBlackoutRef.current = active;
    setVisualBlackoutActive(active);
  };

  const silent =
    import.meta.env.VITE_QOE_SILENT === 'true' || import.meta.env.VITE_QOE_SILENT === '1';

  const calculateQuality = useCallback(
    (_video: HTMLVideoElement, collector: QoECollector): 'excellent' | 'good' | 'fair' | 'poor' => {
      if (collector.totalBufferingTime < 2 && collector.getErrorCount() === 0) {
        return 'excellent';
      }
      if (collector.totalBufferingTime < 5 && collector.getErrorCount() < 2) {
        return 'good';
      }
      if (collector.totalBufferingTime < 10 && collector.getErrorCount() < 5) {
        return 'fair';
      }
      return 'poor';
    },
    []
  );

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    qoeCollectorRef.current = new QoECollector(videoId, { silent });
    qoeCollectorRef.current.markPlaybackRequested();

    let cancelled = false;
    const timers: number[] = [];
    let firstFrameHandled = false;
    let blackoutTimer: number | undefined;
    let blackoutClearTimer: number | undefined;
    let rebufferTimer: number | undefined;
    let bitrateTimer: number | undefined;

    const scheduleScenarioSideEffects = (hls: Hls) => {
      const sc = scenarioRef.current;

      if (sc === 'black_screen_pulse') {
        blackoutTimer = window.setTimeout(() => {
          if (cancelled) return;
          setBlackout(true);
          blackoutClearTimer = window.setTimeout(() => {
            if (!cancelled) setBlackout(false);
          }, BLACKOUT_DURATION_MS);
        }, BLACKOUT_START_MS);
      }

      if (sc === 'forced_mid_play_rebuffer') {
        rebufferTimer = window.setTimeout(() => {
          if (cancelled) return;
          hls.stopLoad();
          window.setTimeout(() => {
            if (!cancelled) hls.startLoad(-1);
          }, MID_REBUFFER_HOLD_MS);
        }, MID_REBUFFER_AT_MS);
      }

      if (sc === 'bitrate_step_down' && hls.levels.length > 1) {
        bitrateTimer = window.setTimeout(() => {
          if (cancelled) return;
          hls.nextLevel = 0;
          window.setTimeout(() => {
            if (!cancelled) hls.nextLevel = hls.levels.length - 1;
          }, 2500);
        }, BITRATE_STEP_AT_MS);
      }
    };

    const attachHls = () => {
      if (!Hls.isSupported()) return;

      const hls = new Hls({
        enableWorker: true,
        lowLatencyMode: false,
      });

      hlsRef.current = hls;

      const beginLoad = () => {
        hls.loadSource(videoUrl);
        hls.attachMedia(video);
      };

      if (scenarioRef.current === 'startup_delay') {
        timers.push(window.setTimeout(beginLoad, STARTUP_DELAY_MS));
      } else {
        beginLoad();
      }

      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        scheduleScenarioSideEffects(hls);
        if (autoPlayForE2E) {
          void video.play().catch(() => undefined);
        }
      });

      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (data.fatal) {
          qoeCollectorRef.current?.recordError(
            'HLS_ERROR',
            data.error?.message ?? String(data.details) ?? 'Unknown HLS error'
          );
        }
      });

      hls.on(Hls.Events.LEVEL_SWITCHED, (_event, data) => {
        const level = hls.levels[data.level];
        if (level) {
          qoeCollectorRef.current?.recordBitrateChange(level.bitrate);
        }
      });
    };

    if (Hls.isSupported()) {
      attachHls();
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      const beginNative = () => {
        video.src = videoUrl;
      };
      if (scenarioRef.current === 'startup_delay') {
        timers.push(window.setTimeout(beginNative, STARTUP_DELAY_MS));
      } else {
        beginNative();
      }
    }

    const handleWaiting = () => {
      setIsBuffering(true);
      qoeCollectorRef.current?.recordBufferingStart();
    };

    const handlePlaying = () => {
      setIsBuffering(false);
      qoeCollectorRef.current?.recordBufferingEnd();
      if (!firstFrameHandled) {
        firstFrameHandled = true;
        qoeCollectorRef.current?.markFirstFrameRendered();
        setProbeRenderTick((n) => n + 1);
      }
    };

    const handleTimeUpdate = () => {
      setCurrentTime(video.currentTime);
    };

    const handleLoadedMetadata = () => {
      setDuration(video.duration);
    };

    const handleError = () => {
      qoeCollectorRef.current?.recordError('PLAYBACK_ERROR', 'Video playback error');
    };

    video.addEventListener('waiting', handleWaiting);
    video.addEventListener('playing', handlePlaying);
    video.addEventListener('timeupdate', handleTimeUpdate);
    video.addEventListener('loadedmetadata', handleLoadedMetadata);
    video.addEventListener('error', handleError);

    const metricsInterval = window.setInterval(() => {
      const collector = qoeCollectorRef.current;
      if (!collector || !video) return;
      const hls = hlsRef.current;
      const currentLevel = hls?.currentLevel;
      const level = currentLevel !== undefined && currentLevel >= 0 ? hls?.levels[currentLevel] : null;
      const paused = video.paused;
      const playbackState = paused ? 'paused' : video.seeking ? 'buffering' : 'playing';

      void collector.sendMetrics({
        playbackState,
        currentTime: video.currentTime,
        duration: video.duration || 0,
        currentBitrate: level?.bitrate,
        currentResolution: level ? `${level.width}x${level.height}` : undefined,
        playbackQuality: calculateQuality(video, collector),
      });
    }, 5000);

    registerQoeDemoBridge({
      scenario,

      getSnapshot: () => {
        const collector = qoeCollectorRef.current;
        const v         = videoRef.current;
        const hls       = hlsRef.current;

        return {
          // QoE collector
          scenario,
          timeToFirstFrameMs:   collector?.getTimeToFirstFrameMs() ?? null,
          totalBufferingTime:   collector?.totalBufferingTime ?? 0,
          bufferingEventsCount: collector?.getBufferingEventsCount() ?? 0,
          bitrateSwitches:      collector?.getBitrateSwitches() ?? 0,
          lastBitrate:          collector?.lastBitrate ?? null,
          errorCount:           collector?.getErrorCount() ?? 0,
          visualBlackoutActive: visualBlackoutRef.current,

          // Native video element
          currentTime: v?.currentTime ?? 0,
          duration:    v?.duration ?? 0,
          isPaused:    v?.paused  ?? true,
          isEnded:     v?.ended   ?? false,
          isSeeking:   v?.seeking ?? false,

          // HLS.js ABR state
          hlsLevels: hls?.levels.map((l, i) => ({
            index:   i,
            bitrate: l.bitrate,
            width:   l.width  ?? 0,
            height:  l.height ?? 0,
            name:    `${l.height ?? '?'}p (${Math.round(l.bitrate / 1000)} kbps)`,
          })) ?? [],
          currentLevelIndex: hls?.currentLevel ?? -1,
          bandwidthEstimate: hls?.bandwidthEstimate ?? 0,
        };
      },

      // ── Playback controls ────────────────────────────────────────
      play:  () => videoRef.current?.play() ?? Promise.resolve(),
      pause: () => videoRef.current?.pause(),
      seek:  (seconds: number) => {
        const v = videoRef.current;
        if (!v) return;
        const dur = isFinite(v.duration) ? v.duration : Infinity;
        v.currentTime = Math.max(0, Math.min(seconds, dur));
      },

      // ── ABR controls ─────────────────────────────────────────────
      setLevel: (levelIndex: number) => {
        const hls = hlsRef.current;
        if (hls) hls.currentLevel = levelIndex;
      },
    });

    return () => {
      cancelled = true;
      clearInterval(metricsInterval);
      timers.forEach(clearTimeout);
      if (blackoutTimer) clearTimeout(blackoutTimer);
      if (blackoutClearTimer) clearTimeout(blackoutClearTimer);
      if (rebufferTimer) clearTimeout(rebufferTimer);
      if (bitrateTimer) clearTimeout(bitrateTimer);
      hlsRef.current?.destroy();
      hlsRef.current = null;
      video.removeEventListener('waiting', handleWaiting);
      video.removeEventListener('playing', handlePlaying);
      video.removeEventListener('timeupdate', handleTimeUpdate);
      video.removeEventListener('loadedmetadata', handleLoadedMetadata);
      video.removeEventListener('error', handleError);
      unregisterQoeDemoBridge();
      setBlackout(false);
    };
  }, [videoUrl, videoId, scenario, silent, calculateQuality, autoPlayForE2E]);

  const togglePlay = () => {
    const video = videoRef.current;
    if (!video) return;

    if (video.paused) {
      void video.play();
      setIsPlaying(true);
    } else {
      video.pause();
      setIsPlaying(false);
    }
  };

  const snapshotJson = () => {
    try {
      return JSON.stringify(window.__QOE_DEMO__?.getSnapshot() ?? {}, null, 2);
    } catch {
      return '{}';
    }
  };

  return (
    <div className="video-player">
      <div
        className="video-stage"
        style={{ position: 'relative', display: 'inline-block', width: '100%', maxWidth: '800px' }}
      >
        <video ref={videoRef} className="video-element" controls style={{ width: '100%', display: 'block' }} />
        {visualBlackoutActive && (
          <div
            data-testid="visual-blackout-overlay"
            className="qoe-blackout-overlay"
            style={{
              position: 'absolute',
              inset: 0,
              background: '#000',
              pointerEvents: 'none',
            }}
            aria-hidden
          />
        )}
      </div>
      <div className="controls">
        <button type="button" onClick={togglePlay}>
          {isPlaying ? 'Pause' : 'Play'}
        </button>
        <span>
          {Math.floor(currentTime)}s / {Math.floor(duration)}s
        </span>
        {isBuffering && <span className="buffering">Buffering...</span>}
      </div>
      {/* Always render a hidden snapshot element so Playwright can read it */}
      <pre
        data-testid="qoe-snapshot"
        aria-hidden={!showQoePanel}
        style={{ display: showQoePanel ? undefined : 'none' }}
        className="qoe-snapshot-pre"
      >
        {snapshotJson()}
      </pre>
      {showQoePanel && (
        <section className="qoe-probe-panel" aria-label="Player probe snapshot for CI">
          <h4>Player probe snapshot (window.__QOE_DEMO__)</h4>
        </section>
      )}
    </div>
  );
}
