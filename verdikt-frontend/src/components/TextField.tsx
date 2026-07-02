import type { InputHTMLAttributes } from 'react';
import './TextField.css';

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
}

export default function TextField({ label, error, id, ...rest }: TextFieldProps) {
  return (
    <div className="textfield">
      {label && (
        <label className="textfield__label" htmlFor={id}>
          {label}
        </label>
      )}
      <input id={id} className={`textfield__input ${error ? 'textfield__input--error' : ''}`} {...rest} />
      {error && <p className="textfield__error">{error}</p>}
    </div>
  );
}
