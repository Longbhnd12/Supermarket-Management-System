import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Bell, ChevronDown, KeyRound, LogOut, UserRound } from 'lucide-react';
import { useAuth } from '../context/authContext';
import { roleLabel as toRoleLabel } from '../utils/roleLabels';
import './AccountMenu.css';

// UC02 - Logout (SDD 4.2.3.1): account menu opened from the avatar/user name in the header
export default function AccountMenu() {
    const { currentUser: user, logout: endSession } = useAuth();
    const menuRef = useRef(null);
    const [open, setOpen] = useState(false);
    const [loggingOut, setLoggingOut] = useState(false);

    const fullName = user?.fullName || user?.username || 'User';
    const roleLabel = toRoleLabel(user?.role);

    // Close the menu when clicking outside it or pressing Escape
    useEffect(() => {
        if (!open) return undefined;
        const onClick = (event) => {
            if (menuRef.current && !menuRef.current.contains(event.target)) setOpen(false);
        };
        const onKey = (event) => event.key === 'Escape' && setOpen(false);
        document.addEventListener('mousedown', onClick);
        document.addEventListener('keydown', onKey);
        return () => {
            document.removeEventListener('mousedown', onClick);
            document.removeEventListener('keydown', onKey);
        };
    }, [open]);

    // AuthProvider calls the API, clears the user here and in the other tabs;
    // ProtectedRoute then shows the Login page.
    const logout = async () => {
        setLoggingOut(true);
        await endSession();
    };

    return (
        <div className="sms-account" ref={menuRef}>
            <button
                type="button"
                className="sms-account-trigger"
                onClick={() => setOpen((value) => !value)}
                aria-haspopup="menu"
                aria-expanded={open}
            >
                <div className="sms-user-info">
                    <strong>{fullName}</strong>
                    <span>{roleLabel}</span>
                </div>
                <div className="sms-user-avatar">
                    <UserRound size={19} />
                </div>
                <ChevronDown size={16} className="sms-user-chevron" />
            </button>

            {open && (
                <div className="sms-account-menu" role="menu">
                    <div className="sms-account-card">
                        <strong>{fullName}</strong>
                        <span>{user?.email}</span>
                        <span className="sms-account-role">{roleLabel}</span>
                    </div>
                    <Link to="/profile" role="menuitem" onClick={() => setOpen(false)}>
                        <UserRound size={16} /> Profile
                    </Link>
                    <Link to="/change-password" role="menuitem" onClick={() => setOpen(false)}>
                        <KeyRound size={16} /> Change Password
                    </Link>
                    <Link to="/notifications" role="menuitem" onClick={() => setOpen(false)}>
                        <Bell size={16} /> Notifications
                    </Link>
                    <button
                        type="button"
                        role="menuitem"
                        className="sms-account-logout"
                        onClick={logout}
                        disabled={loggingOut}
                    >
                        <LogOut size={16} /> {loggingOut ? 'Logging out…' : 'Logout'}
                    </button>
                </div>
            )}
        </div>
    );
}
