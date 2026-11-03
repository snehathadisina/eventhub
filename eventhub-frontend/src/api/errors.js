// export function getErrorMessage(error) {
//   if (!error.response) {
//     return 'Cannot reach the server. Please check your connection and try again.';
//   }
//   const data = error.response.data;
//   if (data?.errors && typeof data.errors === 'object') {
//     return Object.entries(data.errors)
//       .map(([field, message]) => `${field} ${message}`)
//       .join('. ');
//   }
//   return data?.detail || 'Something went wrong. Please try again.';
// }



// export function getErrorMessage(error) {
//   if (!error.response) {
//     return 'Cannot reach the server. Please check your connection and try again.';
//   }

//   const status = error.response.status;
//   const data = error.response.data;

//   if (status === 404) {
//     return 'Booking not found.';
//   }

//   if (status === 401 || status === 403) {
//     return 'You are not authorized to access this resource.';
//   }

//   if (data?.errors && typeof data.errors === 'object') {
//     return Object.entries(data.errors)
//       .map(([field, message]) => `${field} ${message}`)
//       .join('. ');
//   }

//   return data?.detail || 'Something went wrong. Please try again.';
// }





export function getErrorMessage(error) {
  if (!error.response) {
    return 'Cannot reach the server. Please check your connection and try again.';
  }

  const status = error.response.status;
  const data = error.response.data;

  if (data?.errors && typeof data.errors === 'object') {
    return Object.entries(data.errors)
      .map(([field, message]) => `${field} ${message}`)
      .join('. ');
  }

  if (status === 404) {
    return 'Booking not found.';
  }

  if (status === 401) {
    return data?.detail || 'Invalid email or password.';
  }

  if (status === 403) {
    return data?.detail || 'You are not authorized to access this resource.';
  }

  if (status === 429) {
    return data?.detail || 'Too many attempts. Please try again later.';
  }

  return data?.detail || 'Something went wrong. Please try again.';
}
