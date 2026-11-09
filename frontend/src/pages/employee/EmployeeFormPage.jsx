import React, { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
    Shield, ShieldCheck, AlertCircle, CheckCircle2, Lock,
    User, Phone, Mail, MapPin, Calendar, ArrowLeft,
    RefreshCw, Sparkles, Check, X, Save, RotateCcw,
    CreditCard, IdCard, Fingerprint, Briefcase, UserCheck,
    ChevronDown, BadgeCheck, Zap, Terminal
} from 'lucide-react';
import { useAuth } from '../../context/authContext';
import EmployeeService from '../../services/employeeService';
import './EmployeeFormPage.css';

const POSITIONS = [
    { value: 'Cashier', label: 'Cashier', desc: 'Frontline Retail / POS Cashier' },
    { value: 'Inventory Staff', label: 'Inventory Staff', desc: 'Warehouse & Receiving' },
    { value: 'Manager', label: 'Manager', desc: 'Store Operations Supervisor' },
    { value: 'Administrator', label: 'Administrator', desc: 'Systems & Database Admin' },
    { value: 'POS Support', label: 'POS Support', desc: 'Hardware & Terminal Technician' },
];

const today = new Date().toISOString().split('T')[0];

function generateNextCode() {
    return `EMP-${Date.now().toString().slice(-4)}`;
}

function isValidEmail(email) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function isValidPhone(phone) {
    return /^\d{10,15}$/.test(phone.replace(/\s/g, ''));
}

export default function EmployeeFormPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { currentUser, hasRole } = useAuth();

    const isEditMode = Boolean(id);

    const [formData, setFormData] = useState({
        employeeCode: '',
        position: '',
        hireDate: today,
        status: 'ACTIVE',
        fullName: '',
        dateOfBirth: '',
        gender: '',
        phone: '',
        email: '',
        address: '',
    });

    const [loading, setLoading] = useState(isEditMode);
    const [saving, setSaving] = useState(false);
    const [formErrors, setFormErrors] = useState({});
    const [toastMessage, setToastMessage] = useState(null);
    const [toastType, setToastType] = useState('success');

    const showToast = (msg, type = 'success') => {
        setToastType(type);
        setToastMessage(msg);
        setTimeout(() => setToastMessage(null), 3500);
    };

    // Load data
    useEffect(() => {
        if (!isEditMode) {
            const code = generateNextCode();
            setFormData(prev => ({ ...prev, employeeCode: code, hireDate: today, status: 'ACTIVE' }));
            setLoading(false);
            return;
        }

        const fetchEmployee = async () => {
            setLoading(true);
            try {
                const res = await EmployeeService.getById(id);
                const data = res.data?.data || res.data;
                if (data) {
                    setFormData({
                        employeeCode: data.employeeCode || '',
                        position: data.position || '',
                        hireDate: data.hireDate || today,
                        status: data.status || 'ACTIVE',
                        fullName: data.fullName || '',
                        dateOfBirth: data.dateOfBirth || '',
                        gender: data.gender || '',
                        phone: data.phone || '',
                        email: data.email || '',
                        address: data.address || '',
                    });
                }
            } catch (err) {
                console.error('Failed to load employee:', err);
            } finally {
                setLoading(false);
            }
        };

        fetchEmployee();
    }, [id, isEditMode]);

    // SRS 3.6.3 checklist
    const checklist = useMemo(() => {
        const nameOk = formData.fullName.trim().length > 0 && formData.fullName.trim().length <= 100;
        const positionOk = formData.position.trim().length > 0;
        const hireDateOk = formData.hireDate && formData.hireDate <= today;
        const phoneOk = isValidPhone(formData.phone);
        const emailOk = formData.email.trim().length > 0 && isValidEmail(formData.email);
        const statusOk = formData.status === 'ACTIVE' || formData.status === 'INACTIVE';

        return [
            { key: 'fullName', label: 'Full Name provided (≤ 100)', detail: 'Mandatory string, max 100 characters', passed: nameOk },
            { key: 'position', label: 'Position / System Role assigned', detail: 'Must select one of 5 system roles', passed: positionOk },
            { key: 'hireDate', label: 'Hire Date valid & not in future', detail: 'Must be today or earlier', passed: hireDateOk },
            { key: 'phone', label: 'Phone format valid (10-15 digits)', detail: 'Digits only, standard Vietnam telecom', passed: phoneOk },
            { key: 'email', label: 'Email format valid & unique', detail: 'Valid RFC email format required', passed: emailOk },
            { key: 'status', label: 'Status defined (Active/Inactive)', detail: 'Active or Inactive status set', passed: statusOk },
        ];
    }, [formData]);

    const passedCount = checklist.filter(c => c.passed).length;

    // Validation on submit
    const validate = () => {
        const errors = {};
        if (!formData.fullName.trim()) errors.fullName = 'Họ và tên là bắt buộc';
        else if (formData.fullName.trim().length > 100) errors.fullName = 'Họ và tên tối đa 100 ký tự';
        if (!formData.position) errors.position = 'Vui lòng chọn vị trí';
        if (!formData.hireDate) errors.hireDate = 'Ngày vào làm là bắt buộc';
        else if (formData.hireDate > today) errors.hireDate = 'Ngày vào làm không được vượt quá hôm nay';
        if (!formData.phone.trim()) errors.phone = 'Số điện thoại là bắt buộc';
        else if (!isValidPhone(formData.phone)) errors.phone = 'Số điện thoại không hợp lệ (10-15 chữ số)';
        if (!formData.email.trim()) errors.email = 'Email là bắt buộc';
        else if (!isValidEmail(formData.email)) errors.email = 'Email không hợp lệ';
        if (formData.dateOfBirth) {
            const dob = new Date(formData.dateOfBirth);
            const minAge = new Date();
            minAge.setFullYear(minAge.getFullYear() - 18);
            if (dob > minAge) errors.dateOfBirth = 'Nhân viên phải đủ 18 tuổi';
        }
        if (formData.address && formData.address.length > 255) errors.address = 'Địa chỉ tối đa 255 ký tự';
        return errors;
    };

    const handleChange = (field, value) => {
        setFormData(prev => ({ ...prev, [field]: value }));
        if (formErrors[field]) setFormErrors(prev => ({ ...prev, [field]: null }));
    };

    const handleReset = () => {
        if (isEditMode) {
            // Reload from backend
            if (id) {
                setLoading(true);
                EmployeeService.getById(id)
                    .then(res => {
                        const data = res.data?.data || res.data;
                        if (data) {
                            setFormData({
                                employeeCode: data.employeeCode || '',
                                position: data.position || '',
                                hireDate: data.hireDate || today,
                                status: data.status || 'ACTIVE',
                                fullName: data.fullName || '',
                                dateOfBirth: data.dateOfBirth || '',
                                gender: data.gender || '',
                                phone: data.phone || '',
                                email: data.email || '',
                                address: data.address || '',
                            });
                        }
                    })
                    .catch(err => console.error('Reset failed:', err))
                    .finally(() => setLoading(false));
            }
        } else {
            setFormData({
                employeeCode: generateNextCode(),
                position: '',
                hireDate: today,
                status: 'ACTIVE',
                fullName: '',
                dateOfBirth: '',
                gender: '',
                phone: '',
                email: '',
                address: '',
            });
        }
        setFormErrors({});
        showToast('Form đã được đặt lại.', 'info');
    };

    const handleSubmit = async () => {
        const errors = validate();
        if (Object.keys(errors).length > 0) {
            setFormErrors(errors);
            showToast('Vui lòng kiểm tra lại các trường bị lỗi.', 'error');
            return;
        }

        setSaving(true);
        const payload = {
            employeeCode: formData.employeeCode.trim(),
            fullName: formData.fullName.trim(),
            dateOfBirth: formData.dateOfBirth || null,
            gender: formData.gender || null,
            phone: formData.phone.trim(),
            email: formData.email.trim(),
            address: formData.address.trim() || null,
            position: formData.position.trim(),
            hireDate: formData.hireDate,
        };

        try {
            let savedId;
            if (isEditMode) {
                const res = await EmployeeService.update(id, payload);
                savedId = res.data?.data?.employeeId || id;
                // Update status separately if changed
                await EmployeeService.updateStatus(id, formData.status).catch(() => {});
            } else {
                const res = await EmployeeService.create(payload);
                savedId = res.data?.data?.employeeId || id;
            }
            showToast('Đã lưu hồ sơ nhân viên thành công! ✓', 'success');
            setTimeout(() => navigate(`/employees/${savedId || ''}`), 900);
        } catch (err) {
            const msg = err.response?.data?.message || 'Có lỗi xảy ra khi lưu. Vui lòng thử lại.';
            setFormErrors(prev => ({ ...prev, submit: msg }));
            showToast(msg, 'error');
        } finally {
            setSaving(false);
        }
    };

    // Live card preview helpers
    const cardName = formData.fullName.trim() || 'Họ và Tên St...';
    const cardPosition = formData.position || 'Chức vụ chưa chọn';
    const cardCode = formData.employeeCode || '---';
    const cardHireDate = formData.hireDate ? formData.hireDate.substring(0, 7) : '----';
    const cardStatus = formData.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE';

    if (loading) {
        return (
            <div className="efp-loading">
                <RefreshCw className="spin" size={28} />
                <span>Đang tải dữ liệu nhân viên...</span>
            </div>
        );
    }

    const pageTitle = isEditMode ? 'Edit Employee Information' : 'Create New Employee';
    const breadcrumbLast = isEditMode
        ? `${formData.employeeCode} / Edit Employee (UC18)`
        : 'Create Employee (UC18)';

    return (
        <div className="efp-root">
            {/* Toast */}
            {toastMessage && (
                <div className={`efp-toast efp-toast--${toastType}`}>
                    {toastType === 'success' ? <CheckCircle2 size={16} /> : toastType === 'error' ? <AlertCircle size={16} /> : <Sparkles size={16} />}
                    {toastMessage}
                </div>
            )}

            {/* Breadcrumb */}
            <div className="efp-breadcrumb">
                <Link to="/">Home</Link>
                <span>/</span>
                <span>Operations Hub</span>
                <span>/</span>
                <span>Directory</span>
                <span>/</span>
                <Link to="/employees">Employees</Link>
                <span>/</span>
                <span className="efp-breadcrumb-current">{breadcrumbLast}</span>
            </div>

            {/* Page Header */}
            <div className="efp-page-header">
                <div className="efp-header-left">
                    <h1 className="efp-page-title">
                        {pageTitle}
                        {isEditMode && <span className="efp-code-badge">{formData.employeeCode}</span>}
                    </h1>
                    <div className="efp-header-meta">
                        <span className="efp-role-badge">
                            <Shield size={12} /> Role Access: Administrator &amp; Manager Only (UC18)
                        </span>
                        <span className="efp-draft-badge">● Draft Record</span>
                    </div>
                    <p className="efp-header-desc">
                        Enter supermarket staff information, role assignment, and contact details in accordance with SRS 3.6.3 &amp; UC18. Deactivation immediately revokes terminal session tokens.
                    </p>
                </div>
                <div className="efp-header-actions">
                    <button className="efp-btn-cancel" onClick={() => navigate('/employees')}>
                        <ArrowLeft size={15} /> Cancel &amp; Return
                    </button>
                    <button className="efp-btn-save" onClick={handleSubmit} disabled={saving}>
                        {saving ? <RefreshCw size={15} className="spin" /> : <Save size={15} />}
                        {saving ? 'Đang lưu...' : 'Save Employee'}
                    </button>
                </div>
            </div>

            {/* Main layout: form + sidebar */}
            <div className="efp-layout">
                {/* ===== LEFT: Main Form ===== */}
                <div className="efp-form-main">

                    {/* Section A */}
                    <div className="efp-section">
                        <div className="efp-section-header">
                            <div className="efp-section-title">
                                <span className="efp-section-letter">A</span>
                                <div>
                                    <h2>Section A: System Identity &amp; Placement</h2>
                                </div>
                            </div>
                            <span className="efp-badge efp-badge--blue">Immutable SRS Specification</span>
                        </div>

                        <div className="efp-field-grid efp-field-grid--2">
                            {/* Employee Code */}
                            <div className="efp-field-group">
                                <label className="efp-label">
                                    Employee Code *
                                    <span className="efp-label-badge efp-label-badge--lock">
                                        <Lock size={10} /> Read-Only System ID
                                    </span>
                                </label>
                                <div className="efp-input-wrapper efp-input-readonly">
                                    <IdCard size={15} className="efp-input-icon" />
                                    <input
                                        type="text"
                                        className="efp-input"
                                        value={formData.employeeCode}
                                        readOnly
                                        disabled
                                    />
                                    <Lock size={13} className="efp-input-icon-right" />
                                </div>
                                <p className="efp-hint">Auto-generated immutable key (max 20 chars). Cannot be manually modified per SRS 3.6.3.</p>
                            </div>

                            {/* Position */}
                            <div className="efp-field-group">
                                <label className="efp-label">
                                    Position / System Role *
                                    <span className={`efp-label-badge ${formData.position ? 'efp-label-badge--green' : 'efp-label-badge--warn'}`}>
                                        {formData.position ? <BadgeCheck size={10} /> : <AlertCircle size={10} />}
                                        {formData.position ? 'Role Assigned' : 'Terminal Clearance Pending'}
                                    </span>
                                </label>
                                <div className={`efp-input-wrapper ${formErrors.position ? 'efp-input--error' : ''}`}>
                                    <Briefcase size={15} className="efp-input-icon" />
                                    <select
                                        className="efp-select"
                                        value={formData.position}
                                        onChange={e => handleChange('position', e.target.value)}
                                    >
                                        <option value="">Select system authorization role...</option>
                                        {POSITIONS.map(p => (
                                            <option key={p.value} value={p.value}>{p.label} — {p.desc}</option>
                                        ))}
                                    </select>
                                </div>
                                {formErrors.position && <p className="efp-error">{formErrors.position}</p>}
                                <p className="efp-hint">Select role to verify hardware &amp; POS authorization clearance</p>
                            </div>

                            {/* Hire Date */}
                            <div className="efp-field-group">
                                <label className="efp-label">Hire Date *</label>
                                <div className={`efp-input-wrapper ${formErrors.hireDate ? 'efp-input--error' : ''}`}>
                                    <Calendar size={15} className="efp-input-icon" />
                                    <input
                                        type="date"
                                        className="efp-input"
                                        value={formData.hireDate}
                                        max={today}
                                        onChange={e => handleChange('hireDate', e.target.value)}
                                    />
                                </div>
                                {formErrors.hireDate && <p className="efp-error">{formErrors.hireDate}</p>}
                                <p className="efp-hint">Validation constraint: Hire date cannot exceed today ({today}).</p>
                            </div>

                            {/* Account Status */}
                            <div className="efp-field-group">
                                <label className="efp-label">Account Status *</label>
                                <div className="efp-status-toggle">
                                    <button
                                        className={`efp-status-btn ${formData.status === 'ACTIVE' ? 'efp-status-btn--active' : ''}`}
                                        onClick={() => handleChange('status', 'ACTIVE')}
                                        type="button"
                                    >
                                        <span className="efp-status-dot efp-status-dot--active"></span>
                                        Active Staff
                                    </button>
                                    <button
                                        className={`efp-status-btn ${formData.status === 'INACTIVE' ? 'efp-status-btn--inactive' : ''}`}
                                        onClick={() => handleChange('status', 'INACTIVE')}
                                        type="button"
                                    >
                                        <span className="efp-status-dot efp-status-dot--inactive"></span>
                                        Suspended
                                    </button>
                                </div>
                                <p className="efp-hint">Active staff can access POS/Inventory terminals. Inactive status revokes session tokens without altering sales ledgers.</p>
                            </div>
                        </div>
                    </div>

                    {/* Section B */}
                    <div className="efp-section">
                        <div className="efp-section-header">
                            <div className="efp-section-title">
                                <span className="efp-section-letter efp-section-letter--green">B</span>
                                <div>
                                    <h2>Section B: Personal &amp; Demographic Record</h2>
                                </div>
                            </div>
                            <span className="efp-badge efp-badge--green">Vietnamese Labor Law Compliant</span>
                        </div>

                        {/* Full Name */}
                        <div className="efp-field-group efp-field-group--full">
                            <label className="efp-label">
                                Full Name (Họ và Tên) *
                                <span className="efp-char-counter">{formData.fullName.length} / 100</span>
                            </label>
                            <div className={`efp-input-wrapper ${formErrors.fullName ? 'efp-input--error' : ''}`}>
                                <User size={15} className="efp-input-icon" />
                                <input
                                    type="text"
                                    className="efp-input"
                                    placeholder="e.g. Nguyễn Văn An, Trần Thị Mai..."
                                    maxLength={100}
                                    value={formData.fullName}
                                    onChange={e => handleChange('fullName', e.target.value)}
                                />
                            </div>
                            {formErrors.fullName && <p className="efp-error">{formErrors.fullName}</p>}
                        </div>

                        <div className="efp-field-grid efp-field-grid--2">
                            {/* Date of Birth */}
                            <div className="efp-field-group">
                                <label className="efp-label">Date of Birth (Optional)</label>
                                <div className={`efp-input-wrapper ${formErrors.dateOfBirth ? 'efp-input--error' : ''}`}>
                                    <Calendar size={15} className="efp-input-icon" />
                                    <input
                                        type="date"
                                        className="efp-input"
                                        placeholder="mm/dd/yyyy"
                                        value={formData.dateOfBirth}
                                        onChange={e => handleChange('dateOfBirth', e.target.value)}
                                    />
                                </div>
                                {formErrors.dateOfBirth && <p className="efp-error">{formErrors.dateOfBirth}</p>}
                                <p className="efp-hint">Optional date picker, must be valid past date (18+ for cash management).</p>
                            </div>

                            {/* Gender */}
                            <div className="efp-field-group">
                                <label className="efp-label">Gender (Optional)</label>
                                <div className="efp-input-wrapper">
                                    <User size={15} className="efp-input-icon" />
                                    <select
                                        className="efp-select"
                                        value={formData.gender}
                                        onChange={e => handleChange('gender', e.target.value)}
                                    >
                                        <option value="">Select gender option...</option>
                                        <option value="MALE">Male (Nam)</option>
                                        <option value="FEMALE">Female (Nữ)</option>
                                    </select>
                                </div>
                                <p className="efp-hint">Demographic data for store shift logistics and uniform allocation.</p>
                            </div>
                        </div>
                    </div>

                    {/* Section C */}
                    <div className="efp-section">
                        <div className="efp-section-header">
                            <div className="efp-section-title">
                                <span className="efp-section-letter efp-section-letter--indigo">C</span>
                                <div>
                                    <h2>Section C: Contact &amp; Residential Location</h2>
                                </div>
                            </div>
                            <span className="efp-badge efp-badge--indigo">Unique Credentials Enforced</span>
                        </div>

                        <div className="efp-field-grid efp-field-grid--2">
                            {/* Phone */}
                            <div className="efp-field-group">
                                <label className="efp-label">
                                    Phone Number *
                                    <span className="efp-label-sm">Digits only max 15</span>
                                </label>
                                <div className={`efp-input-wrapper ${formErrors.phone ? 'efp-input--error' : ''}`}>
                                    <Phone size={15} className="efp-input-icon" />
                                    <input
                                        type="text"
                                        className="efp-input"
                                        placeholder="e.g. 0903123456"
                                        maxLength={15}
                                        value={formData.phone}
                                        onChange={e => handleChange('phone', e.target.value.replace(/[^\d]/g, ''))}
                                    />
                                </div>
                                {formErrors.phone && <p className="efp-error">{formErrors.phone}</p>}
                                <p className="efp-hint">Standard format: 10-11 numeric digits (Vietnam telecom prefix).</p>
                            </div>

                            {/* Email */}
                            <div className="efp-field-group">
                                <label className="efp-label">
                                    Corporate / Primary Email
                                    <span className="efp-label-badge efp-label-badge--indigo">
                                        <Fingerprint size={10} /> Unique System ID
                                    </span>
                                </label>
                                <div className={`efp-input-wrapper ${formErrors.email ? 'efp-input--error' : ''}`}>
                                    <Mail size={15} className="efp-input-icon" />
                                    <input
                                        type="email"
                                        className="efp-input"
                                        placeholder="e.g. mai.tran@smsretail.vn"
                                        value={formData.email}
                                        onChange={e => handleChange('email', e.target.value)}
                                    />
                                </div>
                                {formErrors.email && <p className="efp-error">{formErrors.email}</p>}
                                <p className="efp-hint">Used for single sign-on, digital payslips, and POS cashier credentials.</p>
                            </div>
                        </div>

                        {/* Address */}
                        <div className="efp-field-group efp-field-group--full">
                            <label className="efp-label">
                                Residential Address (Optional, max 255 chars)
                                <span className="efp-char-counter">{formData.address.length} / 255</span>
                            </label>
                            <div className={`efp-input-wrapper efp-input-wrapper--textarea ${formErrors.address ? 'efp-input--error' : ''}`}>
                                <MapPin size={15} className="efp-input-icon efp-input-icon--top" />
                                <textarea
                                    className="efp-textarea"
                                    rows={3}
                                    placeholder="e.g. Số 123 Đường Hai Bà Trưng, Phường Nghệ, Quận 1, TP. Hồ Chí Minh..."
                                    maxLength={255}
                                    value={formData.address}
                                    onChange={e => handleChange('address', e.target.value)}
                                />
                            </div>
                            {formErrors.address && <p className="efp-error">{formErrors.address}</p>}
                        </div>
                    </div>

                    {/* Submit error */}
                    {formErrors.submit && (
                        <div className="efp-submit-error">
                            <AlertCircle size={15} /> {formErrors.submit}
                        </div>
                    )}
                </div>

                {/* ===== RIGHT: Sidebar ===== */}
                <div className="efp-sidebar">
                    {/* Digital ID Credential Card */}
                    <div className="efp-id-card-wrapper">
                        <div className="efp-id-card-header">
                            <span className="efp-id-card-title">
                                <CreditCard size={14} /> DIGITAL ID CREDENTIAL
                            </span>
                            <span className="efp-rfid-badge">RFID ENCRYPTED</span>
                        </div>

                        <div className="efp-id-card">
                            <div className="efp-id-card-top">
                                <div className="efp-id-card-brand">
                                    <span className="efp-id-card-brand-icon">🏪</span>
                                    <span>SMS Retail Flagship</span>
                                </div>
                                <span className={`efp-id-status-badge ${cardStatus === 'ACTIVE' ? 'efp-id-status-badge--active' : 'efp-id-status-badge--inactive'}`}>
                                    ● {cardStatus}
                                </span>
                            </div>
                            <div className="efp-id-card-body">
                                <div className="efp-id-avatar">
                                    <span>+</span>
                                </div>
                                <div className="efp-id-info">
                                    <div className="efp-id-name">{cardName.length > 20 ? cardName.substring(0, 20) + '...' : cardName}</div>
                                    <div className="efp-id-role">{cardPosition}</div>
                                    <div className="efp-id-code">{cardCode}</div>
                                </div>
                            </div>
                            <div className="efp-id-card-footer">
                                <span>Hire Date: {cardHireDate}</span>
                                <span>Terminal: POS Standard</span>
                            </div>
                        </div>

                        <p className="efp-id-card-note">
                            Synchronized with barcode scanner, thermal label printers, and checkout NFC terminals.
                        </p>
                    </div>

                    {/* SRS 3.6.3 Verification Checklist */}
                    <div className="efp-checklist-card">
                        <div className="efp-checklist-header">
                            <div className="efp-checklist-title">
                                <ShieldCheck size={15} /> SRS 3.6.3 Verification
                            </div>
                            <span className={`efp-checklist-score ${passedCount >= 5 ? 'score-high' : passedCount >= 3 ? 'score-mid' : 'score-low'}`}>
                                {passedCount} / {checklist.length} Passed
                            </span>
                        </div>

                        <div className="efp-checklist-items">
                            {checklist.map(item => (
                                <div key={item.key} className={`efp-check-item ${item.passed ? 'passed' : 'pending'}`}>
                                    <div className="efp-check-icon-wrap">
                                        {item.passed
                                            ? <CheckCircle2 size={15} className="check-icon check-ok" />
                                            : <div className="check-icon-circle"></div>
                                        }
                                    </div>
                                    <div className="efp-check-text">
                                        <span className={`efp-check-label ${item.passed ? 'is-passed' : ''}`}>{item.label}</span>
                                        <span className="efp-check-detail">{item.passed ? (item.key === 'hireDate' ? 'Valid' : item.key === 'status' ? (formData.status === 'ACTIVE' ? 'Active' : 'Inactive') : '✓') : 'Pending'}</span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Supermarket Audit Guarantee */}
                    <div className="efp-audit-card">
                        <div className="efp-audit-header">
                            <Shield size={14} /> Supermarket Audit Guarantee
                        </div>
                        <p className="efp-audit-desc">
                            Role assignment strictly adheres to the 5 system roles. Deactivation revokes terminal tokens while preserving ledger integrity per UC08 Audit Logs. An immutable employee code is forever bound to their sales receipts and stock adjustments.
                        </p>
                        <div className="efp-audit-footer">
                            <span>Terminal Gateway: v4.8</span>
                            <span className="efp-audit-sync">
                                <span className="efp-sync-dot"></span> Sync Live
                            </span>
                        </div>
                    </div>
                </div>
            </div>

            {/* Bottom Action Bar */}
            <div className="efp-bottom-bar">
                <div className="efp-bottom-info">
                    <Shield size={14} />
                    All field changes will be immutably recorded in UC08 Audit Logs upon saving.
                </div>
                <div className="efp-bottom-actions">
                    <button className="efp-btn-reset" onClick={handleReset}>
                        <RotateCcw size={14} /> Reset Form
                    </button>
                    <button className="efp-btn-commit" onClick={handleSubmit} disabled={saving}>
                        {saving ? <RefreshCw size={14} className="spin" /> : <Save size={14} />}
                        {saving ? 'Đang lưu...' : 'Commit Employee Record'}
                    </button>
                </div>
            </div>
        </div>
    );
}

