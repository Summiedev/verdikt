import type { InputHTMLAttributes } from 'react';
import './TextField.css';

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> { label?: string; error?: string; hint?: string; }

export default function TextField({ label, error, hint, id, ...rest }: TextFieldProps) {
  const messageId = id + '-message';
  return (
    <div className="textfield">
      {label && <label className="textfield__label" htmlFor={id}>{label}</label>}
      <input id={id} className={'textfield__input' + (error ? ' textfield__input--error' : '')} aria-invalid={Boolean(error)} aria-describedby={(error || hint) ? messageId : undefined} {...rest} />
      {(error || hint) && <p id={messageId} className={'textfield__message' + (error ? ' textfield__error' : '')}>{error || hint}</p>}
    </div>
  );
}
