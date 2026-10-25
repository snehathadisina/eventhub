const dateTime = new Intl.DateTimeFormat('en-IN', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
});

const currency = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
});

export function formatDateTime(value) {
  return value ? dateTime.format(new Date(value)) : '';
}

export function formatPrice(value) {
  return Number(value) === 0 ? 'Free' : currency.format(value);
}