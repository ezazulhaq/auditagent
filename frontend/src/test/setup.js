import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(cleanup);

Object.defineProperty(URL, 'createObjectURL', { configurable: true, writable: true, value: () => 'blob:audit-report' });
Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, writable: true, value: () => {} });

const values = new Map();
Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: {
  getItem: key => values.get(key) ?? null,
  setItem: (key, value) => values.set(key, String(value)),
  removeItem: key => values.delete(key),
  clear: () => values.clear(),
} });
