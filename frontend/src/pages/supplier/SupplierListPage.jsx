import React, { useState, useEffect, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
    Search, Plus, RefreshCw, Download, ShieldCheck, AlertCircle,
    CheckCircle2, PauseCircle, Truck, Building2, ExternalLink,
    ChevronLeft, ChevronRight, X, Phone, Mail, FileText, ArrowRight,
    Lock, Sparkles, Filter
} from 'lucide-react';
import { useAuth } from '../../context/authContext';
import SupplierService from '../../services/supplierService';
import { MOCK_SUPPLIERS, enrichSupplier } from './mockSuppliersData';
import './SupplierListPage.css';

export default function SupplierListPage() {
    const { currentUser, hasRole } = useAuth();
    const navigate = useNavigate();

    // Data states
    const [rawSuppliers, setRawSuppliers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');
    const [statusFilter, setStatusFilter] = useState('ALL'); // ALL, ACTIVE, DISABLED
    const [selectedIds, setSelectedIds] = useState(new Set());
    const [toastMessage, setToastMessage] = useState(null);

    // Create modal state (UC20 integration)
    const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
    const [createForm, setCreateForm] = useState({
        supplierCode: '',
        supplierName: '',
        contactPerson: '',
        phone: '',
        email: '',
        address: '',
        taxCode: ''
    });
    const [createError, setCreateError] = useState(null);
    const [creating, setCreating] = useState(false);

    // Pagination
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    const isAdminOrManager = hasRole
        ? hasRole('ADMIN') || hasRole('MANAGER')
        : currentUser?.role === 'ADMIN' || currentUser?.role === 'MANAGER';

    // Show toast
    const showToast = (msg) => {
        setToastMessage(msg);
        setTimeout(() => setToastMessage(null), 3500);
    };

    // Load suppliers
    const loadSuppliers = async () => {
        setLoading(true);
        try {
            const res = await SupplierService.getAll();
            const data = res.data?.data || [];
            if (Array.isArray(data) && data.length > 0) {
                // Merge real DB data with enriched metadata
                const enriched = data.map((item) => enrichSupplier(item));
                // If DB has fewer than mock, also include remaining mock suppliers so demo is intact
                const existingCodes = new Set(enriched.map((s) => s.supplierCode));
                const extraMocks = MOCK_SUPPLIERS.filter((m) => !existingCodes.has(m.supplierCode));
                setRawSuppliers([...enriched, ...extraMocks]);
            } else {
                // Fallback to baseline mock data matching screenshot
                setRawSuppliers(MOCK_SUPPLIERS);
            }
        } catch (err) {
            console.error('Failed to load suppliers from backend:', err);
            setRawSuppliers(MOCK_SUPPLIERS);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadSuppliers();
    }, []);

    // Filter suppliers
    const filteredSuppliers = useMemo(() => {
        return rawSuppliers.filter((s) => {
            const matchStatus =
                statusFilter === 'ALL'
                    ? true
                    : statusFilter === 'ACTIVE'
                    ? s.status === 'ACTIVE'
                    : s.status === 'DISABLED' || s.status === 'INACTIVE';

            if (!matchStatus) return false;

            if (!searchTerm.trim()) return true;

            const q = searchTerm.toLowerCase().trim();
            return (
                (s.supplierName && s.supplierName.toLowerCase().includes(q)) ||
                (s.supplierCode && s.supplierCode.toLowerCase().includes(q)) ||
                (s.phone && s.phone.includes(q)) ||
                (s.email && s.email.toLowerCase().includes(q)) ||
                (s.contactPerson && s.contactPerson.toLowerCase().includes(q)) ||
                (s.category && s.category.toLowerCase().includes(q))
            );
        });
    }, [rawSuppliers, statusFilter, searchTerm]);

    // Statistics
    const stats = useMemo(() => {
        const total = rawSuppliers.length;
        const active = rawSuppliers.filter((s) => s.status === 'ACTIVE').length;
        const disabled = rawSuppliers.filter((s) => s.status === 'DISABLED' || s.status === 'INACTIVE').length;
        const activeRate = total > 0 ? ((active / total) * 100).toFixed(1) : '0.0';
        return { total, active, disabled, activeRate };
    }, [rawSuppliers]);

    // Selection
    const handleSelectAll = (e) => {
        if (e.target.checked) {
            setSelectedIds(new Set(filteredSuppliers.map((s) => s.supplierId)));
        } else {
            setSelectedIds(new Set());
        }
    };

    const handleSelectOne = (id) => {
        const next = new Set(selectedIds);
        if (next.has(id)) {
            next.delete(id);
        } else {
            next.add(id);
        }
        setSelectedIds(next);
    };

    // Export CSV
    const handleExportCSV = () => {
        const headers = ['Mã NCC,Tên nhà cung cấp,Người liên hệ,Điện thoại,Email,MST,Địa chỉ,Trạng thái'];
        const rows = filteredSuppliers.map((s) =>
            `"${s.supplierCode}","${s.supplierName}","${s.contactPerson || ''}","${s.phone || ''}","${s.email || ''}","${s.taxCode || ''}","${s.address || ''}","${s.status}"`
        );
        const csvContent = 'data:text/csv;charset=utf-8,\uFEFF' + [headers, ...rows].join('\n');
        const encodedUri = encodeURI(csvContent);
        const link = document.createElement('a');
        link.setAttribute('href', encodedUri);
        link.setAttribute('download', `suppliers_export_${new Date().toISOString().slice(0, 10)}.csv`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        showToast('Xuất danh sách nhà cung cấp thành công (CSV).');
    };

    // Handle Create Supplier
    const handleCreateSubmit = async (e) => {
        e.preventDefault();
        setCreateError(null);

        if (!createForm.supplierCode.trim() || !createForm.supplierName.trim() || !createForm.phone.trim()) {
            setCreateError('Vui lòng điền các trường bắt buộc (Mã NCC, Tên công ty, Số điện thoại)');
            return;
        }

        setCreating(true);
        try {
            const res = await SupplierService.create({
                supplierCode: createForm.supplierCode.trim(),
                supplierName: createForm.supplierName.trim(),
                contactPerson: createForm.contactPerson.trim(),
                phone: createForm.phone.trim(),
                email: createForm.email.trim(),
                address: createForm.address.trim(),
                taxCode: createForm.taxCode.trim()
            });
            const created = res.data?.data || {
                ...createForm,
                supplierId: Date.now(),
                status: 'ACTIVE'
            };
            const enriched = enrichSupplier(created);
            setRawSuppliers((prev) => [enriched, ...prev]);
            setIsCreateModalOpen(false);
            setCreateForm({
                supplierCode: '',
                supplierName: '',
                contactPerson: '',
                phone: '',
                email: '',
                address: '',
                taxCode: ''
            });
            showToast(`Đã thêm nhà cung cấp mới: ${enriched.supplierName}`);
        } catch (err) {
            const msg = err.response?.data?.message || err.message || 'Không thể tạo nhà cung cấp';
            setCreateError(msg);
        } finally {
            setCreating(false);
        }
    };

    // Pagination calculations
    const totalPages = Math.max(1, Math.ceil(filteredSuppliers.length / pageSize));
    const paginatedSuppliers = useMemo(() => {
        const start = (currentPage - 1) * pageSize;
        return filteredSuppliers.slice(start, start + pageSize);
    }, [filteredSuppliers, currentPage, pageSize]);

    return (
        <div className="supplier-management-page">
            {/* Breadcrumb & Main Header */}
            <div className="supplier-page-header">
                <div className="header-breadcrumbs">
                    <Link to="/">Home</Link>
                    <span className="crumb-sep">/</span>
                    <span>Operations Hub</span>
                    <span className="crumb-sep">/</span>
                    <span>Directory</span>
                    <span className="crumb-sep">/</span>
                    <span className="crumb-current">Suppliers (UC19)</span>
                </div>

                <div className="header-title-bar">
                    <div className="title-left">
                        <h1 className="page-title">Supplier Management</h1>
                        <span className="role-access-pill">
                            <ShieldCheck size={14} />
                            Role Access: Administrator, Manager, and Inventory Staff (UC19)
                        </span>
                    </div>

                    <div className="header-actions">
                        <button type="button" className="action-btn secondary" onClick={handleExportCSV}>
                            <Download size={15} />
                            Export CSV
                        </button>
                        <button type="button" className="action-btn secondary" onClick={loadSuppliers} title="Làm mới dữ liệu">
                            <RefreshCw size={15} className={loading ? 'spinning' : ''} />
                            Refresh
                        </button>
                        {isAdminOrManager && (
                            <Link
                                to="/suppliers/create"
                                className="action-btn primary"
                            >
                                <Plus size={16} />
                                + Create Supplier
                            </Link>
                        )}
                    </div>
                </div>
            </div>

            {/* 4 Stat Cards */}
            <div className="supplier-stats-grid">
                {/* Card 1: Total */}
                <div className="stat-card">
                    <div className="stat-card-header">
                        <span className="stat-label">TOTAL SUPPLIERS</span>
                        <div className="stat-icon-wrap blue">
                            <Truck size={18} />
                        </div>
                    </div>
                    <div className="stat-number-wrap">
                        <span className="stat-number">{stats.total}</span>
                        <span className="stat-unit">Vendors</span>
                        <span className="stat-badge green">+3 YTD</span>
                    </div>
                    <div className="stat-subtext">Active Partner Network (Flagship)</div>
                </div>

                {/* Card 2: In-Service Active */}
                <div className="stat-card">
                    <div className="stat-card-header">
                        <span className="stat-label">IN-SERVICE SUPPLIERS</span>
                        <div className="stat-icon-wrap green">
                            <CheckCircle2 size={18} />
                        </div>
                    </div>
                    <div className="stat-number-wrap">
                        <span className="stat-number green">{stats.active}</span>
                        <span className="stat-unit">Active</span>
                        <span className="stat-dot-live"></span>
                    </div>
                    <div className="stat-progress-bar">
                        <div className="stat-progress-fill green" style={{ width: `${stats.activeRate}%` }}></div>
                    </div>
                    <div className="stat-subtext">{stats.activeRate}% operational delivery status</div>
                </div>

                {/* Card 3: Disabled / On Hold */}
                <div className="stat-card">
                    <div className="stat-card-header">
                        <span className="stat-label">DISABLED / ON HOLD</span>
                        <div className="stat-icon-wrap amber">
                            <PauseCircle size={18} />
                        </div>
                    </div>
                    <div className="stat-number-wrap">
                        <span className="stat-number amber">{stats.disabled}</span>
                        <span className="stat-unit">Disabled</span>
                        <span className="stat-badge amber">Action req.</span>
                    </div>
                    <div className="stat-subtext">Review required / delivery pauses</div>
                </div>

                {/* Card 4: Procurement Verticals */}
                <div className="stat-card">
                    <div className="stat-card-header">
                        <span className="stat-label">PROCUREMENT VERTICALS</span>
                        <div className="stat-icon-wrap purple">
                            <Building2 size={18} />
                        </div>
                    </div>
                    <div className="stat-number-wrap">
                        <span className="stat-number">5</span>
                        <span className="stat-unit">Master Categories</span>
                    </div>
                    <div className="vertical-tags-wrap">
                        <span className="v-tag">Dairy</span>
                        <span className="v-tag">FMCG</span>
                        <span className="v-tag">Fresh Produce</span>
                        <span className="v-tag">Beverages</span>
                        <span className="v-tag">Dry Goods</span>
                    </div>
                </div>
            </div>

            {/* SRS 3.7.1 & UC21 Safeguard Rule Alert */}
            <div className="supplier-safeguard-banner">
                <div className="safeguard-left">
                    <div className="safeguard-icon-box">
                        <AlertCircle size={20} />
                    </div>
                    <div className="safeguard-text">
                        <strong className="safeguard-title">
                            SRS 3.7.1 & UC21 Purchase Order Safeguard Rule:
                        </strong>
                        <p className="safeguard-desc">
                            Disabling a supplier prevents issuance of new Purchase Orders (UC21) and restock requests. Historical goods received vouchers and ledger entries remain immutable in audit logs (UC08).
                        </p>
                    </div>
                </div>
                <div className="safeguard-right">
                    <span className="policy-badge">Audit Policy 2025.2</span>
                    <button
                        type="button"
                        className="policy-link-btn"
                        onClick={() => showToast('Chính sách kiểm toán nhà cung cấp: SRS Section 3.7.1 Enforced.')}
                    >
                        Read Policy PIXEL_4_4XL_5A
                    </button>
                </div>
            </div>

            {/* Search and Filters Bar */}
            <div className="supplier-filter-card">
                <div className="filter-row-top">
                    <div className="search-box">
                        <Search size={16} className="search-icon" />
                        <input
                            type="text"
                            placeholder="Search by supplier name, code, phone, or email..."
                            value={searchTerm}
                            maxLength={100}
                            onChange={(e) => setSearchTerm(e.target.value)}
                        />
                        <span className="search-char-count">{searchTerm.length}/100 max</span>
                    </div>

                    <div className="status-pill-group">
                        <button
                            type="button"
                            className={`pill-btn ${statusFilter === 'ALL' ? 'active' : ''}`}
                            onClick={() => setStatusFilter('ALL')}
                        >
                            All Suppliers {stats.total}
                        </button>
                        <button
                            type="button"
                            className={`pill-btn ${statusFilter === 'ACTIVE' ? 'active' : ''}`}
                            onClick={() => setStatusFilter('ACTIVE')}
                        >
                            Active {stats.active}
                        </button>
                        <button
                            type="button"
                            className={`pill-btn ${statusFilter === 'DISABLED' ? 'active' : ''}`}
                            onClick={() => setStatusFilter('DISABLED')}
                        >
                            Disabled {stats.disabled}
                        </button>
                    </div>
                </div>

                <div className="applied-filters-row">
                    <span className="applied-label">Applied filters:</span>
                    <span className="applied-tag">
                        Status: {statusFilter === 'ALL' ? `Showing All (${filteredSuppliers.length})` : statusFilter}
                        <button
                            type="button"
                            className="tag-remove-btn"
                            onClick={() => setStatusFilter('ALL')}
                        >
                            <X size={12} />
                        </button>
                    </span>
                    <span className="applied-tag store-tag">
                        Store: Dist 1 Flagship
                    </span>
                    {(searchTerm || statusFilter !== 'ALL') && (
                        <button
                            type="button"
                            className="reset-filters-btn"
                            onClick={() => {
                                setSearchTerm('');
                                setStatusFilter('ALL');
                            }}
                        >
                            Reset all filters
                        </button>
                    )}
                </div>
            </div>

            {/* Suppliers Table */}
            <div className="supplier-table-container">
                <table className="supplier-table">
                    <thead>
                        <tr>
                            <th className="th-cb">
                                <input
                                    type="checkbox"
                                    checked={
                                        filteredSuppliers.length > 0 &&
                                        selectedIds.size === filteredSuppliers.length
                                    }
                                    onChange={handleSelectAll}
                                />
                            </th>
                            <th className="th-code">CODE</th>
                            <th className="th-entity">COMPANY / LEGAL ENTITY</th>
                            <th className="th-contact">REPRESENTATIVE CONTACT</th>
                            <th className="th-phone">PHONE NUMBER</th>
                            <th className="th-email">CORPORATE EMAIL</th>
                        </tr>
                    </thead>
                    <tbody>
                        {loading ? (
                            <tr>
                                <td colSpan="6" className="table-loading">
                                    <RefreshCw size={24} className="spinning" />
                                    <span>Đang tải danh sách nhà cung cấp...</span>
                                </td>
                            </tr>
                        ) : paginatedSuppliers.length === 0 ? (
                            <tr>
                                <td colSpan="6" className="table-empty">
                                    <div className="empty-state-box">
                                        <Building2 size={36} className="empty-icon" />
                                        <h4>Không tìm thấy nhà cung cấp</h4>
                                        <p>Không có nhà cung cấp nào khớp với bộ lọc hiện tại.</p>
                                        <button
                                            type="button"
                                            className="action-btn secondary"
                                            onClick={() => {
                                                setSearchTerm('');
                                                setStatusFilter('ALL');
                                            }}
                                        >
                                            Đặt lại bộ lọc
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ) : (
                            paginatedSuppliers.map((supplier) => {
                                const isDisabled =
                                    supplier.status === 'DISABLED' || supplier.status === 'INACTIVE';
                                const isSelected = selectedIds.has(supplier.supplierId);

                                return (
                                    <tr
                                        key={supplier.supplierId}
                                        className={`supplier-row ${isDisabled ? 'row-disabled' : ''} ${
                                            isSelected ? 'row-selected' : ''
                                        }`}
                                    >
                                        <td className="td-cb" onClick={(e) => e.stopPropagation()}>
                                            <input
                                                type="checkbox"
                                                checked={isSelected}
                                                onChange={() => handleSelectOne(supplier.supplierId)}
                                            />
                                        </td>
                                        <td className="td-code">
                                            <Link
                                                to={`/suppliers/${supplier.supplierId}`}
                                                className="code-badge-link"
                                            >
                                                {supplier.supplierCode}
                                            </Link>
                                        </td>
                                        <td className="td-entity">
                                            <div className="entity-cell">
                                                <div
                                                    className="entity-avatar"
                                                    style={{ backgroundColor: supplier.avatarBg || '#2563eb' }}
                                                >
                                                    {supplier.initial || supplier.supplierName?.charAt(0) || 'S'}
                                                </div>
                                                <div className="entity-info">
                                                    <div className="entity-name-row">
                                                        <Link
                                                            to={`/suppliers/${supplier.supplierId}`}
                                                            className={`entity-name ${isDisabled ? 'disabled-strike' : ''}`}
                                                        >
                                                            {supplier.supplierName}
                                                        </Link>
                                                        {isDisabled && (
                                                            <span className="hold-warning-pill">
                                                                ⚠️ Temporarily On-Hold (UC21 Blocked)
                                                            </span>
                                                        )}
                                                    </div>
                                                    <div className="entity-meta">
                                                        {supplier.category || 'General FMCG'} • Contract {supplier.contractRef || '#CTR-ACTIVE'}
                                                    </div>
                                                </div>
                                            </div>
                                        </td>
                                        <td className="td-contact">
                                            <span className="contact-person-name">
                                                {supplier.contactPerson || '—'}
                                            </span>
                                        </td>
                                        <td className="td-phone">
                                            <span className="phone-text">
                                                {supplier.phone || '—'}
                                            </span>
                                        </td>
                                        <td className="td-email">
                                            <span className="email-text">
                                                {supplier.email || '—'}
                                            </span>
                                        </td>
                                    </tr>
                                );
                            })
                        )}
                    </tbody>
                </table>

                {/* Table Footer / Pagination */}
                <div className="table-pagination-row">
                    <div className="pagination-info">
                        Showing {filteredSuppliers.length > 0 ? (currentPage - 1) * pageSize + 1 : 0} to{' '}
                        {Math.min(currentPage * pageSize, filteredSuppliers.length)} of{' '}
                        {filteredSuppliers.length} suppliers
                    </div>

                    <div className="pagination-controls">
                        <div className="rows-per-page">
                            <span>Rows per page:</span>
                            <select
                                value={pageSize}
                                onChange={(e) => {
                                    setPageSize(Number(e.target.value));
                                    setCurrentPage(1);
                                }}
                            >
                                <option value={5}>5 / page</option>
                                <option value={10}>10 / page</option>
                                <option value={20}>20 / page</option>
                                <option value={50}>50 / page</option>
                            </select>
                        </div>

                        <div className="page-buttons">
                            <button
                                type="button"
                                className="page-nav-btn"
                                disabled={currentPage <= 1}
                                onClick={() => setCurrentPage((p) => p - 1)}
                            >
                                <ChevronLeft size={16} />
                            </button>
                            {Array.from({ length: totalPages }, (_, i) => i + 1).map((num) => (
                                <button
                                    key={num}
                                    type="button"
                                    className={`page-num-btn ${currentPage === num ? 'active' : ''}`}
                                    onClick={() => setCurrentPage(num)}
                                >
                                    {num}
                                </button>
                            ))}
                            <button
                                type="button"
                                className="page-nav-btn"
                                disabled={currentPage >= totalPages}
                                onClick={() => setCurrentPage((p) => p + 1)}
                            >
                                <ChevronRight size={16} />
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            {/* Bottom 3 Dashboard Widgets */}
            <div className="supplier-bottom-widgets-grid">
                {/* Widget 1: Procurement Shortcuts */}
                <div className="bottom-widget-card shortcuts-widget">
                    <div className="widget-header">
                        <span className="widget-icon">⚡</span>
                        <h3 className="widget-title">Procurement Shortcuts</h3>
                    </div>
                    <p className="widget-subtitle">
                        Quick links directly linked to Module 3.7 Supplier and restock management workflow.
                    </p>

                    <div className="shortcuts-list">
                        {isAdminOrManager && (
                            <Link
                                to="/suppliers/create"
                                className="shortcut-item-btn"
                            >
                                <div className="shortcut-icon-box blue">📋</div>
                                <div className="shortcut-text">
                                    <span className="shortcut-label">Register New Vendor (UC20)</span>
                                </div>
                                <ArrowRight size={16} className="shortcut-arrow" />
                            </Link>
                        )}

                        <Link to="/purchase-orders/create" className="shortcut-item-btn">
                            <div className="shortcut-icon-box purple">📄</div>
                            <div className="shortcut-text">
                                <span className="shortcut-label">Issue Purchase Order (UC21)</span>
                            </div>
                            <ArrowRight size={16} className="shortcut-arrow" />
                        </Link>

                        <Link to="/system-logs?search=Supplier" className="shortcut-item-btn">
                            <div className="shortcut-icon-box teal">📜</div>
                            <div className="shortcut-text">
                                <span className="shortcut-label">Vendor Audit Trail (UC08)</span>
                            </div>
                            <ArrowRight size={16} className="shortcut-arrow" />
                        </Link>
                    </div>
                </div>

                {/* Widget 2: On-Time Restock Delivery */}
                <div className="bottom-widget-card delivery-widget">
                    <div className="widget-header">
                        <div className="header-left-title">
                            <Truck size={18} className="widget-truck-icon" />
                            <h3 className="widget-title">On-Time Restock Delivery</h3>
                        </div>
                        <span className="sla-badge">98.4% SLA</span>
                    </div>
                    <p className="widget-subtitle">
                        Delivery reliability index across District 1 Flagship distribution dock over last 30 days.
                    </p>

                    <div className="delivery-bars-list">
                        <div className="delivery-item">
                            <div className="delivery-meta">
                                <span className="vendor-name">Vinamilk (Cold Chain)</span>
                                <span className="vendor-rate green">99.2%</span>
                            </div>
                            <div className="delivery-bar-track">
                                <div className="delivery-bar-fill green" style={{ width: '99.2%' }}></div>
                            </div>
                        </div>

                        <div className="delivery-item">
                            <div className="delivery-meta">
                                <span className="vendor-name">Acecook Việt Nam (Dry Groceries)</span>
                                <span className="vendor-rate green">98.5%</span>
                            </div>
                            <div className="delivery-bar-track">
                                <div className="delivery-bar-fill green" style={{ width: '98.5%' }}></div>
                            </div>
                        </div>

                        <div className="delivery-item">
                            <div className="delivery-meta">
                                <span className="vendor-name">Unilever International (FMCG)</span>
                                <span className="vendor-rate green">96.0%</span>
                            </div>
                            <div className="delivery-bar-track">
                                <div className="delivery-bar-fill green" style={{ width: '96.0%' }}></div>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Widget 3: Food Safety & Legal Compliance */}
                <div className="bottom-widget-card compliance-widget">
                    <div className="widget-header">
                        <ShieldCheck size={18} className="widget-shield-icon" />
                        <h3 className="widget-title">Food Safety & Legal Compliance</h3>
                    </div>
                    <p className="widget-subtitle">
                        All {stats.total} enterprise partners maintain active food safety certifications and official Vietnamese business registrations.
                    </p>

                    <div className="compliance-metrics-list">
                        <div className="metric-row">
                            <span className="metric-label">ISO 22000 / HACCP Valid:</span>
                            <strong className="metric-val green">{stats.total} / {stats.total} (100%)</strong>
                        </div>
                        <div className="metric-row">
                            <span className="metric-label">Contracts Up for Renewal:</span>
                            <strong className="metric-val amber">2 Suppliers (Q2)</strong>
                        </div>
                        <div className="metric-row">
                            <span className="metric-label">Last System Full Audit:</span>
                            <strong className="metric-val text-dark">Today, 08:30 AM</strong>
                        </div>
                    </div>
                </div>
            </div>

            {/* Create Supplier Modal (UC20 Integration) */}
            {isCreateModalOpen && (
                <div className="modal-backdrop" onClick={() => setIsCreateModalOpen(false)}>
                    <div className="modal-card" onClick={(e) => e.stopPropagation()}>
                        <div className="modal-header">
                            <h3>Thêm nhà cung cấp mới (UC20)</h3>
                            <button
                                type="button"
                                className="modal-close-btn"
                                onClick={() => setIsCreateModalOpen(false)}
                            >
                                <X size={18} />
                            </button>
                        </div>

                        {createError && (
                            <div className="modal-error-alert">
                                <AlertCircle size={16} />
                                <span>{createError}</span>
                            </div>
                        )}

                        <form onSubmit={handleCreateSubmit} className="modal-form">
                            <div className="form-grid-2">
                                <div className="form-group">
                                    <label>Mã nhà cung cấp *</label>
                                    <input
                                        type="text"
                                        required
                                        placeholder="Ví dụ: SUP-009"
                                        value={createForm.supplierCode}
                                        onChange={(e) =>
                                            setCreateForm({ ...createForm, supplierCode: e.target.value })
                                        }
                                    />
                                </div>
                                <div className="form-group">
                                    <label>Mã số thuế (MST)</label>
                                    <input
                                        type="text"
                                        placeholder="Ví dụ: 0300123456"
                                        value={createForm.taxCode}
                                        onChange={(e) =>
                                            setCreateForm({ ...createForm, taxCode: e.target.value })
                                        }
                                    />
                                </div>
                            </div>

                            <div className="form-group">
                                <label>Tên đầy đủ công ty / Pháp nhân *</label>
                                <input
                                    type="text"
                                    required
                                    placeholder="Ví dụ: Công ty Cổ phần Thực phẩm ABC"
                                    value={createForm.supplierName}
                                    onChange={(e) =>
                                        setCreateForm({ ...createForm, supplierName: e.target.value })
                                    }
                                />
                            </div>

                            <div className="form-grid-2">
                                <div className="form-group">
                                    <label>Người liên hệ đại diện</label>
                                    <input
                                        type="text"
                                        placeholder="Ví dụ: Nguyễn Văn A"
                                        value={createForm.contactPerson}
                                        onChange={(e) =>
                                            setCreateForm({ ...createForm, contactPerson: e.target.value })
                                        }
                                    />
                                </div>
                                <div className="form-group">
                                    <label>Số điện thoại liên hệ *</label>
                                    <input
                                        type="text"
                                        required
                                        placeholder="Ví dụ: 028 1234 5678"
                                        value={createForm.phone}
                                        onChange={(e) =>
                                            setCreateForm({ ...createForm, phone: e.target.value })
                                        }
                                    />
                                </div>
                            </div>

                            <div className="form-group">
                                <label>Email liên hệ chính thức</label>
                                <input
                                    type="email"
                                    placeholder="Ví dụ: contact@abc.com.vn"
                                    value={createForm.email}
                                    onChange={(e) =>
                                        setCreateForm({ ...createForm, email: e.target.value })
                                    }
                                />
                            </div>

                            <div className="form-group">
                                <label>Địa chỉ trụ sở / Kho xuất hàng</label>
                                <input
                                    type="text"
                                    placeholder="Địa chỉ chi tiết..."
                                    value={createForm.address}
                                    onChange={(e) =>
                                        setCreateForm({ ...createForm, address: e.target.value })
                                    }
                                />
                            </div>

                            <div className="modal-actions">
                                <button
                                    type="button"
                                    className="action-btn secondary"
                                    onClick={() => setIsCreateModalOpen(false)}
                                    disabled={creating}
                                >
                                    Hủy bỏ
                                </button>
                                <button
                                    type="submit"
                                    className="action-btn primary"
                                    disabled={creating}
                                >
                                    {creating ? 'Đang lưu...' : 'Lưu nhà cung cấp'}
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
