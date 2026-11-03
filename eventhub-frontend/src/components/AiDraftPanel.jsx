import { useEffect, useState } from 'react';
import { isCancel } from 'axios';
import api from '../api/client';
import { getErrorMessage } from '../api/errors';
import { formatPrice } from '../utils/format';

const TONES = [
  { value: 'friendly', label: 'Friendly' },
  { value: 'professional', label: 'Professional' },
  { value: 'fun', label: 'Fun' },
];

export default function AiDraftPanel({ city, onApply }) {
  const [enabled, setEnabled] = useState(null); // null while we find out
  const [open, setOpen] = useState(false);
  const [notes, setNotes] = useState('');
  const [tone, setTone] = useState('friendly');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [draft, setDraft] = useState(null);

  useEffect(() => {
    const controller = new AbortController();
    api
      .get('/api/organizer/ai/status', { signal: controller.signal })
      .then(({ data }) => setEnabled(Boolean(data.enabled)))
      .catch((err) => {
        if (!isCancel(err)) setEnabled(false);
      });
    return () => controller.abort();
  }, []);

  // No API key on the server, or still checking: show nothing
  if (!enabled) return null;

  const generate = async () => {
    setLoading(true);
    setError('');
    try {
      const { data } = await api.post('/api/organizer/ai/event-draft', {
        notes: notes.trim(),
        city: city.trim() || null,
        tone,
      });
      setDraft(data);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const priceLine = () => {
    if (draft.free) return 'This looks like a free event.';
    if (draft.priceMin != null && draft.priceMax != null) {
      return `Rough ticket price idea: ${formatPrice(draft.priceMin)} to ${formatPrice(draft.priceMax)}`;
    }
    return null;
  };

  return (
    <section className="ai-panel">
      <div className="ai-panel-head">
        <div>
          <strong>Write with AI</strong>
          <div className="ai-note">Type rough notes. You choose what to use and can edit everything.</div>
        </div>
        <button type="button" className="btn btn-outline btn-small" onClick={() => setOpen((o) => !o)}>
          {open ? 'Hide' : 'Open'}
        </button>
      </div>

      {open && (
        <>
          <div className="field">
            <label htmlFor="ai-notes">Your notes</label>
            <textarea
              id="ai-notes"
              rows={4}
              maxLength={1500}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="e.g. Java meetup for beginners, Saturday evening at T-Hub, free pizza, two talks on Spring Boot and testing"
            />
            <span className="hint">{notes.length}/1500. Don&apos;t include private information.</span>
          </div>

          <div className="field">
            <label htmlFor="ai-tone">Tone</label>
            <select id="ai-tone" value={tone} onChange={(e) => setTone(e.target.value)}>
              {TONES.map((t) => (
                <option key={t.value} value={t.value}>{t.label}</option>
              ))}
            </select>
          </div>

          <button
            type="button"
            className="btn btn-primary"
            onClick={generate}
            disabled={loading || notes.trim().length < 10}
          >
            {loading ? 'Writing...' : draft ? 'Write again' : 'Write my listing'}
          </button>

          {error && <div className="alert-error ai-error" role="alert">{error}</div>}

          {draft && (
            <div className="ai-result">
              <h3>{draft.title}</h3>
              <div className="ai-text">{draft.description}</div>
              <p className="ai-note">
                Category: {draft.category || 'none'}
                {priceLine() && <><br />{priceLine()}</>}
                {draft.priceNote && <><br />{draft.priceNote}</>}
              </p>

              <div className="ai-actions">
                <button type="button" className="btn btn-primary btn-small"
                        onClick={() => onApply({ title: draft.title, description: draft.description, category: draft.category })}>
                  Use everything
                </button>
                <button type="button" className="btn btn-outline btn-small"
                        onClick={() => onApply({ title: draft.title })}>
                  Use title
                </button>
                <button type="button" className="btn btn-outline btn-small"
                        onClick={() => onApply({ description: draft.description })}>
                  Use description
                </button>
                <button type="button" className="btn btn-outline btn-small"
                        onClick={() => onApply({ category: draft.category })}>
                  Use category
                </button>
              </div>
              <p className="ai-note">AI can make mistakes. Check every detail before you publish.</p>
            </div>
          )}
        </>
      )}
    </section>
  );
}