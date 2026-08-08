package io.github.mateof.siyuanremote.ui.web

import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import io.github.mateof.siyuanremote.BuildConfig

/**
 * Supplies the browser-level capabilities the SiYuan frontend expects: file
 * pickers for asset upload, microphone access for audio blocks, and progress.
 */
class SiyuanWebChromeClient(
    private val onFileChooser: (ValueCallback<Array<Uri>>, FileChooserParams) -> Boolean,
    private val onMediaPermission: (PermissionRequest) -> Unit,
    private val onProgress: (Int) -> Unit,
) : WebChromeClient() {

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = onFileChooser(filePathCallback, fileChooserParams)

    override fun onPermissionRequest(request: PermissionRequest) {
        onMediaPermission(request)
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        // The app never needs location; denying without a prompt keeps it that way.
        callback.invoke(origin, false, false)
    }

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        onProgress(newProgress)
    }

    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "${message.sourceId()}:${message.lineNumber()} ${message.message()}")
        }
        return true
    }

    private companion object {
        const val TAG = "SiyuanWebView"
    }
}
