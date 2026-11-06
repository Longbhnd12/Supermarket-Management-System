import { useEffect, useRef, useState } from 'react';
import { ShieldAlert, WifiOff, TriangleAlert, X } from 'lucide-react';
import { onAuthEvent } from '../services/authEvents';
import './AppNotice.css';

// Toast for errors handled centrally in api.js (403, 5xx, backend unreachable).
// The same message is shown only once while it is on screen (e.g. 5 requests failing together).
const ICONS = { forbidden: ShieldAlert, 'network-error': WifiOff, 'server-error': TriangleAlert };
const TITLES = { forbidden: 'Access denied', 'network-error': 'Connection problem', 'server-error': 'Server error' };
const SHOW_FOR_MS = 5000;

export default function AppNotice() {
    const [notices, setNotices] = useState([]);
    const timers = useRef(new Map());

    useEffect(() => {
        const activeTimers = timers.current;
        const dismiss = (key) => {
            clearTimeout(activeTimers.get(key));
            activeTimers.delete(key);
            setNotices((current) => current.filter((notice) => notice.key !== key));
        };
        const unsubscribe = onAuthEvent((event) => {
            if (!ICONS[event.type]) return;
            const key = `${event.type}:${event.message}`;
            setNotices((current) => (current.some((notice) => notice.key === key)
                ? current
                : [...current, { key, type: event.type, message: event.message }].slice(-3)));
            clearTimeout(activeTimers.get(key));
            activeTimers.set(key, setTimeout(() => dismiss(key), SHOW_FOR_MS));
        });
        return () => {
            unsubscribe();
            activeTimers.forEach((timer) => clearTimeout(timer));
            activeTimers.clear();
        };
    }, []);

    const close = (key) => {
        clearTimeout(timers.current.get(key));
        timers.current.delete(key);
        setNotices((current) => current.filter((notice) => notice.key !== key));
    };

    if (notices.length === 0) return null;

    return (
        <div className="sms-notice-stack" aria-live="assertive">
            {notices.map((notice) => {
                const Icon = ICONS[notice.type];
                return (
                    <div key={notice.key} className={`sms-notice sms-notice--${notice.type}`} role="alert">
                        <Icon size={20} aria-hidden="true" />
                        <div className="sms-notice-text">
                            <strong>{TITLES[notice.type]}</strong>
                            <span>{notice.message}</span>
                        </div>
                        <button type="button" onClick={() => close(notice.key)} aria-label="Close notification">
                            <X size={16} />
                        </button>
                    </div>
                );
            })}
        </div>
    );
}
