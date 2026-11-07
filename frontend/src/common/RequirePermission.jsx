import { Outlet, useOutletContext } from 'react-router-dom';
import { useAuth } from '../context/authContext';
import { canAccess } from '../utils/pageAccess';
import AccessDeniedPage from '../pages/AccessDeniedPage';

/**
 * Route guard by permission (inside <ProtectedRoute>, so the user is already logged in).
 *
 * <Route element={<RequirePermission access={PAGE_ACCESS.users} />}>
 *     <Route path="/users" element={<UserListPage />} />
 * </Route>
 *
 * Without the permission the page is not loaded at all (no API call, no 403 toast):
 * the Access Denied page is shown instead, at the same URL.
 */
export default function RequirePermission({ access }) {
    const { currentUser } = useAuth();
    const context = useOutletContext();
    return canAccess(currentUser, access) ? <Outlet context={context} /> : <AccessDeniedPage />;
}
