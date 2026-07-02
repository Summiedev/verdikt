import './Loader.css';

interface LoaderProps {
  message?: string;
  size?: 'sm' | 'md' | 'lg';
}

export default function Loader({ message = 'Loading...', size = 'md' }: LoaderProps) {
  return (
    <div className="loader-container">
      <div className={`loader loader--${size}`}>
        <div className="loader__spinner" />
      </div>
      {message && <p className="loader__message">{message}</p>}
    </div>
  );
}
