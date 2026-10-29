import { useEffect, useState } from 'react';
import { isCancel } from 'axios';
import { Link, useNavigate, useParams } from 'react-router-dom';
import api from '../api/client';
import { getErrorMessage } from '../api/errors';
import ConfirmDialog from '../components/ConfirmDialog';
import { formatDateTime, formatPrice } from '../utils/format';

const EMPTY_CARD = { name: '', number: '', expiry: '', cvc: '' };

function luhnValid(digits) {
  let sum = 0;
  let double = false;
  for (let i = digits.length - 1; i >= 0; i -= 1) {
    let n = Number(digits[i]);
    if (double) {
      n *= 2;
      if (n > 9) n -= 9;
    }
    sum += n;
    double = !double;
  }
  return sum % 10 === 0;
}

function formatCardNumber(value) {
  return value.replace(/\D/g, '').slice(0, 19).replace(/(.{4})(?=.)/g, '$1 ');
}

function formatExpiry(value) {
  const digits = value.replace(/\D/g, '').slice(0, 4);
  return digits.length > 2 ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits;
}

function validateCard(card) {
  if (!card.name.trim()) return 'Enter the name on the card.';

  const digits = card.number.replace(/\D/g, '');
  if (digits.length < 13 || !luhnValid(digits)) return 'Enter a valid card number.';

  const match = /^(0[1-9]|1[0-2])\/(\d{2})$/.exec(card.expiry);
  if (!match) return 'Enter the expiry date as MM/YY.';
  const month = Number(match[1]);
  const year = 2000 + Number(match[2]);
  const today = new Date();
  if (year < today.getFullYear() || (year === today.getFullYear() && month < today.getMonth() + 1)) {
    return 'This card has expired.';
  }

  if (!/^\d{3,4}$/.test(card.cvc)) return 'Enter the 3 or 4 digit security code.';
  return '';
}

export default function Checkout() {
  const { bookingId } = useParams();
  const navigate = useNavigate();

  const [state, setState] = useState({ id: null, booking: null, deadline: 0, error: '' });
  const [now, setNow] = useState(0);
  const [card, setCard] = useState(EMPTY_CARD);
  const [paying, setPaying] = useState(false);
  const [message, setMessage] = useState(null); // { ok, text }
  const [askCancel, setAskCancel] = useState(false);
  const [cancelling, setCancelling] = useState(false);

  useEffect(() => {
    const controller = new AbortController();

    api
      .get(`/api/bookings/${bookingId}`, { signal: controller.signal })
      .then(({ data }) => {
        const loadedAt = Date.now();
        setNow(loadedAt);
        setState({
          id: bookingId,
          booking: data,
          // The countdown uses seconds-left from the server, so time zones can't break it
          deadline: data.secondsLeft != null ? loadedAt + data.secondsLeft * 1000 : 0,
          error: '',
        });
      })
      .catch((err) => {
        if (isCancel(err)) return;
        setState({ id: bookingId, booking: null, deadline: 0, error: getErrorMessage(err) });
      });

    return () => controller.abort();
  }, [bookingId]);

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  const loading = state.id !== bookingId;
  const { booking, deadline, error } = state;

  const secondsLeft = deadline > 0 ? Math.max(0, Math.ceil((deadline - now) / 1000)) : 0;
  const isPending = booking?.status === 'PENDING';
  const expired = isPending && deadline > 0 && secondsLeft === 0;
  const clock = `${String(Math.floor(secondsLeft / 60)).padStart(2, '0')}:${String(secondsLeft % 60).padStart(2, '0')}`;

  const updateCard = (field, value) => setCard((prev) => ({ ...prev, [field]: value }));

  const handlePay = async (e) => {
    e.preventDefault();
    const problem = validateCard(card);
    if (problem) {
      setMessage({ ok: false, text: problem });
      return;
    }

    setPaying(true);
    setMessage(null);
    try {
      // Only the last 4 digits leave the browser
      const cardLast4 = card.number.replace(/\D/g, '').slice(-4);
      const { data } = await api.post(`/api/bookings/${bookingId}/pay`, { cardLast4 });

      if (data.success) {
        setState((prev) => ({ ...prev, booking: data.booking }));
        setCard(EMPTY_CARD);
        setMessage({ ok: true, text: 'Payment successful. Your tickets are ready.' });
      } else {
        setMessage({ ok: false, text: data.message });
      }
    } catch (err) {
      setMessage({ ok: false, text: getErrorMessage(err) });
    } finally {
      setPaying(false);
    }
  };

  const handleCancel = async () => {
    setCancelling(true);
    try {
      await api.post(`/api/bookings/${bookingId}/cancel`);
      navigate('/my-tickets');
    } catch (err) {
      setAskCancel(false);
      setMessage({ ok: false, text: getErrorMessage(err) });
    } finally {
      setCancelling(false);
    }
  };

  return (
    <div className="container page">
      <div className="checkout">
        <Link to="/my-tickets" className="back-link">&larr; My tickets</Link>
        <h1>Checkout</h1>

        {loading && <p className="muted">Loading...</p>}
        {!loading && error && <div className="alert-error" role="alert">{error}</div>}

        {!loading && booking && (
          <>
            <section className="checkout-summary">
              <dl>
                <dt>Event</dt>
                <dd><Link to={`/events/${booking.eventId}`}>{booking.eventTitle}</Link></dd>
                <dt>When</dt>
                <dd>{formatDateTime(booking.eventStartTime)}</dd>
                <dt>Tickets</dt>
                <dd>{booking.quantity} x {booking.ticketTypeName}</dd>
                <dt>Total</dt>
                <dd>{formatPrice(booking.totalAmount)}</dd>
              </dl>
            </section>

            {message && (
              <div className={`pay-message ${message.ok ? 'ok' : 'fail'}`} role={message.ok ? 'status' : 'alert'}>
                {message.text}
              </div>
            )}

            {booking.status === 'CONFIRMED' && (
              <div className="alert-success" role="status">
                This booking is paid. <Link to="/my-tickets">View your tickets and QR codes</Link>
              </div>
            )}

            {booking.status === 'CANCELLED' && (
              <div className="alert-error" role="alert">
                This booking was cancelled, so it can no longer be paid.{' '}
                <Link to={`/events/${booking.eventId}`}>Back to the event</Link>
              </div>
            )}

            {isPending && (
              <>
                <div className={`timer-banner${expired ? ' expired' : ''}`} role="timer">
                  {expired
                    ? 'Time is up. Your tickets are being released.'
                    : `Your tickets are held for ${clock}. Pay before the time runs out.`}
                </div>

                <div className="test-card-hint">
                  <strong>Test mode.</strong> Use <code>4242 4242 4242 4242</code> to succeed,{' '}
                  <code>4000 0000 0000 0002</code> to be declined, or <code>4000 0000 0000 9995</code> for
                  insufficient funds. Any future expiry and any 3 digit code. Card details stay in your
                  browser; only the last 4 digits are sent.
                </div>

                <form className="pay-form" onSubmit={handlePay}>
                  <label>
                    Name on card
                    <input
                      value={card.name}
                      onChange={(e) => updateCard('name', e.target.value)}
                      autoComplete="cc-name"
                      disabled={paying || expired}
                    />
                  </label>
                  <label>
                    Card number
                    <input
                      value={card.number}
                      onChange={(e) => updateCard('number', formatCardNumber(e.target.value))}
                      inputMode="numeric"
                      autoComplete="cc-number"
                      placeholder="4242 4242 4242 4242"
                      disabled={paying || expired}
                    />
                  </label>
                  <div className="pay-row">
                    <label>
                      Expiry (MM/YY)
                      <input
                        value={card.expiry}
                        onChange={(e) => updateCard('expiry', formatExpiry(e.target.value))}
                        inputMode="numeric"
                        autoComplete="cc-exp"
                        placeholder="12/30"
                        disabled={paying || expired}
                      />
                    </label>
                    <label>
                      Security code
                      <input
                        value={card.cvc}
                        onChange={(e) => updateCard('cvc', e.target.value.replace(/\D/g, '').slice(0, 4))}
                        inputMode="numeric"
                        autoComplete="cc-csc"
                        placeholder="123"
                        disabled={paying || expired}
                      />
                    </label>
                  </div>

                  <div className="form-actions">
                    <button type="submit" className="btn btn-primary" disabled={paying || expired}>
                      {paying ? 'Processing...' : `Pay ${formatPrice(booking.totalAmount)}`}
                    </button>
                    <button type="button" className="btn btn-outline" onClick={() => setAskCancel(true)} disabled={paying}>
                      Cancel booking
                    </button>
                  </div>
                </form>
              </>
            )}
          </>
        )}

        <ConfirmDialog
          open={askCancel}
          title="Cancel this booking?"
          message="Your tickets go back on sale."
          confirmLabel="Cancel booking"
          cancelLabel="Keep it"
          busy={cancelling}
          onConfirm={handleCancel}
          onCancel={() => setAskCancel(false)}
        />
      </div>
    </div>
  );
}