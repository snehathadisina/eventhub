import { useEffect, useState } from 'react';
import { isCancel } from 'axios';
import { Link } from 'react-router-dom';
import api from '../api/client';
import { getErrorMessage } from '../api/errors';
import ConfirmDialog from '../components/ConfirmDialog';
import { formatDateTime } from '../utils/format';

export default function OrganizerEvents() {
  const [reloadKey, setReloadKey] = useState(0);
  const [result, setResult] = useState({ key: null, events: [], error: '' });
  const [actionError, setActionError] = useState('');
  const [pending, setPending] = useState(null);
  const [cancelling, setCancelling] = useState(false);

  useEffect(() => {
    const controller = new AbortController();

    api
      .get('/api/organizer/events', { signal: controller.signal })
      .then(({ data }) => setResult({ key: reloadKey, events: data, error: '' }))
      .catch((err) => {
        if (isCancel(err)) return;
        setResult({ key: reloadKey, events: [], error: getErrorMessage(err) });
      });

    return () => controller.abort();
  }, [reloadKey]);

  const loading = result.key !== reloadKey;
  const { events, error } = result;

  const confirmCancel = async () => {
    if (!pending) return;
    setCancelling(true);
    setActionError('');
    try {
      await api.delete(`/api/organizer/events/${pending.id}`);
      setPending(null);
      setReloadKey((k) => k + 1);
    } catch (err) {
      setPending(null);
      setActionError(getErrorMessage(err));
    } finally {
      setCancelling(false);
    }
  };

  return (
    <div className="container page">
      <div className="page-header">
        <h1>My events</h1>
        <Link to="/organizer/events/new" className="btn btn-primary">Create event</Link>
      </div>

      {loading && <p className="muted">Loading...</p>}
      {!loading && error && <div className="alert-error" role="alert">{error}</div>}
      {actionError && <div className="alert-error" role="alert">{actionError}</div>}

      {!loading && !error && events.length === 0 && (
        <p className="muted">You haven&apos;t created any events yet.</p>
      )}

      {!loading && !error && events.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Title</th>
                <th>Date</th>
                <th>City</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {events.map((event) => (
                <tr key={event.id}>
                  <td>
                    {event.status === 'PUBLISHED' ? (
                      <Link to={`/events/${event.id}`}>{event.title}</Link>
                    ) : (
                      event.title
                    )}
                  </td>
                  <td>{formatDateTime(event.startTime)}</td>
                  <td>{event.city}</td>
                  <td>
                    <span className={`status status-${event.status.toLowerCase()}`}>
                      {event.status}
                    </span>
                  </td>
                  <td>
                    {event.status !== 'CANCELLED' && (
                      <div className="actions">
                        <Link to={`/organizer/events/${event.id}/edit`}>Edit</Link>
                        <Link to={`/organizer/events/${event.id}/tickets`}>Tickets</Link>
                        <button type="button" className="link-danger"
                                onClick={() => setPending(event)}>
                          Cancel
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmDialog
        open={pending !== null}
        title="Cancel this event?"
        message={
          pending
            ? `"${pending.title}" will be hidden from everyone and can no longer be edited. This can't be undone.`
            : ''
        }
        confirmLabel="Yes, cancel event"
        cancelLabel="Keep event"
        busy={cancelling}
        onConfirm={confirmCancel}
        onCancel={() => setPending(null)}
      />
    </div>
  );
}