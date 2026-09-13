import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '../components/Button';
import TextField from '../components/TextField';
import BackButton from '../components/BackButton';
import './FormScreen.css';
import { saveSession } from '../session';
import { apiRequest, ApiError } from '../api/client';

const TIMER_OPTIONS = [
  { label: 'No timer', value: null, desc: 'host moves manually' },
  { label: '10 sec', value: 10, desc: 'quick fire' },
  { label: '15 sec', value: 15, desc: 'balanced pace' },
  { label: '20 sec', value: 20, desc: 'more time to debate' },
];
const QUESTION_COUNT_OPTIONS = [5, 10, 15, 20];
interface CreateErrors { roomName?: string; hostName?: string; form?: string; }
interface CreatedRoom {
  id: string; code: string; name: string; voteMode: 'PUBLIC' | 'ANONYMOUS';
  questionDurationSeconds: number | null; maxQuestions: number; playerToken: string;
  players: { id: string; name: string; isHost: boolean }[];
}

export default function CreateRoom() {
  const navigate = useNavigate();
  const [roomName, setRoomName] = useState('');
  const [hostName, setHostName] = useState('');
  const [voteMode, setVoteMode] = useState<'PUBLIC' | 'ANONYMOUS'>('PUBLIC');
  const [questionDurationSeconds, setQuestionDurationSeconds] = useState<number | null>(15);
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<CreateErrors>({});
  const [questionCount, setQuestionCount] = useState(10);

  async function submitRoomCreate(): Promise<CreatedRoom> {
    for (let attempt = 0; attempt < 2; attempt += 1) {
      try {
        return await apiRequest<CreatedRoom>('/api/rooms', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ name: roomName.trim(), hostName: hostName.trim(), voteMode, questionDurationSeconds, questionCount }),
        });
      } catch (requestError) {
        if (requestError instanceof ApiError && (requestError.status === 502 || requestError.status === 503) && attempt === 0) {
          await new Promise((resolve) => window.setTimeout(resolve, 600));
          continue;
        }
        if (attempt === 0) { await new Promise((resolve) => window.setTimeout(resolve, 600)); continue; }
        throw requestError;
      }
    }
    throw new Error('Room creation failed unexpectedly.');
  }

  async function handleCreate() {
    if (roomName.trim().length < 2) { setErrors({ roomName: 'Give your room a name first.' }); document.getElementById('roomName')?.focus(); return; }
    if (hostName.trim().length < 2) { setErrors({ hostName: 'What should we call you?' }); document.getElementById('hostName')?.focus(); return; }
    setErrors({});
    setLoading(true);
    try {
      const data = await submitRoomCreate();
      const me = data.players.find((p) => p.isHost) ?? data.players[0];
      if (!me) throw new Error('The room was created without a host player.');
      saveSession({ playerToken: data.playerToken, playerId: me.id, playerName: hostName.trim(), isHost: true, roomId: data.id, roomCode: data.code, roomName: data.name, voteMode: data.voteMode, questionDurationSeconds: data.questionDurationSeconds, questionCount: data.maxQuestions ?? questionCount });
      navigate('/lobby/' + data.code);
    } catch (error) {
      const message = error instanceof ApiError
        ? (error.status === 502 || error.status === 503 ? 'Verdikt is waking up - try again in a few seconds.' : error.message)
        : 'Could not reach Verdikt. Check your connection and try again.';
      setErrors({ form: message });
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="screen form-screen">
      <BackButton label="Back" />
      <div className="form-screen__intro">
        <p className="form-screen__eyebrow">New room</p>
        <h1 className="form-screen__title">Start a room</h1>
        <p className="form-screen__subtitle">Set the vibe, then send the invite to your GC.</p>
      </div>
      <form className="form-screen__form" onSubmit={(event) => { event.preventDefault(); void handleCreate(); }}>
        <TextField id="roomName" label="Room name" placeholder="e.g. Soro Soke Gang" value={roomName} error={errors.roomName} onChange={(event) => { setRoomName(event.target.value); setErrors((current) => ({ ...current, roomName: undefined })); }} maxLength={30} autoComplete="off" />
        <TextField id="hostName" label="Your name" placeholder="What should we call you?" value={hostName} error={errors.hostName} onChange={(event) => { setHostName(event.target.value); setErrors((current) => ({ ...current, hostName: undefined })); }} maxLength={30} autoComplete="nickname" />
        <fieldset className="choice-group">
          <legend>Vote mode</legend>
          <div className="choice-group__grid choice-group__grid--two">
            {(['PUBLIC', 'ANONYMOUS'] as const).map((mode) => (
              <button key={mode} className={'choice-card ' + (voteMode === mode ? 'choice-card--active' : '')} type="button" aria-pressed={voteMode === mode} onClick={() => setVoteMode(mode)}>
                <span className="choice-card__title">{mode === 'PUBLIC' ? 'Public' : 'Anonymous'}</span>
                <span className="choice-card__desc">{mode === 'PUBLIC' ? 'Everyone sees who voted' : 'Only totals are shown'}</span>
              </button>
            ))}
          </div>
        </fieldset>
        <fieldset className="choice-group">
          <legend>Time per question</legend>
          <div className="choice-group__grid choice-group__grid--two">
            {TIMER_OPTIONS.map((option) => (
              <button key={option.label} className={'choice-card ' + (questionDurationSeconds === option.value ? 'choice-card--active' : '')} type="button" aria-pressed={questionDurationSeconds === option.value} onClick={() => setQuestionDurationSeconds(option.value)}>
                <span className="choice-card__title">{option.label}</span>
                <span className="choice-card__desc">{option.desc}</span>
              </button>
            ))}
          </div>
        </fieldset>
        <fieldset className="choice-group">
          <legend>Number of questions</legend>
          <div className="choice-group__grid choice-group__grid--four">
            {QUESTION_COUNT_OPTIONS.map((count) => (
              <button key={count} className={'choice-card choice-card--compact ' + (questionCount === count ? 'choice-card--active' : '')} type="button" aria-pressed={questionCount === count} onClick={() => setQuestionCount(count)}>
                <span className="choice-card__title">{count}</span>
              </button>
            ))}
          </div>
        </fieldset>
        {errors.form && <p className="form-screen__error" role="alert">{errors.form}</p>}
        <Button fullWidth type="submit" loading={loading}>{loading ? 'Creating room' : 'Create room'}</Button>
      </form>
    </main>
  );
}
