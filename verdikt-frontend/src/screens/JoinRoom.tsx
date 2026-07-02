import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useSearchParams } from "react-router-dom";
import { saveSession } from "../session";

import Button from "../components/Button";
import TextField from "../components/TextField";
import "./FormScreen.css";

export default function JoinRoom() {
  const navigate = useNavigate();

  const [playerName, setPlayerName] = useState("");
  const [roomExpired, setRoomExpired] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [searchParams] = useSearchParams();
  const [code, setCode] = useState(
    searchParams.get("code")?.toUpperCase() ?? "",
  );
 if (roomExpired) {
    return (
      <div className="screen form-screen">
        <div style={{ textAlign: "center", marginTop: "3rem" }}>
          <p style={{ fontSize: "2.5rem" }}>⏳</p>

          <h1 className="form-screen__title">Room expired</h1>

          <p className="form-screen__subtitle">
            This room has closed. Ask your host to create a new one.
          </p>

          <Button fullWidth onClick={() => navigate("/")}>
            Back to home
          </Button>
        </div>
      </div>
    );
  }
  async function handleJoin() {
    if (code.trim().length !== 6) {
      setError("Room codes are 6 characters");
      return;
    }
    if (playerName.trim().length < 2) {
      setError("What should we call you?");
      return;
    }
    setError("");
    setLoading(true);

    try {
      const res = await fetch(
        `${import.meta.env.VITE_API_URL}/api/rooms/join`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            code: code.trim(),
            playerName: playerName.trim(),
          }),
        },
      );

      if (res.status === 410) {
  setRoomExpired(true);
  return;
}
if (!res.ok) {
  const err = await res.json().catch(() => ({}));
  setError(err.message ?? "Couldn't find that room. Check the code.");
  return;
}

      const data = await res.json();
      const me = data.players.find(
        (p: { token?: string }) => p.token === data.playerToken,
      ) ?? data.players.find(
        (p: { name: string }) => p.name === playerName.trim(),
      );
      saveSession({
        playerToken: data.playerToken,
        playerId: me.id,
  playerName: playerName.trim(),
  isHost: false,
  roomId: data.id,
  roomCode: data.code,
  roomName: data.name,
  voteMode: data.voteMode,
  questionDurationSeconds: data.questionDurationSeconds,
  questionCount: data.maxQuestions,
});
      navigate(`/lobby/${data.code}`);
    } catch {
      setError("Couldn't find that room. Check the code.");
    } finally {
      setLoading(false);
    }
  }

  return (
    
    <div className="screen form-screen">
      <button
        className="form-screen__back"
        onClick={() => navigate(-1)}
        aria-label="Go back"
      >
        ←
      </button>

      <h1 className="form-screen__title">Join a room</h1>
      <p className="form-screen__subtitle">
        Got a code from your GC? Drop it in.
      </p>

      <div className="form-screen__fields">
        <TextField
          id="code"
          label="Room code"
          placeholder="e.g VBLQMT"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          maxLength={6}
          style={{
            letterSpacing: "0.15em",
            fontFamily: "var(--font-display)",
            fontWeight: 600,
          }}
        />
        <TextField
          id="playerName"
          label="Your name"
          placeholder="What should we call you?"
          value={playerName}
          onChange={(e) => setPlayerName(e.target.value)}
          maxLength={30}
        />

        {error && <p className="form-screen__error">{error}</p>}
      </div>

      <Button fullWidth onClick={handleJoin} disabled={loading}>
        {loading ? "Joining…" : "Join room"}
      </Button>
    </div>
  );
}
