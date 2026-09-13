import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import Button from '../components/Button';
import HourglassIcon from '../components/icons/HourglassIcon';
import AlarmIcon from '../components/icons/AlarmIcon';
import Loader from '../components/Loader';
import ConnectionBanner from '../components/ConnectionBanner';
import Toast from '../components/Toast';
import { loadSession } from '../session';
import { useSocket } from '../useSocket';
import './Play.css';
import { apiRequest, ApiError } from '../api/client';

interface PlayerResult {
  id: string;
  name: string;
  votes: number;
  voters: { id: string; name: string }[];
}

interface Question {
  id: string;
  text: string;
  questionIndex: number;
  totalQuestions: number;
  startedAt?: string;   // ISO string from backend
}

interface CurrentQuestionPayload {
  question?: { questionId?: string; text?: string; startedAt?: string };
  questionId?: string;
  id?: string;
  text?: string;
  questionIndex?: number;
  totalQuestions?: number;
  startedAt?: string;
}

export default function Play() {
  const { code } = useParams();
  const navigate = useNavigate();

  const [session] = useState(() => loadSession());

  const [question, setQuestion] = useState<Question | null>(null);
  const [playersMap, setPlayersMap] = useState<Map<string, PlayerResult>>(new Map());
  // locked order: set once per question, never re-sorted — prevents layout jumps
  const [lockedOrder, setLockedOrder] = useState<string[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const selectedRef = useRef<Set<string>>(new Set());
  const voteRequestSeqRef = useRef(0);
  const [removingId, setRemovingId] = useState<string | null>(null);
  const [timeLeft, setTimeLeft] = useState<number | null>(null);
  const [showEndConfirm, setShowEndConfirm] = useState(false);
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');
  const [nexting, setNexting] = useState(false);
  const [ending, setEnding] = useState(false);
  const [endGameError, setEndGameError] = useState('');
  const [roomExpired, setRoomExpired] = useState(false);
  const [connectionStatus, setConnectionStatus] = useState<'connecting' | 'connected' | 'reconnecting'>('connecting');
  const isHost = session?.isHost ?? false;
  const selectionCacheKey = session ? `verdikt:vote-selection:${session.roomId}` : null;

  // keep a ref to the hydrate function so we can call it from WS reconnect
  const hydrateVotesRef = useRef<(() => Promise<void>) | null>(null);

  useEffect(() => {
    selectedRef.current = selected;
  }, [selected]);

  const loadCachedSelection = useCallback((questionId: string): Set<string> => {
    if (!selectionCacheKey) return new Set();
    try {
      const raw = sessionStorage.getItem(`${selectionCacheKey}:${questionId}`);
      if (!raw) return new Set();
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) return new Set();
      return new Set(parsed.filter((id): id is string => typeof id === 'string'));
    } catch {
      return new Set();
    }
  }, [selectionCacheKey]);

  function saveCachedSelection(questionId: string, selection: Set<string>) {
    if (!selectionCacheKey) return;
    const key = `${selectionCacheKey}:${questionId}`;
    if (selection.size === 0) {
      sessionStorage.removeItem(key);
      return;
    }
    sessionStorage.setItem(key, JSON.stringify(Array.from(selection)));
  }

  // compute remaining time from startedAt so refreshes/late-joiners get correct countdown
  function computeTimeLeft(startedAt: string | undefined, durationSeconds: number | null): number | null {
    if (!durationSeconds) return null;
    if (!startedAt) return durationSeconds;
    const elapsed = Math.floor((Date.now() - new Date(startedAt).getTime()) / 1000);
    return Math.max(0, durationSeconds - elapsed);
  }

  useEffect(() => {
    if (!session) { navigate('/'); return; }

    let cancelled = false;

    const sleep = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms));

    async function hydrateVotes(currentMap: Map<string, PlayerResult>, currentQuestionId?: string) {
      const existingVotes = await apiRequest<{ voterId?: string; voterName?: string; votedForId: string }[]>(
        `/api/rooms/${session!.roomId}/votes/current`,
        { playerToken: session!.playerToken },
      );

      const map = new Map(currentMap);
      const myVotedFor = new Set<string>();

      for (const v of existingVotes) {
        const target = map.get(v.votedForId);
        if (target) {
          // only add voter to list if PUBLIC (backend omits voterId/voterName in ANONYMOUS)
          if (v.voterId && !target.voters.some((x) => x.id === v.voterId!)) {
            target.votes += 1;
            target.voters = [...target.voters, { id: v.voterId!, name: v.voterName ?? '?' }];
          } else if (!v.voterId) {
            // ANONYMOUS mode: just count
            target.votes += 1;
          }
        }
        if (v.voterId === session!.playerId) myVotedFor.add(v.votedForId);
      }

      const cachedSelection = currentQuestionId ? loadCachedSelection(currentQuestionId) : new Set<string>();
      const nextSelection =
        session!.voteMode === 'ANONYMOUS' && cachedSelection.size > 0
          ? cachedSelection
          : myVotedFor;

      setPlayersMap(new Map(map));
      setSelected(nextSelection);
      selectedRef.current = nextSelection;
      return nextSelection;
    }

    async function init(attempt = 0) {
      if (!session || cancelled) return;
      try {
        // 1. current question
        if (cancelled) return;
        const qData = await apiRequest<CurrentQuestionPayload>(
          `/api/rooms/${session.roomId}/game/current-question`,
          { playerToken: session.playerToken },
        );
        const nestedQuestion = qData.question;
        const questionId = nestedQuestion?.questionId ?? qData.questionId ?? qData.id;
        const questionText = nestedQuestion?.text ?? qData.text;
        if (!questionId || !questionText) throw new Error('The room returned an incomplete question.');
        const q: Question = {
          id: questionId,
          text: questionText,
          questionIndex: qData.questionIndex ?? 1,
          totalQuestions: qData.totalQuestions ?? 10,
          startedAt: qData.startedAt ?? nestedQuestion?.startedAt,
        };
        setQuestion(q);
        setTimeLeft(computeTimeLeft(q.startedAt, session.questionDurationSeconds));

        // 2. player list
        if (cancelled) return;
        const roomData = await apiRequest<{ players?: { id: string; name: string }[] }>(
          '/api/rooms/rejoin',
          { playerToken: session.playerToken },
        );
        const basePlayers: PlayerResult[] = (roomData.players ?? []).map(
          (p: { id: string; name: string }) => ({ id: p.id, name: p.name, votes: 0, voters: [] })
        );

        const baseMap = new Map<string, PlayerResult>();
        for (const p of basePlayers) baseMap.set(p.id, { ...p });

        // lock order now (join order), never re-sort again until next question
        setLockedOrder(basePlayers.map((p) => p.id));

        // 3. hydrate existing votes
        await hydrateVotes(baseMap, q.id);

        // expose hydrateVotes for WS reconnect
        hydrateVotesRef.current = async () => {
          const freshMap = new Map<string, PlayerResult>();
          for (const p of basePlayers) freshMap.set(p.id, { id: p.id, name: p.name, votes: 0, voters: [] });
          await hydrateVotes(freshMap, q.id);
        };

      } catch (error) {
        if (cancelled) return;
        const status = error instanceof ApiError ? error.status : undefined;
        if (attempt < 3 && (status === 400 || status === 404 || status === 500 || status === 502 || status === 503 || status === 504 || status === undefined)) {
          await sleep(250 * (attempt + 1));
          await init(attempt + 1);
          return;
        }
        if (error instanceof ApiError && error.status === 410) setRoomExpired(true);
        else setError(error instanceof ApiError ? error.message : 'Lost connection. Refresh and rejoin.');
      }
    }

    void init();

    return () => {
      cancelled = true;
    };
  }, [navigate, session, loadCachedSelection]);

  // Recalculate from the server timestamp so sleeping tabs catch up accurately.
  useEffect(() => {
    if (!question || !session?.questionDurationSeconds) return;
    const tick = () => setTimeLeft(computeTimeLeft(question.startedAt, session.questionDurationSeconds));
    tick();
    const id = window.setInterval(tick, 1000);
    return () => window.clearInterval(id);
  }, [question, session]);

  const handleQuestionAdvanced = useCallback(
    (payload: Record<string, unknown>) => {
      const q = payload.question as {
        questionId: string;
        text: string;
        questionIndex: number;
        totalQuestions: number;
        startedAt?: string;
      };
      const newQ: Question = {
        id: q.questionId,
        text: q.text,
        questionIndex: q.questionIndex,
        totalQuestions: q.totalQuestions,
        startedAt: q.startedAt,
      };
      setQuestion(newQ);
      setSelected(new Set());
      selectedRef.current = new Set();
      // reset votes but KEEP locked order (same players, new question)
      setPlayersMap((prev) => {
        const next = new Map(prev);
        for (const [id, p] of next) next.set(id, { ...p, votes: 0, voters: [] });
        return next;
      });
      setTimeLeft(computeTimeLeft(q.startedAt, session?.questionDurationSeconds ?? null));
      // re-hydrate in case we missed events during question transition
      hydrateVotesRef.current?.();
    },
    [session]
  );

  const applyVoteState = useCallback((incomingVotes: Array<{ voterId?: string; voterName?: string; votedForId: string }>) => {
    setPlayersMap((prev) => {
      const next = new Map(prev);
      for (const [id, player] of next) {
        next.set(id, { ...player, votes: 0, voters: [] });
      }

      for (const vote of incomingVotes) {
        const votedForId = String(vote.votedForId);
        const target = next.get(votedForId);
        if (!target) continue;

        const voterId = vote.voterId ? String(vote.voterId) : null;
        const voterName = vote.voterName as string | undefined;

        next.set(votedForId, {
          ...target,
          votes: target.votes + 1,
          voters: voterId
            ? target.voters.some((v) => v.id === voterId)
              ? target.voters
              : [...target.voters, { id: voterId, name: voterName ?? '?' }]
            : target.voters,
        });
      }

      return next;
    });
  }, []);

  const subscriptions = useMemo(
    () => [
      {
        topic: `/topic/room/${session?.roomId}/game`,
        handler: (payload: Record<string, unknown>) => {
          if (payload.type === 'QUESTION_ADVANCED') handleQuestionAdvanced(payload);
          if (payload.type === 'GAME_ENDED') navigate(`/report-card/${code}`);
        },
      },
      {
        topic: `/topic/room/${session?.roomId}/votes`,
        handler: (payload: Record<string, unknown>) => {
          if (payload.type !== 'VOTE_STATE') return;

          const incomingVotes = Array.isArray(payload.votes)
            ? (payload.votes as Array<{ voterId?: string; voterName?: string; votedForId: string }>)
            : [];

          applyVoteState(incomingVotes);
        },
      },
    ],
    [session?.roomId, handleQuestionAdvanced, navigate, code, applyVoteState]
  );

  const handleSocketConnected = useCallback(() => {
    setConnectionStatus('connected');
    hydrateVotesRef.current?.();
  }, []);

  const handleSocketDisconnected = useCallback((status: 'connecting' | 'connected' | 'reconnecting') => {
    setConnectionStatus(status);
  }, []);

  useSocket(session?.roomId, subscriptions, handleSocketConnected, handleSocketDisconnected, session?.playerToken);

  async function castVote(playerId: string) {
    if (!session || !question) return;
    const questionId = question.id;
    const previousSelection = new Set(selectedRef.current);
    const nextSelection = new Set(selectedRef.current);
    const wasSelected = selectedRef.current.has(playerId);
    const requestSeq = ++voteRequestSeqRef.current;

    if (wasSelected) {
      nextSelection.delete(playerId);
      setRemovingId(playerId);
      window.setTimeout(() => setRemovingId(null), 220);
    } else {
      nextSelection.add(playerId);
      setRemovingId(null);
    }

      setSelected(nextSelection);
      selectedRef.current = nextSelection;
      if (session.voteMode === 'ANONYMOUS') {
        saveCachedSelection(questionId, nextSelection);
      }

      try {
      const votes = await apiRequest<Array<{ voterId?: string; voterName?: string; votedForId: string }>>(
        `/api/rooms/${session.roomId}/votes`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          playerToken: session.playerToken,
          body: JSON.stringify({ questionId, votedForPlayerIds: Array.from(nextSelection) }),
        },
      );

      if (requestSeq !== voteRequestSeqRef.current) return;
      if (!votes) {
        setSelected(previousSelection);
        selectedRef.current = previousSelection;
        if (session.voteMode === 'ANONYMOUS') {
          saveCachedSelection(questionId, previousSelection);
        }
        setToast('Vote failed. Try again.');
        return;
      }

      applyVoteState(votes);
    } catch (error) {
      if (requestSeq === voteRequestSeqRef.current) {
        setSelected(previousSelection);
        selectedRef.current = previousSelection;
        if (session.voteMode === 'ANONYMOUS') {
          saveCachedSelection(questionId, previousSelection);
        }
        setToast(error instanceof ApiError ? error.message : 'Vote failed. Check your connection and try again.');
      }
    }
  }

  async function handleNext() {
    if (!session) return;
    setNexting(true);
    try {
      const nextQuestion = await apiRequest<Record<string, unknown> | undefined>(
        `/api/rooms/${session.roomId}/game/next-question`,
        { method: 'POST', playerToken: session.playerToken },
      );
      if (!nextQuestion) navigate(`/report-card/${code}`);
    } catch (error) {
      if (error instanceof ApiError && error.code === 'GAME_ALREADY_ENDED') navigate(`/report-card/${code}`);
      else setToast(error instanceof ApiError ? error.message : 'Could not reach the room. Check your connection and try again.');
    } finally {
      setNexting(false);
    }
  }

  async function confirmEndGame() {
    if (!session) return;
    setEndGameError('');
    setEnding(true);
    try {
      await apiRequest(`/api/rooms/${session.roomId}/game/end`, { method: 'POST', playerToken: session.playerToken });
      navigate(`/report-card/${code}`);
    } catch (e) {
      setEndGameError(e instanceof ApiError ? e.message : "Couldn't end the game.");
    } finally {
      setEnding(false);
    }
  }

  // display in locked join order — no re-sorting mid-question
  const players = useMemo(() => {
    return lockedOrder.map((id) => playersMap.get(id)).filter(Boolean) as PlayerResult[];
  }, [lockedOrder, playersMap]);

  const questionIndex = question?.questionIndex ?? 1;
  const totalQuestions = question?.totalQuestions ?? 10;
  const progress = (questionIndex / totalQuestions) * 100;
  const totalVotes = players.reduce((sum, p) => sum + p.votes, 0);
  const maxVotes = Math.max(...players.map((p) => p.votes), 0);
  const isPublic = session?.voteMode === 'PUBLIC';

  if (roomExpired) {
    return (
      <div className="screen play">
        <div className="play__status-card play__status-card--empty">
          <HourglassIcon className="play__status-icon" />
          <h1 className="play__status-title">Room expired</h1>
          <p className="play__status-copy">This room has closed.</p>
          <Button fullWidth onClick={() => navigate('/')}>Back to home</Button>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="screen play">
        <div className="play__status-card">
         <p className="play__status-copy">{error}</p>
          <Button onClick={() => window.location.reload()}>Try again</Button>
          <Button variant="secondary" onClick={() => navigate("/")}>Back to home</Button>
        </div>
      </div>
    );
  }

  if (!question) {
    return (
      <div className="screen play">
        <div className="play__status-card">
         <p className="play__status-copy">Loading…</p>
          <Loader message="Getting the next question" />
        </div>
      </div>
    );
  }

  return (
    <div className="screen play">

      <div className="play__topbar">
        <div className="play__progress-track">
          <div className="play__progress-fill" style={{ width: `${progress}%` }} />
        </div>
        {/* your name badge — always visible */}
        {session?.playerName && (
          <span className="play__you-badge">
            {session.playerName}
          </span>
        )}
        {isHost && (
          <button className="play__end-btn" onClick={() => setShowEndConfirm(true)} aria-label="End game">
            End
          </button>
        )}
      </div>

      <div className="play__meta-row">
        <p className="play__counter">Question {questionIndex} of {totalQuestions}</p>
        {timeLeft !== null && (
          <span className={`play__timer ${timeLeft <= 0 ? 'play__timer--up' : ''} ${timeLeft > 0 && timeLeft <= 5 ? 'play__timer--urgent' : ''}`} aria-live="polite">
            {timeLeft <= 0 ? "time's up" : `${timeLeft}s`}
          </span>
        )}
      </div>

      <h1 className="play__question">{question.text}</h1>

      <div className="play__hint-row">
        <p className="play__hint">
          <span className="play__live-dot" /> tap as many people as you want
        </p>
        <ConnectionBanner status={connectionStatus} />
      </div>

      <div className="play__poll">
        {players.map((p) => {
          const isSelected = selected.has(p.id);
          const isRemovingThis = removingId === p.id;
          const pct = totalVotes === 0 ? 0 : Math.round((p.votes / totalVotes) * 100);
          const isLeading = p.votes > 0 && p.votes === maxVotes;
          const fillPct = totalVotes > 0 ? pct : (isSelected ? 100 : 0);
          const visibleVoters = p.voters.slice(0, 5);   // show up to 5
          const extraVoters = p.voters.length - visibleVoters.length;

          return (
            <button
              type="button"
              key={p.id}
              aria-pressed={isSelected}
              className={['play__poll-row', isLeading ? 'play__poll-row--leading' : '', isSelected ? 'play__poll-row--selected' : '', isRemovingThis ? 'play__poll-row--removing' : ''].filter(Boolean).join(' ')}
              onClick={() => castVote(p.id)}
            >
              <div className="play__poll-top">
                <span className={`play__poll-radio ${isSelected ? 'play__poll-radio--checked' : ''}`}>
                  {isSelected && <span className="play__poll-radio-check">✓</span>}
                </span>
                <span className={`play__poll-avatar ${isSelected ? 'play__poll-avatar--checked' : ''}`}>
                  {p.name.charAt(0).toUpperCase()}
                </span>
                <span className="play__poll-name">{p.name}</span>
                {isSelected ? (
                  <span className={`play__your-choice-pill ${isRemovingThis ? 'play__your-choice-pill--removing' : ''}`}>
                    {isRemovingThis ? 'removed' : 'you voted'}
                  </span>
                ) : null}
                {(totalVotes > 0 || isSelected) && (
                  <span className="play__poll-pct">{pct}%</span>
                )}
              </div>

              <div className="play__poll-bar-track">
                <div
                  className={`play__poll-bar-fill ${isLeading ? 'play__poll-bar-fill--leading' : ''}`}
                  style={{ transform: `scaleX(${fillPct / 100})` }}
                />
              </div>

              {/* voter names — PUBLIC mode only, shown to everyone */}
              {isPublic && p.voters.length > 0 && (
                <div className="play__poll-voters">
                  <div className="play__poll-voter-stack">
                    {visibleVoters.map((v) => (
                      <span key={v.id} className="play__poll-voter-avatar" title={v.name}>
                        {v.name.charAt(0).toUpperCase()}
                      </span>
                    ))}
                    {extraVoters > 0 && (
                      <span className="play__poll-voter-avatar play__poll-voter-avatar--more">
                        +{extraVoters}
                      </span>
                    )}
                  </div>
                  <span className="play__poll-voter-names">
                    {visibleVoters.map((v) => v.name).join(', ')}
                    {extraVoters > 0 && ` +${extraVoters} more`} voted
                  </span>
                </div>
              )}

              {/* ANONYMOUS mode: just show vote count text */}
              {!isPublic && p.votes > 0 && (
                <div className="play__poll-voters">
                  <span className="play__poll-voter-names">
                    {p.votes} {p.votes === 1 ? 'vote' : 'votes'}
                  </span>
                </div>
              )}
            </button>
          );
        })}
      </div>

      {timeLeft !== null && timeLeft <= 0 && (
        <p className="play__nudge"><AlarmIcon style={{ verticalAlign: 'middle', marginRight: 6, fontSize: '1em' }} /> Time's up</p>
      )}

      {isHost && (
        <div className="play__footer">
         <Button fullWidth variant="secondary" onClick={() => void handleNext()} loading={nexting}>
            Next question →
          </Button>
        </div>
      )}

      {showEndConfirm && (
        <div className="play__modal-overlay" onClick={() => setShowEndConfirm(false)}>
          <div className="play__modal" onClick={(e) => e.stopPropagation()}>
            <h2 className="play__modal-title">End the game now?</h2>
            <p className="play__modal-text">
              Everyone will jump straight to the report card with whatever's been voted so far.
            </p>
            {endGameError && <p className="play__modal-error">{endGameError}</p>}
            <div className="play__modal-actions">
              <Button variant="secondary" fullWidth onClick={() => { setShowEndConfirm(false); setEndGameError(''); }}>
                Keep playing
              </Button>
            <Button variant="danger" fullWidth onClick={() => void confirmEndGame()} loading={ending}>
                End game
              </Button>
            </div>
          </div>
        </div>
      )}
      {toast && <Toast message={toast} tone={toast.includes('failed') || toast.includes('Couldn') ? 'error' : 'success'} onDismiss={() => setToast('')} />}
    </div>
  );
}
