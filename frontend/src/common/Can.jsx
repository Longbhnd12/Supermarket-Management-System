import { useAuth } from '../context/authContext';
import { canAccess } from '../utils/pageAccess';

/**
 * Shows its children only when the current user has the permission.
 *
 * <Can permission="PRODUCT_MANAGE"><button>Create product</button></Can>
 * <Can anyOf={['PURCHASE_REQUEST_CREATE', 'PURCHASE_REQUEST_APPROVE']}>...</Can>
 * <Can access={PAGE_ACCESS.users} fallback={<span>-</span>}>...</Can>
 *
 * Only for the UI: the backend still checks every request.
 */
export default function Can({ permission, anyOf, roles, access, fallback = null, children }) {
    const { currentUser } = useAuth();
    const rule = access ?? { roles, anyOf: permission ? [permission] : anyOf };
    return canAccess(currentUser, rule) ? children : fallback;
}
