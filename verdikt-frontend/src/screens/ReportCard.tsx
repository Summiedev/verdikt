import { useState, useEffect, useRef, useCallback } from "react";
import { useParams, useNavigate } from "react-router-dom";
import html2canvas from "html2canvas";
import Stamp from "../components/Stamp";
import Button from "../components/Button";
import FlameIcon from "../components/icons/FlameIcon";
import TrophyIcon from "../components/icons/TrophyIcon";
import { loadSession, clearSession } from "../session";
import "./ReportCard.css";

interface PollResultEntry {
  playerId: string;
  playerName: string;
  voteCount: number;
  isWinner: boolean;
  votedByNames: string[] | null;
}

interface PollCard {
  questionId: string;
  questionText: string;
  orderIndex: number;
  results: PollResultEntry[];
}

interface PlayerCard {
  playerId: string;
  playerName: string;
  titlesWon: string[];
  totalVotesReceived: number;
}

interface LeaderboardEntry {
  playerName: string;
  totalVotesReceived: number;
  topTitle: string | null;
}

interface Overview {
  roomName: string;
  totalPlayers: number;
  totalQuestions: number;
  leaderboard: LeaderboardEntry[];
  verdict: string;
}

interface ReportCardData {
  overview: Overview;
  pollCards: PollCard[];
  playerCards: PlayerCard[];
}

type Slide =
  | { type: "intro"; data: Overview }
  | { type: "poll"; data: PollCard }
  | { type: "title"; data: PlayerCard }
  | { type: "final" };

export default function ReportCard() {
  useParams();
  const navigate = useNavigate();
  const session = loadSession();

  const [data, setData] = useState<ReportCardData | null>(null);
  const [slides, setSlides] = useState<Slide[]>([]);
  const [index, setIndex] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [expandedVoters, setExpandedVoters] = useState<Set<string>>(new Set());
  const [exporting, setExporting] = useState(false);

  const voteMode = session?.voteMode ?? "ANONYMOUS";
  const captureScale = Math.max(2, Math.ceil(window.devicePixelRatio || 2));

  const waitForPaint = useCallback(
    () => new Promise<void>((resolve) => {
      window.requestAnimationFrame(() => window.requestAnimationFrame(() => resolve()));
    }),
    [],
  );

  function crc32(bytes: Uint8Array) {
    const table = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) {
        c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
      }
      table[n] = c >>> 0;
    }

    let crc = 0xffffffff;
    for (let i = 0; i < bytes.length; i++) {
      crc = table[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8);
    }
    return (crc ^ 0xffffffff) >>> 0;
  }

  function writeUint16LE(buffer: Uint8Array, offset: number, value: number) {
    buffer[offset] = value & 0xff;
    buffer[offset + 1] = (value >>> 8) & 0xff;
  }

  function writeUint32LE(buffer: Uint8Array, offset: number, value: number) {
    buffer[offset] = value & 0xff;
    buffer[offset + 1] = (value >>> 8) & 0xff;
    buffer[offset + 2] = (value >>> 16) & 0xff;
    buffer[offset + 3] = (value >>> 24) & 0xff;
  }

  function concatBytes(chunks: Uint8Array[]) {
    const total = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
    const merged = new Uint8Array(total);
    let offset = 0;
    for (const chunk of chunks) {
      merged.set(chunk, offset);
      offset += chunk.length;
    }
    return merged;
  }

  function buildZip(files: { name: string; data: Uint8Array }[]) {
    const localParts: Uint8Array[] = [];
    const centralParts: Uint8Array[] = [];
    let offset = 0;

    for (const file of files) {
      const nameBytes = new TextEncoder().encode(file.name);
      const data = file.data;
      const crc = crc32(data);

      const localHeader = new Uint8Array(30 + nameBytes.length);
      writeUint32LE(localHeader, 0, 0x04034b50);
      writeUint16LE(localHeader, 4, 20);
      writeUint16LE(localHeader, 6, 0);
      writeUint16LE(localHeader, 8, 0);
      writeUint16LE(localHeader, 10, 0);
      writeUint16LE(localHeader, 12, 0);
      writeUint32LE(localHeader, 14, crc);
      writeUint32LE(localHeader, 18, data.length);
      writeUint32LE(localHeader, 22, data.length);
      writeUint16LE(localHeader, 26, nameBytes.length);
      writeUint16LE(localHeader, 28, 0);
      localHeader.set(nameBytes, 30);

      localParts.push(localHeader, data);

      const centralHeader = new Uint8Array(46 + nameBytes.length);
      writeUint32LE(centralHeader, 0, 0x02014b50);
      writeUint16LE(centralHeader, 4, 20);
      writeUint16LE(centralHeader, 6, 20);
      writeUint16LE(centralHeader, 8, 0);
      writeUint16LE(centralHeader, 10, 0);
      writeUint16LE(centralHeader, 12, 0);
      writeUint16LE(centralHeader, 14, 0);
      writeUint32LE(centralHeader, 16, crc);
      writeUint32LE(centralHeader, 20, data.length);
      writeUint32LE(centralHeader, 24, data.length);
      writeUint16LE(centralHeader, 28, nameBytes.length);
      writeUint16LE(centralHeader, 30, 0);
      writeUint16LE(centralHeader, 32, 0);
      writeUint16LE(centralHeader, 34, 0);
      writeUint16LE(centralHeader, 36, 0);
      writeUint32LE(centralHeader, 38, 0);
      writeUint32LE(centralHeader, 42, offset);
      centralHeader.set(nameBytes, 46);

      centralParts.push(centralHeader);
      offset += localHeader.length + data.length;
    }

    const centralDirectory = concatBytes(centralParts);
    const localData = concatBytes(localParts);
    const endRecord = new Uint8Array(22);
    writeUint32LE(endRecord, 0, 0x06054b50);
    writeUint16LE(endRecord, 4, 0);
    writeUint16LE(endRecord, 6, 0);
    writeUint16LE(endRecord, 8, files.length);
    writeUint16LE(endRecord, 10, files.length);
    writeUint32LE(endRecord, 12, centralDirectory.length);
    writeUint32LE(endRecord, 16, localData.length);
    writeUint16LE(endRecord, 20, 0);

    return new Blob([localData, centralDirectory, endRecord], { type: "application/zip" });
  }

  useEffect(() => {
    if (!session) {
      navigate("/");
      return;
    }

    fetch(
      `${import.meta.env.VITE_API_URL}/api/rooms/${session.roomId}/report-card`,
      {
        headers: { "X-Player-Token": session.playerToken },
      },
    )
      .then(async (r) => {
        if (!r.ok) {
          const err = await r.json().catch(() => ({}));
          throw new Error(err.message ?? "Couldn't load the report card.");
        }
        return r.json();
      })
      .then((raw: ReportCardData) => {
        const overview = raw.overview;
      
        const playerCards = raw.playerCards ?? [];

// inject 0-vote entries for players the backend omitted
const allPlayers = playerCards.map(pc => ({ playerId: pc.playerId, playerName: pc.playerName }));
const pollCards = (raw.pollCards ?? []).map(card => {
  const existingIds = new Set(card.results.map(r => r.playerId));
  const missing = allPlayers
    .filter(p => !existingIds.has(p.playerId))
    .map(p => ({ playerId: p.playerId, playerName: p.playerName, voteCount: 0, isWinner: false, votedByNames: [] }));
  return { ...card, results: [...card.results, ...missing] };
});

setData({ overview, pollCards, playerCards });

const built: Slide[] = [];
built.push({ type: "intro", data: overview });
for (const card of pollCards) built.push({ type: "poll", data: card });
for (const card of playerCards)
          built.push({ type: "title", data: card });
        built.push({ type: "final" });
        setSlides(built);
        setLoading(false);
      })
      .catch((e) => {
        setError(
          e instanceof Error
            ? e.message
            : "Couldn't load the report card. Try refreshing.",
        );
        setLoading(false);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const next = useCallback(() => {
    setIndex((i) => Math.min(i + 1, slides.length - 1));
    setExpandedVoters(new Set());
  }, [slides.length]);

  const prev = useCallback(() => {
    setIndex((i) => Math.max(i - 1, 0));
    setExpandedVoters(new Set());
  }, []);

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "ArrowRight" || e.key === " ") next();
      if (e.key === "ArrowLeft") prev();
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [next, prev]);

  const touchStart = useRef<{ x: number; y: number } | null>(null);

  function onTouchStart(e: React.TouchEvent) {
    touchStart.current = { x: e.touches[0].clientX, y: e.touches[0].clientY };
  }

  function onTouchEnd(e: React.TouchEvent) {
    if (!touchStart.current) return;
    const dx = e.changedTouches[0].clientX - touchStart.current.x;
    const dy = e.changedTouches[0].clientY - touchStart.current.y;
    const absX = Math.abs(dx);
    const absY = Math.abs(dy);
    if (absX > 40 && absX > absY) {
      if (dx < 0) {
        next();
      } else {
        prev();
      }
    }
    touchStart.current = null;
  }

  function handleDone() {
    clearSession();
    navigate("/");
  }

  function toggleVoters(playerId: string, e: React.MouseEvent) {
    e.stopPropagation();
    setExpandedVoters((prev) => {
      const next = new Set(prev);
      if (next.has(playerId)) next.delete(playerId);
      else next.add(playerId);
      return next;
    });
  }

  function buildShareText(slide: Slide): string {
    const roomName = data?.overview.roomName ?? session?.roomName ?? "the game";

    if (slide.type === "poll") {
      const top = slide.data.results[0];
      return top
        ? `🗳️ "${slide.data.questionText}" — ${top.playerName} won with ${top.voteCount} vote${top.voteCount !== 1 ? "s" : ""} in ${roomName}! Verdikt has spoken 🔥`
        : `Results for "${slide.data.questionText}" in ${roomName} 🔥`;
    }

    if (slide.type === "title") {
      const title = slide.data.titlesWon[0];
      return title
        ? `${slide.data.playerName} got crowned "${title}" with ${slide.data.totalVotesReceived} votes in ${roomName}! 🔥`
        : `${slide.data.playerName} racked up ${slide.data.totalVotesReceived} votes in ${roomName}! 🔥`;
    }

    if (slide.type === "intro") {
      const winner = slide.data.leaderboard[0];
      return winner
        ? `The GC has spoken in ${roomName}! 🔥 ${winner.playerName} leads with ${winner.totalVotesReceived} votes.`
        : `Check out our Verdikt results for ${roomName}! 🔥`;
    }

    const overview = data?.overview;
    const winner = overview?.leaderboard[0];
    return winner
      ? `🔥 The GC has spoken in ${roomName}! ${winner.playerName} took the crown with ${winner.totalVotesReceived} votes${winner.topTitle ? ` — "${winner.topTitle}"` : ""}. ${overview?.totalPlayers} players, ${overview?.totalQuestions} questions settled.`
      : `Check out our Verdikt results for ${roomName}! 🔥`;
  }

  async function handleShare(slide: Slide, e?: React.MouseEvent) {
    e?.stopPropagation();
    const text = buildShareText(slide);
    if (navigator.share) {
      try {
        await navigator.share({
          title: "Verdikt — The GC Has Spoken",
          text,
          url: window.location.href,
        });
      } catch {
        /* cancelled */
      }
    } else {
      await navigator.clipboard.writeText(`${text} ${window.location.href}`);
      alert("Link copied! Share it with your GC.");
    }
  }

  async function captureSlideBlob(targetIndex: number) {
    const previousIndex = index;
    setIndex(targetIndex);
    setExpandedVoters(new Set());
    try {
      await waitForPaint();

      const slideEl = document.querySelector(".reportcard__slide-wrapper");
      if (!slideEl) {
        throw new Error("Slide not found");
      }

      const canvas = await html2canvas(slideEl as HTMLElement, {
        backgroundColor: "#11111a",
        scale: captureScale,
        useCORS: true,
        scrollX: 0,
        scrollY: 0,
        windowWidth: Math.max(slideEl.scrollWidth, slideEl.clientWidth),
        windowHeight: Math.max(slideEl.scrollHeight, slideEl.clientHeight),
      });

      const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/png"));
      if (!blob) throw new Error("Failed to render slide");
      return new Uint8Array(await blob.arrayBuffer());
    } finally {
      setIndex(previousIndex);
      setExpandedVoters(new Set());
      await waitForPaint();
    }
  }

  async function handleDownloadSlide(e?: React.MouseEvent) {
    e?.stopPropagation();
    try {
      if (isLast && slides.length > 0) {
        setExporting(true);
        const files: { name: string; data: Uint8Array }[] = [];

        for (let i = 0; i < slides.length; i++) {
          const png = await captureSlideBlob(i);
          files.push({
            name: `verdikt-slide-${String(i + 1).padStart(2, "0")}.png`,
            data: png,
          });
        }

        const zip = buildZip(files);
        const link = document.createElement("a");
        link.href = URL.createObjectURL(zip);
        link.download = `verdikt-slides.zip`;
        link.click();
        window.setTimeout(() => URL.revokeObjectURL(link.href), 1000);
        setExporting(false);
        return;
      }

      const png = await captureSlideBlob(index);
      const link = document.createElement("a");
      link.href = URL.createObjectURL(new Blob([png], { type: "image/png" }));
      link.download = `verdikt-slide-${index + 1}.png`;
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(link.href), 1000);
      setExporting(false);
    } catch {
      setExporting(false);
      alert('Failed to download slide. Try again.');
    }
  }

  if (loading) {
    return (
      <div className="screen reportcard">
        <p className="reportcard__status">Loading results…</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="screen reportcard">
        <div className="reportcard__status-block">
          <p className="reportcard__status">{error}</p>
          <Button variant="secondary" onClick={handleDone}>
            Back home
          </Button>
        </div>
      </div>
    );
  }

  const slide = slides[index];
  const isLast = index === slides.length - 1;

  return (
    <div className="screen reportcard">
      {exporting && (
        <div className="reportcard__exporting" aria-live="polite">
          <div className="reportcard__exporting-card">
            <p className="reportcard__exporting-title">Preparing your ZIP</p>
            <p className="reportcard__exporting-copy">We’re rendering every slide for download.</p>
          </div>
        </div>
      )}
      <div className="reportcard__topbar">
        <div className="reportcard__progress">
          {slides.map((_, i) => (
            <span
              key={i}
              className={`reportcard__dot ${i <= index ? "reportcard__dot--filled" : ""}`}
            />
          ))}
        </div>
        <button
          className="reportcard__quick-share"
          onClick={(e) => {
            e.stopPropagation();
            handleShare(slide);
          }}
          type="button"
          aria-label="Share this slide"
        >
          ↗
        </button>
      </div>

      <div
        className="reportcard__stage"
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
      >
        <div key={index} className="reportcard__slide-wrapper">
          {/* ── INTRO ── */}
          {slide.type === "intro" && (
            <div className="reportcard__slide reportcard__slide--center">
             <Stamp label="the GC has spoken" size="md" color="coral" />
              <h1 className="reportcard__intro-title">{slide.data.roomName}</h1>
              <p className="reportcard__intro-meta">
                {slide.data.totalPlayers} players · {slide.data.totalQuestions}{" "}
                questions
              </p>
              {slide.data.leaderboard.length > 0 && (
                <div className="reportcard__podium">
                  {slide.data.leaderboard.slice(0, 3).map((entry, i) => (
                    <div
                      key={entry.playerName}
                      className={`reportcard__podium-slot reportcard__podium-slot--${i + 1}`}
                    >
                      <span className="reportcard__podium-rank">
                        {i === 0 ? "1" : i === 1 ? "2" : "3"}
                      </span>
                      <span className="reportcard__podium-name">
                        {entry.playerName}
                      </span>
                      <span className="reportcard__podium-votes">
                        {entry.totalVotesReceived} votes
                      </span>
                    </div>
                  ))}
                </div>
              )}
              {slide.data.leaderboard.length > 0 && (
                <div className="reportcard__leaderboard">
                  {slide.data.leaderboard.map((e, i) => (
                    <div key={e.playerName} className="reportcard__leader-row">
                      <span className="reportcard__leader-rank">
                        {["🥇", "🥈", "🥉"][i] ?? `#${i + 1}`}
                      </span>
                      <span className="reportcard__leader-name">
                        {e.playerName}
                      </span>
                      {e.topTitle && (
                        <span className="reportcard__leader-title">
                          {e.topTitle}
                        </span>
                      )}
                      <span className="reportcard__leader-votes">
                        {e.totalVotesReceived} votes
                      </span>
                    </div>
                  ))}
                </div>
              )}
              <p className="reportcard__tap-hint">
                swipe or use the arrows to move through results
              </p>
            </div>
          )}

          {/* ── POLL ── */}
          {slide.type === "poll" &&
            (() => {
              const total = slide.data.results.reduce(
                (s, r) => s + r.voteCount,
                0,
              );
              const orderedResults = slide.data.results
                .slice()
                .sort((a, b) => b.voteCount - a.voteCount || a.playerName.localeCompare(b.playerName));
              const hasVotes = orderedResults.some((r) => r.voteCount > 0);

              return (
                <div className="reportcard__slide">
                  <div className="reportcard__poll-header">
                    <p className="reportcard__poll-label">poll result</p>
                    <button
                      className="reportcard__share-pill"
                      onClick={(e) => handleShare(slide, e)}
                      type="button"
                    >
                      Share ↗
                    </button>
                  </div>
                  <h2 className="reportcard__poll-question">
                    {slide.data.questionText}
                  </h2>
                  <div className="reportcard__summary-row">
                    <span className="reportcard__summary-pill">
                      {hasVotes ? `${total} total votes` : 'no votes yet'}
                    </span>
                    {hasVotes && (
                      <span className="reportcard__summary-pill reportcard__summary-pill--accent">
                        {orderedResults[0]?.playerName ?? '—'} leading
                      </span>
                    )}
                  </div>

                  <div className="reportcard__poll-results">
                    {!hasVotes ? (
                      <div className="reportcard__empty-state">
                        <p className="reportcard__empty-title">No votes landed on this question yet.</p>
                        <p className="reportcard__empty-copy">The room will fill this in as players vote live.</p>
                      </div>
                    ) : orderedResults.map((r) => {
                      const pct =
                        total === 0
                          ? 0
                          : Math.round((r.voteCount / total) * 100);
                      const isExpanded = expandedVoters.has(r.playerId);
                      const hasVoters =
                        voteMode === "PUBLIC" &&
                        r.votedByNames &&
                        r.votedByNames.length > 0;

                      return (
                        <div
                          key={r.playerId}
                          className={`reportcard__poll-row ${r.isWinner ? "reportcard__poll-row--winner" : ""}`}
                        >
                          {/* top: avatar · name · pct · vote count */}
                          <div className="reportcard__poll-row-top">
                            <span className="reportcard__poll-name">
                              {r.isWinner ? (
                                <Stamp label="winner" size="sm" color="gold" />
                              ) : (
                                <span className="reportcard__poll-avatar">
                                  {r.playerName.charAt(0)}
                                </span>
                              )}
                              {r.playerName}
                            </span>
                          <span className={`reportcard__poll-count ${r.voteCount === 0 ? 'reportcard__poll-count--zero' : ''}`}>
  {r.voteCount} vote{r.voteCount !== 1 ? 's' : ''}
</span>
                          </div>

                          {/* bar + pct */}
                          <div className="reportcard__poll-bar-row">
                            <div className="reportcard__poll-bar-track">
                              <div
                                className={`reportcard__poll-bar-fill ${r.isWinner ? "reportcard__poll-bar-fill--winner" : ""}`}
                                style={{ width: `${pct}%` }}
                              />
                            </div>
                            <span className="reportcard__poll-pct">{pct}%</span>
                          </div>

                          {/* voter detail section */}
                          {hasVoters && (
                            <div className="reportcard__voter-section">
                              {/* collapsed: avatar stack + toggle */}
                              <div
                                className="reportcard__voter-collapsed"
                                onClick={(e) => toggleVoters(r.playerId, e)}
                              >
                                <div className="reportcard__voter-stack">
                                  {r
                                    .votedByNames!.slice(0, 4)
                                    .map((name, i) => (
                                      <span
                                        key={i}
                                        className="reportcard__voter-pip"
                                        title={name}
                                      >
                                        {name.charAt(0).toUpperCase()}
                                      </span>
                                    ))}
                                  {r.votedByNames!.length > 4 && (
                                    <span className="reportcard__voter-pip reportcard__voter-pip--more">
                                      +{r.votedByNames!.length - 4}
                                    </span>
                                  )}
                                </div>
                                <span className="reportcard__voter-summary">
                                  {r.votedByNames!.length === 1
                                    ? `${r.votedByNames![0]} voted`
                                    : `${r.votedByNames![0]} + ${r.votedByNames!.length - 1} more`}
                                </span>
                                <span className="reportcard__voter-toggle">
                                  {isExpanded ? "▲" : "▼"}
                                </span>
                              </div>

                              {/* expanded: full name chips */}
                              {isExpanded && (
                                <div
                                  className="reportcard__voter-expanded"
                                  onClick={(e) => e.stopPropagation()}
                                >
                                  {r.votedByNames!.map((name, i) => (
                                    <span
                                      key={i}
                                      className="reportcard__voter-chip"
                                    >
                                      <span className="reportcard__voter-chip-avatar">
                                        {name.charAt(0).toUpperCase()}
                                      </span>
                                      {name}
                                    </span>
                                  ))}
                                </div>
                              )}
                            </div>
                          )}

                          {/* anonymous mode — show count only */}
                          {voteMode !== "PUBLIC" && r.voteCount > 0 && (
                            <p className="reportcard__poll-voters">
                              {r.voteCount} vote{r.voteCount !== 1 ? "s" : ""}{" "}
                              cast
                            </p>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })()}

          {/* ── TITLE (player card) ── */}
         {slide.type === "title" && (
  <div className="reportcard__slide reportcard__slide--center">
    <div className="reportcard__title-avatar">
      {slide.data.playerName.charAt(0).toUpperCase()}
    </div>
    <h1 className="reportcard__title-name">{slide.data.playerName}</h1>
    <p className="reportcard__title-votes">
      {slide.data.totalVotesReceived} vote{slide.data.totalVotesReceived !== 1 ? 's' : ''} received
    </p>
    {slide.data.titlesWon.length > 0 && (
      <div className="reportcard__title-awards">
        <p className="reportcard__title-awards-label">crowned</p>
        {slide.data.titlesWon.map((t) => (
          <div key={t} className="reportcard__title-award-row">
            <TrophyIcon style={{ fontSize: '1.2em', color: 'var(--gold)', flexShrink: 0 }} />
            <span>{t}</span>
          </div>
        ))}
      </div>
    )}
    {slide.data.titlesWon.length === 0 && (
      <p className="reportcard__title-novotes">no titles this round</p>
    )}
  </div>
)}
          {/* ── FINAL ── */}
          {slide.type === "final" && (
  <div className="reportcard__slide reportcard__slide--center">
    <FlameIcon className="reportcard__final-emoji" />
    <h1 className="reportcard__intro-title">That's the verdikt</h1>

              {/* full leaderboard recap */}
              {data && data.overview.leaderboard.length > 0 && (
                <div className="reportcard__leaderboard reportcard__leaderboard--final">
                  {data.overview.leaderboard.map((e, i) => (
                    <div key={e.playerName} className="reportcard__leader-row">
                      <span className="reportcard__leader-rank">
                        {["🥇", "🥈", "🥉"][i] ?? `#${i + 1}`}
                      </span>
                      <span className="reportcard__leader-name">
                        {e.playerName}
                      </span>
                      {e.topTitle && (
                        <span className="reportcard__leader-title">
                          "{e.topTitle}"
                        </span>
                      )}
                      <span className="reportcard__leader-votes">
                        {e.totalVotesReceived} votes
                      </span>
                    </div>
                  ))}
                </div>
              )}

              {/* all questions recap */}
              {data && data.pollCards.length > 0 && (
                <div className="reportcard__final-recap">
                  <p className="reportcard__final-recap-label">all questions</p>
                  {data.pollCards.map((card) => {
                    const winner = card.results.find((r) => r.isWinner) ??
                      card.results.slice().sort((a, b) => b.voteCount - a.voteCount)[0];
                    return (
                      <div key={card.questionId} className="reportcard__final-recap-row">
                        <span className="reportcard__final-recap-q">{card.questionText}</span>
                        <span className="reportcard__final-recap-winner">
                          {winner && winner.voteCount > 0
                            ? `${winner.playerName} · ${winner.voteCount}v`
                            : "no votes"}
                        </span>
                      </div>
                    );
                  })}
                </div>
              )}

              <p className="reportcard__intro-meta" style={{ marginTop: 4 }}>
                {data?.overview.totalPlayers} players ·{" "}
                {data?.overview.totalQuestions} questions
              </p>

              <div className="reportcard__final-actions">
                <Button
                  onClick={(e) => {
                    e.stopPropagation();
                    handleShare(slide);
                  }}
                >
                  Share results ↗
                </Button>
                <Button
                  variant="secondary"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleDone();
                  }}
                >
                  Back home
                </Button>
              </div>
            </div>
          )}
        </div>
      </div>

      <div className="reportcard__nav">
        <button
          className="reportcard__nav-btn"
          onClick={(e) => {
            e.stopPropagation();
            prev();
          }}
          disabled={index === 0}
          aria-label="Previous slide"
        >
          ←
        </button>
        <button
          className="reportcard__nav-btn"
          onClick={handleDownloadSlide}
          aria-label={isLast ? "Download all slides as ZIP" : "Download slide"}
          title={isLast ? "Download all slides as ZIP" : "Download this slide"}
        >
          {isLast ? "ZIP" : "⬇"}
        </button>
        <button
          className={`reportcard__nav-btn ${isLast ? 'reportcard__nav-btn--finish' : 'reportcard__nav-btn--primary'}`}
          disabled={exporting}
          onClick={(e) => {
            e.stopPropagation();
            if (isLast) {
              handleDone();
            } else {
              next();
            }
          }}
          aria-label={isLast ? "Done" : "Next slide"}
        >
          {isLast ? '✓' : '→'}
        </button>
      </div>
    </div>
  );
}
