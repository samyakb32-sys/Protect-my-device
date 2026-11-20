// Web dashboard: sign in with the same account as the app, see every phone on it, send it commands.
// `?demo` in the URL runs the whole page on made-up data without touching Firebase.
import { firebaseConfig } from "./firebase-config.js";

const SDK = "https://www.gstatic.com/firebasejs/10.12.2";
const PARAMS = new URLSearchParams(location.search);
const DEMO = PARAMS.has("demo");
// `?emulator` points the page at the local Firebase emulators (see tests/). Only honoured on localhost.
const EMULATOR = PARAMS.has("emulator") && ["localhost", "127.0.0.1"].includes(location.hostname);
const LOST_MESSAGE = "This phone is lost. Please call the owner.";
const $ = (id) => document.getElementById(id);

/** Tiny DOM builder. Text always goes in as text, never as HTML: device names and messages are user data. */
function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === "class") node.className = v;
    else if (k.startsWith("on")) node.addEventListener(k.slice(2), v);
    else if (v !== false && v != null) node.setAttribute(k, v === true ? "" : v);
  }
  for (const c of children.flat()) if (c != null && c !== false) node.append(c);
  return node;
}

const EVENT_LABELS = {
  SIM_CHANGED: "SIM card was swapped",
  WRONG_PASSWORD: "wrong unlock attempt",
  STOLEN_MODE_ON: "stolen mode switched on",
  STOLEN_MODE_PHOTO: "photo taken as stolen mode started",
  MARKED_SAFE: "marked safe",
  REMOTE_PHOTO: "photo you asked for",
  POWERING_OFF: "phone was switched off",
  POWER_OFF_BLOCKED: "someone tried to power it off (blocked)",
  BATTERY_LOW: "battery running low",
  AIRPLANE_MODE_ON: "airplane mode switched on",
  AIRPLANE_MODE_PHOTO: "photo taken as airplane mode went on",
  WIPE_EXECUTED: "phone was erased",
};
const COMMAND_SENT = {
  LOCATE: "locate", ALARM: "alarm", STOP_ALARM: "stop", LOCK: "lock", LOST_MODE: "lost mode",
  PHOTO: "photo", STOLEN_MODE: "stolen mode", SAFE: "all clear", WIPE: "erase",
};

function ago(date) {
  if (!date) return "never";
  const min = Math.max(0, Math.round((Date.now() - date.getTime()) / 60000));
  if (min < 1) return "just now";
  if (min < 60) return `${min} min ago`;
  if (min < 48 * 60) return `${Math.round(min / 60)} h ago`;
  return `${Math.round(min / 1440)} d ago`;
}
const clock = (date) => date
  ? date.toLocaleString(undefined, { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })
  : "pending";

function toast(text) {
  const t = $("toast");
  t.textContent = text;
  t.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (t.hidden = true), 3200);
}

// ---------------------------------------------------------------- data sources
// Both expose the same surface, so the UI below doesn't know which one it has.

async function firebaseBackend() {
  const [{ initializeApp }, auth, fs, st] = await Promise.all([
    import(`${SDK}/firebase-app.js`), import(`${SDK}/firebase-auth.js`),
    import(`${SDK}/firebase-firestore.js`), import(`${SDK}/firebase-storage.js`),
  ]);
  const app = initializeApp(EMULATOR ? { apiKey: "emulator", projectId: "demo-protect", storageBucket: "demo-protect.appspot.com" } : firebaseConfig);
  const a = auth.getAuth(app), db = fs.getFirestore(app), storage = st.getStorage(app);
  if (EMULATOR) {
    auth.connectAuthEmulator(a, "http://127.0.0.1:9099", { disableWarnings: true });
    fs.connectFirestoreEmulator(db, "127.0.0.1", 8080);
    st.connectStorageEmulator(storage, "127.0.0.1", 9199);
  } else if (firebaseConfig.appCheckSiteKey) {
    // Optional: proves requests come from this site, not a script that copied the config.
    const ac = await import(`${SDK}/firebase-app-check.js`);
    ac.initializeAppCheck(app, { provider: new ac.ReCaptchaEnterpriseProvider(firebaseConfig.appCheckSiteKey), isTokenAutoRefreshEnabled: true });
  }
  const date = (ts) => (ts && ts.toDate ? ts.toDate() : null);
  const sub = (deviceId, name, max, map) => (cb) => fs.onSnapshot(
    fs.query(fs.collection(db, "devices", deviceId, name), fs.orderBy("time", "desc"), fs.limit(max)),
    (snap) => cb(snap.docs.map((d) => map(d.data()))),
    (err) => toast(err.message),
  );
  return {
    onUser: (cb) => auth.onAuthStateChanged(a, (u) => cb(u ? { uid: u.uid, email: u.email } : null)),
    signIn: (email, password) => auth.signInWithEmailAndPassword(a, email, password),
    signUp: async (email, password) => {
      const created = await auth.createUserWithEmailAndPassword(a, email, password);
      auth.sendEmailVerification(created.user).catch(() => {});
    },
    resetPassword: (email) => auth.sendPasswordResetEmail(a, email),
    signOut: () => auth.signOut(a),
    watchDevices: (uid, cb) => fs.onSnapshot(
      fs.query(fs.collection(db, "devices"), fs.where("owner", "==", uid)),
      (snap) => cb(snap.docs.map((d) => {
        const v = d.data(), last = v.lastLocation || {};
        return {
          id: d.id, name: v.name || d.id, battery: v.battery ?? null, stolen: v.stolen === true,
          lastSeen: date(v.lastSeen), registered: date(v.registeredAt),
          lat: last.lat ?? null, lng: last.lng ?? null, accuracy: last.accuracy ?? null,
        };
      })),
      (err) => toast(err.message),
    ),
    watchLocations: (id, cb) => sub(id, "locations", 60, (v) => ({ lat: v.lat, lng: v.lng, accuracy: v.accuracy, time: date(v.time) }))(cb),
    watchEvents: (id, cb) => sub(id, "events", 40, (v) => ({ type: v.type, photoPath: v.photoPath || null, time: date(v.time) }))(cb),
    photoUrl: (path) => st.getDownloadURL(st.ref(storage, path)),
    send: (id, cmd, message = "") =>
      fs.addDoc(fs.collection(db, "devices", id, "commands"), { cmd, message, time: fs.serverTimestamp() }),
  };
}

function demoBackend() {
  const t = (minAgo) => new Date(Date.now() - minAgo * 60000);
  const trail = [[19.0596, 72.8295], [19.0611, 72.8322], [19.0634, 72.8347], [19.0665, 72.8362], [19.0702, 72.8371], [19.0738, 72.8394], [19.076, 72.8427]];
  const devices = [
    { id: "9f3ac1d27b604e11", name: "Samsung Galaxy A54", battery: 12, stolen: true, lastSeen: t(4), registered: t(60 * 24 * 41), lat: trail[0][0], lng: trail[0][1], accuracy: 14 },
    { id: "27c0be5510aa93f4", name: "Nothing Phone (2a)", battery: 82, stolen: false, lastSeen: t(9), registered: t(60 * 24 * 12), lat: 19.1136, lng: 72.8697, accuracy: 22 },
  ];
  const shot = "data:image/svg+xml," + encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 200"><rect width="320" height="200" fill="#282826"/><circle cx="160" cy="84" r="34" fill="#4b4a45"/><path d="M92 200c6-46 34-68 68-68s62 22 68 68z" fill="#4b4a45"/><text x="160" y="188" font-family="monospace" font-size="11" fill="#a9a497" text-anchor="middle">SAMPLE PHOTO</text></svg>`);
  let listener = () => {};
  return {
    onUser: (cb) => cb({ uid: "demo", email: "you@example.com" }),
    signIn: async () => {}, signUp: async () => {}, resetPassword: async () => {}, signOut: async () => { location.search = ""; },
    watchDevices: (_uid, cb) => { listener = cb; cb(devices); return () => {}; },
    watchLocations: (id, cb) => {
      cb(id === devices[0].id
        ? trail.map(([lat, lng], i) => ({ lat, lng, accuracy: 12 + i * 3, time: t(4 + i * 10) }))
        : [{ lat: devices[1].lat, lng: devices[1].lng, accuracy: 22, time: t(9) }]);
      return () => {};
    },
    watchEvents: (id, cb) => {
      cb(id === devices[0].id ? [
        { type: "POWER_OFF_BLOCKED", time: t(6) },
        { type: "WRONG_PASSWORD", photoPath: "demo", time: t(31) },
        { type: "SIM_CHANGED", photoPath: "demo", time: t(52) },
        { type: "STOLEN_MODE_ON", time: t(58) },
      ] : []);
      return () => {};
    },
    photoUrl: async () => shot,
    send: async (id, cmd) => {
      const d = devices.find((x) => x.id === id);
      if (cmd === "STOLEN_MODE") d.stolen = true;
      if (cmd === "SAFE") d.stolen = false;
      listener([...devices]);
    },
  };
}

// ---------------------------------------------------------------- UI

let api, user = null, devices = [], selected = null, locations = [], events = [];
let stopDevices = () => {}, stopDetail = () => {};
let map = null, mapLayer = null, mapFor = null, mapNode = null; // mapNode is reused across renders so Leaflet keeps its state

function show(view) {
  for (const id of ["login", "setup", "dash"]) $(id).hidden = id !== view;
}

function select(id) {
  if (id === selected) return;
  selected = id;
  locations = []; events = [];
  stopDetail();
  if (id) {
    const a = api.watchLocations(id, (l) => { locations = l; renderDetail(); });
    const b = api.watchEvents(id, (e) => { events = e; renderDetail(); });
    stopDetail = () => { a(); b(); };
  }
  renderDevices();
  renderDetail();
}

function batteryChip(pct) {
  if (pct == null) return el("span", { class: "sticker plain" }, "battery --");
  const tone = pct <= 15 ? "coral" : pct <= 35 ? "hazard" : "";
  return el("span", { class: `sticker ${tone}` }, `${pct}%`);
}

function renderDevices() {
  const list = $("devices");
  list.replaceChildren(...(devices.length ? devices.map((d) =>
    el("button", { class: "box device", type: "button", "aria-current": String(d.id === selected), onclick: () => select(d.id) },
      el("span", { class: "icon" }),
      el("span", {}, el("b", {}, d.name), el("span", { class: "tag" }, `seen ${ago(d.lastSeen)}`)),
      el("span", { class: "chips" }, batteryChip(d.battery), d.stolen && el("span", { class: "sticker hazard" }, "stolen")),
    )) : [el("div", { class: "box pad" }, el("b", {}, "nothing here yet."),
      el("p", { class: "fine" }, "install the app on a phone and sign in with this account. it shows up here."))]));
}

async function send(device, cmd) {
  try {
    await api.send(device.id, cmd, cmd === "LOST_MODE" || cmd === "STOLEN_MODE" ? LOST_MESSAGE : "");
    toast(`${COMMAND_SENT[cmd]} sent to ${device.name}`);
  } catch (err) {
    toast(err.message || "couldn't send that");
  }
}

function askErase(device) {
  const dlg = $("erase"), check = $("erase-check"), go = $("erase-go");
  $("erase-title").textContent = `erase ${device.name}?`;
  check.value = ""; go.disabled = true;
  check.oninput = () => (go.disabled = check.value.trim() !== "ERASE");
  dlg.onclose = () => { if (dlg.returnValue === "go" && check.value.trim() === "ERASE") send(device, "WIPE"); };
  dlg.showModal();
}

function drawMap(device) {
  const holder = $("map");
  if (!holder || !window.L) return;
  const points = locations.filter((p) => p.lat != null && p.lng != null);
  if (!points.length && device.lat != null) points.push({ lat: device.lat, lng: device.lng, time: device.lastSeen });
  if (!points.length) return;
  if (!map || map.getContainer() !== holder) {
    map = L.map(holder, { scrollWheelZoom: false });
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", { maxZoom: 19, attribution: "&copy; OpenStreetMap contributors" }).addTo(map);
    mapLayer = L.layerGroup().addTo(map);
    mapFor = null;
  }
  mapLayer.clearLayers();
  const path = points.map((p) => [p.lat, p.lng]);
  if (path.length > 1) L.polyline(path, { color: "#111111", weight: 4, dashArray: "2 9", lineCap: "round" }).addTo(mapLayer);
  points.slice(1).forEach((p) => L.circleMarker([p.lat, p.lng], { radius: 5, color: "#111111", weight: 2, fillColor: "#ffffff", fillOpacity: 1 })
    .bindTooltip(clock(p.time)).addTo(mapLayer));
  L.marker(path[0], { icon: L.divIcon({ className: "", html: `<div class="pin${device.stolen ? " stolen" : ""}"></div>`, iconSize: [22, 22], iconAnchor: [11, 11] }) })
    .bindTooltip(`latest · ${clock(points[0].time)}`).addTo(mapLayer);
  // Only re-frame when switching device, so a new ping doesn't yank the map away from where you were looking.
  if (mapFor !== device.id) {
    if (path.length > 1) map.fitBounds(path, { padding: [40, 40], maxZoom: 17 }); else map.setView(path[0], 16);
    mapFor = device.id;
  }
}

function renderDetail() {
  const root = $("detail");
  const d = devices.find((x) => x.id === selected);
  if (!d) { root.replaceChildren(); map = null; return; }
  const hasSpot = d.lat != null && d.lng != null;
  const button = (label, cmd, tone = "card") => el("button", { class: `btn ${tone}`, type: "button", onclick: () => send(d, cmd) }, label);

  const hero = el("div", { class: `box hero ${d.stolen ? "hazard" : "lime"}` },
    el("span", { class: "sticker ink", style: `color: var(--${d.stolen ? "hazard" : "lime"})` }, "status"),
    el("h1", {}, d.stolen ? "stolen mode." : "all good."),
    el("p", {}, d.stolen
      ? "locked, showing the lost screen, and reporting where it is every 10 minutes while it has power and internet."
      : `${d.name} last checked in ${ago(d.lastSeen)}. if it goes missing, hit the button.`),
    el("div", { class: "row" }, d.stolen
      ? el("button", { class: "btn lime", type: "button", onclick: () => send(d, "SAFE") }, "got it back — all clear")
      : el("button", { class: "btn card", type: "button", style: "color: var(--ink); background: #fff", onclick: () => send(d, "STOLEN_MODE") }, "it's been stolen")),
  );

  const mapBox = el("div", { class: "box", style: "padding: 6px" },
    hasSpot || locations.length ? (mapNode ||= el("div", { id: "map" }))
      : el("div", { class: "map-empty" }, "no location yet. it shows up after the phone's first report."));

  const actions = el("div", { class: "box pad" }, el("div", { class: "actions" },
    button("locate now", "LOCATE", "lime"), button("alarm", "ALARM", "hazard"), button("stop alarm", "STOP_ALARM"),
    button("lock", "LOCK", "lilac"), button("lost mode", "LOST_MODE"), button("take photo", "PHOTO"),
    el("button", { class: "btn coral full", type: "button", onclick: () => askErase(d) }, "erase everything"),
  ));

  const info = el("div", { class: "box pad" }, el("dl", {},
    el("dt", {}, "device"), el("dd", {}, d.name),
    el("dt", {}, "battery"), el("dd", {}, d.battery == null ? "unknown" : `${d.battery}%`),
    el("dt", {}, "last seen"), el("dd", {}, d.lastSeen ? `${ago(d.lastSeen)} · ${clock(d.lastSeen)}` : "never"),
    el("dt", {}, "position"), el("dd", {}, hasSpot
      ? [el("span", { class: "mono" }, `${d.lat.toFixed(5)}, ${d.lng.toFixed(5)}`), d.accuracy != null ? ` ±${Math.round(d.accuracy)} m · ` : " · ",
        el("a", { href: `https://www.google.com/maps?q=${d.lat},${d.lng}`, target: "_blank", rel: "noopener noreferrer" }, "open in maps")]
      : "none yet"),
    el("dt", {}, "added"), el("dd", {}, d.registered ? clock(d.registered) : "unknown"),
    el("dt", {}, "device id"), el("dd", { class: "mono" }, d.id),
  ));

  const history = el("div", { class: "box" },
    el("div", { class: "head" }, el("h3", {}, "where it's been"), el("span", { class: "tag" }, `${locations.length} pings`)),
    el("ul", { class: "list scroll" }, locations.length ? locations.map((p) => el("li", {},
      el("a", { class: "mono", href: `https://www.google.com/maps?q=${p.lat},${p.lng}`, target: "_blank", rel: "noopener noreferrer" },
        `${Number(p.lat).toFixed(5)}, ${Number(p.lng).toFixed(5)}`),
      el("span", { class: "when" }, clock(p.time)))) : el("li", {}, el("span", { class: "empty" }, "no pings yet."))));

  const feed = el("div", { class: "box" },
    el("div", { class: "head" }, el("h3", {}, "what happened"), el("span", { class: "tag" }, "alerts + photos")),
    el("ul", { class: "list scroll" }, events.length ? events.map((e) => {
      const li = el("li", {}, el("b", {}, EVENT_LABELS[e.type] || String(e.type).replaceAll("_", " ").toLowerCase()),
        el("span", { class: "when" }, clock(e.time)));
      if (e.photoPath) {
        const img = el("img", { alt: "Photo taken by the phone's front camera", loading: "lazy" });
        api.photoUrl(e.photoPath).then((url) => (img.src = url)).catch(() => img.remove());
        li.append(img);
      }
      return li;
    }) : el("li", {}, el("span", { class: "empty" }, "nothing yet. SIM swaps, wrong unlock attempts and power-offs land here."))));

  root.replaceChildren(hero, mapBox, actions, el("div", { class: "cols" }, info, history), feed);
  drawMap(d);
}

function authMessage(err) {
  const code = err.code || "";
  if (/invalid-credential|wrong-password|user-not-found/.test(code)) return "that email and password don't match an account.";
  if (/email-already-in-use/.test(code)) return "that email already has an account. sign in instead.";
  if (/weak-password/.test(code)) return "pick a longer password — at least 6 characters.";
  if (/invalid-email/.test(code)) return "that doesn't look like an email address.";
  if (/too-many-requests/.test(code)) return "too many tries. wait a minute and try again.";
  return err.message || "that didn't work.";
}

function wire() {
  const box = $("login-error"), note = $("login-notice");
  const say = (target, text) => { box.hidden = note.hidden = true; if (text) { target.textContent = text; target.hidden = false; } };
  async function attempt(action, button) {
    const email = $("email").value.trim(), password = $("password").value;
    say(box, "");
    if (!email || !password) return say(box, "type your email and a password first.");
    button.disabled = true;
    try {
      await action(email, password);
    } catch (err) {
      say(box, authMessage(err));
    } finally {
      button.disabled = false;
      $("password").value = "";
    }
  }
  $("login-form").addEventListener("submit", (ev) => {
    ev.preventDefault();
    attempt(api.signIn, ev.target.querySelector("button[type=submit]"));
  });
  $("signup").addEventListener("click", (ev) => attempt(api.signUp, ev.currentTarget));
  $("forgot").addEventListener("click", async () => {
    const email = $("email").value.trim();
    if (!email) return say(box, "type your email above, then press this again.");
    // Same answer whether or not the address has an account, so this can't be used to probe for one.
    await api.resetPassword(email).catch(() => {});
    say(note, `if ${email} has an account, a reset link is on its way.`);
  });
  $("signout").addEventListener("click", () => api.signOut());
  setInterval(() => { if (user) { renderDevices(); } }, 60000); // keep "seen 4 min ago" honest
}

async function start() {
  if (!DEMO && !EMULATOR && !firebaseConfig.apiKey) return show("setup");
  api = DEMO ? demoBackend() : await firebaseBackend();
  $("demo-flag").hidden = !DEMO;
  wire();
  api.onUser((u) => {
    user = u;
    stopDevices(); stopDetail();
    devices = []; selected = null; map = null; mapNode = null; locations = []; events = [];
    // Clear the page itself, not just the variables: a signed-out tab must hold none of the last account's data.
    renderDevices(); renderDetail();
    if (!u) return show("login");
    $("who").textContent = u.email || "";
    show("dash");
    stopDevices = api.watchDevices(u.uid, (list) => {
      // Stolen phones float to the top; otherwise most recently seen first.
      devices = list.sort((a, b) => (b.stolen - a.stolen) || ((b.lastSeen?.getTime() || 0) - (a.lastSeen?.getTime() || 0)));
      if (!devices.some((d) => d.id === selected)) { selected = null; select(devices[0]?.id || null); }
      renderDevices();
      renderDetail();
    });
  });
}

start().catch((err) => { console.error(err); show("setup"); });
