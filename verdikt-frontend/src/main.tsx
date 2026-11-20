import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@fontsource/fredoka/latin-500.css'
import '@fontsource/fredoka/latin-600.css'
import '@fontsource/fredoka/latin-700.css'
import '@fontsource/dm-sans/latin-400.css'
import '@fontsource/dm-sans/latin-500.css'
import '@fontsource/dm-sans/latin-600.css'
import '@fontsource/dm-sans/latin-700.css'
import './styles/tokens.css'
import './styles/screen.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    void navigator.serviceWorker
      .register('/sw.js')
      .then(() => navigator.serviceWorker.ready)
      .then((registration) => {
        const appAssets = [
          '/',
          '/manifest.webmanifest',
          ...performance.getEntriesByType('resource')
            .map((entry) => entry.name)
            .filter((url) => {
              const assetUrl = new URL(url, window.location.origin);
              return assetUrl.origin === window.location.origin
                && (assetUrl.pathname.startsWith('/assets/')
                  || ['/favicon.svg', '/verdikt-mark.svg', '/verdikt-icon-192.png', '/verdikt-icon-512.png',
                    '/verdikt-icon-maskable-512.png', '/apple-touch-icon.png'].includes(assetUrl.pathname));
            }),
        ].filter((url, index, urls) => urls.indexOf(url) === index);

        registration.active?.postMessage({ type: 'CACHE_URLS', urls: appAssets });
      })
      .catch(() => {
        // The application remains fully usable when service workers are unavailable.
      });
  });
}
