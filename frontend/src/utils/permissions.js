// Permission checks on the current user returned by GET /auth/me.
// user.permissions holds the permission codes of the user's role, e.g. ['USER_VIEW', 'INVOICE_VIEW'].
// These checks only hide or show UI. The backend still enforces every permission.

export function hasPermission(user, permission) {
    return Boolean(permission) && Array.isArray(user?.permissions) && user.permissions.includes(permission);
}

export function hasAnyPermission(user, permissions = []) {
    return permissions.some((permission) => hasPermission(user, permission));
}

export function hasAllPermissions(user, permissions = []) {
    return permissions.length > 0 && permissions.every((permission) => hasPermission(user, permission));
}

export function hasRole(user, ...roles) {
    return Boolean(user?.role) && roles.includes(user.role);
}
