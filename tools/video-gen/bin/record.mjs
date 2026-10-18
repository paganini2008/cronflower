// Screen-recording engine for the cronflower tutorials.
//
// Drives a real Chromium against the running console (default http://localhost:7200) with a visible
// cursor that glides between targets, realistic typing, scrolling and menu interaction, and an
// on-page caption bar (large, transparent background). The whole session is recorded to a webm, so
// it feels like a screencast, not a slideshow. A flow module under flows/<name>.mjs supplies the
// steps and the caption copy. ffmpeg (encode.sh) then adds the synthesized BGM and encodes to mp4.
//
// Usage: node bin/record.mjs <flowName>   ->   raw/<flowName>.webm
import { chromium } from 'playwright';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, resolve } from 'node:path';
import { mkdirSync, rmSync, renameSync, existsSync } from 'node:fs';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '..');
const URL_BASE = process.env.CF_URL || 'http://localhost:7200';
const W = 1280, H = 720;

// Injected into every document: the cursor, the caption bar, the intro card, and the helpers the
// engine calls. Kept dependency-free so it survives every navigation.
const INIT = `
(() => {
  if (window.__pwReady) return;
  window.__pwReady = true;
  const ARROW = "data:image/svg+xml;utf8," + encodeURIComponent(
    '<svg xmlns=\\'http://www.w3.org/2000/svg\\' width=\\'28\\' height=\\'28\\' viewBox=\\'0 0 28 28\\'>' +
    '<path d=\\'M4 2 L4 22 L10 16 L14 25 L18 23 L14 14 L22 14 Z\\' fill=\\'white\\' stroke=\\'black\\' stroke-width=\\'1.5\\' stroke-linejoin=\\'round\\'/></svg>');
  const mk = () => {
    if (document.getElementById('pwcur')) return;
    const style = document.createElement('style');
    style.textContent = \`
      #pwcur{position:fixed;left:0;top:0;width:28px;height:28px;z-index:2147483647;pointer-events:none;
        background:url('\${ARROW}') no-repeat;transform:translate(-100px,-100px);transition:transform .02s linear;
        filter:drop-shadow(0 2px 3px rgba(0,0,0,.35));}
      #pwcur.click::after{content:'';position:absolute;left:4px;top:4px;width:18px;height:18px;border-radius:50%;
        border:3px solid rgba(37,99,235,.9);transform:scale(.3);opacity:.9;animation:pwpulse .5s ease-out;}
      @keyframes pwpulse{to{transform:scale(2.2);opacity:0;}}
      #pwcap{position:fixed;left:50%;bottom:46px;transform:translateX(-50%);z-index:2147483646;pointer-events:none;
        max-width:86%;text-align:center;color:#fff;font-weight:650;font-size:30px;line-height:1.35;
        font-family:'Inter',system-ui,-apple-system,'Segoe UI',Roboto,sans-serif;opacity:0;transition:opacity .25s;
        text-shadow:0 2px 6px rgba(0,0,0,.85),0 0 2px rgba(0,0,0,.9);-webkit-text-stroke:.5px rgba(0,0,0,.55);}
      #pwintro{position:fixed;inset:0;z-index:2147483645;pointer-events:none;display:flex;flex-direction:column;
        align-items:center;justify-content:center;gap:10px;opacity:0;transition:opacity .4s;
        background:radial-gradient(1200px 600px at 50% 30%,#153a6b 0%,#0f2c4d 60%,#0a2038 100%);color:#fff;
        font-family:'Poppins','Inter',system-ui,sans-serif;}
      #pwintro .nm{font-size:62px;font-weight:800;letter-spacing:-.02em;}
      #pwintro .nm b{color:#5aa2ff;}
      #pwintro .ver{font-size:22px;font-weight:600;color:#9ec5ff;letter-spacing:.04em;}
      #pwintro .tag{margin-top:10px;font-size:24px;font-weight:500;color:#dce8fb;max-width:70%;text-align:center;}
    \`;
    document.head.appendChild(style);
    const cur = document.createElement('div'); cur.id = 'pwcur'; document.body.appendChild(cur);
    const cap = document.createElement('div'); cap.id = 'pwcap'; document.body.appendChild(cap);
    const intro = document.createElement('div'); intro.id = 'pwintro';
    intro.innerHTML = '<div class="nm"></div><div class="ver"></div><div class="tag"></div>';
    document.body.appendChild(intro);
    document.addEventListener('mousemove', (e) => {
      cur.style.transform = 'translate(' + (e.clientX - 3) + 'px,' + (e.clientY - 2) + 'px)';
    }, true);
  };
  if (document.body) mk(); else document.addEventListener('DOMContentLoaded', mk);
  window.__pwSay = (t) => { const c = document.getElementById('pwcap'); if (c) { c.textContent = t || ''; c.style.opacity = t ? '1' : '0'; } };
  window.__pwClick = () => { const c = document.getElementById('pwcur'); if (c) { c.classList.remove('click'); void c.offsetWidth; c.classList.add('click'); } };
  window.__pwIntro = (nm, ver, tag) => {
    const i = document.getElementById('pwintro'); if (!i) return;
    i.querySelector('.nm').innerHTML = nm; i.querySelector('.ver').textContent = ver;
    i.querySelector('.tag').textContent = tag; i.style.opacity = '1';
  };
  window.__pwIntroHide = () => { const i = document.getElementById('pwintro'); if (i) i.style.opacity = '0'; };
})();
`;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

class UI {
  constructor(page) { this.page = page; this.x = W / 2; this.y = H / 2; }

  async goto(path) {
    await this.page.goto(URL_BASE + path, { waitUntil: 'domcontentloaded' }).catch(() => {});
    await sleep(700);
  }
  async say(text) { await this.page.evaluate((t) => window.__pwSay(t), text); }
  async wait(ms) { await sleep(ms); }

  // Smoothly glide the cursor to (x,y); Playwright's mousemove drives the injected cursor.
  async glideTo(x, y) {
    await this.page.mouse.move(x, y, { steps: 28 });
    this.x = x; this.y = y;
    await sleep(120);
  }
  async _box(selector) {
    const el = this.page.locator(selector).first();
    await el.scrollIntoViewIfNeeded().catch(() => {});
    await el.waitFor({ state: 'visible', timeout: 8000 });
    const b = await el.boundingBox();
    if (!b) throw new Error('no box for ' + selector);
    return b;
  }
  async moveTo(selector) {
    const b = await this._box(selector);
    await this.glideTo(Math.round(b.x + b.width / 2), Math.round(b.y + Math.min(b.height / 2, 24)));
  }
  async click(selector) {
    await this.moveTo(selector);
    await this.page.evaluate(() => window.__pwClick());
    await sleep(140);
    await this.page.mouse.click(this.x, this.y);
    await sleep(350);
  }
  async type(selector, text, delay = 55) {
    await this.click(selector);
    await this.page.keyboard.type(text, { delay });
    await sleep(250);
  }
  async scrollBy(dy) {
    await this.page.mouse.wheel(0, dy);
    await sleep(600);
  }
  async press(key) { await this.page.keyboard.press(key); await sleep(300); }
  // Pick an option in a native <select>.
  async selectNative(selector, value) {
    await this.moveTo(selector).catch(() => {});
    await this.page.selectOption(selector, value);
    await sleep(350);
  }
  // Paste text into a field instantly (for a long definition), rather than typing char by char.
  async paste(selector, text) {
    await this.click(selector);
    await this.page.fill(selector, text);
    await sleep(300);
  }
  async intro(name, version, tagline, ms = 3600) {
    await this.page.evaluate(([n, v, t]) => window.__pwIntro(n, v, t), [name, version, tagline]);
    await sleep(ms);
    await this.page.evaluate(() => window.__pwIntroHide());
    await sleep(450);
  }
  // Click a sidebar item for the screencast feel, with a goto fallback so a selector miss never
  // breaks the recording.
  async nav(label, path) {
    try {
      await this.click(`.nav-list a:has-text("${label}")`);
      await this.page.waitForURL((u) => u.toString().includes(path), { timeout: 6000 });
      await sleep(500);
    } catch {
      await this.goto(path);
    }
  }
  // Pick an option from an open Angular Material select (the trigger must be clicked first).
  async pickOption(text) {
    await this.click(`mat-option:has-text("${text}")`);
  }
  async login() {
    if (!this.page.url().includes('/login')) await this.goto('/login');
    // If already authenticated the app redirects away from /login.
    if (!this.page.url().includes('/login')) return;
    await this.type('input[formcontrolname="username"], input[name="username"]', 'admin', 35);
    await this.type('input[formcontrolname="password"], input[type="password"]', 'admin123', 35);
    await this.click('button[type="submit"], button:has-text("Sign in")');
    await this.page.waitForURL((u) => !u.toString().includes('/login'), { timeout: 10000 }).catch(() => {});
    await sleep(600);
  }
}

async function main() {
  const flowName = process.argv[2];
  if (!flowName) { console.error('usage: node bin/record.mjs <flowName>'); process.exit(2); }
  const flow = await import(pathToFileURL(resolve(ROOT, 'flows', flowName + '.mjs')).href);

  const rawDir = resolve(ROOT, 'raw');
  mkdirSync(rawDir, { recursive: true });

  const browser = await chromium.launch({ headless: process.env.HEADED ? false : true });
  const context = await browser.newContext({
    viewport: { width: W, height: H },
    recordVideo: { dir: rawDir, size: { width: W, height: H } },
    deviceScaleFactor: 1,
  });
  await context.addInitScript(INIT);
  const page = await context.newPage();
  const ui = new UI(page);

  try {
    const m = flow.meta || {};
    await ui.goto('/login');                   // land on a page so the intro overlay can show first
    await ui.intro(m.introName || 'cronflower', m.version || '', m.tagline || '');
    await ui.login();
    await flow.run(ui);
    await ui.say('');
    await sleep(500);
  } finally {
    await context.close();          // finalizes the webm
    const vpath = await page.video().path();
    await browser.close();
    const out = resolve(rawDir, flowName + '.webm');
    if (existsSync(out)) rmSync(out);
    renameSync(vpath, out);
    console.log('recorded ' + out);
  }
}

main().catch((e) => { console.error(e); process.exit(1); });
