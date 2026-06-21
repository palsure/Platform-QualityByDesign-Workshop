export type NavTab = 'home' | 'movies' | 'shows' | 'live';

interface NavbarProps {
  active: NavTab;
  onNavigate: (tab: NavTab) => void;
}

const LINKS: { tab: NavTab; label: string }[] = [
  { tab: 'home', label: 'Home' },
  { tab: 'movies', label: 'Movies' },
  { tab: 'shows', label: 'Shows' },
  { tab: 'live', label: 'Live' },
];

export function Navbar({ active, onNavigate }: NavbarProps) {
  return (
    <nav className="navbar">
      <button className="navbar-logo" onClick={() => onNavigate('home')} type="button" aria-label="Go home">
        <span className="navbar-logo-icon">▶</span>
        <span className="navbar-logo-text">StreamApp</span>
      </button>
      <ul className="navbar-links">
        {LINKS.map(({ tab, label }) => (
          <li key={tab}>
            <button
              type="button"
              className={active === tab ? 'navbar-link-active' : undefined}
              onClick={() => onNavigate(tab)}
              aria-current={active === tab ? 'page' : undefined}
            >
              {label}
            </button>
          </li>
        ))}
      </ul>
      <div className="navbar-actions">
        <button className="navbar-search" type="button" aria-label="Search">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="20" height="20">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
        </button>
        <div className="navbar-avatar" aria-label="Profile">
          <span>QE</span>
        </div>
      </div>
    </nav>
  );
}
