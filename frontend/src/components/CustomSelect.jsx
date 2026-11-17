import { Check, ChevronDown } from 'lucide-react';
import { memo, useMemo, useState } from 'react';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectTrigger,
  SelectValue,
} from '@ui-sdk/react/select';

export const CustomSelect = memo(function CustomSelect({
  value,
  onChange,
  options = [],
  disabled,
  placeholder = 'Select option',
}) {
  const [open, setOpen] = useState(false);

  const items = useMemo(() => {
    return (options || []).map((opt) => ({
      value: String(opt.value),
      label: String(opt.label),
    }));
  }, [options]);

  const selectedValue = value != null && value !== '' ? String(value) : null;

  const handleValueChange = (newVal) => {
    if (newVal == null) return;
    const match = (options || []).find((opt) => String(opt.value) === String(newVal));
    onChange?.(match ? match.value : newVal);
  };

  return (
    <div className="relative w-full">
      <Select
        items={items}
        open={open}
        onOpenChange={setOpen}
        value={selectedValue}
        onValueChange={handleValueChange}
        disabled={disabled}
        placeholder={placeholder}
      >
        <SelectTrigger
          className={`field-control group px-3 text-xs flex items-center justify-between text-left w-full transition ${
            disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer hover:border-slate-500/50'
          }`}
        >
          <span className="truncate pr-2">
            <SelectValue placeholder={placeholder} />
          </span>
          <ChevronDown size={14} className="text-slate-500 shrink-0 transition-transform group-data-open:rotate-180" />
        </SelectTrigger>

        <SelectContent
          className="z-50 min-w-[var(--anchor-width)] max-h-64 overflow-y-auto rounded-xl border border-[var(--border-subtle)] bg-[var(--surface-card)] p-1 shadow-2xl backdrop-blur-xl"
        >
          {items.length === 0 ? (
            <div className="px-4 py-3 text-xs text-[var(--text-muted)] text-center italic">
              No options available
            </div>
          ) : (
            open && items.map((item) => (
              <SelectItem
                key={item.value}
                value={item.value}
                className="flex items-center justify-between px-3 py-2 mx-1 text-xs cursor-pointer rounded-lg transition-colors text-[var(--text-secondary)] hover:bg-[var(--surface-inset)] hover:text-[var(--text-primary)] data-selected:bg-cyan-500/10 data-selected:text-cyan-400 data-selected:font-semibold data-highlighted:bg-[var(--surface-inset)] data-highlighted:text-[var(--text-primary)]"
              >
                <span className="truncate pr-3">{item.label}</span>
                <SelectItemIndicator>
                  <Check size={14} className="text-cyan-400 shrink-0" />
                </SelectItemIndicator>
              </SelectItem>
            ))
          )}
        </SelectContent>
      </Select>
    </div>
  );
});
