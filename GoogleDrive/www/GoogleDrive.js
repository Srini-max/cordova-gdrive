var exec = require('cordova/exec');

exports.print = function (arg0, success, error) {
    exec(success, error, 'GoogleDrive', 'print', [arg0]);
};

exports.signIn = function (successCallback, errorCallback) {
    exec(successCallback, errorCallback, "GoogleDrive", "signIn");
}

exports.signOut = function (successCallback, errorCallback) {
    exec(successCallback, errorCallback, "GoogleDrive", "signOut");
}

exports.pickFolder = function (successCallback, errorCallback) {
    exec(successCallback, errorCallback, "GoogleDrive", "pickFolder");
}

exports.uploadFileWithPicker = function (folderId, title, description, successCallback, errorCallback) {
    exec(successCallback, errorCallback, "GoogleDrive", "uploadFileWithPicker", [folderId, title, description]);
}

exports.uploadFile = function (drive, file_details,is_app_folder, successCallback, errorCallback) {
    exec(successCallback, errorCallback, "GoogleDrive", "uploadFile", [drive,file_details,is_app_folder]);
}

exports.query = function (successCallback, errorCallback)
{
    exec(successCallback, errorCallback, "GoogleDrive", "query");
}

exports.downloadFiles = function(downloadFiles,successCallback, errorCallback)
{
    exec(successCallback,errorCallback,"GoogleDrive", "downloadFiles",downloadFiles);
}
