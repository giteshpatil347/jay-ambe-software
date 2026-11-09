import { useEffect } from 'react';
import { useBranding } from '../../hooks/useBranding';

/** Keeps the tab title and favicon in step with the business branding (renders nothing). */
export function BrandingHead() {
  const { name, iconUrl } = useBranding();
  useEffect(() => { document.title = name; }, [name]);
  useEffect(() => {
    const link = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
    if (!link) return;
    if (!link.dataset.defaultHref) {
      link.dataset.defaultHref = link.getAttribute('href') ?? '';
      link.dataset.defaultType = link.getAttribute('type') ?? '';
    }
    if (iconUrl) {
      link.removeAttribute('type'); // PNG/JPEG/WEBP: let the browser sniff instead of the static svg type
      link.setAttribute('href', iconUrl);
    } else {
      link.setAttribute('href', link.dataset.defaultHref);
      if (link.dataset.defaultType) link.setAttribute('type', link.dataset.defaultType);
    }
  }, [iconUrl]);
  return null;
}
