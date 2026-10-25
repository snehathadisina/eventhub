import { Route, Routes } from 'react-router-dom';
import Navbar from './components/Navbar';
import Events from './pages/Events';
import Login from './pages/Login';
import Register from './pages/Register';
import EventDetail from './pages/EventDetail';

function NotFound() {
  return (
    <div className="container page">
      <h1>Page not found</h1>
    </div>
  );
}

export default function App() {
  return (
    <>
      <Navbar />
      <main>
        <Routes>
          <Route path="/" element={<Events />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="*" element={<NotFound />} />
          <Route path="/events/:id" element={<EventDetail />} />
        </Routes>
      </main>
    </>
  );
}