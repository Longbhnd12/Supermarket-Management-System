import { getErrorMessage } from '../../utils/errorUtils';

// Translate POS errors locally; other pages keep their existing error messages.
export function getPOSErrorMessage(error) {
    const message = error.response?.data?.message ?? getErrorMessage(error);
    const translations = {
        'Sale is locked by a pending, successful or unresolved payment': 'The sale is locked by a pending, successful or unresolved payment.',
        'A sale with a pending, successful or unresolved payment cannot be cancelled': 'A sale with a pending, successful or unresolved payment cannot be cancelled.',
        'Không thể hủy: Đơn hàng đã có giao dịch thanh toán thành công.': 'Cannot cancel: this sale has a successful payment.',
        'Không thể hủy: Đơn hàng đang có giao dịch thanh toán chờ xử lý (chưa hết thời hạn). Vui lòng đợi hết hạn thanh toán hoặc hủy trên cổng thanh toán.': 'Cannot cancel: this sale has a pending payment that has not expired. Wait for it to expire or cancel it on the payment gateway.',
        'Đơn hàng đang có giao dịch thanh toán chờ xử lý hoặc cần đối soát.': 'This sale has a pending payment or requires reconciliation.',
    };
    if (translations[message]) return translations[message];
    const stock = message.match(/^Không đủ tồn kho cho (.*): yêu cầu (.*), tồn kho (.*)\. Vui lòng giảm số lượng\.$/);
    if (stock) return `Insufficient stock for ${stock[1]}: requested ${stock[2]}, available ${stock[3]}. Please reduce the quantity.`;
    const fefo = message.match(/^Không đủ tồn kho FEFO cho (.*): cần (.*), có thể bán (.*)\. Chưa tạo thanh toán\. Vui lòng giảm số lượng hoặc nhập và xác nhận lô hàng hợp lệ\.$/);
    if (fefo) return `Insufficient FEFO stock for ${fefo[1]}: required ${fefo[2]}, available ${fefo[3]}. No payment was created. Reduce the quantity or receive and confirm an eligible stock batch.`;
    return message;
}
