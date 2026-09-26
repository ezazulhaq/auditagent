import { describe, expect, it } from 'vitest';
import { parseSseFrame, readJsonSse, SseProtocolError } from './sse';

const streamFrom = (...chunks) => new ReadableStream({
  start(controller) {
    const encoder = new TextEncoder();
    chunks.forEach(chunk => controller.enqueue(encoder.encode(chunk)));
    controller.close();
  },
});

describe('SSE parser', () => {
  it('accepts data fields with and without a space', () => {
    expect(parseSseFrame('data:{"type":"one"}')).toEqual({ type: 'one' });
    expect(parseSseFrame('data: {"type":"two"}')).toEqual({ type: 'two' });
  });

  it('handles CRLF, comments, multiple events, and arbitrary chunks', async () => {
    const events = [];
    await readJsonSse(streamFrom(
      ': heartbeat\r\nda',
      'ta: {"type":"progress","progress":50}\r\n\r\ndata:{"type":"complete"}\n\n',
    ), event => events.push(event));
    expect(events).toEqual([
      { type: 'progress', progress: 50 },
      { type: 'complete' },
    ]);
  });

  it('joins multi-line data and flushes the final frame at EOF', async () => {
    const events = [];
    await readJsonSse(streamFrom('data: {"type":\ndata: "complete"}'), event => events.push(event));
    expect(events).toEqual([{ type: 'complete' }]);
  });

  it('reports malformed JSON as token', async () => {
    const events = [];
    await readJsonSse(streamFrom('data: not-json'), event => events.push(event));
    expect(events).toEqual([{ type: 'token', content: 'not-json' }]);
  });
});
