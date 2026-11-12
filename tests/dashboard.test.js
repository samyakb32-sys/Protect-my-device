// End-to-end: the real website in a real browser, talking to the Auth, Firestore and Storage emulators.
// A second Firebase client in this process plays the part of the phone.
import test, { before, after } from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { readFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { extname, join, normalize } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";
import { initializeApp, deleteApp } from "firebase/app";
import { getAuth, connectAuthEmulator, signInWithEmailAndPassword } from "firebase/auth";
import { getFirestore, connectFirestoreEmulator, doc, setDoc, updateDoc, addDoc, collection, getDocs, serverTimestamp } from "firebase/firestore";
import { getStorage, connectStorageEmulator, ref, uploadBytes } from "firebase/storage";

const WEB = fileURLToPath(new URL("../web/", import.meta.url));
const TYPES = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css" };
const EMAIL = `owner${Date.now()}@example.com`, PASSWORD = "correct horse";
// 1x1 PNG, standing in for a front-camera photo.
const PHOTO = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==", "base64");

let server, base, browser, page, phone, uid;
const pageErrors = [];

before(async () => {
  server = http.createServer(async (req, res) => {
    const path = normalize(new URL(req.url, "http://x").pathname).replace(/^[/\\]+/, "") || "index.html";
    let body;
    try { body = await readFile(join(WEB, path)); } catch { return res.writeHead(404).end(); }
    res.writeHead(200, { "content-type": TYPES[extname(path)] || "application/octet-stream" }).end(body);
  }).listen(0, "127.0.0.1");
  await new Promise((r) => server.once("listening", r));
  base = `http://127.0.0.1:${server.address().port}/?emulator`;

  // Use a system Chromium when one is provided (CHROMIUM_PATH); otherwise Playwright's own download.
  const chrome = process.env.CHROMIUM_PATH || (existsSync("/opt/pw-browsers/chromium") ? "/opt/pw-browsers/chromium" : "");
  browser = await chromium.launch(chrome ? { executablePath: chrome } : {});
  page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  page.on("pageerror", (e) => pageErrors.push(e.message));

  const app = initializeApp({ apiKey: "emulator", projectId: "demo-protect", storageBucket: "demo-protect.appspot.com" }, "phone");
  phone = { app, auth: getAuth(app), db: getFirestore(app), storage: getStorage(app) };
  connectAuthEmulator(phone.auth, "http://127.0.0.1:9099", { disableWarnings: true });
  connectFirestoreEmulator(phone.db, "127.0.0.1", 8080);
  connectStorageEmulator(phone.storage, "127.0.0.1", 9199);
});
after(async () => {
  await browser?.close();
  server?.closeAllConnections();
  server?.close();
  if (phone) await deleteApp(phone.app); // otherwise its open Firestore stream keeps the test process alive
});

const commands = async () => (await getDocs(collection(phone.db, "devices/phone-1/commands"))).docs.map((d) => d.data());

test("signed-out visitors get the sign-in page, and empty forms are refused", async () => {
  await page.goto(base);
  await page.waitForSelector("#login:not([hidden])");
  assert.equal(await page.isHidden("#dash"), true);
  await page.click("button[type=submit]");
  assert.match(await page.textContent("#login-error"), /type your email/);
});

test("a wrong password is rejected with a plain message", async () => {
  await page.fill("#email", "nobody@example.com");
  await page.fill("#password", "whatever1");
  await page.click("button[type=submit]");
  await page.waitForSelector("#login-error:not([hidden])");
  assert.match(await page.textContent("#login-error"), /don't match an account/);
  assert.equal(await page.inputValue("#password"), "", "password field is cleared after a failed try");
});

test("password reset answers the same whether or not the account exists", async () => {
  await page.fill("#email", "nobody@example.com");
  await page.click("#forgot");
  await page.waitForSelector("#login-notice:not([hidden])");
  assert.match(await page.textContent("#login-notice"), /if nobody@example\.com has an account/);
});

test("creating an account signs you in to an empty dashboard", async () => {
  await page.fill("#email", EMAIL);
  await page.fill("#password", PASSWORD);
  await page.click("#signup");
  await page.waitForSelector("#dash:not([hidden])");
  assert.equal((await page.textContent("#who")).trim(), EMAIL);
  await page.waitForSelector("text=nothing here yet.");
});

test("a phone that registers shows up live, with battery, position, history, alerts and photo", async () => {
  uid = (await signInWithEmailAndPassword(phone.auth, EMAIL, PASSWORD)).user.uid;
  const spot = { lat: 19.0596, lng: 72.8295, accuracy: 14, time: serverTimestamp() };
  await setDoc(doc(phone.db, "devices/phone-1"), {
    owner: uid, name: "Test Phone <b>x</b>", fcmToken: "t", battery: 12, registeredAt: serverTimestamp(),
    lastLocation: spot, lastSeen: serverTimestamp(),
  });
  await addDoc(collection(phone.db, "devices/phone-1/locations"), spot);
  await addDoc(collection(phone.db, "devices/phone-1/locations"), { ...spot, lat: 19.0611, lng: 72.8322 });
  await uploadBytes(ref(phone.storage, "thieves/phone-1/shot.png"), PHOTO, { contentType: "image/png" });
  await addDoc(collection(phone.db, "devices/phone-1/events"), { type: "WRONG_PASSWORD", photoPath: "thieves/phone-1/shot.png", time: serverTimestamp() });

  await page.waitForSelector(".device b");
  // The name contains markup on purpose: it must be shown as text, never run as HTML.
  assert.equal(await page.textContent(".device b"), "Test Phone <b>x</b>");
  assert.equal(await page.locator(".device b b").count(), 0);
  assert.equal((await page.textContent(".device .sticker")).trim(), "12%");
  await page.waitForSelector(".hero h1");
  assert.equal(await page.textContent(".hero h1"), "all good.");
  await page.waitForSelector("text=19.05960, 72.82950");
  await page.waitForFunction(() => document.querySelectorAll(".list.scroll")[0].children.length === 2);
  await page.waitForSelector("text=wrong unlock attempt");
  await page.waitForFunction(() => { const i = document.querySelector(".list img"); return i && i.complete && i.naturalWidth > 0; });
  await page.waitForSelector("#map .pin");
});

test("every action button writes exactly the command it says", async () => {
  const buttons = { "locate now": "LOCATE", "alarm": "ALARM", "stop alarm": "STOP_ALARM", "lock": "LOCK", "lost mode": "LOST_MODE", "take photo": "PHOTO" };
  for (const [label, cmd] of Object.entries(buttons)) {
    await page.locator(".actions button", { hasText: new RegExp(`^${label}$`) }).click();
    await page.waitForFunction((t) => document.getElementById("toast").textContent.startsWith(t), `${{ LOCATE: "locate", ALARM: "alarm", STOP_ALARM: "stop", LOCK: "lock", LOST_MODE: "lost mode", PHOTO: "photo" }[cmd]} sent`);
  }
  const sent = await commands();
  assert.deepEqual(sent.map((c) => c.cmd).sort(), Object.values(buttons).sort());
  assert.equal(sent.find((c) => c.cmd === "LOST_MODE").message, "This phone is lost. Please call the owner.");
  assert.ok(sent.every((c) => c.time), "each command is timestamped by the server");
});

test("stolen mode: the button sends it, and the page flips when the phone confirms", async () => {
  await page.click("text=it's been stolen");
  await page.waitForFunction(() => document.getElementById("toast").textContent.startsWith("stolen mode sent"));
  assert.ok((await commands()).some((c) => c.cmd === "STOLEN_MODE"));
  assert.equal(await page.textContent(".hero h1"), "all good.", "no optimistic lie before the phone reports back");
  await updateDoc(doc(phone.db, "devices/phone-1"), { stolen: true });
  await page.waitForFunction(() => document.querySelector(".hero h1")?.textContent === "stolen mode.");
  await page.click("text=got it back");
  await page.waitForFunction(() => document.getElementById("toast").textContent.startsWith("all clear sent"));
  assert.ok((await commands()).some((c) => c.cmd === "SAFE"));
});

test("erase needs the word ERASE typed, and cancelling sends nothing", async () => {
  await page.click("text=erase everything");
  assert.equal(await page.isDisabled("#erase-go"), true);
  await page.fill("#erase-check", "erase");
  assert.equal(await page.isDisabled("#erase-go"), true, "lower case is not enough");
  await page.click("text=keep it");
  assert.ok(!(await commands()).some((c) => c.cmd === "WIPE"));

  await page.click("text=erase everything");
  assert.equal(await page.inputValue("#erase-check"), "", "confirmation is blank again each time");
  await page.fill("#erase-check", "ERASE");
  await page.click("#erase-go");
  await page.waitForFunction(() => document.getElementById("toast").textContent.startsWith("erase sent"));
  assert.equal((await commands()).filter((c) => c.cmd === "WIPE").length, 1);
});

test("signing out hides everything; a different account sees none of it", async () => {
  await page.click("#signout");
  await page.waitForSelector("#login:not([hidden])");
  assert.equal(await page.locator(".device").count(), 0);
  await page.fill("#email", `other${Date.now()}@example.com`);
  await page.fill("#password", PASSWORD);
  await page.click("#signup");
  await page.waitForSelector("text=nothing here yet.");
  assert.equal(await page.locator(".device").count(), 0);
});

test("the page ran without script errors", () => assert.deepEqual(pageErrors, []));
