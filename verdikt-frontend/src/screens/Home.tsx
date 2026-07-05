import { useNavigate } from 'react-router-dom';
import Button from '../components/Button';
import Stamp from '../components/Stamp';
import './Home.css';

const highlights = [
  'Live vote updates',
  'Public or anonymous',
  'Fast room setup',
];

const steps = [
  {
    title: 'Start a room',
    copy: 'Pick a name, set the tone, and generate a clean invite link.',
  },
  {
    title: 'Drop the code',
    copy: 'Your GC joins instantly. No messy setup, no extra friction.',
  },
  {
    title: 'Let Verdikt cook',
    copy: 'Vote live, reveal the results, and see the verdict land.',
  },
];

export default function Home() {
  const navigate = useNavigate();

  return (
    <div className="screen home">
      <div className="home__orb home__orb--one" aria-hidden="true" />
      <div className="home__orb home__orb--two" aria-hidden="true" />
      <div className="home__grid" aria-hidden="true" />

      <header className="home__brand">
        <div className="home__brand-mark">V</div>
        <div>
          <p className="home__brand-kicker">group chat verdicts, upgraded</p>
          <h2 className="home__brand-name">Verdikt</h2>
        </div>
      </header>

      <main className="home__hero">
        <section className="home__hero-copy">
          <Stamp label="the verdikt is in" size="lg" color="coral" />
          <p className="home__eyebrow">Live room voting for the boldest GC energy</p>
          <h1 className="home__headline">
            THE <span className="home__headline-accent">GC</span> HAS SPOKEN
          </h1>
          <p className="home__subtitle">
            Turn group chat chaos into a clean live verdict. Create a room, vote fast,
            and watch the room decide who gets crowned.
          </p>

          <div className="home__pills" aria-label="Key features">
            {highlights.map((item) => (
              <span key={item} className="home__pill">
                {item}
              </span>
            ))}
          </div>

          <div className="home__actions">
            <Button fullWidth onClick={() => navigate('/create')}>
              Start a room
            </Button>
            <Button fullWidth variant="secondary" onClick={() => navigate('/join')}>
              Join with a code
            </Button>
          </div>
        </section>

        <aside className="home__feature-card" aria-label="How Verdikt works">
          <div className="home__feature-card-top">
            <span className="home__feature-card-tag">how it plays</span>
            <span className="home__feature-card-score">3 steps</span>
          </div>

          <div className="home__feature-card-body">
            {steps.map((step, index) => (
              <article key={step.title} className="home__step">
                <div className="home__step-index">{index + 1}</div>
                <div>
                  <h3 className="home__step-title">{step.title}</h3>
                  <p className="home__step-copy">{step.copy}</p>
                </div>
              </article>
            ))}
          </div>

          <div className="home__feature-footer">
            <span className="home__feature-footer-label">best for</span>
            <p className="home__feature-footer-copy">
              polls, petty debates, and the kind of verdict your group will keep talking about.
            </p>
          </div>
        </aside>
      </main>

      <footer className="home__footer">
        <p className="home__footer-copy">made for the chaos in your group chat</p>
      </footer>
    </div>
  );
}
