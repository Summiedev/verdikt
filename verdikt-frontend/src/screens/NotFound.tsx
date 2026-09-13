import { useNavigate } from 'react-router-dom';
import Button from '../components/Button';
import './NotFound.css';

export default function NotFound() {
  const navigate = useNavigate();
  return (
    <main className="screen not-found">
      <div className="not-found__mark" aria-hidden="true">?</div>
      <p className="not-found__eyebrow">404 · wrong room</p>
      <h1 className="not-found__title">That verdict went missing.</h1>
      <p className="not-found__copy">This page does not exist, but your next room is only a tap away.</p>
      <Button onClick={() => navigate('/')}>Back to Verdikt</Button>
    </main>
  );
}
