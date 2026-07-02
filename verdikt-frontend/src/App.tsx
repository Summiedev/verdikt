import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Home from './screens/Home';
import CreateRoom from './screens/CreateRoom';
import JoinRoom from './screens/JoinRoom';
import Lobby from './screens/Lobby';
import Play from './screens/Play';
import ReportCard from './screens/ReportCard';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/create" element={<CreateRoom />} />
        <Route path="/join" element={<JoinRoom />} />
        <Route path="/lobby/:code" element={<Lobby />} />
        <Route path="/play/:code" element={<Play />} />
        <Route path="/report-card/:code" element={<ReportCard />} />
      </Routes>
    </BrowserRouter>
  );
}