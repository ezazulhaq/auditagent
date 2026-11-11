import { render } from '@testing-library/react';
import { ToastProvider } from '../components/Toast';
import { AppProviders } from '../context/AppProviders';

/**
 * Custom test render function that wraps components in all application providers.
 * Follows Liskov Substitution Principle (LSP) by supporting mock API implementations.
 */
export function renderWithProviders(ui, { api, ...renderOptions } = {}) {
  function Wrapper({ children }) {
    return (
      <ToastProvider>
        <AppProviders api={api}>
          {children}
        </AppProviders>
      </ToastProvider>
    );
  }

  return {
    ...render(ui, { wrapper: Wrapper, ...renderOptions }),
  };
}

export * from '@testing-library/react';
