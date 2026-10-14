import * as React from 'react';
import { BackHandler, Linking, LogBox } from 'react-native';
import { AnimatedTextScreen } from './src/animated/AnimatedTextScreen';
import { HomeScreen } from './src/home/HomeScreen';
import { ParityScreen } from './src/parity/ParityScreen';

LogBox.ignoreAllLogs();

type Route =
  | { screen: 'home' }
  | { screen: 'animated' }
  | {
      screen: 'parity';
      page: number;
      pageSize: number;
      isPerturbed: boolean;
      autoAdvanceMs: number;
      runId: string;
      requestId: number;
    };

const HOME: Route = { screen: 'home' };

const parityRoute = (params: Record<string, string> = {}): Route => ({
  screen: 'parity',
  page: Number(params.page ?? 0),
  pageSize: Number(params.pageSize ?? 4),
  isPerturbed: params.perturb === '1',
  autoAdvanceMs: Number(params.autoAdvanceMs ?? 0),
  runId: params.runId ?? 'manual',
  requestId: Date.now(),
});

// animateabletext://home
// animateabletext://animated
// animateabletext://parity?page=0&pageSize=4&perturb=1&autoAdvanceMs=2500&runId=abc
function parseRoute(url: string | null): Route | null {
  if (!url) return null;
  const match = url.match(/^animateabletext:\/\/([^?]+)(?:\?(.*))?$/);
  if (!match) return null;
  const params = Object.fromEntries(
    (match[2] ?? '')
      .split('&')
      .filter(Boolean)
      .map((pair) => pair.split('=').map(decodeURIComponent))
  );
  if (match[1] === 'parity') return parityRoute(params);
  if (match[1] === 'animated') return { screen: 'animated' };
  if (match[1] === 'home') return HOME;
  return null;
}

export default function App() {
  const [route, setRoute] = React.useState<Route>(HOME);

  React.useEffect(() => {
    Linking.getInitialURL().then((url) => {
      const initial = parseRoute(url);
      if (initial) setRoute(initial);
    });
    const subscription = Linking.addEventListener('url', ({ url }) => {
      const next = parseRoute(url);
      if (next) setRoute(next);
    });
    return () => subscription.remove();
  }, []);

  React.useEffect(() => {
    if (route.screen === 'home') return;
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      setRoute(HOME);
      return true;
    });
    return () => subscription.remove();
  }, [route.screen]);

  const goHome = () => setRoute(HOME);

  if (route.screen === 'parity') {
    return (
      <ParityScreen
        key={route.requestId}
        page={route.page}
        pageSize={route.pageSize}
        isPerturbed={route.isPerturbed}
        autoAdvanceMs={route.autoAdvanceMs}
        runId={route.runId}
        onPageChange={(page) => setRoute({ ...route, page })}
        onBack={goHome}
      />
    );
  }
  if (route.screen === 'animated') {
    return <AnimatedTextScreen onBack={goHome} />;
  }
  return <HomeScreen onOpen={(id) => setRoute(id === 'parity' ? parityRoute() : { screen: id })} />;
}
