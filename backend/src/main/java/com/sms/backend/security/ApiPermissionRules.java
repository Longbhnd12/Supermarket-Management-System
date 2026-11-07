package com.sms.backend.security;

import com.sms.backend.enums.Permission;
import com.sms.backend.enums.RoleName;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.sms.backend.enums.Permission.*;

/**
 * Authorization table of the whole REST API, in one place (SRS 3.1.3 Screen Authorization + UC06).
 *
 * <p>Each endpoint is mapped to the permission(s) that allow it. A role gets its permissions from
 * {@code role.permissions} (UC06, see {@link RolePermissions}), so when the Admin changes a role
 * the API follows from the next request.
 *
 * <p>Rules are read from top to bottom and the FIRST rule that matches decides (same as Spring Security),
 * so a specific path must come before a general one. A request that matches no rule is DENIED
 * (deny by default): a new endpoint must be added here, {@code ApiPermissionRulesTest} fails otherwise.
 *
 * <p>Staff API rules also require a staff role: an Admin could give any permission to the Supplier role
 * in UC06, but a Supplier account must never reach the staff API (UC48 BR-01).
 *
 * <p>Applied by {@link SecurityConfig}; the full table is in backend/API-PERMISSIONS.md.
 */
public final class ApiPermissionRules {

    public enum Access {
        /** No login (login page, forgot password, VNPay server callback...). */
        PUBLIC,
        /** Any logged-in account, Supplier included. */
        AUTHENTICATED,
        /** Logged in with one of {@link Rule#roles()} and at least one of {@link Rule#anyOf()}. */
        PERMISSION
    }

    /**
     * One line of the table.
     *
     * @param method  HTTP method, or {@code null} for every method
     * @param pattern Spring path pattern, e.g. {@code /api/v1/products/**}
     * @param roles   roles allowed (empty = any role)
     * @param anyOf   the user needs at least ONE of these permissions (empty = no permission needed)
     */
    public record Rule(HttpMethod method, String pattern, Access access, Set<RoleName> roles, Set<Permission> anyOf) {

        public Rule {
            roles = roles.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(roles));
            anyOf = anyOf.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(anyOf));
        }

        public boolean matches(HttpMethod requestMethod, String path) {
            return (method == null || method.equals(requestMethod))
                    && parse(pattern).matches(PathContainer.parsePath(path));
        }

        /** Spring authorities: "ROLE_ADMIN", ... */
        public String[] roleAuthorities() {
            return roles.stream().map(role -> "ROLE_" + role.name()).toArray(String[]::new);
        }

        /** Spring authorities: "PERM_USER_VIEW", ... */
        public String[] permissionAuthorities() {
            return anyOf.stream().map(permission -> "PERM_" + permission.name()).toArray(String[]::new);
        }

        public boolean allows(RoleName role, Set<Permission> permissions) {
            return switch (access) {
                case PUBLIC -> true;
                case AUTHENTICATED -> role != null;
                case PERMISSION -> role != null
                        && (roles.isEmpty() || roles.contains(role))
                        && (anyOf.isEmpty() || anyOf.stream().anyMatch(permissions::contains));
            };
        }

        @Override
        public String toString() {
            return (method == null ? "ANY" : method.name()) + " " + pattern + " -> " + access
                    + (roles.isEmpty() ? "" : " roles=" + roles) + (anyOf.isEmpty() ? "" : " anyOf=" + anyOf);
        }
    }

    /** Admin, Manager, Cashier, Inventory Staff. */
    public static final Set<RoleName> STAFF = Collections.unmodifiableSet(EnumSet.of(
            RoleName.ADMIN, RoleName.MANAGER, RoleName.CASHIER, RoleName.WAREHOUSE_STAFF));

    /** UC22/UC23 BR: invoices are for Admin, Manager and Cashier (with INVOICE_VIEW). */
    private static final Set<RoleName> INVOICE_ROLES = EnumSet.of(RoleName.ADMIN, RoleName.MANAGER, RoleName.CASHIER);

    private static final HttpMethod GET = HttpMethod.GET;
    private static final HttpMethod POST = HttpMethod.POST;
    private static final HttpMethod PATCH = HttpMethod.PATCH;
    private static final HttpMethod PUT = HttpMethod.PUT;
    private static final HttpMethod DELETE = HttpMethod.DELETE;
    private static final HttpMethod ANY = null;

    public static final List<Rule> RULES = List.of(
            // ---- Public -------------------------------------------------------------------------
            open(GET, "/api/v1/health"),
            open(GET, "/api/v1/test-error"),
            open(ANY, "/error"),
            open(POST, "/api/v1/auth/login"),
            open(POST, "/api/v1/auth/register"),
            open(POST, "/api/v1/auth/forgot-password"),
            open(POST, "/api/v1/auth/reset-password"),
            // Logout works even with an expired token: it only clears the cookie
            open(POST, "/api/v1/auth/logout"),
            // VNPay server-to-server callback: no cookie; PaymentService checks the VNPay signature
            open(GET, "/api/v1/payments/vnpay/ipn"),

            // ---- Any logged-in account (UC03, UC44) ---------------------------------------------
            loggedIn(GET, "/api/v1/auth/me"),
            loggedIn(POST, "/api/v1/auth/change-password"),
            loggedIn(ANY, "/api/v1/profile"),

            // ---- Administration (UC04-UC06, UC07, UC08) -----------------------------------------
            // GET /users (plain list): the POS screen loads the cashiers -> any staff
            staff(GET, "/api/v1/users"),
            staff(GET, "/api/v1/users/**", USER_VIEW),
            staff(ANY, "/api/v1/users/**", USER_MANAGE),
            staff(ANY, "/api/v1/roles/**", ROLE_MANAGE),
            staff(ANY, "/api/v1/system-settings/**", SYSTEM_SETTINGS_MANAGE),
            staff(GET, "/api/v1/system-logs/**", SYSTEM_LOG_VIEW),

            // ---- Dashboard & notifications (UC42, UC43) -----------------------------------------
            staff(GET, "/api/v1/dashboard", DASHBOARD_VIEW),
            anyRole(ANY, "/api/v1/notifications/**", NOTIFICATION_VIEW), // Supplier too

            // ---- Products, categories, promotions -----------------------------------------------
            staff(GET, "/api/v1/products/**", PRODUCT_VIEW),
            staff(PATCH, "/api/v1/products/*/price", PRICE_MANAGE, PRODUCT_MANAGE),
            staff(ANY, "/api/v1/products/**", PRODUCT_MANAGE),
            staff(GET, "/api/v1/categories/**", PRODUCT_VIEW),
            staff(ANY, "/api/v1/categories/**", CATEGORY_MANAGE),
            staff(GET, "/api/v1/promotions/**", PROMOTION_MANAGE, PROMOTION_VIEW_ACTIVE),
            staff(ANY, "/api/v1/promotions/**", PROMOTION_MANAGE),

            // ---- Employees & suppliers ----------------------------------------------------------
            staff(GET, "/api/v1/employees/**", EMPLOYEE_VIEW),
            staff(ANY, "/api/v1/employees/**", EMPLOYEE_MANAGE),
            staff(GET, "/api/v1/suppliers/**", SUPPLIER_VIEW),
            staff(HttpMethod.DELETE, "/api/v1/suppliers/**", SUPPLIER_DISABLE),
            staff(ANY, "/api/v1/suppliers/**", SUPPLIER_MANAGE),

            // ---- Sales, POS, invoices, payments -------------------------------------------------
            invoice("/api/v1/sales/*/invoice"),
            invoice("/api/v1/sales/*/invoice/pdf"),
            invoice("/api/v1/invoices/**"),
            staff(GET, "/api/v1/sales/products", POS_SALE),
            staff(GET, "/api/v1/sales/**", SALE_VIEW, POS_SALE),
            staff(ANY, "/api/v1/sales/**", POS_SALE),
            staff(GET, "/api/v1/payments/reference/*", POS_SALE, SALE_VIEW),
            staff(GET, "/api/v1/payments/sales/*", POS_SALE, SALE_VIEW),
            staff(ANY, "/api/v1/payments/**", POS_SALE),

            // ---- Inventory -----------------------------------------------------------------------
            staff(GET, "/api/v1/inventory", INVENTORY_VIEW),
            staff(GET, "/api/v1/inventory/export", INVENTORY_VIEW, REPORT_INVENTORY),
            staff(POST, "/api/v1/inventory/adjust", STOCK_ADJUST),
            // InventoryController answers on both paths: /history and /stock-history, /alerts and /stock-alerts
            staff(GET, "/api/v1/inventory/history", STOCK_HISTORY_VIEW),
            staff(GET, "/api/v1/inventory/stock-history", STOCK_HISTORY_VIEW),
            staff(GET, "/api/v1/inventory/movements", STOCK_HISTORY_VIEW),
            staff(GET, "/api/v1/inventory/alerts", STOCK_ALERT_VIEW),
            staff(GET, "/api/v1/inventory/stock-alerts", STOCK_ALERT_VIEW),
            staff(GET, "/api/v1/inventory/receipts/**", STOCK_IMPORT, INVENTORY_VIEW),
            staff(ANY, "/api/v1/inventory/receipts/**", STOCK_IMPORT),
            staff(GET, "/api/v1/inventory/checks/**", STOCK_CHECK, INVENTORY_VIEW),
            staff(ANY, "/api/v1/inventory/checks/**", STOCK_CHECK),
            staff(GET, "/api/v1/stock-receipts/next-number", STOCK_IMPORT),
            staff(GET, "/api/v1/stock-receipts/**", STOCK_IMPORT, INVENTORY_VIEW),
            staff(ANY, "/api/v1/stock-receipts/**", STOCK_IMPORT),

            // ---- Purchase orders (UC46, UC47, UC50) ---------------------------------------------
            staff(GET, "/api/v1/purchase-orders/pending", PURCHASE_REQUEST_APPROVE),
            staff(GET, "/api/v1/purchase-orders/next-number", PURCHASE_REQUEST_CREATE),
            staff(GET, "/api/v1/purchase-orders/low-stock-suggestions", PURCHASE_REQUEST_CREATE),
            staff(GET, "/api/v1/purchase-orders/**", PURCHASE_ORDER_VIEW, PURCHASE_REQUEST_CREATE, PURCHASE_REQUEST_APPROVE),
            staff(POST, "/api/v1/purchase-orders", PURCHASE_REQUEST_CREATE),
            staff(POST, "/api/v1/purchase-orders/requests", PURCHASE_REQUEST_CREATE),
            staff(POST, "/api/v1/purchase-orders/*/approve", PURCHASE_REQUEST_APPROVE),
            staff(POST, "/api/v1/purchase-orders/*/reject", PURCHASE_REQUEST_APPROVE),
            // UC49 follow-up: Manager/Admin accept or reject the Supplier's proposal (SupplierProposalReviewController)
            staff(POST, "/api/v1/purchase-orders/*/supplier-proposal/review", PURCHASE_REQUEST_APPROVE),
            staff(POST, "/api/v1/purchase-orders/*/cancel", PURCHASE_REQUEST_CREATE, PURCHASE_REQUEST_APPROVE),

            // ---- Supplier portal (UC48, UC49) ---------------------------------------------------
            anyRole(ANY, "/api/v1/supplier-portal/**", SUPPLIER_PORTAL),

            // ---- Reports (UC38-UC41) ------------------------------------------------------------
            staff(GET, "/api/v1/reports/sales", REPORT_SALES),
            staff(GET, "/api/v1/reports/revenue", REPORT_REVENUE),
            staff(GET, "/api/v1/reports/product-sales", REPORT_PRODUCT_SALES),
            staff(GET, "/api/v1/reports/inventory", REPORT_INVENTORY)
    );

    private ApiPermissionRules() {
    }

    /** The rule Spring Security will apply (first match), or empty when the request is denied by default. */
    public static Optional<Rule> find(HttpMethod method, String path) {
        return RULES.stream().filter(rule -> rule.matches(method, path)).findFirst();
    }

    /** Can an account with this role and these permissions call the endpoint? ({@code role == null}: not logged in) */
    public static boolean isAllowed(RoleName role, Set<Permission> permissions, HttpMethod method, String path) {
        return find(method, path).map(rule -> rule.allows(role, permissions)).orElse(false);
    }

    private static PathPattern parse(String pattern) {
        return PathPatternParser.defaultInstance.parse(pattern);
    }

    private static Rule open(HttpMethod method, String pattern) {
        return new Rule(method, pattern, Access.PUBLIC, Set.of(), Set.of());
    }

    private static Rule loggedIn(HttpMethod method, String pattern) {
        return new Rule(method, pattern, Access.AUTHENTICATED, Set.of(), Set.of());
    }

    private static Rule staff(HttpMethod method, String pattern, Permission... anyOf) {
        return new Rule(method, pattern, Access.PERMISSION, STAFF, toSet(anyOf));
    }

    private static Rule anyRole(HttpMethod method, String pattern, Permission... anyOf) {
        return new Rule(method, pattern, Access.PERMISSION, Set.of(), toSet(anyOf));
    }

    private static Rule invoice(String pattern) {
        return new Rule(GET, pattern, Access.PERMISSION, INVOICE_ROLES, Set.of(INVOICE_VIEW));
    }

    private static Set<Permission> toSet(Permission... permissions) {
        return permissions.length == 0 ? Set.of() : EnumSet.copyOf(Arrays.asList(permissions));
    }
}
