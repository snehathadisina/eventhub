import { useEffect, useRef, useState } from 'react';
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
  const inputRef = useRef(null);
  const scanCounter = useRef(0);

  const [refreshKey, setRefreshKey] = useState(0);
  const [result, setResult] = useState({ id: null, data: null, error: '' });
  const [search, setSearch] = useState('');
  const [code, setCode] = useState('');
  const [scanning, setScanning] = useState(false);
  const [banner, setBanner] = useState(null); // { ok, text }
  const [recent, setRecent] = useState([]);

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

  const addRecent = (ok, text) => {
    scanCounter.current += 1;
    setRecent((prev) => [{ key: scanCounter.current, ok, text }, ...prev].slice(0, 8));
  };

  const checkIn = async (ticketCode) => {
    setScanning(true);
    try {
      const { data: res } = await api.post(`/api/organizer/events/${id}/check-in`, { ticketCode });
      const text = `${res.attendeeName} - ${res.ticketTypeName}`;
      setBanner({ ok: true, text: `Checked in: ${text}` });
      addRecent(true, text);

      // Update the counters and that one row without reloading the whole list
      setResult((prev) => {
        if (!prev.data) return prev;
        return {
          ...prev,
          data: {
            ...prev.data,
            totalTickets: res.totalTickets,
            checkedInCount: res.checkedInCount,
            attendees: prev.data.attendees.map((a) =>
              a.ticketCode === res.ticketCode
                ? { ...a, checkedIn: true, checkedInAt: res.checkedInAt }
                : a,
            ),
          },
        };
      });
    } catch (err) {
      const message = getErrorMessage(err);
      setBanner({ ok: false, text: message });
      addRecent(false, message);
    } finally {
      setScanning(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const trimmed = code.trim();
    if (!trimmed) return;
    setCode('');
    await checkIn(trimmed);
    inputRef.current?.focus();
  };

  const loading = result.id !== id;
  const { data, error } = result;

  const term = search.trim().toLowerCase();
  const rows = data
    ? data.attendees.filter(
        (a) =>
          !term ||
          [a.attendeeName, a.attendeeEmail, a.ticketCode].some((v) => v.toLowerCase().includes(term)),
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

          <section className="scan-card">
            <h2>Check in</h2>
            <p className="muted">
              Scan a ticket with a barcode scanner, or type or paste the ticket code and press Enter.
            </p>
            <form className="scan-form" onSubmit={handleSubmit}>
              <input
                ref={inputRef}
                value={code}
                onChange={(e) => setCode(e.target.value)}
                placeholder="Ticket code"
                aria-label="Ticket code"
                autoFocus
                autoComplete="off"
              />
              <button type="submit" className="btn btn-primary">Check in</button>
            </form>

            {banner && (
              <div
                className={`scan-banner ${banner.ok ? 'scan-banner-ok' : 'scan-banner-error'}`}
                role={banner.ok ? 'status' : 'alert'}
              >
                {banner.text}
              </div>
            )}

            {recent.length > 0 && (
              <ul className="recent-scans">
                {recent.map((r) => (
                  <li key={r.key} className={r.ok ? 'ok' : 'fail'}>{r.text}</li>
                ))}
              </ul>
            )}
          </section>

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
                    <th />
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
                      <td>
                        {!a.checkedIn && (
                          <button
                            type="button"
                            className="btn btn-outline btn-small"
                            onClick={() => checkIn(a.ticketCode)}
                            disabled={scanning}
                          >
                            Check in
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
    </div>
  );
}