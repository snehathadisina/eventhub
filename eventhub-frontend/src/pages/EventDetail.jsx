// import { useEffect, useState } from 'react';
// import { isCancel } from 'axios';
// import { Link, useParams } from 'react-router-dom';
// import api from '../api/client';
// import { getErrorMessage } from '../api/errors';
// import { formatDateTime, formatPrice } from '../utils/format';

// export default function EventDetail() {
//   const { id } = useParams();
//   const [result, setResult] = useState({ id: null, event: null, notFound: false, error: '' });

//   useEffect(() => {
//     const controller = new AbortController();

//     api
//       .get(`/api/events/${id}`, { signal: controller.signal })
//       .then(({ data }) => setResult({ id, event: data, notFound: false, error: '' }))
//       .catch((err) => {
//         if (isCancel(err)) return;
//         const status = err.response?.status;
//         setResult({
//           id,
//           event: null,
//           notFound: status === 404 || status === 400,
//           error: getErrorMessage(err),
//         });
//       });

//     return () => controller.abort();
//   }, [id]);

//   const loading = result.id !== id;
//   const { event, notFound, error } = result;

//   return (
//     <div className="container page">
//       <Link to="/" className="back-link">&larr; All events</Link>

//       {loading && <p className="muted">Loading...</p>}

//       {!loading && notFound && (
//         <>
//           <h1>Event not found</h1>
//           <p className="muted">This event doesn&apos;t exist or is no longer available.</p>
//         </>
//       )}

//       {!loading && !notFound && error && (
//         <div className="alert-error" role="alert">{error}</div>
//       )}

//       {!loading && event && (
//         <article className="detail">
//           {event.category && <span className="badge">{event.category}</span>}
//           <h1>{event.title}</h1>
//           <p className="event-meta">
//             {formatDateTime(event.startTime)}
//             {event.endTime && ` - ${formatDateTime(event.endTime)}`}
//           </p>
//           <p className="event-meta">
//             {[event.venue, event.city].filter(Boolean).join(', ')}
//           </p>
//           <p className="muted">Organized by {event.organizerName}</p>

//           {event.description && <p className="detail-desc">{event.description}</p>}

//           <h2>Tickets</h2>
//           {event.ticketTypes.length === 0 ? (
//             <p className="muted">Tickets are not available yet.</p>
//           ) : (
//             <table className="ticket-table">
//               <thead>
//                 <tr>
//                   <th>Type</th>
//                   <th>Price</th>
//                   <th>Availability</th>
//                 </tr>
//               </thead>
//               <tbody>
//                 {event.ticketTypes.map((t) => (
//                   <tr key={t.id}>
//                     <td>{t.name}</td>
//                     <td>{formatPrice(t.price)}</td>
//                     <td>
//                       {t.availableQuantity > 0
//                         ? `${t.availableQuantity} left`
//                         : 'Sold out'}
//                     </td>
//                   </tr>
//                 ))}
//               </tbody>
//             </table>
//           )}
//         </article>
//       )}
//     </div>
//   );
// }


import { useEffect, useState } from 'react';
import { isCancel } from 'axios';
import { Link, useParams } from 'react-router-dom';
import api from '../api/client';
import { getErrorMessage } from '../api/errors';
import TicketRow from '../components/TicketRow';
import useAuth from '../hooks/useAuth';
import { formatDateTime } from '../utils/format';

export default function EventDetail() {
  const { id } = useParams();
  const { isAuthenticated } = useAuth();

  const [refreshKey, setRefreshKey] = useState(0);
  const [result, setResult] = useState({
    id: null,
    event: null,
    started: false,
    notFound: false,
    error: '',
  });
  const [waiting, setWaiting] = useState([]);
  const [notice, setNotice] = useState({ id: null, text: '' });

  useEffect(() => {
    const controller = new AbortController();

    api
      .get(`/api/events/${id}`, { signal: controller.signal })
      .then(({ data }) =>
        setResult({
          id,
          event: data,
          started: Date.parse(data.startTime) <= Date.now(),
          notFound: false,
          error: '',
        }),
      )
      .catch((err) => {
        if (isCancel(err)) return;
        const status = err.response?.status;
        setResult({
          id,
          event: null,
          started: false,
          notFound: status === 404 || status === 400,
          error: getErrorMessage(err),
        });
      });

    return () => controller.abort();
  }, [id, refreshKey]);

  useEffect(() => {
    if (!isAuthenticated) return undefined;
    const controller = new AbortController();

    api
      .get('/api/waiting-list/me', { signal: controller.signal })
      .then(({ data }) => setWaiting(data))
      .catch((err) => {
        if (!isCancel(err)) setWaiting([]);
      });

    return () => controller.abort();
  }, [isAuthenticated, refreshKey]);

  const handleChanged = () => setRefreshKey((k) => k + 1);
  const handleDone = (text) => {
    setNotice({ id, text });
    handleChanged();
  };

  const loading = result.id !== id;
  const { event, started, notFound, error } = result;

  const waitingByTicketType = {};
  if (isAuthenticated) {
    waiting
      .filter((w) => w.status === 'WAITING')
      .forEach((w) => {
        waitingByTicketType[w.ticketTypeId] = w;
      });
  }

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

          {notice.id === id && notice.text && (
            <div className="alert-success" role="status">
              {notice.text} <Link to="/my-tickets">View my tickets</Link>
            </div>
          )}

          {event.ticketTypes.length === 0 ? (
            <p className="muted">Tickets are not available yet.</p>
          ) : (
            <div className="table-scroll">
              <table className="ticket-table">
                <thead>
                  <tr>
                    <th>Type</th>
                    <th>Price</th>
                    <th>Availability</th>
                    <th>Book</th>
                  </tr>
                </thead>
                <tbody>
                  {event.ticketTypes.map((t) => (
                    <TicketRow
                      key={t.id}
                      ticket={t}
                      eventStarted={started}
                      waitingEntry={waitingByTicketType[t.id]}
                      onDone={handleDone}
                      onChanged={handleChanged}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </article>
      )}
    </div>
  );
}