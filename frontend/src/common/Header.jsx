import {
    Bell,
    MapPin,
    Menu
} from 'lucide-react';
import { Link } from 'react-router-dom';
import AccountMenu from './AccountMenu';

export default function Header({ onMenuClick }) {
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
                </Link>

                <div className="sms-header-divider" />

                {/* UC02 - account menu: user info, Profile, Change Password, Notifications, Logout */}
                <AccountMenu />
            </div>
        </header>
    );
}
