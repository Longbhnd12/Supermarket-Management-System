/**
 * CategoryListPage — UC11: Manage Categories
 *
 * Implements category management according to SRS 3.5.4 and UC11 specification:
 * - Stat cards: Total Categories (covering active SKUs), Active in Service, Inactive/Archived.
 * - Search by category name & description, filter by status (All / Active / Inactive).
 * - Category table with departmental icons, slug tags, descriptions, status badges, and action triggers.
 * - Modal for Create Category & Edit Category (validates unique name, max lengths).
 * - Deactivate Confirmation Dialog with affected product count warning.
 * - Role-based authorization for ADMIN / MANAGER.
 */

import { useState, useEffect, useMemo, useCallback } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import CategoryService from '../../services/categoryService';
import { useAuth } from '../../context/authContext';
import './CategoryListPage.css';

// ─── Departmental Icon Helper ─────────────────────────────────────────────────

function getCategoryIcon(name = '') {
    const lower = name.toLowerCase();
    if (lower.includes('sữa') || lower.includes('trứng') || lower.includes('dairy') || lower.includes('egg')) {
        return <MilkEggIcon />;
    }
    if (lower.includes('mì') || lower.includes('phở') || lower.includes('noodle') || lower.includes('ăn liền')) {
        return <NoodleIcon />;
    }
    if (lower.includes('nước') || lower.includes('uống') || lower.includes('giải khát') || lower.includes('beverage') || lower.includes('cà phê')) {
        return <DrinkIcon />;
    }
    if (lower.includes('gia vị') || lower.includes('sốt') || lower.includes('mắm') || lower.includes('condiment') || lower.includes('dầu ăn')) {
        return <SauceIcon />;
    }
    if (lower.includes('gạo') || lower.includes('nông sản') || lower.includes('grain') || lower.includes('bột') || lower.includes('đậu')) {
        return <GrainIcon />;
    }
    if (lower.includes('bánh') || lower.includes('kẹo') || lower.includes('snack') || lower.includes('snack') || lower.includes('ăn vặt')) {
        return <SnackIcon />;
    }
    if (lower.includes('hóa mỹ phẩm') || lower.includes('tẩy rửa') || lower.includes('vệ sinh') || lower.includes('household')) {
        return <CleaningIcon />;
    }
    if (lower.includes('hộp') || lower.includes('chế biến') || lower.includes('đông lạnh') || lower.includes('canned')) {
        return <CannedIcon />;
    }
    return <DefaultFolderIcon />;
}

// ─── Slug generator ───────────────────────────────────────────────────────────

function generateSlug(name = '', id = 0) {
    const code = `CAT-${String(id).padStart(3, '0')}`;
    const slug = name
        .toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[đĐ]/g, 'd')
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/(^-|-$)+/g, '');
    return { code, slug: slug || 'general' };
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function CategoryListPage() {
    const navigate = useNavigate();
    const { hasPermission, hasRole } = useAuth();
    const canManage = hasPermission('CATEGORY_MANAGE') || hasRole('ADMIN', 'MANAGER');

    // ── Data State ────────────────────────────────────────────────────────────
    const [categories, setCategories] = useState([]);
    const [loading, setLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [error, setError] = useState(null);
    const [toastMessage, setToastMessage] = useState(null);

    // ── Filters & Search ──────────────────────────────────────────────────────
    const [keyword, setKeyword] = useState('');
    const [statusFilter, setStatusFilter] = useState('ALL'); // ALL, ACTIVE, INACTIVE

    // ── Modal State: Create / Edit ────────────────────────────────────────────
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [editingCategory, setEditingCategory] = useState(null); // null = Create Mode
    const [formData, setFormData] = useState({
        categoryName: '',
        description: '',
        status: 'ACTIVE',
    });
    const [formErrors, setFormErrors] = useState({});
    const [formSubmitting, setFormSubmitting] = useState(false);
    const [formServerError, setFormServerError] = useState('');

    // ── Modal State: Deactivate Confirmation ──────────────────────────────────
    const [deactivateModalCategory, setDeactivateModalCategory] = useState(null);
    const [deactivateSubmitting, setDeactivateSubmitting] = useState(false);

    // ── Notification Toast Auto-dismiss ───────────────────────────────────────
    useEffect(() => {
        if (!toastMessage) return;
        const timer = setTimeout(() => setToastMessage(null), 4000);
        return () => clearTimeout(timer);
    }, [toastMessage]);

    // ── Fetch Categories ──────────────────────────────────────────────────────
    const fetchCategories = useCallback(async (isRefresh = false) => {
        if (isRefresh) setRefreshing(true);
        else setLoading(true);
        setError(null);

        try {
            const res = await CategoryService.getAll();
            const data = res.data?.data || [];
            // Sort by categoryId ascending
            data.sort((a, b) => a.categoryId - b.categoryId);
            setCategories(data);
        } catch (err) {
            setError(err.response?.data?.message || 'Không thể tải danh sách ngành hàng.');
            setCategories([]);
        } finally {
            setLoading(false);
            setRefreshing(false);
        }
    }, []);

    useEffect(() => {
        fetchCategories();
    }, [fetchCategories]);

    // ── Aggregate Stats ───────────────────────────────────────────────────────
    const stats = useMemo(() => {
        const total = categories.length;
        const active = categories.filter(c => c.status === 'ACTIVE').length;
        const inactive = categories.filter(c => c.status === 'INACTIVE').length;
        const totalProducts = categories.reduce((sum, c) => sum + (c.productCount || 0), 0);
        return { total, active, inactive, totalProducts };
    }, [categories]);

    // ── Filtered Categories ───────────────────────────────────────────────────
    const filteredCategories = useMemo(() => {
        return categories.filter(c => {
            // Status filter
            if (statusFilter === 'ACTIVE' && c.status !== 'ACTIVE') return false;
            if (statusFilter === 'INACTIVE' && c.status !== 'INACTIVE') return false;

            // Keyword filter
            if (keyword.trim()) {
                const q = keyword.trim().toLowerCase();
                const nameMatch = c.categoryName && c.categoryName.toLowerCase().includes(q);
                const descMatch = c.description && c.description.toLowerCase().includes(q);
                if (!nameMatch && !descMatch) return false;
            }

            return true;
        });
    }, [categories, statusFilter, keyword]);

    // ── Modal Handlers: Open Create ───────────────────────────────────────────
    const handleOpenCreate = () => {
        setEditingCategory(null);
        setFormData({
            categoryName: '',
            description: '',
            status: 'ACTIVE',
        });
        setFormErrors({});
        setFormServerError('');
        setIsFormOpen(true);
    };

    // ── Modal Handlers: Open Edit ─────────────────────────────────────────────
    const handleOpenEdit = (category) => {
        setEditingCategory(category);
        setFormData({
            categoryName: category.categoryName || '',
            description: category.description || '',
            status: category.status || 'ACTIVE',
        });
        setFormErrors({});
        setFormServerError('');
        setIsFormOpen(true);
    };

    // ── Modal Validation ──────────────────────────────────────────────────────
    const validateForm = () => {
        const errors = {};
        if (!formData.categoryName.trim()) {
            errors.categoryName = 'Tên danh mục không được để trống';
        } else if (formData.categoryName.trim().length > 100) {
            errors.categoryName = 'Tên danh mục tối đa 100 ký tự';
        }

        if (formData.description && formData.description.length > 255) {
            errors.description = 'Mô tả tối đa 255 ký tự';
        }

        setFormErrors(errors);
        return Object.keys(errors).length === 0;
    };

    // ── Modal Handlers: Save Category (Create / Edit) ─────────────────────────
    const handleSaveCategory = async (e) => {
        e.preventDefault();
        if (!validateForm()) return;

        setFormSubmitting(true);
        setFormServerError('');

        const payload = {
            categoryName: formData.categoryName.trim(),
            description: formData.description.trim() || null,
            status: formData.status,
        };

        try {
            if (editingCategory) {
                // Update
                await CategoryService.update(editingCategory.categoryId, payload);
                setToastMessage({
                    type: 'success',
                    text: `Cập nhật danh mục "${payload.categoryName}" thành công!`
                });
            } else {
                // Create
                await CategoryService.create(payload);
                setToastMessage({
                    type: 'success',
                    text: `Tạo mới danh mục "${payload.categoryName}" thành công!`
                });
            }
            setIsFormOpen(false);
            fetchCategories();
        } catch (err) {
            const msg = err.response?.data?.message || err.message || 'Lỗi khi lưu danh mục.';
            setFormServerError(msg);
            if (msg.toLowerCase().includes('tồn tại') || msg.toLowerCase().includes('already exists')) {
                setFormErrors(prev => ({ ...prev, categoryName: msg }));
            }
        } finally {
            setFormSubmitting(false);
        }
    };

    // ── Status Toggle Handler: Activate directly / Prompt Deactivate ──────────
    const handleToggleStatus = (category) => {
        if (!canManage) return;

        if (category.status === 'ACTIVE') {
            // Open confirmation modal before deactivating
            setDeactivateModalCategory(category);
        } else {
            // Re-activate directly
            executeStatusChange(category.categoryId, 'ACTIVE', `Kích hoạt lại ngành hàng "${category.categoryName}" thành công!`);
        }
    };

    // ── Execute Status Change ─────────────────────────────────────────────────
    const executeStatusChange = async (id, newStatus, successMsg) => {
        try {
            await CategoryService.updateStatus(id, newStatus);
            setToastMessage({ type: 'success', text: successMsg });
            fetchCategories();
        } catch (err) {
            setToastMessage({
                type: 'error',
                text: err.response?.data?.message || 'Không thể thay đổi trạng thái danh mục.'
            });
        }
    };

    // ── Confirm Deactivate ────────────────────────────────────────────────────
    const handleConfirmDeactivate = async () => {
        if (!deactivateModalCategory) return;
        setDeactivateSubmitting(true);

        try {
            await CategoryService.updateStatus(deactivateModalCategory.categoryId, 'INACTIVE');
            setToastMessage({
                type: 'success',
                text: `Đã vô hiệu hóa danh mục "${deactivateModalCategory.categoryName}".`
            });
            setDeactivateModalCategory(null);
            fetchCategories();
        } catch (err) {
            setToastMessage({
                type: 'error',
                text: err.response?.data?.message || 'Không thể vô hiệu hóa danh mục.'
            });
        } finally {
            setDeactivateSubmitting(false);
        }
    };

    return (
        <div className="cat-page">
            {/* ── Toast Notification ────────────────────────────────────────── */}
            {toastMessage && (
                <div className={`cat-toast cat-toast--${toastMessage.type}`}>
                    <span>{toastMessage.type === 'success' ? '✔' : '⚠'}</span>
                    <span>{toastMessage.text}</span>
                    <button className="cat-toast-close" onClick={() => setToastMessage(null)}>×</button>
                </div>
            )}

            {/* ── Header & Breadcrumbs ──────────────────────────────────────── */}
            <div className="cat-header">
                <div className="cat-header-left">
                    <div className="cat-breadcrumb">
                        <Link to="/" className="cat-breadcrumb-link">Home</Link>
                        <span className="cat-breadcrumb-sep">/</span>
                        <Link to="/products" className="cat-breadcrumb-link">Operations Hub</Link>
                        <span className="cat-breadcrumb-sep">/</span>
                        <Link to="/products" className="cat-breadcrumb-link">Catalog &amp; Inventory</Link>
                        <span className="cat-breadcrumb-sep">/</span>
                        <span className="cat-breadcrumb-current">Categories (UC11)</span>
                    </div>

                    <div className="cat-title-row">
                        <h1 className="cat-title">Category Management</h1>
                        <span className="cat-spec-badge">SRS 3.5.4 (UC11)</span>
                        <span className="cat-role-badge">Admin &amp; Manager Only</span>
                    </div>

                    <p className="cat-subtitle">
                        Supermarket merchandise taxonomy configuration. Maintain exact departments, active sales availability, and retail product associations.
                    </p>
                </div>

                <div className="cat-header-actions">
                    <button
                        className="cat-btn cat-btn--secondary"
                        onClick={() => navigate('/products')}
                        title="Quay lại danh sách sản phẩm (UC09)"
                    >
                        View Product Catalog
                    </button>

                    {canManage && (
                        <button
                            id="cat-create-btn"
                            className="cat-btn cat-btn--primary"
                            onClick={handleOpenCreate}
                        >
                            <PlusIcon />
                            + New Category
                        </button>
                    )}
                </div>
            </div>

            {/* ── Stat Cards Row ────────────────────────────────────────────── */}
            <div className="cat-stats-row">
                {/* Total */}
                <div className="cat-stat-card">
                    <div className="cat-stat-icon-wrap cat-stat-icon-wrap--blue">
                        <TaxonomyIcon />
                    </div>
                    <div className="cat-stat-info">
                        <div className="cat-stat-label">TOTAL CATEGORIES</div>
                        <div className="cat-stat-value">
                            {stats.total} <span className="cat-stat-unit">Departments</span>
                        </div>
                        <div className="cat-stat-sub">
                            Covering {stats.totalProducts.toLocaleString()} Active SKUs
                        </div>
                    </div>
                </div>

                {/* Active */}
                <div className="cat-stat-card">
                    <div className="cat-stat-icon-wrap cat-stat-icon-wrap--green">
                        <CheckCircleIcon />
                    </div>
                    <div className="cat-stat-info">
                        <div className="cat-stat-label">ACTIVE STATUS</div>
                        <div className="cat-stat-value">
                            {stats.active} <span className="cat-stat-unit">In Service</span>
                        </div>
                        <div className="cat-stat-sub cat-stat-sub--green">
                            ✓ Available at Cashier POS &amp; Imports
                        </div>
                    </div>
                </div>

                {/* Inactive */}
                <div className="cat-stat-card">
                    <div className="cat-stat-icon-wrap cat-stat-icon-wrap--gray">
                        <ArchiveIcon />
                    </div>
                    <div className="cat-stat-info">
                        <div className="cat-stat-label">INACTIVE CATEGORIES</div>
                        <div className="cat-stat-value">
                            {stats.inactive} <span className="cat-stat-unit">Archived</span>
                        </div>
                        <div className="cat-stat-sub cat-stat-sub--muted">
                            Product links intact; new entries blocked
                        </div>
                    </div>
                </div>
            </div>

            {/* ── Filter & Search Toolbar ───────────────────────────────────── */}
            <div className="cat-toolbar">
                <div className="cat-search-wrap">
                    <SearchIcon className="cat-search-icon" />
                    <input
                        type="text"
                        className="cat-search-input"
                        placeholder="Search category name or description..."
                        value={keyword}
                        onChange={e => setKeyword(e.target.value)}
                        maxLength={100}
                    />
                    {keyword && (
                        <button
                            className="cat-search-clear"
                            onClick={() => setKeyword('')}
                            title="Xóa tìm kiếm"
                        >×</button>
                    )}
                </div>

                <div className="cat-filter-pills">
                    <button
                        className={`cat-pill ${statusFilter === 'ALL' ? 'cat-pill--active' : ''}`}
                        onClick={() => setStatusFilter('ALL')}
                    >
                        All
                    </button>
                    <button
                        className={`cat-pill ${statusFilter === 'ACTIVE' ? 'cat-pill--active' : ''}`}
                        onClick={() => setStatusFilter('ACTIVE')}
                    >
                        Active
                    </button>
                    <button
                        className={`cat-pill ${statusFilter === 'INACTIVE' ? 'cat-pill--active' : ''}`}
                        onClick={() => setStatusFilter('INACTIVE')}
                    >
                        Inactive
                    </button>
                </div>

                <div className="cat-toolbar-right">
                    <span className="cat-modify-rights">
                        Roles with modify rights: <strong>Admin, Manager</strong>
                    </span>
                    <button
                        className={`cat-btn-icon ${refreshing ? 'cat-btn-icon--spinning' : ''}`}
                        onClick={() => fetchCategories(true)}
                        title="Làm mới danh sách"
                        disabled={refreshing}
                    >
                        <RefreshIcon />
                    </button>
                </div>
            </div>

            {/* ── Table Card ────────────────────────────────────────────────── */}
            <div className="cat-card">
                {/* Table Header Info */}
                <div className="cat-table-top-bar">
                    <span className="cat-table-count">
                        Showing <strong>{filteredCategories.length}</strong> of <strong>{categories.length}</strong> categories
                    </span>
                </div>

                {loading ? (
                    <div className="cat-state-box">
                        <span className="cat-spinner" />
                        <p>Loading taxonomy departments...</p>
                    </div>
                ) : error ? (
                    <div className="cat-state-box cat-state-box--error">
                        <p>{error}</p>
                        <button className="cat-btn cat-btn--secondary" onClick={() => fetchCategories()}>
                            Thử lại
                        </button>
                    </div>
                ) : filteredCategories.length === 0 ? (
                    <div className="cat-state-box">
                        <EmptyBoxIcon />
                        <p className="cat-empty-title">Không tìm thấy danh mục phù hợp</p>
                        <p className="cat-empty-sub">
                            {keyword || statusFilter !== 'ALL'
                                ? 'Thử điều chỉnh từ khóa tìm kiếm hoặc chuyển bộ lọc về "All".'
                                : 'Hệ thống chưa có danh mục sản phẩm nào.'}
                        </p>
                    </div>
                ) : (
                    <div className="cat-table-wrap">
                        <table className="cat-table">
                            <thead>
                                <tr>
                                    <th className="cat-th--name">CATEGORY NAME</th>
                                    <th className="cat-th--desc">DESCRIPTION</th>
                                    <th className="cat-th--status">STATUS</th>
                                    <th className="cat-th--actions">ACTIONS</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filteredCategories.map(cat => {
                                    const { code, slug } = generateSlug(cat.categoryName, cat.categoryId);
                                    const isActive = cat.status === 'ACTIVE';

                                    return (
                                        <tr key={cat.categoryId} className={`cat-row ${!isActive ? 'cat-row--inactive' : ''}`}>
                                            {/* Name & Slug */}
                                            <td className="cat-td--name">
                                                <div className="cat-name-cell">
                                                    <div className="cat-icon-avatar">
                                                        {getCategoryIcon(cat.categoryName)}
                                                    </div>
                                                    <div className="cat-name-info">
                                                        <span className="cat-primary-name">{cat.categoryName}</span>
                                                        <span className="cat-meta-slug">
                                                            {code} • {slug}
                                                            {cat.productCount > 0 && (
                                                                <span className="cat-prod-count">
                                                                    ({cat.productCount} SKUs)
                                                                </span>
                                                            )}
                                                        </span>
                                                    </div>
                                                </div>
                                            </td>

                                            {/* Description */}
                                            <td className="cat-td--desc">
                                                <span className="cat-desc-text">
                                                    {cat.description || <em className="cat-no-desc">Chưa có mô tả</em>}
                                                </span>
                                            </td>

                                            {/* Status */}
                                            <td className="cat-td--status">
                                                <span className={`cat-status-badge cat-status-badge--${isActive ? 'active' : 'inactive'}`}>
                                                    <span className="cat-status-dot" />
                                                    {isActive ? 'Active' : 'Inactive'}
                                                </span>
                                            </td>

                                            {/* Actions */}
                                            <td className="cat-td--actions">
                                                <div className="cat-action-group">
                                                    {/* Edit */}
                                                    <button
                                                        type="button"
                                                        className="cat-btn-action cat-btn-action--edit"
                                                        onClick={() => handleOpenEdit(cat)}
                                                        disabled={!canManage}
                                                        title="Chỉnh sửa danh mục"
                                                    >
                                                        <EditIcon />
                                                        Edit
                                                    </button>

                                                    {/* Deactivate / Activate */}
                                                    {isActive ? (
                                                        <button
                                                            type="button"
                                                            className="cat-btn-action cat-btn-action--deactivate"
                                                            onClick={() => handleToggleStatus(cat)}
                                                            disabled={!canManage}
                                                            title="Vô hiệu hóa danh mục"
                                                        >
                                                            <BanIcon />
                                                            Deactivate
                                                        </button>
                                                    ) : (
                                                        <button
                                                            type="button"
                                                            className="cat-btn-action cat-btn-action--activate"
                                                            onClick={() => handleToggleStatus(cat)}
                                                            disabled={!canManage}
                                                            title="Kích hoạt lại danh mục"
                                                        >
                                                            <CheckSmallIcon />
                                                            Activate
                                                        </button>
                                                    )}
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}

                {/* Table Footer */}
                <div className="cat-table-footer">
                    <span className="cat-footer-text">
                        Showing {filteredCategories.length} of {categories.length} categories • Standard View (UC11)
                    </span>
                </div>
            </div>

            {/* ═══════════════════════════════════════════════════════════════════
                 MODAL: CREATE / EDIT CATEGORY
               ═══════════════════════════════════════════════════════════════════ */}
            {isFormOpen && (
                <div className="cat-modal-backdrop" onClick={() => !formSubmitting && setIsFormOpen(false)}>
                    <div className="cat-modal" onClick={e => e.stopPropagation()}>
                        <div className="cat-modal-header">
                            <div>
                                <h2 className="cat-modal-title">
                                    {editingCategory ? 'Edit Category' : 'Create New Category'}
                                </h2>
                                <p className="cat-modal-subtitle">
                                    {editingCategory
                                        ? `Cập nhật thông tin ngành hàng #${editingCategory.categoryId}`
                                        : 'Thêm ngành hàng phân loại sản phẩm mới vào danh mục siêu thị'}
                                </p>
                            </div>
                            <button
                                type="button"
                                className="cat-modal-close"
                                onClick={() => setIsFormOpen(false)}
                                disabled={formSubmitting}
                            >×</button>
                        </div>

                        {formServerError && (
                            <div className="cat-modal-alert cat-modal-alert--error">
                                ⚠ {formServerError}
                            </div>
                        )}

                        <form onSubmit={handleSaveCategory} className="cat-modal-form">
                            {/* Category Name */}
                            <div className="cat-form-group">
                                <div className="cat-form-label-row">
                                    <label className="cat-form-label" htmlFor="cat-name-input">
                                        Category Name <span className="cat-req">*</span>
                                    </label>
                                    <span className={`cat-char-count ${formData.categoryName.length > 100 ? 'cat-char-count--warn' : ''}`}>
                                        {formData.categoryName.length} / 100
                                    </span>
                                </div>
                                <input
                                    id="cat-name-input"
                                    type="text"
                                    className={`cat-input ${formErrors.categoryName ? 'cat-input--error' : ''}`}
                                    placeholder="e.g. Sữa & Trứng, Nước giải khát..."
                                    maxLength={100}
                                    value={formData.categoryName}
                                    onChange={e => {
                                        setFormData(prev => ({ ...prev, categoryName: e.target.value }));
                                        if (formErrors.categoryName) {
                                            setFormErrors(prev => ({ ...prev, categoryName: null }));
                                        }
                                    }}
                                    disabled={formSubmitting}
                                    autoFocus
                                    required
                                />
                                {formErrors.categoryName ? (
                                    <span className="cat-error-text">{formErrors.categoryName}</span>
                                ) : (
                                    <span className="cat-hint-text">Tên ngành hàng hiển thị trên bảng sản phẩm, POS và báo cáo.</span>
                                )}
                            </div>

                            {/* Description */}
                            <div className="cat-form-group">
                                <div className="cat-form-label-row">
                                    <label className="cat-form-label" htmlFor="cat-desc-input">
                                        Description <span className="cat-opt">(Optional)</span>
                                    </label>
                                    <span className={`cat-char-count ${formData.description.length > 255 ? 'cat-char-count--warn' : ''}`}>
                                        {formData.description.length} / 255
                                    </span>
                                </div>
                                <textarea
                                    id="cat-desc-input"
                                    rows={3}
                                    className={`cat-textarea ${formErrors.description ? 'cat-input--error' : ''}`}
                                    placeholder="Mô tả nhóm sản phẩm, quy chuẩn bảo quản hoặc vị trí quầy kệ..."
                                    maxLength={255}
                                    value={formData.description}
                                    onChange={e => {
                                        setFormData(prev => ({ ...prev, description: e.target.value }));
                                        if (formErrors.description) {
                                            setFormErrors(prev => ({ ...prev, description: null }));
                                        }
                                    }}
                                    disabled={formSubmitting}
                                />
                                {formErrors.description ? (
                                    <span className="cat-error-text">{formErrors.description}</span>
                                ) : (
                                    <span className="cat-hint-text">Mô tả chi tiết các mặt hàng thuộc nhóm này.</span>
                                )}
                            </div>

                            {/* Status */}
                            <div className="cat-form-group">
                                <label className="cat-form-label">Initial Status</label>
                                <div className="cat-status-selector">
                                    <label className={`cat-status-option ${formData.status === 'ACTIVE' ? 'cat-status-option--selected' : ''}`}>
                                        <input
                                            type="radio"
                                            name="cat-status"
                                            value="ACTIVE"
                                            checked={formData.status === 'ACTIVE'}
                                            onChange={() => setFormData(prev => ({ ...prev, status: 'ACTIVE' }))}
                                            disabled={formSubmitting}
                                        />
                                        <div>
                                            <span className="cat-status-opt-title">Active</span>
                                            <span className="cat-status-opt-sub">Sử dụng ngay cho hàng hóa và thu ngân POS</span>
                                        </div>
                                    </label>
                                    <label className={`cat-status-option ${formData.status === 'INACTIVE' ? 'cat-status-option--selected' : ''}`}>
                                        <input
                                            type="radio"
                                            name="cat-status"
                                            value="INACTIVE"
                                            checked={formData.status === 'INACTIVE'}
                                            onChange={() => setFormData(prev => ({ ...prev, status: 'INACTIVE' }))}
                                            disabled={formSubmitting}
                                        />
                                        <div>
                                            <span className="cat-status-opt-title">Inactive</span>
                                            <span className="cat-status-opt-sub">Lưu trữ tạm thời, chưa áp dụng vào bán hàng</span>
                                        </div>
                                    </label>
                                </div>
                            </div>

                            <div className="cat-modal-footer">
                                <button
                                    type="button"
                                    className="cat-btn cat-btn--secondary"
                                    onClick={() => setIsFormOpen(false)}
                                    disabled={formSubmitting}
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    className="cat-btn cat-btn--primary"
                                    disabled={formSubmitting}
                                >
                                    {formSubmitting ? (
                                        <><span className="cat-btn-spinner" /> Saving...</>
                                    ) : (
                                        editingCategory ? 'Save Changes' : 'Create Category'
                                    )}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* ═══════════════════════════════════════════════════════════════════
                 MODAL: DEACTIVATE CONFIRMATION DIALOG
               ═══════════════════════════════════════════════════════════════════ */}
            {deactivateModalCategory && (
                <div className="cat-modal-backdrop" onClick={() => !deactivateSubmitting && setDeactivateModalCategory(null)}>
                    <div className="cat-modal cat-modal--confirm" onClick={e => e.stopPropagation()}>
                        <div className="cat-confirm-icon-wrap">
                            <AlertWarningIcon />
                        </div>

                        <h2 className="cat-confirm-title">
                            Deactivate Department Confirmation
                        </h2>

                        <p className="cat-confirm-message">
                            Bạn có chắc chắn muốn vô hiệu hóa ngành hàng <strong>"{deactivateModalCategory.categoryName}"</strong>?
                        </p>

                        <div className="cat-confirm-notice">
                            <strong>Lưu ý an toàn nghiệp vụ (SRS 3.5.4):</strong>
                            <ul>
                                <li>
                                    Ngành hàng này hiện có <strong>{deactivateModalCategory.productCount || 0} sản phẩm</strong> đang liên kết.
                                </li>
                                <li>
                                    Các sản phẩm cũ vẫn giữ nguyên liên kết và lịch sử dữ liệu, nhưng sẽ <strong>không thể tạo mới hoặc gán</strong> danh mục này cho sản phẩm khác.
                                </li>
                            </ul>
                        </div>

                        <div className="cat-modal-footer cat-modal-footer--confirm">
                            <button
                                type="button"
                                className="cat-btn cat-btn--secondary"
                                onClick={() => setDeactivateModalCategory(null)}
                                disabled={deactivateSubmitting}
                            >
                                Cancel
                            </button>
                            <button
                                type="button"
                                className="cat-btn cat-btn--danger"
                                onClick={handleConfirmDeactivate}
                                disabled={deactivateSubmitting}
                            >
                                {deactivateSubmitting ? (
                                    <><span className="cat-btn-spinner" /> Deactivating...</>
                                ) : (
                                    'Confirm Deactivate'
                                )}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

// ─── SVG Icons ────────────────────────────────────────────────────────────────

function TaxonomyIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="22" height="22">
            <rect x="3" y="3" width="7" height="7" rx="1.5" />
            <rect x="14" y="3" width="7" height="7" rx="1.5" />
            <rect x="14" y="14" width="7" height="7" rx="1.5" />
            <rect x="3" y="14" width="7" height="7" rx="1.5" />
        </svg>
    );
}

function CheckCircleIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="22" height="22">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
            <polyline points="22 4 12 14.01 9 11.01" />
        </svg>
    );
}

function ArchiveIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="22" height="22">
            <polyline points="21 8 21 21 3 21 3 8" />
            <rect x="1" y="3" width="22" height="5" rx="1" />
            <line x1="10" y1="12" x2="14" y2="12" />
        </svg>
    );
}

function SearchIcon({ className }) {
    return (
        <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
    );
}

function RefreshIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
            <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.19" />
        </svg>
    );
}

function PlusIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" width="16" height="16">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
    );
}

function EditIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="13" height="13">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
        </svg>
    );
}

function BanIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="13" height="13">
            <circle cx="12" cy="12" r="10" />
            <line x1="4.93" y1="4.93" x2="19.07" y2="19.07" />
        </svg>
    );
}

function CheckSmallIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" width="13" height="13">
            <polyline points="20 6 9 17 4 12" />
        </svg>
    );
}

function AlertWarningIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="32" height="32" style={{ color: '#d97706' }}>
            <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z" />
            <line x1="12" y1="9" x2="12" y2="13" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
        </svg>
    );
}

function EmptyBoxIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" width="48" height="48" style={{ color: '#94a3b8' }}>
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
            <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
            <line x1="12" y1="22.08" x2="12" y2="12" />
        </svg>
    );
}

// ── Department Icons ──────────────────────────────────────────────────────────

function MilkEggIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="18" height="18" style={{ color: '#2563eb' }}>
            <path d="M8 2h8l1 4H7l1-4z" />
            <path d="M7 6v14a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2V6" />
            <circle cx="12" cy="14" r="2.5" />
        </svg>
    );
}

function NoodleIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="18" height="18" style={{ color: '#ea580c' }}>
            <path d="M4 11h16a8 8 0 0 1-16 0z" />
            <path d="M6 11V6" /><path d="M10 11V6" /><path d="M14 11V6" /><path d="M18 11V6" />
            <path d="M4 19h16" />
        </svg>
    );
}

function DrinkIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="18" height="18" style={{ color: '#0284c7' }}>
            <path d="M18 8h1a4 4 0 0 1 0 8h-1" />
            <path d="M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z" />
            <line x1="6" y1="1" x2="6" y2="4" /><line x1="10" y1="1" x2="10" y2="4" /><line x1="14" y1="1" x2="14" y2="4" />
        </svg>
    );
}

function SauceIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="18" height="18" style={{ color: '#dc2626' }}>
            <path d="M9 3h6v4H9z" />
            <path d="M8 7l-2 5v9a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2v-9l-2-5" />
            <path d="M10 14h4" />
        </svg>
    );
}

function GrainIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="18" height="18" style={{ color: '#ca8a04' }}>
            <path d="M2 22s5-3 10-10c5-7 10-10 10-10s-3 5-10 10c-7 5-10 10-10 10z" />
            <path d="M12 12c-2-2-4-2-6 0" /><path d="M18 6c-2-2-4-2-6 0" />
        </svg>
    );
}

function SnackIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="18" height="18" style={{ color: '#d946ef' }}>
            <circle cx="12" cy="12" r="9" />
            <circle cx="9" cy="9" r="1.5" /><circle cx="15" cy="9" r="1.5" />
            <path d="M8 15s1.5 2 4 2 4-2 4-2" />
        </svg>
    );
}

function CleaningIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="18" height="18" style={{ color: '#0d9488' }}>
            <path d="m19 11-4-7-4 7" /><path d="M15 4v16" /><path d="M4 20h16" />
        </svg>
    );
}

function CannedIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="18" height="18" style={{ color: '#64748b' }}>
            <ellipse cx="12" cy="5" rx="8" ry="3" />
            <path d="M4 5v14c0 1.66 3.58 3 8 3s8-1.34 8-3V5" />
            <path d="M4 12c0 1.66 3.58 3 8 3s8-1.34 8-3" />
        </svg>
    );
}

function DefaultFolderIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="18" height="18" style={{ color: '#475569' }}>
            <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
        </svg>
    );
}

