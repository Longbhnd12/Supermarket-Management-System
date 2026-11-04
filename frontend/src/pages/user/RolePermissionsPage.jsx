import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Lock, RotateCcw, Save } from 'lucide-react';
import RoleService from '../../services/roleService';
import { getAccessAwareErrorMessage } from '../../utils/errorUtils';
import { roleLabel } from '../../utils/roleLabels';
import ConfirmDialog from './ConfirmDialog';
import './UserManagement.css';

// UC06 - Manage Roles & Permissions (SRS 3.3.3). Only the base roles: no New Role / Delete (BR-02).
export default function RolePermissionsPage() {
    const [roles, setRoles] = useState([]);
    const [catalog, setCatalog] = useState([]);
    const [selectedId, setSelectedId] = useState(null);
    const [checked, setChecked] = useState(new Set());
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [confirmReset, setConfirmReset] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');

    const selectedRole = roles.find((role) => role.roleId === selectedId) || null;
    const required = useMemo(() => new Set(selectedRole?.requiredPermissions || []), [selectedRole]);

    const groups = useMemo(() => {
        const map = new Map();
        catalog.forEach((permission) => {
            if (!map.has(permission.group)) map.set(permission.group, []);
            map.get(permission.group).push(permission);
        });
        return [...map.entries()];
    }, [catalog]);

    const dirty = useMemo(() => {
        if (!selectedRole) return false;
        const original = new Set(selectedRole.permissions);
        return original.size !== checked.size || [...checked].some((code) => !original.has(code));
    }, [selectedRole, checked]);

    const selectRole = (role) => {
        setSelectedId(role.roleId);
        setChecked(new Set(role.permissions));
        setNotice('');
        setError('');
    };

    useEffect(() => {
        let active = true;
        Promise.all([RoleService.getAll(), RoleService.getPermissionCatalog()])
            .then(([rolesResponse, catalogResponse]) => {
                if (!active) return;
                const loadedRoles = rolesResponse.data.data || [];
                setRoles(loadedRoles);
                setCatalog(catalogResponse.data.data || []);
                if (loadedRoles.length > 0) {
                    setSelectedId(loadedRoles[0].roleId);
                    setChecked(new Set(loadedRoles[0].permissions));
                }
            })
            .catch((err) => active && setError(getAccessAwareErrorMessage(err)))
            .finally(() => active && setLoading(false));
        return () => { active = false; };
    }, []);

    const toggle = (code) => {
        if (required.has(code)) return;
        setChecked((current) => {
            const next = new Set(current);
            if (next.has(code)) next.delete(code);
            else next.add(code);
            return next;
        });
        setNotice('');
    };

    const toggleGroup = (permissions, allChecked) => {
        setChecked((current) => {
            const next = new Set(current);
            permissions.forEach(({ code }) => {
                if (required.has(code)) return;
                if (allChecked) next.delete(code);
                else next.add(code);
            });
            return next;
        });
        setNotice('');
    };

    const applySaved = (updated) => {
        setRoles((current) => current.map((role) => (role.roleId === updated.roleId ? updated : role)));
        setChecked(new Set(updated.permissions));
    };

    const save = async () => {
        if (!selectedRole) return;
        setSaving(true);
        setError('');
        try {
            const { data } = await RoleService.updatePermissions(selectedRole.roleId, [...checked]);
            applySaved(data.data);
            setNotice('Role updated. Users of this role get the new permissions from their next login.');
        } catch (err) {
            setError(getAccessAwareErrorMessage(err));
        } finally {
            setSaving(false);
        }
    };

    const reset = async () => {
        if (!selectedRole) return;
        setSaving(true);
        setError('');
        try {
            const { data } = await RoleService.resetToDefault(selectedRole.roleId);
            applySaved(data.data);
            setNotice('Permissions reset to the default of this role.');
        } catch (err) {
            setError(getAccessAwareErrorMessage(err));
        } finally {
            setSaving(false);
            setConfirmReset(false);
        }
    };

    return (
        <div className="um-page">
            <div className="um-header">
                <div>
                    <h1>Roles & Permissions</h1>
                    <p>Choose a role and tick the functions it is allowed to use.</p>
                </div>
                <Link to="/users" className="um-btn um-btn-outline"><ArrowLeft size={16} /> Back to Users</Link>
            </div>

            {notice && <p className="um-alert um-alert-success" role="status">{notice}</p>}
            {error && <p className="um-alert um-alert-error" role="alert">{error}</p>}

            {loading ? <p className="um-empty">Loading roles…</p> : (
                <div className="um-roles">
                    <nav className="um-card um-role-list" aria-label="Roles">
                        {roles.map((role) => (
                            <button
                                key={role.roleId}
                                type="button"
                                className={`um-role-item${role.roleId === selectedId ? ' is-selected' : ''}`}
                                onClick={() => selectRole(role)}
                                aria-current={role.roleId === selectedId}
                            >
                                <div>
                                    <strong>{roleLabel(role.roleName)}</strong>
                                    <span>{role.permissions.length} permissions</span>
                                </div>
                                <span className="um-role-count">{role.userCount} {role.userCount === 1 ? 'user' : 'users'}</span>
                            </button>
                        ))}
                    </nav>

                    {selectedRole && (
                        <section className="um-card">
                            <div className="um-perm-toolbar">
                                <div>
                                    <h2>{roleLabel(selectedRole.roleName)}</h2>
                                    <p>{selectedRole.description || `${checked.size} of ${catalog.length} permissions selected`}</p>
                                </div>
                                <div className="um-actions">
                                    <button type="button" className="um-btn um-btn-outline" onClick={() => setConfirmReset(true)} disabled={saving}>
                                        <RotateCcw size={16} /> Reset to default
                                    </button>
                                    <button type="button" className="um-btn um-btn-primary" onClick={save} disabled={saving || !dirty}>
                                        <Save size={16} /> {saving ? 'Saving…' : 'Save'}
                                    </button>
                                </div>
                            </div>

                            <div className="um-perm-groups">
                                {groups.map(([group, permissions]) => {
                                    const allChecked = permissions.every(({ code }) => checked.has(code));
                                    return (
                                        <fieldset key={group} className="um-perm-group">
                                            <label className="um-perm-group-title">
                                                <input type="checkbox" checked={allChecked} onChange={() => toggleGroup(permissions, allChecked)} />
                                                {group}
                                            </label>
                                            {permissions.map((permission) => {
                                                const locked = required.has(permission.code);
                                                return (
                                                    <label key={permission.code} className={`um-perm-item${locked ? ' is-locked' : ''}`}>
                                                        <input
                                                            type="checkbox"
                                                            checked={checked.has(permission.code)}
                                                            onChange={() => toggle(permission.code)}
                                                            disabled={locked}
                                                        />
                                                        <span>
                                                            {permission.label}
                                                            {locked && <small><Lock size={10} /> Required for Admin</small>}
                                                        </span>
                                                    </label>
                                                );
                                            })}
                                        </fieldset>
                                    );
                                })}
                            </div>
                        </section>
                    )}
                </div>
            )}

            {confirmReset && selectedRole && (
                <ConfirmDialog
                    title="Reset to default"
                    message={`Replace the permissions of ${roleLabel(selectedRole.roleName)} with the default set from the requirements?`}
                    confirmLabel="Reset"
                    busy={saving}
                    onConfirm={reset}
                    onCancel={() => setConfirmReset(false)}
                />
            )}
        </div>
    );
}
