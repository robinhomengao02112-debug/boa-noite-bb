package com.sandbox.patchsim.core.sim

import com.sandbox.patchsim.core.model.LicenseResponse
import com.sandbox.patchsim.core.model.LicenseVerdict

/**
 * Decides the verdict a local mock license server returns for a given package.
 *
 * This mirrors the shape of a classic LVL (License Verification Library)
 * response so a developer can test how their OWN app reacts to LICENSED /
 * NOT_LICENSED / RETRY / ERROR outcomes — entirely offline. It validates nothing
 * against any real licensing backend.
 */
class LicensePolicy {

    /** Per-package configured verdict. Defaults to LICENSED when unset. */
    private val configured = HashMap<String, LicenseVerdict>()

    fun configure(packageName: String, verdict: LicenseVerdict) {
        configured[packageName] = verdict
    }

    fun verdictFor(packageName: String): LicenseVerdict =
        configured[packageName] ?: LicenseVerdict.LICENSED

    /** Produce a signed-looking (but local) response echoing the client [nonce]. */
    fun evaluate(packageName: String, nonce: String): LicenseResponse {
        val verdict = verdictFor(packageName)
        val allow = verdict == LicenseVerdict.LICENSED
        val reason = when (verdict) {
            LicenseVerdict.LICENSED -> "Local mock: entitlement granted"
            LicenseVerdict.NOT_LICENSED -> "Local mock: entitlement denied"
            LicenseVerdict.RETRY -> "Local mock: transient, client should retry"
            LicenseVerdict.ERROR_SERVER -> "Local mock: simulated server error"
        }
        return LicenseResponse(
            packageName = packageName,
            verdict = verdict,
            allowAccess = allow,
            reason = reason,
            nonce = nonce
        )
    }
}
