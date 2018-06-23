// Empty constructor
function XapkReader() {}

// The function that passes work along to native shells
// Message is a string, duration may be 'long' or 'short'
var exec = require('cordova/exec');
var utils = require('cordova/utils');

exports.downloadExpansionIfAvailable = function(successCB, errorCB) {
    exec(successCB, errorCB, "XAPKReader", "downloadExpansionIfAvailable");
};

// Installation constructor that binds ToastyPlugin to window
XapkReader.install = function() {
  if (!window.plugins) {
    window.plugins = {};
  }
  window.plugins.xapkreader = new XapkReader();
  return window.plugins.xapkreader;
};
cordova.addConstructor(XapkReader.install);