import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '../components/Button';
import './Home.css';

const roomPreview = [
  { name: 'Maya', color: 'peach', votes: '12' },
  { name: 'Leon', color: 'lime', votes: '08' },
  { name: 'Zee', color: 'violet', votes: '16' },
];

const howItPlays = [
  { number: '01', title: 'Pick the energy', copy: 'Set the question, the spice, and who gets to vote.' },
  { number: '02', title: 'Pass the code', copy: 'Your people join the room in one tap. No account parade.' },
  { number: '03', title: 'Reveal the verdict', copy: 'Watch the votes land and crown the group-chat champion.' },
];

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
}

export default function Home() {
  const navigate = useNavigate();
  const homeRef = useRef<HTMLDivElement>(null);
  const [playIntro] = useState(
    () => sessionStorage.getItem('verdikt-home-intro-seen') !== 'true',
  );
  const [installPrompt, setInstallPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [isInstalled, setIsInstalled] = useState(false);
  const [showInstallHelp, setShowInstallHelp] = useState(false);

  useEffect(() => {
    sessionStorage.setItem('verdikt-home-intro-seen', 'true');

    const displayMode = window.matchMedia('(display-mode: standalone)');
    const syncInstalledState = () => {
      const isIosStandalone = (navigator as Navigator & { standalone?: boolean }).standalone === true;
      setIsInstalled(displayMode.matches || isIosStandalone);
    };
    const captureInstallPrompt = (event: Event) => {
      event.preventDefault();
      setInstallPrompt(event as BeforeInstallPromptEvent);
    };
    const markInstalled = () => {
      setIsInstalled(true);
      setInstallPrompt(null);
      setShowInstallHelp(false);
    };

    syncInstalledState();
    displayMode.addEventListener('change', syncInstalledState);
    window.addEventListener('beforeinstallprompt', captureInstallPrompt);
    window.addEventListener('appinstalled', markInstalled);

    const bubbleObserver = 'IntersectionObserver' in window
      ? new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
          (entry.target as HTMLElement).style.animationPlayState = entry.isIntersecting ? 'running' : 'paused';
        });
      }, { rootMargin: '64px' })
      : null;
    if (bubbleObserver) {
      homeRef.current?.querySelectorAll<HTMLElement>('.home__bubble').forEach((bubble) => {
        bubble.style.animationPlayState = 'paused';
        bubbleObserver.observe(bubble);
      });
    }

    return () => {
      displayMode.removeEventListener('change', syncInstalledState);
      window.removeEventListener('beforeinstallprompt', captureInstallPrompt);
      window.removeEventListener('appinstalled', markInstalled);
      bubbleObserver?.disconnect();
    };
  }, []);

  async function handleInstallApp() {
    if (!installPrompt) {
      setShowInstallHelp((visible) => !visible);
      return;
    }

    try {
      await installPrompt.prompt();
      const { outcome } = await installPrompt.userChoice;
      setInstallPrompt(null);
      setShowInstallHelp(false);
      if (outcome === 'accepted') setIsInstalled(true);
    } catch {
      setShowInstallHelp(true);
    }
  }

  const isAppleTouchDevice = /iPhone|iPad|iPod/i.test(navigator.userAgent)
    || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

  return (
    <div className={`screen home${playIntro ? ' home--intro' : ''}`} ref={homeRef}>
      <div className="home__bubble home__bubble--one" aria-hidden="true" />
      <div className="home__bubble home__bubble--two" aria-hidden="true" />
      <div className="home__bubble home__bubble--three" aria-hidden="true" />
      <div className="home__bubble home__bubble--four" aria-hidden="true" />
      <div className="home__bubble home__bubble--five" aria-hidden="true" />
      <div className="home__bubble home__bubble--six" aria-hidden="true" />
      <div className="home__bubble home__bubble--seven" aria-hidden="true" />
      <div className="home__grain" aria-hidden="true" />

      <header className="home__header">
        <a className="home__brand" href="#top" aria-label="Verdikt home">
          <span className="home__brand-mark" aria-hidden="true">
            <img src="/verdikt-mark.svg" alt="" />
          </span>
          <span className="home__brand-name">Verdikt</span>
        </a>
        {!isInstalled && (
          <div className="home__install-action">
            <button
              className="home__install-button"
              type="button"
              aria-controls="home-install-help"
              aria-expanded={showInstallHelp}
              onClick={() => void handleInstallApp()}
            >
              Install app
            </button>
            <p className="home__install-help" id="home-install-help" role="status" hidden={!showInstallHelp}>
              {isAppleTouchDevice
                ? 'Tap Share in your browser, then choose “Add to Home Screen”.'
                : 'Open your browser menu and choose “Install Verdikt” or “Add to Home Screen”.'}
            </p>
          </div>
        )}
      </header>

      <main className="home__hero" id="top">
        <section className="home__hero-copy" aria-labelledby="home-title">
          <p className="home__eyebrow">Pass the question. Let your people decide.</p>
          <h1 className="home__headline" id="home-title">
            <span>Big takes.</span>
            <span className="home__headline-accent">One verdict.</span>
          </h1>
          <p className="home__subtitle">
            Turn your group chat&apos;s hottest debate into a live vote. Bring everyone in, watch the
            votes roll in, and see what your crew really thinks.
          </p>

          <div className="home__actions">
            <Button fullWidth onClick={() => navigate('/create')}>
              Start a round
            </Button>
            <Button fullWidth variant="secondary" onClick={() => navigate('/join')}>
              I have a room code
            </Button>
          </div>

          <ul className="home__promises" aria-label="Verdikt highlights">
            <li>Fast setup</li>
            <li>Live votes</li>
            <li>Zero downloads</li>
          </ul>
        </section>

        <aside className="home__game-preview" aria-label="Example Verdikt live voting room">
          <div className="home__preview-shadow" aria-hidden="true" />
          <div className="home__preview-card">
            <div className="home__preview-topline">
              <p>Tonight&apos;s round</p>
              <span>04:18</span>
            </div>
            <p className="home__question">Who would survive a chaotic airport run?</p>

            <div className="home__candidate-list" aria-hidden="true">
              {roomPreview.map((player, index) => (
                <div className={`home__candidate home__candidate--${player.color}`} key={player.name}>
                  <span className="home__candidate-rank">0{index + 1}</span>
                  <span className="home__candidate-name">{player.name}</span>
                  <span className="home__candidate-votes">{player.votes}</span>
                  <span className="home__candidate-bar"><i /></span>
                </div>
              ))}
            </div>

            <div className="home__preview-footer">
              <span className="home__live-pulse" aria-hidden="true" />
              6 people are voting
            </div>
          </div>
          <div className="home__vote-chip home__vote-chip--one" aria-hidden="true">+1</div>
          <div className="home__vote-chip home__vote-chip--two" aria-hidden="true">VOTE</div>
          <div className="home__crown" aria-hidden="true">
            <span /><span /><span />
          </div>
        </aside>
      </main>

      <section className="home__how" aria-labelledby="how-title">
        <div className="home__how-heading">
          <p className="home__eyebrow">Three moves. One group decision.</p>
          <h2 id="how-title">Make the verdict land.</h2>
        </div>
        <ol className="home__steps">
          {howItPlays.map((step) => (
            <li className="home__step" key={step.number}>
              <span className="home__step-number">{step.number}</span>
              <h3>{step.title}</h3>
              <p>{step.copy}</p>
            </li>
          ))}
        </ol>
      </section>

      <footer className="home__footer">
        <p>
          Made with <span className="sr-only">love</span>
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 21s-8.2-4.9-9.7-10C.7 5.5 7 2.2 12 7.1c5-4.9 11.3-1.6 9.7 3.9C20.2 16.1 12 21 12 21Z" />
          </svg>
          for the chaos in your group chat.
        </p>
      </footer>
    </div>
  );
}
