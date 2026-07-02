import './Stamp.css';

interface StampProps {
  label: string;
  size?: 'sm' | 'md' | 'lg';
  color?: 'coral' | 'gold' | 'periwinkle';
}

export default function Stamp({ label, size = 'md', color = 'coral' }: StampProps) {
  return (
    <div className={`stamp stamp--${size} stamp--${color}`}>
      <svg viewBox="0 0 100 100" className="stamp__ring" aria-hidden="true">
        <circle cx="50" cy="50" r="46" fill="none" strokeWidth="2.5" strokeDasharray="3 4" />
      </svg>
      <span className="stamp__label">{label}</span>
    </div>
  );
}
