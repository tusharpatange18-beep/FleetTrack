import { NavLink } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

/** Top bar: logo, links to the two pages, the username and a logout button. */
export function Header() {
  const { username, logout } = useAuth();
  return (
    <header className="topbar">
      <div className="brand">Fleet<span>Track</span></div>
      <nav>
        {/* NavLink adds the "active" CSS class to the link of the page you're on */}
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

/** A coloured label for a status, e.g. "IN_TRANSIT" is shown as "IN TRANSIT" with class chip-in_transit. */
export function StatusChip({ status }: { status: string }) {
  const cssClass = 'chip chip-' + status.toLowerCase();
  const text = status.replace('_', ' ');
  return <span className={cssClass}>{text}</span>;
}
