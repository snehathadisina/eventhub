import { useEffect, useState } from 'react';
import { isCancel } from 'axios';
import { Link, useParams } from 'react-router-dom';
import api from '../api/client';
import { getErrorMessage } from '../api/errors';
import { formatDateTime, formatPrice } from '../utils/format';

export default function EventDetail() {
  const { id } = useParams();
  const [result, setResult] = useState({ id: null, event: null, notFound: false, error: '' });

  useEffect(() => {
    const controller = new AbortController();

    api
      .get(`/api/events/${id}`, { signal: controller.signal })
      .then(({ data }) => setResult({ id, event: data, notFound: false, error: '' }))
      .catch((err) => {
        if (isCancel(err)) return;
        const status = err.response?.status;
        setResult({
          id,
          event: null,
          notFound: status === 404 || status === 400,
          error: getErrorMessage(err),
        });
      });

    return () => controller.abort();
  }, [id]);

  const loading = result.id !== id;
  const { event, notFound, error } = result;

  return (
    <div className="container page">
      <Link to="/" className="back-link">&larr; All events</Link>

      {loading && <p className="muted">Loading...</p>}

      {!loading && notFound && (
        <>
          <h1>Event not found</h1>
          <p className="muted">This event doesn&apos;t exist or is no longer available.</p>
        </>
      )}

      {!loading && !notFound && error && (
        <div className="alert-error" role="alert">{error}</div>
      )}

      {!loading && event && (
        <article className="detail">
          {event.category && <span className="badge">{event.category}</span>}
          <h1>{event.title}</h1>
          <p className="event-meta">
            {formatDateTime(event.startTime)}
            {event.endTime && ` - ${formatDateTime(event.endTime)}`}
          </p>
          <p className="event-meta">
            {[event.venue, event.city].filter(Boolean).join(', ')}
          </p>
          <p className="muted">Organized by {event.organizerName}</p>

          {event.description && <p className="detail-desc">{event.description}</p>}

          <h2>Tickets</h2>
          {event.ticketTypes.length === 0 ? (
            <p className="muted">Tickets are not available yet.</p>
          ) : (
            <table className="ticket-table">
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Price</th>
                  <th>Availability</th>
                </tr>
              </thead>
              <tbody>
                {event.ticketTypes.map((t) => (
                  <tr key={t.id}>
                    <td>{t.name}</td>
                    <td>{formatPrice(t.price)}</td>
                    <td>
                      {t.availableQuantity > 0
                        ? `${t.availableQuantity} left`
                        : 'Sold out'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </article>
      )}
    </div>
  );
}