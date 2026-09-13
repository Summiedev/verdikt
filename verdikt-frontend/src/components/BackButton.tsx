import { useNavigate } from 'react-router-dom';
import Button from './Button';

interface BackButtonProps { label?: string; }

export default function BackButton({ label = 'Back' }: BackButtonProps) {
  const navigate = useNavigate();
  return <Button variant="ghost" className="back-button" onClick={() => navigate(-1)} aria-label={label}>← <span>{label}</span></Button>;
}
