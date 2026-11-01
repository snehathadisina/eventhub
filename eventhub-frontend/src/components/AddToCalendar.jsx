import api from '../api/client';

export default function AddToCalendar({ eventId }) {
  const base = `${api.defaults.baseURL ?? ''}/api/events/${eventId}`;

  return (
    <div className="calendar-links">
      <a
        href={`${base}/google-calendar`}
        target="_blank"
        rel="noopener noreferrer"
        className="btn btn-outline btn-small"
      >
        Add to Google Calendar
      </a>
      <a href={`${base}/calendar.ics`} className="btn btn-outline btn-small">
        Apple / Outlook (.ics)
      </a>
    </div>
  );
}