import * as allure from 'allure-js-commons';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { QoECollector } from './qoeCollector';

describe('QoECollector', () => {
  let collector: QoECollector;

  beforeEach(() => {
    collector = new QoECollector('test-video', { silent: true });
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  // ── construction ────────────────────────────────────────────────────────────

  describe('initial state', () => {
    it('starts with zero buffering count and time', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Initial State');
      await allure.description('A freshly constructed QoECollector must report zero buffering events and zero accumulated buffering time.');
      await allure.step('bufferingEventsCount === 0', () => expect(collector.getBufferingEventsCount()).toBe(0));
      await allure.step('totalBufferingTime === 0',   () => expect(collector.totalBufferingTime).toBe(0));
    });

    it('starts with zero bitrate switches and null lastBitrate', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Initial State');
      await allure.description('No bitrate switches must be recorded until the first bitrateChange event fires.');
      await allure.step('bitrateSwitches === 0', () => expect(collector.getBitrateSwitches()).toBe(0));
      await allure.step('lastBitrate === null',   () => expect(collector.lastBitrate).toBeNull());
    });

    it('starts with zero errors', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Initial State');
      await allure.description('Error count must be zero before any playback errors are recorded.');
      await allure.step('errorCount === 0', () => expect(collector.getErrorCount()).toBe(0));
    });

    it('generates a unique sessionId for each instance (observable via lastBitrate independence)', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Initial State');
      await allure.description('Each QoECollector instance must be fully independent — mutating one must not affect another.');
      const a = new QoECollector('v', { silent: true });
      const b = new QoECollector('v', { silent: true });
      await allure.step('mutate instance a', () => a.recordBitrateChange(1_000_000));
      await allure.step('a.lastBitrate changed', () => expect(a.lastBitrate).toBe(1_000_000));
      await allure.step('b.lastBitrate unchanged', () => expect(b.lastBitrate).toBeNull());
    });
  });

  // ── time-to-first-frame ─────────────────────────────────────────────────────

  describe('getTimeToFirstFrameMs', () => {
    it('returns null before markPlaybackRequested is called', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Time to First Frame');
      await allure.description('TTFF cannot be computed before the playback request has been marked — must return null.');
      await allure.step('getTimeToFirstFrameMs() === null', () =>
        expect(collector.getTimeToFirstFrameMs()).toBeNull(),
      );
    });

    it('returns null when only markFirstFrameRendered has been called', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Time to First Frame');
      await allure.description('Without a playback-request mark there is no start time, so TTFF must remain null even after the first frame is marked.');
      await allure.step('mark first frame (no prior request mark)', () => collector.markFirstFrameRendered());
      await allure.step('getTimeToFirstFrameMs() === null', () =>
        expect(collector.getTimeToFirstFrameMs()).toBeNull(),
      );
    });

    it('returns null when only markPlaybackRequested has been called', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Time to First Frame');
      await allure.description('Without a first-frame mark there is no end time, so TTFF must remain null even after the request is marked.');
      await allure.step('mark playback requested', () => collector.markPlaybackRequested());
      await allure.step('getTimeToFirstFrameMs() === null', () =>
        expect(collector.getTimeToFirstFrameMs()).toBeNull(),
      );
    });

    it('returns the elapsed ms between request and first frame', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Time to First Frame');
      await allure.description('TTFF = firstFrameMark − playbackRequestMark, measured in milliseconds via performance.now().');
      let now = 1000;
      vi.spyOn(performance, 'now').mockImplementation(() => now);
      await allure.step('mark playback requested @ 1000 ms', () => collector.markPlaybackRequested());
      await allure.step('advance clock to 1750 ms',           () => { now = 1750; });
      await allure.step('mark first frame rendered @ 1750 ms', () => collector.markFirstFrameRendered());
      await allure.step('TTFF === 750 ms', () => expect(collector.getTimeToFirstFrameMs()).toBe(750));
    });

    it('markPlaybackRequested is idempotent — subsequent calls are ignored', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Time to First Frame');
      await allure.description('Only the first call to markPlaybackRequested sets the start mark; subsequent calls must be silently ignored so TTFF is not reset mid-session.');
      let now = 1000;
      vi.spyOn(performance, 'now').mockImplementation(() => now);
      await allure.step('first request mark @ 1000 ms',        () => collector.markPlaybackRequested());
      await allure.step('advance to 9000 ms, call again',      () => { now = 9000; collector.markPlaybackRequested(); });
      await allure.step('advance to 9500 ms, mark first frame', () => { now = 9500; collector.markFirstFrameRendered(); });
      await allure.step('TTFF === 8500 ms (from original mark)', () =>
        expect(collector.getTimeToFirstFrameMs()).toBe(8500),
      );
    });

    it('markFirstFrameRendered is idempotent — subsequent calls are ignored', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Time to First Frame');
      await allure.description('Only the first call to markFirstFrameRendered captures the end mark; subsequent calls must not overwrite it.');
      let now = 1000;
      vi.spyOn(performance, 'now').mockImplementation(() => now);
      await allure.step('mark request @ 1000 ms',              () => collector.markPlaybackRequested());
      await allure.step('mark first frame @ 1300 ms',          () => { now = 1300; collector.markFirstFrameRendered(); });
      await allure.step('advance to 5000 ms, mark frame again', () => { now = 5000; collector.markFirstFrameRendered(); });
      await allure.step('TTFF === 300 ms (original mark kept)', () =>
        expect(collector.getTimeToFirstFrameMs()).toBe(300),
      );
    });
  });

  // ── buffering tracking ──────────────────────────────────────────────────────

  describe('buffering tracking', () => {
    it('records a buffering event and accumulates duration', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Buffering Tracking');
      await allure.description('A start/end pair must produce exactly one buffering event and add the elapsed seconds to totalBufferingTime.');
      await allure.step('set clock to 0',          () => vi.setSystemTime(0));
      await allure.step('recordBufferingStart',     () => collector.recordBufferingStart());
      await allure.step('advance 2 s',             () => vi.setSystemTime(2000));
      await allure.step('recordBufferingEnd',       () => collector.recordBufferingEnd());
      await allure.step('bufferingEventsCount === 1', () => expect(collector.getBufferingEventsCount()).toBe(1));
      await allure.step('totalBufferingTime ≈ 2 s',   () => expect(collector.totalBufferingTime).toBeCloseTo(2, 5));
    });

    it('ignores recordBufferingEnd when no start has been recorded', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Buffering Tracking');
      await allure.description('Calling recordBufferingEnd without a prior recordBufferingStart must be a safe no-op.');
      await allure.step('call recordBufferingEnd with no open event', () => collector.recordBufferingEnd());
      await allure.step('bufferingEventsCount === 0', () => expect(collector.getBufferingEventsCount()).toBe(0));
      await allure.step('totalBufferingTime === 0',   () => expect(collector.totalBufferingTime).toBe(0));
    });

    it('recordBufferingStart is idempotent while a buffering event is open', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Buffering Tracking');
      await allure.description('A second recordBufferingStart while an event is already open must be ignored — the original start time is preserved.');
      await allure.step('set clock to 0, start buffering', () => { vi.setSystemTime(0); collector.recordBufferingStart(); });
      await allure.step('advance 1 s, call start again',   () => { vi.setSystemTime(1000); collector.recordBufferingStart(); });
      await allure.step('advance to 3 s, end buffering',   () => { vi.setSystemTime(3000); collector.recordBufferingEnd(); });
      await allure.step('totalBufferingTime ≈ 3 s',        () => expect(collector.totalBufferingTime).toBeCloseTo(3, 5));
    });

    it('accumulates multiple buffering events', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Buffering Tracking');
      await allure.description('Each start/end pair must be tracked independently and their durations summed in totalBufferingTime.');
      vi.setSystemTime(0);
      await allure.step('event 1: 0 → 1 s', () => { collector.recordBufferingStart(); vi.setSystemTime(1000); collector.recordBufferingEnd(); });
      await allure.step('event 2: 5 → 7 s', () => { vi.setSystemTime(5000); collector.recordBufferingStart(); vi.setSystemTime(7000); collector.recordBufferingEnd(); });
      await allure.step('bufferingEventsCount === 2', () => expect(collector.getBufferingEventsCount()).toBe(2));
      await allure.step('totalBufferingTime ≈ 3 s',   () => expect(collector.totalBufferingTime).toBeCloseTo(3, 5));
    });

    it('does not double-count if recordBufferingEnd is called twice', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Buffering Tracking');
      await allure.description('A redundant second recordBufferingEnd must not add a phantom event.');
      vi.setSystemTime(0);
      await allure.step('start and end one event', () => { collector.recordBufferingStart(); vi.setSystemTime(1000); collector.recordBufferingEnd(); });
      await allure.step('call end again (no open event)', () => collector.recordBufferingEnd());
      await allure.step('bufferingEventsCount still === 1', () => expect(collector.getBufferingEventsCount()).toBe(1));
    });
  });

  // ── error tracking ──────────────────────────────────────────────────────────

  describe('error tracking', () => {
    it('increments the error count for each recorded error', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Error Tracking');
      await allure.description('Each call to recordError must increment the error count by exactly one.');
      await allure.step('record NET_ERR',   () => { collector.recordError('NET_ERR', 'Network error'); expect(collector.getErrorCount()).toBe(1); });
      await allure.step('record DECODE_ERR', () => { collector.recordError('DECODE_ERR', 'Decode failed'); expect(collector.getErrorCount()).toBe(2); });
    });

    it('accumulates errors independently — each call adds exactly one', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Error Tracking');
      await allure.description('Duplicate error codes are valid (the same error can recur); each call must add one entry regardless of code.');
      await allure.step('record A → count 1', () => { collector.recordError('A', 'first');               expect(collector.getErrorCount()).toBe(1); });
      await allure.step('record B → count 2', () => { collector.recordError('B', 'second');              expect(collector.getErrorCount()).toBe(2); });
      await allure.step('record A → count 3', () => { collector.recordError('A', 'third duplicate');     expect(collector.getErrorCount()).toBe(3); });
    });
  });

  // ── bitrate tracking ────────────────────────────────────────────────────────

  describe('bitrate tracking', () => {
    it('does not count the first bitrate set as a switch', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Bitrate Tracking');
      await allure.description('The first bitrateChange event establishes a baseline; it must not be counted as a quality-level switch.');
      await allure.step('recordBitrateChange(1 Mbps)', () => collector.recordBitrateChange(1_000_000));
      await allure.step('bitrateSwitches === 0',        () => expect(collector.getBitrateSwitches()).toBe(0));
      await allure.step('lastBitrate === 1 Mbps',       () => expect(collector.lastBitrate).toBe(1_000_000));
    });

    it('counts a switch when the bitrate changes', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Bitrate Tracking');
      await allure.description('A transition from one bitrate to a different one must increment the switch counter.');
      await allure.step('set 1 Mbps',             () => collector.recordBitrateChange(1_000_000));
      await allure.step('switch to 500 kbps',     () => collector.recordBitrateChange(500_000));
      await allure.step('bitrateSwitches === 1',   () => expect(collector.getBitrateSwitches()).toBe(1));
    });

    it('does not count as a switch when the bitrate stays the same', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Bitrate Tracking');
      await allure.description('Receiving the same bitrate value twice must not increment the switch counter.');
      await allure.step('set 1 Mbps',           () => collector.recordBitrateChange(1_000_000));
      await allure.step('set 1 Mbps again',     () => collector.recordBitrateChange(1_000_000));
      await allure.step('bitrateSwitches === 0', () => expect(collector.getBitrateSwitches()).toBe(0));
    });

    it('counts multiple switches correctly', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Bitrate Tracking');
      await allure.description('The switch counter must accurately reflect the number of quality-level transitions across the session.');
      await allure.step('apply 4 bitrate values (3 transitions)', () =>
        [1_000_000, 500_000, 1_000_000, 250_000].forEach(b => collector.recordBitrateChange(b)),
      );
      await allure.step('bitrateSwitches === 3', () => expect(collector.getBitrateSwitches()).toBe(3));
    });

    it('tracks the last observed bitrate', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Bitrate Tracking');
      await allure.description('lastBitrate must always reflect the most recently recorded bitrate value.');
      await allure.step('set 1 Mbps then 250 kbps', () => { collector.recordBitrateChange(1_000_000); collector.recordBitrateChange(250_000); });
      await allure.step('lastBitrate === 250 kbps',  () => expect(collector.lastBitrate).toBe(250_000));
    });
  });

  // ── sendMetrics (silent) ────────────────────────────────────────────────────

  describe('sendMetrics in silent mode', () => {
    it('resolves without throwing', async () => {
      await allure.feature('QoE Collector');
      await allure.story('Metrics Ingest');
      await allure.description('In silent mode (used in CI without a backend) sendMetrics must return immediately without making any HTTP call or throwing.');
      await allure.step('mark playback requested and first frame', () => {
        collector.markPlaybackRequested();
        collector.markFirstFrameRendered();
      });
      await allure.step('sendMetrics resolves to undefined', () =>
        expect(
          collector.sendMetrics({ playbackState: 'playing', currentTime: 5, duration: 120 }),
        ).resolves.toBeUndefined(),
      );
    });
  });
});
