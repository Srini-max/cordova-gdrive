package org.apache.cordova;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresPermission;
import android.os.Bundle;
import android.util.DebugUtils;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.auth.api.accounttransfer.AccountTransfer;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.drive.CreateFileActivityOptions;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.OpenFileActivityOptions;
import com.google.android.gms.drive.query.Filter;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import static android.app.Activity.RESULT_OK;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class GoogleDrive extends CordovaPlugin {
	private static final int REQUEST_CODE_RESOLUTION = 3;
    private final int CODE_UPLOAD_FILE = 10;
    private final int CODE_AUTH = 11;
    private final int CODE_FOLDER_PICKER = 12;
    private final String TAG = "GoogleDrivePlugin";
    private final String FILE_MIME = "application/octet-stream";
    private final String SUCCESS_NORMAL_SIGN_IN = "SignIn Success with Normal SignIn";
    private final String SUCCESS_SILENT_SIGN_IN = "SignIn Success with Silent SignIn";
    private final String FAILED_SIGN_IN = "SignIn failed";
    private final String ENTER_STREAM = "Enter To Stream";
    private final String EXIT_STREAM = "Exit From Stream";
    private final String FILE_CREATED = "File Created";
    private final String ERROR_FILE = "Unable to create File";
    private final String INITIAL = "Google Drive Plugin Initiated";
    private final String EXECUTING = "Google Drive Plugin Is Executing";
    private final String SIGN_OUT = "SignOut From User:";
    private final String SIGN_IN_ACTION = "signIn";
    private final String SILENT_SIGN_IN_ACTION = "silentSignIn";
    private final String SIGN_OUT_ACTION = "signOut";
    private final String PICK_FOLDER_ACTION = "pickFolder";
    private final String QUERY_FILES_ACTION = "query";
    private final String UPLOAD_FILE_WITH_FILE_PICKER_ACTION = "uploadFileWithPicker";
    private final String UPLOAD_FILE_ACTION = "uploadFile";
    private final String DOWNLOAD_FILES_ACTION ="downloadFiles";
    private final String SIGN_IN_PROGRESS = "Google Drive Plugin Is Trying To Login";
    private final String DRIVE_ID_DIC_KEY = "driveId";
    private final String DESCRIPTION_DRIVE_DIC_KEY = "data";

    private final int BUFFER_SIZE = 1024;

    private Set<Scope> appScopes = new HashSet<Scope>();

    private GoogleSignInClient mGoogleSignInClient;
    private DriveClient mDriveClient;
    private DriveResourceClient mDriveResourceClient;
    private TaskCompletionSource<DriveId> mOpenItemTaskSource;
    DriveFile file;
    private boolean appFolder, listOfFiles;
    private GoogleApiClient mGoogleApiClient;
    private CallbackContext callback;
    private String mAction;
    private JSONArray mArgs;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.cordova = cordova;
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(cordova.getActivity())
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addScope(Drive.SCOPE_APPFOLDER)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        Log.i(TAG,"Plugin initialized. Cordova has activity: " + cordova.getActivity());
    }

    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext)
            throws JSONException {
        super.execute(action, args, callbackContext);
        Log.i(TAG, EXECUTING);
        callback = callbackContext;
        mAction = action;
        mArgs = args;
        // return true;//
        return selectActionToExecute();
    }

    private boolean selectActionToExecute() {

        if (SIGN_IN_ACTION.equals(mAction)) {
            Log.i(TAG, "executing: " + mAction);
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        signIn();
                    } catch (Exception ex) {
                        callback.error("Error: " + ex.getLocalizedMessage());
                    }

                }
            });
            return true;
        } else if (SILENT_SIGN_IN_ACTION.equals(mAction)) {
            Log.i(TAG, "executing: " + mAction);
            if (silentSignIn(true)) {
                Log.i(TAG, "Silent Sign In Success");
            } else {
                Log.i(TAG, "Silent Sign In Failed");
                 callback.error("Silent Sign In Failed");
            }
            return true;
        } else if (SIGN_OUT_ACTION.equals(mAction)) {
            Log.i(TAG, "executing: " + mAction);
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        signOut();
                    } catch (Exception ex) {
                         callback.error("Error: " + ex.getLocalizedMessage());
                    }

                }
            });
            return true;
        } else if (PICK_FOLDER_ACTION.equals(mAction)) {
            Log.i(TAG, "executing: " + mAction);
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        pickFolder();
                    } catch (Exception ex) {
                        Log.i(TAG, "exception");
                         callback.error("Error: " + ex.getLocalizedMessage());
                    }

                }
            });
            return true;

        } else if (UPLOAD_FILE_WITH_FILE_PICKER_ACTION.equals(mAction)) {
            Log.i(TAG, "executing: " + mAction);
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {

                    try {
                        String driveFolderIdStr = mArgs.getString(0);
                        Log.i(TAG, driveFolderIdStr);
                        selectImage();
                    } catch (Exception e) {
                        Log.e(TAG, "Error: ", e);
                         callback.error("Error " + e.getLocalizedMessage());
                    }
                }
            });
            return true;

        } else if (UPLOAD_FILE_ACTION.equals(mAction)) {
            Log.i(TAG, "executing: " + mAction);
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {

                    try {
                        if (! silentSignIn(false)) {
                            throw new Exception("Error In Login");
                        }
                        final String driveFolderIdStr = mArgs.getString(0);
                        final JSONArray fileDetails = mArgs.getJSONArray(1);
                        final Boolean isAppFolder = mArgs.getBoolean(2);

                        Log.i(TAG, driveFolderIdStr);
                        Log.i(TAG, fileDetails.toString());
                        Log.i(TAG, driveFolderIdStr);

                        JSONArray filesId = uploadFiles(driveFolderIdStr, fileDetails, isAppFolder);
                         callback.success(filesId);

                    } catch (Exception e) {
                        Log.e(TAG, "Error: ", e);
                         callback.error("Error " + e.getLocalizedMessage());
                    }
                }
            });

            return true;
        }

        else if (QUERY_FILES_ACTION.equals(mAction))
        {
			cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
					Log.i(TAG, "executing: " + mAction);
                    try {
                        appFolder = args.getBoolean(0);
                        if (mGoogleApiClient.isConnected()) {
                            fileList(appFolder);
                        } else {
                            mGoogleApiClient.connect();
                        }
                    }catch(JSONException ex){
                        callback.error("Error " + ex.getLocalizedMessage());
                    }

                }
            });
            return true;
        }

        else if (DOWNLOAD_FILES_ACTION.equals(mAction))
        {
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, "executing: " + mAction);
                    try {
                        JSONArray elements = mArgs;
                        Log.i(TAG, " before download: " + elements.toString());
                        downloadDriveFiles(elements);
                        Log.i(TAG, " after download: " + elements.toString());
                         callback.success(elements);

                    } catch (Exception e) {
                        e.printStackTrace();
                         callback.error("Error " + e.getLocalizedMessage());
                    }
                }
            });

            return true;
        }

        return false;
    }

    private boolean silentSignIn(boolean toSendBack) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(cordova.getActivity());

   GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail()
                .requestScopes(Drive.SCOPE_FILE, Drive.SCOPE_APPFOLDER).requestProfile().requestIdToken("496146475990-vugtthf7j59cb98n55nvfe61g00ovfut.apps.googleusercontent.com").build();

        if (account != null && account.getGrantedScopes().containsAll(this.appScopes)) {

            mGoogleSignInClient = GoogleSignIn.getClient(cordova.getActivity(), gso);
            initializeDriveClient(account, SUCCESS_SILENT_SIGN_IN, toSendBack);
            return true;
        }
        return false;
    }

    private boolean silentSignIn(GoogleSignInAccount account, GoogleSignInOptions gso) {
        if (account != null && account.getGrantedScopes().containsAll(this.appScopes)) {

            mGoogleSignInClient = GoogleSignIn.getClient(cordova.getActivity(), gso);
            initializeDriveClient(account, SUCCESS_SILENT_SIGN_IN, true);
            return true;
        }
        return false;
    }
 private void fileList(final boolean appFolder) {
        /* Allowed MIME types: https://developers.google.com/drive/v3/web/mime-types */
        Query.Builder qb = new Query.Builder();
        qb.addFilter(Filters.and(
          Filters.and(Filters.eq(SearchableField.TRASHED, false)),
          Filters.or(
            Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.folder"),
            Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.photo"),
            Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.video"),
            Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.audio"),
            Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.file"),
            Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.unknown")
            )
          )
        );

        if(appFolder) {
            DriveId appFolderId = Drive.DriveApi.getAppFolder(mGoogleApiClient).getDriveId();
            qb.addFilter(Filters.in(SearchableField.PARENTS, appFolderId));
        }

        Query query = qb.build();

        Drive.DriveApi.query(mGoogleApiClient, query)
                .setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {
                    @Override
                    public void onResult(DriveApi.MetadataBufferResult result) {
                        if (!result.getStatus().isSuccess()) {
                            mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR,"failed to retrieve file list"));
                            return;
                        }
                        MetadataBuffer flist = result.getMetadataBuffer();
                        JSONArray response = new JSONArray();
                        for (Metadata file: flist) {
                            try {
                                response.put(new JSONObject().put("name", file.getTitle()).put("modifiedTime", file.getCreatedDate().toString()).put("id", file.getDriveId()));
                            }catch (JSONException ex){}
                        }
                        JSONObject flistJSON = new JSONObject();
                        try{
                            flistJSON.put("flist", response);
                        } catch (JSONException ex){}
                        mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK,flistJSON));
                        flist.release();
                    }
                });
    }
    private void signIn() {
        Log.i(TAG, SIGN_IN_PROGRESS);
        //GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(cordova.getActivity());

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail()
                .requestScopes(Drive.SCOPE_FILE, Drive.SCOPE_APPFOLDER).requestProfile().requestIdToken("496146475990-vugtthf7j59cb98n55nvfe61g00ovfut.apps.googleusercontent.com").build();

        //if (! silentSignIn(account, gso)) {
        mGoogleSignInClient = GoogleSignIn.getClient(cordova.getActivity(), gso);
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();

        cordova.setActivityResultCallback(this);
        cordova.getActivity().startActivityForResult(signInIntent, CODE_AUTH);
        // }
    }

    private void initializeDriveClient(GoogleSignInAccount signInAccount, String type, boolean toSendBack) {
        mDriveClient = Drive.getDriveClient(cordova.getActivity(), signInAccount);
        mDriveResourceClient = Drive.getDriveResourceClient(cordova.getActivity(), signInAccount);
        Log.i(TAG, type + " With Email: " + signInAccount.getEmail());
        if (toSendBack) {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("email", signInAccount.getEmail());
                Log.i(TAG, " GDrive initialised With Email:"+ signInAccount.getEmail() );
                 callback.success(jsonObject);
            } catch (Exception ex) {
                Log.e(TAG, "Error: ", ex);
                 Log.i(TAG, " GDrive initialised With Error: ",ex );
                 callback.error("Error: " + ex.getLocalizedMessage());
            }
        }

    }

    private void googleSignInAccountTask(Task<GoogleSignInAccount> task) {
        task.addOnSuccessListener(new OnSuccessListener<GoogleSignInAccount>() {
            @Override
            public void onSuccess(GoogleSignInAccount googleSignInAccount) {
                initializeDriveClient(googleSignInAccount, SUCCESS_NORMAL_SIGN_IN, true);
                Log.i(TAG,  " initializeDriveClient " );
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.i(TAG, FAILED_SIGN_IN, e);
            }
        });
    }

    private void signOut() throws Exception {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(cordova.getActivity());
        if (account != null) {
            String accountEmail = account.getEmail();
            mGoogleSignInClient.signOut();
            Log.i(TAG, SIGN_OUT + " " + accountEmail);
            JSONObject jsonObject = new JSONObject();

            try {
                jsonObject.put("email", accountEmail);
                 callback.success(jsonObject);
            } catch (JSONException e) {
                 callback.error("Error: " + e.getLocalizedMessage());
            }

        }
    }

    private void selectImage() throws Exception {
        if (! silentSignIn(false)) {
            throw new Exception("Error In Login");
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        cordova.setActivityResultCallback(GoogleDrive.this);
        cordova.getActivity().startActivityForResult(intent, CODE_UPLOAD_FILE);

    }

    private JSONArray uploadFiles(final String folderId, final JSONArray fileDetails, final Boolean isAppFolder) throws Exception {
        JSONArray filesId = new JSONArray();

        final DriveFolder driveFolder;
        if (isAppFolder || folderId == null || folderId.equals("")) {
            Task<DriveFolder> folderTask = mDriveResourceClient.getAppFolder();
            driveFolder = Tasks.await(folderTask);

        } else {
            driveFolder = getDriveFolder(folderId);
        }

        Log.i(TAG, driveFolder.toString());

        for (int i = 0; i < fileDetails.length(); i++) {
            final JSONObject jsonObject = fileDetails.getJSONObject(i);
            final String fileTitle = jsonObject.getString("title");
            final String fileDescription = jsonObject.getString("description");
            final String fileUriStr = jsonObject.getString("uri");
            Uri uriFile = Uri.parse(fileUriStr);
            String fileId = uploadFile(driveFolder, uriFile, fileTitle, fileDescription, isAppFolder);
            filesId.put(fileId);
        }
        return filesId;
    }

    private String uploadFile(final DriveFolder driveFolder, final Uri filePath, final String fileName,
                              final String description, final boolean isAppFolder) throws Exception {
        // try {
        Log.i(TAG, "enter uploadFile");
            /*final Task<DriveContents> createContentsTask = mDriveResourceClient.createContents();
            final DriveFolder driveFolder;
            if (isAppFolder) {
                Task<DriveFolder> folderTask = mDriveResourceClient.getAppFolder();
                driveFolder = Tasks.await(folderTask);
            } else {
                driveFolder = getDriveFolder(folderId);
            }*/

        //Log.i(TAG, driveFolder.toString());


        //DriveContents contents = Tasks.await(createContentsTask);//maybe per item
        //for everyone till here

        // try {
        final Task<DriveContents> createContentsTask = mDriveResourceClient.createContents();
        DriveContents contents = Tasks.await(createContentsTask);
        OutputStream outputStream = contents.getOutputStream();
        InputStream inputStream = cordova.getActivity().getContentResolver().openInputStream(filePath);
        Log.i(TAG, ENTER_STREAM);
        byte[] data = new byte[BUFFER_SIZE];

        while (inputStream.read(data) != - 1) {
            outputStream.write(data);
        }

        inputStream.close();
        outputStream.close();
        Log.i(TAG, EXIT_STREAM);

        MetadataChangeSet changeSet = new MetadataChangeSet.Builder().setTitle(fileName)
                .setMimeType(FILE_MIME).setDescription(description).build();
        Log.i(TAG, FILE_CREATED + " With !!!!!: " + driveFolder.toString());
        DriveFile driveFile = Tasks.await(mDriveResourceClient.createFile(driveFolder, changeSet, contents));
        Log.i(TAG, FILE_CREATED + " With2 !!!!!: " + driveFolder.toString());
        Log.i(TAG, FILE_CREATED + " With FileId: " + driveFile.getDriveId().toInvariantString());
        return driveFile.getDriveId().encodeToString();


        //mCallbackContext.success("Success File Uploaded");

                /*} catch (Exception ex) {
                Log.i(TAG, "Error:", ex);
                mCallbackContext.error(ex.getLocalizedMessage());
                //return null;
            }*/



            /*Tasks.whenAll(createContentsTask).continueWithTask(new Continuation<Void, Task<DriveFile>>() {
                @Override
                public Task<DriveFile> then(@NonNull Task<Void> task) throws Exception {
                    DriveFolder parent = driveFolder;
                    DriveContents contents = createContentsTask.getResult();
                    try {
                        OutputStream outputStream = contents.getOutputStream();
                        InputStream inputStream = cordova.getActivity().getContentResolver().openInputStream(filePath);
                        Log.i(TAG, ENTER_STREAM);
                        byte[] data = new byte[BUFFER_SIZE];
                        while (inputStream.read(data) != - 1) {
                            outputStream.write(data);
                        }
                        inputStream.close();
                        outputStream.close();
                        Log.i(TAG, EXIT_STREAM);
                        MetadataChangeSet changeSet = new MetadataChangeSet.Builder().setTitle(fileName)
                                .setMimeType(FILE_MIME).setDescription(description).build();
                        return mDriveResourceClient.createFile(parent, changeSet, contents);
                    } catch (Exception ex) {
                        Log.i(TAG, "Error:", ex);
                        return null;
                    }
                }
            }).addOnSuccessListener(cordova.getActivity(), new OnSuccessListener<DriveFile>() {
                @Override
                public void onSuccess(DriveFile driveFile) {
                    Log.i(TAG, FILE_CREATED + " With FileId: " + driveFile.getDriveId().toInvariantString());
                    mCallbackContext.success("Success File Uploaded");
                }
            }).addOnFailureListener(cordova.getActivity(), new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.e(TAG, ERROR_FILE, e);
                    mCallbackContext.error("Failed File Uploaded " + e.getLocalizedMessage());
                }
            });*/
       /* } catch (Exception ex) {
            Log.e(TAG, "Error", ex);
            mCallbackContext.error(ex.getLocalizedMessage());
        }*/
    }

    private DriveFolder getDriveFolder(String folderIdStr) {
        return DriveId.decodeFromString(folderIdStr).asDriveFolder();
    }

    private Task<DriveId> pickFolder() throws Exception {
        Log.i(TAG, "enter pick folder");
        if (! silentSignIn(false)) {
            throw new Exception("Error In Login");
        }
        OpenFileActivityOptions openOptions = new OpenFileActivityOptions.Builder()
                .setSelectionFilter(Filters.eq(SearchableField.MIME_TYPE, DriveFolder.MIME_TYPE))
                .setActivityTitle("pick Folder").build();
        return pickItem(openOptions);
    }

    private Task<DriveId> pickItem(OpenFileActivityOptions openOptions) {

        mOpenItemTaskSource = new TaskCompletionSource<DriveId>();
        mDriveClient.newOpenFileActivityIntentSender(openOptions)
                .continueWith(new Continuation<IntentSender, Object>() {
                    @Override
                    public Object then(@NonNull Task<IntentSender> task) throws Exception {
                        Log.i(TAG, "enter continueWith");
                        cordova.setActivityResultCallback(GoogleDrive.this);
                        cordova.getActivity().startIntentSenderForResult(task.getResult(), CODE_FOLDER_PICKER, null, 0,
                                0, 0);
                        return null;
                    }
                });
        Log.i(TAG, "exit pickItem");
        return mOpenItemTaskSource.getTask();
    }

    private JSONArray queryAllAppFiles() throws Exception {
        Query query = new Query.Builder().addFilter(
											Filters.and(
											Filters.and(Filters.eq(SearchableField.TRASHED, false)),
											Filters.or(
												Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.folder"),
												Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.photo"),
												Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.video"),
												Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.audio"),
												Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.file"),
												Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.unknown")
												)
											)
										).build();
       // Query query = new Query.Builder().addFilter(Filters.ownedByMe()).build();
                Log.i(TAG, "Enetering  GDrive view list"+query);
        Task<MetadataBuffer> queryTask = mDriveResourceClient.query(query);
        MetadataBuffer metadataBuffer = Tasks.await(queryTask);
        JSONArray elements = new JSONArray();
        Log.i(TAG, "finish query metadatabuffer");
        Log.i(TAG, "MetadataBuffer"+metadataBuffer);
        for (Metadata metadata : metadataBuffer) {
            Log.i(TAG, "MetaGET"+ metadata);
            Log.i(TAG, "MetaGETDESC"+ metadata.getDescription());
            Log.i(TAG, "MetaGETisFolder()"+ metadata.isFolder());
            if ( metadata.isFolder()) {

                //DriveFile driveFile = .asDriveFile();
                JSONObject object = new JSONObject();
               //String driveFilenameStr = metadata.getTitle().encodeToString();
                String driveFileIdStr = metadata.getDriveId().encodeToString();
                Log.i(TAG, driveFileIdStr+" !!!!!!!!!!!!!!!!!!!!!driveFileIdStr encoded string");
                String description = metadata.getDescription();
		Log.i(TAG, description+" !!!!!!!!!!!!!!!!!!!!!description encoded string");    
		object.put(DRIVE_ID_DIC_KEY, driveFileIdStr);
                object.put(DESCRIPTION_DRIVE_DIC_KEY, description);
                elements.put(object);
            }
        }
        return elements;
    }

    private void downloadDriveFiles(JSONArray elements) throws Exception {
        for (int i = 0; i < elements.length(); i++)
        {
            JSONObject object = elements.getJSONObject(i);
            String driveId = object.getString("driveId");
            String title = object.getString("title");
            if(driveId != null && !driveId.equals("") && title != null && !title.equals("")) {
                String uri = downloadDriveFile(driveId, title);
                object.put("uri",uri);
            }
        }
    }

    private String downloadDriveFile(String driveFileId, String title) throws Exception {
        DriveFile driveFile = DriveId.decodeFromString(driveFileId).asDriveFile();
        Log.i(TAG, driveFileId+" !!!!!!!!!!!!!!!!!!!!!here");
        Task<DriveContents> task = mDriveResourceClient.openFile(driveFile, DriveFile.MODE_READ_ONLY);
        DriveContents driveContents = Tasks.await(task);
        InputStream inputStream = driveContents.getInputStream();
        FileOutputStream outputStream;

        File file = new File(cordova.getActivity().getFilesDir(), title);

        outputStream = new FileOutputStream(file, false);


        byte[] data = new byte[BUFFER_SIZE];

        while (inputStream.read(data) != - 1) {
            outputStream.write(data);
        }

        Log.i(TAG, " Stream: " + file.toURI());
        inputStream.close();
        outputStream.close();
        return file.toURI().toString();

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        try {
            switch (requestCode) {

                case CODE_AUTH: {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(intent);
                    googleSignInAccountTask(task);
                    break;
                }
		case REQUEST_CODE_RESOLUTION: {
                                mGoogleApiClient.connect();
                    break;
                }
                case CODE_UPLOAD_FILE: {
                    final String driveFolderIdStr = mArgs.getString(0);
                    //Task<DriveFolder> task =mDriveResourceClient.getRootFolder();
                    //final String driveFolderIdStr = Tasks.await(task).
                    final String fileTitle = mArgs.getString(1);
                    final String fileDescription = mArgs.getString(2);

                    final DriveFolder driveFolder;
                    driveFolder = getDriveFolder(driveFolderIdStr);

                    if (intent.getData() != null) {
                        Uri fileUri = intent.getData();
                        uploadFile(driveFolder, fileUri, fileTitle, fileDescription, false);

                    } else if (intent.getClipData() != null) {

                        ClipData clipData = intent.getClipData();
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            Uri fileUri = clipData.getItemAt(i).getUri();
                            uploadFile(driveFolder, fileUri, fileTitle, fileDescription, false);
                        }
                    }
                }

                break;

                case CODE_FOLDER_PICKER: {
                    if (resultCode == RESULT_OK) {
                        DriveId driveId = intent.getParcelableExtra(OpenFileActivityOptions.EXTRA_RESPONSE_DRIVE_ID);
                        Log.i(TAG, driveId.toString());

                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("id", driveId.toString());
                         callback.success(jsonObject);

                        mOpenItemTaskSource.setResult(driveId);
                    } else {
                        mOpenItemTaskSource.setException(new RuntimeException("Unable to open file"));
                    }
                    break;
                }
            }
        } catch (Exception ex)

        {
             callback.error("error " + ex.getLocalizedMessage());
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Called whenever the API client fails to connect.
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
        if (!result.hasResolution()) {
            // show the localized error dialog.
            GoogleApiAvailability.getInstance().getErrorDialog(cordova.getActivity(), result.getErrorCode(), 0).show();
            return;
        }
        try {
            Log.i(TAG,"trying to resolve issue...");
            cordova.setActivityResultCallback(this);//
            result.startResolutionForResult(cordova.getActivity(), REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        if (QUERY_FILES_ACTION.equals(mAction)) {
              fileList(appFolder);
        } 
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "GoogleApiClient connection suspended");
    }
}


