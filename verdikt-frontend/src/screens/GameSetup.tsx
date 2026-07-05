import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Button from '../components/Button';
import { loadSession } from '../session';
import './GameSetup.css';

interface PreviewQuestion {
  id: string;
  text: string;
  spiceLevel: string;
  isCustom?: boolean;
}

export default function GameSetup() {
  const { code } = useParams();
  const navigate = useNavigate();
  const session = loadSession();

  const [questions, setQuestions] = useState<PreviewQuestion[]>([]);
  const [draft, setDraft] = useState('');
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!session) { navigate('/'); return; }
    fetchBatch();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function fetchBatch() {
    if (!session) return;
    setLoading(true);
    try {
      const res = await fetch(
        `${import.meta.env.VITE_API_URL}/api/rooms/${session.roomId}/game/preview-questions?count=15`,
        { headers: { 'X-Player-Token': session.playerToken } }
      );
      if (res.status === 404) {
        setError('No built-in questions exist yet. Add your own questions to start.');
        setQuestions([]);
        return;
      }
      const data = await res.json();
      setQuestions(data.map((q: PreviewQuestion) => ({ ...q, isCustom: false })));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't load questions. Try again.");
      } finally {
      setLoading(false);
    }
  }

  function removeQuestion(id: string) {
    setQuestions((prev) => prev.filter((q) => q.id !== id));
  }

  function addCustomQuestion() {
    const trimmed = draft.trim();
    if (trimmed.length < 5) return;
    setQuestions((prev) => [
      { id: `custom-${Date.now()}`, text: trimmed, spiceLevel: 'CUSTOM', isCustom: true },
      ...prev,
    ]);
    setDraft('');
  }

  async function handleStart() {
    if (!session || questions.length === 0) return;
    setStarting(true);
    setError('');

    const selectedQuestionIds = questions.filter((q) => !q.isCustom).map((q) => q.id);
    const customQuestionTexts = questions.filter((q) => q.isCustom).map((q) => q.text);

    try {
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
      if (res.status === 404) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message ?? 'No built-in questions exist yet. Add your own questions to start.');
      }
      if (!res.ok) throw new Error();
      navigate(`/play/${code}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't start the game. Try again.");
      setStarting(false);
    }
  }

  const timerLabel = session?.questionDurationSeconds
    ? `${session.questionDurationSeconds}s per question`
    : 'No timer — manual advance';

  return (
    <div className="screen setup">
      <button className="setup__back" onClick={() => navigate(-1)} aria-label="Go back">
        ←
      </button>

      <h1 className="setup__title">Set up the game</h1>
      <p className="setup__subtitle">Remove what you don't want, add your own, keep the rest.</p>

      <div className="setup__timer-confirm">
        <span className="setup__timer-confirm-label">⏱ {timerLabel}</span>
        <button className="setup__timer-confirm-edit" onClick={() => navigate(-1)} type="button">
          change in lobby
        </button>
      </div>

      <div className="setup__section setup__section--grow">
        <div className="setup__list-header">
          <p className="setup__label" style={{ margin: 0 }}>
            Questions <span className="setup__count">{questions.length}</span>
          </p>
          <button className="setup__shuffle" onClick={fetchBatch} type="button" disabled={loading}>
            {loading ? 'Shuffling…' : '🔀 Shuffle batch'}
          </button>
        </div>

        <div className="setup__add-row">
          <input
            className="setup__add-input"
            placeholder="Type your own question…"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && addCustomQuestion()}
            maxLength={120}
          />
          <button className="setup__add-btn" onClick={addCustomQuestion} type="button" aria-label="Add question">
            +
          </button>
        </div>

        {loading ? (
          <p className="setup__loading">Picking questions…</p>
        ) : (
          <div className="setup__question-list">
            {questions.map((q) => (
              <div key={q.id} className={`setup__question-item ${q.isCustom ? 'setup__question-item--custom' : ''}`}>
                <span className="setup__question-text">{q.text}</span>
                <button
                  className="setup__custom-remove"
                  onClick={() => removeQuestion(q.id)}
                  aria-label="Remove question"
                  type="button"
                >
                  ×
                </button>
              </div>
            ))}
            {questions.length === 0 && (
              <p className="setup__empty">No questions left — add your own or shuffle a new batch.</p>
            )}
          </div>
        )}

        {error && <p className="form-screen__error">{error}</p>}
      </div>

      <div className="setup__footer">
        <p className="setup__footer-hint">{questions.length} questions ready</p>
        <Button fullWidth onClick={handleStart} disabled={questions.length === 0 || starting}>
          {starting ? 'Starting…' : 'Start the game'}
        </Button>
      </div>
    </div>
  );
}
