# Verdikt — The GC Has Spoken 🔥

A real-time multiplayer polling game where your friend group settles disputes, declares winners, and crowns champions. Built for instant, interactive group decisions with live results.

## Overview

**Verdikt** is a full-stack web application that lets players join a room, vote on questions about each other, and watch live results unfold. Perfect for group chats, game nights, or any scenario where the group's verdict matters.

### Key Features

- **Real-time Voting**: Vote on multiple options simultaneously and see results update live via WebSocket
- **Multi-Vote Support**: Players can vote for more than one person per question, not just one
- **Anonymous or Public Mode**: Control whether votes are hidden or attributed to voters
- **Slide Deck Results**: View results in a polished report card with individual player highlights and leaderboards
- **Share & Export**: Copy share links or download each slide as an image to your phone
- **Responsive Design**: Works seamlessly on desktop and mobile browsers
- **Live Sync**: All players see the same state in real-time; no refresh-and-reload needed

---

## Architecture

### Backend (Java / Spring Boot)

Located in `verdikt-backend/`

**Stack**:
- Java 17+
- Spring Boot 3.x with WebSocket/STOMP over SockJS
- Spring Data JPA with PostgreSQL
- Maven for build

**Key Components**:

- **VoteService**: Core vote mutation logic; handles replacing and multi-selecting votes
- **RoomService**: Room creation, player join, and per-player token management
- **VoteController**: HTTP endpoints for casting and retrieving votes
- **WebSocket Publisher**: Broadcasts vote state and game events to all connected players

**Database Model**:
- `Room`: Contains game session, vote mode (ANONYMOUS/PUBLIC), and status
- `Player`: Represents each participant; stores backend-issued token for identity
- `Question`: Poll questions (category, spice level, text)
- `RoomQuestion`: Links a question to a room and marks it as active
- `Vote`: Records a voter's selection; voter is identified by token

### Frontend (React / TypeScript / Vite)

Located in `verdikt-frontend/`

**Stack**:
- React 19 with TypeScript
- React Router for navigation
- Vite for bundling
- Custom WebSocket hook with STOMP protocol

**Key Screens**:

1. **Home** (`Home.tsx`): Landing page with create/join options
2. **CreateRoom** (`CreateRoom.tsx`): Set up a new game (vote mode, question count, duration)
3. **JoinRoom** (`JoinRoom.tsx`): Enter room code and player name
4. **Lobby** (`Lobby.tsx`): Wait for other players, preview or add questions
5. **Play** (`Play.tsx`): Live polling; tap to vote for multiple people, see live results
6. **ReportCard** (`ReportCard.tsx`): Slide deck of results; navigate, share, download

**Session Management**:
- Per-tab session store in `session.ts` (prevents identity confusion across tabs)
- Backend-issued tokens ensure correct player identification
- WebSocket reconnection logic hydrates vote state on reconnect

---

## Getting Started

### Prerequisites

- **Backend**: Java 17+, Maven 3.8+, PostgreSQL 12+
- **Frontend**: Node.js 18+, npm 9+

### Database Setup

1. Create a PostgreSQL database:
   ```sql
   CREATE DATABASE verdikt;
   ```

2. The backend will auto-create tables on first run (Hibernate DDL `update` mode).

### Running the Backend

```bash
cd verdikt-backend

# Build and run tests
./mvnw clean verify

# Start the server (default: http://localhost:8080)
./mvnw spring-boot:run
```

Set environment variables to override defaults:
- `SPRING_DATASOURCE_URL`: PostgreSQL JDBC URL
- `SPRING_DATASOURCE_USERNAME`: DB user
- `SPRING_DATASOURCE_PASSWORD`: DB password

### Running the Frontend

```bash
cd verdikt-frontend

# Install dependencies
npm install

# Start dev server (default: http://localhost:5173)
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

Set environment variables:
- `VITE_API_URL`: Backend API base URL (e.g., `http://localhost:8080`)

---

## UI Walkthrough

### 1. Home Screen
**Entry point** — Choose to create a new room or join an existing one.

```
╔════════════════════════════════════╗
║       VERDIKT — THE GC HAS         ║
║          SPOKEN 🔥                  ║
║                                    ║
║    ┌──────────────────────────┐    ║
║    │  Create a New Room       │    ║
║    │  (Set vote mode, count)  │    ║
║    └──────────────────────────┘    ║
║                                    ║
║    ┌──────────────────────────┐    ║
║    │  Join a Room             │    ║
║    │  (Enter code)            │    ║
║    └──────────────────────────┘    ║
╚════════════════════════════════════╝
```

**Actions:**
- Tap "Create a New Room" → Redirect to CreateRoom
- Tap "Join a Room" → Redirect to JoinRoom

---

### 2. Create Room Screen
**Setup** — Configure game settings before inviting players.

```
╔════════════════════════════════════╗
║  Setup Your Game                   ║
║                                    ║
║  Vote Mode:                        ║
║  ◉ Anonymous  ○ Public             ║
║                                    ║
║  Questions to ask:                 ║
║  [  15  ]  (1–50)                  ║
║                                    ║
║  Time per question (sec):          ║
║  [  30  ]  (10–180)                ║
║                                    ║
║  ┌──────────────────────────┐      ║
║  │  Next: Add Questions     │      ║
║  └──────────────────────────┘      ║
╚════════════════════════════════════╝
```

**Actions:**
- Select vote mode (Anonymous hides voter names; Public shows them)
- Set question count and duration
- Proceed to question preview/customization

---

### 3. Lobby Screen
**Wait & Prep** — Invite friends, preview questions, and wait for players.

```
╔════════════════════════════════════╗
║  Room: ABC123                      ║
║  👥 Players: 3/8                   ║
║                                    ║
║  Share the code: ABC123            ║
║  [Copy to clipboard]               ║
║                                    ║
║  Players joined:                   ║
║  ✓ Alice                           ║
║  ✓ Bob                             ║
║  ✓ Cara                            ║
║                                    ║
║  Questions (15):                   ║
║  1. Who's the funniest?            ║
║  2. Best hair in the GC?           ║
║  [+ Add custom question]           ║
║                                    ║
║  ┌──────────────────────────┐      ║
║  │  Start the Game →        │      ║
║  └──────────────────────────┘      ║
╚════════════════════════════════════╝
```

**Actions:**
- Share room code with friends
- Add or remove custom questions
- Start game when ready (host only)

---

### 4. Play Screen (Live Voting)
**Main gameplay** — Vote on multiple people per question; see live results.

```
╔════════════════════════════════════╗
║  [████████░░░░░░░░]  Q 3/15       ║
║  "Who's the funniest?"             ║
║                                    ║
║  👤 You (Alice)                    ║
║  ⏱ 28s remaining                   ║
║                                    ║
║  [Tap to vote for multiple people] ║
║                                    ║
║  ✓ Bob         ◌ you voted  45%    ║
║  ███████████████████████░░░░░░░    ║
║                                    ║
║  ✓ Cara        ◌ you voted  35%    ║
║  ███████████░░░░░░░░░░░░░░░░░░░░   ║
║                                    ║
║  ◌ Diana               20%          ║
║  ██████░░░░░░░░░░░░░░░░░░░░░░░░    ║
║                                    ║
║  💚 results update live            ║
║  ┌──────────────────────────┐      ║
║  │  Next question →         │ (H)  ║
║  └──────────────────────────┘      ║
╚════════════════════════════════════╝
```

**Controls:**
- **Tap a person's name** → Add them to your vote (highlights with green)
- **Tap again** → Remove from your vote (brief removal animation)
- **Live vote count** → Updates in real-time as others vote
- **Green "you voted" badge** → Shows your current selection
- **Next button** (host only) → Advance to next question

**Modes:**
- **Anonymous**: Only vote counts are shown
- **Public**: Vote counts + voter names displayed

---

### 5. Report Card — Slide Navigation
**Results deck** — Navigate through detailed results with controls.

```
╔════════════════════════════════════╗
║  [●●●●●○○○○○○○○○○○]               ║
║                          ↗ Share   ║
║═════════════════════════════════════║
║                                    ║
║  INTRO SLIDE (Slide 1/17):         ║
║                                    ║
║  🔥 THE GC HAS SPOKEN              ║
║  Friday Night Shenanigans          ║
║  8 players · 15 questions          ║
║                                    ║
║  🥇 Alice     — 87 votes           ║
║  🥈 Bob       — 72 votes           ║
║  🥉 Cara      — 61 votes           ║
║                                    ║
║  tap or swipe to see results       ║
║                                    ║
║  ┌─────────┬─────────┬─────────┐   ║
║  │    ←    │    ⬇    │    →    │   ║
║  │Previous │Download │  Next   │   ║
║  └─────────┴─────────┴─────────┘   ║
╚════════════════════════════════════╝
```

**Controls:**
- **← Previous** (disabled on slide 1) — Go back
- **⬇ Download** — Save slide as PNG image to device
- **→ Next or ✓ Done** (on last slide) — Advance or finish

---

### 6. Report Card — Poll Results Slide
**Question breakdown** — See vote counts and winner.

```
╔════════════════════════════════════╗
║  [●●●●○○○○○○○○○○○○○○○]            ║
║                     POLL RESULT ↗  ║
║═════════════════════════════════════║
║                                    ║
║  "Who's the funniest?"             ║
║                                    ║
║  🏆 45 total votes — Alice leading ║
║                                    ║
║  🏅 Alice        45 votes [100%]   ║
║  ████████████████████████████████  ║
║  (Voted by: Bob, Cara, +5 more)    ║
║                                    ║
║  ◌ Bob           32 votes [71%]    ║
║  ██████████████████████░░░░░░░░    ║
║  (Voted by: Alice, Diana)          ║
║                                    ║
║  ◌ Cara          8 votes [18%]     ║
║  █████░░░░░░░░░░░░░░░░░░░░░░░░░░   ║
║  (Voted by: Eve)                   ║
║                                    ║
║  ┌─────────┬─────────┬─────────┐   ║
║  │    ←    │    ⬇    │    →    │   ║
║  └─────────┴─────────┴─────────┘   ║
╚════════════════════════════════════╝
```

**Features:**
- **Vote counts** with percentages
- **Winner badge** (🏆) on the top result
- **Voter attribution** (in PUBLIC mode)
- **Expandable voter list** — Tap to see who voted
- **Share button** — Share this poll result

---

### 7. Report Card — Player Card Slide
**Individual highlights** — Personal stats for each player.

```
╔════════════════════════════════════╗
║  [●●●●●●●●○○○○○○○○○○○]            ║
║                                    ║
║═════════════════════════════════════║
║                                    ║
║            🅰️  Alice               ║
║                                    ║
║       87 votes received            ║
║                                    ║
║  Crowned With:                     ║
║  🏆 "The Funniest"                 ║
║  🏆 "Best Hair"                    ║
║  🏆 "Most Likely to...             ║
║                                    ║
║                                    ║
║                                    ║
║  ┌─────────┬─────────┬─────────┐   ║
║  │    ←    │    ⬇    │    →    │   ║
║  └─────────┴─────────┴─────────┘   ║
╚════════════════════════════════════╝
```

**Info:**
- Player's total votes received
- Titles/awards they won
- Summary of impact in the game

---

### 8. Report Card — Final Slide
**Summary & Export** — Game recap with all stats and full export.

```
╔════════════════════════════════════╗
║  [●●●●●●●●●●●●●●●●●●●●]           ║
║                          ↗ Share   ║
║═════════════════════════════════════║
║                                    ║
║        🔥 THAT'S THE VERDIKT       ║
║                                    ║
║  🥇 Alice    — 87 votes — Funniest ║
║  🥈 Bob      — 72 votes — Hair     ║
║  🥉 Cara     — 61 votes            ║
║                                    ║
║  All Questions:                    ║
║  Who's funniest?      Alice · 45v  ║
║  Best hair?           Bob · 32v    ║
║  Most likely to...    Cara · 28v   ║
║  [scroll for more]                 ║
║                                    ║
║  8 players · 15 questions          ║
║                                    ║
║  ┌──────────────────────────┐      ║
║  │  Share results ↗         │      ║
║  │  Back home               │      ║
║  └──────────────────────────┘      ║
╚════════════════════════════════════╝
```

**Actions:**
- **Share results** — Copy link or native share
- **Download** — Export final summary or any slide as image
- **Back home** — Return to home, clear session

---

## How to Play

1. **Create a Room**:
   - Click "Create Room"
   - Choose vote mode (Anonymous or Public)
   - Set question count and duration per question
   - Add custom questions or let the app pre-populate

2. **Invite Players**:
   - Share the room code with friends
   - They join via "Join Room" and enter their name

3. **Vote**:
   - For each question, tap to vote for one or more people
   - Your selections highlight; the app shows "you voted" for your picks
   - See live vote counts and leading options in real-time
   - Deselect by tapping again; removal plays a quick animation

4. **Review Results**:
   - Navigate through the slide deck with arrow buttons or swipe
   - Tap any slide to advance to the next
   - Download slides as images or share via link
   - View leaderboards and individual player highlights

---

## API Endpoints

### Rooms
- `POST /api/rooms` — Create a new room
- `GET /api/rooms/rejoin` — Get current room and players (requires `X-Player-Token` header)

### Voting
- `POST /api/rooms/{roomId}/votes` — Cast a vote (can include multiple selections)
- `DELETE /api/rooms/{roomId}/votes` — Remove a vote
- `GET /api/rooms/{roomId}/votes/current` — Get all votes for the active question

### Game Flow
- `GET /api/rooms/{roomId}/game/current-question` — Get the active question
- `POST /api/rooms/{roomId}/game/next-question` — Advance to the next question
- `POST /api/rooms/{roomId}/game/end` — End the game early (host only)

### Reports
- `GET /api/rooms/{roomId}/report-card` — Get the full results summary

All endpoints require the `X-Player-Token` header (issued on room join).

---

## Project Structure

```
verdikt/
├── verdikt-backend/               # Java / Spring Boot server
│   ├── src/main/java/...
│   │   ├── controller/            # HTTP endpoints
│   │   ├── service/               # Business logic
│   │   ├── repository/            # JPA repositories
│   │   ├── model/                 # Entities (Room, Player, Vote, etc.)
│   │   └── websocket/             # WebSocket publishers
│   ├── src/test/java/...          # Unit & integration tests
│   └── pom.xml
│
├── verdikt-frontend/              # React / TypeScript / Vite
│   ├── src/
│   │   ├── screens/               # Page components (Home, Play, ReportCard, etc.)
│   │   ├── components/            # Reusable UI components (Button, Card, etc.)
│   │   ├── icons/                 # SVG icon components
│   │   ├── styles/                # Global CSS
│   │   ├── App.tsx                # Main app with routing
│   │   ├── useSocket.ts           # Custom WebSocket hook
│   │   └── session.ts             # Session storage
│   ├── package.json
│   └── vite.config.ts
│
└── README.md                       # This file
```

---

## Data Flow

### Voting Flow

1. **Player Taps Option**:
   - Frontend sets optimistic UI state (selection highlight, "you voted" badge)
   - Sends `POST /votes` with the full set of selected player IDs

2. **Backend Processes Vote**:
   - Validates room, player token, and question
   - Compares desired selections with existing votes
   - Adds new votes, removes deselected ones
   - Generates authoritative vote state

3. **WebSocket Broadcast**:
   - Backend publishes `VOTE_STATE` event to `/topic/room/{id}/votes`
   - All connected players receive updated state
   - Frontend applies state (vote counts, leaderboards)

4. **UI Updates**:
   - Results refresh without page reload
   - Animations play for added/removed votes
   - Live vote counts update in real-time

### Session & Identity

- **Backend Issues Token**: On room join, backend generates a unique UUID token for each player
- **Frontend Stores Token**: Token is saved in per-tab session store (not shared across tabs)
- **Token in Headers**: Every subsequent request includes `X-Player-Token` header
- **Server Validates**: Backend looks up player by token; rejects requests from unknown tokens

---

## Testing

### Backend Tests

```bash
cd verdikt-backend

# Run all tests
./mvnw test

# Run a specific test class
./mvnw -Dtest=VoteServiceTest test
```

**Test Coverage**:
- Vote service: multi-vote selection, state building, WebSocket publishing
- Room service: player join, token management
- Controller integration: request validation, response formats

### Frontend Build

```bash
cd verdikt-frontend

# Type check + bundle
npm run build
```

---

## Deployment

### Backend

Use any Java-compatible hosting (Heroku, AWS, DigitalOcean, etc.):

```bash
# Build a JAR
./mvnw clean package

# Run the JAR
java -jar target/verdikt_backend-0.0.1-SNAPSHOT.jar
```

Ensure your PostgreSQL instance is reachable and credentials are set via environment variables.

### Frontend

Build and deploy the static site:

```bash
cd verdikt-frontend
npm run build
# Outputs to dist/
```

Deploy `dist/` to any static host (Netlify, Vercel, GitHub Pages, AWS S3, etc.).

**Update `VITE_API_URL`**: Point frontend to your deployed backend API.

---

## Features Explained

### Multi-Vote Voting

Players can tap multiple people per question (not limited to one choice). The backend reconciles desired selections with existing votes in a single request, ensuring no duplicate entries and fast state sync.

### Anonymous vs. Public Mode

- **Anonymous**: Vote counts are shown, but voter names are hidden
- **Public**: Vote counts include voter attributions; leaderboard shows who voted for whom

### Live Results

The WebSocket connection broadcasts vote state updates to all players as they vote. No polling; changes appear instantly.

### Share & Export

- **Share Link**: Copy the report card URL with pre-filled room code; friends can join
- **Download Slide**: Each slide can be captured as a PNG and saved to your phone or device

---

## Troubleshooting

### Backend won't start
- Check PostgreSQL is running and the database exists
- Verify `SPRING_DATASOURCE_*` environment variables
- Check port 8080 is not in use

### Frontend won't connect to backend
- Ensure `VITE_API_URL` points to your backend
- Check CORS is enabled on the backend (it is by default)
- Verify the backend server is running

### WebSocket connection fails
- Check your network allows WebSocket upgrades (some proxies block them)
- Verify the backend is accessible from your browser
- Try refreshing the page

### Votes not syncing
- Ensure all players are in the same room
- Check the WebSocket connection is active (look for `/topic/room/{id}/votes` subscriptions)
- Verify player tokens are valid (check server logs)

---

## Future Enhancements

- [ ] Question categories and difficulty levels
- [ ] Timeout-based auto-advance to next question
- [ ] Player profiles and statistics
- [ ] Admin controls for kick/ban
- [ ] Custom themes and branding
- [ ] Mobile app (React Native)
- [ ] Results archival and history

---

## Contributing

This is a group game project. If you'd like to add features or fix bugs:

1. Create a branch: `git checkout -b feature/your-feature`
2. Make your changes
3. Test locally
4. Open a pull request

---

## License

MIT License — feel free to fork and use for your own game nights!

---

## Credits

Built with ❤️ for group decisions and gaming nights.

**Tech Stack**: Spring Boot, React, TypeScript, WebSocket, PostgreSQL, Vite

---

## Questions?

- Check the [API Endpoints](#api-endpoints) section
- Review the code comments in `verdikt-backend/src/main/java/` and `verdikt-frontend/src/`
- Open an issue if something's broken

**The GC Has Spoken** 🔥
