import { Check } from 'lucide-react';
import './CheckoutSteps.css';

const labels = ['Scan & Cart', 'Select Payment Method', 'Process Payment', 'Result & Invoice'];

export default function CheckoutSteps({ currentStep = 1 }) {
    return <nav className="checkout-steps" aria-label="Checkout progress">
        {labels.map((label, index) => {
            const step = index + 1;
            return <div key={label} className={step === currentStep ? 'is-current' : ''}
                aria-current={step === currentStep ? 'step' : undefined}>
                <span>{step < currentStep ? <Check size={14} /> : step}</span>
                <div>Step {step}: {label}<small>{step < currentStep ? 'Completed'
                    : step === currentStep ? step === 4 ? 'UC30 · Complete Sale' : 'In Progress' : 'Not Started'}</small></div>
            </div>;
        })}
    </nav>;
}
