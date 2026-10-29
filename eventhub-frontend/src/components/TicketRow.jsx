import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import api from '../api/client';
import { getErrorMessage } from '../api/errors';
import useAuth from '../hooks/useAuth';
import { formatPrice } from '../utils/format';

const MAX_PER_BOOKING = 10;

export default function TicketRow({ ticket, eventStarted, waitingEntry, onDone, onChanged }) {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [quantity, setQuantity] = useState(1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const maxQuantity = Math.min(MAX_PER_BOOKING, ticket.totalQuantity);
  const canBookNow = quantity <= ticket.availableQuantity;

  const handleSubmit = async () => {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: location.pathname } });
      return;
    }

    setBusy(true);
    setError('');
    try {
      if (canBookNow) {
        const { data } = await api.post('/api/bookings', { ticketTypeId: ticket.id, quantity });
        if (data.status === 'PENDING') {
          // Paid tickets are held for a few minutes while the buyer pays
          navigate(`/checkout/${data.id}`);
          return;
        }
        onDone(`Booked ${quantity} x ${ticket.name}.`);
      } else {
        await api.post('/api/waiting-list', { ticketTypeId: ticket.id, quantity });
        onDone(`You are on the waiting list for ${quantity} x ${ticket.name}.`);
      }
    } catch (err) {
      setError(getErrorMessage(err));
      onChanged(); // refresh, in case someone bought the last tickets a moment ago
    } finally {
      setBusy(false);
    }
  };

  const handleLeave = async () => {
    setBusy(true);
    setError('');
    try {
      await api.delete(`/api/waiting-list/${waitingEntry.id}`);
      onDone('You left the waiting list.');
    } catch (err) {
      setError(getErrorMessage(err));
      onChanged();
    } finally {
      setBusy(false);
    }
  };

  let action;
  if (eventStarted) {
    action = <span className="muted">Event started</span>;
  } else if (waitingEntry) {
    action = (
      <div className="waiting-note">
        <span className="status status-draft">
          Waiting for {waitingEntry.quantity}, position {waitingEntry.position}
        </span>
        <button type="button" className="btn btn-outline btn-small" onClick={handleLeave} disabled={busy}>
          {busy ? 'Leaving...' : 'Leave'}
        </button>
      </div>
    );
  } else if (maxQuantity < 1) {
    action = <span className="muted">Not available</span>;
  } else {
    let label = 'Join waiting list';
    if (!isAuthenticated) {
      label = 'Login to book';
    } else if (canBookNow) {
      label = `Book (${formatPrice(Number(ticket.price) * quantity)})`;
    }

    action = (
      <>
        <div className="buy-controls">
          <select
            aria-label={`Quantity for ${ticket.name}`}
            value={quantity}
            onChange={(e) => setQuantity(Number(e.target.value))}
            disabled={busy}
          >
            {Array.from({ length: maxQuantity }, (_, i) => i + 1).map((n) => (
              <option key={n} value={n}>{n}</option>
            ))}
          </select>
          <button type="button" className="btn btn-primary" onClick={handleSubmit} disabled={busy}>
            {busy ? 'Please wait...' : label}
          </button>
        </div>
        {!canBookNow && ticket.availableQuantity > 0 && (
          <div className="hint">
            Only {ticket.availableQuantity} left. Pick {ticket.availableQuantity} or fewer to book now.
          </div>
        )}
      </>
    );
  }

  return (
    <tr>
      <td>{ticket.name}</td>
      <td>{formatPrice(ticket.price)}</td>
      <td>{ticket.availableQuantity > 0 ? `${ticket.availableQuantity} left` : 'Sold out'}</td>
      <td>
        {action}
        {error && <div className="inline-error" role="alert">{error}</div>}
      </td>
    </tr>
  );
}