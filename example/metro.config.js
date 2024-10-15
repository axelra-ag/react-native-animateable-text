const { getDefaultConfig } = require('expo/metro-config');
const path = require('path');

const config = getDefaultConfig(__dirname);

config.resolver.blockList = [
  ...Array.from(config.resolver.blockList ?? []),
  new RegExp(path.resolve('..', 'node_modules', 'react-native')),
];

config.resolver.nodeModulesPaths = [
  path.resolve(__dirname, './node_modules'),
  path.resolve(__dirname, '../node_modules'),
];

config.watchFolders = [path.resolve(__dirname, '..')];

// this is causing me some issues after build (white screen)
// config.transformer.getTransformOptions = async () => ({
//   transform: {
//     experimentalImportSupport: false,
//     inlineRequires: true,
//   },
// });

module.exports = config;