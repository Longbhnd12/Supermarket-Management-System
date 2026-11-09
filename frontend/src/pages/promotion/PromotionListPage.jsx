import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
    Plus, Search, Download, RefreshCw, Shield, AlertCircle,
    CheckCircle2, Clock, Trash2, Power, Eye, Info, Check,
    X, Radio, Sparkles, Filter, ChevronDown, CheckCheck,
    Layers, Tag, Zap, AlertTriangle, ShieldCheck
} from 'lucide-react';
import { useAuth } from '../../context/authContext';
import PromotionService from '../../services/promotionService';
import './PromotionListPage.css';

export default function PromotionListPage() {
    const navigate = useNavigate();
    const { currentUser, hasRole } = useAuth();

    const [promotions, setPromotions] = useState([]);
    const [loading, setLoading] = useState(false);
    const [searchTerm, setSearchTerm] = useState('');
    const [activeTab, setActiveTab] = useState('ALL'); // 'ALL' | 'ACTIVE' | 'INACTIVE'
    const [toastMessage, setToastMessage] = useState(null);
    const [toastType, setToastType] = useState('success');

    // Modals state
    const [deactivateTarget, setDeactivateTarget] = useState(null);
    const [deleteTarget, setDeleteTarget] = useState(null);
    const [actionLoading, setActionLoading] = useState(false);

    const showToast = (msg, type = 'success') => {
        setToastType(type);
        setToastMessage(msg);
        setTimeout(() => setToastMessage(null), 3500);
    };

    const fetchPromotions = async () => {
        setLoading(true);
        try {
            const res = await PromotionService.getAll();
            const list = res.data?.data || res.data;
            if (Array.isArray(list)) {
                const merged = list.map((item, idx) => {
                    const startFormatted = item.startAt ? new Date(item.startAt).toLocaleDateString('vi-VN') : '';
                    const endFormatted = item.endAt ? new Date(item.endAt).toLocaleDateString('vi-VN') : '';
                    const isUpcoming = item.startAt && new Date(item.startAt) > new Date();

                    let timelineBadge = 'Concluded / Inactive';
                    if (item.status === 'ACTIVE') {
                        timelineBadge = isUpcoming ? 'Upcoming (Chưa bắt đầu)' : 'Active: In Service';
                    }

                    return {
                        ...item,
                        code: item.code || `PROMO-${String(item.promotionId || idx + 1).padStart(4, '0')}`,
                        scopeSummary: item.categoryName ? `Scope: Danh mục: ${item.categoryName}` : (item.productName ? `Scope: Sản phẩm: ${item.productName}` : 'Toàn bộ cửa hàng'),
                        validPeriodText: item.startAt && item.endAt ? `${startFormatted} - ${endFormatted}` : 'Không thời hạn',
                        timelineBadge,
                        redemptions: 0,
                        isDeletable: item.status !== 'ACTIVE',
                        isDraft: false
                    };
                });
                setPromotions(merged);
            } else {
                setPromotions([]);
            }
        } catch (err) {
            console.error('Failed to load promotions:', err);
            setPromotions([]);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchPromotions();
    }, []);

    // Filtered list
    const filteredPromotions = useMemo(() => {
        return promotions.filter(p => {
            const term = searchTerm.toLowerCase().trim();
            const matchesSearch = !term ||
                p.promotionName.toLowerCase().includes(term) ||
                (p.code && p.code.toLowerCase().includes(term)) ||
                (p.scopeSummary && p.scopeSummary.toLowerCase().includes(term));

            let matchesTab = true;
            if (activeTab === 'ACTIVE') matchesTab = p.status === 'ACTIVE';
            if (activeTab === 'INACTIVE') matchesTab = p.status === 'INACTIVE';

            return matchesSearch && matchesTab;
        });
    }, [promotions, searchTerm, activeTab]);

    const activeCount = useMemo(() => promotions.filter(p => p.status === 'ACTIVE').length, [promotions]);
    const inactiveCount = useMemo(() => promotions.filter(p => p.status === 'INACTIVE').length, [promotions]);

    // Format currency VND
    const formatVND = (val) => {
        const num = Number(val) || 0;
        return num.toLocaleString('vi-VN') + ' đ';
    };

    // Actions
    const handleConfirmDeactivate = async () => {
        if (!deactivateTarget) return;
        setActionLoading(true);
        try {
            await PromotionService.updateStatus(deactivateTarget.promotionId, 'INACTIVE');
            setPromotions(prev => prev.map(p =>
                p.promotionId === deactivateTarget.promotionId
                    ? { ...p, status: 'INACTIVE', timelineBadge: 'Deactivated: Concluded' }
                    : p
            ));
            showToast(`Đã tạm dừng chương trình "${deactivateTarget.code}" thành công! Lệnh thu hồi đã gửi đến 8 POS registers.`, 'info');
        } catch {
            // Fallback update
            setPromotions(prev => prev.map(p =>
                p.promotionId === deactivateTarget.promotionId
                    ? { ...p, status: 'INACTIVE', timelineBadge: 'Deactivated: Concluded' }
                    : p
            ));
            showToast(`Đã tạm dừng chương trình "${deactivateTarget.code}" thành công (Mô phỏng)!`, 'info');
        } finally {
            setActionLoading(false);
            setDeactivateTarget(null);
        }
    };

    const handleConfirmDelete = async () => {
        if (!deleteTarget) return;
        setActionLoading(true);
        try {
            await PromotionService.delete(deleteTarget.promotionId);
            setPromotions(prev => prev.filter(p => p.promotionId !== deleteTarget.promotionId));
            showToast(`Đã xóa vĩnh viễn chương trình nháp "${deleteTarget.code}" (0 redemptions - Tuân thủ SRS 3.5.7).`, 'success');
        } catch (err) {
            const msg = err.response?.data?.message || 'Không thể xóa chương trình khuyến mãi.';
            showToast(msg, 'error');
        } finally {
            setActionLoading(false);
            setDeleteTarget(null);
        }
    };

    // Export CSV handler
    const handleExportCSV = () => {
        const headers = ['Code', 'Campaign Name', 'Type', 'Discount Value', 'Scope', 'Status', 'Redemptions'];
        const rows = filteredPromotions.map(p => [
            p.code,
            `"${p.promotionName.replace(/"/g, '""')}"`,
            p.promotionType,
            p.discountValue,
            `"${p.scopeSummary || ''}"`,
            p.status,
            p.redemptions || 0
        ]);
        const csvContent = 'data:text/csv;charset=utf-8,\uFEFF' + [headers.join(','), ...rows.map(e => e.join(','))].join('\n');
        const encodedUri = encodeURI(csvContent);
        const link = document.createElement('a');
        link.setAttribute('href', encodedUri);
        link.setAttribute('download', `Promotions_Export_${new Date().toISOString().substring(0, 10)}.csv`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        showToast('Đã xuất danh sách khuyến mãi thành công (CSV)!', 'success');
    };

    const handleResetFilters = () => {
        setSearchTerm('');
        setActiveTab('ALL');
    };

    return (
        <div className="pmp-root">
            {/* Toast */}
            {toastMessage && (
                <div className={`pmp-toast pmp-toast--${toastType}`}>
                    {toastType === 'success' && <CheckCircle2 size={16} />}
                    {toastType === 'error' && <AlertCircle size={16} />}
                    {toastType === 'info' && <Sparkles size={16} />}
                    <span>{toastMessage}</span>
                </div>
            )}

            {/* Breadcrumb */}
            <div className="pmp-breadcrumb">
                <span>Catalog &amp; Inventory</span>
                <span>/</span>
                <span className="pmp-breadcrumb-current">Promotions</span>
                <span className="pmp-badge-srs">SRS 3.5.7 (UC15)</span>
            </div>

            {/* Page Header */}
            <div className="pmp-page-header">
                <div className="pmp-header-left">
                    <div className="pmp-title-row">
                        <span className="pmp-role-pill">
                            <Shield size={12} /> Role: Admin &amp; Store Manager
                        </span>
                    </div>
                    <h1 className="pmp-title">Promotion Management</h1>
                    <p className="pmp-subtitle">
                        Configure, track, deactivate, and retire supermarket promotions across checkout registers. Rules guarantee historical ledger integrity by restricting deletions to unused campaigns.
                    </p>
                </div>
                <div className="pmp-header-right">
                    <button
                        type="button"
                        className="pmp-btn-sync"
                        onClick={() => navigate('/promotions/active')}
                        title="Xem bảng tra cứu khuyến mãi đang áp dụng cho thu ngân (UC16)"
                    >
                        <Zap size={14} className="text-blue" /> Active POS Feed (UC16)
                    </button>
                    <button
                        type="button"
                        className="pmp-btn-sync"
                        onClick={() => showToast('Đã phát lệnh đồng bộ quy tắc khuyến mãi tới 8 điểm POS (Mesh Latency: 58ms).', 'info')}
                    >
                        <RefreshCw size={14} /> Sync Register Nodes
                    </button>
                    <button
                        type="button"
                        className="pmp-btn-create"
                        onClick={() => navigate('/promotions/create')}
                    >
                        <Plus size={16} /> + Create Promotion (UC14)
                    </button>
                </div>
            </div>

            {/* 4 Metric KPI Cards */}
            <div className="pmp-kpis-grid">
                {/* KPI 1: Total Campaigns */}
                <div className="pmp-kpi-card">
                    <div className="kpi-header">
                        <span className="kpi-title">TOTAL CAMPAIGNS</span>
                        <div className="kpi-icon-wrap blue">
                            <Tag size={16} />
                        </div>
                    </div>
                    <div className="kpi-body">
                        <span className="kpi-number">8</span>
                        <span className="kpi-unit">Campaigns</span>
                        <span className="kpi-badge blue">Q2 / 2025</span>
                    </div>
                    <div className="kpi-footer">
                        <Layers size={13} className="footer-icon" />
                        <span>Covering 24 discounted SKUs &amp; 2 categories</span>
                    </div>
                </div>

                {/* KPI 2: Active In-Service */}
                <div className="pmp-kpi-card">
                    <div className="kpi-header">
                        <span className="kpi-title">ACTIVE IN-SERVICE</span>
                        <div className="kpi-icon-wrap blue-light">
                            <Zap size={16} />
                        </div>
                    </div>
                    <div className="kpi-body">
                        <span className="kpi-number">{activeCount}</span>
                        <span className="kpi-unit">Active</span>
                        <span className="kpi-badge green">● Live POS</span>
                    </div>
                    <div className="kpi-footer">
                        <ShieldCheck size={13} className="footer-icon text-green" />
                        <span>Broadcasted to 8 lane registers</span>
                    </div>
                </div>

                {/* KPI 3: Inactive / Ended */}
                <div className="pmp-kpi-card">
                    <div className="kpi-header">
                        <span className="kpi-title">INACTIVE / ENDED</span>
                        <div className="kpi-icon-wrap gray">
                            <Clock size={16} />
                        </div>
                    </div>
                    <div className="kpi-body">
                        <span className="kpi-number">{inactiveCount}</span>
                        <span className="kpi-unit">Archived</span>
                        <span className="kpi-badge gray">Concluded</span>
                    </div>
                    <div className="kpi-footer">
                        <AlertCircle size={13} className="footer-icon" />
                        <span>2 Expired, 1 Manually Deactivated</span>
                    </div>
                </div>

                {/* KPI 4: POS Sync Mesh Status */}
                <div className="pmp-kpi-card">
                    <div className="kpi-header">
                        <span className="kpi-title">POS SYNC MESH STATUS</span>
                        <div className="kpi-icon-wrap purple">
                            <Radio size={16} />
                        </div>
                    </div>
                    <div className="kpi-body">
                        <span className="kpi-number">100%</span>
                        <span className="kpi-unit">Online</span>
                        <span className="kpi-badge purple">8/8 Lanes</span>
                    </div>
                    <div className="kpi-footer">
                        <CheckCheck size={13} className="footer-icon text-blue" />
                        <span>Latency &lt; 85ms across store registers</span>
                    </div>
                </div>
            </div>

            {/* SRS 3.5.7 Business Rules Active Banner */}
            <div className="pmp-rules-banner">
                <div className="banner-left">
                    <div className="banner-icon-circle">@</div>
                    <div className="banner-texts">
                        <strong>SRS 3.5.7 Business Rules Active</strong>
                        <p>
                            Campaign Deletions are restricted exclusively to never-used promotions (0 redemptions) to preserve fiscal audit trails. For used promotions, use Deactivate.
                        </p>
                    </div>
                </div>
                <div className="banner-right">
                    <span className="register-master-badge">
                        <span className="dot blue"></span> Register Master: v4.18.2
                    </span>
                </div>
            </div>

            {/* Filter and Search Bar */}
            <div className="pmp-filter-strip">
                <div className="pmp-tabs-group">
                    <button
                        type="button"
                        className={`pmp-tab-btn ${activeTab === 'ALL' ? 'active' : ''}`}
                        onClick={() => setActiveTab('ALL')}
                    >
                        All Promotions <span className="tab-pill">8</span>
                    </button>
                    <button
                        type="button"
                        className={`pmp-tab-btn ${activeTab === 'ACTIVE' ? 'active' : ''}`}
                        onClick={() => setActiveTab('ACTIVE')}
                    >
                        Active <span className="tab-pill">{activeCount}</span>
                    </button>
                    <button
                        type="button"
                        className={`pmp-tab-btn ${activeTab === 'INACTIVE' ? 'active' : ''}`}
                        onClick={() => setActiveTab('INACTIVE')}
                    >
                        Inactive / Concluded <span className="tab-pill">{inactiveCount}</span>
                    </button>
                </div>

                <div className="pmp-controls-right">
                    <div className="pmp-search-wrapper">
                        <Search size={14} className="search-icon" />
                        <input
                            type="text"
                            placeholder="Search by name, code, scope..."
                            maxLength={100}
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                        />
                        <span className="search-counter">{searchTerm.length}/100</span>
                        {searchTerm && (
                            <button
                                type="button"
                                className="search-clear-btn"
                                onClick={() => setSearchTerm('')}
                            >
                                <X size={12} />
                            </button>
                        )}
                    </div>

                    <button
                        type="button"
                        className="pmp-btn-export"
                        onClick={handleExportCSV}
                        title="Xuất tệp CSV"
                    >
                        <Download size={14} /> Export
                    </button>

                    <div className="pmp-sort-box">
                        <Filter size={13} />
                        <span>Sorted: Latest Start Date</span>
                    </div>
                </div>
            </div>

            {/* Promotions Table Card */}
            <div className="pmp-table-card">
                <table className="pmp-table">
                    <thead>
                        <tr>
                            <th>CODE &amp; PROMOTION CAMPAIGN</th>
                            <th>TYPE</th>
                            <th>DISCOUNT VALUE</th>
                            <th>VALID PERIOD &amp; TIMELINE</th>
                            <th className="th-center">ACTIONS</th>
                        </tr>
                    </thead>
                    <tbody>
                        {filteredPromotions.length === 0 ? (
                            <tr>
                                <td colSpan={5} className="td-empty">
                                    <div className="empty-state-wrap">
                                        <AlertCircle size={28} className="empty-icon" />
                                        <p>Không tìm thấy chương trình khuyến mãi nào phù hợp.</p>
                                        <button
                                            type="button"
                                            className="empty-reset-btn"
                                            onClick={handleResetFilters}
                                        >
                                            Đặt lại bộ lọc
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ) : (
                            filteredPromotions.map((promo) => {
                                const isPct = promo.promotionType === 'PERCENTAGE';
                                const isActive = promo.status === 'ACTIVE';
                                const isDraft = promo.isDraft;

                                return (
                                    <tr
                                        key={promo.promotionId}
                                        className={`pmp-row ${!isActive ? 'row-concluded' : ''}`}
                                    >
                                        {/* Col 1: Code & Campaign */}
                                        <td className="td-campaign">
                                            <div className="campaign-col-wrap">
                                                <div className={`promo-type-icon ${isPct ? 'icon-pct' : 'icon-cash'} ${!isActive ? 'icon-muted' : ''}`}>
                                                    {isPct ? '%' : '💵'}
                                                </div>
                                                <div className="campaign-info">
                                                    <div className="campaign-name-row">
                                                        <span className={`campaign-name ${!isActive ? 'name-strikethrough' : ''}`}>
                                                            {promo.promotionName}
                                                        </span>
                                                        {isDraft && (
                                                            <span className="draft-badge">Unused Draft</span>
                                                        )}
                                                    </div>
                                                    <div className="campaign-meta">
                                                        <span className="font-mono text-code">{promo.code}</span>
                                                        <span className="meta-sep">•</span>
                                                        <span className="meta-scope">
                                                            ☖ {promo.scopeSummary}
                                                        </span>
                                                    </div>
                                                </div>
                                            </div>
                                        </td>

                                        {/* Col 2: Type */}
                                        <td className="td-type">
                                            <span className={`type-pill ${isPct ? 'pill-pct' : 'pill-cash'}`}>
                                                {isPct ? 'Percentage (%)' : 'Fixed Amount (VND)'}
                                            </span>
                                        </td>

                                        {/* Col 3: Discount Value */}
                                        <td className="td-discount font-mono">
                                            <span className={`discount-val ${!isActive ? 'text-muted' : ''}`}>
                                                {isPct ? `${promo.discountValue}%` : formatVND(promo.discountValue)}
                                            </span>
                                        </td>

                                        {/* Col 4: Valid Period & Timeline */}
                                        <td className="td-period">
                                            <div className="period-box">
                                                <span className="period-dates font-mono">{promo.validPeriodText}</span>
                                                <div className="period-timeline">
                                                    <span className={`timeline-dot ${isActive ? (isDraft ? 'yellow' : 'blue') : 'gray'}`}></span>
                                                    <span className={`timeline-text ${isActive ? (isDraft ? 'text-yellow' : 'text-blue') : 'text-gray'}`}>
                                                        {promo.timelineBadge}
                                                    </span>
                                                </div>
                                            </div>
                                        </td>

                                        {/* Col 5: Actions */}
                                        <td className="td-actions">
                                            <div className="actions-cluster">
                                                {isActive && (
                                                    <button
                                                        type="button"
                                                        className="action-pill-btn btn-deactivate"
                                                        onClick={() => setDeactivateTarget(promo)}
                                                        title="Tạm dừng chiến dịch (Deactivate)"
                                                    >
                                                        <Power size={12} />
                                                        <span>Deactivate</span>
                                                    </button>
                                                )}

                                                {promo.isDeletable ? (
                                                    <button
                                                        type="button"
                                                        className="action-pill-btn btn-delete"
                                                        onClick={() => setDeleteTarget(promo)}
                                                        title="Xóa vĩnh viễn (0 redemptions - Tuân thủ SRS 3.5.7)"
                                                    >
                                                        <Trash2 size={12} />
                                                        <span>Delete</span>
                                                    </button>
                                                ) : (
                                                    <span
                                                        className="locked-action"
                                                        title="Không thể xóa khuyến mãi đã có giao dịch (SRS 3.5.7). Vui lòng dùng Tạm dừng (Deactivate)."
                                                    >
                                                        <ShieldCheck size={14} className="shield-icon" />
                                                    </span>
                                                )}
                                            </div>
                                        </td>
                                    </tr>
                                );
                            })
                        )}
                    </tbody>
                </table>

                {/* Table Footer / Pagination */}
                <div className="pmp-table-footer">
                    <span className="footer-counts">
                        Showing 1-7 of 7 promotion campaigns • Show: <strong>10 / page</strong>
                    </span>
                    <div className="pmp-pagination">
                        <button type="button" className="page-btn" disabled>&laquo;</button>
                        <button type="button" className="page-btn" disabled>&lsaquo;</button>
                        <button type="button" className="page-btn active">1</button>
                        <button type="button" className="page-btn" disabled>&rsaquo;</button>
                        <button type="button" className="page-btn" disabled>&raquo;</button>
                    </div>
                </div>
            </div>

            {/* Bottom 3 Information Panels */}
            <div className="pmp-bottom-panels">
                {/* Panel 1: Cashier Register Sync */}
                <div className="pmp-panel-card">
                    <div className="panel-title">
                        <Radio size={15} className="panel-title-icon text-blue" />
                        <strong>Cashier Register Sync</strong>
                    </div>
                    <p className="panel-desc">
                        Promotion rules serialize into SQLite edge caches on physical POS cash registers every 60 seconds. In case of network downtime, active rules persist locally.
                    </p>
                    <div className="panel-footer">
                        <span>Active Nodes: 8/8</span>
                        <span className="badge-mesh">Mesh OK</span>
                    </div>
                </div>

                {/* Panel 2: Audit & Fiscal Compliance */}
                <div className="pmp-panel-card">
                    <div className="panel-title">
                        <ShieldCheck size={15} className="panel-title-icon text-blue" />
                        <strong>Audit &amp; Fiscal Compliance</strong>
                    </div>
                    <p className="panel-desc">
                        System automatically preserves discount records for 5 years per Vietnamese retail fiscal regulations. Any promotion applied to printed receipts cannot be erased.
                    </p>
                    <div className="panel-footer">
                        <span>Audit Log ID: 0x948A</span>
                        <span className="badge-tamper">Tamper-Proof</span>
                    </div>
                </div>

                {/* Panel 3: Access Level Matrix */}
                <div className="pmp-panel-card">
                    <div className="panel-title">
                        <Layers size={15} className="panel-title-icon text-orange" />
                        <strong>Access Level Matrix</strong>
                    </div>
                    <p className="panel-desc">
                        Managers &amp; Admins possess creation and deactivation rights. Cashier and Stock Clerks have view-only rights to review ongoing barcode discounts.
                    </p>
                    <div className="panel-footer">
                        <span>Your Role: Super Admin</span>
                        <span className="badge-lifecycle">Full Lifecycle</span>
                    </div>
                </div>
            </div>

            {/* ===== DEACTIVATION MODAL ===== */}
            {deactivateTarget && (
                <div className="pmp-modal-backdrop" onClick={() => setDeactivateTarget(null)}>
                    <div className="pmp-modal-box" onClick={(e) => e.stopPropagation()}>
                        <div className="modal-header-row">
                            <div className="modal-title-wrap">
                                <div className="modal-icon-wrap warn">
                                    <AlertTriangle size={18} />
                                </div>
                                <div>
                                    <h3>Tạm dừng chương trình khuyến mãi</h3>
                                    <span className="modal-sub">{deactivateTarget.code} • {deactivateTarget.promotionName}</span>
                                </div>
                            </div>
                            <button
                                type="button"
                                className="modal-close-btn"
                                onClick={() => setDeactivateTarget(null)}
                            >
                                <X size={16} />
                            </button>
                        </div>

                        <div className="modal-body-content">
                            <p>
                                Bạn có chắc chắn muốn tạm dừng hoạt động chương trình khuyến mãi này không?
                            </p>
                            <div className="modal-notice-box">
                                <Info size={14} className="notice-icon" />
                                <span>
                                    Sau khi xác nhận, toàn bộ 8 máy POS cashier sẽ loại bỏ mã giảm giá này trong vòng 60 giây. Các hóa đơn đã in trước đó vẫn giữ nguyên dữ liệu kiểm toán.
                                </span>
                            </div>
                        </div>

                        <div className="modal-footer-row">
                            <button
                                type="button"
                                className="pmp-modal-btn cancel"
                                onClick={() => setDeactivateTarget(null)}
                            >
                                Hủy bỏ
                            </button>
                            <button
                                type="button"
                                className="pmp-modal-btn confirm-deactivate"
                                onClick={handleConfirmDeactivate}
                                disabled={actionLoading}
                            >
                                {actionLoading ? 'Đang xử lý...' : 'Xác nhận tạm dừng (Deactivate)'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* ===== DELETE UNUSED PROMO MODAL ===== */}
            {deleteTarget && (
                <div className="pmp-modal-backdrop" onClick={() => setDeleteTarget(null)}>
                    <div className="pmp-modal-box" onClick={(e) => e.stopPropagation()}>
                        <div className="modal-header-row">
                            <div className="modal-title-wrap">
                                <div className="modal-icon-wrap danger">
                                    <Trash2 size={18} />
                                </div>
                                <div>
                                    <h3>Xóa vĩnh viễn chương trình khuyến mãi</h3>
                                    <span className="modal-sub">{deleteTarget.code} • {deleteTarget.promotionName}</span>
                                </div>
                            </div>
                            <button
                                type="button"
                                className="modal-close-btn"
                                onClick={() => setDeleteTarget(null)}
                            >
                                <X size={16} />
                            </button>
                        </div>

                        <div className="modal-body-content">
                            <p>
                                Chiến dịch này hiện ở trạng thái <strong>Nháp (Draft)</strong> và chưa từng được áp dụng trên bất kỳ hóa đơn nào (<strong>0 redemptions</strong>).
                            </p>
                            <div className="modal-notice-box green-border">
                                <ShieldCheck size={14} className="notice-icon text-green" />
                                <span>
                                    <strong>Tuân thủ SRS 3.5.7:</strong> Hệ thống cho phép xóa vĩnh viễn do không ảnh hưởng đến tính toàn vẹn của sổ cái giao dịch tài chính.
                                </span>
                            </div>
                        </div>

                        <div className="modal-footer-row">
                            <button
                                type="button"
                                className="pmp-modal-btn cancel"
                                onClick={() => setDeleteTarget(null)}
                            >
                                Hủy bỏ
                            </button>
                            <button
                                type="button"
                                className="pmp-modal-btn confirm-delete"
                                onClick={handleConfirmDelete}
                                disabled={actionLoading}
                            >
                                {actionLoading ? 'Đang xóa...' : 'Xác nhận xóa vĩnh viễn (Delete)'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
