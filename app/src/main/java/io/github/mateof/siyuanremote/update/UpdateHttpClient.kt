package io.github.mateof.siyuanremote.update

import javax.inject.Qualifier

/**
 * Marks the HTTP client used to talk to GitHub.
 *
 * It has to be a different client from the one used for the SiYuan kernel: that
 * one carries a cookie jar backed by the WebView store, which is not scoped by
 * host, so reusing it would send the kernel session cookie to github.com.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpdateHttpClient
