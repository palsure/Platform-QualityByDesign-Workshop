# QoE Metrics Schema

This directory contains the shared schema definitions for Quality of Experience (QoE) metrics used across all platforms.

## Files

- `qoe-metrics.schema.json` - JSON Schema definition for QoE metrics
- `qoe-metrics.types.ts` - TypeScript type definitions
- `README.md` - This file

## Usage

### TypeScript/JavaScript

```typescript
import { QoEMetricPayload, Platform } from './qoe-metrics.types';

const metric: QoEMetricPayload = {
  platform: 'web',
  videoId: 'video-123',
  sessionId: 'session-456',
  timestamp: new Date().toISOString(),
  metrics: {
    playbackState: 'playing',
    currentTime: 30.5,
    duration: 120.0,
    startupTime: 1500,
    currentBitrate: 2500000
  }
};
```

### JSON Schema Validation

Use the JSON Schema for runtime validation:

```javascript
const Ajv = require('ajv');
const ajv = new Ajv();
const validate = ajv.compile(require('./qoe-metrics.schema.json'));

const isValid = validate(metricPayload);
if (!isValid) {
  console.error(validate.errors);
}
```

## Metric Fields

### Required Fields

- `platform`: Platform identifier (web, ios, android, etc.)
- `videoId`: Unique video identifier
- `sessionId`: Unique playback session identifier
- `timestamp`: ISO 8601 timestamp
- `metrics.playbackState`: Current playback state
- `metrics.currentTime`: Current playback time in seconds
- `metrics.duration`: Total video duration in seconds

### Optional Fields

- `deviceInfo`: Device and browser information
- `bufferingEvents`: Array of buffering events
- `startupTime`: Time to first frame in milliseconds
- `currentBitrate`: Current bitrate in bps
- `currentResolution`: Current video resolution
- `errors`: Array of playback errors
- `framesDropped`: Number of dropped frames
- `networkSpeed`: Estimated network speed

## Platform-Specific Notes

- **Web**: Browser information is typically available
- **iOS/Android**: Device type and OS version are available
- **Smart TV**: Screen resolution is typically available
