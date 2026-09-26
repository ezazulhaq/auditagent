import { useState, useEffect, useCallback } from 'react';

export function useTheme() {
  const [theme, setThemeState] = useState(() => {
    const saved = localStorage.getItem('auditagent_theme');
    if (saved === 'light' || saved === 'dark') return saved;
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
  });

  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'light') {
      root.classList.add('light');
      root.setAttribute('data-theme', 'light');
    } else {
      root.classList.remove('light');
      root.setAttribute('data-theme', 'dark');
    }
    localStorage.setItem('auditagent_theme', theme);
  }, [theme]);

  const toggleTheme = useCallback(() => {
    setThemeState(previous => (previous === 'dark' ? 'light' : 'dark'));
  }, []);

  return { theme, toggleTheme, setTheme: setThemeState };
}
