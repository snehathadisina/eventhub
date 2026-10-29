import { useEffect, useState } from 'react';
import { isCancel } from 'axios';
import { Link } from 'react-router-dom';
import api from '../api/client';
import { getErrorMessage } from '../api/errors';
import ConfirmDialog from '../components/ConfirmDialog';
import TicketsDialog from '../components/TicketsDialog';
import { formatDateTime, formatPrice } from '../utils/format';

const STATUS_CLASS = {
  CONFIRMED: 'status-published',
  PROMOTED: 'status-published',
  WAITING: 'status-draft',
  PENDING: 'status-draft',
  CANCELLED: 'status-cancelled',
};

const STATUS_LABEL = {
  CONFIRMED: 'Confirmed',
  PENDING: 'Pending',
  CANCELLED: 'Cancelled',
  WAITING: 'Waiting',
  PROMOTED: 'Ticket booked',
};

function StatusBadge({ status, cancelledLabel }) {
  const label = status === 'CANCELLED' && cancelledLabel ? cancelledLabel : STATUS_LABEL[status] ?? status;
  return <span className={`status ${STATUS_CLASS[status] ?? 'status-completed'}`}>{label}</span>;
}

export default function MyTickets() {
  const [refreshKey, setRefreshKey] = useState(0);
  const [data, setData] = useState({ loaded: false, bookings: [], waiting: [], error: '' });
  const [pending, setPending] = useState(null); // { kind: 'booking' | 'waiting', item }
  const [viewing, setViewing] = useState(null); // the booking whose QR codes are open
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [actionError, setActionError] = useState('');

  useEffect(() => {
    const controller = new AbortController();

    Promise.all([
      api.get('/api/bookings/me', { signal: controller.signal }),
      api.get('/api/waiting-list/me', { signal: controller.signal }),
    ])
      .then(([bookingsRes, waitingRes]) => {
        const now = Date.now();
        const bookings = bookingsRes.data.map((b) => ({
          ...b,
          canCancel: b.status === 'CONFIRMED' && Date.parse(b.eventStartTime) > now,
        }));
        setData({ loaded: true, bookings, waiting: waitingRes.data, error: '' });
      })
      .catch((err) => {
        if (isCancel(err)) return;
        setData((prev) => ({ ...prev, loaded: true, error: getErrorMessage(err) }));
      });

    return () => controller.abort();
  }, [refreshKey]);

  const handleConfirm = async () => {
    if (!pending) return;
    setBusy(true);
    setActionError('');
    setNotice('');
    try {
      if (pending.kind === 'booking') {
        await api.post(`/api/bookings/${pending.item.id}/cancel`);
        setNotice('Booking cancelled. Your tickets are back on sale.');
      } else {
        await api.delete(`/api/waiting-list/${pending.item.id}`);
        setNotice('You left the waiting list.');
      }
    } catch (err) {
      setActionError(getErrorMessage(err));
    } finally {
      setBusy(false);
      setPending(null);
      setRefreshKey((k) => k + 1);
    }
  };

  const isBooking = pending?.kind === 'booking';
  const dialogTitle = isBooking ? 'Cancel this booking?' : 'Leave the waiting list?';
  const dialogMessage = pending
    ? isBooking
      ? `${pending.item.quantity} x ${pending.item.ticketTypeName} for ${pending.item.eventTitle}. The tickets go back on sale.`
      : `${pending.item.quantity} x ${pending.item.ticketTypeName} for ${pending.item.eventTitle}. You will lose your place in the queue.`
    : '';

  const { loaded, bookings, waiting, error } = data;

  return (
    <div className="container page">
      <div className="page-header">
        <h1>My tickets</h1>
        <button type="button" className="btn btn-outline" onClick={() => setRefreshKey((k) => k + 1)}>
          Refresh
        </button>
      </div>

      {notice && <div className="alert-success" role="status">{notice}</div>}
      {(actionError || error) && (
        <div className="alert-error" role="alert">{actionError || error}</div>
      )}

      {!loaded && <p className="muted">Loading...</p>}

      {loaded && (
        <>
          <h2 className="section-title">Bookings</h2>
          {bookings.length === 0 ? (
            <div className="empty">
              You haven&apos;t booked any tickets yet. <Link to="/">Browse events</Link>
            </div>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Event</th>
                    <th>When</th>
                    <th>Tickets</th>
                    <th>Total</th>
                    <th>Status</th>
                    <th>QR codes</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {bookings.map((b) => (
                    <tr key={b.id}>
                      <td><Link to={`/events/${b.eventId}`}>{b.eventTitle}</Link></td>
                      <td>{formatDateTime(b.eventStartTime)}</td>
                      <td>{b.quantity} x {b.ticketTypeName}</td>
                      <td>{formatPrice(b.totalAmount)}</td>
                      <td><StatusBadge status={b.status} /></td>
                      <td>
                        {b.status === 'CONFIRMED' && b.tickets.length > 0 ? (
                          <button type="button" className="link-primary" onClick={() => setViewing(b)}>
                            Show {b.tickets.length} ticket{b.tickets.length === 1 ? '' : 's'}
                          </button>
                        ) : (
                          '-'
                        )}
                      </td>
                      <td>
                        {b.canCancel && (
                          <button
                            type="button"
                            className="link-danger"
                            onClick={() => setPending({ kind: 'booking', item: b })}
                          >
                            Cancel
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <h2 className="section-title">Waiting list</h2>
          {waiting.length === 0 ? (
            <div className="empty">You are not on any waiting list.</div>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Event</th>
                    <th>Ticket type</th>
                    <th>Quantity</th>
                    <th>Status</th>
                    <th>Position</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {waiting.map((w) => (
                    <tr key={w.id}>
                      <td><Link to={`/events/${w.eventId}`}>{w.eventTitle}</Link></td>
                      <td>{w.ticketTypeName}</td>
                      <td>{w.quantity}</td>
                      <td><StatusBadge status={w.status} cancelledLabel="Left" /></td>
                      <td>{w.status === 'WAITING' ? `#${w.position}` : '-'}</td>
                      <td>
                        {w.status === 'WAITING' && (
                          <button
                            type="button"
                            className="link-danger"
                            onClick={() => setPending({ kind: 'waiting', item: w })}
                          >
                            Leave
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      <ConfirmDialog
        open={pending !== null}
        title={dialogTitle}
        message={dialogMessage}
        confirmLabel={isBooking ? 'Cancel booking' : 'Leave'}
        cancelLabel="Keep it"
        busy={busy}
        onConfirm={handleConfirm}
        onCancel={() => setPending(null)}
      />

      <TicketsDialog booking={viewing} onClose={() => setViewing(null)} />
    </div>
  );
}