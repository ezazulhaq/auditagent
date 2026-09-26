import { Check, ChevronDown } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

export function CustomSelect({ value, onChange, options, disabled, placeholder }) {
  const [isOpen, setIsOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (ref.current && !ref.current.contains(event.target)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const selectedOption = options.find(opt => String(opt.value) === String(value));

  return (
    <div className="relative w-full" ref={ref}>
      <button
        type="button"
        disabled={disabled}
        onClick={() => setIsOpen(!isOpen)}
        className={`field-control px-3 text-xs flex items-center justify-between text-left ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
      >
        <span className={!selectedOption ? 'text-slate-500' : 'text-slate-200 truncate pr-2'}>
          {selectedOption ? selectedOption.label : placeholder}
        </span>
        <ChevronDown size={14} className={`text-slate-500 shrink-0 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
      </button>

      {isOpen && !disabled && (
        <div className="absolute z-50 w-full mt-1.5 bg-[#0b1a2b] border border-slate-700/50 rounded-xl shadow-[0_20px_60px_rgba(0,0,0,0.6)] backdrop-blur-xl overflow-hidden py-1 max-h-64 overflow-y-auto">
          {options.length === 0 ? (
            <div className="px-4 py-3 text-xs text-slate-500 text-center italic">No options available</div>
          ) : (
            options.map((opt) => (
              <div
                key={opt.value}
                onClick={() => {
                  onChange(opt.value);
                  setIsOpen(false);
                }}
                className={`flex items-center justify-between px-3 py-2.5 mx-1 text-xs cursor-pointer rounded-lg transition-colors ${String(value) === String(opt.value)
                    ? 'bg-cyan-500/10 text-cyan-300 font-semibold'
                    : 'text-slate-300 hover:bg-slate-800/80 hover:text-white'
                  }`}
              >
                <span className="truncate pr-3">{opt.label}</span>
                {String(value) === String(opt.value) && <Check size={14} className="text-cyan-400 shrink-0" />}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}

