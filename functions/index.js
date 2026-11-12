const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { commandMessage, alertBody } = require("./logic");

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
