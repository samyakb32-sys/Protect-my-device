// Security rules, run against the Firestore and Storage emulators (`npm test`).
// These are the real protection: anything the app or the website does can be imitated by someone
// who has taken them apart, so who-may-read-what has to hold on the server.
import test, { before, after, beforeEach } from "node:test";
import { readFileSync } from "node:fs";
import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc, deleteDoc, addDoc, collection, getDocs, query, where, serverTimestamp } from "firebase/firestore";
import { ref, uploadBytes, getBytes } from "firebase/storage";

const read = (p) => readFileSync(new URL(p, import.meta.url), "utf8");
let env, owner, stranger, nobody;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-protect",
    firestore: { rules: read("../firestore.rules") },
    storage: { rules: read("../storage.rules") },
  });
  // Each context's clients are created once: asking a context for firestore() again after it has
  // been used tries to reconfigure a running client and throws.
  const clients = (ctx) => ({ db: ctx.firestore(), files: ctx.storage() });
  owner = clients(env.authenticatedContext("owner"));
  stranger = clients(env.authenticatedContext("stranger"));
  nobody = clients(env.unauthenticatedContext());
});
after(() => env.cleanup());
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (admin) => {
    const db = admin.firestore();
    await setDoc(doc(db, "devices/phone"), { owner: "owner", name: "Pixel", fcmToken: "t" });
    await setDoc(doc(db, "devices/phone/locations/a"), { lat: 1, lng: 2 });
    await setDoc(doc(db, "devices/phone/events/a"), { type: "SIM_CHANGED" });
  });
});

const command = (extra = {}) => ({ cmd: "LOCK", message: "", time: serverTimestamp(), ...extra });

test("owner reads their device, its history and its events", async () => {
  const db = owner.db;
  await assertSucceeds(getDoc(doc(db, "devices/phone")));
  await assertSucceeds(getDocs(query(collection(db, "devices"), where("owner", "==", "owner"))));
  await assertSucceeds(getDocs(collection(db, "devices/phone/locations")));
  await assertSucceeds(getDocs(collection(db, "devices/phone/events")));
});

test("another signed-in account sees nothing of it", async () => {
  const db = stranger.db;
  await assertFails(getDoc(doc(db, "devices/phone")));
  await assertFails(getDocs(collection(db, "devices")));
  await assertFails(getDocs(query(collection(db, "devices"), where("owner", "==", "owner"))));
  await assertFails(getDocs(collection(db, "devices/phone/locations")));
  await assertFails(getDocs(collection(db, "devices/phone/events")));
  await assertFails(getDocs(collection(db, "devices/phone/commands")));
});

test("signed-out visitors see nothing and write nothing", async () => {
  const db = nobody.db;
  await assertFails(getDoc(doc(db, "devices/phone")));
  await assertFails(setDoc(doc(db, "devices/new"), { owner: "x" }));
  await assertFails(addDoc(collection(db, "devices/phone/commands"), command()));
});

test("nobody else can command the phone — not lock, not erase", async () => {
  const db = stranger.db;
  await assertFails(addDoc(collection(db, "devices/phone/commands"), command()));
  await assertFails(addDoc(collection(db, "devices/phone/commands"), command({ cmd: "WIPE" })));
  await assertFails(addDoc(collection(db, "devices/phone/locations"), { lat: 0, lng: 0 }));
});

test("owner can send every real command and nothing else", async () => {
  const cmds = collection(owner.db, "devices/phone/commands");
  for (const cmd of ["LOCATE", "LOCK", "ALARM", "STOP_ALARM", "LOST_MODE", "WIPE", "STOLEN_MODE", "SAFE", "PHOTO"]) {
    await assertSucceeds(addDoc(cmds, command({ cmd })));
  }
  await assertFails(addDoc(cmds, command({ cmd: "FORMAT_C" })));
  await assertFails(addDoc(cmds, command({ message: "x".repeat(501) })));
  await assertFails(addDoc(cmds, command({ message: 42 })));
  await assertFails(addDoc(cmds, command({ extra: "field" })));
});

test("commands, history and events can't be rewritten after the fact", async () => {
  const db = owner.db;
  const sent = await addDoc(collection(db, "devices/phone/commands"), command());
  await assertFails(updateDoc(sent, { cmd: "WIPE" }));
  await assertFails(deleteDoc(sent));
  await assertFails(updateDoc(doc(db, "devices/phone/locations/a"), { lat: 9 }));
  await assertFails(deleteDoc(doc(db, "devices/phone/events/a")));
});

test("a device can't be registered for, or handed to, another account", async () => {
  await assertSucceeds(setDoc(doc(stranger.db, "devices/theirs"), { owner: "stranger", name: "X" }));
  await assertFails(setDoc(doc(stranger.db, "devices/fake"), { owner: "owner", name: "X" }));
  await assertFails(updateDoc(doc(owner.db, "devices/phone"), { owner: "stranger" }));
  await assertFails(updateDoc(doc(stranger.db, "devices/phone"), { owner: "stranger" }));
  await assertSucceeds(updateDoc(doc(owner.db, "devices/phone"), { battery: 50, stolen: true }));
});

test("thief photos: only the owning account uploads or views them", async () => {
  const bytes = new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);
  const path = "thieves/phone/shot.jpg";
  await assertSucceeds(uploadBytes(ref(owner.files, path), bytes));
  await assertSucceeds(getBytes(ref(owner.files, path)));
  await assertFails(getBytes(ref(stranger.files, path)));
  await assertFails(getBytes(ref(nobody.files, path)));
  await assertFails(uploadBytes(ref(stranger.files, "thieves/phone/planted.jpg"), bytes));
  await assertFails(uploadBytes(ref(owner.files, "thieves/phone/huge.jpg"), new Uint8Array(5 * 1024 * 1024 + 1)));
  await assertFails(uploadBytes(ref(owner.files, "elsewhere/x.jpg"), bytes));
});
