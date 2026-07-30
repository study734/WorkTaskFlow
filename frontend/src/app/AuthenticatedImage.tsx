import { useEffect, useState } from 'react';
import { accessToken, refreshAccessToken } from '../api/client';

export function AuthenticatedImage({ src, alt, className }: { src: string; alt: string; className?: string }) {
  const [objectUrl, setObjectUrl] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    let createdUrl = '';
    const resolved = new URL(src, window.location.origin);
    if (resolved.origin !== window.location.origin || !resolved.pathname.startsWith('/uploads/')) return;
    async function load(allowRefresh: boolean): Promise<void> {
      const token = accessToken.get();
      const response = await fetch(resolved.toString(), {
        signal: controller.signal,
        credentials: 'include',
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (response.status === 401 && allowRefresh) {
        await refreshAccessToken();
        return load(false);
      }
      if (!response.ok) throw new Error('image');
      const blob = await response.blob();
      createdUrl = URL.createObjectURL(blob);
      setObjectUrl(createdUrl);
    }
    load(true).catch(() => undefined);
    return () => {
      controller.abort();
      if (createdUrl) URL.revokeObjectURL(createdUrl);
    };
  }, [src]);

  return objectUrl ? <img src={objectUrl} alt={alt} className={className} /> : null;
}
