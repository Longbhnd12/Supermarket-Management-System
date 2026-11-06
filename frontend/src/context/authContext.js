import { createContext, useContext } from 'react';

// Filled by <AuthProvider>. Kept in its own file so AuthProvider.jsx only exports a component
// (eslint react-refresh/only-export-components).
export const AuthContext = createContext(null);

/**
 * Login state shared by the whole app.
 *
 * const {
 *   currentUser,        // { userId, username, email, role, fullName, permissions } or null
 *   isAuthenticated, isLoading,
 *   permissions,        // string[]
 *   hasPermission, hasAnyPermission, hasRole,
 *   login, logout, refreshUser, clear
 * } = useAuth();
 */
export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth() must be used inside <AuthProvider>.');
    }
    return context;
}
