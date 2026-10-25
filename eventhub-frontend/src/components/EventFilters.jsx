import { useState } from 'react';

export default function EventFilters({ initial, onApply }) {
  const [form, setForm] = useState(initial);
  const [error, setError] = useState('');

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = (e) => {
    e.preventDefault();
    if (form.from && form.to && form.to < form.from) {
      setError('The "to" date cannot be before the "from" date.');
      return;
    }
    setError('');
    onApply(form);
  };

  const handleClear = () => {
    const empty = { q: '', city: '', category: '', from: '', to: '' };
    setForm(empty);
    setError('');
    onApply(empty);
  };

  return (
    <form className="filters" onSubmit={handleSubmit}>
      <div className="filters-grid">
        <div className="field">
          <label htmlFor="q">Search</label>
          <input id="q" name="q" type="search" placeholder="Title, description or venue"
                 maxLength={100} value={form.q} onChange={handleChange} />
        </div>
        <div className="field">
          <label htmlFor="city">City</label>
          <input id="city" name="city" type="text" value={form.city} onChange={handleChange} />
        </div>
        <div className="field">
          <label htmlFor="category">Category</label>
          <input id="category" name="category" type="text" value={form.category} onChange={handleChange} />
        </div>
        <div className="field">
          <label htmlFor="from">From</label>
          <input id="from" name="from" type="date" value={form.from} onChange={handleChange} />
        </div>
        <div className="field">
          <label htmlFor="to">To</label>
          <input id="to" name="to" type="date" value={form.to} onChange={handleChange} />
        </div>
      </div>

      {error && <div className="alert-error" role="alert">{error}</div>}

      <div className="filters-actions">
        <button type="submit" className="btn btn-primary">Search</button>
        <button type="button" className="btn btn-outline" onClick={handleClear}>Clear</button>
      </div>
    </form>
  );
}