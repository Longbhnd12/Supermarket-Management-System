# Frontend: login state and API errors

Read this before writing a page that needs the current user or calls the API.

## 1. Get the current user: `useAuth()`

```jsx
import { useAuth } from '../../context/authContext';

export default function MyPage() {
    const { currentUser, hasPermission, hasAnyPermission, hasRole } = useAuth();

    return (
        <>
            <p>Hello {currentUser.fullName}</p>
            {hasPermission('PRODUCT_MANAGE') && <button>Create product</button>}
        </>
    );
}
```

| Field | Meaning |
|---|---|
| `currentUser` | `{ userId, username, email, role, fullName, permissions }` from `GET /auth/me`, or `null` |
| `isAuthenticated`, `isLoading` | Login state |
| `permissions` | Permission codes of the user's role, e.g. `['INVOICE_VIEW']` |
| `hasPermission(code)` / `hasAnyPermission([codes])` / `hasRole('ADMIN', ...)` | Show or hide UI |
| `refreshUser()` | Reload `/auth/me` (e.g. after the profile changes); every open tab updates |
| `logout()` | UC02: logs out this tab and all other tabs |

Rules:
- **Do not** call `authService.getMe()` in a page, and **do not** store the user in `localStorage` or `sessionStorage`.
  The token is in an HttpOnly cookie; `useAuth()` is the only source of the user.
- Hiding a button is only for the UI. The backend still checks every permission.
- Pages that use `useOutletContext().currentUser` keep working, but new code should use `useAuth()`.

## 2. API calls: always use `services/api.js`

Every service file imports `API` from `./api`. That client sends the auth cookie and the CSRF header,
and handles these errors in one place:

| Response | What happens | What your page should do |
|---|---|---|
| 401 | The user is logged out. The Login page shows "Your session has expired". After login the user returns to the same page | Nothing |
| 403 `FORBIDDEN` | A toast "Access denied" is shown. The user stays logged in | Optional: show MSG19 with `getAccessAwareErrorMessage(error)` |
| 403 `CSRF_INVALID` | The request is sent again once automatically | Nothing |
| 5xx | A toast with the server message is shown. The user stays logged in | Show the error in the form if useful |
| Server unreachable | A toast "Cannot connect to server" is shown. The user stays logged in | Same |

To turn off the toast for one request (for example, when the page already shows the error in a dialog):

```js
API.get('/products', { skipGlobalError: true });
```

The error is still rejected, so `try/catch` in the page works as before.

## 3. Several tabs

All tabs of the same browser share the login cookie, so:
- Logout in one tab: the other tabs go to the Login page ("You have been logged out in another tab").
- Login in one tab: a tab that is on the Login page opens the home page.
- Another account logs in from another tab: the old tab reloads with the new account.
- When a tab gets focus again, the session is checked again (account locked, token expired...).

## Files

| File | Role |
|---|---|
| `src/context/AuthProvider.jsx` | Holds the login state, listens to 401 and to the other tabs |
| `src/context/authContext.js` | `useAuth()` |
| `src/context/authState.js` | State transitions (unit-tested) |
| `src/services/api.js` | Axios client + central error handling |
| `src/services/authEvents.js` | Events between `api.js`, React and the other tabs |
| `src/common/AppNotice.jsx` | Toasts for 403, 5xx and connection errors |
| `src/common/ProtectedRoute.jsx` | Login required; "Cannot connect" screen with "Try again" |
| `src/utils/permissions.js` | `hasPermission`, `hasAnyPermission`, `hasAllPermissions`, `hasRole` |

Tests: `npm test` (Node's built-in test runner, no extra package).
