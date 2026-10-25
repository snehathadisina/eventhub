import { Route, Routes } from 'react-router-dom';
import Navbar from './components/Navbar';
import RequireOrganizer from './components/RequireOrganizer';
import EventDetail from './pages/EventDetail';
import EventForm from './pages/EventForm';
import Events from './pages/Events';
import Login from './pages/Login';
import ManageTickets from './pages/ManageTickets';
import OrganizerEvents from './pages/OrganizerEvents';
import Register from './pages/Register';

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
          <Route path="/events/:id" element={<EventDetail />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />

          <Route element={<RequireOrganizer />}>
            <Route path="/organizer/events" element={<OrganizerEvents />} />
            <Route path="/organizer/events/new" element={<EventForm />} />
            <Route path="/organizer/events/:id/edit" element={<EventForm />} />
            <Route path="/organizer/events/:id/tickets" element={<ManageTickets />} />
          </Route>

          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
    </>
  );
}