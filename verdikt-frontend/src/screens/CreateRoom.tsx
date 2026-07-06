import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '../components/Button';
import TextField from '../components/TextField';
import './FormScreen.css';
import { saveSession } from '../session';

const TIMER_OPTIONS = [
  { label: 'No timer', value: null, desc: 'host moves manually' },
  { label: '10s', value: 10, desc: '' },
  { label: '15s', value: 15, desc: '' },
  { label: '20s', value: 20, desc: '' },
];

const QUESTION_COUNT_OPTIONS = [5, 10, 15, 20];
export default function CreateRoom() {
  const navigate = useNavigate();
  const [roomName, setRoomName] = useState('');
  const [hostName, setHostName] = useState('');
  const [voteMode, setVoteMode] = useState<'PUBLIC' | 'ANONYMOUS'>('PUBLIC');
  const [questionDurationSeconds, setQuestionDurationSeconds] = useState<number | null>(15);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [questionCount, setQuestionCount] = useState(10);

  const sleep = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms));

  async function submitRoomCreate(): Promise<Response> {
    for (let attempt = 0; attempt < 2; attempt += 1) {
      try {
        const res = await fetch(`${import.meta.env.VITE_API_URL}/api/rooms`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            name: roomName.trim(),
            hostName: hostName.trim(),
            voteMode,
            questionDurationSeconds,
            questionCount,
          }),
        });

        if ((res.status === 502 || res.status === 503) && attempt === 0) {
          await sleep(600);
          continue;
        }

        return res;
      } catch (error) {
        if (attempt === 0) {
          await sleep(600);
          continue;
        }
        throw error;
      }
    }

    throw new Error('Room creation failed unexpectedly.');
  }

  async function handleCreate() {
    if (roomName.trim().length < 2) { setError('Give your room a name first'); return; }
    if (hostName.trim().length < 2) { setError('What should we call you?'); return; }
    setError('');
    setLoading(true);

    try {
      const res = await submitRoomCreate();

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        if (res.status === 502 || res.status === 503) {
          setError('The backend is waking up or unreachable right now. Give it a few seconds and try again.');
        } else {
          setError(err.message ?? "Couldn't create the room. Try again.");
        }
        return;
      }

      const data = await res.json();
      const me = data.players.find((p: { token?: string; isHost?: boolean }) => p.token === data.playerToken)
        ?? data.players.find((p: { isHost?: boolean }) => p.isHost);

      saveSession({
        playerToken: data.playerToken,
        playerId: me.id,
        playerName: hostName.trim(),
        isHost: true,
        roomId: data.id,
        roomCode: data.code,
        roomName: data.name,
        voteMode: data.voteMode,
        questionDurationSeconds: data.questionDurationSeconds,
        questionCount: data.maxQuestions ?? questionCount,
      });

      navigate(`/lobby/${data.code}`);
    } catch {
      setError('The backend is unreachable right now. Try again in a moment.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="screen form-screen">
      <button className="form-screen__back" onClick={() => navigate(-1)} aria-label="Go back">
        ←
      </button>

      <h1 className="form-screen__title">Start a room</h1>
      <p className="form-screen__subtitle">Set it up, then send the link to your GC.</p>

      <div className="form-screen__fields">
        <TextField
          id="roomName"
          label="Room name"
          placeholder="e.g Soro Soke Gang"
          value={roomName}
          onChange={(e) => setRoomName(e.target.value)}
          maxLength={30}
        />
        <TextField
          id="hostName"
          label="Your name"
          placeholder="What should we call you?"
          value={hostName}
          onChange={(e) => setHostName(e.target.value)}
          maxLength={30}
        />

        <div className="vote-mode">
          <span className="vote-mode__label">Vote mode</span>
          <div className="vote-mode__options">
            <button
              className={`vote-mode__option ${voteMode === 'PUBLIC' ? 'vote-mode__option--active' : ''}`}
              onClick={() => setVoteMode('PUBLIC')}
              type="button"
            >
              <span className="vote-mode__option-title">Public</span>
              <span className="vote-mode__option-desc">everyone sees who voted</span>
            </button>
            <button
              className={`vote-mode__option ${voteMode === 'ANONYMOUS' ? 'vote-mode__option--active' : ''}`}
              onClick={() => setVoteMode('ANONYMOUS')}
              type="button"
            >
              <span className="vote-mode__option-title">Anonymous</span>
              <span className="vote-mode__option-desc">only totals are shown</span>
            </button>
          </div>
        </div>

        <div className="vote-mode">
          <span className="vote-mode__label">Time per question</span>
          <div className="vote-mode__options">
           {TIMER_OPTIONS.map((opt) => (
  <button
    key={opt.label}
    className={`vote-mode__option ${questionDurationSeconds === opt.value ? 'vote-mode__option--active' : ''}`}
    onClick={() => setQuestionDurationSeconds(opt.value)}
    type="button"
  >
    <span className="vote-mode__option-title">{opt.label}</span>
    {opt.desc && <span className="vote-mode__option-desc">{opt.desc}</span>}
  </button>
))}
          </div>
        </div>
<div className="vote-mode">
  <span className="vote-mode__label">Number of questions</span>
  <div className="vote-mode__options">
    {QUESTION_COUNT_OPTIONS.map((n) => (
      <button
        key={n}
        className={`vote-mode__option ${questionCount === n ? 'vote-mode__option--active' : ''}`}
        onClick={() => setQuestionCount(n)}
        type="button"
      >
        <span className="vote-mode__option-title">{n}</span>
      </button>
    ))}
  </div>
</div>
        {error && <p className="form-screen__error">{error}</p>}
      </div>

      <Button fullWidth onClick={handleCreate} disabled={loading}>
        {loading ? 'Creating…' : 'Create room'}
      </Button>
    </div>
  );
}
