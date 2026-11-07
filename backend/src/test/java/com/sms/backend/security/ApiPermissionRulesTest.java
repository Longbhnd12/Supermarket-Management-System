package com.sms.backend.security;

import com.sms.backend.enums.Permission;
import com.sms.backend.enums.RoleName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The authorization table (ApiPermissionRules) against the real controllers and the default
 * permissions of each role (RolePermissions = SRS 3.1.3 Screen Authorization).
 */
class ApiPermissionRulesTest {

    private static final List<ControllerEndpoints.Endpoint> ENDPOINTS = ControllerEndpoints.scan();

    @Test
    void allControllersAreScanned() {
        assertTrue(ENDPOINTS.size() >= 125, "Only " + ENDPOINTS.size() + " endpoints found");
    }

    /** Deny by default: an endpoint without a rule answers 403 for everybody. */
    @Test
    void everyEndpointHasARule() {
        List<String> missing = ENDPOINTS.stream()
                .filter(endpoint -> ApiPermissionRules.find(endpoint.method(), endpoint.samplePath()).isEmpty())
                .map(ControllerEndpoints.Endpoint::toString)
                .toList();
        assertTrue(missing.isEmpty(), "Add a rule in ApiPermissionRules for: " + missing);
    }

    @Test
    void adminCanCallEveryEndpoint() {
        Set<Permission> admin = RolePermissions.defaultsFor(RoleName.ADMIN);
        List<String> denied = ENDPOINTS.stream()
                .filter(endpoint -> !ApiPermissionRules.isAllowed(RoleName.ADMIN, admin, endpoint.method(), endpoint.samplePath()))
                .map(ControllerEndpoints.Endpoint::toString)
                .toList();
        assertTrue(denied.isEmpty(), "Admin is denied: " + denied);
    }

    @Test
    void unknownEndpointIsDenied() {
        Set<Permission> all = EnumSet.allOf(Permission.class);
        assertTrue(ApiPermissionRules.find(HttpMethod.GET, "/api/v1/not-in-the-table").isEmpty());
        assertFalse(ApiPermissionRules.isAllowed(RoleName.ADMIN, all, HttpMethod.GET, "/api/v1/not-in-the-table"));
    }

    /** Role (default permissions of SRS 3.1.3) | method | path | allowed. ANONYMOUS = not logged in. */
    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @CsvSource({
            // Not logged in
            "ANONYMOUS, POST, /api/v1/auth/login, true",
            "ANONYMOUS, POST, /api/v1/auth/forgot-password, true",
            "ANONYMOUS, POST, /api/v1/auth/logout, true",
            "ANONYMOUS, POST, /api/v1/auth/register, false", // removed: no self-registration
            "ANONYMOUS, GET, /api/v1/payments/vnpay/ipn, true",
            "ANONYMOUS, GET, /api/v1/auth/me, false",
            "ANONYMOUS, GET, /api/v1/products, false",
            // Cashier: POS, invoices, read products
            "CASHIER, GET, /api/v1/products, true",
            "CASHIER, GET, /api/v1/products/barcode/893001, true",
            "CASHIER, POST, /api/v1/products, false",
            "CASHIER, POST, /api/v1/sales, true",
            "CASHIER, POST, /api/v1/sales/5/complete, true",
            "CASHIER, GET, /api/v1/sales/products, true",
            "CASHIER, POST, /api/v1/payments/sales/5, true",
            "CASHIER, POST, /api/v1/payments/9/reconcile, true",
            "CASHIER, GET, /api/v1/invoices, true",
            "CASHIER, GET, /api/v1/sales/5/invoice/pdf, true",
            "CASHIER, GET, /api/v1/users, true",
            "CASHIER, GET, /api/v1/users/search, false",
            "CASHIER, GET, /api/v1/purchase-orders, false",
            "CASHIER, POST, /api/v1/purchase-orders/5/approve, false",
            "CASHIER, POST, /api/v1/inventory/adjust, false",
            "CASHIER, GET, /api/v1/reports/revenue, false",
            "CASHIER, GET, /api/v1/system-settings, false",
            "CASHIER, GET, /api/v1/dashboard, false",
            "CASHIER, GET, /api/v1/profile, true",
            // Manager: products, prices, promotions, approvals, reports
            "MANAGER, POST, /api/v1/products, true",
            "MANAGER, PUT, /api/v1/categories/3, true",
            "MANAGER, POST, /api/v1/promotions, true",
            "MANAGER, GET, /api/v1/purchase-orders/pending, true",
            "MANAGER, POST, /api/v1/purchase-orders/5/approve, true",
            "MANAGER, POST, /api/v1/purchase-orders/5/reject, true",
            "MANAGER, POST, /api/v1/purchase-orders/5/supplier-proposal/review, true",
            "WAREHOUSE_STAFF, POST, /api/v1/purchase-orders/5/supplier-proposal/review, false",
            "SUPPLIER, POST, /api/v1/purchase-orders/5/supplier-proposal/review, false",
            "MANAGER, POST, /api/v1/purchase-orders/requests, false",
            "MANAGER, GET, /api/v1/purchase-orders/next-number, false",
            "MANAGER, GET, /api/v1/stock-receipts/next-number, false",
            "MANAGER, GET, /api/v1/stock-receipts/3, true",
            "MANAGER, GET, /api/v1/sales/products, false",
            "MANAGER, GET, /api/v1/inventory/checks/7, true",
            "MANAGER, POST, /api/v1/inventory/adjust, false",
            "MANAGER, GET, /api/v1/sales, true",
            "MANAGER, POST, /api/v1/sales, false",
            "MANAGER, DELETE, /api/v1/suppliers/4, true",
            "MANAGER, GET, /api/v1/reports/revenue, true",
            "MANAGER, GET, /api/v1/system-logs, false",
            "MANAGER, GET, /api/v1/dashboard, true",
            "MANAGER, GET, /api/v1/roles, false",
            // Inventory staff: stock, receipts, purchase requests
            "WAREHOUSE_STAFF, POST, /api/v1/inventory/adjust, true",
            "WAREHOUSE_STAFF, GET, /api/v1/inventory/products/7/batches, true",
            "WAREHOUSE_STAFF, GET, /api/v1/inventory/products/7/batches/grouped, true",
            "CASHIER, GET, /api/v1/inventory/products/7/batches, false",
            "WAREHOUSE_STAFF, PUT, /api/v1/inventory/checks/3, true",
            "WAREHOUSE_STAFF, POST, /api/v1/stock-receipts, true",
            "WAREHOUSE_STAFF, POST, /api/v1/stock-receipts/3/confirm, true",
            "WAREHOUSE_STAFF, GET, /api/v1/purchase-orders/low-stock-suggestions, true",
            "WAREHOUSE_STAFF, POST, /api/v1/purchase-orders/requests, true",
            "WAREHOUSE_STAFF, POST, /api/v1/purchase-orders/5/cancel, true",
            "WAREHOUSE_STAFF, POST, /api/v1/purchase-orders/5/approve, false",
            "WAREHOUSE_STAFF, GET, /api/v1/purchase-orders/pending, false",
            "WAREHOUSE_STAFF, GET, /api/v1/categories, true",
            "WAREHOUSE_STAFF, GET, /api/v1/suppliers, true",
            "WAREHOUSE_STAFF, DELETE, /api/v1/suppliers/4, false",
            "WAREHOUSE_STAFF, GET, /api/v1/invoices, false",
            "WAREHOUSE_STAFF, POST, /api/v1/sales, false",
            "WAREHOUSE_STAFF, GET, /api/v1/reports/inventory, true",
            "WAREHOUSE_STAFF, GET, /api/v1/inventory/stock-history, true",
            "WAREHOUSE_STAFF, GET, /api/v1/inventory/stock-alerts, true",
            "WAREHOUSE_STAFF, GET, /api/v1/dashboard, false",
            "CASHIER, GET, /api/v1/inventory/stock-alerts, false",
            // Supplier: only the portal (UC48 BR-01)
            "SUPPLIER, GET, /api/v1/supplier-portal/purchase-orders, true",
            "SUPPLIER, POST, /api/v1/supplier-portal/purchase-orders/5/respond, true",
            "SUPPLIER, GET, /api/v1/notifications, true",
            "SUPPLIER, GET, /api/v1/profile, true",
            "SUPPLIER, GET, /api/v1/purchase-orders, false",
            "SUPPLIER, GET, /api/v1/products, false",
            "SUPPLIER, POST, /api/v1/inventory/adjust, false",
            "SUPPLIER, GET, /api/v1/users, false",
            "SUPPLIER, GET, /api/v1/dashboard, false",
            // Admin: administration (BR-03 permissions)
            "ADMIN, GET, /api/v1/system-settings, true",
            "ADMIN, GET, /api/v1/dashboard, true",
            "ADMIN, PUT, /api/v1/system-settings/1, true",
            "ADMIN, GET, /api/v1/system-logs/actions, true",
            "ADMIN, PUT, /api/v1/roles/2/permissions, true",
            "ADMIN, DELETE, /api/v1/users/9, true"
    })
    void defaultPermissionsOfEachRole(String role, String method, String path, boolean allowed) {
        RoleName roleName = "ANONYMOUS".equals(role) ? null : RoleName.valueOf(role);
        Set<Permission> permissions = RolePermissions.defaultsFor(roleName);
        assertEquals(allowed, ApiPermissionRules.isAllowed(roleName, permissions, HttpMethod.valueOf(method), path),
                () -> role + " " + method + " " + path + " matched " + ApiPermissionRules.find(HttpMethod.valueOf(method), path));
    }

    /** UC06: the API follows the permissions the Admin gives to a role. */
    @Test
    void permissionChangesOfARoleApplyToTheApi() {
        Set<Permission> cashier = RolePermissions.defaultsFor(RoleName.CASHIER);
        cashier.add(Permission.PURCHASE_ORDER_VIEW);
        assertTrue(ApiPermissionRules.isAllowed(RoleName.CASHIER, cashier, HttpMethod.GET, "/api/v1/purchase-orders"));

        cashier.remove(Permission.PRODUCT_VIEW);
        assertFalse(ApiPermissionRules.isAllowed(RoleName.CASHIER, cashier, HttpMethod.GET, "/api/v1/products"));
    }

    /** UC42: DASHBOARD_VIEW cannot turn the management dashboard into a Cashier screen. */
    @Test
    void dashboardRequiresAnAdminOrManagerRoleInAdditionToPermission() {
        assertFalse(ApiPermissionRules.isAllowed(RoleName.CASHIER, Set.of(Permission.DASHBOARD_VIEW),
                HttpMethod.GET, "/api/v1/dashboard"));
        assertFalse(ApiPermissionRules.isAllowed(RoleName.MANAGER, Set.of(),
                HttpMethod.GET, "/api/v1/dashboard"));
        assertTrue(ApiPermissionRules.isAllowed(RoleName.MANAGER, Set.of(Permission.DASHBOARD_VIEW),
                HttpMethod.GET, "/api/v1/dashboard"));
    }

    /** UC48 BR-01: even with every permission, a Supplier account cannot use the staff API. */
    @Test
    void supplierNeverReachesTheStaffApi() {
        Set<Permission> all = EnumSet.allOf(Permission.class);
        for (ControllerEndpoints.Endpoint endpoint : ENDPOINTS) {
            String path = endpoint.samplePath();
            boolean allowed = ApiPermissionRules.isAllowed(RoleName.SUPPLIER, all, endpoint.method(), path);
            boolean sharedWithSupplier = path.startsWith("/api/v1/supplier-portal")
                    || path.startsWith("/api/v1/notifications")
                    || path.startsWith("/api/v1/auth")
                    || path.startsWith("/api/v1/profile")
                    || ApiPermissionRules.find(endpoint.method(), path)
                    .map(rule -> rule.access() == ApiPermissionRules.Access.PUBLIC).orElse(false);
            assertEquals(sharedWithSupplier, allowed, endpoint::toString);
        }
    }
}
