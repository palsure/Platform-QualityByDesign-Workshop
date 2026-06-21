import * as allure from 'allure-js-commons';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { registerQoeDemoBridge, unregisterQoeDemoBridge } from './qoeDemoBridge';
import type { QoeDemoBridge } from './qoeDemoBridge';

// Minimal stub bridge for testing registration
function makeBridge(scenario: QoeDemoBridge['scenario'] = 'baseline'): QoeDemoBridge {
  return {
    scenario,
    getSnapshot: vi.fn(),
    play: vi.fn(),
    pause: vi.fn(),
    seek: vi.fn(),
    setLevel: vi.fn(),
  };
}

afterEach(() => {
  unregisterQoeDemoBridge();
});

describe('registerQoeDemoBridge', () => {
  it('attaches the bridge to window.__QOE_DEMO__', async () => {
    await allure.feature('QoE Demo Bridge');
    await allure.story('registerQoeDemoBridge');
    await allure.description('registerQoeDemoBridge must expose the bridge on window.__QOE_DEMO__ so Playwright tests can call getSnapshot() and control playback.');
    const bridge = makeBridge();
    await allure.step('register bridge', () => registerQoeDemoBridge(bridge));
    await allure.step('window.__QOE_DEMO__ === bridge', () => expect(window.__QOE_DEMO__).toBe(bridge));
  });

  it('overrides a previously registered bridge', async () => {
    await allure.feature('QoE Demo Bridge');
    await allure.story('registerQoeDemoBridge');
    await allure.description('A second call to registerQoeDemoBridge must replace the previous bridge so hot-reloads pick up the latest instance.');
    const first  = makeBridge('baseline');
    const second = makeBridge('startup_delay');
    await allure.step('register first bridge',  () => registerQoeDemoBridge(first));
    await allure.step('register second bridge', () => registerQoeDemoBridge(second));
    await allure.step('window.__QOE_DEMO__ === second', () => expect(window.__QOE_DEMO__).toBe(second));
  });

  it('preserves the scenario value on the registered bridge', async () => {
    await allure.feature('QoE Demo Bridge');
    await allure.story('registerQoeDemoBridge');
    await allure.description('The scenario property must survive registration so Playwright tests can read it from window.__QOE_DEMO__.scenario.');
    const bridge = makeBridge('black_screen_pulse');
    await allure.step('register bridge with black_screen_pulse', () => registerQoeDemoBridge(bridge));
    await allure.step('scenario === "black_screen_pulse"', () =>
      expect(window.__QOE_DEMO__?.scenario).toBe('black_screen_pulse'),
    );
  });
});

describe('unregisterQoeDemoBridge', () => {
  it('removes window.__QOE_DEMO__ when a bridge is registered', async () => {
    await allure.feature('QoE Demo Bridge');
    await allure.story('unregisterQoeDemoBridge');
    await allure.description('unregisterQoeDemoBridge must delete window.__QOE_DEMO__ so tests that navigate away from the player cannot call a stale bridge.');
    await allure.step('register bridge',      () => registerQoeDemoBridge(makeBridge()));
    await allure.step('unregister bridge',    () => unregisterQoeDemoBridge());
    await allure.step('window.__QOE_DEMO__ === undefined', () =>
      expect(window.__QOE_DEMO__).toBeUndefined(),
    );
  });

  it('is safe to call when no bridge is registered (idempotent)', async () => {
    await allure.feature('QoE Demo Bridge');
    await allure.story('unregisterQoeDemoBridge');
    await allure.description('Calling unregisterQoeDemoBridge when no bridge is registered must not throw — it should be a safe no-op.');
    await allure.step('call with no bridge registered', () =>
      expect(() => unregisterQoeDemoBridge()).not.toThrow(),
    );
    await allure.step('window.__QOE_DEMO__ remains undefined', () =>
      expect(window.__QOE_DEMO__).toBeUndefined(),
    );
  });
});
