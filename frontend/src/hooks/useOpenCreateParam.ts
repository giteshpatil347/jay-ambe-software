import { useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';

/** Dashboard quick actions link here as `?new=1`: open the create form once (if allowed), then clean the URL. */
export function useOpenCreateParam(allowed: boolean, open: () => void) {
  const [params, setParams] = useSearchParams();
  useEffect(() => {
    if (!params.has('new')) return;
    if (allowed && params.get('new') === '1') open();
    const next = new URLSearchParams(params);
    next.delete('new');
    setParams(next, { replace: true });
  }, [params, allowed, open, setParams]);
}
