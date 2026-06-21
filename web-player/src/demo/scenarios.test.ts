import * as allure from 'allure-js-commons';
import { describe, expect, it } from 'vitest';
import { DEMO_SCENARIO_IDS, DEMO_SCENARIO_LABELS, parseDemoScenario } from './scenarios';

describe('parseDemoScenario', () => {
  it('defaults invalid values to baseline', async () => {
    await allure.feature('Demo Scenarios');
    await allure.story('parseDemoScenario');
    await allure.description('Null, empty string, and unrecognised values must all fall back to the "baseline" scenario.');
    await allure.step('null → baseline',    () => expect(parseDemoScenario(null)).toBe('baseline'));
    await allure.step('empty → baseline',   () => expect(parseDemoScenario('')).toBe('baseline'));
    await allure.step('unknown → baseline', () => expect(parseDemoScenario('unknown')).toBe('baseline'));
  });

  it('accepts every known scenario id', async () => {
    await allure.feature('Demo Scenarios');
    await allure.story('parseDemoScenario');
    await allure.description('Every id in DEMO_SCENARIO_IDS must round-trip through parseDemoScenario unchanged.');
    for (const id of DEMO_SCENARIO_IDS) {
      await allure.step(`"${id}" → "${id}"`, () => expect(parseDemoScenario(id)).toBe(id));
    }
  });

  it('returns "baseline" for a string that is a prefix of a valid id', async () => {
    await allure.feature('Demo Scenarios');
    await allure.story('parseDemoScenario');
    await allure.description('Partial matches must not be accepted — only exact id strings are valid.');
    await allure.step('"base" → baseline',    () => expect(parseDemoScenario('base')).toBe('baseline'));
    await allure.step('"startup" → baseline', () => expect(parseDemoScenario('startup')).toBe('baseline'));
  });

  it('is case-sensitive — mixed-case ids fall back to baseline', async () => {
    await allure.feature('Demo Scenarios');
    await allure.story('parseDemoScenario');
    await allure.description('Scenario ids are case-sensitive; mixed-case variants must fall back to "baseline".');
    await allure.step('"Baseline" → baseline', () => expect(parseDemoScenario('Baseline')).toBe('baseline'));
    await allure.step('"BASELINE" → baseline', () => expect(parseDemoScenario('BASELINE')).toBe('baseline'));
  });
});

describe('DEMO_SCENARIO_IDS', () => {
  it('contains exactly 5 scenario ids', async () => {
    await allure.feature('Demo Scenarios');
    await allure.story('DEMO_SCENARIO_IDS');
    await allure.description('The catalogue must expose exactly 5 fault-injection scenario ids for CI tests.');
    await allure.step('length === 5', () => expect(DEMO_SCENARIO_IDS.length).toBe(5));
  });

  it('always includes "baseline" as the first entry', async () => {
    await allure.feature('Demo Scenarios');
    await allure.story('DEMO_SCENARIO_IDS');
    await allure.description('"baseline" must be the first scenario — it is the reference stream used by other tests.');
    await allure.step('first entry is "baseline"', () => expect(DEMO_SCENARIO_IDS[0]).toBe('baseline'));
  });
});

describe('DEMO_SCENARIO_LABELS', () => {
  it('has a label entry for every known scenario id', async () => {
    await allure.feature('Demo Scenarios');
    await allure.story('DEMO_SCENARIO_LABELS');
    await allure.description('Every scenario id must have a corresponding human-readable label.');
    for (const id of DEMO_SCENARIO_IDS) {
      await allure.step(`label exists for "${id}"`, () =>
        expect(DEMO_SCENARIO_LABELS[id], `label missing for "${id}"`).toBeTruthy(),
      );
    }
  });

  it('all labels are non-empty strings', async () => {
    await allure.feature('Demo Scenarios');
    await allure.story('DEMO_SCENARIO_LABELS');
    await allure.description('Each label value must be a non-empty string suitable for display in a UI.');
    for (const [id, label] of Object.entries(DEMO_SCENARIO_LABELS)) {
      await allure.step(`"${id}" has a string label`, () => {
        expect(typeof label).toBe('string');
        expect(label.length).toBeGreaterThan(0);
      });
    }
  });

  it('has no extra entries beyond the known scenario ids', async () => {
    await allure.feature('Demo Scenarios');
    await allure.story('DEMO_SCENARIO_LABELS');
    await allure.description('DEMO_SCENARIO_LABELS must not contain orphaned entries for unknown scenario ids.');
    await allure.step('key count matches DEMO_SCENARIO_IDS', () =>
      expect(Object.keys(DEMO_SCENARIO_LABELS).length).toBe(DEMO_SCENARIO_IDS.length),
    );
  });
});
