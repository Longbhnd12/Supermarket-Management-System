import { Outlet, useOutletContext } from 'react-router-dom';
import { canViewInvoices } from '../utils/invoiceAccess';

export default function InvoiceAccessRoute() {
    const context = useOutletContext();
    return canViewInvoices(context?.currentUser)
        ? <Outlet context={context} />
        : <div role="alert">You do not have permission to view invoices.</div>;
}
