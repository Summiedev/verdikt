import { useState, useEffect, useMemo, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Button from '../components/Button';
import Card from '../components/Card';
import HourglassIcon from '../components/icons/HourglassIcon';
import { loadSession } from '../session';
import { useSocket } from '../useSocket';
import './Lobby.css';

interface Player {
  id: string;
  name: string;
  isHost: boolean;
  isActive: boolean;
}

interface PreviewQuestion {
  id: string;
  text: string;
  spiceLevel: string;
  isCustom?: boolean;
}

export default function Lobby() {
  const { code } = useParams();
  const navigate = useNavigate();
const [session] = useState(() => loadSession());

  const [players, setPlayers] = useState<Player[]>([]);
  const [copied, setCopied] = useState(false);
  const isHost = session?.isHost ?? false;

  const [draftError, setDraftError] = useState('');
  const [showQuestions, setShowQuestions] = useState(false);
  const [questions, setQuestions] = useState<PreviewQuestion[]>([]);
  const [draft, setDraft] = useState('');
  const [loadingQuestions, setLoadingQuestions] = useState(false);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState('');

  const [roomExpired, setRoomExpired] = useState(false);

  useEffect(() => {
    if (!session) { navigate('/'); return; }
    fetch(`${import.meta.env.VITE_API_URL}/api/rooms/rejoin`, {
      headers: { 'X-Player-Token': session.playerToken },
    })
      .then((r) => {
        // FIX 2: detect expired room on rejoin
        if (r.status === 410) { setRoomExpired(true); return null; }
        if (!r.ok) throw new Error('rejoin failed');
        return r.json();
      })
      .then((data) => {
        if (!data) return;
        setPlayers(data.players ?? []);
        if (data.status === 'IN_PROGRESS') {
          navigate(`/play/${code}`);
        }
      })
      .catch(() => navigate('/'));
  }, [code, navigate, session]);

  // FIX 3 (perf): stable callbacks so useMemo subscriptions don't rebuild on every render
  const handlePlayerEvent = useCallback((payload: Record<string, unknown>) => {
    if (payload.type === 'PLAYER_JOINED') {
      setPlayers((prev) => {
        if (prev.find((p) => p.id === String(payload.playerId))) return prev;
        return [...prev, {
          id: String(payload.playerId),
          name: payload.playerName as string,
          isHost: false,
          isActive: true,
        }];
      });
    }
    if (payload.type === 'PLAYER_STATUS_CHANGED') {
      setPlayers((prev) =>
        prev.map((p) =>
          p.id === String(payload.playerId) ? { ...p, isActive: payload.isActive as boolean } : p
        )
      );
    }
  }, []);

  const handleGameEvent = useCallback((payload: Record<string, unknown>) => {
    if (payload.type === 'GAME_STARTED') navigate(`/play/${code}`);
  }, [navigate, code]);

  const subscriptions = useMemo(() => [
    {
      topic: `/topic/room/${session?.roomId}/players`,
      handler: handlePlayerEvent,
    },
    {
      topic: `/topic/room/${session?.roomId}/game`,
      handler: handleGameEvent,
    },
  // FIX 3 (perf): stable deps — roomId never changes, callbacks are memoized
  ], [session?.roomId, handlePlayerEvent, handleGameEvent]);

  useSocket(session?.roomId, subscriptions);

  async function fetchQuestionBatch() {
    if (!session) return;
    setShowQuestions(true);
    setLoadingQuestions(true);
    setError('');
    const count = session.questionCount ?? 10;
    try {
      const res = await fetch(
        `${import.meta.env.VITE_API_URL}/api/rooms/${session.roomId}/game/preview-questions?count=${count}`,
        { headers: { 'X-Player-Token': session.playerToken } }
      );
      // FIX 2: check for expired on any fetch
      if (res.status === 410) { setRoomExpired(true); return; }
      if (res.status === 404) {
        setError('No built-in questions exist yet. Add your own questions to start.');
        setQuestions([]);
        return;
      }
      if (!res.ok) throw new Error();
      const data = await res.json();
      setQuestions(data.map((q: PreviewQuestion) => ({ ...q, isCustom: false })));
    } catch {
      setError("Couldn't load questions. Check your connection and try again.");
    } finally {
      setLoadingQuestions(false);
    }
  }

  function removeQuestion(id: string) {
    setQuestions((prev) => prev.filter((q) => q.id !== id));
  }

  function addCustomQuestion(e?: React.MouseEvent | React.KeyboardEvent) {
    e?.preventDefault();
    const trimmed = draft.trim();
    if (trimmed.length < 5) {
      setDraftError(trimmed.length === 0 ? 'Type a question first.' : 'Needs at least 5 characters.');
      return;
    }
    setDraftError('');
    setQuestions((prev) => [
      { id: `custom-${Date.now()}`, text: trimmed, spiceLevel: 'CUSTOM', isCustom: true },
      ...prev,
    ]);
    setDraft('');
  }

  async function handleStart() {
    if (!session) return;
    setStarting(true);
    setError('');

  try {
      let selectedQuestionIds = questions.filter((q) => !q.isCustom).map((q) => q.id);
      const customQuestionTexts = questions.filter((q) => q.isCustom).map((q) => q.text);

      if (selectedQuestionIds.length === 0 && customQuestionTexts.length === 0) {
        const count = session.questionCount ?? 10;
        const res = await fetch(
          `${import.meta.env.VITE_API_URL}/api/rooms/${session.roomId}/game/preview-questions?count=${count}`,
          { headers: { 'X-Player-Token': session.playerToken } }
        );
        if (res.status === 410) { setRoomExpired(true); setStarting(false); return; }
        if (res.status === 404) {
          setError('No built-in questions exist yet. Add your own questions to start.');
          setStarting(false);
          return;
        }
        if (!res.ok) throw new Error("Couldn't load questions.");
        const data: PreviewQuestion[] = await res.json();
        selectedQuestionIds = data.map((q) => q.id);
      }

      const res = await fetch(
        `${import.meta.env.VITE_API_URL}/api/rooms/${session.roomId}/game/start`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Player-Token': session.playerToken,
          },
          body: JSON.stringify({ selectedQuestionIds, customQuestionTexts }),
        }
      );
      // FIX 2: catch expired on start too
        if (res.status === 410) { setRoomExpired(true); setStarting(false); return; }
        if (res.status === 404) {
          const err = await res.json().catch(() => ({}));
          throw new Error(err.message ?? 'No built-in questions exist yet. Add your own questions to start.');
        }
        if (!res.ok) {
          const err = await res.json().catch(() => ({}));
          throw new Error(err.message ?? "Couldn't start the game.");
        }

        navigate(`/play/${code}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't start the game.");
      setStarting(false);
    }
  }

  function copyLink() {
    navigator.clipboard.writeText(`${window.location.origin}/join?code=${code}`);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  }

  // FIX 1 (host controls): use activePlayers for the start button count
  const activePlayers = players.filter((p) => p.isActive);
  const timerLabel = session?.questionDurationSeconds
    ? `${session.questionDurationSeconds}s per question`
    : 'no timer';

 if (roomExpired) {
    return (
      <div className="screen lobby lobby--expired">
        <div className="lobby__expired-content">
          <HourglassIcon className="lobby__expired-icon" />
          <h1 className="lobby__expired-title">Room expired</h1>
          <p className="lobby__expired-body">
            This room has closed. Rooms expire after a period of inactivity.
          </p>
          <Button fullWidth onClick={() => navigate('/')}>Back to home</Button>
        </div>
      </div>
    );
  }

  return (
    <div className="screen lobby">
      <div className="lobby__header">
        <p className="lobby__eyebrow">Room code</p>
        <h1 className="lobby__code">{code}</h1>
        <button className="lobby__copy" onClick={copyLink}>
          {copied ? 'Copied!' : 'Copy invite link'}
        </button>
      </div>

      <Card className="lobby__players">
        <p className="lobby__players-title">
          {activePlayers.length} {activePlayers.length === 1 ? 'player' : 'players'} in · {timerLabel}
        </p>
        <ul className="lobby__list">
          {activePlayers.map((p) => (
            <li key={p.id} className="lobby__player">
              <span className="lobby__avatar">{p.name.charAt(0).toUpperCase()}</span>
              <span className="lobby__player-name">{p.name}</span>
              {p.isHost && <span className="lobby__host-tag">host</span>}
            </li>
          ))}
        </ul>
      </Card>

      {!isHost && (
        <div className="lobby__waiting">
          <p className="lobby__hint">Waiting for the host to start the game…</p>
        </div>
      )}

      {isHost && !showQuestions && (
        <div className="lobby__host-panel">
          <p className="lobby__hint">Waiting on more friends? Share the code above. When you're ready:</p>
          <Button fullWidth variant="secondary" onClick={fetchQuestionBatch}>
            Preview questions
          </Button>
        </div>
      )}

      {isHost && showQuestions && (
        <div className="lobby__questions">
          <div className="lobby__questions-header">
            <p className="lobby__custom-label" style={{ margin: 0 }}>
              Questions <span className="lobby__optional">{questions.length} picked</span>
            </p>
            <button className="lobby__shuffle" onClick={fetchQuestionBatch} type="button" disabled={loadingQuestions}>
              {loadingQuestions ? 'Shuffling…' : 'Shuffle again'}
            </button>
          </div>

          <div className="lobby__add-row">
            {draftError && <p className="lobby__add-error">{draftError}</p>}
            <input
              className="lobby__add-input"
              placeholder="Type your own question…"
              value={draft}
              onChange={(e) => { setDraft(e.target.value); setDraftError(''); }}
              onKeyDown={(e) => {
                if (e.key === 'Enter') { e.preventDefault(); addCustomQuestion(e); }
              }}
              maxLength={120}
            />
            <button
              className="lobby__add-btn"
              onMouseDown={(e) => { e.preventDefault(); addCustomQuestion(e); }}
              type="button"
              aria-label="Add question"
            >
              +
            </button>
          </div>

          {loadingQuestions ? (
            <p className="lobby__loading">Picking questions…</p>
          ) : (
            <Card className="lobby__custom-list" padded={false}>
              {questions.map((q) => (
                <div key={q.id} className={`lobby__custom-item ${q.isCustom ? 'lobby__custom-item--custom' : ''}`}>
                  <span className="lobby__custom-text">{q.text}</span>
                  <button className="lobby__custom-remove" onClick={() => removeQuestion(q.id)} type="button" aria-label="Remove">×</button>
                </div>
              ))}
              {questions.length === 0 && (
                <p className="lobby__empty">No questions left — add your own or shuffle a new batch.</p>
              )}
            </Card>
          )}
        </div>
      )}

      {error && <p className="lobby__error">{error}</p>}
{isHost && (
        <Button
          fullWidth
          onClick={handleStart}
          disabled={activePlayers.length < 2 || starting}
        >
          {starting
            ? 'Starting…'
            : activePlayers.length < 2
              ? 'Need at least 2 players'
              : 'Start the game'}
        </Button>
      )}
    </div>
  );
}
