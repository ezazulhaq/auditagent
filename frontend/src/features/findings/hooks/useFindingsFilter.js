import { useState, useMemo, useCallback } from 'react';
import { FINDING_SEVERITY_ORDER, filterFindings } from '../../../utils/auditSelectors';

const statusOrder = [
  'DETECTED', 'ANALYZING', 'GENERATING_FIX', 'VERIFYING',
  'PATCH_FAILED', 'AWAITING_APPROVAL', 'PR_OPEN', 'FIXED', 'IGNORED'
];

/**
 * Custom hook encapsulating finding filters, search, grouping, and selection logic.
 * Adheres to Single Responsibility Principle (SRP).
 */
export function useFindingsFilter(findings = [], runs = {}) {
  const [query, setQuery] = useState('');
  const [severity, setSeverity] = useState('ALL');
  const [status, setStatus] = useState('ALL');
  const [viewMode, setViewMode] = useState('list'); // 'list' | 'grouped'
  const [selectedIds, setSelectedIds] = useState(new Set());

  const filtered = useMemo(() => {
    return filterFindings(findings, { query, severity, status, runs });
  }, [findings, query, severity, status, runs]);

  const groupedFindings = useMemo(() => {
    if (viewMode !== 'grouped') return [];
    const groups = new Map();
    for (const finding of filtered) {
      if (!groups.has(finding.filePath)) groups.set(finding.filePath, []);
      groups.get(finding.filePath).push(finding);
    }
    return Array.from(groups.entries()).sort((a, b) => a[0].localeCompare(b[0]));
  }, [filtered, viewMode]);

  const severityOptions = useMemo(() => [
    { value: 'ALL', label: 'All', count: findings.length },
    ...FINDING_SEVERITY_ORDER.map(value => ({
      value,
      label: value.charAt(0) + value.slice(1).toLowerCase(),
      count: findings.filter(finding => finding.severity === value).length,
    })),
  ], [findings]);

  const statusOptions = useMemo(() => {
    const counts = findings.reduce((result, finding) => {
      const isTerminal = finding.status === 'FIXED' || finding.status === 'IGNORED';
      const value = (isTerminal ? finding.status : (runs?.[finding.id]?.status || finding.status)) || 'DETECTED';
      result.set(value, (result.get(value) || 0) + 1);
      return result;
    }, new Map());

    const values = [...counts.keys()].sort((left, right) => {
      const leftIndex = statusOrder.indexOf(left);
      const rightIndex = statusOrder.indexOf(right);
      return (leftIndex === -1 ? statusOrder.length : leftIndex) - (rightIndex === -1 ? statusOrder.length : rightIndex)
        || left.localeCompare(right);
    });

    if (status !== 'ALL' && !counts.has(status)) values.push(status);
    return values.map(value => ({ value, count: counts.get(value) || 0 }));
  }, [findings, status, runs]);

  const hasFilters = Boolean(query.trim()) || severity !== 'ALL' || status !== 'ALL';

  const resetFilters = useCallback(() => {
    setQuery('');
    setSeverity('ALL');
    setStatus('ALL');
  }, []);

  const handleToggleSelect = useCallback((id, isSelected) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (isSelected) next.add(id);
      else next.delete(id);
      return next;
    });
  }, []);

  const handleToggleGroup = useCallback((ids, isSelected) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      ids.forEach(id => {
        if (isSelected) next.add(id);
        else next.delete(id);
      });
      return next;
    });
  }, []);

  const handleSelectAllFiltered = useCallback((isSelected) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      filtered.forEach(f => {
        if (isSelected && f.triageActionable !== false) next.add(f.id);
        else next.delete(f.id);
      });
      return next;
    });
  }, [filtered]);

  const selectableFiltered = useMemo(() => {
    return filtered.filter(f => f.triageActionable !== false);
  }, [filtered]);

  const filteredSelectedCount = selectableFiltered.filter(f => selectedIds.has(f.id)).length;
  const isAllFilteredSelected = filteredSelectedCount === selectableFiltered.length && selectableFiltered.length > 0;
  const isFilteredIndeterminate = filteredSelectedCount > 0 && filteredSelectedCount < selectableFiltered.length;

  const clearSelection = useCallback(() => {
    setSelectedIds(new Set());
  }, []);

  return {
    query,
    setQuery,
    severity,
    setSeverity,
    status,
    setStatus,
    viewMode,
    setViewMode,
    selectedIds,
    setSelectedIds,
    clearSelection,
    filtered,
    groupedFindings,
    severityOptions,
    statusOptions,
    hasFilters,
    resetFilters,
    handleToggleSelect,
    handleToggleGroup,
    handleSelectAllFiltered,
    isAllFilteredSelected,
    isFilteredIndeterminate,
  };
}
