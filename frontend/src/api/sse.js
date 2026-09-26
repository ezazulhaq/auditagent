export class SseProtocolError extends Error {
  constructor(message, cause) {
    super(message, { cause });
    this.name = 'SseProtocolError';
  }
}

const findFrameBoundary = (buffer) => {
  const lf = buffer.indexOf('\n\n');
  const crlf = buffer.indexOf('\r\n\r\n');
  if (lf < 0) return crlf < 0 ? null : { index: crlf, length: 4 };
  if (crlf < 0 || lf < crlf) return { index: lf, length: 2 };
  return { index: crlf, length: 4 };
};

export const parseSseFrame = (frame) => {
  const dataLines = [];
  for (const line of frame.split(/\r?\n/)) {
    if (!line || line.startsWith(':')) continue;
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''));
    }
  }
  if (dataLines.length === 0) return null;

  const payload = dataLines.join('\n');
  try {
    return JSON.parse(payload);
  } catch (error) {
    // If it's not valid JSON (e.g. a plain text token from streaming), return as a token event
    return { type: 'token', content: payload };
  }
};

/**
 * Reads JSON Server-Sent Events from a Fetch ReadableStream.
 * Handles optional whitespace after data:, CRLF, arbitrary chunking, and EOF without a blank line.
 */
export async function readJsonSse(stream, onEvent) {
  if (!stream?.getReader) throw new SseProtocolError('Streaming response has no readable body.');

  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let eventCount = 0;

  const emitFrame = (frame) => {
    const event = parseSseFrame(frame);
    if (event !== null) {
      eventCount += 1;
      onEvent(event);
    }
  };

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    let boundary = findFrameBoundary(buffer);
    while (boundary) {
      emitFrame(buffer.slice(0, boundary.index));
      buffer = buffer.slice(boundary.index + boundary.length);
      boundary = findFrameBoundary(buffer);
    }
  }

  buffer += decoder.decode();
  if (buffer.trim()) emitFrame(buffer);
  return eventCount;
}
