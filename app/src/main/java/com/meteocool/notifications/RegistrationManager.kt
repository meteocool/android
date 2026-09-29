package com.meteocool.notifications

import android.content.Context
import android.os.SystemClock
import com.meteocool.environment.MeteocoolEnvironment
import com.meteocool.location.MeteocoolLocation
import com.meteocool.network.ApiClient
import com.meteocool.network.ApiClient.Endpoint
import com.meteocool.permissions.PermUtils
import com.meteocool.preferences.Prefs
import com.meteocool.sensors.PressureReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.Locale

/**
 * Keeps the backend's push registration in step with the settings, the
 * permissions and the phone's location.
 *
 * A registration is a `post_location` carrying the push token; `unregister`
 * removes it. Requests run one at a time. The API a registration went to is
 * remembered, so switching environment removes it there before registering
 * on the new one -- otherwise the old deployment goes on notifying.
 */
class RegistrationManager(
    private val context: Context,
    private val prefs: Prefs,
    private val api: ApiClient,
    private val pressure: PressureReader,
    private val scope: CoroutineScope,
    private val currentLocation: suspend () -> MeteocoolLocation?,
) {
    private val mutex = Mutex()

    private val _syncFailed = MutableStateFlow(false)

    /** A registration change did not reach the server; the settings screen says so. */
    val syncFailed: StateFlow<Boolean> = _syncFailed

    @Volatile
    private var lastFix: MeteocoolLocation? = null

    @Volatile
    private var lastPosted: MeteocoolLocation? = null

    private val currentApi: String get() = MeteocoolEnvironment.current.apiBase

    /**
     * Alerts are on, allowed, located and tokened, and nothing is still
     * registered on another deployment. Approximate location while the app is
     * open is enough; "Allow all the time" only adds updates while it is closed.
     */
    fun canRegister(): Boolean =
        prefs.notification &&
            PermUtils.areNotificationsEnabled(context) &&
            PermUtils.isLocationPermissionGranted(context) &&
            Registration.isValidToken(prefs.pushToken) &&
            prefs.registrationOrigin.let { it == null || it == currentApi }

    private fun settings(token: String) = RegistrationSettings(
        token = token,
        aheadMinutes = prefs.aheadMinutes,
        intensityDbz = prefs.intensityDbz,
        withDbz = prefs.withDbz,
        experimental = MeteocoolEnvironment.current == MeteocoolEnvironment.STAGING,
        lang = Registration.lang(Locale.getDefault().language),
    )

    private fun isFresh(fix: MeteocoolLocation) = Registration.isFresh(fix, SystemClock.elapsedRealtimeNanos())

    /** A new token from FCM, or null when there is none. */
    fun setToken(token: String?) {
        if (token == prefs.pushToken && token != null) return
        prefs.pushToken = token
        if (canRegister()) refreshRegistration() else if (prefs.registrationOrigin != null) unregister()
    }

    /** Every fix. In the foreground only one that is significant against the last posted fix is sent. */
    fun onLocation(fix: MeteocoolLocation, background: Boolean) {
        lastFix = fix
        if (!canRegister()) return
        scope.launch { submit(fix, background) }
    }

    /** Posts [fix] with a barometer reading, returning whether the backend took it. */
    suspend fun submit(fix: MeteocoolLocation, background: Boolean): Boolean {
        lastFix = fix
        if (!canRegister()) return false
        return mutex.withLock {
            if (!background && !Registration.isSignificant(fix, lastPosted)) return@withLock true
            postLocked(fix, readPressure = true)
        }
    }

    /** Re-sends the registration, e.g. after a settings change. */
    fun refreshRegistration(): Job = scope.launch { refreshRegistrationNow() }

    private suspend fun refreshRegistrationNow(): Boolean {
        if (!canRegister()) return false
        val fix = lastFix?.takeIf { isFresh(it) } ?: currentLocation()?.also { lastFix = it } ?: return false
        return mutex.withLock { postLocked(fix, readPressure = false) }
    }

    /**
     * Brings the registration in line with the permissions as they are now,
     * without asking for any. Runs at launch and whenever the app comes back.
     */
    fun refreshAuthorization(): Job = scope.launch {
        val origin = prefs.registrationOrigin
        if (origin != null && origin != currentApi) {
            if (unregisterNow() && canRegister()) refreshRegistrationNow()
        } else if (canRegister()) {
            refreshRegistrationNow()
        } else if (origin != null) {
            unregisterNow()
        }
    }

    /** The user turned alerts off. */
    fun disable() {
        prefs.notification = false
        Notifications.clearAll(context)
        unregister()
    }

    fun unregister(): Job = scope.launch { unregisterNow() }

    private suspend fun unregisterNow(): Boolean = mutex.withLock { unregisterLocked() }

    private suspend fun postLocked(fix: MeteocoolLocation, readPressure: Boolean): Boolean {
        val token = prefs.pushToken
        if (token == null || !canRegister() || !isFresh(fix)) return false
        val hpa = if (readPressure) pressure.read() else -1f
        // The reading can take two seconds; alerts may have gone off meanwhile.
        if (!canRegister() || prefs.pushToken != token || !isFresh(fix)) return false
        val base = currentApi
        prefs.registrationOrigin = base
        val ok = api.post(base, Endpoint.POST_LOCATION, Registration.payload(fix, hpa, settings(token)))
        lastPosted = if (ok) fix else null
        _syncFailed.value = !ok
        // Alerts were turned off while the request was out.
        if (!prefs.notification || !PermUtils.areNotificationsEnabled(context)) unregisterLocked()
        return ok
    }

    private suspend fun unregisterLocked(): Boolean {
        lastPosted = null
        val origin = prefs.registrationOrigin
        val token = prefs.pushToken
        if (token == null) {
            // Nothing to name the registration by; the backend drops it once the token is dead.
            prefs.registrationOrigin = null
            return true
        }
        val base = origin?.let { MeteocoolEnvironment.apiBaseFor(it) ?: it } ?: currentApi
        val ok = api.post(base, Endpoint.UNREGISTER, mapOf("token" to token))
        if (ok) prefs.registrationOrigin = null
        _syncFailed.value = !ok
        return ok
    }

    /**
     * Tells the backend a notification was seen, so it can send the next one.
     * A push can wake the app before FCM has handed over this launch's token,
     * so a missing token is waited for once.
     */
    fun acknowledge(from: String): Job = scope.launch {
        val token = prefs.pushToken ?: run {
            delay(4_000)
            prefs.pushToken
        } ?: return@launch
        if (!Registration.isValidToken(token)) return@launch
        val ok = api.post(currentApi, Endpoint.CLEAR_NOTIFICATION, mapOf("token" to token, "from" to from))
        if (!ok) Timber.d("clear_notification ($from) not accepted")
    }
}
