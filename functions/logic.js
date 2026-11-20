// Pure helpers, kept apart from index.js so they can be tested without Firebase.

/** Every command the phone understands. Must match Command.kt and firestore.rules. */
const ALLOWED = ["LOCATE", "LOCK", "ALARM", "STOP_ALARM", "LOST_MODE", "WIPE", "STOLEN_MODE", "SAFE", "PHOTO"];

/** The FCM message for a command document, or null if it must not be forwarded. */
function commandMessage(cmd, token) {
  if (!cmd || !ALLOWED.includes(cmd.cmd) || !token) return null;
  const message = typeof cmd.message === "string" ? cmd.message.slice(0, 500) : "";
  return { token, data: { cmd: cmd.cmd, message }, android: { priority: "high" } };
}

/** Notification text sent to the owner's other phones when something happens to one of them. */
function alertBody(deviceName, type) {
  const name = deviceName || "Your phone";
  if (type === "DEVICE_OFFLINE") return `${name} went offline. Its last spot is saved.`;
  if (type === "BACK_ONLINE") return `${name} is back online.`;
  return `${name}: ${String(type || "alert").replaceAll("_", " ").toLowerCase()}`;
}

/**
 * How long a phone can stay silent before it counts as offline: one missed check-in plus 15
 * minutes' slack. It checks in every 2.5 hours normally and every 10 minutes in stolen mode, so
 * that is 2 h 45 min, or 25 min in stolen mode. (A phone being switched off normally reports
 * itself straight away; this catches the ones that just go dark.)
 * Must match isOffline in web/app.js and DeviceInfo.isOffline in the app.
 */
function offlineAfterMs(stolen) {
  return ((stolen ? 10 : 150) + 15) * 60_000;
}

function isOffline(lastSeenMs, stolen, nowMs) {
  return lastSeenMs != null && nowMs - lastSeenMs > offlineAfterMs(stolen);
}

module.exports = { ALLOWED, commandMessage, alertBody, offlineAfterMs, isOffline };
