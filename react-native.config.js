/**
 * @type {import('@react-native-community/cli-types').UserDependencyConfig}
 */
module.exports = {
  dependency: {
    platforms: {
      android: {
        libraryName: 'JBAnimatedText',
        componentDescriptors: ['CParagraphComponentDescriptor'],
        cmakeListsPath: '../android/src/main/jni/CMakeLists.txt',
      },
      macos: null,
      windows: null,
    },
  },
};
