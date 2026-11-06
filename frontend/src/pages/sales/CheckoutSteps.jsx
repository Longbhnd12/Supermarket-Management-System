import { Check } from 'lucide-react';
import './CheckoutSteps.css';

const labels = ['Quét mã & Giỏ hàng', 'Chọn phương thức', 'Xử lý thanh toán', 'Kết quả & Hóa đơn'];

export default function CheckoutSteps({ currentStep = 1 }) {
    return <nav className="checkout-steps" aria-label="Tiến trình bán hàng">
        {labels.map((label, index) => {
            const step = index + 1;
            return <div key={label} className={step === currentStep ? 'is-current' : ''}
                aria-current={step === currentStep ? 'step' : undefined}>
                <span>{step < currentStep ? <Check size={14} /> : step}</span>
                <div>Bước {step}: {label}<small>{step < currentStep ? 'Đã hoàn thành'
                    : step === currentStep ? step === 4 ? 'UC30 · Hoàn tất đơn' : 'Đang thực hiện' : 'Chưa thực hiện'}</small></div>
            </div>;
        })}
    </nav>;
}
