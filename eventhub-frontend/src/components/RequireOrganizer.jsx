import { Navigate, Outlet, useLocation } from 'react-router-dom';
import useAuth from '../hooks/useAuth';

export default function RequireOrganizer() {
  const { isAuthenticated, isOrganizer } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (!isOrganizer) {
    return (
      <div className="container page">
        <h1>Organizers only</h1>
        <p className="muted">
          Your account can&apos;t open this area. Register an organizer account to create events.
        </p>
      </div>
    );
  }

  return <Outlet />;
}