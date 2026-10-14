#!/usr/bin/env node
// Pixel-compares <Text> against <AnimateableText> on the parity screen.
//
// node scripts/parity.mjs --platform ios --device <simulator udid>
// node scripts/parity.mjs --platform android --device emulator-5554
//
// The example app must be running. Each page is opened via deep link, the app
// logs the measured rects (read from the device log), a screenshot is taken,
// and every case's two rects are diffed pixel by pixel.

import { execFileSync, spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { setTimeout as sleep } from 'node:timers/promises';
import { PNG } from 'pngjs';
import pixelmatch from 'pixelmatch';

const args = {};
for (let i = 2; i < process.argv.length; i += 1) {
  const key = process.argv[i];
  if (!key.startsWith('--')) continue;
  const value = process.argv[i + 1];
  if (value === undefined || value.startsWith('--')) {
    args[key.slice(2)] = 'true';
  } else {
    args[key.slice(2)] = value;
    i += 1;
  }
}

const platform = args.platform;
const device = args.device;
const pageSize = Number(args.pageSize ?? 4);
const autoAdvanceMs = Number(args.dwell ?? 2500);
const runId = `${Date.now()}`;
const isSelfTest = args.selftest === 'true';
const label = args.label ?? (isSelfTest ? 'selftest' : 'default');
const outDir = path.resolve(args.out ?? `parity-output/${platform}-${label}`);

if (!['ios', 'android'].includes(platform) || !device) {
  console.error('Usage: parity.mjs --platform ios|android --device <id> [--pageSize 4] [--label name] [--out dir]');
  process.exit(2);
}

fs.mkdirSync(outDir, { recursive: true });
for (const file of fs.readdirSync(outDir)) {
  if (file.endsWith('.png')) fs.rmSync(path.join(outDir, file));
}

const adb = path.join(process.env.ANDROID_HOME ?? `${process.env.HOME}/Library/Android/sdk`, 'platform-tools', 'adb');

function openUrl(url) {
  if (platform === 'ios') {
    execFileSync('xcrun', ['simctl', 'openurl', device, url]);
  } else {
    execFileSync(adb, ['-s', device, 'shell', 'am', 'start', '-a', 'android.intent.action.VIEW', '-d', `'${url}'`], {
      stdio: 'ignore',
    });
  }
}

function screenshot(file) {
  if (platform === 'ios') {
    execFileSync('xcrun', ['simctl', 'io', device, 'screenshot', file], { stdio: 'ignore' });
  } else {
    fs.writeFileSync(file, execFileSync(adb, ['-s', device, 'exec-out', 'screencap', '-p'], { maxBuffer: 64 * 1024 * 1024 }));
  }
}

const logLines = [];
const logProcess =
  platform === 'ios'
    ? spawn('xcrun', [
        'simctl',
        'spawn',
        device,
        'log',
        'stream',
        '--style',
        'compact',
        '--level',
        'info',
        '--predicate',
        'subsystem == "com.facebook.react.log"',
      ])
    : spawn(adb, ['-s', device, 'logcat', '-T', '1', '-v', 'raw', '-s', 'ReactNativeJS:I']);
let pending = '';
logProcess.stdout.on('data', (chunk) => {
  pending += chunk.toString();
  const lines = pending.split('\n');
  pending = lines.pop();
  for (const line of lines) {
    const match = line.match(/(PARITY_PAGE|PARITY_CASE|PARITY_DONE) (\{.*\})\s*$/);
    if (!match) continue;
    const event = { kind: match[1], ...JSON.parse(match[2]) };
    if (event.runId === runId) logLines.push(event);
  }
});

async function waitForPage(from, page, timeoutMs = 60000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    const events = logLines.slice(from);
    const header = events.find((e) => e.kind === 'PARITY_PAGE' && e.page === page);
    if (header) {
      const cases = events.filter((e) => e.kind === 'PARITY_CASE' && e.page === page);
      if (cases.length >= header.caseCount) return { ...header, cases: cases.slice(0, header.caseCount) };
    } else if (events.some((e) => e.kind === 'PARITY_DONE')) {
      return { kind: 'PARITY_DONE' };
    }
    await sleep(200);
  }
  throw new Error(`Timed out waiting for parity page ${page} to report its layout`);
}

function crop(png, rect, scale) {
  const x = Math.round(rect.x * scale);
  const y = Math.round(rect.y * scale);
  const width = Math.round(rect.width * scale);
  const height = Math.round(rect.height * scale);
  const out = new PNG({ width, height });
  PNG.bitblt(png, out, x, y, width, height, 0, 0);
  return out;
}

function isBlank(png) {
  const [r, g, b, a] = png.data;
  for (let i = 4; i < png.data.length; i += 4) {
    if (png.data[i] !== r || png.data[i + 1] !== g || png.data[i + 2] !== b || png.data[i + 3] !== a) return false;
  }
  return true;
}

const results = [];
let page = 0;
let pageCount = Infinity;

// One deep link starts the run; the app advances pages on its own every
// autoAdvanceMs, so iOS only shows its "Open in…?" prompt once.
await sleep(1500);
openUrl(
  `animateabletext://parity?page=0&pageSize=${pageSize}&autoAdvanceMs=${autoAdvanceMs}&runId=${runId}${isSelfTest ? '&perturb=1' : ''}`
);

while (page < pageCount) {
  const report = await waitForPage(0, page);
  if (report.kind === 'PARITY_DONE') break;
  pageCount = report.pageCount;
  await sleep(300);

  const shotFile = path.join(outDir, `page-${page}.png`);
  screenshot(shotFile);
  const shot = PNG.sync.read(fs.readFileSync(shotFile));
  const expectedWidth = Math.round(report.window.width * report.scale);
  if (Math.abs(shot.width - expectedWidth) > 1) {
    console.warn(`page ${page}: screenshot is ${shot.width}px wide, window is ${expectedWidth}px — rects may be offset`);
  }

  for (const item of report.cases) {
    // Rows taller than the screen (largest accessibility sizes) are compared on
    // their visible part; both columns start at the same y, so the crop is fair.
    const visibleBottom = report.window.height;
    const isPartial =
      item.text.y + item.text.height > visibleBottom || item.animateable.y + item.animateable.height > visibleBottom;
    if (isPartial) {
      if (item.text.height !== item.animateable.height || item.text.y !== item.animateable.y) {
        results.push({ id: item.id, ok: false, reason: 'size differs (row taller than screen)' });
        continue;
      }
      const height = visibleBottom - item.text.y;
      item.text = { ...item.text, height };
      item.animateable = { ...item.animateable, height };
    }
    const a = crop(shot, item.text, report.scale);
    const b = crop(shot, item.animateable, report.scale);
    if (isBlank(a) || isBlank(b)) {
      results.push({ id: item.id, ok: false, reason: `${isBlank(a) ? 'Text' : 'AnimateableText'} rendered nothing` });
      continue;
    }
    if (a.width !== b.width || a.height !== b.height) {
      results.push({
        id: item.id,
        ok: false,
        reason: `size differs: Text ${item.text.width}x${item.text.height}pt, AnimateableText ${item.animateable.width}x${item.animateable.height}pt`,
      });
      fs.writeFileSync(path.join(outDir, `${item.id}-text.png`), PNG.sync.write(a));
      fs.writeFileSync(path.join(outDir, `${item.id}-animateable.png`), PNG.sync.write(b));
      continue;
    }
    const diff = new PNG({ width: a.width, height: a.height });
    const diffPixels = pixelmatch(a.data, b.data, diff.data, a.width, a.height, { threshold: 0, includeAA: true });
    if (diffPixels > 0) {
      fs.writeFileSync(path.join(outDir, `${item.id}-text.png`), PNG.sync.write(a));
      fs.writeFileSync(path.join(outDir, `${item.id}-animateable.png`), PNG.sync.write(b));
      fs.writeFileSync(path.join(outDir, `${item.id}-diff.png`), PNG.sync.write(diff));
    }
    const note = isPartial ? ' (visible part only)' : '';
    results.push({ id: item.id, ok: diffPixels === 0, reason: diffPixels ? `${diffPixels} px differ${note}` : note.trim() });
  }
  page += 1;
}

logProcess.kill();

const failed = results.filter((r) => !r.ok);
for (const r of results) {
  console.log(`${r.ok ? 'PASS' : 'FAIL'}  ${r.id}${r.reason ? `  — ${r.reason}` : ''}`);
}
if (isSelfTest) {
  const missed = results.filter((r) => r.ok);
  console.log(`\n${platform} self-test: ${failed.length}/${results.length} perturbed cases detected. Output: ${outDir}`);
  process.exit(missed.length ? 1 : 0);
}
console.log(`\n${platform} (${label}): ${results.length - failed.length}/${results.length} identical. Output: ${outDir}`);
process.exit(failed.length ? 1 : 0);
