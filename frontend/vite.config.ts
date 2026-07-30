import { defineConfig, loadEnv, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';

const backendProxy = {
  target: 'http://localhost:8081',
  changeOrigin: true,
  xfwd: true,
};
const adminBackendProxy = {
  target: 'http://localhost:19092',
  changeOrigin: true,
  xfwd: true,
};

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '..', '');
  const adminMode = mode === 'admin';
  const publicSiteUrl = String(env.VITE_PUBLIC_SITE_URL || 'http://localhost:5174').replace(/\/$/, '');
  const allowIndexing = env.VITE_ALLOW_INDEXING === 'true';
  const normalProxy = {
    '/api': backendProxy,
    '/oauth2': backendProxy,
    '/login/oauth2': backendProxy,
    '/uploads': backendProxy,
  };
  return {
  plugins: [react(), seoFilesPlugin(publicSiteUrl, env.VITE_GOOGLE_SITE_VERIFICATION || '', allowIndexing)],
  envDir: '..',
  server: {
    host: '127.0.0.1',
    port: adminMode ? 19091 : 5174,
    strictPort: true,
    proxy: adminMode ? { '/api/v1/admin': adminBackendProxy, ...normalProxy } : normalProxy,
  },
  preview: {
    host: '127.0.0.1',
    port: adminMode ? 19091 : 5174,
    strictPort: true,
    proxy: adminMode ? { '/api/v1/admin': adminBackendProxy, ...normalProxy } : normalProxy,
  },
  };
});

function seoFilesPlugin(siteUrl: string, verification: string, allowIndexing: boolean): Plugin {
  const publicPaths = ['/', '/demo', '/product', '/b2b', '/pricing', '/contact', '/privacy', '/terms', '/paid-terms', '/refund-policy', '/site-map'];
  const robots = allowIndexing ? [
    'User-agent: *',
    'Allow: /',
    'Disallow: /api/',
    'Disallow: /uploads/',
    'Disallow: /app',
    'Disallow: /groups',
    'Disallow: /tasks',
    'Disallow: /calendar',
    'Disallow: /notifications',
    'Disallow: /profile',
    'Disallow: /account',
    'Disallow: /payments',
    'Disallow: /login',
    'Disallow: /signup',
    'Disallow: /oauth/',
    'Disallow: /group-invitations/',
    `Sitemap: ${siteUrl}/sitemap.xml`,
    '',
  ].join('\n') : [
    'User-agent: *',
    'Disallow: /',
    '',
  ].join('\n');
  const sitemap = [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">',
    ...publicPaths.map((path) => `  <url><loc>${escapeXml(`${siteUrl}${path}`)}</loc><changefreq>${path === '/' ? 'weekly' : 'monthly'}</changefreq><priority>${path === '/' ? '1.0' : '0.5'}</priority></url>`),
    '</urlset>',
    '',
  ].join('\n');
  return {
    name: 'totaskflow-seo-files',
    transformIndexHtml(html) {
      const transformed = allowIndexing
        ? html.replace('<meta name="robots" content="noindex,nofollow" />', '<meta name="robots" content="index,follow" />')
        : html;
      if (!verification) return transformed;
      return {
        html: transformed,
        tags: [{ tag: 'meta', attrs: { name: 'google-site-verification', content: verification }, injectTo: 'head' }],
      };
    },
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        const url = (request as { url?: string }).url;
        if (url === '/robots.txt') {
          response.setHeader('Content-Type', 'text/plain; charset=utf-8');
          response.end(robots);
          return;
        }
        if (url === '/sitemap.xml') {
          response.setHeader('Content-Type', 'application/xml; charset=utf-8');
          response.end(sitemap);
          return;
        }
        next();
      });
    },
    generateBundle() {
      this.emitFile({ type: 'asset', fileName: 'robots.txt', source: robots });
      this.emitFile({ type: 'asset', fileName: 'sitemap.xml', source: sitemap });
    },
  };
}

function escapeXml(value: string) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&apos;');
}
