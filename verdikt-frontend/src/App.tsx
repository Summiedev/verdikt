import { BrowserRouter, Routes, Route, useLocation } from 'react-router-dom';
import { useEffect } from 'react';
import Home from './screens/Home';
import CreateRoom from './screens/CreateRoom';
import JoinRoom from './screens/JoinRoom';
import Lobby from './screens/Lobby';
import Play from './screens/Play';
import ReportCard from './screens/ReportCard';
import NotFound from './screens/NotFound';

function RouteTitle() {
  const { pathname } = useLocation();
  const title = pathname === '/' ? 'Verdikt - Let the Group Chat Decide'
    : pathname.startsWith('/create') ? 'Start a room - Verdikt'
      : pathname.startsWith('/join') ? 'Join a room - Verdikt'
        : pathname.startsWith('/lobby') ? 'Lobby - Verdikt'
          : pathname.startsWith('/play') ? 'Vote live - Verdikt'
            : pathname.startsWith('/report-card') ? 'The verdict is in - Verdikt'
              : 'Page not found - Verdikt';
  useEffect(() => { document.title = title; }, [title]);
  return null;
}

export default function App() {
  return (
    <BrowserRouter>
      <RouteTitle />
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/create" element={<CreateRoom />} />
        <Route path="/join" element={<JoinRoom />} />
        <Route path="/lobby/:code" element={<Lobby />} />
        <Route path="/play/:code" element={<Play />} />
        <Route path="/report-card/:code" element={<ReportCard />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </BrowserRouter>
  );
}
