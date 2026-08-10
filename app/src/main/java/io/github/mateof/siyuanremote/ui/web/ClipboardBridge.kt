// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.ui.web

import android.content.ClipboardManager
import android.content.Context
import android.webkit.JavascriptInterface
import io.github.mateof.siyuanremote.util.isSameOrigin
import androidx.core.net.toUri

/**
 * Gives the page a working `navigator.clipboard.read`.
 *
 * The real one needs the `clipboard-read` permission, and a WebView has no way
 * to grant it: `WebChromeClient.onPermissionRequest` only covers audio, video,
 * MIDI and protected media, and there is no prompt. So the promise rejects, and
 * SiYuan answers with "allow clipboard access in the browser address bar",
 * which is advice that means nothing inside an app. The result is that pasting
 * with formatting is impossible, since the only path that carries `text/html`
 * from a programmatic read is that API.
 *
 * Rather than reimplement SiYuan's paste, this supplies the API it already
 * calls, backed by Android's own clipboard, where `getHtmlText()` is the
 * formatted copy other apps left behind.
 *
 * ## Why the origin check
 *
 * `addJavascriptInterface` is not scoped to an origin: every page in this
 * WebView would see the bridge. The WebView only ever loads the configured
 * server, since outbound links leave for a custom tab, but a redirect could
 * still land somewhere else. So the bridge refuses to answer unless the page
 * it is serving belongs to that server.
 */
class ClipboardBridge(
    private val context: Context,
    private val baseUrl: () -> String,
) {
    /** Updated from the WebView callbacks, which run on the UI thread. */
    @Volatile
    private var currentUrl: String = ""

    fun onPageUrlChanged(url: String) {
        currentUrl = url
    }

    @JavascriptInterface
    fun readText(): String = ifSameOrigin {
        clip()?.coerceToText(context)?.toString().orEmpty()
    }

    @JavascriptInterface
    fun readHtml(): String = ifSameOrigin {
        clip()?.htmlText.orEmpty()
    }

    private fun clip() = runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
    }.getOrNull()

    private inline fun ifSameOrigin(block: () -> String): String {
        val base = baseUrl()
        val page = currentUrl
        if (base.isEmpty() || page.isEmpty()) return ""
        if (!isSameOrigin(page.toUri(), base)) return ""
        return runCatching(block).getOrDefault("")
    }

    companion object {
        const val NAME = "SiyuanRemoteClipboard"

        /**
         * Replaces only the two read methods, keeping whatever else the real
         * object provides. `write` and `writeText` already work in a WebView,
         * because writing needs a user gesture rather than a permission.
         *
         * Shaped to match what the page expects from `read()`: a list of items
         * exposing `types` and `getType(mime)` returning a Blob.
         */
        val SHIM = """
            (function () {
              if (!window.$NAME) { return; }
              var bridge = window.$NAME;
              var original = navigator.clipboard || {};

              function itemsFrom(text, html) {
                var types = [];
                var blobs = {};
                if (html) {
                  types.push('text/html');
                  blobs['text/html'] = new Blob([html], { type: 'text/html' });
                }
                types.push('text/plain');
                blobs['text/plain'] = new Blob([text || ''], { type: 'text/plain' });
                return [{
                  types: types,
                  getType: function (type) {
                    return blobs[type]
                      ? Promise.resolve(blobs[type])
                      : Promise.reject(new Error('Unsupported type: ' + type));
                  }
                }];
              }

              var shim = {
                read: function () {
                  return Promise.resolve(itemsFrom(bridge.readText(), bridge.readHtml()));
                },
                readText: function () {
                  return Promise.resolve(bridge.readText());
                }
              };

              // Bound rather than copied: these live on the prototype, so an
              // Object.assign would silently drop them and break copying.
              if (original.write) { shim.write = original.write.bind(original); }
              if (original.writeText) { shim.writeText = original.writeText.bind(original); }

              try {
                Object.defineProperty(navigator, 'clipboard', {
                  configurable: true,
                  enumerable: true,
                  get: function () { return shim; }
                });
              } catch (e) {
                // Nothing to do: the page keeps the original, which is what it
                // had before this existed.
              }
            })();
        """.trimIndent()
    }
}
