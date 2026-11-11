const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const ALLOWED = ["LOCATE", "LOCK", "ALARM", "STOP_ALARM", "LOST_MODE", "WIPE", "STOLEN_MODE", "SAFE", "PHOTO"];

// Owner writes devices/{id}/commands/{cid} -> forward to the phone as a high-priority FCM data message.
exports.forwardCommand = onDocumentCreated("devices/{deviceId}/commands/{cid}", async (event) => {
  const cmd = event.data.data();
  if (!ALLOWED.includes(cmd.cmd)) return;
  const device = await getFirestore().doc(`devices/${event.params.deviceId}`).get();
  const token = device.get("fcmToken");
  if (!token) return;
  await getMessaging().send({
    token,
    data: { cmd: cmd.cmd, message: cmd.message || "" },
    android: { priority: "high" },
  });
});

// Thief/SIM events -> push notification to the owner's other registered devices.
exports.notifyOwner = onDocumentCreated("devices/{deviceId}/events/{eid}", async (event) => {
  const ev = event.data.data();
  const db = getFirestore();
  const device = await db.doc(`devices/${event.params.deviceId}`).get();
  const siblings = await db.collection("devices").where("owner", "==", device.get("owner")).get();
  const tokens = siblings.docs.filter((d) => d.id !== device.id).map((d) => d.get("fcmToken")).filter(Boolean);
  if (!tokens.length) return;
  await getMessaging().sendEachForMulticast({
    tokens,
    notification: {
      title: "Security alert",
      body: `${device.get("name")}: ${String(ev.type).replace("_", " ").toLowerCase()}`,
    },
  });
});
