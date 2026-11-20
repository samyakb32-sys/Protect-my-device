// Serves web/ through the Hosting emulator, so the real response headers from firebase.json apply,
// and checks the Content-Security-Policy doesn't break the page (`npm run test:hosting`).
import test from "node:test";
import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { chromium } from "playwright";

test("the dashboard works under the production security headers", async () => {
  const chrome = process.env.CHROMIUM_PATH || (existsSync("/opt/pw-browsers/chromium") ? "/opt/pw-browsers/chromium" : "");
  const browser = await chromium.launch(chrome ? { executablePath: chrome } : {});
  try {
    const page = await browser.newPage();
    const problems = [];
    page.on("console", (m) => { if (m.type() === "error") problems.push(m.text()); });
    page.on("pageerror", (e) => problems.push(e.message));
    const response = await page.goto("http://127.0.0.1:5000/?demo", { waitUntil: "networkidle" });
    const headers = response.headers();
    assert.match(headers["content-security-policy"], /frame-ancestors 'none'/);
    assert.equal(headers["x-frame-options"], "DENY");
    assert.equal(headers["x-content-type-options"], "nosniff");
    await page.waitForSelector(".hero h1");
    await page.waitForSelector("#map .pin");                       // Leaflet loaded (script + SRI + CSP)
    await page.waitForFunction(() => document.querySelectorAll("#map img.leaflet-tile-loaded").length > 0); // map tiles allowed
    await page.waitForFunction(() => document.fonts.check('800 20px "Bricolage Grotesque"'));              // fonts allowed
    assert.deepEqual(problems.filter((p) => /Content Security Policy|Refused to/i.test(p)), []);

    // The Firebase SDK itself must also be loadable under the policy.
    const loaded = await page.evaluate(() => import("https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js").then(() => true, (e) => String(e)));
    assert.equal(loaded, true);
    // ...and something off the allow-list must not be.
    const blocked = await page.evaluate(() => fetch("https://example.com/").then(() => "allowed", () => "blocked"));
    assert.equal(blocked, "blocked");
  } finally {
    await browser.close();
  }
});
