import { NavLink } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export function Header() {
  const { username, logout } = useAuth();
  return (
    <header className="topbar">
      <div className="brand">Fleet<span>Track</span></div>
      <nav>
        <NavLink to="/" end>Dispatch</NavLink>
        <NavLink to="/driver">Driver app</NavLink>
      </nav>
      <div className="topbar-user">
        <span className="muted">{username}</span>
        <button className="btn btn-ghost" onClick={logout}>Log out</button>
      </div>
    </header>
  );
}

export function StatusChip({ status }: { status: string }) {
  return <span className={`chip chip-${status.toLowerCase()}`}>{status.replace('_', ' ')}</span>;
}
