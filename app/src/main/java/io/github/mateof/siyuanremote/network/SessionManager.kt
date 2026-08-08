package io.github.mateof.siyuanremote.network

import android.util.Log
import io.github.mateof.siyuanremote.data.SecretStore
import io.github.mateof.siyuanremote.data.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns "which server are we talking to" and "are we logged in", which are the
 * two questions every entry point of the app needs answered before it can do
 * anything useful.
 *
 * ## Why the session never expires
 *
 * The kernel issues a session cookie that lasts at most 30 days
 * (`kernel/model/session.go`, `maxAge = 60 * 60 * 24 * 30` when `rememberMe` is
 * set). We cannot make the server issue a longer one, so instead the app keeps
 * the access authorization code encrypted on device and silently replays the
 * login whenever the cookie is gone. From the user's point of view the session
 * is indefinite: it only ends when they sign out from Settings, which is the
 * one action that erases the stored code.
 */
@Singleton
class SessionManager @Inject constructor(
    private val api: SiyuanApi,
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    private val cookieJar: WebViewCookieJar,
) {
    private val mutex = Mutex()

    @Volatile
    var activeBaseUrl: String? = null
        private set

    /**
     * Resolves a reachable base URL and guarantees a valid session on it,
     * logging in again in the background if the cookie has expired.
     */
    suspend fun prepare(): SessionResult = mutex.withLock {
        val current = settings.current()
        if (!current.isConfigured) return@withLock SessionResult.NotConfigured

        val baseUrl = resolveBaseUrl(current.candidateUrls)
            ?: return@withLock SessionResult.Unreachable(current.candidateUrls)

        activeBaseUrl = baseUrl
        if (baseUrl != current.lastGoodUrl) settings.setLastGoodUrl(baseUrl)

        return@withLock authenticate(baseUrl)
    }

    /**
     * First-time (or re-entered) sign in. On success the code is persisted so
     * every later session can renew itself without asking again.
     */
    suspend fun signIn(rawBaseUrl: String, authCode: String, captcha: String? = null): SessionResult =
        mutex.withLock {
            val baseUrl = rawBaseUrl.trimEnd('/')
            if (api.version(baseUrl) == null) return@withLock SessionResult.Unreachable(listOf(baseUrl))

            val response = api.loginAuth(baseUrl, authCode, captcha)
            return@withLock when {
                response.ok -> {
                    secrets.writeAuthCode(authCode)
                    activeBaseUrl = baseUrl
                    settings.setLastGoodUrl(baseUrl)
                    SessionResult.Ready(baseUrl)
                }

                response.code == CODE_CAPTCHA_REQUIRED ->
                    SessionResult.AuthRequired(baseUrl, AuthFailure.CAPTCHA_REQUIRED, response.msg)

                else -> SessionResult.AuthRequired(baseUrl, AuthFailure.WRONG_CODE, response.msg)
            }
        }

    /** Ends the session for good: server side, cookie store and stored code. */
    suspend fun signOut() = mutex.withLock {
        activeBaseUrl?.let { runCatching { api.logoutAuth(it) } }
        cookieJar.clear()
        secrets.clear()
        settings.clearServer()
        activeBaseUrl = null
    }

    /**
     * Called when the WebView lands on `/check-auth`, which is the kernel
     * telling us the cookie is gone. Returns true when the silent re-login
     * worked and the caller can just reload the page it wanted.
     */
    suspend fun renewSession(baseUrl: String): Boolean = mutex.withLock {
        val code = secrets.readAuthCode() ?: return@withLock false
        val response = api.loginAuth(baseUrl, code)
        if (!response.ok) {
            Log.w(TAG, "Silent re-login rejected: code=${response.code} msg=${response.msg}")
        }
        response.ok
    }

    private suspend fun authenticate(baseUrl: String): SessionResult {
        val conf = api.getConf(baseUrl)
        if (conf.ok) return SessionResult.Ready(baseUrl)

        if (!conf.unauthorized) {
            Log.w(TAG, "Unexpected answer from $baseUrl: http=${conf.httpCode} msg=${conf.msg}")
            return SessionResult.Unreachable(listOf(baseUrl))
        }

        if (conf.msg.contains(LOCK_SCREEN_HINT, ignoreCase = true)) {
            return SessionResult.AuthRequired(baseUrl, AuthFailure.SERVER_HAS_NO_AUTH_CODE, conf.msg)
        }

        val code = secrets.readAuthCode()
            ?: return SessionResult.AuthRequired(baseUrl, AuthFailure.NO_CODE_STORED, conf.msg)

        val login = api.loginAuth(baseUrl, code)
        return when {
            login.ok -> SessionResult.Ready(baseUrl)
            login.code == CODE_CAPTCHA_REQUIRED ->
                SessionResult.AuthRequired(baseUrl, AuthFailure.CAPTCHA_REQUIRED, login.msg)

            else -> SessionResult.AuthRequired(baseUrl, AuthFailure.WRONG_CODE, login.msg)
        }
    }

    /** Probes candidates in order and returns the first kernel that answers. */
    private suspend fun resolveBaseUrl(candidates: List<String>): String? =
        candidates.firstOrNull { api.version(it) != null }

    private companion object {
        const val TAG = "SessionManager"
        const val CODE_CAPTCHA_REQUIRED = 1

        // The kernel refuses non-local access when no access authorization code
        // is configured. Recognising it lets us show an actionable message
        // instead of a generic "auth failed".
        const val LOCK_SCREEN_HINT = "Lock screen password"
    }
}

sealed interface SessionResult {
    data class Ready(val baseUrl: String) : SessionResult
    data object NotConfigured : SessionResult
    data class Unreachable(val triedUrls: List<String>) : SessionResult
    data class AuthRequired(
        val baseUrl: String,
        val failure: AuthFailure,
        val serverMessage: String = "",
    ) : SessionResult
}

enum class AuthFailure {
    /** Nothing stored yet: normal on first run. */
    NO_CODE_STORED,

    /** The stored code no longer matches the one configured on the server. */
    WRONG_CODE,

    /** Too many failed attempts: the kernel now demands a captcha. */
    CAPTCHA_REQUIRED,

    /** The server has no access authorization code, so it rejects remote clients. */
    SERVER_HAS_NO_AUTH_CODE,
}
