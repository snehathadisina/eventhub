import { useEffect, useState } from 'react';
import { isCancel } from 'axios';
import { Link, useNavigate, useParams } from 'react-router-dom';
import api from '../api/client';
import { getErrorMessage } from '../api/errors';
import { toApiValue, toInputValue } from '../utils/format';

const EMPTY = {
  title: '',
  description: '',
  venue: '',
  city: '',
  category: '',
  imageUrl: '',
  startTime: '',
  endTime: '',
  status: 'PUBLISHED',
};

function toForm(event) {
  return {
    title: event.title ?? '',
    description: event.description ?? '',
    venue: event.venue ?? '',
    city: event.city ?? '',
    category: event.category ?? '',
    imageUrl: event.imageUrl ?? '',
    startTime: toInputValue(event.startTime),
    endTime: toInputValue(event.endTime),
    status: ['DRAFT', 'PUBLISHED'].includes(event.status) ? event.status : 'PUBLISHED',
  };
}

function EventFormBody({ eventId, initial }) {
  const navigate = useNavigate();
  const isEdit = Boolean(eventId);
  const [form, setForm] = useState(initial);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!form.title.trim()) {
      setError('Title is required.');
      return;
    }
    if (!form.startTime) {
      setError('Start time is required.');
      return;
    }
    if (form.endTime && form.endTime < form.startTime) {
      setError('End time must be after the start time.');
      return;
    }

    const payload = {
      title: form.title.trim(),
      description: form.description.trim() || null,
      venue: form.venue.trim() || null,
      city: form.city.trim() || null,
      category: form.category.trim() || null,
      imageUrl: form.imageUrl || null,
      startTime: toApiValue(form.startTime),
      endTime: toApiValue(form.endTime),
      status: form.status,
    };

    setSubmitting(true);
    try {
      if (isEdit) {
        await api.put(`/api/organizer/events/${eventId}`, payload);
        navigate('/organizer/events');
      } else {
        const { data } = await api.post('/api/organizer/events', payload);
        navigate(`/organizer/events/${data.id}/tickets`);
      }
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="container page">
      <Link to="/organizer/events" className="back-link">&larr; My events</Link>

      <form className="form-card" onSubmit={handleSubmit}>
        <h1>{isEdit ? 'Edit event' : 'Create event'}</h1>
        {error && <div className="alert-error" role="alert">{error}</div>}

        <div className="field">
          <label htmlFor="title">Title *</label>
          <input id="title" name="title" type="text" maxLength={255}
                 value={form.title} onChange={handleChange} required />
        </div>

        <div className="field">
          <label htmlFor="description">Description</label>
          <textarea id="description" name="description" rows={5} maxLength={2000}
                    value={form.description} onChange={handleChange} />
        </div>

        <div className="form-row">
          <div className="field">
            <label htmlFor="venue">Venue</label>
            <input id="venue" name="venue" type="text" maxLength={255}
                   value={form.venue} onChange={handleChange} />
          </div>
          <div className="field">
            <label htmlFor="city">City</label>
            <input id="city" name="city" type="text" maxLength={255}
                   value={form.city} onChange={handleChange} />
          </div>
        </div>

        <div className="form-row">
          <div className="field">
            <label htmlFor="category">Category</label>
            <input id="category" name="category" type="text" maxLength={255}
                   value={form.category} onChange={handleChange} />
          </div>
          <div className="field">
            <label htmlFor="status">Status</label>
            <select id="status" name="status" value={form.status} onChange={handleChange}>
              <option value="PUBLISHED">Published (visible to everyone)</option>
              <option value="DRAFT">Draft (hidden)</option>
            </select>
          </div>
        </div>

        <div className="form-row">
          <div className="field">
            <label htmlFor="startTime">Starts *</label>
            <input id="startTime" name="startTime" type="datetime-local"
                   value={form.startTime} onChange={handleChange} required />
          </div>
          <div className="field">
            <label htmlFor="endTime">Ends</label>
            <input id="endTime" name="endTime" type="datetime-local"
                   value={form.endTime} onChange={handleChange} />
          </div>
        </div>

        <button type="submit" className="btn btn-primary" disabled={submitting}>
          {submitting ? 'Saving...' : isEdit ? 'Save changes' : 'Create event'}
        </button>
      </form>
    </div>
  );
}

export default function EventForm() {
  const { id } = useParams();
  const [result, setResult] = useState({ id: null, event: null, error: '' });

  useEffect(() => {
    if (!id) return undefined;
    const controller = new AbortController();

    api
      .get(`/api/organizer/events/${id}`, { signal: controller.signal })
      .then(({ data }) => setResult({ id, event: data, error: '' }))
      .catch((err) => {
        if (isCancel(err)) return;
        setResult({ id, event: null, error: getErrorMessage(err) });
      });

    return () => controller.abort();
  }, [id]);

  if (!id) {
    return <EventFormBody initial={EMPTY} />;
  }

  if (result.id !== id) {
    return <div className="container page"><p className="muted">Loading...</p></div>;
  }

  if (result.error) {
    return (
      <div className="container page">
        <Link to="/organizer/events" className="back-link">&larr; My events</Link>
        <div className="alert-error" role="alert">{result.error}</div>
      </div>
    );
  }

  if (result.event.status === 'CANCELLED') {
    return (
      <div className="container page">
        <Link to="/organizer/events" className="back-link">&larr; My events</Link>
        <h1>Event cancelled</h1>
        <p className="muted">Cancelled events can&apos;t be edited.</p>
      </div>
    );
  }

  return <EventFormBody key={id} eventId={id} initial={toForm(result.event)} />;
}