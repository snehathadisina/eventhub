import { useEffect, useState } from 'react';
import { isCancel } from 'axios';
import { useSearchParams } from 'react-router-dom';
import api from '../api/client';
import { getErrorMessage } from '../api/errors';
import EventCard from '../components/EventCard';
import EventFilters from '../components/EventFilters';
import Pagination from '../components/Pagination';

const FILTER_KEYS = ['q', 'city', 'category', 'from', 'to'];

export default function Events() {
  const [searchParams, setSearchParams] = useSearchParams();
  const queryKey = searchParams.toString();

  const filters = Object.fromEntries(
    FILTER_KEYS.map((key) => [key, searchParams.get(key) ?? '']),
  );
  const filtersKey = JSON.stringify(filters);

  const [result, setResult] = useState({ key: null, data: null, error: '' });

  useEffect(() => {
    const controller = new AbortController();
    const params = Object.fromEntries(new URLSearchParams(queryKey));

    api
      .get('/api/events', { params, signal: controller.signal })
      .then(({ data }) => setResult({ key: queryKey, data, error: '' }))
      .catch((err) => {
        if (isCancel(err)) return;
        setResult({ key: queryKey, data: null, error: getErrorMessage(err) });
      });

    return () => controller.abort();
  }, [queryKey]);

  const loading = result.key !== queryKey;
  const { data, error } = result;

  const applyFilters = (next) => {
    const params = new URLSearchParams();
    FILTER_KEYS.forEach((key) => {
      const value = (next[key] ?? '').trim();
      if (value) params.set(key, value);
    });
    const size = searchParams.get('size');
    if (size) params.set('size', size);
    setSearchParams(params);
  };

  const goToPage = (page) => {
    const params = new URLSearchParams(searchParams);
    if (page <= 0) params.delete('page');
    else params.set('page', String(page));
    setSearchParams(params);
    window.scrollTo({ top: 0 });
  };

  const hasFilters = FILTER_KEYS.some((key) => filters[key]);

  return (
    <div className="container page">
      <h1>Upcoming events</h1>

      <EventFilters key={filtersKey} initial={filters} onApply={applyFilters} />

      {loading && <p className="muted">Loading events...</p>}
      {!loading && error && <div className="alert-error" role="alert">{error}</div>}

      {!loading && !error && data && (
        <>
          <p className="muted">
            {data.totalElements} event{data.totalElements === 1 ? '' : 's'} found
          </p>

          {data.content.length === 0 ? (
            <p className="muted">
              {hasFilters
                ? 'No events match your search. Try changing or clearing the filters.'
                : 'No upcoming events yet.'}
            </p>
          ) : (
            <div className="event-grid">
              {data.content.map((event) => (
                <EventCard key={event.id} event={event} />
              ))}
            </div>
          )}

          <Pagination page={data.page} totalPages={data.totalPages} onChange={goToPage} />
        </>
      )}
    </div>
  );
}