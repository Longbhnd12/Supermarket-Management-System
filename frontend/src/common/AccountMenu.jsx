import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Bell, ChevronDown, KeyRound, LogOut, UserRound } from 'lucide-react';
import authService from '../services/authService';
import './AccountMenu.css';

// UC02 - Logout (SDD 4.2.3.1): account menu opened from the avatar/user name in the header
const ROLE_LABELS = {
    ADMIN: 'Admin',
    MANAGER: 'Manager',
    CASHIER: 'Cashier',
    WAREHOUSE_STAFF: 'Inventory Staff',
    SUPPLIER: 'Supplier'
};

export default function AccountMenu({ user }) {
    const navigate = useNavigate();
    const menuRef = useRef(null);
    const [open, setOpen] = useState(false);
    const [loggingOut, setLoggingOut] = useState(false);

    const fullName = user?.fullName || user?.username || 'User';
    const roleLabel = ROLE_LABELS[user?.role] || user?.role || '';

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

    const logout = async () => {
        setLoggingOut(true);
        try {
            await authService.logout();
        } catch {
            // Session may already be expired: still go back to Login
        } finally {
            navigate('/login', { replace: true });
        }
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
