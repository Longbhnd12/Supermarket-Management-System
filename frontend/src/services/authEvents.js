// Small event bus between the Axios client (plain JS) and React (AuthProvider, AppNotice).
//
// In this tab:
//   'unauthorized'  - a request got 401: the session cookie is missing, expired or revoked
//   'forbidden'     - a request got 403 (not CSRF): MSG19
//   'server-error'  - 5xx response
//   'network-error' - the backend could not be reached
//
// Across tabs (same browser): 'login', 'logout', 'refresh' are sent with BroadcastChannel,
// or with the localStorage "storage" event in browsers without BroadcastChannel.

const listeners = new Set();

export function onAuthEvent(listener) {
    listeners.add(listener);
    return () => listeners.delete(listener);
}

export function emitAuthEvent(event) {
    listeners.forEach((listener) => listener(event));
}

const CHANNEL_NAME = 'sms-auth';
const STORAGE_KEY = 'sms-auth-event';

function openChannel() {
    try {
        return typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(CHANNEL_NAME);
    } catch {
        return null;
    }
}

const channel = openChannel();

// Tell the other tabs that the login state changed. The message never contains user data.
export function broadcastAuthChange(type) {
    const message = { type, at: Date.now() };
    if (channel) {
        channel.postMessage(message);
        return;
    }
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(message));
    } catch {
        // Storage blocked (private mode): other tabs will notice on their next request (401)
    }
}

export function onOtherTabAuthChange(listener) {
    if (channel) {
        const onMessage = (event) => listener(event.data?.type);
        channel.addEventListener('message', onMessage);
        return () => channel.removeEventListener('message', onMessage);
    }
    const onStorage = (event) => {
        if (event.key !== STORAGE_KEY || !event.newValue) return;
        try {
            listener(JSON.parse(event.newValue).type);
        } catch {
            // ignore malformed values
        }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
}
