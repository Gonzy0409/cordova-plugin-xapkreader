// Empty constructor
function XapkReader() {}

// The function that passes work along to native shells
// Message is a string, duration may be 'long' or 'short'
var exec = require('cordova/exec');
var utils = require('cordova/utils');

module.exports.remapUrl = function(successCB, errorCB) {
    exec(successCB, errorCB, "XAPKReader", "remapUri");
};


