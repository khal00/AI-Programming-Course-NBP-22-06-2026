// Karma configuration
// Generated for Angular 19 + headless CI/Windows Server environment.
// Uses a ChromeHeadlessNoSandbox custom launcher so tests can run without
// a display server or sandbox (required in Azure/Windows Server environments).

// Point Karma at the system Chrome on Windows Server when not set in env
if (!process.env['CHROME_BIN']) {
  const fs = require('fs');
  const candidates = [
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  ];
  for (const c of candidates) {
    if (fs.existsSync(c)) {
      process.env['CHROME_BIN'] = c;
      break;
    }
  }
}

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma')
    ],
    client: {
      jasmine: {},
      clearContext: false
    },
    jasmineHtmlReporter: {
      suppressAll: true
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/frontend'),
      subdir: '.',
      reporters: [
        { type: 'html' },
        { type: 'text-summary' }
      ]
    },
    reporters: ['progress', 'kjhtml'],
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: [
          '--no-sandbox',
          '--disable-gpu',
          '--headless=new',
          '--disable-dev-shm-usage',
          '--disable-setuid-sandbox'
        ]
      }
    },
    browsers: ['ChromeHeadlessNoSandbox'],
    restartOnFileChange: true,
    singleRun: false
  });
};
