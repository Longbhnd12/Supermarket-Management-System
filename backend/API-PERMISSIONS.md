# API permissions

Every REST endpoint is checked against **one table**: `security/ApiPermissionRules.java`.
`SecurityConfig` turns that table into Spring Security rules.

## How a request is checked

1. The rules are read **from top to bottom**. The **first rule that matches** the method and the path decides,
   so a specific path (`/purchase-orders/pending`) must come **before** a general one (`/purchase-orders/**`).
2. A rule is one of:
   - **Public**: no login.
   - **Any logged-in account** (Supplier included).
   - **Permission**: the account has at least **one** of the listed permissions.
     Staff API rules also require a staff role (Admin, Manager, Cashier, Inventory Staff), so a Supplier account
     never reaches the staff API even if an Admin gives the Supplier role a staff permission (UC48 BR-01).
3. A request that matches **no rule is denied** (deny by default): 401 when not logged in, 403 otherwise.

The permissions of an account come from its role (`role.permissions`, UC06 Roles & Permissions).
When the Admin changes a role, the API follows from the next request. No restart is needed.

| Response | `errorCode` | Meaning |
|---|---|---|
| 401 | `UNAUTHORIZED` | Not logged in or session expired |
| 403 | `FORBIDDEN` | Logged in, but the role does not have the permission (MSG19) |
| 403 | `CSRF_INVALID` | POST/PUT/PATCH/DELETE without the `X-XSRF-TOKEN` header (the frontend `api.js` sends it) |

## Adding an endpoint

1. Write the controller method as usual.
2. Add a line in `ApiPermissionRules.RULES`, in the right section and **before** any more general rule:
   ```java
   staff(GET, "/api/v1/products/expiring", PRODUCT_VIEW),
   staff(POST, "/api/v1/products/*/archive", PRODUCT_MANAGE),
   ```
   `*` = one path segment, `{id}` is written `*`, `/**` at the end = anything below.
3. Run `.\mvnw.cmd test -Dtest=ApiPermissionRulesTest`.
   `everyEndpointHasARule` fails and lists your endpoint if you forget step 2.
   Without a rule, the endpoint answers **403 for everybody**, Admin included.
4. If a new permission is needed, add it to `enums/Permission.java` and to the default roles in
   `RolePermissions.java` (follow SRS 3.1.3).

Do not use `@PreAuthorize` or role checks in controllers: keep every rule in this table.

## The table

✓ = allowed with the **default** permissions of the role (SRS 3.1.3 Screen Authorization).
An Admin can change them in Roles & Permissions.
This table is generated from the code.

| # | Method | Path | Who | Admin | Manager | Cashier | Inventory | Supplier |
|---|---|---|---|:-:|:-:|:-:|:-:|:-:|
| 1 | GET | `/api/v1/health` | Public (no login) | ✓ | ✓ | ✓ | ✓ | ✓ |
| 2 | GET | `/api/v1/test-error` | Public (no login) | ✓ | ✓ | ✓ | ✓ | ✓ |
| 3 | ANY | `/error` | Public (no login) | ✓ | ✓ | ✓ | ✓ | ✓ |
| 4 | POST | `/api/v1/auth/login` | Public (no login) | ✓ | ✓ | ✓ | ✓ | ✓ |
| 5 | POST | `/api/v1/auth/forgot-password` | Public (no login) | ✓ | ✓ | ✓ | ✓ | ✓ |
| 6 | POST | `/api/v1/auth/reset-password` | Public (no login) | ✓ | ✓ | ✓ | ✓ | ✓ |
| 7 | POST | `/api/v1/auth/logout` | Public (no login) | ✓ | ✓ | ✓ | ✓ | ✓ |
| 8 | GET | `/api/v1/payments/vnpay/ipn` | Public (no login) | ✓ | ✓ | ✓ | ✓ | ✓ |
| 9 | GET | `/api/v1/auth/me` | Any logged-in account | ✓ | ✓ | ✓ | ✓ | ✓ |
| 10 | POST | `/api/v1/auth/change-password` | Any logged-in account | ✓ | ✓ | ✓ | ✓ | ✓ |
| 11 | ANY | `/api/v1/profile` | Any logged-in account | ✓ | ✓ | ✓ | ✓ | ✓ |
| 12 | GET | `/api/v1/users` | Any staff role | ✓ | ✓ | ✓ | ✓ |  |
| 13 | GET | `/api/v1/users/**` | `USER_VIEW` | ✓ |  |  |  |  |
| 14 | ANY | `/api/v1/users/**` | `USER_MANAGE` | ✓ |  |  |  |  |
| 15 | ANY | `/api/v1/roles/**` | `ROLE_MANAGE` | ✓ |  |  |  |  |
| 16 | ANY | `/api/v1/system-settings/**` | `SYSTEM_SETTINGS_MANAGE` | ✓ |  |  |  |  |
| 17 | GET | `/api/v1/system-logs/**` | `SYSTEM_LOG_VIEW` | ✓ |  |  |  |  |
| 18 | GET | `/api/v1/dashboard` | `DASHBOARD_VIEW` | ✓ | ✓ | ✓ | ✓ |  |
| 19 | ANY | `/api/v1/notifications/**` | `NOTIFICATION_VIEW` (any role) | ✓ | ✓ | ✓ | ✓ | ✓ |
| 20 | GET | `/api/v1/products/**` | `PRODUCT_VIEW` | ✓ | ✓ | ✓ | ✓ |  |
| 21 | PATCH | `/api/v1/products/*/price` | `PRODUCT_MANAGE` or `PRICE_MANAGE` | ✓ | ✓ |  |  |  |
| 22 | ANY | `/api/v1/products/**` | `PRODUCT_MANAGE` | ✓ | ✓ |  |  |  |
| 23 | GET | `/api/v1/categories/**` | `PRODUCT_VIEW` | ✓ | ✓ | ✓ | ✓ |  |
| 24 | ANY | `/api/v1/categories/**` | `CATEGORY_MANAGE` | ✓ | ✓ |  |  |  |
| 25 | GET | `/api/v1/promotions/**` | `PROMOTION_MANAGE` or `PROMOTION_VIEW_ACTIVE` | ✓ | ✓ | ✓ |  |  |
| 26 | ANY | `/api/v1/promotions/**` | `PROMOTION_MANAGE` | ✓ | ✓ |  |  |  |
| 27 | GET | `/api/v1/employees/**` | `EMPLOYEE_VIEW` | ✓ | ✓ |  |  |  |
| 28 | ANY | `/api/v1/employees/**` | `EMPLOYEE_MANAGE` | ✓ | ✓ |  |  |  |
| 29 | GET | `/api/v1/suppliers/**` | `SUPPLIER_VIEW` | ✓ | ✓ | ✓ | ✓ |  |
| 30 | PATCH | `/api/v1/suppliers/*/status` | `SUPPLIER_MANAGE` or `SUPPLIER_DISABLE` | ✓ | ✓ | ✓ | ✓ |  |
| 31 | DELETE | `/api/v1/suppliers/**` | `SUPPLIER_DISABLE` | ✓ | ✓ |  |  |  |
| 32 | ANY | `/api/v1/suppliers/**` | `SUPPLIER_MANAGE` | ✓ | ✓ | ✓ | ✓ |  |
| 33 | GET | `/api/v1/sales/*/invoice` | `INVOICE_VIEW` + role ADMIN/MANAGER/CASHIER | ✓ | ✓ | ✓ |  |  |
| 34 | GET | `/api/v1/sales/*/invoice/pdf` | `INVOICE_VIEW` + role ADMIN/MANAGER/CASHIER | ✓ | ✓ | ✓ |  |  |
| 35 | GET | `/api/v1/invoices/**` | `INVOICE_VIEW` + role ADMIN/MANAGER/CASHIER | ✓ | ✓ | ✓ |  |  |
| 36 | GET | `/api/v1/sales/products` | `POS_SALE` | ✓ |  | ✓ |  |  |
| 37 | GET | `/api/v1/sales/**` | `SALE_VIEW` or `POS_SALE` | ✓ | ✓ | ✓ |  |  |
| 38 | ANY | `/api/v1/sales/**` | `POS_SALE` | ✓ |  | ✓ |  |  |
| 39 | GET | `/api/v1/payments/reference/*` | `SALE_VIEW` or `POS_SALE` | ✓ | ✓ | ✓ |  |  |
| 40 | GET | `/api/v1/payments/sales/*` | `SALE_VIEW` or `POS_SALE` | ✓ | ✓ | ✓ |  |  |
| 41 | ANY | `/api/v1/payments/**` | `POS_SALE` | ✓ |  | ✓ |  |  |
| 42 | GET | `/api/v1/inventory` | `INVENTORY_VIEW` | ✓ | ✓ |  | ✓ |  |
| 43 | GET | `/api/v1/inventory/export` | `INVENTORY_VIEW` or `REPORT_INVENTORY` | ✓ | ✓ |  | ✓ |  |
| 44 | POST | `/api/v1/inventory/adjust` | `STOCK_ADJUST` | ✓ |  |  | ✓ |  |
| 45 | GET | `/api/v1/inventory/history` | `STOCK_HISTORY_VIEW` | ✓ | ✓ |  | ✓ |  |
| 46 | GET | `/api/v1/inventory/stock-history` | `STOCK_HISTORY_VIEW` | ✓ | ✓ |  | ✓ |  |
| 47 | GET | `/api/v1/inventory/movements` | `STOCK_HISTORY_VIEW` | ✓ | ✓ |  | ✓ |  |
| 48 | GET | `/api/v1/inventory/alerts` | `STOCK_ALERT_VIEW` | ✓ | ✓ |  | ✓ |  |
| 49 | GET | `/api/v1/inventory/products/*/batches/**` | `INVENTORY_VIEW` or `STOCK_ADJUST` | ✓ | ✓ |  | ✓ |  |
| 50 | GET | `/api/v1/inventory/stock-alerts` | `STOCK_ALERT_VIEW` | ✓ | ✓ |  | ✓ |  |
| 51 | GET | `/api/v1/inventory/receipts/**` | `INVENTORY_VIEW` or `STOCK_IMPORT` | ✓ | ✓ |  | ✓ |  |
| 52 | ANY | `/api/v1/inventory/receipts/**` | `STOCK_IMPORT` | ✓ |  |  | ✓ |  |
| 53 | GET | `/api/v1/inventory/checks/**` | `INVENTORY_VIEW` or `STOCK_CHECK` | ✓ | ✓ |  | ✓ |  |
| 54 | ANY | `/api/v1/inventory/checks/**` | `STOCK_CHECK` | ✓ |  |  | ✓ |  |
| 55 | GET | `/api/v1/stock-receipts/next-number` | `STOCK_IMPORT` | ✓ |  |  | ✓ |  |
| 56 | GET | `/api/v1/stock-receipts/**` | `INVENTORY_VIEW` or `STOCK_IMPORT` | ✓ | ✓ |  | ✓ |  |
| 57 | ANY | `/api/v1/stock-receipts/**` | `STOCK_IMPORT` | ✓ |  |  | ✓ |  |
| 58 | GET | `/api/v1/purchase-orders/pending` | `PURCHASE_REQUEST_APPROVE` | ✓ | ✓ |  |  |  |
| 59 | GET | `/api/v1/purchase-orders/next-number` | `PURCHASE_REQUEST_CREATE` | ✓ |  |  | ✓ |  |
| 60 | GET | `/api/v1/purchase-orders/low-stock-suggestions` | `PURCHASE_REQUEST_CREATE` | ✓ |  |  | ✓ |  |
| 61 | GET | `/api/v1/purchase-orders/**` | `PURCHASE_REQUEST_CREATE` or `PURCHASE_REQUEST_APPROVE` or `PURCHASE_ORDER_VIEW` | ✓ | ✓ |  | ✓ |  |
| 62 | POST | `/api/v1/purchase-orders` | `PURCHASE_REQUEST_CREATE` | ✓ |  |  | ✓ |  |
| 63 | POST | `/api/v1/purchase-orders/requests` | `PURCHASE_REQUEST_CREATE` | ✓ |  |  | ✓ |  |
| 64 | POST | `/api/v1/purchase-orders/*/approve` | `PURCHASE_REQUEST_APPROVE` | ✓ | ✓ |  |  |  |
| 65 | POST | `/api/v1/purchase-orders/*/reject` | `PURCHASE_REQUEST_APPROVE` | ✓ | ✓ |  |  |  |
| 66 | POST | `/api/v1/purchase-orders/*/supplier-proposal/review` | `PURCHASE_REQUEST_APPROVE` | ✓ | ✓ |  |  |  |
| 67 | POST | `/api/v1/purchase-orders/*/cancel` | `PURCHASE_REQUEST_CREATE` or `PURCHASE_REQUEST_APPROVE` | ✓ | ✓ |  | ✓ |  |
| 68 | ANY | `/api/v1/supplier-portal/**` | `SUPPLIER_PORTAL` (any role) | ✓ |  |  |  | ✓ |
| 69 | GET | `/api/v1/reports/sales` | `REPORT_SALES` | ✓ | ✓ |  |  |  |
| 70 | GET | `/api/v1/reports/revenue` | `REPORT_REVENUE` | ✓ | ✓ |  |  |  |
| 71 | GET | `/api/v1/reports/product-sales` | `REPORT_PRODUCT_SALES` | ✓ | ✓ |  |  |  |
| 72 | GET | `/api/v1/reports/inventory` | `REPORT_INVENTORY` | ✓ | ✓ |  | ✓ |  |
| | ANY | anything else | **Denied** (deny by default) | | | | | |

## To confirm by each module owner

The table follows the default permissions of SRS 3.1.3. Please check the rows of your module and say if a
role needs more or less access:

1. **Inventory**: Manager can view stock, history, alerts, stock checks and receipts, but cannot adjust stock,
   perform stock checks or create/confirm stock receipts (Inventory Staff only).
2. **Reports**: Cashier has no report permission by default. UC41 mentions a cashier shift report: if it uses
   `/reports/...`, the Cashier role needs that report permission.
3. **Prices**: there is no separate price API. Changing a price goes through `PUT /products/{id}`, which needs
   `PRODUCT_MANAGE`. `PRICE_MANAGE` is not used by the API yet.
4. **Suppliers**: Cashier and Inventory Staff have `SUPPLIER_MANAGE` by default (create/update suppliers).
   Disabling a supplier (`DELETE`) needs `SUPPLIER_DISABLE` (Admin, Manager).
5. **Purchase orders**: Cashier no longer reaches `/purchase-orders/**` (UC46/UC47/UC50 actors are
   Admin, Manager and Inventory Staff).
6. `GET /api/v1/test-error` is public. It is a debug endpoint and should be removed before the final demo.

## Tests

| Test | What it checks |
|---|---|
| `security/ApiPermissionRulesTest` | Every controller endpoint has a rule; Admin can call everything; ~70 role/endpoint cases with the default permissions; UC06 changes apply; Supplier never reaches the staff API |
| `controller/ApiSecurityRulesWebTest` | The real Spring Security filter chain: 401/403/200, role + permission, CSRF, deny by default, public VNPay IPN |
| `controller/InvoiceSearchSecurityTest` | Invoice rules (UC22/UC23) |
