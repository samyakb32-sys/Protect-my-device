// Cloud Function logic + the three places the command list is written down must agree.
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";

const { ALLOWED, commandMessage, alertBody } = createRequire(import.meta.url)("../functions/logic.js");
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
