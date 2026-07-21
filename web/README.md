# Browser solver (n ≤ 6)

Static web UI that generates inequivalent expressions **in your browser** (Web Worker), caches them in IndexedDB, then solves puzzles locally.

## Run locally

```bash
npx serve web -p 5173
```

Open http://localhost:5173

## Verify algorithm counts

```bash
node web/scripts/verify.mjs 5   # fast
node web/scripts/verify.mjs 6   # ~20–40s
```

Expected OEIS A140606 counts: 1, 6, 68, 1170, 27142, 793002 for n=1…6.

## Deploy

Copy the `web/` folder to any static host (GitHub Pages, Netlify, your site). No backend required.
