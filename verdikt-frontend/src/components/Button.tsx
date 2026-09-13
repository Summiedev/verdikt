import type { ButtonHTMLAttributes, ReactNode } from 'react';
import './Button.css';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  fullWidth?: boolean;
  loading?: boolean;
  children: ReactNode;
}

export default function Button({ variant = 'primary', fullWidth = false, loading = false, children, className = '', disabled, ...rest }: ButtonProps) {
  return (
    <button className={'btn btn--' + variant + (fullWidth ? ' btn--full' : '') + ' ' + className} disabled={disabled || loading} aria-busy={loading || undefined} {...rest}>
      {loading && <span className="btn__spinner" aria-hidden="true" />}
      <span>{children}</span>
    </button>
  );
}
