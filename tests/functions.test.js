// Cloud Function logic + the three places the command list is written down must agree.
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";

const { ALLOWED, commandMessage, alertBody, offlineAfterMs, isOffline } = createRequire(import.meta.url)("../functions/logic.js");
const read = (p) => readFileSync(new URL(p, import.meta.url), "utf8");

test("known commands are forwarded as high-priority data messages", () => {
  assert.deepEqual(commandMessage({ cmd: "LOCK", message: "" }, "tok"), {
    token: "tok", data: { cmd: "LOCK", message: "" }, android: { priority: "high" },
  });
});

test("unknown commands, missing tokens and junk are dropped", () => {
  assert.equal(commandMessage({ cmd: "FORMAT_C" }, "tok"), null);
  assert.equal(commandMessage({ cmd: "LOCK" }, undefined), null);
  assert.equal(commandMessage(undefined, "tok"), null);
});

test("message is coerced to a bounded string", () => {
  assert.equal(commandMessage({ cmd: "LOST_MODE", message: { evil: 1 } }, "t").data.message, "");
  assert.equal(commandMessage({ cmd: "LOST_MODE", message: "x".repeat(900) }, "t").data.message.length, 500);
});

test("alert text is readable", () => {
  assert.equal(alertBody("Pixel 8", "SIM_CHANGED"), "Pixel 8: sim changed");
  assert.equal(alertBody("Pixel 8", "POWER_OFF_BLOCKED"), "Pixel 8: power off blocked");
  assert.equal(alertBody(undefined, undefined), "Your phone: alert");
  assert.equal(alertBody("Pixel 8", "DEVICE_OFFLINE"), "Pixel 8 went offline. Its last spot is saved.");
  assert.equal(alertBody("Pixel 8", "BACK_ONLINE"), "Pixel 8 is back online.");
});

test("a phone is offline after one missed check-in plus 15 minutes", () => {
  const now = Date.parse("2026-11-20T12:00:00Z"), min = 60_000;
  assert.equal(offlineAfterMs(false), 165 * min); // checks in every 2.5 h
  assert.equal(offlineAfterMs(true), 25 * min); // every 10 min in stolen mode
  assert.equal(isOffline(now - 164 * min, false, now), false);
  assert.equal(isOffline(now - 166 * min, false, now), true);
  assert.equal(isOffline(now - 26 * min, true, now), true, "stolen phones are flagged sooner");
  assert.equal(isOffline(null, false, now), false, "never checked in is not the same as offline");
});

test("phone, function and dashboard use the same offline threshold", () => {
  const web = read("../web/app.js");
  assert.match(web, /\(\(d\.stolen \? 10 : 150\) \+ 15\) \* 60000/, "web/app.js");
  const kotlin = read("../app/src/main/java/com/protectmydevice/data/DeviceRepository.kt");
  assert.match(kotlin, /\(\(if \(stolen\) 10 else 150\) \+ 15\) \* 60_000L/, "DeviceRepository.kt");
});

test("phone, function, rules and dashboard all know the same commands", () => {
  const sorted = [...ALLOWED].sort();
  const kotlin = read("../app/src/main/java/com/protectmydevice/data/Command.kt");
  const enumBody = kotlin.slice(kotlin.indexOf("enum class Command {"), kotlin.indexOf("companion object"));
  assert.deepEqual(enumBody.replace(/\/\*\*[\s\S]*?\*\//g, "").match(/\b[A-Z][A-Z_]{2,}\b/g).sort(), sorted, "Command.kt");
  const rules = read("../firestore.rules");
  const list = rules.slice(rules.indexOf("request.resource.data.cmd in"));
  assert.deepEqual(list.slice(list.indexOf("["), list.indexOf("]")).match(/[A-Z_]{3,}/g).sort(), sorted, "firestore.rules");
  const web = read("../web/app.js");
  const sent = web.slice(web.indexOf("const COMMAND_SENT = {"), web.indexOf("};", web.indexOf("const COMMAND_SENT = {")));
  assert.deepEqual(sent.match(/\b[A-Z][A-Z_]{2,}(?=:)/g).sort(), sorted, "web/app.js");
});
