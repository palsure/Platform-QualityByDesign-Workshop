import { useState } from 'react';
import { HomePage } from './pages/HomePage';
import { BrowsePage } from './pages/BrowsePage';
import { DetailPage } from './pages/DetailPage';
import { Navbar, type NavTab } from './components/Navbar';
import {
  CATALOG,
  getHeroVideosForType,
  getLiveBrowseRows,
  getMoviesBrowseRows,
  getShowsBrowseRows,
  type Video,
} from './data/catalog';
import { parseDemoScenario } from './demo/scenarios';
import './App.css';

// ── E2E bypass ─────────────────────────────────────────────────────
function getE2EVideo(): { video: Video; autoPlay: boolean } | null {
  const params = new URLSearchParams(window.location.search);
  const autoPlay = params.get('e2e_autoplay') === '1' || params.get('e2e_autoplay') === 'true';
  if (!autoPlay) return null;

  const scenarioParam = params.get('scenario');
  const scenario      = parseDemoScenario(scenarioParam);

  const match =
    CATALOG.flatMap(r => r.videos).find(v => v.scenario === scenario) ??
    CATALOG[0].videos[0];

  return { video: match, autoPlay: true };
}

type AppPage = NavTab;
type Route =
  | { page: AppPage }
  | { page: 'detail'; video: Video; autoPlay?: boolean; returnTo: AppPage };

export default function App() {
  const e2e = getE2EVideo();

  const [route, setRoute] = useState<Route>(
    e2e ? { page: 'detail', video: e2e.video, autoPlay: e2e.autoPlay, returnTo: 'home' } : { page: 'home' },
  );

  const navigate = (page: AppPage) => setRoute({ page });
  const goDetail = (v: Video) =>
    setRoute(r => ({
      page: 'detail',
      video: v,
      returnTo: r.page === 'detail' ? 'home' : r.page,
    }));

  const goPlay = (v: Video) => {
    document.documentElement.requestFullscreen?.().catch(() => {});
    setRoute(r => ({
      page: 'detail',
      video: v,
      autoPlay: true,
      returnTo: r.page === 'detail' ? 'home' : r.page,
    }));
  };

  const activeTab: AppPage = route.page === 'detail' ? route.returnTo : route.page;
  const goBack = () => navigate(route.page === 'detail' ? route.returnTo : 'home');

  return (
    <div className="app-shell">
      <Navbar active={activeTab} onNavigate={navigate} />

      {route.page === 'home' && (
        <HomePage onSelect={goDetail} onPlay={goPlay} />
      )}

      {route.page === 'movies' && (
        <BrowsePage
          title="Movies"
          subtitle="Blockbusters, indies, and everything in between."
          heroVideos={getHeroVideosForType('movie')}
          rows={getMoviesBrowseRows()}
          onSelect={goDetail}
          onPlay={goPlay}
        />
      )}

      {route.page === 'shows' && (
        <BrowsePage
          title="TV Shows"
          subtitle="Series to binge, one episode at a time."
          heroVideos={getHeroVideosForType('show')}
          rows={getShowsBrowseRows()}
          onSelect={goDetail}
          onPlay={goPlay}
        />
      )}

      {route.page === 'live' && (
        <BrowsePage
          title="Live"
          subtitle="Sports, news, concerts, and nature — streaming now."
          heroVideos={getHeroVideosForType('live')}
          rows={getLiveBrowseRows()}
          onSelect={goDetail}
          onPlay={goPlay}
        />
      )}

      {route.page === 'detail' && (
        <DetailPage
          video={route.video}
          onBack={goBack}
          autoPlay={route.autoPlay ?? (e2e?.autoPlay ?? false)}
        />
      )}
    </div>
  );
}
