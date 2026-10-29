import { useEffect, useState } from 'react';
import { isCancel } from 'axios';
import { Link, useParams } from 'react-router-dom';
import api from '../api/client';
import { getErrorMessage } from '../api/errors';
import { formatDateTime } from '../utils/format';

function csvCell(value) {
  let text = String(value ?? '');
  // A name like "=SUM(1+1)" would run as a formula when opened in Excel, so we defuse it
  if (/^[=+\-@]/.test(text)) text = `'${text}`;
  return `"${text.replaceAll('"', '""')}"`;
}

export default function Attendees() {
  const { id } = useParams();
  const [refreshKey, setRefreshKey] = useState(0);
  const [result, setResult] = useState({ id: null, data: null, error: '' });
  const [search, setSearch] = useState('');

  useEffect(() => {
    const controller = new AbortController();

    api
      .get(`/api/organizer/events/${id}/attendees`, { signal: controller.signal })
      .then(({ data }) => setResult({ id, data, error: '' }))
      .catch((err) => {
        if (isCancel(err)) return;
        setResult({ id, data: null, error: getErrorMessage(err) });
      });

    return () => controller.abort();
  }, [id, refreshKey]);

  const loading = result.id !== id;
  const { data, error } = result;

  const term = search.trim().toLowerCase();
 
//const term = search.trim().toLowerCase();

const rows = data
  ? data.attendees.filter((a) =>
      [
        a.attendeeName,
        a.attendeeEmail,
        a.ticketCode,
        a.ticketTypeName,
      ].some((value) =>
        String(value ?? '').toLowerCase().includes(term)
      )
    )
  : [];


  const downloadCsv = () => {
    const header = ['Name', 'Email', 'Ticket type', 'Ticket code', 'Checked in at'];
    const lines = data.attendees.map((a) =>
      [a.attendeeName, a.attendeeEmail, a.ticketTypeName, a.ticketCode, a.checkedInAt ?? '']
        .map(csvCell)
        .join(','),
    );
    const blob = new Blob([[header.map(csvCell).join(','), ...lines].join('\n')], {
      type: 'text/csv;charset=utf-8',
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `attendees-event-${id}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="container page">
      <Link to="/organizer/events" className="back-link">&larr; My events</Link>

      {loading && <p className="muted">Loading...</p>}
      {!loading && error && <div className="alert-error" role="alert">{error}</div>}

      {!loading && data && (
        <>
          <div className="page-header">
            <div>
              <h1>{data.eventTitle}</h1>
              <p className="muted">{formatDateTime(data.eventStartTime)}</p>
            </div>
          </div>

          <div className="stats">
            <div className="stat">
              <div className="muted">Tickets sold</div>
              <div className="stat-value">{data.totalTickets}</div>
            </div>
            <div className="stat">
              <div className="muted">Checked in</div>
              <div className="stat-value">{data.checkedInCount}</div>
            </div>
            <div className="stat">
              <div className="muted">Still to arrive</div>
              <div className="stat-value">{data.totalTickets - data.checkedInCount}</div>
            </div>
          </div>

          <div className="toolbar">
            <input
              className="search-input"
              type="search"
              placeholder="Search by name, email or code"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <div className="actions">
              <button type="button" className="btn btn-outline" onClick={() => setRefreshKey((k) => k + 1)}>
                Refresh
              </button>
              <button
                type="button"
                className="btn btn-outline"
                onClick={downloadCsv}
                disabled={data.attendees.length === 0}
              >
                Download CSV
              </button>
            </div>
          </div>

          {data.attendees.length === 0 ? (
            <div className="empty">No tickets have been sold yet.</div>
          ) : rows.length === 0 ? (
            <div className="empty">No attendees match your search.</div>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Email</th>
                    <th>Ticket</th>
                    <th>Code</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((a) => (
                    <tr key={a.ticketId}>
                      <td>{a.attendeeName}</td>
                      <td>{a.attendeeEmail}</td>
                      <td>{a.ticketTypeName}</td>
                      <td><code title={a.ticketCode}>{a.ticketCode.slice(0, 8)}</code></td>
                      <td>
                        {a.checkedIn ? (
                          <span className="status status-published">
                            Checked in {formatDateTime(a.checkedInAt)}
                          </span>
                        ) : (
                          <span className="status status-draft">Not yet</span>
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
    </div>
  );
}