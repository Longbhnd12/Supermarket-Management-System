import { useEffect, useState, useCallback } from 'react';
import {
    Bell,
    MapPin,
    Menu
} from 'lucide-react';
import { Link } from 'react-router-dom';
import AccountMenu from './AccountMenu';
import notificationService from '../services/notificationService';

export default function Header({ onMenuClick }) {
    const [unreadCount, setUnreadCount] = useState(0);

    const fetchUnread = useCallback(async () => {
        try {
            const res = await notificationService.getUnreadCount();
            const count = res?.data?.unreadCount ?? (typeof res?.data === 'number' ? res.data : 0);
            setUnreadCount(count);
        } catch {
            // Silently ignore if unauthenticated or error
        }
    }, []);

    useEffect(() => {
        void fetchUnread();
        const interval = setInterval(() => {
            void fetchUnread();
        }, 45000);

        const handleNotificationRead = () => {
            void fetchUnread();
        };
        window.addEventListener('notification-read', handleNotificationRead);

        return () => {
            clearInterval(interval);
            window.removeEventListener('notification-read', handleNotificationRead);
        };
    }, [fetchUnread]);

    return (
        <header className="sms-header">
            <div className="sms-header-left">
                <button
                    type="button"
                    className="sms-header-menu-button"
                    onClick={onMenuClick}
                    aria-label="Open navigation menu"
                >
                    <Menu size={21} />
                </button>

                <div className="sms-store-info">
                    <MapPin size={18} />

                    <div>
                        <strong>Supermarket Management</strong>
                        <span>Store Operations</span>
                    </div>
                </div>

                <div className="sms-environment-badge">
                    Development
                </div>
            </div>

            <div className="sms-header-right">
                <Link
                    to="/notifications"
                    className="sms-header-notification"
                    title="Notifications"
                >
                    <Bell size={20} />
                    {unreadCount > 0 && (
                        <span className="sms-header-notification-badge">
                            {unreadCount > 99 ? '99+' : unreadCount}
                        </span>
                    )}
                </Link>

                <div className="sms-header-divider" />

                {/* UC02 - account menu: user info, Profile, Change Password, Notifications, Logout */}
                <AccountMenu />
            </div>
        </header>
    );
}
