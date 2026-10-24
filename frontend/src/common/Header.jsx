import {
    Bell,
    ChevronDown,
    MapPin,
    Menu,
    UserRound
} from 'lucide-react';

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
                <div
                    className="sms-header-notification"
                    title="Notifications will be available after authentication"
                >
                    <Bell size={20} />
                </div>

                <div className="sms-header-divider" />

                <div className="sms-user-info">
                    <strong>Guest User</strong>
                    <span>Authentication not configured</span>
                </div>

                <div className="sms-user-avatar">
                    <UserRound size={19} />
                </div>

                <ChevronDown
                    size={16}
                    className="sms-user-chevron"
                />
            </div>
        </header>
    );
}