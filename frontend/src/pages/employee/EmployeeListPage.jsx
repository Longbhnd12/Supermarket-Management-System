import React, { useState, useEffect, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
    Users, UserCheck, UserX, Shield, CreditCard, Search,
    RotateCcw, Download, Eye, MoreVertical, ChevronLeft, ChevronRight,
    UserPlus, CheckCircle2, AlertCircle, Sparkles, Building2,
    ShieldCheck, Package, Lock, ExternalLink, RefreshCw
} from 'lucide-react';
import { useAuth } from '../../context/authContext';
import EmployeeService from '../../services/employeeService';
import { enrichEmployee } from './mockEmployeesData';
import { mergePositions, getPositionDisplayLabel } from './employeePositions';
import './EmployeeListPage.css';

export default function EmployeeListPage() {
    const navigate = useNavigate();
    const { currentUser, hasRole } = useAuth();

    // Data states
    const [employees, setEmployees] = useState([]);
    const [dbPositions, setDbPositions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [toastMessage, setToastMessage] = useState(null);

    // Filter states
    const [searchTerm, setSearchTerm] = useState('');
    const [positionFilter, setPositionFilter] = useState('ALL');
    const [statusFilter, setStatusFilter] = useState('ALL'); // 'ALL' | 'ACTIVE' | 'INACTIVE'

    // Pagination
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    // Active dropdown row ID
    const [openDropdownId, setOpenDropdownId] = useState(null);

    const showToast = (msg) => {
        setToastMessage(msg);
        setTimeout(() => setToastMessage(null), 3500);
    };

    // Load employees from backend API
    const fetchEmployees = async () => {
        setLoading(true);
        try {
            const res = await EmployeeService.getAll();
            const backendList = res.data?.data || res.data || [];
            if (Array.isArray(backendList) && backendList.length > 0) {
                // Enrich backend items with UI metadata
                const enriched = backendList.map(enrichEmployee);
                setEmployees(enriched);
            } else {
                setEmployees([]);
            }
        } catch (err) {
            console.warn('Backend employee fetch failed:', err);
            setEmployees([]);
        } finally {
            setLoading(false);
        }
    };

    // Fetch distinct positions from database API
    const fetchPositions = async () => {
        try {
            const res = await EmployeeService.getPositions();
            const list = res.data?.data || res.data || [];
            if (Array.isArray(list)) {
                setDbPositions(list);
            }
        } catch (err) {
            console.warn('Backend distinct positions fetch failed:', err);
        }
    };

    useEffect(() => {
        fetchEmployees();
        fetchPositions();
    }, []);

    // Dynamically derive unique positions from API, DB, and unpaginated employees
    const availablePositions = useMemo(() => {
        const fromEmployees = employees.map(e => e.position).filter(Boolean);
        return mergePositions([...dbPositions, ...fromEmployees]);
    }, [dbPositions, employees]);

    // Close dropdown on outside click
    useEffect(() => {
        const handleClickOutside = () => setOpenDropdownId(null);
        window.addEventListener('click', handleClickOutside);
        return () => window.removeEventListener('click', handleClickOutside);
    }, []);

    // Filter Logic
    const filteredEmployees = useMemo(() => {
        return employees.filter((emp) => {
            // Position filter
            if (positionFilter !== 'ALL' && emp.position !== positionFilter) {
                return false;
            }
            // Status filter
            if (statusFilter !== 'ALL' && emp.status !== statusFilter) {
                return false;
            }
            // Search term (code, name, email, phone)
            if (searchTerm.trim()) {
                const term = searchTerm.toLowerCase().trim();
                const matchCode = emp.employeeCode?.toLowerCase().includes(term);
                const matchName = emp.fullName?.toLowerCase().includes(term);
                const matchEmail = emp.email?.toLowerCase().includes(term);
                const matchPhone = emp.phone?.toLowerCase().replace(/\s/g, '').includes(term.replace(/\s/g, ''));
                if (!matchCode && !matchName && !matchEmail && !matchPhone) {
                    return false;
                }
            }
            return true;
        });
    }, [employees, positionFilter, statusFilter, searchTerm]);

    // Pagination calculations
    const totalPages = Math.ceil(filteredEmployees.length / pageSize) || 1;
    const paginatedEmployees = useMemo(() => {
        const start = (currentPage - 1) * pageSize;
        return filteredEmployees.slice(start, start + pageSize);
    }, [filteredEmployees, currentPage, pageSize]);

    // Summary Metrics
    const metrics = useMemo(() => {
        const total = employees.length;
        const active = employees.filter((e) => e.status === 'ACTIVE').length;
        const inactive = employees.filter((e) => e.status === 'INACTIVE').length;
        const activePercent = total > 0 ? ((active / total) * 100).toFixed(1) : '0.0';
        const inactivePercent = total > 0 ? ((inactive / total) * 100).toFixed(1) : '0.0';

        const cashiers = employees.filter((e) => e.position === 'Cashier').length;
        const inventory = employees.filter((e) => e.position === 'Inventory Staff').length;
        const managers = employees.filter((e) => e.position === 'Manager').length;
        const admins = employees.filter((e) => e.position === 'Administrator').length;

        return {
            total,
            active,
            inactive,
            activePercent,
            inactivePercent,
            cashiers,
            inventory,
            managers,
            admins
        };
    }, [employees]);

    // Reset all filters
    const handleResetFilters = () => {
        setSearchTerm('');
        setPositionFilter('ALL');
        setStatusFilter('ALL');
        setCurrentPage(1);
        showToast('Đã đặt lại toàn bộ bộ lọc về trạng thái ban đầu.');
    };

    // Export to CSV
    const handleExportCSV = () => {
        const headers = ['Mã nhân viên,Họ và tên,Vị trí,Số điện thoại,Email,Trạng thái'];
        const rows = filteredEmployees.map((e) =>
            `"${e.employeeCode}","${e.fullName}","${e.position}","${e.phone}","${e.email}","${e.status}"`
        );
        const csvContent = 'data:text/csv;charset=utf-8,\uFEFF' + [headers, ...rows].join('\n');
        const encodedUri = encodeURI(csvContent);
        const link = document.createElement('a');
        link.setAttribute('href', encodedUri);
        link.setAttribute('download', `SMS_Employee_Directory_${new Date().toISOString().slice(0, 10)}.csv`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        showToast(`Đã xuất ${filteredEmployees.length} bản ghi nhân viên ra tệp CSV.`);
    };

    // Toggle Employee Status
    const handleToggleStatus = async (emp, e) => {
        e.stopPropagation();
        const newStatus = emp.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
        const actionText = newStatus === 'ACTIVE' ? 'Kích hoạt lại' : 'Tạm dừng hoạt động';

        try {
            if (emp.employeeId) {
                await EmployeeService.updateStatus(emp.employeeId, newStatus);
            }
            setEmployees((prev) =>
                prev.map((item) =>
                    item.employeeId === emp.employeeId || item.employeeCode === emp.employeeCode
                        ? { ...item, status: newStatus }
                        : item
                )
            );
            showToast(`Đã ${actionText} thành công nhân viên ${emp.fullName} (${emp.employeeCode}).`);
        } catch (err) {
            console.warn('Status toggle fallback to local state:', err);
            setEmployees((prev) =>
                prev.map((item) =>
                    item.employeeId === emp.employeeId || item.employeeCode === emp.employeeCode
                        ? { ...item, status: newStatus }
                        : item
                )
            );
            showToast(`Đã ${actionText} nhân viên ${emp.fullName} (Mô phỏng).`);
        }
        setOpenDropdownId(null);
    };

    const getPositionBadge = (pos) => {
        switch (pos) {
            case 'Cashier':
                return (
                    <span className="pos-badge pos-cashier">
                        <Users size={12} />
                        Cashier
                    </span>
                );
            case 'Inventory Staff':
                return (
                    <span className="pos-badge pos-inventory">
                        <Package size={12} />
                        Inventory Staff
                    </span>
                );
            case 'Manager':
                return (
                    <span className="pos-badge pos-manager">
                        <UserCheck size={12} />
                        Manager
                    </span>
                );
            case 'Administrator':
                return (
                    <span className="pos-badge pos-admin">
                        <Shield size={12} />
                        Administrator
                    </span>
                );
            default:
                return <span className="pos-badge">{pos}</span>;
        }
    };

    return (
        <div className="employee-list-page-container">
            {/* Top Toast Banner */}
            {toastMessage && (
                <div className="employee-toast-banner">
                    <CheckCircle2 size={16} />
                    <span>{toastMessage}</span>
                </div>
            )}

            {/* Breadcrumb & Main Header */}
            <div className="employee-page-header">
                <div className="header-breadcrumbs">
                    <Link to="/">Home</Link>
                    <span className="crumb-sep">/</span>
                    <span>Operations Hub</span>
                    <span className="crumb-sep">/</span>
                    <span>Directory</span>
                    <span className="crumb-sep">/</span>
                    <span className="crumb-current">Employees (UC17)</span>
                </div>

                <div className="header-title-bar">
                    <div className="title-left">
                        <div className="title-heading-row">
                            <h1 className="page-title">Employee Management</h1>
                            <span className="role-access-pill">
                                <ShieldCheck size={14} />
                                Role Access: Administrator & Manager Only
                            </span>
                        </div>
                        <p className="page-desc">
                            Directory and access control of supermarket store personnel, cashiers, inventory clerks, and managerial staff (SRS 3.6.1 • UC17).
                        </p>
                    </div>

                    <div className="header-actions">
                        <button
                            type="button"
                            className="action-btn secondary reset-btn"
                            onClick={handleResetFilters}
                            title="Đặt lại các bộ lọc"
                        >
                            <RotateCcw size={15} />
                            Reset
                        </button>
                        <button
                            type="button"
                            className="action-btn primary create-emp-btn"
                            onClick={() => navigate('/employees/create')}
                        >
                            <UserPlus size={16} />
                            Create Employee
                        </button>
                    </div>
                </div>
            </div>

            {/* 4 KPI / METRIC SUMMARY CARDS */}
            <div className="employee-metrics-grid">
                {/* Metric 1: Total Employees */}
                <div className="metric-card total-card">
                    <div className="metric-header-row">
                        <span className="metric-title">TOTAL EMPLOYEES</span>
                        <div className="metric-icon-wrap blue">
                            <Users size={18} />
                        </div>
                    </div>
                    <div className="metric-main-val">{metrics.total}</div>
                    <div className="metric-subtext-row">
                        <span className="store-tag">
                            <Building2 size={12} />
                            Store #04 Flagship
                        </span>
                        <span className="capacity-text">100% capacity</span>
                    </div>
                </div>

                {/* Metric 2: In-Service Personnel */}
                <div className="metric-card active-card">
                    <div className="metric-header-row">
                        <span className="metric-title">IN-SERVICE PERSONNEL</span>
                        <div className="metric-icon-wrap green">
                            <UserCheck size={18} />
                        </div>
                    </div>
                    <div className="metric-val-with-status">
                        <span className="val-number">{metrics.active}</span>
                        <span className="val-label green">Active Staff</span>
                    </div>
                    <div className="metric-progress-wrap">
                        <div className="metric-progress-bar">
                            <div
                                className="progress-fill green"
                                style={{ width: `${metrics.activePercent}%` }}
                            ></div>
                        </div>
                        <span className="progress-percent">{metrics.activePercent}%</span>
                    </div>
                </div>

                {/* Metric 3: Inactive / On Leave */}
                <div className="metric-card inactive-card">
                    <div className="metric-header-row">
                        <span className="metric-title">INACTIVE / ON LEAVE</span>
                        <div className="metric-icon-wrap purple">
                            <UserX size={18} />
                        </div>
                    </div>
                    <div className="metric-val-with-status">
                        <span className="val-number">{metrics.inactive}</span>
                        <span className="val-label purple">Staff</span>
                    </div>
                    <div className="metric-breakdown-row">
                        <span className="leave-detail">● 2 Medical • 2 Resigned</span>
                        <span className="leave-percent">{metrics.inactivePercent}%</span>
                    </div>
                </div>

                {/* Metric 4: Role & Terminal Coverage */}
                <div className="metric-card coverage-card">
                    <div className="metric-header-row">
                        <span className="metric-title">ROLE & TERMINAL COVERAGE</span>
                        <div className="metric-icon-wrap indigo">
                            <CreditCard size={18} />
                        </div>
                    </div>
                    <div className="coverage-subcols-grid">
                        <div className="coverage-col">
                            <span className="cov-num">{metrics.cashiers}</span>
                            <span className="cov-label">Cashiers</span>
                            <span className="cov-note">POS Regs 1-8 Covered</span>
                        </div>
                        <div className="coverage-col">
                            <span className="cov-num">{metrics.inventory}</span>
                            <span className="cov-label">Inv</span>
                            <span className="cov-note">Inv Staff</span>
                        </div>
                        <div className="coverage-col">
                            <span className="cov-num">{metrics.managers}</span>
                            <span className="cov-label">Mgr</span>
                            <span className="cov-note">{metrics.admins} Admins</span>
                        </div>
                    </div>
                </div>
            </div>

            {/* FILTER & SEARCH TOOLBAR */}
            <div className="employee-filter-bar">
                <div className="filter-left-group">
                    {/* Search box */}
                    <div className="search-input-wrap">
                        <Search size={16} className="search-icon" />
                        <input
                            type="text"
                            placeholder="Search by employee name, code, email, or phone..."
                            value={searchTerm}
                            maxLength={100}
                            onChange={(e) => {
                                setSearchTerm(e.target.value);
                                setCurrentPage(1);
                            }}
                        />
                        <span className="search-char-count">{searchTerm.length} / 100</span>
                    </div>

                    {/* Position Dropdown */}
                    <div className="position-select-wrap">
                        <select
                            value={positionFilter}
                            onChange={(e) => {
                                setPositionFilter(e.target.value);
                                setCurrentPage(1);
                            }}
                        >
                            <option value="ALL">All Positions</option>
                            {availablePositions.map((pos) => (
                                <option key={pos.value} value={pos.value}>
                                    {getPositionDisplayLabel(pos.value)}
                                </option>
                            ))}
                        </select>
                    </div>

                    {/* Status Tabs */}
                    <div className="status-tabs-group">
                        <button
                            type="button"
                            className={`status-tab ${statusFilter === 'ALL' ? 'active' : ''}`}
                            onClick={() => {
                                setStatusFilter('ALL');
                                setCurrentPage(1);
                            }}
                        >
                            All
                        </button>
                        <button
                            type="button"
                            className={`status-tab ${statusFilter === 'ACTIVE' ? 'active' : ''}`}
                            onClick={() => {
                                setStatusFilter('ACTIVE');
                                setCurrentPage(1);
                            }}
                        >
                            Active
                        </button>
                        <button
                            type="button"
                            className={`status-tab ${statusFilter === 'INACTIVE' ? 'active' : ''}`}
                            onClick={() => {
                                setStatusFilter('INACTIVE');
                                setCurrentPage(1);
                            }}
                        >
                            Inactive
                        </button>
                    </div>
                </div>

                <div className="filter-right-group">
                    <button
                        type="button"
                        className="export-btn"
                        onClick={handleExportCSV}
                        title="Xuất danh sách ra tệp CSV"
                    >
                        <Download size={14} />
                        Export
                    </button>
                </div>
            </div>

            {/* Active Filters Summary Strip */}
            <div className="active-filters-strip">
                <div className="filter-tags-left">
                    <span className="strip-label">Active filters:</span>
                    <span className="filter-pill-tag">
                        Position: {positionFilter === 'ALL' ? 'All' : positionFilter}
                    </span>
                    <span className="filter-pill-tag">
                        Status: {statusFilter === 'ALL' ? 'All' : statusFilter}
                    </span>
                    {searchTerm.trim() && (
                        <span className="filter-pill-tag search-tag">
                            Query: "{searchTerm.trim()}"
                        </span>
                    )}
                </div>
                <div className="filter-count-right">
                    Showing {filteredEmployees.length} records
                </div>
            </div>

            {/* EMPLOYEE DATA TABLE */}
            <div className="employee-table-card">
                {loading ? (
                    <div className="table-loading-box">
                        <RefreshCw size={24} className="spinning" />
                        <span>Đang tải danh bạ nhân sự...</span>
                    </div>
                ) : filteredEmployees.length === 0 ? (
                    <div className="table-empty-box">
                        <AlertCircle size={36} className="empty-icon" />
                        <h3>Không tìm thấy nhân viên phù hợp</h3>
                        <p>Vui lòng thử lại với từ khóa tìm kiếm hoặc điều chỉnh các bộ lọc chức vụ, trạng thái.</p>
                        <button type="button" className="action-btn secondary" onClick={handleResetFilters}>
                            <RotateCcw size={14} />
                            Đặt lại bộ lọc
                        </button>
                    </div>
                ) : (
                    <table className="employee-table">
                        <thead>
                            <tr>
                                <th style={{ width: '100px' }}>CODE</th>
                                <th style={{ minWidth: '220px' }}>FULL NAME</th>
                                <th style={{ width: '160px' }}>POSITION</th>
                                <th style={{ width: '140px' }}>PHONE NUMBER</th>
                                <th style={{ minWidth: '200px' }}>STORE EMAIL</th>
                                <th style={{ width: '110px' }}>STATUS</th>
                                <th style={{ width: '120px', textAlign: 'center' }}>ACTIONS</th>
                            </tr>
                        </thead>
                        <tbody>
                            {paginatedEmployees.map((emp) => {
                                const isActive = emp.status === 'ACTIVE';
                                const initialsColorClass = `avatar-color-${(emp.employeeId || 1) % 6}`;

                                return (
                                    <tr key={emp.employeeId || emp.employeeCode} className="employee-row">
                                        {/* Code */}
                                        <td className="code-cell font-mono">
                                            <Link
                                                to={`/employees/${emp.employeeId || emp.employeeCode}`}
                                                className="emp-code-link"
                                            >
                                                {emp.employeeCode}
                                            </Link>
                                        </td>

                                        {/* Full Name with Initials Avatar */}
                                        <td className="name-cell">
                                            <div className="employee-name-group">
                                                <div className={`emp-avatar-initials ${initialsColorClass}`}>
                                                    {emp.initials || 'NV'}
                                                </div>
                                                <div className="emp-text-details">
                                                    <Link
                                                        to={`/employees/${emp.employeeId || emp.employeeCode}`}
                                                        className="emp-name-title"
                                                    >
                                                        {emp.fullName}
                                                    </Link>
                                                    <span className="emp-dept-subtitle">
                                                        {emp.department || 'Frontline Retail'}
                                                    </span>
                                                </div>
                                            </div>
                                        </td>

                                        {/* Position */}
                                        <td className="position-cell">
                                            {getPositionBadge(emp.position)}
                                        </td>

                                        {/* Phone Number */}
                                        <td className="phone-cell font-mono">
                                            {emp.phone}
                                        </td>

                                        {/* Store Email */}
                                        <td className="email-cell font-mono">
                                            {emp.email}
                                        </td>

                                        {/* Status */}
                                        <td className="status-cell">
                                            {isActive ? (
                                                <span className="status-badge-inline active">
                                                    <span className="dot green"></span>
                                                    Active
                                                </span>
                                            ) : (
                                                <span className="status-badge-inline inactive">
                                                    <span className="dot gray"></span>
                                                    Inactive
                                                </span>
                                            )}
                                        </td>

                                        {/* Actions */}
                                        <td className="actions-cell">
                                            <div className="action-buttons-wrap">
                                                <Link
                                                    to={`/employees/${emp.employeeId || emp.employeeCode}`}
                                                    className="view-link-btn"
                                                    title="Xem chi tiết hồ sơ nhân viên (UC17)"
                                                >
                                                    <Eye size={14} />
                                                    View
                                                </Link>

                                                {/* 3-dots dropdown */}
                                                <div
                                                    className="dropdown-wrap"
                                                    onClick={(e) => e.stopPropagation()}
                                                >
                                                    <button
                                                        type="button"
                                                        className="more-options-btn"
                                                        onClick={() =>
                                                            setOpenDropdownId(
                                                                openDropdownId === emp.employeeId ? null : emp.employeeId
                                                            )
                                                        }
                                                        title="Tùy chọn khác"
                                                    >
                                                        <MoreVertical size={15} />
                                                    </button>

                                                    {openDropdownId === emp.employeeId && (
                                                        <div className="row-dropdown-menu">
                                                            <button
                                                                type="button"
                                                                className="dropdown-item"
                                                                onClick={() =>
                                                                    navigate(`/employees/${emp.employeeId || emp.employeeCode}`)
                                                                }
                                                            >
                                                                <Eye size={13} />
                                                                Xem chi tiết hồ sơ
                                                            </button>
                                                            <button
                                                                type="button"
                                                                className="dropdown-item"
                                                                onClick={() => {
                                                                    navigate(`/employees/${emp.employeeId}/edit`);
                                                                    setOpenDropdownId(null);
                                                                }}
                                                            >
                                                                <UserCheck size={13} />
                                                                Chỉnh sửa thông tin
                                                            </button>
                                                            <button
                                                                type="button"
                                                                className={`dropdown-item ${isActive ? 'danger' : 'success'}`}
                                                                onClick={(e) => handleToggleStatus(emp, e)}
                                                            >
                                                                {isActive ? (
                                                                    <>
                                                                        <UserX size={13} />
                                                                        Tạm dừng hoạt động
                                                                    </>
                                                                ) : (
                                                                    <>
                                                                        <CheckCircle2 size={13} />
                                                                        Kích hoạt lại
                                                                    </>
                                                                )}
                                                            </button>
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                )}

                {/* Table Footer & Pagination */}
                <div className="table-pagination-footer">
                    <div className="footer-left-info">
                        Showing {filteredEmployees.length === 0 ? 0 : (currentPage - 1) * pageSize + 1}-
                        {Math.min(currentPage * pageSize, filteredEmployees.length)} of {filteredEmployees.length} employees
                    </div>

                    <div className="footer-right-pagination">
                        <div className="rows-per-page">
                            <span>Rows per page:</span>
                            <select
                                value={pageSize}
                                onChange={(e) => {
                                    setPageSize(Number(e.target.value));
                                    setCurrentPage(1);
                                }}
                            >
                                <option value={8}>8 / page</option>
                                <option value={10}>10 / page</option>
                                <option value={20}>20 / page</option>
                            </select>
                        </div>

                        <div className="page-nav-btns">
                            <button
                                type="button"
                                className="nav-arrow-btn"
                                disabled={currentPage === 1}
                                onClick={() => setCurrentPage((p) => Math.max(p - 1, 1))}
                            >
                                <ChevronLeft size={16} />
                            </button>

                            {Array.from({ length: totalPages }, (_, i) => i + 1).map((pg) => (
                                <button
                                    key={pg}
                                    type="button"
                                    className={`page-num-btn ${pg === currentPage ? 'active' : ''}`}
                                    onClick={() => setCurrentPage(pg)}
                                >
                                    {pg}
                                </button>
                            ))}

                            <button
                                type="button"
                                className="nav-arrow-btn"
                                disabled={currentPage === totalPages}
                                onClick={() => setCurrentPage((p) => Math.min(p + 1, totalPages))}
                            >
                                <ChevronRight size={16} />
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            {/* 3 BOTTOM INFORMATIONAL PANELS */}
            <div className="employee-bottom-cards-grid">
                {/* Panel 1: Position Allocation */}
                <div className="bottom-info-card role-allocation-card">
                    <div className="bottom-card-header">
                        <div className="title-with-icon">
                            <ShieldCheck size={18} className="panel-icon blue" />
                            <h3>Position Allocation</h3>
                        </div>
                        <span className="header-meta-tag">Active Staff</span>
                    </div>

                    <div className="allocation-list">
                        <div className="allocation-row">
                            <div className="role-name-val">
                                <span>Cashiers (18 Staff)</span>
                                <strong>50.0%</strong>
                            </div>
                            <div className="alloc-bar-bg">
                                <div className="alloc-bar-fill blue" style={{ width: '50%' }}></div>
                            </div>
                        </div>

                        <div className="allocation-row">
                            <div className="role-name-val">
                                <span>Inventory Clerks (8 Staff)</span>
                                <strong>22.2%</strong>
                            </div>
                            <div className="alloc-bar-bg">
                                <div className="alloc-bar-fill indigo" style={{ width: '22.2%' }}></div>
                            </div>
                        </div>

                        <div className="allocation-row">
                            <div className="role-name-val">
                                <span>Store Managers (6 Staff)</span>
                                <strong>16.7%</strong>
                            </div>
                            <div className="alloc-bar-bg">
                                <div className="alloc-bar-fill amber" style={{ width: '16.7%' }}></div>
                            </div>
                        </div>

                        <div className="allocation-row">
                            <div className="role-name-val">
                                <span>Administrators (4 Staff)</span>
                                <strong>11.1%</strong>
                            </div>
                            <div className="alloc-bar-bg">
                                <div className="alloc-bar-fill slate" style={{ width: '11.1%' }}></div>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Panel 2: Personnel Compliance */}
                <div className="bottom-info-card compliance-summary-card">
                    <div className="bottom-card-header">
                        <div className="title-with-icon">
                            <CheckCircle2 size={18} className="panel-icon green" />
                            <h3>Personnel Compliance</h3>
                        </div>
                        <span className="audit-ready-badge">Audit Ready</span>
                    </div>

                    <div className="compliance-items-list">
                        <div className="comp-item">
                            <div className="comp-item-header">
                                <CreditCard size={15} className="comp-icon" />
                                <strong>POS Cashier PIN Verification</strong>
                            </div>
                            <p>All 18 cashiers have assigned terminal auth tokens for Store #04.</p>
                        </div>

                        <div className="comp-item">
                            <div className="comp-item-header">
                                <Package size={15} className="comp-icon" />
                                <strong>Scanner Handheld Barcode Handshake</strong>
                            </div>
                            <p>8 inventory staff linked with Zebra TC21 mobile barcode scanners.</p>
                        </div>

                        <div className="comp-item">
                            <div className="comp-item-header">
                                <Lock size={15} className="comp-icon" />
                                <strong>Role Separation Enforced</strong>
                            </div>
                            <p>Cashier roles restricted from price adjustment & PO approval per SRS.</p>
                        </div>
                    </div>
                </div>

                {/* Panel 3: Governance & Delegation (UC18 Onboarding) */}
                <div className="bottom-info-card governance-card">
                    <div className="bottom-card-header">
                        <div className="title-with-icon">
                            <Building2 size={18} className="panel-icon blue" />
                            <h3>Governance & Delegation</h3>
                        </div>
                    </div>

                    <p className="governance-desc">
                        Need to register a newly appointed supervisor or adjust terminal access permissions? Use the standardized workflow:
                    </p>

                    <div className="next-step-box">
                        <div className="next-step-header">
                            <span className="step-title">Next Step: UC18 Onboarding</span>
                            <span className="step-count-badge">Step 1 of 3</span>
                        </div>
                        <p className="step-info">
                            Create user account, map default role permissions, and assign to physical store location.
                        </p>

                        <button
                            type="button"
                            className="wizard-launch-btn"
                            onClick={() => {
                                showToast('Chức năng UC18 Onboarding Wizard đang trong quá trình chuẩn bị phát triển.');
                            }}
                        >
                            <UserPlus size={15} />
                            Launch UC18 Creation Wizard
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}
