// import { useEffect, useState } from 'react';
// import { isCancel } from 'axios';
// import { Link, useParams } from 'react-router-dom';
// import api from '../api/client';
// import { getErrorMessage } from '../api/errors';
// import { formatPrice } from '../utils/format';

// const EMPTY = { name: '', price: '', totalQuantity: '' };

// export default function ManageTickets() {
//   const { id } = useParams();
//   const [reloadKey, setReloadKey] = useState(0);
//   const loadKey = `${id}:${reloadKey}`;
//   const [result, setResult] = useState({ key: null, event: null, ticketTypes: [], error: '' });

//   const [form, setForm] = useState(EMPTY);
//   const [formError, setFormError] = useState('');
//   const [success, setSuccess] = useState('');
//   const [submitting, setSubmitting] = useState(false);

//   useEffect(() => {
//     const controller = new AbortController();

//     Promise.all([
//       api.get(`/api/organizer/events/${id}`, { signal: controller.signal }),
//       api.get(`/api/organizer/events/${id}/ticket-types`, { signal: controller.signal }),
//     ])
//       .then(([eventRes, ticketsRes]) =>
//         setResult({ key: loadKey, event: eventRes.data, ticketTypes: ticketsRes.data, error: '' }),
//       )
//       .catch((err) => {
//         if (isCancel(err)) return;
//         setResult({ key: loadKey, event: null, ticketTypes: [], error: getErrorMessage(err) });
//       });

//     return () => controller.abort();
//   }, [id, loadKey]);

//   const loading = result.key !== loadKey;
//   const { event, ticketTypes, error } = result;

//   const handleChange = (e) => {
//     setForm({ ...form, [e.target.name]: e.target.value });
//     if (success) setSuccess('');
//   };

//   const handleSubmit = async (e) => {
//     e.preventDefault();
//     setFormError('');
//     setSuccess('');

//     const name = form.name.trim();
//     const price = Number(form.price);
//     const quantity = Number(form.totalQuantity);

//     if (!name) {
//       setFormError('Ticket name is required.');
//       return;
//     }
//     if (form.price.trim() === '' || !Number.isFinite(price) || price < 0) {
//       setFormError('Enter a valid price (0 or more).');
//       return;
//     }
//     if (!Number.isInteger(quantity) || quantity < 1) {
//       setFormError('Quantity must be a whole number of at least 1.');
//       return;
//     }

//     setSubmitting(true);
//     try {
//       await api.post(`/api/organizer/events/${id}/ticket-types`, {
//         name,
//         price,
//         totalQuantity: quantity,
//       });
//       setForm(EMPTY);
//       setSuccess(`Ticket type "${name}" added. Add another, or click Done.`);
//       setReloadKey((k) => k + 1);
//     } catch (err) {
//       setFormError(getErrorMessage(err));
//     } finally {
//       setSubmitting(false);
//     }
//   };

//   return (
//     <div className="container page">
//       <Link to="/organizer/events" className="back-link">&larr; My events</Link>

//       {loading && <p className="muted">Loading...</p>}
//       {!loading && error && <div className="alert-error" role="alert">{error}</div>}

//       {!loading && event && (
//         <>
//           <h1>Tickets for {event.title}</h1>

//           {ticketTypes.length === 0 ? (
//             <p className="muted">No ticket types yet. Add one below so people can book.</p>
//           ) : (
//             <div className="table-wrap">
//               <table className="table">
//                 <thead>
//                   <tr>
//                     <th>Type</th>
//                     <th>Price</th>
//                     <th>Available</th>
//                     <th>Total</th>
//                   </tr>
//                 </thead>
//                 <tbody>
//                   {ticketTypes.map((t) => (
//                     <tr key={t.id}>
//                       <td>{t.name}</td>
//                       <td>{formatPrice(t.price)}</td>
//                       <td>{t.availableQuantity}</td>
//                       <td>{t.totalQuantity}</td>
//                     </tr>
//                   ))}
//                 </tbody>
//               </table>
//             </div>
//           )}

//           {event.status === 'CANCELLED' ? (
//             <p className="muted">This event is cancelled, so tickets can&apos;t be added.</p>
//           ) : (
//             <form className="form-card" onSubmit={handleSubmit}>
//               <h2>Add a ticket type</h2>
//               {success && <div className="alert-success" role="status">{success}</div>}
//               {formError && <div className="alert-error" role="alert">{formError}</div>}

//               <div className="field">
//                 <label htmlFor="name">Name</label>
//                 <input id="name" name="name" type="text" maxLength={255}
//                        placeholder="e.g. General, VIP"
//                        value={form.name} onChange={handleChange} required />
//               </div>

//               <div className="form-row">
//                 <div className="field">
//                   <label htmlFor="price">Price (INR)</label>
//                   <input id="price" name="price" type="number" min="0" step="0.01"
//                          value={form.price} onChange={handleChange} required />
//                 </div>
//                 <div className="field">
//                   <label htmlFor="totalQuantity">Quantity</label>
//                   <input id="totalQuantity" name="totalQuantity" type="number" min="1" step="1"
//                          value={form.totalQuantity} onChange={handleChange} required />
//                 </div>
//               </div>

//               <div className="form-actions">
//                 <button type="submit" className="btn btn-primary" disabled={submitting}>
//                   {submitting ? 'Adding...' : 'Add ticket type'}
//                 </button>
//                 <Link to="/organizer/events" className="btn btn-outline">Done</Link>
//               </div>
//             </form>
//           )}
//         </>
//       )}
//     </div>
//   );
// }




import { useEffect, useState } from 'react';
import { isCancel } from 'axios';
import { Link, useParams } from 'react-router-dom';
import api from '../api/client';
import { getErrorMessage } from '../api/errors';
import { formatPrice } from '../utils/format';

const EMPTY = { name: '', price: '', totalQuantity: '' };

export default function ManageTickets() {
  const { id } = useParams();
  const [reloadKey, setReloadKey] = useState(0);
  const loadKey = `${id}:${reloadKey}`;
  const [result, setResult] = useState({ key: null, event: null, ticketTypes: [], error: '' });

  const [form, setForm] = useState(EMPTY);
  const [formError, setFormError] = useState('');
  const [success, setSuccess] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    const controller = new AbortController();

    Promise.all([
      api.get(`/api/organizer/events/${id}`, { signal: controller.signal }),
      api.get(`/api/organizer/events/${id}/ticket-types`, { signal: controller.signal }),
    ])
      .then(([eventRes, ticketsRes]) =>
        setResult({ key: loadKey, event: eventRes.data, ticketTypes: ticketsRes.data, error: '' }),
      )
      .catch((err) => {
        if (isCancel(err)) return;
        setResult({ key: loadKey, event: null, ticketTypes: [], error: getErrorMessage(err) });
      });

    return () => controller.abort();
  }, [id, loadKey]);

  const loading = result.key !== loadKey;
  const { event, ticketTypes, error } = result;

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
    if (success) setSuccess('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFormError('');
    setSuccess('');

    const name = form.name.trim();
    const price = Number(form.price);
    const quantity = Number(form.totalQuantity);

    if (!name) {
      setFormError('Ticket name is required.');
      return;
    }
    if (form.price.trim() === '' || !Number.isFinite(price) || price < 0) {
      setFormError('Enter a valid price (0 or more).');
      return;
    }
    if (!Number.isInteger(quantity) || quantity < 1) {
      setFormError('Quantity must be a whole number of at least 1.');
      return;
    }

    setSubmitting(true);
    try {
      await api.post(`/api/organizer/events/${id}/ticket-types`, {
        name,
        price,
        totalQuantity: quantity,
      });
      setForm(EMPTY);
      setSuccess(`Ticket type "${name}" added. Add another, or click Done.`);
      setReloadKey((k) => k + 1);
    } catch (err) {
      setFormError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDone = () => {
    setShowForm(false);
    setForm(EMPTY);
    setFormError('');
    setSuccess('');
  };

  return (
    <div className="container page">
      <Link to="/organizer/events" className="back-link">&larr; My events</Link>

      {loading && <p className="muted">Loading...</p>}
      {!loading && error && <div className="alert-error" role="alert">{error}</div>}

      {!loading && event && (
        <>
          <h1>Tickets for {event.title}</h1>

          {ticketTypes.length === 0 ? (
            <p className="muted">No ticket types yet. Add one below so people can book.</p>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Type</th>
                    <th>Price</th>
                    <th>Available</th>
                    <th>Total</th>
                  </tr>
                </thead>
                <tbody>
                  {ticketTypes.map((t) => (
                    <tr key={t.id}>
                      <td>{t.name}</td>
                      <td>{formatPrice(t.price)}</td>
                      <td>{t.availableQuantity}</td>
                      <td>{t.totalQuantity}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {event.status === 'CANCELLED' ? (
            <p className="muted">This event is cancelled, so tickets can&apos;t be added.</p>
          ) : !showForm ? (
            <button type="button" className="btn btn-primary" onClick={() => setShowForm(true)}>
              + Add ticket type
            </button>
          ) : (
            <form className="form-card" onSubmit={handleSubmit}>
              <h2>Add a ticket type</h2>
              {success && <div className="alert-success" role="status">{success}</div>}
              {formError && <div className="alert-error" role="alert">{formError}</div>}

              <div className="field">
                <label htmlFor="name">Name</label>
                <input id="name" name="name" type="text" maxLength={255}
                       placeholder="e.g. General, VIP"
                       value={form.name} onChange={handleChange} required />
              </div>

              <div className="form-row">
                <div className="field">
                  <label htmlFor="price">Price (INR)</label>
                  <input id="price" name="price" type="number" min="0" step="0.01"
                         value={form.price} onChange={handleChange} required />
                </div>
                <div className="field">
                  <label htmlFor="totalQuantity">Quantity</label>
                  <input id="totalQuantity" name="totalQuantity" type="number" min="1" step="1"
                         value={form.totalQuantity} onChange={handleChange} required />
                </div>
              </div>

              <div className="form-actions">
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? 'Adding...' : 'Add ticket type'}
                </button>
                <button type="button" className="btn btn-outline" onClick={handleDone}>
                  Done
                </button>
              </div>
            </form>
          )}
        </>
      )}
    </div>
  );
}