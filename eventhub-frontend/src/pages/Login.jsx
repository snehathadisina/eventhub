// // import { useState } from 'react';
// import { useEffect, useState } from 'react';
// import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
// import GoogleSignInButton from '../components/GoogleSignInButton';
// import useAuth from '../hooks/useAuth';
// import { getErrorMessage } from '../api/errors';

// export default function Login() {
//   const { login, loginWithGoogle, isAuthenticated } = useAuth();
//   const navigate = useNavigate();
//   const location = useLocation();
//   const redirectTo = location.state?.from ?? '/';

//   const [form, setForm] = useState({ email: '', password: '' });
//   const [error, setError] = useState('');
//   const [submitting, setSubmitting] = useState(false);
//   const [retrySeconds, setRetrySeconds] = useState(0);

//   useEffect(() => {
//   if (retrySeconds <= 0) return;

//   const timer = setTimeout(() => {
//     setRetrySeconds((seconds) => seconds - 1);
//   }, 1000);

//   return () => clearTimeout(timer);
// }, [retrySeconds]);

//   if (isAuthenticated) {
//     return <Navigate to={redirectTo} replace />;
//   }

//   const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

//   const handleSubmit = async (e) => {
//     e.preventDefault();
//     setError('');
//     setSubmitting(true);
//     try {
//       await login(form.email, form.password);
//       navigate(redirectTo, { replace: true });
//     } catch (err) {
//       setError(getErrorMessage(err));
//     } finally {
//       setSubmitting(false);
//     }
//   };

//   const handleGoogle = async (idToken) => {
//     setError('');
//     setSubmitting(true);
//     try {
//       await loginWithGoogle(idToken);
//       navigate(redirectTo, { replace: true });
//     } catch (err) {
//       setError(getErrorMessage(err));
//     } finally {
//       setSubmitting(false);
//     }
//   };


//   return (
//     <div className="container">
//       <form className="auth-card" onSubmit={handleSubmit}>
//         <h1>Login</h1>
//         {error && <div className="alert-error" role="alert">{error}</div>}

//         <div className="field">
//           <label htmlFor="email">Email</label>
//           <input id="email" name="email" type="email" autoComplete="email"
//                  value={form.email} onChange={handleChange} required />
//         </div>

//         <div className="field">
//           <label htmlFor="password">Password</label>
//           <input id="password" name="password" type="password" autoComplete="current-password"
//                  value={form.password} onChange={handleChange} required />
//         </div>

//         <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
//           {submitting ? 'Please wait...' : 'Login'}
//         </button>

//         <GoogleSignInButton
//           onCredential={handleGoogle}
//           onError={setError}
//           disabled={submitting}
//         />

//         <p className="auth-switch">
//           New here? <Link to="/register">Create an account</Link>
//         </p>
//       </form>
//     </div>
//   );
// }






import { useEffect, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import GoogleSignInButton from '../components/GoogleSignInButton';
import useAuth from '../hooks/useAuth';
import { getErrorMessage } from '../api/errors';

export default function Login() {
  const { login, loginWithGoogle, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const redirectTo = location.state?.from ?? '/';

  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [retrySeconds, setRetrySeconds] = useState(0);

  // Countdown timer
  useEffect(() => {
    if (retrySeconds <= 0) return;

    const timer = setTimeout(() => {
      setRetrySeconds((seconds) => seconds - 1);
    }, 1000);

    return () => clearTimeout(timer);
  }, [retrySeconds]);

  // Clear rate-limit message when countdown finishes
  useEffect(() => {
    if (retrySeconds === 0 && error.startsWith('Too many attempts.')) {
      setError('');
    }
  }, [retrySeconds, error]);

  if (isAuthenticated) {
    return <Navigate to={redirectTo} replace />;
  }

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  // Normal login
  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setRetrySeconds(0);
    setSubmitting(true);

    try {
      await login(form.email, form.password);
      navigate(redirectTo, { replace: true });
    } catch (err) {
      setError(getErrorMessage(err));

      if (err.response?.status === 429) {
        const headerSeconds = Number(
          err.response.headers?.['retry-after']
        );

        const detailSeconds = Number(
          err.response.data?.detail?.match(/wait\s+(\d+)\s+seconds?/i)?.[1]
        );

        const seconds = headerSeconds > 0
          ? headerSeconds
          : detailSeconds;

        if (seconds > 0) {
          setRetrySeconds(seconds);
        }
      }
    } finally {
      setSubmitting(false);
    }
  };

  // Google login
  const handleGoogle = async (idToken) => {
    setError('');
    setSubmitting(true);

    try {
      await loginWithGoogle(idToken);
      navigate(redirectTo, { replace: true });
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="container">
      <form className="auth-card" onSubmit={handleSubmit}>
        <h1>Login</h1>

        {error && (
          <div className="alert-error" role="alert">
            {retrySeconds > 0
              ? `Too many attempts. Please wait ${retrySeconds} second${retrySeconds === 1 ? '' : 's'} and try again.`
              : error}
          </div>
        )}

        <div className="field">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            value={form.email}
            onChange={handleChange}
            required
          />
        </div>

        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={form.password}
            onChange={handleChange}
            required
          />
        </div>

        <button
          type="submit"
          className="btn btn-primary btn-block"
          disabled={submitting || retrySeconds > 0}
        >
          {retrySeconds > 0
            ? `Wait ${retrySeconds}s`
            : submitting
              ? 'Please wait...'
              : 'Login'}
        </button>

        <GoogleSignInButton
          onCredential={handleGoogle}
          onError={setError}
          disabled={submitting}
        />

        <p className="auth-switch">
          New here? <Link to="/register">Create an account</Link>
        </p>
      </form>
    </div>
  );
}
