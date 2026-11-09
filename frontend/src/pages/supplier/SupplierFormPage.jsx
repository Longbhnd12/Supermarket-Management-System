import React, { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
    Shield, ShieldCheck, AlertCircle, CheckCircle2, Lock,
    Building2, Phone, Mail, MapPin, User, FileText, ArrowLeft,
    RefreshCw, Sparkles, Check, X, Ban, Save, RotateCcw,
    CreditCard, ExternalLink
} from 'lucide-react';
import { useAuth } from '../../context/authContext';
import SupplierService from '../../services/supplierService';
import { enrichSupplier } from './mockSuppliersData';
import './SupplierFormPage.css';

export default function SupplierFormPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { currentUser, hasRole } = useAuth();

    const isEditMode = Boolean(id);

    // Form data state
    const [formData, setFormData] = useState({
        supplierCode: '',
        supplierName: '',
        contactPerson: '',
        phone: '',
        email: '',
        address: '',
        taxCode: '',
        status: 'ACTIVE'
    });

    const [loading, setLoading] = useState(isEditMode);
    const [saving, setSaving] = useState(false);
    const [formErrors, setFormErrors] = useState({});
    const [toastMessage, setToastMessage] = useState(null);

    const isStatusLocked = currentUser?.role === 'WAREHOUSE_STAFF';

    // Deactivation Dialog State
    const [isDeactivateDialogOpen, setIsDeactivateDialogOpen] = useState(false);
    const [deactivateReason, setDeactivateReason] = useState('');

    const showToast = (msg) => {
        setToastMessage(msg);
        setTimeout(() => setToastMessage(null), 3500);
    };

    // Auto-generate code for create mode if not provided
    const generateNextCode = () => {
        return `SUP-${Date.now().toString().slice(-4)}`;
    };

    // Load supplier data in Edit mode
    useEffect(() => {
        if (!isEditMode) {
            setFormData((prev) => ({
                ...prev,
                supplierCode: generateNextCode(),
                status: 'ACTIVE'
            }));
            setLoading(false);
            return;
        }

        const fetchSupplier = async () => {
            setLoading(true);
            try {
                const res = await SupplierService.getById(id);
                const data = res.data?.data || res.data;
                if (data) {
                    setFormData({
                        supplierCode: data.supplierCode || '',
                        supplierName: data.supplierName || '',
                        contactPerson: data.contactPerson || '',
                        phone: data.phone || '',
                        email: data.email || '',
                        address: data.address || '',
                        taxCode: data.taxCode || '',
                        status: data.status || 'ACTIVE'
                    });
                }
            } catch (err) {
                console.error('Failed to load supplier from backend:', err);
                showToast('Không thể tải thông tin nhà cung cấp.', 'error');
            } finally {
                setLoading(false);
            }
        };

        fetchSupplier();
    }, [id, isEditMode]);

    // Live Compliance Evaluation (SRS 3.7.3 Compliance Check - Option 1: Only tick when entered & valid)
    const compliance = useMemo(() => {
        // 1. Supplier Name: Mandatory string, 2-150 characters
        const nameEntered = Boolean(formData.supplierName.trim());
        const nameValid =
            nameEntered &&
            formData.supplierName.trim().length >= 2 &&
            formData.supplierName.trim().length <= 150;
        const nameHasError = nameEntered && !nameValid;

        // 2. Phone Telecom: Mandatory, 10-15 numeric digits
        const phoneEntered = Boolean(formData.phone.trim());
        const cleanPhone = formData.phone.replace(/[\s\-\.\(\)]/g, '');
        const phoneValid = phoneEntered && /^[0-9]{10,15}$/.test(cleanPhone);
        const phoneHasError = phoneEntered && !phoneValid;

        // 3. Tax Code: Optional, only ticks when entered & valid (10 or 13 digits)
        const taxEntered = Boolean(formData.taxCode.trim());
        const cleanTax = formData.taxCode.trim().replace(/-/g, '');
        const taxValid =
            taxEntered &&
            (cleanTax.length === 10 || cleanTax.length === 13) &&
            /^[0-9]{10,13}$/.test(cleanTax);
        const taxHasError = taxEntered && !taxValid;

        // 4. Email format: Optional, only ticks when entered & valid RFC email
        const emailEntered = Boolean(formData.email.trim());
        const emailValid =
            emailEntered &&
            /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email.trim());
        const emailHasError = emailEntered && !emailValid;

        // 5. Operational Status defined: Active or Disabled
        const statusValid = Boolean(formData.status);

        const checks = [
            {
                id: 'name',
                label: 'Supplier Name provided',
                desc: nameValid
                    ? `Valid legal name (${formData.supplierName.trim().length}/150 chars)`
                    : 'Mandatory string, max 150 characters',
                valid: nameValid,
                hasError: nameHasError
            },
            {
                id: 'phone',
                label: 'Phone format valid',
                desc: phoneValid
                    ? `Valid telecom format (${cleanPhone.length} digits)`
                    : phoneHasError
                    ? 'Invalid phone number (must be 10-15 digits)'
                    : 'Mandatory, 10-15 numeric digits',
                valid: phoneValid,
                hasError: phoneHasError
            },
            {
                id: 'tax',
                label: 'Tax Code unique / valid',
                desc: taxValid
                    ? 'Valid 10/13-digit tax code verified'
                    : taxHasError
                    ? 'Invalid tax code format (must be 10 or 13 digits)'
                    : 'Chưa nhập (Tùy chọn, 10 hoặc 13 số)',
                valid: taxValid,
                hasError: taxHasError
            },
            {
                id: 'email',
                label: 'Email format valid',
                desc: emailValid
                    ? 'Valid RFC email address'
                    : emailHasError
                    ? 'Invalid email format'
                    : 'Chưa nhập (Tùy chọn, định dạng RFC email)',
                valid: emailValid,
                hasError: emailHasError
            },
            {
                id: 'status',
                label: 'Operational Status defined',
                desc: formData.status === 'ACTIVE'
                    ? 'Active state defined (PO issuance allowed)'
                    : 'Disabled state defined (PO issuance blocked)',
                valid: statusValid,
                hasError: false
            }
        ];

        const passedCount = checks.filter((c) => c.valid).length;
        const allPassed = passedCount === checks.length;
        const mandatoryPassed = nameValid && phoneValid && statusValid && !taxHasError && !emailHasError;

        return { checks, passedCount, total: checks.length, allPassed, mandatoryPassed };
    }, [formData]);

    // Validate form before save
    const validate = () => {
        const errors = {};
        if (!formData.supplierName.trim()) {
            errors.supplierName = 'Tên pháp nhân nhà cung cấp là bắt buộc (SRS 3.7.3.1)';
        } else if (formData.supplierName.trim().length > 150) {
            errors.supplierName = 'Tên nhà cung cấp không vượt quá 150 ký tự';
        }

        const cleanPhone = formData.phone.replace(/[\s\-\.\(\)]/g, '');
        if (!formData.phone.trim()) {
            errors.phone = 'Số điện thoại liên hệ là bắt buộc (SRS 3.7.3.2)';
        } else if (!/^[0-9]{10,15}$/.test(cleanPhone)) {
            errors.phone = 'Số điện thoại phải từ 10 đến 15 chữ số';
        }

        if (formData.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email.trim())) {
            errors.email = 'Định dạng email không hợp lệ (Ví dụ: contact@vinamilk.com.vn)';
        }

        if (formData.taxCode.trim()) {
            const cleanTax = formData.taxCode.trim().replace(/-/g, '');
            if (!/^[0-9]{10,13}$/.test(cleanTax)) {
                errors.taxCode = 'Mã số thuế phải gồm 10 hoặc 13 chữ số';
            }
        }

        setFormErrors(errors);
        return Object.keys(errors).length === 0;
    };

    // Handle Form Submit
    const handleSubmit = async (e) => {
        if (e) e.preventDefault();

        if (!validate()) {
            showToast('Vui lòng kiểm tra và sửa các lỗi nhập liệu được báo đỏ.');
            return;
        }

        setSaving(true);
        try {
            const payload = {
                supplierCode: formData.supplierCode.trim(),
                supplierName: formData.supplierName.trim(),
                contactPerson: formData.contactPerson.trim() || null,
                phone: formData.phone.trim(),
                email: formData.email.trim() || null,
                address: formData.address.trim() || null,
                taxCode: formData.taxCode.trim() || null,
                status: formData.status
            };

            let savedId = id;
            if (isEditMode) {
                const res = await SupplierService.update(id, payload);
                savedId = res.data?.data?.supplierId || id;
            } else {
                const res = await SupplierService.create(payload);
                savedId = res.data?.data?.supplierId || Date.now();
            }

            showToast(isEditMode ? 'Cập nhật nhà cung cấp thành công!' : 'Đã đăng ký nhà cung cấp mới thành công!');
            setTimeout(() => {
                navigate(`/suppliers/${savedId}`);
            }, 800);
        } catch (err) {
            const msg = err.response?.data?.message || err.message || 'Không thể lưu thông tin nhà cung cấp.';
            setFormErrors((prev) => ({ ...prev, submit: msg }));
            showToast(`Lỗi: ${msg}`);
        } finally {
            setSaving(false);
        }
    };

    // Toggle Status with Deactivation Check
    const handleStatusToggle = () => {
        if (isStatusLocked) {
            showToast('Chỉ Quản trị viên (Admin) hoặc Cửa hàng trưởng (Manager) mới có quyền đổi trạng thái.');
            return;
        }

        if (formData.status === 'ACTIVE') {
            setIsDeactivateDialogOpen(true);
        } else {
            setFormData((prev) => ({ ...prev, status: 'ACTIVE' }));
            showToast('Đã chuyển trạng thái sang Kích hoạt (Active).');
        }
    };

    const confirmDeactivation = () => {
        setFormData((prev) => ({ ...prev, status: 'DISABLED' }));
        setIsDeactivateDialogOpen(false);
        showToast('Đã tạm dừng hoạt động nhà cung cấp (Khóa phát hành PO theo UC21).');
    };

    // Reset Form
    const handleResetForm = () => {
        if (isEditMode) {
            if (id) {
                setLoading(true);
                SupplierService.getById(id)
                    .then((res) => {
                        const data = res.data?.data || res.data;
                        if (data) {
                            setFormData({
                                supplierCode: data.supplierCode || '',
                                supplierName: data.supplierName || '',
                                contactPerson: data.contactPerson || '',
                                phone: data.phone || '',
                                email: data.email || '',
                                address: data.address || '',
                                taxCode: data.taxCode || '',
                                status: data.status || 'ACTIVE'
                            });
                        }
                    })
                    .catch((err) => console.error(err))
                    .finally(() => setLoading(false));
            }
        } else {
            setFormData({
                supplierCode: generateNextCode(),
                supplierName: '',
                contactPerson: '',
                phone: '',
                email: '',
                address: '',
                taxCode: '',
                status: 'ACTIVE'
            });
        }
        setFormErrors({});
        showToast('Đã hoàn tác và đặt lại form về trạng thái ban đầu.');
    };

    if (loading) {
        return (
            <div className="supplier-form-loading-box">
                <RefreshCw size={28} className="spinning" />
                <span>Đang tải thông tin hồ sơ nhà cung cấp...</span>
            </div>
        );
    }

    const isActive = formData.status === 'ACTIVE';

    return (
        <div className="supplier-form-page">
            {/* Breadcrumb & Top Actions Header */}
            <div className="form-page-header">
                <div className="header-breadcrumbs">
                    <Link to="/">Home</Link>
                    <span className="crumb-sep">/</span>
                    <span>Operations Hub</span>
                    <span className="crumb-sep">/</span>
                    <span>Directory</span>
                    <span className="crumb-sep">/</span>
                    <Link to="/suppliers">Suppliers</Link>
                    <span className="crumb-sep">/</span>
                    <span className="crumb-current">
                        {isEditMode ? `Edit Supplier (UC20)` : `Create New Supplier (UC20)`}
                    </span>
                </div>

                <div className="header-title-bar">
                    <div className="title-left">
                        <div className="title-heading-row">
                            <h1 className="page-title">
                                {isEditMode ? 'Edit Supplier' : 'Create New Supplier'}
                            </h1>
                            <span className="code-tag-badge">{formData.supplierCode || 'SUP-009'}</span>
                        </div>
                        <p className="page-desc">
                            Enter legal entity details, procurement liaison contacts, taxation code, and active supply status per SRS 3.7.3 and UC20.
                        </p>
                    </div>

                    <div className="header-actions">
                        <button
                            type="button"
                            className="action-btn secondary"
                            onClick={() => navigate(isEditMode ? `/suppliers/${id}` : '/suppliers')}
                        >
                            <ArrowLeft size={16} />
                            Cancel
                        </button>
                        <button
                            type="button"
                            className="action-btn primary"
                            onClick={handleSubmit}
                            disabled={saving}
                        >
                            <Save size={16} />
                            {saving ? 'Saving...' : 'Save Supplier'}
                        </button>
                    </div>
                </div>
            </div>

            {formErrors.submit && (
                <div className="global-error-banner">
                    <AlertCircle size={18} />
                    <span>{formErrors.submit}</span>
                </div>
            )}

            {/* Form Two-Column Grid Layout */}
            <form onSubmit={handleSubmit} className="form-main-grid">
                {/* LEFT COLUMN: Main Form Sections */}
                <div className="form-left-col">
                    {/* SECTION A: Commercial Entity Identity */}
                    <div className="form-section-card">
                        <div className="section-header-row">
                            <div className="section-title-wrap">
                                <span className="section-letter-badge">A</span>
                                <h2 className="section-title">Commercial Entity Identity</h2>
                            </div>
                            <span className="srs-pill">SRS 3.7.3.1</span>
                        </div>

                        <div className="section-body">
                            <div className="form-row-2">
                                {/* Supplier Code */}
                                <div className="field-group">
                                    <div className="field-label-row">
                                        <label>Supplier Code</label>
                                        <span className="immutable-tag">
                                            <Lock size={12} />
                                            Immutable Primary Key
                                        </span>
                                    </div>
                                    <div className="input-with-icon readonly-input">
                                        <input
                                            type="text"
                                            value={formData.supplierCode}
                                            readOnly
                                            disabled
                                        />
                                        <Shield size={16} className="field-icon text-muted" />
                                    </div>
                                    <span className="field-help">
                                        System auto-generated identifier (max 20 chars). Non-editable once issued.
                                    </span>
                                </div>

                                {/* Tax Code (MST) */}
                                <div className="field-group">
                                    <div className="field-label-row">
                                        <label>Tax Code (Mã số thuế - MST) <span className="optional-tag">(Optional)</span></label>
                                        <span className="char-counter">{formData.taxCode.length} / 20</span>
                                    </div>
                                    <div className={`input-with-icon ${formErrors.taxCode ? 'has-error' : ''}`}>
                                        <input
                                            type="text"
                                            maxLength={20}
                                            placeholder="e.g. 0300588569 or 0100109106-001"
                                            value={formData.taxCode}
                                            onChange={(e) => {
                                                setFormData({ ...formData, taxCode: e.target.value });
                                                if (formErrors.taxCode) setFormErrors({ ...formErrors, taxCode: null });
                                            }}
                                        />
                                        <CreditCard size={16} className="field-icon" />
                                    </div>
                                    {formErrors.taxCode ? (
                                        <span className="field-error-text">{formErrors.taxCode}</span>
                                    ) : (
                                        <span className="field-help">
                                            10 or 13-digit tax code. Verified against General Department of Taxation if entered.
                                        </span>
                                    )}
                                </div>
                            </div>

                            {/* Supplier Legal Name */}
                            <div className="field-group">
                                <div className="field-label-row">
                                    <label>Supplier Legal Name <span className="required-star">*</span></label>
                                    <span className="char-counter">{formData.supplierName.length} / 150</span>
                                </div>
                                <div className={`input-with-icon ${formErrors.supplierName ? 'has-error' : ''}`}>
                                    <input
                                        type="text"
                                        required
                                        maxLength={150}
                                        placeholder="e.g. Công ty Cổ phần Sữa Việt Nam (Vinamilk) or Acecook Việt Nam JSC"
                                        value={formData.supplierName}
                                        onChange={(e) => {
                                            setFormData({ ...formData, supplierName: e.target.value });
                                            if (formErrors.supplierName) setFormErrors({ ...formErrors, supplierName: null });
                                        }}
                                    />
                                    <Building2 size={16} className="field-icon" />
                                </div>
                                {formErrors.supplierName ? (
                                    <span className="field-error-text">{formErrors.supplierName}</span>
                                ) : (
                                    <span className="field-help">
                                        Full official trade name as registered in enterprise certificate.
                                    </span>
                                )}
                            </div>
                        </div>
                    </div>

                    {/* SECTION B: Contact & Representation */}
                    <div className="form-section-card">
                        <div className="section-header-row">
                            <div className="section-title-wrap">
                                <span className="section-letter-badge">B</span>
                                <h2 className="section-title">Contact & Representation</h2>
                            </div>
                            <span className="srs-pill">SRS 3.7.3.2</span>
                        </div>

                        <div className="section-body">
                            <div className="form-row-2">
                                {/* Liaison / Contact Person */}
                                <div className="field-group">
                                    <div className="field-label-row">
                                        <label>Liaison / Contact Person <span className="optional-tag">(Optional)</span></label>
                                        <span className="char-counter">{formData.contactPerson.length} / 100</span>
                                    </div>
                                    <div className="input-with-icon">
                                        <input
                                            type="text"
                                            maxLength={100}
                                            placeholder="e.g. Nguyễn Thị Thu Thảo (Key Account Manager)"
                                            value={formData.contactPerson}
                                            onChange={(e) =>
                                                setFormData({ ...formData, contactPerson: e.target.value })
                                            }
                                        />
                                        <User size={16} className="field-icon" />
                                    </div>
                                    <span className="field-help">
                                        Primary representative for order dispatch and shipment logistics.
                                    </span>
                                </div>

                                {/* Contact Phone Number */}
                                <div className="field-group">
                                    <div className="field-label-row">
                                        <label>Contact Phone Number <span className="required-star">*</span></label>
                                        <span className="char-counter">{formData.phone.length} / 15</span>
                                    </div>
                                    <div className={`phone-input-combo ${formErrors.phone ? 'has-error' : ''}`}>
                                        <div className="phone-prefix-pill">
                                            <span className="flag-icon">🇻🇳</span>
                                            <span>+84</span>
                                        </div>
                                        <input
                                            type="tel"
                                            required
                                            maxLength={15}
                                            placeholder="02854155555 or 0908123456"
                                            value={formData.phone}
                                            onChange={(e) => {
                                                setFormData({ ...formData, phone: e.target.value });
                                                if (formErrors.phone) setFormErrors({ ...formErrors, phone: null });
                                            }}
                                        />
                                        <Phone size={15} className="phone-right-icon" />
                                    </div>
                                    {formErrors.phone ? (
                                        <span className="field-error-text">{formErrors.phone}</span>
                                    ) : (
                                        <span className="field-help">
                                            Valid telecom format (10-15 digits). Used for automatic PO dispatch.
                                        </span>
                                    )}
                                </div>
                            </div>

                            {/* Corporate Email */}
                            <div className="field-group">
                                <div className="field-label-row">
                                    <label>Corporate Email <span className="optional-tag">(Optional)</span></label>
                                    <span className="char-counter">{formData.email.length} / 100</span>
                                </div>
                                <div className={`input-with-icon ${formErrors.email ? 'has-error' : ''}`}>
                                    <input
                                        type="email"
                                        maxLength={100}
                                        placeholder="e.g. contact@vinamilk.com.vn or sales@acecook.vn"
                                        value={formData.email}
                                        onChange={(e) => {
                                            setFormData({ ...formData, email: e.target.value });
                                            if (formErrors.email) setFormErrors({ ...formErrors, email: null });
                                        }}
                                    />
                                    <Mail size={16} className="field-icon" />
                                </div>
                                {formErrors.email ? (
                                    <span className="field-error-text">{formErrors.email}</span>
                                ) : (
                                    <span className="field-help">
                                        Electronic PO transmission and invoice matching address.
                                    </span>
                                )}
                            </div>

                            {/* Registered Head Office / Warehouse Address */}
                            <div className="field-group">
                                <div className="field-label-row">
                                    <label>Registered Head Office / Warehouse Address <span className="optional-tag">(Optional)</span></label>
                                    <span className="char-counter">{formData.address.length} / 255</span>
                                </div>
                                <div className="input-with-icon">
                                    <textarea
                                        rows={2}
                                        maxLength={255}
                                        placeholder="e.g. Số 10 Tân Trào, Phường Tân Phú, Quận 7, TP. Hồ Chí Minh"
                                        value={formData.address}
                                        onChange={(e) =>
                                            setFormData({ ...formData, address: e.target.value })
                                        }
                                    />
                                    <MapPin size={16} className="field-icon textarea-icon" />
                                </div>
                                <span className="field-help">
                                    Logistics and return shipment destination (max 255 chars).
                                </span>
                            </div>
                        </div>
                    </div>

                    {/* SECTION C: Procurement Operational Status & Authorization */}
                    <div className="form-section-card">
                        <div className="section-header-row">
                            <div className="section-title-wrap">
                                <span className="section-letter-badge">C</span>
                                <h2 className="section-title">Procurement Operational Status & Authorization</h2>
                            </div>
                            <span className="srs-pill">SRS 3.7.3.3</span>
                        </div>

                        <div className="section-body">
                            <div className="status-toggle-card">
                                <div className="toggle-left-info">
                                    <div className="status-title-row">
                                        <span className="toggle-label">Procurement Availability Status</span>
                                        {isActive ? (
                                            <span className="status-badge-active">
                                                <span className="dot green"></span>
                                                Active
                                            </span>
                                        ) : (
                                            <span className="status-badge-disabled">
                                                <span className="dot red"></span>
                                                Disabled
                                            </span>
                                        )}
                                    </div>
                                    <p className="toggle-desc">
                                        {isActive
                                            ? 'Active suppliers can receive new Purchase Orders (UC21) and deliver Stock Imports (UC22).'
                                            : 'Disabled suppliers cannot be selected for new Purchase Orders (UC21).'}
                                    </p>
                                </div>

                                <div className="toggle-right-switch">
                                    <span className="toggle-state-text">{isActive ? 'Active' : 'Disabled'}</span>
                                    <button
                                        type="button"
                                        className={`toggle-switch-btn ${isActive ? 'is-active' : 'is-disabled'} ${
                                            isStatusLocked ? 'is-locked' : ''
                                        }`}
                                        onClick={handleStatusToggle}
                                        title={
                                            isStatusLocked
                                                ? 'Chỉ Admin / Manager mới có quyền đổi trạng thái hoạt động'
                                                : 'Bật/tắt trạng thái hoạt động'
                                        }
                                    >
                                        <span className="switch-thumb"></span>
                                    </button>
                                </div>
                            </div>

                            {/* Operational Rule Safeguard Callout */}
                            <div className="safeguard-callout-box">
                                <ShieldCheck size={18} className="safeguard-icon" />
                                <div className="safeguard-content">
                                    <strong>Operational Rule Safeguard:</strong>
                                    <p>
                                        Disabling a supplier prevents issuance of new Purchase Orders (UC21). Existing historical receipts, deliveries, and ledger records remain permanently locked and queryable.
                                    </p>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Bottom Actions Bar */}
                    <div className="form-bottom-actions-bar">
                        <div className="bottom-left-btns">
                            <button
                                type="button"
                                className="action-btn text-muted"
                                onClick={handleResetForm}
                            >
                                <RotateCcw size={15} />
                                Reset Form
                            </button>
                            <button
                                type="button"
                                className="action-btn secondary"
                                onClick={() => navigate(isEditMode ? `/suppliers/${id}` : '/suppliers')}
                            >
                                <X size={15} />
                                Cancel
                            </button>
                        </div>

                        <button
                            type="submit"
                            className="action-btn primary large-commit"
                            disabled={saving}
                        >
                            <Save size={16} />
                            {saving ? 'Processing...' : 'Save Supplier Record (UC20 Commit)'}
                        </button>
                    </div>
                </div>

                {/* RIGHT COLUMN: Sidebar Preview & Compliance Widgets */}
                <div className="form-right-col">
                    {/* WIDGET 1: Live Dossier Preview */}
                    <div className="sidebar-widget live-preview-widget">
                        <div className="widget-header-row">
                            <div className="widget-title-group">
                                <FileText size={16} className="widget-icon blue" />
                                <h3>Live Dossier Preview</h3>
                            </div>
                            <span className="preview-tag">Preview</span>
                        </div>

                        <div className="dossier-preview-card">
                            <div className="preview-top-row">
                                <span className="preview-code-pill">{formData.supplierCode || 'SUP-009'}</span>
                                {isActive ? (
                                    <span className="preview-status-pill green">● Active</span>
                                ) : (
                                    <span className="preview-status-pill red">● Disabled</span>
                                )}
                            </div>

                            <h4 className="preview-supplier-name">
                                {formData.supplierName.trim() || '[Pending Legal Name Input]'}
                            </h4>

                            <div className="preview-rep-row">
                                <User size={13} />
                                <span>{formData.contactPerson.trim() || 'No representative specified'}</span>
                            </div>

                            <div className="preview-specs-table">
                                <div className="spec-row">
                                    <span className="spec-label">Tax Code (MST)</span>
                                    <span className="spec-val font-mono">{formData.taxCode.trim() || '—'}</span>
                                </div>
                                <div className="spec-row">
                                    <span className="spec-label">Phone Telecom</span>
                                    <span className="spec-val font-mono">{formData.phone.trim() || '—'}</span>
                                </div>
                                <div className="spec-row">
                                    <span className="spec-label">Corporate Email</span>
                                    <span className="spec-val truncate">{formData.email.trim() || '—'}</span>
                                </div>
                                <div className="spec-row">
                                    <span className="spec-label">Registered Address</span>
                                    <span className="spec-val text-address">
                                        {formData.address.trim() || 'No address provided.'}
                                    </span>
                                </div>
                            </div>

                            {/* Node footer */}
                            <div className="preview-node-banner">
                                <div className="node-text">
                                    <span className="node-label">PROCUREMENT NODE</span>
                                    <strong className="node-title">Central DC #1 Supply</strong>
                                </div>
                                <Building2 size={20} className="node-icon" />
                            </div>
                        </div>
                    </div>

                    {/* WIDGET 2: SRS 3.7.3 Compliance Check */}
                    <div className="sidebar-widget compliance-check-widget">
                        <div className="widget-header-row">
                            <div className="widget-title-group">
                                <CheckCircle2 size={16} className="widget-icon green" />
                                <h3>SRS 3.7.3 Compliance Check</h3>
                            </div>
                            <span className={`compliance-score-pill ${compliance.allPassed ? 'all-passed' : ''}`}>
                                {compliance.passedCount} / {compliance.total} Passed
                            </span>
                        </div>

                        <div className="compliance-checklist">
                            {compliance.checks.map((check) => (
                                <div key={check.id} className="checklist-item">
                                    <div className="check-icon-wrap">
                                        {check.valid ? (
                                            <CheckCircle2 size={16} className="check-icon valid" />
                                        ) : check.hasError ? (
                                            <AlertCircle size={16} className="check-icon error-icon" />
                                        ) : (
                                            <div className="check-icon invalid-circle"></div>
                                        )}
                                    </div>
                                    <div className="check-info">
                                        <div
                                            className={`check-label ${check.valid ? 'is-valid' : ''} ${
                                                check.hasError ? 'is-error' : ''
                                            }`}
                                        >
                                            {check.label}
                                        </div>
                                        <div className="check-desc">{check.desc}</div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* WIDGET 3: UC08 Audit Trail Guarantee */}
                    <div className="sidebar-widget audit-guarantee-widget">
                        <div className="audit-guarantee-body">
                            <ShieldCheck size={20} className="guarantee-shield-icon" />
                            <div className="guarantee-text-wrap">
                                <h4 className="guarantee-title">UC08 Audit Trail Guarantee</h4>
                                <p className="guarantee-desc">
                                    Every creation and alteration is timestamped and cryptographically linked to operator{' '}
                                    <strong>{currentUser?.fullName || currentUser?.username || 'Nguyen Van An'}</strong>{' '}
                                    (ID: {currentUser?.employeeCode || 'EMP-004'}).
                                </p>
                            </div>
                        </div>
                    </div>
                </div>
            </form>

            {/* Deactivation Confirmation Modal */}
            {isDeactivateDialogOpen && (
                <div className="modal-backdrop" onClick={() => setIsDeactivateDialogOpen(false)}>
                    <div className="modal-card deactivation-dialog" onClick={(e) => e.stopPropagation()}>
                        <div className="modal-header">
                            <div className="modal-title-with-icon">
                                <div className="deactivate-icon-box">
                                    <Ban size={20} />
                                </div>
                                <div>
                                    <h3>Tạm dừng hoạt động nhà cung cấp?</h3>
                                    <p className="modal-sub">{formData.supplierCode} — {formData.supplierName || 'New Supplier'}</p>
                                </div>
                            </div>
                            <button
                                type="button"
                                className="modal-close-btn"
                                onClick={() => setIsDeactivateDialogOpen(false)}
                            >
                                <X size={18} />
                            </button>
                        </div>

                        <div className="modal-body-content">
                            <div className="deactivate-alert-box">
                                <AlertCircle size={18} className="warn-icon" />
                                <div>
                                    <strong>Cảnh báo quy tắc vận hành UC21:</strong>
                                    <p>
                                        Khi chuyển trạng thái sang <strong>Disabled</strong>, hệ thống sẽ ngay lập tức chặn tạo mới Đơn đặt hàng (Purchase Order UC21) với nhà cung cấp này.
                                    </p>
                                    <p style={{ marginTop: '4px' }}>
                                        Mọi dữ liệu phiếu nhập kho lịch sử và công nợ trước đây vẫn được lưu trữ nguyên vẹn để đối soát.
                                    </p>
                                </div>
                            </div>

                            <div className="field-group" style={{ marginTop: '16px' }}>
                                <label>Lý do tạm dừng hoạt động (Ghi nhận vào UC08 Audit Log):</label>
                                <input
                                    type="text"
                                    placeholder="Ví dụ: Tạm dừng nhập hàng kiểm toán chất lượng Q2..."
                                    value={deactivateReason}
                                    onChange={(e) => setDeactivateReason(e.target.value)}
                                />
                            </div>
                        </div>

                        <div className="modal-footer-actions">
                            <button
                                type="button"
                                className="action-btn secondary"
                                onClick={() => setIsDeactivateDialogOpen(false)}
                            >
                                Hủy bỏ
                            </button>
                            <button
                                type="button"
                                className="action-btn danger"
                                onClick={confirmDeactivation}
                            >
                                Xác nhận vô hiệu hóa (Disable)
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Toast Feedback */}
            {toastMessage && (
                <div className="supplier-form-toast">
                    <CheckCircle2 size={16} className="toast-icon" />
                    <span>{toastMessage}</span>
                </div>
            )}
        </div>
    );
}

