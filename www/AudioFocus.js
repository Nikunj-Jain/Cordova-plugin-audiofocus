var exec = require('cordova/exec');

exports.MODE_IN_COMMUNICATION = 100;
exports.MODE_NORMAL = 101;
exports.MODE_RINGTONE = 102;

exports.setModeInCommunication = function(success, error) {
    success = success || function() { };
    error = error || function() { };

    exec(success, error, "AudioFocus", "setModeInCommunication");
};

exports.playIncomingRing = function(success, error) {
    success = success || function() { };
    error = error || function() { };

    exec(success, error, "AudioFocus", "playIncomingRing");
};

exports.playOutgoingRing = function(success, error) {
    success = success || function() { };
    error = error || function() { };

    exec(success, error, "AudioFocus", "playOutgoingRing");
};

exports.dumpFocus = function() {
    exec(null, null, "AudioFocus", "dumpFocus");
};