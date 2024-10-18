const path = require('path');
const pak = require('../package.json');

// linking the package to the example
module.exports = {
  dependencies: {
    [pak.name]: {
      root: path.join(__dirname, '..'),
    },
  },
};
