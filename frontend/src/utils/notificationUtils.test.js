import { describe, expect, it } from 'vitest';
import {
  formatRelativeTime,
  extractFindingId,
  extractPrInfo,
  filterNotifications,
} from './notificationUtils';

describe('notificationUtils', () => {
  describe('formatRelativeTime', () => {
    it('returns "Just now" for invalid dates or future times', () => {
      expect(formatRelativeTime(null)).toBe('Just now');
      expect(formatRelativeTime('invalid-date')).toBe('Just now');
      const future = new Date(Date.now() + 60000);
      expect(formatRelativeTime(future)).toBe('Just now');
    });

    it('formats seconds, minutes, hours, and days relative to now', () => {
      const base = new Date('2026-11-15T12:00:00Z');

      // 30 seconds ago
      const sec30 = new Date('2026-11-15T11:59:30Z');
      expect(formatRelativeTime(sec30, base)).toBe('Just now');

      // 5 minutes ago
      const min5 = new Date('2026-11-15T11:55:00Z');
      expect(formatRelativeTime(min5, base)).toBe('5m ago');

      // 3 hours ago
      const hr3 = new Date('2026-11-15T09:00:00Z');
      expect(formatRelativeTime(hr3, base)).toBe('3h ago');

      // 2 days ago
      const day2 = new Date('2026-11-13T12:00:00Z');
      expect(formatRelativeTime(day2, base)).toBe('2d ago');
    });
  });

  describe('extractFindingId', () => {
    it('extracts VULN IDs accurately', () => {
      expect(extractFindingId('Analysis completed for VULN-000042: Remediation verified.')).toBe('VULN-000042');
      expect(extractFindingId('vuln-1 syntax error')).toBe('VULN-1');
      expect(extractFindingId('No vulnerability mentioned')).toBeNull();
      expect(extractFindingId(null)).toBeNull();
    });
  });

  describe('extractPrInfo', () => {
    it('extracts PR number and URL', () => {
      const textWithNum = 'Pull request #42 created for VULN-1.';
      expect(extractPrInfo(textWithNum)).toEqual({ prNumber: '42', prUrl: null });

      const textWithUrl = 'Pull request created: https://github.com/org/repo/pull/108';
      expect(extractPrInfo(textWithUrl)).toEqual({
        prNumber: '108',
        prUrl: 'https://github.com/org/repo/pull/108',
      });

      expect(extractPrInfo('General update')).toEqual({ prNumber: null, prUrl: null });
    });
  });

  describe('filterNotifications', () => {
    const list = [
      { id: '1', read: false, type: 'info', message: 'M1' },
      { id: '2', read: true, type: 'success', message: 'M2' },
      { id: '3', read: false, type: 'error', message: 'M3' },
      { id: '4', read: true, type: 'warning', message: 'M4' },
    ];

    it('filters for all, unread, and alerts', () => {
      expect(filterNotifications(list, 'all')).toHaveLength(4);
      expect(filterNotifications(list, 'unread')).toEqual([
        { id: '1', read: false, type: 'info', message: 'M1' },
        { id: '3', read: false, type: 'error', message: 'M3' },
      ]);
      expect(filterNotifications(list, 'alerts')).toEqual([
        { id: '3', read: false, type: 'error', message: 'M3' },
        { id: '4', read: true, type: 'warning', message: 'M4' },
      ]);
    });
  });
});
