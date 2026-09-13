import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { saveSession } from '../session';
import Button from '../components/Button';
import TextField from '../components/TextField';
import BackButton from '../components/BackButton';
import HourglassIcon from '../components/icons/HourglassIcon';
import './FormScreen.css';
import { apiRequest, ApiError } from '../api/client';

function normalizeRoomCode(value: string | null): string {
  return (value ?? '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6);
}

export default function JoinRoom() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [playerName, setPlayerName] = useState('');
  const [code, setCode] = useState(normalizeRoomCode(searchParams.get('code')));
  const [roomExpired, setRoomExpired] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [codeError, setCodeError] = useState('');
  const [nameError, setNameError] = useState('');

  useEffect(() => {
    const fieldId = searchParams.get('code') ? 'playerName' : 'code';
    window.setTimeout(() => document.getElementById(fieldId)?.focus(), 0);
  }, [searchParams]);

  async function handleJoin() {
    if (code.trim().length !== 6) { setCodeError('Room codes are 6 characters.'); document.getElementById('code')?.focus(); return; }
    if (playerName.trim().length < 2) { setNameError('What should we call you?'); document.getElementById('playerName')?.focus(); return; }
    setError('');
    setCodeError('');
    setNameError('');
    setLoading(true);
    try {
      const data = await apiRequest<{
        id: string; code: string; name: string; voteMode: 'PUBLIC' | 'ANONYMOUS';
        questionDurationSeconds: number | null; maxQuestions: number; playerToken: string;
        players: { id: string; name: string; isHost: boolean }[];
      }>('/api/rooms/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code: code.trim(), playerName: playerName.trim() }),
      });
      const me = data.players.find((p) => p.name === playerName.trim());
      if (!me) throw new Error('The room did not return your player session.');
      saveSession({ playerToken: data.playerToken, playerId: me.id, playerName: playerName.trim(), isHost: false, roomId: data.id, roomCode: data.code, roomName: data.name, voteMode: data.voteMode, questionDurationSeconds: data.questionDurationSeconds, questionCount: data.maxQuestions });
      navigate('/lobby/' + data.code);
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 410) {
        setRoomExpired(true);
        return;
      }
      if (requestError instanceof ApiError && requestError.code === 'DUPLICATE_NAME') {
        setNameError(requestError.message);
        document.getElementById('playerName')?.focus();
        return;
      }
      if (requestError instanceof ApiError && requestError.code === 'ROOM_NOT_FOUND') {
        setCodeError(requestError.message);
        document.getElementById('code')?.focus();
        return;
      }
      setError(requestError instanceof ApiError ? requestError.message : 'Could not reach Verdikt. Check your connection and try again.');
    } finally {
      setLoading(false);
    }
  }

  if (roomExpired) {
    return (
      <main className="screen form-screen form-screen--empty">
        <div className="form-screen__empty-icon"><HourglassIcon /></div>
        <p className="form-screen__eyebrow">Room closed</p>
        <h1 className="form-screen__title">Room expired</h1>
        <p className="form-screen__subtitle">This room has closed. Ask your host to create a new one.</p>
        <Button fullWidth onClick={() => navigate('/')}>Back to home</Button>
      </main>
    );
  }

  return (
    <main className="screen form-screen">
      <BackButton label="Back" />
      <div className="form-screen__intro">
        <p className="form-screen__eyebrow">Join a room</p>
        <h1 className="form-screen__title">Drop the code</h1>
        <p className="form-screen__subtitle">Got an invite from your GC? You are seconds away.</p>
      </div>
      <form className="form-screen__form" onSubmit={(event) => { event.preventDefault(); void handleJoin(); }}>
        <TextField id="code" label="Room code" placeholder="e.g. VBLQMT" value={code} error={codeError} onChange={(event) => { setCode(normalizeRoomCode(event.target.value)); setCodeError(''); }} maxLength={6} autoComplete="off" autoCapitalize="characters" spellCheck={false} hint="Six letters from your host" className="textfield__input--code" />
        <TextField id="playerName" label="Your name" placeholder="What should we call you?" value={playerName} error={nameError} onChange={(event) => { setPlayerName(event.target.value); setNameError(''); }} maxLength={30} autoComplete="nickname" />
        {error && <p className="form-screen__error" role="alert">{error}</p>}
        <Button fullWidth type="submit" loading={loading}>{loading ? 'Joining room' : 'Join room'}</Button>
      </form>
    </main>
  );
}
