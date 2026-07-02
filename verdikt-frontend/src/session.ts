const KEY = 'verdikt_session';

export interface Session {
  playerToken: string;   // UUID from backend
  playerId: string;      // UUID
  playerName: string;
  isHost: boolean;
  roomId: string;        // UUID
  roomCode: string;
  roomName: string;
  voteMode: 'PUBLIC' | 'ANONYMOUS';
  questionDurationSeconds: number | null;
  questionCount: string | number;
}

export function saveSession(s: Session) {
  sessionStorage.setItem(KEY, JSON.stringify(s));
}

export function loadSession(): Session | null {
  const raw = sessionStorage.getItem(KEY);
  if (raw) return JSON.parse(raw) as Session;

  const legacyRaw = localStorage.getItem(KEY);
  if (!legacyRaw) return null;

  const parsed = JSON.parse(legacyRaw) as Session;
  sessionStorage.setItem(KEY, JSON.stringify(parsed));
  localStorage.removeItem(KEY);
  return parsed;
}

export function clearSession() {
  sessionStorage.removeItem(KEY);
  localStorage.removeItem(KEY);
}