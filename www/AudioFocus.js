var exec = require('cordova/exec');

exports.MODE_IN_COMMUNICATION = 100;
exports.MODE_NORMAL = 101;
exports.MODE_RINGTONE = 102;

exports.requestFocus = function(mode, success, error) {
    success = success || function() { };
    error = error || function() { };

    exec(success, error, "AudioFocus", "requestFocus", [mode]);
};


exports.dumpFocus = function(mode, success, error) {
    success = success || function() { };
    error = error || function() { };

    exec(success, error, "AudioFocus", "dumpFocus", [mode]);
};
