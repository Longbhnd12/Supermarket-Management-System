import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Pencil, Plus, Power, RefreshCw, Search, ShieldCheck, Trash2 } from 'lucide-react';
import UserService from '../../services/userService';
import Pagination from '../../components/ui/Pagination';
import { getAccessAwareErrorMessage } from '../../utils/errorUtils';
import { roleLabel } from '../../utils/roleLabels';
import ConfirmDialog from './ConfirmDialog';
import Can from '../../common/Can';
import './UserManagement.css';

// UC04 - View Users (SRS 3.3.1) + row actions of UC05 - Manage Users (activate / deactivate / delete)
const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE', 'LOCKED'];
const EMPTY_RESULT = { content: [], page: 0, totalElements: 0, totalPages: 0 };

function formatDateTime(value) {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    const pad = (n) => String(n).padStart(2, '0');
    return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function StatusBadge({ status }) {
    const cls = status === 'ACTIVE' ? 'um-badge-active' : status === 'LOCKED' ? 'um-badge-locked' : 'um-badge-inactive';
    const label = status ? status.charAt(0) + status.slice(1).toLowerCase() : '';
    return <span className={`um-badge ${cls}`}>{label}</span>;
}

export default function UserListPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const [roles, setRoles] = useState([]);
    const [searchInput, setSearchInput] = useState('');
    const [filters, setFilters] = useState({ search: '', roleId: '', status: '' });
    const [paging, setPaging] = useState({ page: 0, size: 10 });
    const [reloadKey, setReloadKey] = useState(0);
    // Result of the last finished request, tagged with the query it belongs to
    const [loaded, setLoaded] = useState({ key: null, result: EMPTY_RESULT, error: '' });
    const [actionError, setActionError] = useState('');
    const [notice, setNotice] = useState(location.state?.notice || '');
    const [confirm, setConfirm] = useState(null); // { type: 'status' | 'delete', user }
    const [busy, setBusy] = useState(false);

    // Clear the notice passed from the form page so it does not come back on refresh
    useEffect(() => {
        if (location.state?.notice) navigate(location.pathname, { replace: true, state: null });
    }, [location.pathname, location.state, navigate]);

    useEffect(() => {
        UserService.getOptions()
            .then(({ data }) => setRoles(data.data?.roles || []))
            .catch(() => setRoles([]));
    }, []);

    const queryKey = useMemo(
        () => JSON.stringify({ ...filters, ...paging, reloadKey }),
        [filters, paging, reloadKey]
    );
    const loading = loaded.key !== queryKey;
    const result = loaded.result;
    const error = actionError || loaded.error;

    useEffect(() => {
        let active = true;
        UserService.search({
            search: filters.search || undefined,
            roleId: filters.roleId || undefined,
            status: filters.status || undefined,
            page: paging.page,
            size: paging.size
        })
            .then(({ data }) => active && setLoaded({ key: queryKey, result: data.data, error: '' }))
            .catch((err) => active && setLoaded({ key: queryKey, result: EMPTY_RESULT, error: getAccessAwareErrorMessage(err) }));
        return () => { active = false; };
    }, [queryKey, filters, paging]);

    const reload = () => setReloadKey((key) => key + 1);

    const applySearch = (event) => {
        event.preventDefault();
        setPaging((current) => ({ ...current, page: 0 }));
        setFilters((current) => ({ ...current, search: searchInput.trim() }));
    };

    const changeFilter = (field) => (event) => {
        setPaging((current) => ({ ...current, page: 0 }));
        setFilters((current) => ({ ...current, [field]: event.target.value }));
    };

    const runConfirmed = async () => {
        if (!confirm) return;
        setBusy(true);
        setActionError('');
        try {
            if (confirm.type === 'delete') {
                await UserService.delete(confirm.user.userId);
                setNotice('Deleted successfully.');
            } else {
                const target = confirm.user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
                await UserService.updateStatus(confirm.user.userId, target);
                setNotice('Updated successfully.');
            }
            setConfirm(null);
            reload();
        } catch (err) {
            setConfirm(null);
            setActionError(getAccessAwareErrorMessage(err));
        } finally {
            setBusy(false);
        }
    };

    const users = result?.content || [];

    return (
        <div className="um-page">
            <div className="um-header">
                <div>
                    <h1>User Management</h1>
                    <p>View, search and manage the accounts that can sign in to the system.</p>
                </div>
                <div className="um-actions">
                    <Can permission="ROLE_MANAGE">
                        <Link to="/roles" className="um-btn um-btn-outline"><ShieldCheck size={16} /> Roles & Permissions</Link>
                    </Can>
                    <Can permission="USER_MANAGE">
                        <Link to="/users/new" className="um-btn um-btn-primary"><Plus size={16} /> Create User</Link>
                    </Can>
                </div>
            </div>

            {notice && <p className="um-alert um-alert-success" role="status">{notice}</p>}
            {error && <p className="um-alert um-alert-error" role="alert">{error}</p>}

            <form className="um-card um-filters" onSubmit={applySearch}>
                <label className="um-field um-search">
                    Search
                    <Search size={16} />
                    <input
                        value={searchInput}
                        onChange={(event) => setSearchInput(event.target.value)}
                        placeholder="Username or email"
                        maxLength={100}
                    />
                </label>
                <label className="um-field">
                    Role
                    <select value={filters.roleId} onChange={changeFilter('roleId')}>
                        <option value="">All roles</option>
                        {roles.map((role) => <option key={role.roleId} value={role.roleId}>{roleLabel(role.roleName)}</option>)}
                    </select>
                </label>
                <label className="um-field">
                    Status
                    <select value={filters.status} onChange={changeFilter('status')}>
                        <option value="">All statuses</option>
                        {STATUS_OPTIONS.map((status) => <option key={status} value={status}>{status.charAt(0) + status.slice(1).toLowerCase()}</option>)}
                    </select>
                </label>
                <div className="um-actions">
                    <button type="submit" className="um-btn um-btn-primary"><Search size={16} /> Search</button>
                    <button type="button" className="um-btn um-btn-outline" onClick={reload} disabled={loading} title="Refresh">
                        <RefreshCw size={16} />
                    </button>
                </div>
            </form>

            <div className="um-card">
                <div className="um-table-wrap">
                    <table className="um-table">
                        <thead>
                            <tr>
                                <th>Username</th>
                                <th>Email</th>
                                <th>Role</th>
                                <th>Linked to</th>
                                <th>Status</th>
                                <th>Last Login</th>
                                <th>Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {loading && <tr><td colSpan={7} className="um-empty">Loading users…</td></tr>}
                            {!loading && users.length === 0 && <tr><td colSpan={7} className="um-empty">No users found</td></tr>}
                            {!loading && users.map((user) => (
                                <tr key={user.userId}>
                                    <td className="um-username">{user.username}</td>
                                    <td>{user.email}</td>
                                    <td><span className="um-badge um-badge-role">{roleLabel(user.roleName)}</span></td>
                                    <td className="um-muted">{user.employeeFullName || user.supplierName || '—'}</td>
                                    <td><StatusBadge status={user.status} /></td>
                                    <td className="um-muted">{formatDateTime(user.lastLoginAt)}</td>
                                    <td>
                                        {/* UC05 actions: USER_MANAGE only (USER_VIEW can only look) */}
                                        <Can permission="USER_MANAGE" fallback={<span className="um-muted">—</span>}>
                                        <div className="um-actions">
                                            <Link to={`/users/${user.userId}/edit`} className="um-icon-btn" title="Edit"><Pencil size={15} /></Link>
                                            <button
                                                type="button"
                                                className="um-icon-btn"
                                                title={user.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                                                onClick={() => setConfirm({ type: 'status', user })}
                                            >
                                                <Power size={15} />
                                            </button>
                                            <button
                                                type="button"
                                                className="um-icon-btn danger"
                                                title="Delete"
                                                onClick={() => setConfirm({ type: 'delete', user })}
                                            >
                                                <Trash2 size={15} />
                                            </button>
                                        </div>
                                        </Can>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
                <Pagination
                    page={result?.page ?? paging.page}
                    size={paging.size}
                    totalElements={result?.totalElements ?? 0}
                    totalPages={result?.totalPages ?? 0}
                    pageSizeOptions={[10, 20, 50]}
                    onPageChange={(page) => setPaging((current) => ({ ...current, page }))}
                    onSizeChange={(size) => setPaging({ page: 0, size })}
                    itemLabel="users"
                    idPrefix="users-pagination"
                    disabled={loading}
                />
            </div>

            {confirm?.type === 'delete' && (
                <ConfirmDialog
                    title="Delete user"
                    message={`Delete account "${confirm.user.username}"? This action cannot be undone. Past sales, stock movements and logs of this account are kept.`}
                    confirmLabel="Delete"
                    danger
                    busy={busy}
                    onConfirm={runConfirmed}
                    onCancel={() => setConfirm(null)}
                />
            )}
            {confirm?.type === 'status' && (
                <ConfirmDialog
                    title={confirm.user.status === 'ACTIVE' ? 'Deactivate user' : 'Activate user'}
                    message={confirm.user.status === 'ACTIVE'
                        ? `Deactivate "${confirm.user.username}"? This user will no longer be able to log in.`
                        : `Activate "${confirm.user.username}"? This user will be able to log in again${confirm.user.status === 'LOCKED' ? ' (the lock is removed)' : ''}.`}
                    confirmLabel={confirm.user.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                    danger={confirm.user.status === 'ACTIVE'}
                    busy={busy}
                    onConfirm={runConfirmed}
                    onCancel={() => setConfirm(null)}
                />
            )}
        </div>
    );
}
