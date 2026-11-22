// Renders the pictures that are not Android vector files: store icon, repository logo and social preview.
// Usage: npm install playwright && node design/generate-assets.js
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const jobs = [
  { svg: 'nearly_icon.svg', out: 'app/src/main/ic_launcher-playstore.png', width: 512, height: 512 },
  { svg: 'nearly_logo.svg', out: 'doc/logo.png', width: 1200, height: 400, transparent: true },
  { svg: 'nearly_social_preview.svg', out: 'doc/social-preview.png', width: 1280, height: 640 },
];

(async () => {
  const browser = await chromium.launch();
  for (const job of jobs) {
    const page = await browser.newPage({ viewport: { width: job.width, height: job.height }, deviceScaleFactor: 1 });
    const svg = fs.readFileSync(path.join(__dirname, job.svg), 'utf8');
    await page.setContent(`<html><body style="margin:0;background:transparent">${svg}</body></html>`);
    const target = path.join(root, job.out);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    await page.screenshot({ path: target, omitBackground: Boolean(job.transparent), clip: { x: 0, y: 0, width: job.width, height: job.height } });
    await page.close();
    console.log('wrote', job.out);
  }
  await browser.close();
})();
