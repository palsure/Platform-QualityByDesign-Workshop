export const DEMO_SCENARIO_IDS = [
  'baseline',
  'startup_delay',
  'black_screen_pulse',
  'forced_mid_play_rebuffer',
  'bitrate_step_down',
] as const;

export type DemoScenarioId = (typeof DEMO_SCENARIO_IDS)[number];

export function parseDemoScenario(raw: string | null): DemoScenarioId {
  if (raw && (DEMO_SCENARIO_IDS as readonly string[]).includes(raw)) {
    return raw as DemoScenarioId;
  }
  return 'baseline';
}

export const DEMO_SCENARIO_LABELS: Record<DemoScenarioId, string> = {
  baseline: 'Baseline (reference stream)',
  startup_delay: 'Startup delay (late manifest attach)',
  black_screen_pulse: 'Visual fault (black frame overlay)',
  forced_mid_play_rebuffer: 'Mid-play stall (HLS stop/start)',
  bitrate_step_down: 'Bitrate instability (forced level step)',
};
