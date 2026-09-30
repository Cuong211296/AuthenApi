import { useEffect } from 'react';
import { useLocation, useNavigationType } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';
import Header from './Header.jsx';
import Footer from './Footer.jsx';

/**
 * Page chrome: skip link, sticky header, <main> and footer. `children(location)` renders the routes
 * for the given location so AnimatePresence can keep the leaving page mounted during its exit.
 */
export default function AppShell({ children }) {
  const location = useLocation();
  const navigationType = useNavigationType();

  // New pages start at the top; back/forward (POP) keeps the browser's own scroll restoration.
  useEffect(() => {
    if (navigationType !== 'POP') window.scrollTo(0, 0);
    // Only a pathname change should scroll, not a change of navigation type alone.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname]);

  return (
    <>
      <a href="#main" className="skip-link">Bỏ qua đến nội dung chính</a>
      <Header />
      <main id="main" tabIndex={-1} className="app-main">
        <AnimatePresence mode="wait" initial={false}>
          {children(location)}
        </AnimatePresence>
      </main>
      <Footer />
    </>
  );
}
