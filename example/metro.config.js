const { getDefaultConfig } = require('expo/metro-config');
const path = require('path');

const config = getDefaultConfig(__dirname);

const root = path.resolve(__dirname, '..');
const rootModules = path.join(root, 'node_modules');
const escape = (p) => p.replace(/[/\\^$*+?.()|[\]{}]/g, '\\$&');

// The package is resolved from ../src so JS edits are live (no yalc publish).
// Root devDependencies are blocked so the example's copies are the only ones.
config.resolver.blockList = [
  ...Array.from(config.resolver.blockList ?? []),
  ...['react-native', 'react', 'react-native-reanimated', 'react-native-worklets'].map(
    (name) => new RegExp(`^${escape(path.join(rootModules, name))}[/\\\\]`)
  ),
];

config.resolver.nodeModulesPaths = [
  path.resolve(__dirname, './node_modules'),
  rootModules,
];

const defaultResolveRequest = config.resolver.resolveRequest;
config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (moduleName === 'react-native-animateable-text') {
    return context.resolveRequest(context, path.join(root, 'src', 'index'), platform);
  }
  return (defaultResolveRequest ?? context.resolveRequest)(context, moduleName, platform);
};

config.watchFolders = [root];

module.exports = config;
