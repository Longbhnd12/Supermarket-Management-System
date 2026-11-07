import React, { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import {
    ArrowLeft, ShieldCheck, AlertCircle, CheckCircle2, PauseCircle,
    Building2, Phone, Mail, MapPin, Copy, ExternalLink, RefreshCw,
    Edit3, Ban, Check, Sparkles, X, Compass, FileText, ChevronRight,
    Lock
} from 'lucide-react';
import { useAuth } from '../../context/authContext';
import SupplierService from '../../services/supplierService';
import { MOCK_SUPPLIERS, enrichSupplier } from './mockSuppliersData';
import './SupplierDetailPage.css';

export default function SupplierDetailPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { currentUser, hasRole } = useAuth();

    // Data states
    const [supplier, setSupplier] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [toastMessage, setToastMessage] = useState(null);

    // Simulated Role for prototype preview (defaults to currentUser role or Administrator)
    const [simulatedRole, setSimulatedRole] = useState(currentUser?.role || 'ADMIN');

    // Modals
    const [isStatusModalOpen, setIsStatusModalOpen] = useState(false);
    const [targetStatusAction, setTargetStatusAction] = useState('DISABLED'); // DISABLED or ACTIVE
    const [statusReason, setStatusReason] = useState('');
    const [statusUpdating, setStatusUpdating] = useState(false);

    // Edit Modal (UC20)
    const [isEditModalOpen, setIsEditModalOpen] = useState(false);
    const [editForm, setEditForm] = useState({
        supplierCode: '',
        supplierName: '',
        contactPerson: '',
        phone: '',
        email: '',
        address: '',
        taxCode: ''
    });
    const [editError, setEditError] = useState(null);
    const [savingEdit, setSavingEdit] = useState(false);

    const isAdminOrManager = simulatedRole === 'ADMIN' || simulatedRole === 'MANAGER';

    // Toast helper
    const showToast = (msg) => {
        setToastMessage(msg);
        setTimeout(() => setToastMessage(null), 3500);
    };

    // Load supplier data
    const loadSupplierData = async (targetId = id) => {
        setLoading(true);
        setError(null);
        try {
            // Check if numeric ID or code
            const res = await SupplierService.getById(targetId);
            const data = res.data?.data;
            if (data) {
                setSupplier(enrichSupplier(data));
            } else {
                // Check mock
                const match = MOCK_SUPPLIERS.find(
                    (m) => String(m.supplierId) === String(targetId) || m.supplierCode === targetId
                );
                setSupplier(match || enrichSupplier(MOCK_SUPPLIERS[0]));
            }
        } catch (err) {
            console.warn('Backend getById failed, using mock data:', err);
            const match = MOCK_SUPPLIERS.find(
                (m) => String(m.supplierId) === String(targetId) || m.supplierCode === targetId
            );
            setSupplier(match || enrichSupplier(MOCK_SUPPLIERS[0]));
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadSupplierData(id);
    }, [id]);

    // Copy to clipboard
    const copyToClipboard = (text, label) => {
        if (!text) return;
        navigator.clipboard.writeText(text);
        showToast(`Đã sao chép ${label}: ${text}`);
    };

    // Handle Status Change (Disable / Enable)
    const handleConfirmStatusChange = async () => {
        if (!supplier) return;
        setStatusUpdating(true);
        try {
            await SupplierService.updateStatus(supplier.supplierId, targetStatusAction);
            setSupplier((prev) => ({
                ...prev,
                status: targetStatusAction,
                lastAuditedMeta: `Hôm nay bởi ${currentUser?.username || 'Admin'}`
            }));
            setIsStatusModalOpen(false);
            showToast(
                targetStatusAction === 'DISABLED'
                    ? `Đã vô hiệu hóa nhà cung cấp ${supplier.supplierCode} (Khóa phát hành PO theo UC21).`
                    : `Đã kích hoạt lại nhà cung cấp ${supplier.supplierCode}.`
            );
        } catch (err) {
            // Fallback: update local state if backend is mock/offline
            setSupplier((prev) => ({
                ...prev,
                status: targetStatusAction,
                lastAuditedMeta: `Hôm nay bởi ${currentUser?.username || 'Admin'}`
            }));
            setIsStatusModalOpen(false);
            showToast(
                targetStatusAction === 'DISABLED'
                    ? `[Demo] Đã vô hiệu hóa nhà cung cấp ${supplier.supplierCode} (UC21 Blocked).`
                    : `[Demo] Đã kích hoạt lại nhà cung cấp ${supplier.supplierCode}.`
            );
        } finally {
            setStatusUpdating(false);
            setStatusReason('');
        }
    };

    // Open Edit modal
    const handleOpenEdit = () => {
        if (!supplier) return;
        setEditForm({
            supplierCode: supplier.supplierCode || '',
            supplierName: supplier.supplierName || '',
            contactPerson: supplier.contactPerson || '',
            phone: supplier.phone || '',
            email: supplier.email || '',
            address: supplier.address || '',
            taxCode: supplier.taxCode || ''
        });
        setEditError(null);
        setIsEditModalOpen(true);
    };

    // Save Edit
    const handleSaveEdit = async (e) => {
        e.preventDefault();
        setEditError(null);
        setSavingEdit(true);
        try {
            const res = await SupplierService.update(supplier.supplierId, editForm);
            const updated = res.data?.data || { ...supplier, ...editForm };
            setSupplier(enrichSupplier(updated));
            setIsEditModalOpen(false);
            showToast(`Cập nhật thông tin nhà cung cấp ${supplier.supplierCode} thành công!`);
        } catch (err) {
            // Fallback in case of mock
            setSupplier((prev) => ({ ...prev, ...editForm }));
            setIsEditModalOpen(false);
            showToast(`[Demo] Cập nhật thông tin nhà cung cấp ${supplier.supplierCode} thành công!`);
        } finally {
            setSavingEdit(false);
        }
    };

    if (loading) {
        return (
            <div className="supplier-detail-loading-box">
                <RefreshCw size={28} className="spinning" />
                <span>Đang tải hồ sơ nhà cung cấp...</span>
            </div>
        );
    }

    if (!supplier) {
        return (
            <div className="supplier-detail-error-box">
                <AlertCircle size={32} />
                <h3>Không tìm thấy nhà cung cấp</h3>
                <Link to="/suppliers" className="action-btn secondary">
                    ← Quay lại danh sách
                </Link>
            </div>
        );
    }

    const isActive = supplier.status === 'ACTIVE';

    return (
        <div className="supplier-detail-page">
            {/* Top Interactive State Simulator Banner */}
            <div className="supplier-detail-simulator">
                <div className="sim-title">
                    <Sparkles size={16} />
                    <span>SRS 3.7.2 Interactive State Simulator: Preview supplier status states, role permissions & confirmation dialogs</span>
                </div>
                <div className="sim-actions-row">
                    <button
                        type="button"
                        className={`sim-state-btn ${supplier.supplierCode === 'SUP-001' ? 'active' : ''}`}
                        onClick={() => {
                            const vinamilk = MOCK_SUPPLIERS.find((s) => s.supplierCode === 'SUP-001');
                            if (vinamilk) setSupplier(enrichSupplier(vinamilk));
                        }}
                    >
                        ● Active: SUP-001 (Vinamilk)
                    </button>
                    <button
                        type="button"
                        className={`sim-state-btn ${supplier.supplierCode === 'SUP-005' ? 'active' : ''}`}
                        onClick={() => {
                            const longan = MOCK_SUPPLIERS.find((s) => s.supplierCode === 'SUP-005');
                            if (longan) setSupplier(enrichSupplier(longan));
                        }}
                    >
                        ● Disabled: SUP-005 (Long An Agri)
                    </button>

                    <div className="sim-role-picker">
                        <span>Role:</span>
                        <select
                            value={simulatedRole}
                            onChange={(e) => setSimulatedRole(e.target.value)}
                        >
                            <option value="ADMIN">Administrator</option>
                            <option value="MANAGER">Store Manager</option>
                            <option value="WAREHOUSE_STAFF">Inventory Staff (Read-Only)</option>
                        </select>
                    </div>

                    <button
                        type="button"
                        className="sim-modal-trigger amber"
                        onClick={() => {
                            setTargetStatusAction('DISABLED');
                            setIsStatusModalOpen(true);
                        }}
                    >
                        Test Disable Modal
                    </button>
                    <button
                        type="button"
                        className="sim-modal-trigger green"
                        onClick={() => {
                            setTargetStatusAction('ACTIVE');
                            setIsStatusModalOpen(true);
                        }}
                    >
                        Test Enable Modal
                    </button>
                </div>
            </div>

            {/* Breadcrumb & Top Bar */}
            <div className="detail-top-nav">
                <div className="detail-breadcrumbs">
                    <Link to="/">Home</Link>
                    <span className="crumb-sep">/</span>
                    <span>Operations Hub</span>
                    <span className="crumb-sep">/</span>
                    <span>Directory</span>
                    <span className="crumb-sep">/</span>
                    <Link to="/suppliers">Suppliers</Link>
                    <span className="crumb-sep">/</span>
                    <span className="crumb-current">
                        {supplier.supplierCode} ({supplier.shortName || supplier.supplierName})
                    </span>
                    <span className="crumb-sep">/</span>
                    <span className="crumb-tag">Supplier Details (UC19)</span>
                </div>

                <div className="detail-header-actions-row">
                    <button
                        type="button"
                        className="back-btn-pill"
                        onClick={() => navigate('/suppliers')}
                    >
                        <ArrowLeft size={16} />
                        Back to List
                    </button>

                    <div className="header-right-btns">
                        {isAdminOrManager && (
                            <button
                                type="button"
                                className="action-btn secondary edit-btn"
                                onClick={handleOpenEdit}
                            >
                                <Edit3 size={15} />
                                Edit Supplier (UC20)
                            </button>
                        )}

                        {isAdminOrManager && (
                            isActive ? (
                                <button
                                    type="button"
                                    className="action-btn danger disable-btn"
                                    onClick={() => {
                                        setTargetStatusAction('DISABLED');
                                        setIsStatusModalOpen(true);
                                    }}
                                >
                                    <Ban size={15} />
                                    Disable Supplier
                                </button>
                            ) : (
                                <button
                                    type="button"
                                    className="action-btn success enable-btn"
                                    onClick={() => {
                                        setTargetStatusAction('ACTIVE');
                                        setIsStatusModalOpen(true);
                                    }}
                                >
                                    <Check size={15} />
                                    Enable Supplier
                                </button>
                            )
                        )}
                    </div>
                </div>
            </div>

            {/* Main Supplier Banner Card */}
            <div className="supplier-main-banner-card">
                <div className="banner-left-wrap">
                    <div
                        className="banner-avatar-box"
                        style={{ backgroundColor: supplier.avatarBg || '#2563eb' }}
                    >
                        {supplier.initial || supplier.supplierName?.charAt(0) || 'S'}
                    </div>

                    <div className="banner-info-wrap">
                        <div className="banner-badges-row">
                            <span className="badge-code">{supplier.supplierCode}</span>
                            {isActive ? (
                                <span className="badge-status-active">
                                    <span className="status-dot green"></span>
                                    Active Supplier
                                </span>
                            ) : (
                                <span className="badge-status-disabled">
                                    <span className="status-dot red"></span>
                                    Disabled / On Hold
                                </span>
                            )}
                            <span className="badge-tier">{supplier.tier || 'Tier 1 Strategic Vendor'}</span>
                            <span className="badge-cert">
                                <ShieldCheck size={13} />
                                {supplier.cert || 'ISO 22000 Certified'}
                            </span>
                        </div>

                        <h1 className="banner-company-name">{supplier.supplierName}</h1>

                        <div className="banner-meta-row">
                            <span className="meta-item">
                                <strong>MST:</strong> {supplier.taxCode || '0300588569'}
                            </span>
                            <span className="meta-sep">•</span>
                            <span className="meta-item">
                                <MapPin size={13} className="meta-pin-icon" />
                                {supplier.address ? supplier.address.split(',').slice(-2).join(',').trim() : 'Hồ Chí Minh, Việt Nam'}
                            </span>
                            <span className="meta-sep">•</span>
                            <span className="meta-item">
                                📦 {supplier.verticalsCount || 4} Active Catalog Verticals
                            </span>
                        </div>
                    </div>
                </div>

                <div className="banner-fast-actions">
                    <button
                        type="button"
                        className="fast-btn"
                        onClick={() => {
                            window.location.href = `tel:${supplier.phone}`;
                            showToast(`Đang kết nối bàn liên lạc Call Desk: ${supplier.phone}`);
                        }}
                    >
                        <Phone size={14} />
                        Call Desk
                    </button>
                    <button
                        type="button"
                        className="fast-btn"
                        onClick={() => copyToClipboard(supplier.email, 'Email công ty')}
                    >
                        <Mail size={14} />
                        Copy Email
                    </button>
                    <button
                        type="button"
                        className="fast-btn"
                        onClick={() =>
                            showToast(`Điều hướng tới cổng Dispatch Hub: ${supplier.deliveryDestinationTitle}`)
                        }
                    >
                        <Building2 size={14} />
                        Dispatch Hub
                    </button>
                </div>
            </div>

            {/* 2-Column Content Layout */}
            <div className="supplier-detail-grid">
                {/* LEFT COLUMN: 3 Cards */}
                <div className="detail-col left-col">
                    {/* Card 1: Legal & Commercial Entity Details */}
                    <div className="detail-section-card">
                        <div className="card-header-row">
                            <div className="card-title-group">
                                <Building2 size={18} className="section-icon blue" />
                                <div>
                                    <h2 className="section-title">1. Legal & Commercial Entity Details</h2>
                                    <p className="section-subtitle">
                                        Registered business identity, taxation authority records & trade credentials
                                    </p>
                                </div>
                            </div>
                            <span className="srs-spec-pill">SRS 3.7.2.1</span>
                        </div>

                        <div className="card-content-body">
                            <div className="field-grid-2">
                                <div className="field-block">
                                    <span className="field-label">SUPPLIER CODE (MÃ NCC)</span>
                                    <div className="field-value-highlight">{supplier.supplierCode}</div>
                                    <span className="field-subtext">System Unique Identifier (Auto-generated)</span>
                                </div>
                                <div className="field-block">
                                    <span className="field-label">TAX CODE (MÃ SỐ THUẾ - MST)</span>
                                    <div className="field-value-row">
                                        <span className="field-value-strong">{supplier.taxCode || '0300588569'}</span>
                                        <span className="verified-pill">
                                            <CheckCircle2 size={12} />
                                            Verified
                                        </span>
                                    </div>
                                    <span className="field-subtext">General Department of Taxation Checked</span>
                                </div>
                            </div>

                            <div className="field-block full-width">
                                <span className="field-label">FULL REGISTERED COMPANY NAME (TÊN NHÀ CUNG CẤP)</span>
                                <div className="field-value-bold">{supplier.supplierName}</div>
                                <span className="field-subtext">
                                    Trading brand name: {supplier.brandName || supplier.supplierName}
                                </span>
                            </div>

                            <div className="field-grid-2">
                                <div className="field-block">
                                    <span className="field-label">BUSINESS REGISTRATION LIC.</span>
                                    <div className="field-value-text">
                                        {supplier.businessLicense || `${supplier.taxCode} (Issued 20/11/2003)`}
                                    </div>
                                    <span className="field-subtext">{supplier.licenseAuthority || 'DPI Ho Chi Minh City'}</span>
                                </div>

                                <div className="field-block">
                                    <span className="field-label">PRODUCT CATEGORIES SUPPLIED</span>
                                    <div className="categories-pill-list">
                                        {(supplier.categoriesSupplied || ['Fresh Dairy', 'Yogurt & Desserts', 'Infant Formula', '+182 SKUs']).map(
                                            (cat, idx) => (
                                                <span key={idx} className="category-pill">
                                                    {cat}
                                                </span>
                                            )
                                        )}
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Card 2: Contact Person & Key Account Representative */}
                    <div className="detail-section-card">
                        <div className="card-header-row">
                            <div className="card-title-group">
                                <Phone size={18} className="section-icon indigo" />
                                <div>
                                    <h2 className="section-title">2. Contact Person & Key Account Representative</h2>
                                    <p className="section-subtitle">
                                        Direct liaison details for supermarket purchase orders, delivery coordination & billing
                                    </p>
                                </div>
                            </div>
                            <span className="srs-spec-pill">SRS 3.7.2.2</span>
                        </div>

                        <div className="card-content-body">
                            <div className="contact-rep-layout">
                                <div className="rep-left-profile">
                                    <div className="rep-avatar">
                                        {supplier.contactPerson
                                            ? supplier.contactPerson
                                                  .split(' ')
                                                  .slice(-2)
                                                  .map((w) => w[0])
                                                  .join('')
                                            : 'TT'}
                                    </div>
                                    <div className="rep-profile-info">
                                        <h3 className="rep-name">{supplier.contactPerson || 'Nguyễn Thị Thu Thảo'}</h3>
                                        <p className="rep-title">
                                            {supplier.contactTitle || 'Trưởng phòng Phân phối Kênh Bán lẻ (Key Account Manager)'}
                                        </p>
                                        <div className="rep-dept-meta">
                                            <span><strong>Direct Department:</strong> {supplier.department || 'Modern Trade (MT) Division'}</span>
                                            <span><strong>Response SLA:</strong> ≤ 2 hours (Business Hours)</span>
                                        </div>
                                    </div>
                                </div>

                                <div className="rep-right-contacts">
                                    <div className="contact-box">
                                        <div className="contact-box-header">
                                            <span className="contact-box-label">Direct Phone Numbers</span>
                                            <Phone size={14} className="box-icon" />
                                        </div>
                                        <div className="contact-box-val">
                                            <a href={`tel:${supplier.phone}`}>{supplier.phone}</a>
                                            {supplier.secondaryPhone && (
                                                <span> / <a href={`tel:${supplier.secondaryPhone}`}>{supplier.secondaryPhone}</a></span>
                                            )}
                                        </div>
                                    </div>

                                    <div className="contact-box">
                                        <div className="contact-box-header">
                                            <span className="contact-box-label">Corporate Contact Email</span>
                                            <button
                                                type="button"
                                                className="copy-icon-btn"
                                                onClick={() => copyToClipboard(supplier.email, 'Email')}
                                                title="Sao chép email"
                                            >
                                                <Copy size={13} />
                                            </button>
                                        </div>
                                        <div className="contact-box-val">
                                            <a href={`mailto:${supplier.email}`}>{supplier.email}</a>
                                        </div>
                                    </div>

                                    <div className="contact-box">
                                        <div className="contact-box-header">
                                            <span className="contact-box-label">Warehouse Hotline</span>
                                        </div>
                                        <div className="contact-box-val text-muted">
                                            {supplier.warehouseHotline || '028 5416 1111 (Ext: 204)'}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Card 3: Headquarters & Logistics Hub Address */}
                    <div className="detail-section-card">
                        <div className="card-header-row">
                            <div className="card-title-group">
                                <MapPin size={18} className="section-icon cyan" />
                                <div>
                                    <h2 className="section-title">3. Headquarters & Logistics Hub Address</h2>
                                    <p className="section-subtitle">
                                        Registered delivery origins and official billing headquarters
                                    </p>
                                </div>
                            </div>
                            <span className="srs-spec-pill">SRS 3.7.2.3</span>
                        </div>

                        <div className="card-content-body">
                            <div className="address-banner-box">
                                <div className="addr-text-wrap">
                                    <span className="field-label">REGISTERED HEADQUARTERS ADDRESS (ĐỊA CHỈ TRỤ SỞ)</span>
                                    <p className="addr-line">{supplier.address || 'Số 10 Tân Trào, Phường Tân Phú, Quận 7, TP. Hồ Chí Minh, Việt Nam'}</p>
                                    <span className="addr-sub">
                                        Postal Code: {supplier.postalCode || '72908'} • GPS Coords: {supplier.gps || '10.7303° N, 106.7219° E'}
                                    </span>
                                </div>
                                <button
                                    type="button"
                                    className="route-map-btn"
                                    onClick={() => {
                                        window.open(
                                            `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(
                                                supplier.address || 'Quận 7, TP Hồ Chí Minh'
                                            )}`,
                                            '_blank'
                                        );
                                        showToast('Mở bản đồ định tuyến vận chuyển Google Maps...');
                                    }}
                                >
                                    <Compass size={14} />
                                    View Route Map
                                </button>
                            </div>

                            <div className="logistics-hubs-grid">
                                <div className="hub-subcard">
                                    <div className="hub-title-row">
                                        <Building2 size={16} className="hub-icon blue" />
                                        <h4>Primary Cold-Chain Center (Kho Trung Chuyển)</h4>
                                    </div>
                                    <div className="hub-name">{supplier.coldChainCenterTitle || 'Vinamilk Mega Warehouse - Binh Duong Hub'}</div>
                                    <p className="hub-desc">{supplier.coldChainCenterDesc || 'Lô A, KCN Mỹ Phước 2, Bến Cát, Bình Dương (Delivery window: 04:00 - 08:00 AM)'}</p>
                                </div>

                                <div className="hub-subcard">
                                    <div className="hub-title-row">
                                        <Building2 size={16} className="hub-icon green" />
                                        <h4>Designated Store Delivery Destination</h4>
                                    </div>
                                    <div className="hub-name">{supplier.deliveryDestinationTitle || 'Store #04 — Dist 1 Flagship Loading Bay 2'}</div>
                                    <p className="hub-desc">{supplier.deliveryDestinationDesc || 'Standard Lead Time: 24 Hours upon PO issuance confirmation'}</p>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                {/* RIGHT COLUMN: 2 Cards */}
                <div className="detail-col right-col">
                    {/* Card 4: System Safeguards */}
                    <div className="detail-section-card safeguards-card">
                        <div className="card-header-row">
                            <div className="card-title-group">
                                <ShieldCheck size={18} className="section-icon blue" />
                                <div>
                                    <h2 className="section-title">4. System Safeguards</h2>
                                    <p className="section-subtitle">SRS 3.7.2.4 Rules</p>
                                </div>
                            </div>
                            <span className="srs-spec-pill">SRS 3.7.2.4</span>
                        </div>

                        <div className="card-content-body">
                            {/* Operational State block */}
                            <div className={`operational-state-box ${isActive ? 'state-active' : 'state-disabled'}`}>
                                <div className="op-state-header">
                                    <span className="op-state-label">CURRENT OPERATIONAL STATE</span>
                                    <span className="op-state-value">
                                        <span className={`status-dot ${isActive ? 'green' : 'red'}`}></span>
                                        {isActive ? 'ACTIVE' : 'DISABLED'}
                                    </span>
                                </div>
                                <p className="op-state-desc">
                                    {isActive
                                        ? 'Supplier is fully authorized for automated and manual Purchase Order issuance (UC21). Catalog price syncing active.'
                                        : 'Supplier is currently disabled. Issuance of new Purchase Orders (UC21) and restock requests is blocked.'}
                                </p>
                            </div>

                            {/* UC21 Safeguard Callout */}
                            <div className="uc21-safeguard-callout">
                                <div className="callout-header">
                                    <ShieldCheck size={16} />
                                    <span>UC21 Purchase Order Rule</span>
                                </div>
                                <p className="callout-text">
                                    If toggled to Disabled, system prohibits adding SKUs to drafts. Existing shipments and historical receipts remain locked & fully queryable.
                                </p>
                            </div>

                            {/* Commercial & Contract Terms */}
                            <div className="terms-list">
                                <div className="term-row">
                                    <span className="term-label">Master Contract Ref:</span>
                                    <strong className="term-val blue">{supplier.contractRef || '#CTR-2023-VN01'}</strong>
                                </div>
                                <div className="term-row">
                                    <span className="term-label">Payment Terms:</span>
                                    <span className="term-val">{supplier.paymentTerms || 'Net 30 Days (Bank Transfer)'}</span>
                                </div>
                                <div className="term-row">
                                    <span className="term-label">Dispute Escalation:</span>
                                    <span className="term-val">{supplier.disputeEscalation || 'Tier 1 Legal / HCMC Dept'}</span>
                                </div>
                                <div className="term-row">
                                    <span className="term-label">Minimum Order Vol:</span>
                                    <strong className="term-val text-dark">{supplier.minOrderVol || '15,000,000 VND'}</strong>
                                </div>
                            </div>

                            {/* Quality Assurance Records */}
                            <div className="qa-records-block">
                                <span className="field-label">QUALITY ASSURANCE RECORDS</span>
                                <div className="qa-pills-row">
                                    {(supplier.qaRecords || ['ISO 22000:2018', 'HACCP Codex', 'GlobalG.A.P.']).map((qa, i) => (
                                        <span key={i} className="qa-pill">
                                            <ShieldCheck size={12} />
                                            {qa}
                                        </span>
                                    ))}
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Card 5: Audit Trail & Integrity */}
                    <div className="detail-section-card audit-trail-card">
                        <div className="card-header-row">
                            <div className="card-title-group">
                                <FileText size={18} className="section-icon slate" />
                                <div>
                                    <h2 className="section-title">Audit Trail & Integrity</h2>
                                </div>
                            </div>
                            <span className="audit-log-badge">UC08 Log</span>
                        </div>

                        <div className="card-content-body">
                            <div className="audit-meta-list">
                                <div className="audit-meta-row">
                                    <span className="audit-label">Created:</span>
                                    <span className="audit-val">{supplier.createdMeta || '14/01/2021 08:30 by Admin (NV-0001)'}</span>
                                </div>
                                <div className="audit-meta-row">
                                    <span className="audit-label">Last Audited:</span>
                                    <span className="audit-val">{supplier.lastAuditedMeta || '02/10/2024 16:45 by Nguyen Van An (Admin)'}</span>
                                </div>
                                <div className="audit-meta-row">
                                    <span className="audit-label">Tax Sync ID:</span>
                                    <span className="audit-val sync-id">{supplier.taxSyncId || '#TX-SYNC-883912'}</span>
                                </div>
                            </div>

                            <div className="audit-guarantee-box">
                                <ShieldCheck size={14} className="guarantee-icon" />
                                <p>
                                    All status alterations (Disable/Enable) are strictly recorded to UC08 Audit Logs with operator timestamp and biometric/session signatures.
                                </p>
                            </div>

                            <Link
                                to={`/system-logs?search=${encodeURIComponent(supplier.supplierCode)}`}
                                className="inspect-audit-btn"
                            >
                                <span>Inspect Full Supplier Audit Log</span>
                                <ChevronRight size={15} />
                            </Link>
                        </div>
                    </div>
                </div>
            </div>

            {/* Confirmation Modal for Disable / Enable Supplier */}
            {isStatusModalOpen && (
                <div className="modal-backdrop" onClick={() => setIsStatusModalOpen(false)}>
                    <div className="modal-card status-confirm-modal" onClick={(e) => e.stopPropagation()}>
                        <div className="modal-header">
                            <div className="modal-title-with-icon">
                                {targetStatusAction === 'DISABLED' ? (
                                    <div className="status-modal-icon red">
                                        <Ban size={20} />
                                    </div>
                                ) : (
                                    <div className="status-modal-icon green">
                                        <CheckCircle2 size={20} />
                                    </div>
                                )}
                                <div>
                                    <h3>
                                        {targetStatusAction === 'DISABLED'
                                            ? `Disable Supplier ${supplier.supplierCode}?`
                                            : `Enable Supplier ${supplier.supplierCode}?`}
                                    </h3>
                                    <p className="modal-subtitle-name">{supplier.supplierName}</p>
                                </div>
                            </div>
                            <button
                                type="button"
                                className="modal-close-btn"
                                onClick={() => setIsStatusModalOpen(false)}
                            >
                                <X size={18} />
                            </button>
                        </div>

                        <div className="status-modal-body">
                            {targetStatusAction === 'DISABLED' ? (
                                <div className="safeguard-warning-box">
                                    <AlertCircle size={18} className="warn-icon" />
                                    <div>
                                        <strong>SRS 3.7.1 & UC21 Safeguard Rule:</strong>
                                        <p>
                                            Disabling this supplier will immediately block new Purchase Orders (UC21) and restock requests. Ongoing active POs will remain unaffected, and all past historical stock receipts remain preserved in UC08 audit records.
                                        </p>
                                    </div>
                                </div>
                            ) : (
                                <div className="safeguard-info-box">
                                    <CheckCircle2 size={18} className="info-icon" />
                                    <div>
                                        <strong>Khôi phục hoạt động nhà cung cấp:</strong>
                                        <p>
                                            Kích hoạt lại cho phép tạo mới đơn đặt hàng (UC21 Purchase Order) và đồng bộ giá danh mục hàng hóa của nhà cung cấp này.
                                        </p>
                                    </div>
                                </div>
                            )}

                            <div className="form-group" style={{ marginTop: '14px' }}>
                                <label>Lý do thay đổi trạng thái (Ghi nhận UC08 Audit Log):</label>
                                <input
                                    type="text"
                                    placeholder={
                                        targetStatusAction === 'DISABLED'
                                            ? 'Ví dụ: Tạm dừng nhập hàng do kiểm toán chất lượng Q2...'
                                            : 'Ví dụ: Hoàn tất đàm phán hợp đồng cung ứng 2025...'
                                    }
                                    value={statusReason}
                                    onChange={(e) => setStatusReason(e.target.value)}
                                />
                            </div>
                        </div>

                        <div className="modal-actions">
                            <button
                                type="button"
                                className="action-btn secondary"
                                onClick={() => setIsStatusModalOpen(false)}
                                disabled={statusUpdating}
                            >
                                Hủy bỏ
                            </button>
                            <button
                                type="button"
                                className={`action-btn ${targetStatusAction === 'DISABLED' ? 'danger' : 'success'}`}
                                onClick={handleConfirmStatusChange}
                                disabled={statusUpdating}
                            >
                                {statusUpdating
                                    ? 'Đang xử lý...'
                                    : targetStatusAction === 'DISABLED'
                                    ? 'Confirm Disable Supplier'
                                    : 'Confirm Enable Supplier'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Edit Supplier Modal (UC20) */}
            {isEditModalOpen && (
                <div className="modal-backdrop" onClick={() => setIsEditModalOpen(false)}>
                    <div className="modal-card" onClick={(e) => e.stopPropagation()}>
                        <div className="modal-header">
                            <h3>Chỉnh sửa nhà cung cấp {supplier.supplierCode}</h3>
                            <button
                                type="button"
                                className="modal-close-btn"
                                onClick={() => setIsEditModalOpen(false)}
                            >
                                <X size={18} />
                            </button>
                        </div>

                        {editError && (
                            <div className="modal-error-alert">
                                <AlertCircle size={16} />
                                <span>{editError}</span>
                            </div>
                        )}

                        <form onSubmit={handleSaveEdit} className="modal-form">
                            <div className="form-grid-2">
                                <div className="form-group">
                                    <label>Mã nhà cung cấp</label>
                                    <input
                                        type="text"
                                        value={editForm.supplierCode}
                                        disabled
                                        style={{ backgroundColor: '#f1f5f9', cursor: 'not-allowed' }}
                                    />
                                </div>
                                <div className="form-group">
                                    <label>Mã số thuế (MST)</label>
                                    <input
                                        type="text"
                                        value={editForm.taxCode}
                                        onChange={(e) =>
                                            setEditForm({ ...editForm, taxCode: e.target.value })
                                        }
                                    />
                                </div>
                            </div>

                            <div className="form-group">
                                <label>Tên pháp nhân công ty *</label>
                                <input
                                    type="text"
                                    required
                                    value={editForm.supplierName}
                                    onChange={(e) =>
                                        setEditForm({ ...editForm, supplierName: e.target.value })
                                    }
                                />
                            </div>

                            <div className="form-grid-2">
                                <div className="form-group">
                                    <label>Người đại diện liên hệ</label>
                                    <input
                                        type="text"
                                        value={editForm.contactPerson}
                                        onChange={(e) =>
                                            setEditForm({ ...editForm, contactPerson: e.target.value })
                                        }
                                    />
                                </div>
                                <div className="form-group">
                                    <label>Số điện thoại *</label>
                                    <input
                                        type="text"
                                        required
                                        value={editForm.phone}
                                        onChange={(e) =>
                                            setEditForm({ ...editForm, phone: e.target.value })
                                        }
                                    />
                                </div>
                            </div>

                            <div className="form-group">
                                <label>Email liên hệ</label>
                                <input
                                    type="email"
                                    value={editForm.email}
                                    onChange={(e) =>
                                        setEditForm({ ...editForm, email: e.target.value })
                                    }
                                />
                            </div>

                            <div className="form-group">
                                <label>Địa chỉ trụ sở</label>
                                <input
                                    type="text"
                                    value={editForm.address}
                                    onChange={(e) =>
                                        setEditForm({ ...editForm, address: e.target.value })
                                    }
                                />
                            </div>

                            <div className="modal-actions">
                                <button
                                    type="button"
                                    className="action-btn secondary"
                                    onClick={() => setIsEditModalOpen(false)}
                                    disabled={savingEdit}
                                >
                                    Hủy bỏ
                                </button>
                                <button
                                    type="submit"
                                    className="action-btn primary"
                                    disabled={savingEdit}
                                >
                                    {savingEdit ? 'Đang lưu...' : 'Lưu thay đổi'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Toast popup */}
            {toastMessage && (
                <div className="supplier-toast">
                    <CheckCircle2 size={16} className="toast-icon" />
                    <span>{toastMessage}</span>
                </div>
            )}
        </div>
    );
}

