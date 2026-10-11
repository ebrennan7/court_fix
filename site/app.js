import { COURTS } from "./courts.js";

const statusMessage = document.querySelector("#status-message");
const signInPanel = document.querySelector("#sign-in-panel");
const appPanel = document.querySelector("#app-panel");
const preferencesForm = document.querySelector("#preferences-form");
const timeWindows = document.querySelector("#time-windows");
const notificationStatus = document.querySelector("#notification-status");
const savedMessage = document.querySelector("#saved-message");
const windowTemplate = document.querySelector("#time-window-template");

let auth;
let db;
let currentUser;
let vapidKey;
let modules;
let installPrompt;

function showStatus(message, isError = false) {
  statusMessage.textContent = message;
  statusMessage.classList.toggle("error", isError);
  statusMessage.classList.remove("hidden");
}

function hideStatus() {
  statusMessage.classList.add("hidden");
}

function setBusy(isBusy) {
  document.querySelectorAll("button, input, select").forEach((control) => {
    control.disabled = isBusy;
  });
}

function renderCourts(selectedIds = COURTS.map((court) => court.id)) {
  const courtList = document.querySelector("#court-list");
  courtList.replaceChildren();
  for (const court of COURTS) {
    const label = document.createElement("label");
    label.className = "court-option";
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.name = "court";
    checkbox.value = court.id;
    checkbox.checked = selectedIds.includes(court.id);
    const text = document.createElement("span");
    text.textContent = court.name.replace("Highbury Fields ", "");
    label.append(checkbox, text);
    courtList.append(label);
  }
}

function appendTimeWindow(window = {}) {
  const fragment = windowTemplate.content.cloneNode(true);
  const row = fragment.querySelector(".time-window-row");
  row.querySelector(".window-day").value = window.dayOfWeek ?? "MONDAY";
  row.querySelector(".window-start").value = window.startTime ?? "18:00";
  row.querySelector(".window-end").value = window.endTime ?? "20:00";
  row.querySelector(".remove-window").addEventListener("click", () => row.remove());
  timeWindows.append(row);
}

function renderTimeWindows(windows = []) {
  timeWindows.replaceChildren();
  for (const window of windows) appendTimeWindow(window);
}

function getTimeWindows() {
  return [...timeWindows.querySelectorAll(".time-window-row")].map((row) => ({
    dayOfWeek: row.querySelector(".window-day").value,
    startTime: row.querySelector(".window-start").value,
    endTime: row.querySelector(".window-end").value,
  }));
}

async function loadPreferences(user) {
  const { doc, getDoc } = modules.firestore;
  const snapshot = await getDoc(doc(db, "users", user.uid, "preferences", "main"));
  const data = snapshot.exists() ? snapshot.data() : {};
  renderCourts(data.courtIds ?? COURTS.map((court) => court.id));
  renderTimeWindows(data.windows ?? []);
}

async function savePreferences(event) {
  event.preventDefault();
  const courtIds = [...document.querySelectorAll('input[name="court"]:checked')].map((checkbox) => checkbox.value);
  const windows = getTimeWindows();
  if (courtIds.length === 0) {
    savedMessage.textContent = "Choose at least one court.";
    return;
  }
  if (windows.some((window) => window.startTime >= window.endTime)) {
    savedMessage.textContent = "Each time window must end after it starts.";
    return;
  }

  setBusy(true);
  savedMessage.textContent = "Saving…";
  try {
    const { doc, setDoc, serverTimestamp } = modules.firestore;
    await setDoc(doc(db, "users", currentUser.uid, "preferences", "main"), {
      courtIds,
      windows,
      updatedAt: serverTimestamp(),
    }, { merge: true });
    savedMessage.textContent = "Preferences saved.";
  } catch (error) {
    savedMessage.textContent = error.message;
  } finally {
    setBusy(false);
  }
}

function bytesToBase64Url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

function base64UrlToBytes(value) {
  const base64 = value.replaceAll("-", "+").replaceAll("_", "/");
  const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=");
  return Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
}

async function savePushSubscription(subscription) {
  const { doc, setDoc, serverTimestamp } = modules.firestore;
  const json = subscription.toJSON();
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(json.endpoint));
  const subscriptionId = bytesToBase64Url(new Uint8Array(digest));
  await setDoc(doc(db, "users", currentUser.uid, "subscriptions", subscriptionId), {
    endpoint: json.endpoint,
    keys: json.keys,
    expirationTime: json.expirationTime ?? null,
    userAgent: navigator.userAgent,
    updatedAt: serverTimestamp(),
    active: true,
  }, { merge: true });
}

function updateNotificationStatus(subscription) {
  const permission = Notification.permission;
  if (permission === "denied") {
    notificationStatus.textContent = "Notifications are blocked in this browser’s settings.";
  } else if (subscription) {
    notificationStatus.textContent = "Notifications are enabled on this device.";
  } else {
    notificationStatus.textContent = "Notifications aren’t enabled on this device.";
  }
}

async function enableNotifications() {
  if (!("serviceWorker" in navigator) || !("PushManager" in window) || !("Notification" in window)) {
    notificationStatus.textContent = "This browser doesn’t support web push notifications.";
    return;
  }
  if (!vapidKey || vapidKey === "YOUR_PUBLIC_VAPID_KEY") {
    notificationStatus.textContent = "Add the VAPID public key to site/firebase-config.js first.";
    return;
  }
  try {
    const permission = await Notification.requestPermission();
    if (permission !== "granted") {
      updateNotificationStatus(null);
      return;
    }
    const registration = await navigator.serviceWorker.ready;
    let subscription = await registration.pushManager.getSubscription();
    if (!subscription) {
      subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: base64UrlToBytes(vapidKey),
      });
    }
    await savePushSubscription(subscription);
    updateNotificationStatus(subscription);
  } catch (error) {
    notificationStatus.textContent = `Could not enable notifications: ${error.message}`;
  }
}

async function signIn(event) {
  event.preventDefault();
  const email = document.querySelector("#email").value.trim();
  const password = document.querySelector("#password").value;
  try {
    await modules.auth.signInWithEmailAndPassword(auth, email, password);
  } catch (error) {
    showStatus(error.message, true);
  }
}

async function sendPasswordReset() {
  const emailInput = document.querySelector("#email");
  const email = emailInput.value.trim();
  if (!email) {
    emailInput.reportValidity();
    return;
  }
  try {
    await modules.auth.sendPasswordResetEmail(auth, email);
    showStatus("If an account exists for that email address, a password reset link has been sent.");
  } catch (error) {
    showStatus(error.message, true);
  }
}

function initControls() {
  document.querySelector("#sign-in-form").addEventListener("submit", signIn);
  document.querySelector("#password-reset-button").addEventListener("click", sendPasswordReset);
  document.querySelector("#sign-out-button").addEventListener("click", () => modules.auth.signOut(auth));
  preferencesForm.addEventListener("submit", savePreferences);
  document.querySelector("#add-window-button").addEventListener("click", () => appendTimeWindow());
  document.querySelector("#enable-notifications-button").addEventListener("click", enableNotifications);
  document.querySelector("#select-all-courts").addEventListener("click", () => {
    document.querySelectorAll('input[name="court"]').forEach((checkbox) => { checkbox.checked = true; });
  });
}

async function startFirebase() {
  try {
    const config = await import("./firebase-config.js");
    if (!config.FIREBASE_CONFIG?.apiKey || config.FIREBASE_CONFIG.apiKey === "YOUR_API_KEY") {
      throw new Error("Firebase setup is incomplete. Copy firebase-config.example.js to firebase-config.js and add your Firebase web app values.");
    }
    const version = "12.19.0";
    const [appSdk, authSdk, firestoreSdk] = await Promise.all([
      import(`https://www.gstatic.com/firebasejs/${version}/firebase-app.js`),
      import(`https://www.gstatic.com/firebasejs/${version}/firebase-auth.js`),
      import(`https://www.gstatic.com/firebasejs/${version}/firebase-firestore.js`),
    ]);
    modules = { auth: authSdk, firestore: firestoreSdk };
    const firebaseApp = appSdk.initializeApp(config.FIREBASE_CONFIG);
    auth = authSdk.getAuth(firebaseApp);
    db = firestoreSdk.getFirestore(firebaseApp);
    vapidKey = config.VAPID_PUBLIC_KEY;
    initControls();
    authSdk.onAuthStateChanged(auth, async (user) => {
      currentUser = user;
      hideStatus();
      signInPanel.classList.toggle("hidden", Boolean(user));
      appPanel.classList.toggle("hidden", !user);
      if (!user) return;
      document.querySelector("#signed-in-email").textContent = user.email ?? "Signed in";
      try {
        await loadPreferences(user);
        if ("Notification" in window && "serviceWorker" in navigator) {
          const registration = await navigator.serviceWorker.ready;
          updateNotificationStatus(await registration.pushManager.getSubscription());
        }
      } catch (error) {
        showStatus(`Could not load your preferences: ${error.message}`, true);
      }
    });
  } catch (error) {
    signInPanel.classList.remove("hidden");
    showStatus(error.message, true);
  }
}

document.querySelector("#sign-in-form").addEventListener("submit", (event) => {
  if (!modules) {
    event.preventDefault();
    showStatus("Configure Firebase before signing in.", true);
  }
});

document.querySelector("#install-button").addEventListener("click", async () => {
  if (!installPrompt) return;
  installPrompt.prompt();
  await installPrompt.userChoice;
  installPrompt = null;
  document.querySelector("#install-button").classList.add("hidden");
});

window.addEventListener("beforeinstallprompt", (event) => {
  event.preventDefault();
  installPrompt = event;
  document.querySelector("#install-button").classList.remove("hidden");
});

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("./service-worker.js", { scope: "./" }).catch((error) => {
    showStatus(`Service worker could not start: ${error.message}`, true);
  });
}

const bookingRedirect = new URLSearchParams(window.location.search).get("booking");
if (bookingRedirect) {
  try {
    const bookingUrl = new URL(bookingRedirect);
    if (bookingUrl.protocol === "https:" && bookingUrl.hostname === "bookings.better.org.uk") {
      window.location.replace(bookingUrl.href);
    }
  } catch {
    // Ignore malformed notification links and continue loading the app.
  }
}

renderCourts();
startFirebase();
