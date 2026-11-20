const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { commandMessage, alertBody, offlineAfterMs, isOffline } = require("./logic");

initializeApp();

// Owner writes devices/{id}/commands/{cid} -> forward to the phone as a high-priority FCM data message.
// firestore.rules already guarantees only the device's owner can create that document.
exports.forwardCommand = onDocumentCreated("devices/{deviceId}/commands/{cid}", async (event) => {
  const device = await getFirestore().doc(`devices/${event.params.deviceId}`).get();
  const message = commandMessage(event.data.data(), device.get("fcmToken"));
  if (message) await getMessaging().send(message);
});

// Thief/SIM events -> push notification to the owner's other registered devices.
exports.notifyOwner = onDocumentCreated("devices/{deviceId}/events/{eid}", async (event) => {
  const db = getFirestore();
  const device = await db.doc(`devices/${event.params.deviceId}`).get();
  const siblings = await db.collection("devices").where("owner", "==", device.get("owner")).get();
  const tokens = siblings.docs.filter((d) => d.id !== device.id).map((d) => d.get("fcmToken")).filter(Boolean);
  if (!tokens.length) return;
  await getMessaging().sendEachForMulticast({
    tokens,
    notification: { title: "Security alert", body: alertBody(device.get("name"), event.data.data().type) },
  });
});

// Every 10 minutes: a phone that has missed two check-ins is marked offline, once, with a
// DEVICE_OFFLINE event (which notifyOwner above turns into a push to the owner's other phones).
// The phone clears the flag itself on its next check-in and logs BACK_ONLINE.
exports.markOffline = onSchedule("every 10 minutes", async () => {
  const db = getFirestore();
  const now = Date.now();
  // The shortest threshold (stolen mode); the longer one for normal phones is checked per device.
  const stale = await db.collection("devices")
    .where("offline", "==", false)
    .where("lastSeen", "<", new Date(now - offlineAfterMs(true)))
    .get();
  for (const doc of stale.docs) {
    await db.runTransaction(async (tx) => {
      const fresh = await tx.get(doc.ref); // it may have checked in since the query ran
      const lastSeen = fresh.get("lastSeen");
      if (fresh.get("offline") !== false || !isOffline(lastSeen?.toMillis(), fresh.get("stolen") === true, now)) return;
      tx.update(doc.ref, { offline: true });
      tx.create(doc.ref.collection("events").doc(), {
        type: "DEVICE_OFFLINE", lastSeen, lastLocation: fresh.get("lastLocation") || null, time: FieldValue.serverTimestamp(),
      });
    });
  }
});
