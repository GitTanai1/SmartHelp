import { ClerkProvider } from '@clerk/react';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './react.css';
import { App } from './react/App';

const publishableKey = import.meta.env['VITE_CLERK_PUBLISHABLE_KEY'];

if (!publishableKey) {
  throw new Error('VITE_CLERK_PUBLISHABLE_KEY is required to start SmartHelp.');
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ClerkProvider publishableKey={publishableKey} afterSignOutUrl="/">
      <App />
    </ClerkProvider>
  </StrictMode>,
);
