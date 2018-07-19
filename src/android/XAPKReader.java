package com.flyingsoftgames.xapkreader;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;

public class XAPKReader extends CordovaPlugin {
    public static final String ACTION_DOWNLOAD_IF_AVAIlABLE = "downloadExpansionIfAvailable";

    private CordovaInterface cordova;
    private CordovaWebView webView;
    private Bundle bundle;

    // Request code used when we do runtime permissions requests during initialization.
    public static final int STARTUP_REQ_CODE = 0;

    @Override
    public void initialize(final CordovaInterface cordova, CordovaWebView webView) {

        this.cordova = cordova;
        this.webView = webView;
        this.bundle = new Bundle();

        String packageName = cordova.getActivity().getPackageName();


        // Get some data from the xapkreader.xml file.
        String[][] xmlData = new String[][]{
                {"xapk_main_version", "integer"},
                {"xapk_patch_version", "integer"},
                {"xapk_main_file_size", "string", "long"},
                {"xapk_patch_file_size", "string", "long"},
                {"xapk_expansion_authority", "string"},
                {"xapk_text_downloading_assets", "string"},
                {"xapk_text_preparing_assets", "string"},
                {"xapk_text_download_failed", "string"},
                {"xapk_text_error", "string"},
                {"xapk_text_close", "string"},
                {"xapk_google_play_public_key", "string"},
                {"xapk_auto_download", "bool"},
                {"xapk_auto_permission", "bool"},
                {"xapk_auto_reload", "bool"},
                {"xapk_progress_format", "string"},
                {"xapk_url_patterns", "string"},
                {"xapk_permission_storage_rationale","string"}
        };
        int curlen = xmlData.length;
        for (int i = 0; i < curlen; i++) {
            int currentId = cordova.getActivity().getResources().getIdentifier(xmlData[i][0], xmlData[i][1], packageName);
            if (xmlData[i][1] == "bool") {
                bundle.putBoolean(xmlData[i][0], cordova.getActivity().getResources().getBoolean(currentId));
                continue;
            }
            if (xmlData[i][1] == "string") {
                if ((xmlData[i].length == 2)) {
                    bundle.putString(xmlData[i][0], cordova.getActivity().getResources().getString(currentId));
                    continue;
                }
                if (xmlData[i][2] == "long") {
                    long val = Long.parseLong(cordova.getActivity().getResources().getString(currentId));
                    bundle.putLong(xmlData[i][0], val);
                    continue;
                }
            }
            if (xmlData[i][1] == "integer") {
                bundle.putInt(xmlData[i][0], cordova.getActivity().getResources().getInteger(currentId));
                continue;
            }
        }
        requestStoragePermission();

        // Send data to the ContentProvider instance.
        ContentResolver cr = cordova.getActivity().getApplicationContext().getContentResolver();
        String expansionAuthority = bundle.getString("xapk_expansion_authority", "");
        cr.call(Uri.parse("content://" + expansionAuthority), "set_expansion_file_version_data", null, bundle);

        // Set the public key.
        XAPKDownloaderService.BASE64_PUBLIC_KEY = bundle.getString("xapk_google_play_public_key", "");

        // Workaround for Android 6 bug wherein downloaded OBB files have the wrong file permissions
        // and require WRITE_EXTERNAL_STORAGE permission
        boolean autoPermission = bundle.getBoolean("xapk_auto_permission", true);
        if (
                autoPermission
                && Build.VERSION.SDK_INT >= 23
                && !cordova.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        ) {
            // We need the permission; so request it (asynchronously, callback is onRequestPermissionsResult)
            cordova.requestPermission(this, STARTUP_REQ_CODE, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } else {
            // We don't need the permission, or we already have it.
            this.autodownloadIfNecessary();
        }


        super.initialize(cordova, webView);
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        for (int r:grantResults) {
            // They granted us WRITE_EXTERNAL_STORAGE (and thus, implicitly, READ_EXTERNAL_STORAGE) permission
            if (requestCode == STARTUP_REQ_CODE && r == PackageManager.PERMISSION_GRANTED) {
                this.autodownloadIfNecessary();
            }
        }
    }

    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext callContext) {
        try {
            PluginResult result = null;
            boolean success = false;

            if (XAPKReader.ACTION_DOWNLOAD_IF_AVAIlABLE.equals(action)) {
                downloadExpansionIfAvailable();
                result = new PluginResult(PluginResult.Status.OK);
                success = true;
            } else {
                result = new PluginResult(PluginResult.Status.ERROR, "no such action: " + action);
            }

            callContext.sendPluginResult(result);
            return success;

        } catch (Exception ex) {
            String message = ex.getMessage();
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, action + ": exception thown, " + message);
            result.setKeepCallback(false);

            callContext.sendPluginResult(result);
            return true;
        }
    }

    private void autodownloadIfNecessary() {
        boolean autoDownload = bundle.getBoolean("xapk_auto_download", true);
        if (autoDownload) {
            downloadExpansionIfAvailable();
        }
    }

    private void downloadExpansionIfAvailable() {
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                XAPKDownloaderActivity.cordovaActivity = cordova.getActivity(); // Workaround for Cordova/Crosswalk flickering status bar bug.
                // Provide webview to Downloader Activity so it can trigger a page
                // reload once the expansion is downloaded.
                XAPKDownloaderActivity.cordovaWebView = webView;
                Context context = cordova.getActivity().getApplicationContext();
                Intent intent = new Intent(context, XAPKDownloaderActivity.class);
                intent.putExtras(bundle);
                cordova.getActivity().startActivity(intent);
            }
        });
    }

    public Uri remapUri(Uri uri){
        String url =  uri.toString();
        int intercept = "file:///android_asset/www/".length();
        if (url.length() >= intercept) {
            String filename = url.substring(intercept);

            final String AUTHORITY =  bundle.getString("xapk_expansion_authority", "");
            String CONTENT_URI = "content://" + AUTHORITY;
            String patterns =  bundle.getString("xapk_url_patterns", "");

            String paths[] = patterns.split(",");
            for (String path: paths){
                if (url.contains(path)){
                    return Uri.parse(CONTENT_URI + File.separator + filename);
                }
            }
        }
        return null;
    }

    public void requestStoragePermission()
    {
        final int READ_EXTERNAL_STORAGE = 1;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (cordova.getActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {


                if (cordova.getActivity().shouldShowRequestPermissionRationale(
                        Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    // Display UI and wait for user interaction
                    AlertDialog.Builder builder = new AlertDialog.Builder(cordova.getActivity());
// Add the buttons
                    builder.setMessage( bundle.getString("xapk_permission_storage_rationale", ""););
                    builder.setPositiveButton("ok", (dialog, id) -> {
                        // User clicked OK button
                        cordova.requestPermissions(
                                XAPKReader.this,
                                READ_EXTERNAL_STORAGE, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
                    });
                    AlertDialog dialog = builder.create();

                } else {
                    cordova.requestPermissions(
                            this,
                            READ_EXTERNAL_STORAGE, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
                }
            } else {

            }
        }

    }

}
