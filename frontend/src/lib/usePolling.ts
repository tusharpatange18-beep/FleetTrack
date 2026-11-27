import { useCallback, useEffect, useRef, useState } from 'react';
import { errorMessage } from './errors';

/**
 * Calls `load` now and then every `intervalMs` (the backend has no push channel, so the UI
 * asks again and again). Pauses while the tab is hidden. `reload()` fetches immediately,
 * e.g. right after the user did something.
 */
export function usePolling<T>(load: () => Promise<T>, intervalMs: number) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Keep the newest `load` in a ref, so the timer below always calls the latest version
  // without having to be restarted on every render
  const loadRef = useRef(load);
  loadRef.current = load;

  // useCallback keeps the same `reload` function between renders
  const reload = useCallback(async () => {
    try {
      const result = await loadRef.current();
      setData(result);
      setError(null);
    } catch (e) {
      setError(errorMessage(e));
    }
  }, []);

  useEffect(() => {
    reload(); // load once straight away

    const timer = setInterval(() => {
      // Don't waste requests while the browser tab is in the background
      if (document.visibilityState === 'visible') {
        reload();
      }
    }, intervalMs);

    // React calls this when the page closes: stop the timer
    return () => clearInterval(timer);
  }, [reload, intervalMs]);

  return { data, error, reload };
}
