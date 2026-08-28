package org.apache.cordova.inappbrowser;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Message;
import android.webkit.JsPromptResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.GeolocationPermissions.Callback;
import android.webkit.PermissionRequest;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.webkit.MimeTypeMap;
import java.util.ArrayList;

public class InAppChromeClient extends WebChromeClient {

    private CordovaWebView webView;
    private String LOG_TAG = "InAppChromeClient";
    private long MAX_QUOTA = 100 * 1024 * 1024;
    
    // Hold reference to callback so file picker doesn't cancel automatically
    public ValueCallback<Uri[]> filePathCallback;

    public InAppChromeClient(CordovaWebView webView) {
        super();
        this.webView = webView;
    }
    
    public void onPermissionRequest(final PermissionRequest request) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            request.grant(request.getResources());
        }
    }

    @Override
    public void onExceededDatabaseQuota(String url, String databaseIdentifier, long currentQuota, long estimatedSize,
            long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater) {
        quotaUpdater.updateQuota(MAX_QUOTA);
    }

    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, Callback callback) {
        super.onGeolocationPermissionsShowPrompt(origin, callback);
        callback.invoke(origin, true, false);
    }

    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
        if (defaultValue != null && defaultValue.startsWith("gap")) {
            if (defaultValue.startsWith("gap-iab://")) {
                PluginResult scriptResult;
                String scriptCallbackId = defaultValue.substring(10);
                if (scriptCallbackId.matches("^InAppBrowser[0-9]{1,10}$")) {
                    if (message == null || message.length() == 0) {
                        scriptResult = new PluginResult(PluginResult.Status.OK, new JSONArray());
                    } else {
                        try {
                            scriptResult = new PluginResult(PluginResult.Status.OK, new JSONArray(message));
                        } catch(JSONException e) {
                            scriptResult = new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());
                        }
                    }
                    this.webView.sendPluginResult(scriptResult, scriptCallbackId);
                    result.confirm("");
                    return true;
                } else {
                    result.cancel();
                    return true;
                }
            } else {
                result.cancel();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        WebView inAppWebView = view;
        final WebViewClient webViewClient = new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                inAppWebView.loadUrl(request.getUrl().toString());
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                inAppWebView.loadUrl(url);
                return true;
            }
        };

        final WebView newWebView = new WebView(view.getContext());
        newWebView.setWebViewClient(webViewClient);

        final WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(newWebView);
        resultMsg.sendToTarget();

        return true;
    }
    
    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        // Cancel existing callback if left hanging
        if (this.filePathCallback != null) {
            this.filePathCallback.onReceiveValue(null);
        }
        this.filePathCallback = filePathCallback;

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        String[] acceptTypes = fileChooserParams.getAcceptTypes();
        ArrayList<String> mimeTypesList = new ArrayList<>();

        if (acceptTypes != null && acceptTypes.length > 0) {
            for (String type : acceptTypes) {
                if (type == null || type.trim().isEmpty()) continue;
                String[] splitTypes = type.split(",");
                for (String cleanedType : splitTypes) {
                    cleanedType = cleanedType.trim();
                    if (cleanedType.startsWith(".")) {
                        String extension = cleanedType.substring(1);
                        String mimeFromExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
                        if (mimeFromExt != null && !mimeTypesList.contains(mimeFromExt)) {
                            mimeTypesList.add(mimeFromExt);
                        }
                    } else if (!mimeTypesList.contains(cleanedType)) {
                        mimeTypesList.add(cleanedType);
                    }
                }
            }
        }

        if (!mimeTypesList.isEmpty()) {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypesList.toArray(new String[0]));
        } else {
            intent.setType("*/*");
        }

        if (fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        try {
            Intent chooserIntent = Intent.createChooser(intent, "Select File");
            webView.getContext().startActivity(chooserIntent);
        } catch (ActivityNotFoundException e) {
            if (this.filePathCallback != null) {
                this.filePathCallback.onReceiveValue(null);
                this.filePathCallback = null;
            }
            return false;
        }
        return true;
    }
}
