import { Link } from 'react-router-dom';
import { formatDateTime } from '../utils/format';

export default function EventCard({ event }) {
  return (
    <Link to={`/events/${event.id}`} className="event-card">
      {event.category && <span className="badge">{event.category}</span>}
      <h2>{event.title}</h2>
      <p className="event-meta">{formatDateTime(event.startTime)}</p>
      <p className="event-meta">
        {[event.venue, event.city].filter(Boolean).join(', ')}
      </p>
      {event.description && (
        <p className="event-desc">{event.description}</p>
      )}
    </Link>
  );
}