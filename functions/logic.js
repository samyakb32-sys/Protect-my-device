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
  return `${deviceName || "Your phone"}: ${String(type || "alert").replaceAll("_", " ").toLowerCase()}`;
}

module.exports = { ALLOWED, commandMessage, alertBody };
