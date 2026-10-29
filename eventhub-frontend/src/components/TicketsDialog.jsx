import { useEffect, useRef } from 'react';
import { formatDateTime } from '../utils/format';
import TicketCard from './TicketCard';

export default function TicketsDialog({ booking, onClose }) {
  const ref = useRef(null);
  const open = booking !== null;

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  const handleCancel = (e) => {
    e.preventDefault();
    onClose();
  };

  const handleBackdropClick = (e) => {
    if (e.target === ref.current) onClose();
  };

  return (
    <dialog
      ref={ref}
      className="tickets-dialog"
      onCancel={handleCancel}
      onClick={handleBackdropClick}
    >
      {booking && (
        <div className="tickets-body">
          <h2>{booking.eventTitle}</h2>
          <p className="muted">{formatDateTime(booking.eventStartTime)}</p>
          <p className="muted">Show a QR code at the entrance. Each code works only once.</p>

          <div className="ticket-grid">
            {booking.tickets.map((ticket, index) => (
              <TicketCard
                key={ticket.code}
                ticket={ticket}
                label={`${booking.ticketTypeName} ${index + 1} of ${booking.tickets.length}`}
              />
            ))}
          </div>

          <div className="confirm-actions">
            <button type="button" className="btn btn-outline" onClick={onClose}>Close</button>
          </div>
        </div>
      )}
    </dialog>
  );
}