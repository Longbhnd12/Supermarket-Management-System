import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Wand2 } from 'lucide-react';
import UserService from '../../services/userService';
import { getAccessAwareErrorMessage } from '../../utils/errorUtils';
import { roleLabel } from '../../utils/roleLabels';
import './UserManagement.css';

// UC05 - Manage Users: Create / Edit User screen (SRS Table 3-6)
const USERNAME_PATTERN = /^[A-Za-z0-9_.-]+$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,255}$/;
const STAFF_ROLES = ['MANAGER', 'CASHIER', 'WAREHOUSE_STAFF'];

const EMPTY_FORM = { roleId: '', employeeId: '', supplierId: '', username: '', email: '', password: '', status: 'ACTIVE' };

// Random temporary password that always meets the password policy
function generatePassword() {
    const sets = ['ABCDEFGHJKLMNPQRSTUVWXYZ', 'abcdefghijkmnopqrstuvwxyz', '23456789', '@#$%'];
    const all = sets.join('');
    const randomIndex = (max) => crypto.getRandomValues(new Uint32Array(1))[0] % max;
    const chars = sets.map((set) => set[randomIndex(set.length)]);
    while (chars.length < 12) chars.push(all[randomIndex(all.length)]);
    for (let i = chars.length - 1; i > 0; i -= 1) {
        const j = randomIndex(i + 1);
        [chars[i], chars[j]] = [chars[j], chars[i]];
    }
    return chars.join('');
}

export default function UserFormPage() {
    const { id } = useParams();
    const isEdit = Boolean(id);
    const navigate = useNavigate();

    const [form, setForm] = useState(EMPTY_FORM);
    const [options, setOptions] = useState({ roles: [], employees: [], suppliers: [] });
    const [fieldErrors, setFieldErrors] = useState({});
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        let active = true;
        const requests = [UserService.getOptions(id)];
        if (isEdit) requests.push(UserService.getById(id));
        Promise.all(requests)
            .then(([optionsResponse, userResponse]) => {
                if (!active) return;
                setOptions(optionsResponse.data.data);
                if (userResponse) {
                    const user = userResponse.data.data;
                    setForm({
                        roleId: user.roleId ? String(user.roleId) : '',
                        employeeId: user.employeeId ? String(user.employeeId) : '',
                        supplierId: user.supplierId ? String(user.supplierId) : '',
                        username: user.username || '',
                        email: user.email || '',
                        password: '',
                        // LOCKED is shown as Active: saving as Active unlocks the account
                        status: user.status === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE'
                    });
                }
            })
            .catch((err) => active && setError(getAccessAwareErrorMessage(err)))
            .finally(() => active && setLoading(false));
        return () => { active = false; };
    }, [id, isEdit]);

    const selectedRole = useMemo(
        () => options.roles.find((role) => String(role.roleId) === form.roleId)?.roleName || '',
        [options.roles, form.roleId]
    );
    const isSupplierRole = selectedRole === 'SUPPLIER';
    const employeeRequired = STAFF_ROLES.includes(selectedRole);

    const update = (field) => (event) => {
        const value = event.target.value;
        setForm((current) => ({ ...current, [field]: value }));
        setFieldErrors((current) => ({ ...current, [field]: '' }));
    };

    const validate = () => {
        const errors = {};
        const required = (field, label) => {
            if (!String(form[field] ?? '').trim()) errors[field] = `The ${label} field is required.`; // MSG02
        };
        required('roleId', 'Role');
        required('username', 'Username');
        required('email', 'Email');
        if (!isEdit) required('password', 'Temporary Password');
        if (employeeRequired) required('employeeId', 'Link to Employee');
        if (isSupplierRole) required('supplierId', 'Link to Supplier');

        const username = form.username.trim();
        if (!errors.username && (username.length < 3 || username.length > 50)) {
            errors.username = 'Username must be between 3 and 50 characters.';
        } else if (!errors.username && !USERNAME_PATTERN.test(username)) {
            errors.username = 'Username may contain only letters, numbers, dots, hyphens, and underscores.';
        }
        if (!errors.email && !EMAIL_PATTERN.test(form.email.trim())) errors.email = 'Enter a valid email address.';
        if (!isEdit && !errors.password && !PASSWORD_PATTERN.test(form.password)) {
            errors.password = 'Password must be at least 8 characters and include uppercase, lowercase and a digit.';
        }
        return errors;
    };

    const submit = async (event) => {
        event.preventDefault();
        setError('');
        const errors = validate();
        setFieldErrors(errors);
        if (Object.keys(errors).length > 0) return;

        const payload = {
            roleId: Number(form.roleId),
            employeeId: !isSupplierRole && form.employeeId ? Number(form.employeeId) : null,
            supplierId: isSupplierRole && form.supplierId ? Number(form.supplierId) : null,
            username: form.username.trim(),
            email: form.email.trim(),
            status: form.status
        };

        setSaving(true);
        try {
            if (isEdit) await UserService.update(id, payload);
            else await UserService.create({ ...payload, password: form.password });
            // "User saved" -> back to the Users List
            navigate('/users', { state: { notice: isEdit ? 'Updated successfully.' : 'Added successfully.' } });
        } catch (err) {
            setError(getAccessAwareErrorMessage(err));
        } finally {
            setSaving(false);
        }
    };

    const fieldError = (field) => fieldErrors[field] && <span className="um-field-error">{fieldErrors[field]}</span>;

    if (loading) return <div className="um-page"><p className="um-empty">Loading…</p></div>;

    return (
        <div className="um-page">
            <div className="um-header">
                <div>
                    <h1>{isEdit ? 'Edit User' : 'Create User'}</h1>
                    <p>{isEdit ? 'Update the account information. The password is not changed here.' : 'Create a new account and give the user a temporary password.'}</p>
                </div>
                <Link to="/users" className="um-btn um-btn-outline"><ArrowLeft size={16} /> Back to Users</Link>
            </div>

            {error && <p className="um-alert um-alert-error" role="alert">{error}</p>}

            <form className="um-card" onSubmit={submit} noValidate>
                <div className="um-form">
                    <label className="um-field">
                        Role
                        <select value={form.roleId} onChange={update('roleId')} aria-invalid={Boolean(fieldErrors.roleId)}>
                            <option value="">Select a role</option>
                            {options.roles.map((role) => <option key={role.roleId} value={role.roleId}>{roleLabel(role.roleName)}</option>)}
                        </select>
                        {fieldError('roleId')}
                    </label>

                    {isSupplierRole ? (
                        <label className="um-field">
                            Link to Supplier
                            <select value={form.supplierId} onChange={update('supplierId')} aria-invalid={Boolean(fieldErrors.supplierId)}>
                                <option value="">Select a supplier</option>
                                {options.suppliers.map((supplier) => (
                                    <option key={supplier.supplierId} value={supplier.supplierId}>
                                        {supplier.supplierName} ({supplier.supplierCode})
                                    </option>
                                ))}
                            </select>
                            {fieldError('supplierId') || <span className="um-field-hint">Required for Supplier accounts.</span>}
                        </label>
                    ) : (
                        <label className="um-field">
                            Link to Employee
                            <select value={form.employeeId} onChange={update('employeeId')} aria-invalid={Boolean(fieldErrors.employeeId)}>
                                <option value="">{employeeRequired ? 'Select an employee' : 'None'}</option>
                                {options.employees.map((employee) => (
                                    <option key={employee.employeeId} value={employee.employeeId}>
                                        {employee.fullName} ({employee.employeeCode}{employee.position ? ` · ${employee.position}` : ''})
                                    </option>
                                ))}
                            </select>
                            {fieldError('employeeId') || (
                                <span className="um-field-hint">
                                    {selectedRole === 'ADMIN' ? 'Optional for Admin.' : 'Required for Manager, Cashier and Inventory Staff.'}
                                </span>
                            )}
                        </label>
                    )}

                    <label className="um-field">
                        Username
                        <input value={form.username} onChange={update('username')} maxLength={50} autoComplete="off" aria-invalid={Boolean(fieldErrors.username)} />
                        {fieldError('username')}
                    </label>

                    <label className="um-field">
                        Email
                        <input type="email" value={form.email} onChange={update('email')} maxLength={150} autoComplete="off" aria-invalid={Boolean(fieldErrors.email)} />
                        {fieldError('email') || <span className="um-field-hint">Used for login and password reset.</span>}
                    </label>

                    {!isEdit && (
                        <label className="um-field">
                            Temporary Password
                            <div className="um-password-row">
                                <input value={form.password} onChange={update('password')} maxLength={255} autoComplete="new-password" aria-invalid={Boolean(fieldErrors.password)} />
                                <button
                                    type="button"
                                    className="um-btn um-btn-outline"
                                    onClick={() => {
                                        setForm((current) => ({ ...current, password: generatePassword() }));
                                        setFieldErrors((current) => ({ ...current, password: '' }));
                                    }}
                                >
                                    <Wand2 size={15} /> Generate
                                </button>
                            </div>
                            {fieldError('password') || <span className="um-field-hint">Give this password to the user; they should change it after the first login.</span>}
                        </label>
                    )}

                    <label className="um-field">
                        Status
                        <select value={form.status} onChange={update('status')}>
                            <option value="ACTIVE">Active</option>
                            <option value="INACTIVE">Inactive</option>
                        </select>
                    </label>

                    <div className="um-form-actions um-span-2">
                        <button type="button" className="um-btn um-btn-outline" onClick={() => navigate('/users')} disabled={saving}>Cancel</button>
                        <button type="submit" className="um-btn um-btn-primary" disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
                    </div>
                </div>
            </form>
        </div>
    );
}
