import { useNavigate } from 'react-router-dom';
import Button from '../components/Button';
import Stamp from '../components/Stamp';
import './Home.css';

export default function Home() {
  const navigate = useNavigate();

  return (
    <div className="screen home">
      <div className="home__brand">
        <span className="home__brand-mark">V</span>
        <span className="home__brand-name">Verdikt</span>
      </div>

      <div className="home__hero">
        <Stamp label="the verdikt is in" size="lg" color="coral" />
        <h1 className="home__headline">
          THE <span className="home__headline-accent">GC</span> HAS SPOKEN
        </h1>
        <p className="home__subtitle">
          Vote on your friends. See who voted for who. Get the verdict.
        </p>
      </div>

      <div className="home__actions">
        <Button fullWidth onClick={() => navigate('/create')}>
          Start a room
        </Button>
        <Button fullWidth variant="secondary" onClick={() => navigate('/join')}>
          Join with a code
        </Button>
      </div>

      <p className="home__footer">made for the chaos in your group chat</p>
    </div>
  );
}