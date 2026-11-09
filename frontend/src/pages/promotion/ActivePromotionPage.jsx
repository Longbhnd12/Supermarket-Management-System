import React, { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import {
    Tag, Search, Printer, RefreshCw, Eye, Shield,
    Clock, CheckCircle2, AlertCircle, Sparkles,
    LayoutGrid, List, Check, X, Radio, Phone,
    Package, FolderTree, Zap, AlertTriangle, Layers,
    HelpCircle, Terminal, Store, CheckCheck, Cpu, Info
} from 'lucide-react';
import { useAuth } from '../../context/authContext';
import PromotionService from '../../services/promotionService';
import './ActivePromotionPage.css';

export default function ActivePromotionPage() {
    const { currentUser } = useAuth();

    const [promotions, setPromotions] = useState([]);
    const [loading, setLoading] = useState(false);
    const [searchTerm, setSearchTerm] = useState('');
    const [scopeFilter, setScopeFilter] = useState('ALL'); // 'ALL' | 'PRODUCTS' | 'CATEGORIES'
    const [viewMode, setViewMode] = useState('cards'); // 'cards' | 'dense'
    const [syncSecondsAgo, setSyncSecondsAgo] = useState(23);
    const [toastMessage, setToastMessage] = useState(null);
    const [toastType, setToastType] = useState('success');

    const showToast = (msg, type = 'success') => {
        setToastType(type);
        setToastMessage(msg);
        setTimeout(() => setToastMessage(null), 3000);
    };

    // Live sync ticker timer
    useEffect(() => {
        const timer = setInterval(() => {
            setSyncSecondsAgo(prev => prev + 1);
        }, 1000);
        return () => clearInterval(timer);
    }, []);

    // Fetch active promotions from backend
    const fetchActivePromotions = async () => {
        setLoading(true);
        try {
            const res = await PromotionService.getActive();
            const list = res.data?.data || res.data;
            if (Array.isArray(list)) {
                const mapped = list.map((item, idx) => {
                    const isPct = item.promotionType === 'PERCENTAGE';
                    let remainingDays = 'Đang diễn ra';
                    if (item.endAt) {
                        const diffMs = new Date(item.endAt) - new Date();
                        const days = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
                        remainingDays = days > 0 ? `Còn ${days} ngày` : 'Sắp hết hạn';
                    }
                    const validUntil = item.endAt
                        ? `${new Date(item.endAt).toLocaleDateString('vi-VN')} • ${new Date(item.endAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}`
                        : '';
                    return {
                        ...item,
                        code: item.code || `PROMO-${String(item.promotionId || idx + 1).padStart(4, '0')}`,
                        shortTitle: item.promotionName,
                        discountBadge: isPct ? `${item.discountValue}% OFF` : `${Number(item.discountValue).toLocaleString('vi-VN')} đ`,
                        scopeType: item.categoryName ? 'CATEGORY' : 'PRODUCTS',
                        scopeLabel: item.categoryName ? 'Áp dụng toàn bộ Danh mục' : 'Sản phẩm cụ thể',
                        scopeSub: item.categoryName ? 'Category Auto-Scan' : 'Direct Barcode Match',
                        items: item.productName ? [{ name: item.productName, sku: `SKU-${item.productId}` }] : [],
                        categoryName: item.categoryName || null,
                        categoryCode: item.categoryId ? `CAT-${item.categoryId}` : '',
                        categoryDesc: item.categoryName ? `Áp dụng cho tất cả sản phẩm thuộc danh mục: ${item.categoryName}` : '',
                        validUntil,
                        remainingDays,
                        badgeRight: 'POS Sync OK'
                    };
                });
                setPromotions(mapped);
            } else {
                setPromotions([]);
            }
        } catch (err) {
            console.error('Failed to load active promotions:', err);
            setPromotions([]);
        } finally {
            setLoading(false);
            setSyncSecondsAgo(0);
            showToast('Đã làm mới dữ liệu nguồn phát sóng POS (Live Feed Sync).', 'success');
        }
    };

    // Filtered promotions
    const filteredPromotions = useMemo(() => {
        return promotions.filter(p => {
            const term = searchTerm.toLowerCase().trim();
            const matchesSearch = !term ||
                p.promotionName.toLowerCase().includes(term) ||
                (p.code && p.code.toLowerCase().includes(term)) ||
                (p.categoryName && p.categoryName.toLowerCase().includes(term)) ||
                (p.items && p.items.some(i => i.name.toLowerCase().includes(term) || i.sku.toLowerCase().includes(term)));

            let matchesScope = true;
            if (scopeFilter === 'PRODUCTS') matchesScope = p.scopeType === 'PRODUCTS';
            if (scopeFilter === 'CATEGORIES') matchesScope = p.scopeType === 'CATEGORY';

            return matchesSearch && matchesScope;
        });
    }, [promotions, searchTerm, scopeFilter]);

    return (
        <div className="app-root">
            {/* Toast Notification */}
            {toastMessage && (
                <div className={`app-toast app-toast--${toastType}`}>
                    {toastType === 'success' && <CheckCircle2 size={16} />}
                    {toastType === 'error' && <AlertTriangle size={16} />}
                    {toastType === 'info' && <Sparkles size={16} />}
                    <span>{toastMessage}</span>
                </div>
            )}

            {/* Breadcrumb */}
            <div className="app-breadcrumb">
                <Link to="/">Home</Link>
                <span>/</span>
                <span>Operations Hub</span>
                <span>/</span>
                <span>Catalog &amp; Inventory</span>
                <span>/</span>
                <Link to="/promotions">Promotions</Link>
                <span>/</span>
                <span className="app-breadcrumb-current">Active Promotions (UC16)</span>
            </div>

            {/* Page Header */}
            <div className="app-page-header">
                <div className="app-header-left">
                    <h1 className="app-title">Active Promotions (Đang áp dụng)</h1>
                    <div className="app-badges-row">
                        <span className="app-pill-blue">SRS 3.5.8 • UC16</span>
                        <span className="app-pill-gray">
                            <Eye size={12} /> Read-Only • Cashier &amp; Staff Reference
                        </span>
                    </div>
                    <p className="app-subtitle">
                        Live reference catalog of currently active retail discounts enforced across Store #04 checkout registers and mobile barcode scanners. Changes must be submitted via Central Marketing Admin.
                    </p>
                </div>
                <div className="app-header-right">
                    <button
                        type="button"
                        className="app-btn-print"
                        onClick={() => window.print()}
                        title="In danh mục khuyến mãi làm tài liệu tham khảo cho thu ngân"
                    >
                        <Printer size={15} /> Print Active Promo Sheet
                    </button>
                    <button
                        type="button"
                        className="app-btn-refresh"
                        onClick={fetchActivePromotions}
                        disabled={loading}
                    >
                        <RefreshCw size={15} className={loading ? 'spin-icon' : ''} />
                        Refresh Live POS Feed
                    </button>
                </div>
            </div>

            {/* 4 Metric KPI Cards */}
            <div className="app-kpis-grid">
                {/* KPI 1 */}
                <div className="app-kpi-card">
                    <div className="kpi-header-row">
                        <span className="kpi-label">TOTAL ACTIVE PROMOTIONS</span>
                        <div className="kpi-icon blue">
                            <Zap size={16} />
                        </div>
                    </div>
                    <div className="kpi-num-row">
                        <span className="kpi-number">5</span>
                        <span className="kpi-subtext">Running campaigns</span>
                    </div>
                    <div className="kpi-footer-note">
                        <span className="dot green"></span>
                        <span>100% active coverage</span>
                    </div>
                </div>

                {/* KPI 2 */}
                <div className="app-kpi-card">
                    <div className="kpi-header-row">
                        <span className="kpi-label">SKUS COVERED</span>
                        <div className="kpi-icon blue-soft">
                            <Package size={16} />
                        </div>
                    </div>
                    <div className="kpi-num-row">
                        <span className="kpi-number">24</span>
                        <span className="kpi-subtext">Discounted SKUs</span>
                    </div>
                    <div className="kpi-footer-note">
                        <Layers size={13} className="text-gray" />
                        <span>Across 2 major brands</span>
                    </div>
                </div>

                {/* KPI 3 */}
                <div className="app-kpi-card">
                    <div className="kpi-header-row">
                        <span className="kpi-label">ACTIVE CATEGORIES</span>
                        <div className="kpi-icon orange">
                            <Store size={16} />
                        </div>
                    </div>
                    <div className="kpi-num-row">
                        <span className="kpi-number">2</span>
                        <span className="kpi-subtext">Departments</span>
                    </div>
                    <div className="kpi-footer-note">
                        <span>Dry Goods &amp; Confectionery</span>
                    </div>
                </div>

                {/* KPI 4 */}
                <div className="app-kpi-card">
                    <div className="kpi-header-row">
                        <span className="kpi-label">POS CASHIER SYNC</span>
                        <div className="kpi-icon purple">
                            <Terminal size={16} />
                        </div>
                    </div>
                    <div className="kpi-num-row">
                        <span className="kpi-number">100%</span>
                        <span className="kpi-subtext">Broadcasted</span>
                    </div>
                    <div className="kpi-footer-note">
                        <CheckCircle2 size={13} className="text-blue" />
                        <span>8/8 POS Lanes Active</span>
                    </div>
                </div>
            </div>

            {/* Reminder Callout Box */}
            <div className="app-reminder-box">
                <div className="reminder-left">
                    <div className="reminder-icon-circle">
                        <Info size={16} />
                    </div>
                    <div className="reminder-texts">
                        <strong>Cashier POS Scanning Reminder &amp; Precedence Rule (UC03 Integration)</strong>
                        <p>
                            All active promotions below automatically trigger upon barcode scanning at cash register terminals. No coupon vouchers required. When multiple discounts apply to a single barcode, the POS terminal system strictly honors the <strong>highest promotional benefit</strong> to the shopper.
                        </p>
                    </div>
                </div>
                <div className="reminder-right">
                    <div className="ticker-badge">
                        <span className="ticker-dot green"></span>
                        <span>Terminal Sync: {syncSecondsAgo}s ago</span>
                    </div>
                </div>
            </div>

            {/* Filter and Scope Bar */}
            <div className="app-filter-strip">
                <div className="app-search-box">
                    <Search size={15} className="search-icon" />
                    <input
                        type="text"
                        placeholder="Filter active promotions by name, promo c..."
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                    />
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

                <div className="app-scope-group">
                    <span className="scope-label">SCOPE:</span>
                    <button
                        type="button"
                        className={`scope-pill ${scopeFilter === 'ALL' ? 'active' : ''}`}
                        onClick={() => setScopeFilter('ALL')}
                    >
                        All Active (5)
                    </button>
                    <button
                        type="button"
                        className={`scope-pill ${scopeFilter === 'PRODUCTS' ? 'active' : ''}`}
                        onClick={() => setScopeFilter('PRODUCTS')}
                    >
                        Specific Products (3)
                    </button>
                    <button
                        type="button"
                        className={`scope-pill ${scopeFilter === 'CATEGORIES' ? 'active' : ''}`}
                        onClick={() => setScopeFilter('CATEGORIES')}
                    >
                        Specific Categories (2)
                    </button>
                </div>

                <div className="app-view-toggle">
                    <button
                        type="button"
                        className={`view-btn ${viewMode === 'cards' ? 'active' : ''}`}
                        onClick={() => setViewMode('cards')}
                        title="Xem dạng thẻ (Cards View)"
                    >
                        <LayoutGrid size={15} />
                    </button>
                    <button
                        type="button"
                        className={`view-btn ${viewMode === 'dense' ? 'active' : ''}`}
                        onClick={() => setViewMode('dense')}
                        title="Xem dạng danh sách tinh gọn (Dense List)"
                    >
                        <List size={15} />
                    </button>
                </div>
            </div>

            {/* Content: Cards View vs Dense Table View */}
            {viewMode === 'cards' ? (
                /* CARDS GRID VIEW */
                <div className="app-cards-grid">
                    {filteredPromotions.map((promo) => {
                        const isCategory = promo.scopeType === 'CATEGORY';
                        const isOrangeBadge = promo.discountValue === 20;

                        return (
                            <div key={promo.promotionId} className={`app-promo-card ${promo.isNearExpiry ? 'card-near-expiry' : ''}`}>
                                <div className="card-top-row">
                                    <span className="card-code font-mono">{promo.code}</span>
                                    <span className={`card-discount-badge ${isOrangeBadge ? 'badge-orange' : 'badge-blue'}`}>
                                        {promo.discountBadge}
                                    </span>
                                </div>

                                <h3 className="card-promo-title">{promo.shortTitle || promo.promotionName}</h3>

                                {/* Scope Tag & Details */}
                                <div className="card-scope-box">
                                    <div className="scope-header-line">
                                        <div className="scope-type-title">
                                            {isCategory ? (
                                                <FolderTree size={14} className="scope-icon orange" />
                                            ) : (
                                                <Package size={14} className="scope-icon blue" />
                                            )}
                                            <strong>{promo.scopeLabel}</strong>
                                        </div>
                                        <span className="scope-sub-note">{promo.scopeSub}</span>
                                    </div>

                                    {/* Items List or Category Desc */}
                                    {isCategory ? (
                                        <div className="scope-category-content">
                                            <div className="cat-code-badge font-mono">
                                                Category: {promo.categoryName} {promo.categoryCode}
                                            </div>
                                            <p className="cat-desc-text">{promo.categoryDesc}</p>
                                        </div>
                                    ) : (
                                        <div className="scope-products-list">
                                            {promo.items && promo.items.map((it, idx) => (
                                                <div key={idx} className="scope-prod-row">
                                                    <span className="prod-bullet">•</span>
                                                    <span className="prod-name-clamp">{it.name}</span>
                                                    <span className="prod-sku-code font-mono">{it.sku}</span>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </div>

                                {/* Card Footer */}
                                <div className="card-footer-box">
                                    <div className="valid-time-row">
                                        <Clock size={12} className="time-icon" />
                                        <span>Valid until: <strong>{promo.validUntil}</strong></span>
                                    </div>
                                    <div className="card-pills-row">
                                        <span className={`status-tag-blue ${promo.isNearExpiry ? 'tag-urgent' : ''}`}>
                                            <span className="dot blue"></span> {promo.remainingDays}
                                        </span>
                                        <span className="status-tag-gray">
                                            <Terminal size={11} /> {promo.badgeRight}
                                        </span>
                                    </div>
                                </div>
                            </div>
                        );
                    })}

                    {/* Card 6: Cashier Desk Notice Card (Matching Mockup Right Side) */}
                    <div className="app-promo-card cashier-notice-card">
                        <div className="notice-card-header">
                            <HelpCircle size={16} className="text-blue" />
                            <strong>Cashier Desk Notice (Quy định CSKH)</strong>
                        </div>
                        <p className="notice-card-body">
                            Need to verify why a customer cart did not receive a discount? Check if the scanned barcode matches the exact <strong>SKU prefix</strong> listed above. Non-packaged fresh produce by weight requires standard weighing scale barcodes.
                        </p>
                        <div className="hotline-box">
                            <span className="hotline-label">Store Manager Hotline:</span>
                            <div className="hotline-number">
                                <Phone size={13} />
                                <strong>Ext: #0482 (Supermarket Operations)</strong>
                            </div>
                        </div>
                        <div className="notice-card-footer">
                            <Cpu size={13} className="text-blue" />
                            <span>SMS POS Engine v4.8 Active</span>
                        </div>
                    </div>
                </div>
            ) : (
                /* DENSE CASHIER LIST VIEW (For fast barcode checkout scanning) */
                <div className="app-dense-card">
                    <table className="app-dense-table">
                        <thead>
                            <tr>
                                <th>CODE</th>
                                <th>PROMOTION NAME</th>
                                <th>DISCOUNT</th>
                                <th>TARGET APPLICABILITY</th>
                                <th>VALID UNTIL</th>
                                <th>REMAINING</th>
                                <th>STATUS</th>
                            </tr>
                        </thead>
                        <tbody>
                            {filteredPromotions.map((p) => {
                                const isCategory = p.scopeType === 'CATEGORY';
                                return (
                                    <tr key={p.promotionId}>
                                        <td className="font-mono text-blue font-bold">{p.code}</td>
                                        <td>
                                            <div className="dense-name font-bold">{p.promotionName}</div>
                                        </td>
                                        <td className="font-mono text-blue font-bold">{p.discountBadge}</td>
                                        <td>
                                            <div className="dense-scope">
                                                {isCategory ? (
                                                    <span>📁 {p.categoryName} ({p.categoryCode})</span>
                                                ) : (
                                                    <span>📦 {p.items?.map(i => i.sku).join(', ')}</span>
                                                )}
                                            </div>
                                        </td>
                                        <td className="font-mono">{p.validUntil}</td>
                                        <td>
                                            <span className="dense-remaining-pill">
                                                {p.remainingDays}
                                            </span>
                                        </td>
                                        <td>
                                            <span className="dense-live-pill">
                                                ● Live POS
                                            </span>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}

            {/* Bottom System Bar */}
            <div className="app-bottom-bar">
                <div className="bottom-left-lock">
                    <Shield size={14} />
                    <span>Promotion parameters are locked in read-only mode for Cashiers &amp; Operations Staff per UC16 specifications.</span>
                </div>
                <div className="bottom-right-node">
                    <span>Store #04 POS Gate: <strong>OK</strong></span>
                    <span className="sep">•</span>
                    <span>Cashier Engine: <strong>v4.8-Build39</strong></span>
                </div>
            </div>
        </div>
    );
}

