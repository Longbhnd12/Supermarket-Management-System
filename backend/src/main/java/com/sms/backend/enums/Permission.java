package com.sms.backend.enums;

/**
 * UC06 - Manage Roles & Permissions: catalog of permissions an Admin can assign to a role.
 * Based on the Screen Authorization table (SRS 3.1.3).
 * Stored in role.permissions as comma-separated codes, e.g. "PRODUCT_VIEW,PRODUCT_MANAGE".
 */
public enum Permission {

    // Administration
    USER_VIEW("Administration", "View users"),
    USER_MANAGE("Administration", "Create, update, deactivate and delete users"),
    ROLE_MANAGE("Administration", "Manage roles & permissions"),
    SYSTEM_SETTINGS_MANAGE("Administration", "Manage system configuration"),
    SYSTEM_LOG_VIEW("Administration", "View system logs"),

    // Dashboard & notifications
    DASHBOARD_VIEW("Dashboard & Notifications", "View dashboard"),
    NOTIFICATION_VIEW("Dashboard & Notifications", "View notifications"),

    // Product & promotion
    PRODUCT_VIEW("Product & Promotion", "View products and product details"),
    PRODUCT_MANAGE("Product & Promotion", "Create, update and deactivate products"),
    CATEGORY_MANAGE("Product & Promotion", "Manage categories"),
    PRICE_MANAGE("Product & Promotion", "Update selling prices"),
    PROMOTION_MANAGE("Product & Promotion", "Create and manage promotions"),
    PROMOTION_VIEW_ACTIVE("Product & Promotion", "View active promotions"),

    // Employee & supplier
    EMPLOYEE_VIEW("Employee & Supplier", "View employees"),
    EMPLOYEE_MANAGE("Employee & Supplier", "Create, update and deactivate employees"),
    SUPPLIER_VIEW("Employee & Supplier", "View suppliers"),
    SUPPLIER_MANAGE("Employee & Supplier", "Create and update suppliers"),
    SUPPLIER_DISABLE("Employee & Supplier", "Disable / enable suppliers"),

    // Sales & POS
    SALE_VIEW("Sales & POS", "View sales transactions"),
    INVOICE_VIEW("Sales & POS", "Search invoices and view invoice details"),
    POS_SALE("Sales & POS", "Create sales, take payments and issue invoices"),

    // Inventory
    INVENTORY_VIEW("Inventory", "View inventory"),
    STOCK_CHECK("Inventory", "Perform stock checks"),
    STOCK_ADJUST("Inventory", "Adjust stock"),
    STOCK_HISTORY_VIEW("Inventory", "View stock history"),
    STOCK_ALERT_VIEW("Inventory", "View stock alerts"),
    STOCK_IMPORT("Inventory", "Import stock and manage stock receipts"),

    // Purchase orders
    PURCHASE_REQUEST_CREATE("Purchase Orders", "Create purchase requests"),
    PURCHASE_REQUEST_APPROVE("Purchase Orders", "Approve / reject purchase requests"),
    PURCHASE_ORDER_VIEW("Purchase Orders", "View purchase order status"),
    SUPPLIER_PORTAL("Purchase Orders", "Supplier portal: view and respond to purchase orders"),

    // Reports
    REPORT_SALES("Reports", "View sales report"),
    REPORT_REVENUE("Reports", "View revenue report"),
    REPORT_PRODUCT_SALES("Reports", "View product sales report"),
    REPORT_INVENTORY("Reports", "View inventory report");

    private final String group;
    private final String label;

    Permission(String group, String label) {
        this.group = group;
        this.label = label;
    }

    public String getGroup() {
        return group;
    }

    public String getLabel() {
        return label;
    }
}
