import { useState, useEffect, useMemo, useCallback, Fragment } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Button from '../components/Button';
import Card from '../components/Card';
import Toast from '../components/Toast';
import ConnectionBanner from '../components/ConnectionBanner';
import HourglassIcon from '../components/icons/HourglassIcon';
import { loadSession } from '../session';
import { useSocket } from '../useSocket';
import './Lobby.css';
import { apiRequest, ApiError } from '../api/client';

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

interface RoomData {
  players: Player[];
  status: string;
}

export default function Lobby() {
  const { code } = useParams();
  const navigate = useNavigate();
const [session] = useState(() => loadSession());

  const [players, setPlayers] = useState<Player[]>([]);
  const [copied, setCopied] = useState(false);
  const [toast, setToast] = useState('');
  const [connectionStatus, setConnectionStatus] = useState<'connecting' | 'connected' | 'reconnecting'>('connecting');
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
    void (async () => {
      try {
        const data = await apiRequest<RoomData>('/api/rooms/rejoin', { playerToken: session.playerToken });
        setPlayers(data.players ?? []);
        if (data.status === 'IN_PROGRESS') navigate(`/play/${code}`);
      } catch (requestError) {
        if (requestError instanceof ApiError && requestError.status === 410) setRoomExpired(true);
        else navigate('/');
      }
    })();
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

  const handleSocketConnected = useCallback(() => setConnectionStatus('connected'), []);
  const handleSocketDisconnected = useCallback(() => setConnectionStatus('reconnecting'), []);
  useSocket(session?.roomId, subscriptions, handleSocketConnected, handleSocketDisconnected, session?.playerToken);

  async function fetchQuestionBatch() {
    if (!session) return;
    setShowQuestions(true);
    setLoadingQuestions(true);
    setError('');
    const count = session.questionCount ?? 10;
    const customQuestions = questions.filter((question) => question.isCustom);
    try {
      const data = await apiRequest<PreviewQuestion[]>(`/api/rooms/${session.roomId}/game/preview-questions?count=${count}`, { playerToken: session.playerToken });
      setQuestions([...data.map((q: PreviewQuestion) => ({ ...q, isCustom: false })), ...customQuestions]);
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 410) setRoomExpired(true);
      else if (requestError instanceof ApiError && requestError.status === 404) {
        setError('No built-in questions exist yet. Add your own questions to start.');
        setQuestions(customQuestions);
      } else setError(requestError instanceof ApiError ? requestError.message : "Couldn't load questions. Check your connection and try again.");
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
      ...prev,
      { id: `custom-${Date.now()}`, text: trimmed, spiceLevel: 'CUSTOM', isCustom: true },
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
        const data = await apiRequest<PreviewQuestion[]>(`/api/rooms/${session.roomId}/game/preview-questions?count=${count}`, { playerToken: session.playerToken });
        selectedQuestionIds = data.map((q) => q.id);
      }

      await apiRequest(`/api/rooms/${session.roomId}/game/start`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        playerToken: session.playerToken,
        body: JSON.stringify({ selectedQuestionIds, customQuestionTexts }),
      });
        navigate(`/play/${code}`);
    } catch (e) {
      if (e instanceof ApiError && e.status === 410) setRoomExpired(true);
      else setError(e instanceof ApiError ? e.message : "Couldn't start the game.");
      setStarting(false);
    }
  }

  async function copyValue(value: string, message: string) {
    try {
      await navigator.clipboard.writeText(value);
    } catch {
      const helper = document.createElement('textarea');
      helper.value = value;
      helper.setAttribute('readonly', '');
      helper.style.position = 'fixed';
      helper.style.opacity = '0';
      document.body.appendChild(helper);
      helper.select();
      document.execCommand('copy');
      helper.remove();
    }
    setToast(message);
  }

  async function copyLink() {
    await copyValue(window.location.origin + '/join?code=' + code, 'Invite link copied');
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  }

  async function copyCode() {
    await copyValue(code ?? '', 'Room code copied');
  }

  async function shareInvite() {
    if (!navigator.share) { await copyLink(); return; }
    try {
      await navigator.share({ title: 'Join my Verdikt room', text: 'Join my Verdikt room with code ' + code, url: window.location.origin + '/join?code=' + code });
      setToast('Invite ready to share');
    } catch { /* The user dismissed native sharing. */ }
  }

  // FIX 1 (host controls): use activePlayers for the start button count
  const activePlayers = players.filter((p) => p.isActive);
  const hostName = players.find((player) => player.isHost)?.name ?? 'the host';
  const requestedQuestionCount = Number(session?.questionCount ?? 10);
  const canNativeShare = typeof navigator !== 'undefined' && 'share' in navigator;
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
        <div className="lobby__share-actions">
          <button className="lobby__copy" onClick={() => void copyLink()} type="button">
            {copied ? 'Copied!' : 'Copy link'}
          </button>
          {canNativeShare && <button className="lobby__share-button" onClick={() => void shareInvite()} type="button">Share</button>}
          <button className="lobby__share-button" onClick={() => void copyCode()} type="button">Copy code</button>
        </div>
        <ConnectionBanner status={connectionStatus} />
      </div>

      <Card className="lobby__players">
        <p className="lobby__players-title">
          {activePlayers.length} {activePlayers.length === 1 ? 'player' : 'players'} in · {timerLabel}
        </p>
        <ul className="lobby__list">
          {players.map((p) => (
            <li key={p.id} className={'lobby__player' + (p.isActive ? '' : ' lobby__player--inactive')} aria-label={p.isActive ? p.name : p.name + ' disconnected'}>
              <span className="lobby__avatar">{p.name.charAt(0).toUpperCase()}</span>
              <span className="lobby__player-name">{p.name}</span>
              {p.isHost && <span className="lobby__host-tag">host</span>}
            </li>
          ))}
        </ul>
      </Card>

      {!isHost && (
        <div className="lobby__waiting">
          <p className="lobby__hint">Waiting for {hostName} to start…</p>
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
              Questions <span className="lobby__optional">{questions.length} / {requestedQuestionCount} playable</span>
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
              aria-describedby="question-character-count"
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
          <p id="question-character-count" className="lobby__char-count">{draft.length} / 120</p>

          {loadingQuestions ? (
            <p className="lobby__loading">Picking questions…</p>
          ) : (
            <Card className="lobby__custom-list" padded={false}>
              {questions.map((q, index) => (
                <Fragment key={q.id}>
                  {index === 0 && !q.isCustom && <h3 className="lobby__list-heading">Generated questions</h3>}
                  {q.isCustom && (index === 0 || !questions[index - 1].isCustom) && <h3 className="lobby__list-heading">Custom questions</h3>}
                  <div className={'lobby__custom-item ' + (q.isCustom ? 'lobby__custom-item--custom' : '')}>
                  <span className="lobby__custom-text">{q.text}</span>
                  <button className="lobby__custom-remove" onClick={() => removeQuestion(q.id)} type="button" aria-label="Remove">×</button>
                </div>
                </Fragment>
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
      {toast && <Toast message={toast} onDismiss={() => setToast('')} />}
    </div>
  );
}
