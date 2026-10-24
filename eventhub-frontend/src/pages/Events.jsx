import { useEffect, useState } from 'react';
import api from '../api/client';
import { getErrorMessage } from '../api/errors';

export default function Events() {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/api/events')
      .then(({ data }) => setEvents(data.content))
      .catch((err) => setError(getErrorMessage(err)))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="container page">
      <h1>Upcoming events</h1>
      {loading && <p className="muted">Loading...</p>}
      {error && <div className="alert-error" role="alert">{error}</div>}
      {!loading && !error && events.length === 0 && (
        <p className="muted">No upcoming events yet.</p>
      )}
      <ul className="simple-list">
        {events.map((event) => (
          <li key={event.id}>{event.title} ({event.city})</li>
        ))}
      </ul>
    </div>
  );
}