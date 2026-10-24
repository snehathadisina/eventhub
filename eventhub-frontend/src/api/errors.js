export function getErrorMessage(error) {
  if (!error.response) {
    return 'Cannot reach the server. Please check your connection and try again.';
  }
  const data = error.response.data;
  if (data?.errors && typeof data.errors === 'object') {
    return Object.entries(data.errors)
      .map(([field, message]) => `${field} ${message}`)
      .join('. ');
  }
  return data?.detail || 'Something went wrong. Please try again.';
}