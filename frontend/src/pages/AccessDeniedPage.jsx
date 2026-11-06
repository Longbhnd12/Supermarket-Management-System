import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, House, ShieldAlert } from 'lucide-react';
import { useAuth } from '../context/authContext';
import { getHomePathByRole } from '../utils/authRedirect';
import './AccessDeniedPage.css';

// Shown by <RequirePermission> when the role does not have the permission of the page (MSG19).
export default function AccessDeniedPage() {
    const navigate = useNavigate();
    const { currentUser } = useAuth();

    return (
        <section className="access-denied" aria-labelledby="access-denied-title">
            <div className="access-denied-icon" aria-hidden="true">
                <ShieldAlert size={30} />
            </div>
            <h1 id="access-denied-title">Access denied</h1>
            <p>You do not have permission to perform this action.</p>
            <p className="access-denied-hint">
                If you need this page for your work, ask an Admin to update the permissions of your role.
            </p>
            <div className="access-denied-actions">
                <button type="button" className="access-denied-btn" onClick={() => navigate(-1)}>
                    <ArrowLeft size={16} /> Go back
                </button>
                <Link to={getHomePathByRole(currentUser?.role)} className="access-denied-btn access-denied-btn--primary">
                    <House size={16} /> Go to home page
                </Link>
            </div>
        </section>
    );
}
