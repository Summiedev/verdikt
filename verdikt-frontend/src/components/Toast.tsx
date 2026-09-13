import './Toast.css';

interface ToastProps { message: string; tone?: 'success' | 'error'; onDismiss: () => void; }

export default function Toast({ message, tone = 'success', onDismiss }: ToastProps) {
  return (
    <div className={'toast toast--' + tone} role={tone === 'error' ? 'alert' : 'status'} aria-live="polite">
      <span>{message}</span>
      <button type="button" onClick={onDismiss} aria-label="Dismiss notification">×</button>
    </div>
  );
}
