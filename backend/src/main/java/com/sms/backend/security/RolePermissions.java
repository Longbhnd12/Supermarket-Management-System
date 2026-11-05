package com.sms.backend.security;

import com.sms.backend.entity.Role;
import com.sms.backend.enums.Permission;
import com.sms.backend.enums.RoleName;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.sms.backend.enums.Permission.*;

/**
 * UC06 - Manage Roles & Permissions: reads and writes the role.permissions column.
 * <ul>
 *     <li>Format in the database: comma-separated permission codes.</li>
 *     <li>Empty / never configured -> the default set of the role (SRS 3.1.3).</li>
 *     <li>BR-03: Admin always keeps the permissions needed for system administration.</li>
 *     <li>Each permission becomes a Spring Security authority "PERM_&lt;CODE&gt;".</li>
 * </ul>
 */
public final class RolePermissions {

    public static final String AUTHORITY_PREFIX = "PERM_";

    /** BR-03: permissions the Admin role can never lose. */
    public static final Set<Permission> ADMIN_REQUIRED = Collections.unmodifiableSet(EnumSet.of(
            USER_VIEW, USER_MANAGE, ROLE_MANAGE, SYSTEM_SETTINGS_MANAGE, SYSTEM_LOG_VIEW));

    private static final Map<RoleName, Set<Permission>> DEFAULTS = Map.of(
            RoleName.ADMIN, EnumSet.allOf(Permission.class),
            RoleName.MANAGER, EnumSet.of(
                    DASHBOARD_VIEW, NOTIFICATION_VIEW,
                    PRODUCT_VIEW, PRODUCT_MANAGE, CATEGORY_MANAGE, PRICE_MANAGE, PROMOTION_MANAGE, PROMOTION_VIEW_ACTIVE,
                    EMPLOYEE_VIEW, EMPLOYEE_MANAGE, SUPPLIER_VIEW, SUPPLIER_MANAGE, SUPPLIER_DISABLE,
                    SALE_VIEW, INVOICE_VIEW,
                    INVENTORY_VIEW, STOCK_HISTORY_VIEW, STOCK_ALERT_VIEW,
                    PURCHASE_REQUEST_APPROVE, PURCHASE_ORDER_VIEW,
                    REPORT_SALES, REPORT_REVENUE, REPORT_PRODUCT_SALES, REPORT_INVENTORY),
            RoleName.CASHIER, EnumSet.of(
                    DASHBOARD_VIEW, NOTIFICATION_VIEW,
                    PRODUCT_VIEW, PROMOTION_VIEW_ACTIVE,
                    SUPPLIER_VIEW, SUPPLIER_MANAGE,
                    INVOICE_VIEW, POS_SALE),
            RoleName.WAREHOUSE_STAFF, EnumSet.of(
                    DASHBOARD_VIEW, NOTIFICATION_VIEW,
                    PRODUCT_VIEW, SUPPLIER_VIEW, SUPPLIER_MANAGE,
                    INVENTORY_VIEW, STOCK_CHECK, STOCK_ADJUST, STOCK_HISTORY_VIEW, STOCK_ALERT_VIEW, STOCK_IMPORT,
                    PURCHASE_REQUEST_CREATE, PURCHASE_ORDER_VIEW,
                    REPORT_INVENTORY),
            RoleName.SUPPLIER, EnumSet.of(SUPPLIER_PORTAL, NOTIFICATION_VIEW)
    );

    private RolePermissions() {
    }

    public static Set<Permission> defaultsFor(RoleName roleName) {
        Set<Permission> defaults = roleName == null ? null : DEFAULTS.get(roleName);
        return defaults == null ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(defaults);
    }

    public static Set<Permission> requiredFor(RoleName roleName) {
        return roleName == RoleName.ADMIN ? EnumSet.copyOf(ADMIN_REQUIRED) : EnumSet.noneOf(Permission.class);
    }

    /** Permissions currently granted to the role (defaults when the column is empty). */
    public static Set<Permission> of(Role role) {
        if (role == null) {
            return EnumSet.noneOf(Permission.class);
        }
        Set<Permission> parsed = parse(role.getPermissions());
        Set<Permission> result = parsed.isEmpty() ? defaultsFor(role.getRoleName()) : parsed;
        result.addAll(requiredFor(role.getRoleName()));
        return result;
    }

    /** Unknown codes (old data, typos) are ignored. */
    public static Set<Permission> parse(String stored) {
        Set<Permission> result = EnumSet.noneOf(Permission.class);
        if (stored == null || stored.isBlank()) {
            return result;
        }
        Arrays.stream(stored.split("[,;\\s\\[\\]\"]+"))
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .filter(code -> !code.isEmpty())
                .forEach(code -> {
                    try {
                        result.add(Permission.valueOf(code));
                    } catch (IllegalArgumentException ignored) {
                        // not a known permission code
                    }
                });
        return result;
    }

    public static String format(Set<Permission> permissions) {
        return permissions.stream()
                .sorted()
                .map(Permission::name)
                .collect(Collectors.joining(","));
    }

    public static String authority(Permission permission) {
        return AUTHORITY_PREFIX + permission.name();
    }
}
