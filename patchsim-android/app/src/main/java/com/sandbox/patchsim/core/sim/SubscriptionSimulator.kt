package com.sandbox.patchsim.core.sim

import com.sandbox.patchsim.core.model.SubscriptionState
import com.sandbox.patchsim.core.model.SubscriptionStatus
import com.sandbox.patchsim.core.model.SubscriptionTier

/**
 * Pure state machine that reproduces subscription states (Free, Premium, Pro,
 * active, expired, grace, canceled) for a target app in the local sandbox.
 *
 * Contains no Android dependencies, so it is fully unit-testable on the JVM.
 * A [clock] is injected to make expiry logic deterministic in tests.
 */
class SubscriptionSimulator(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /** Grace window applied after expiry before a subscription is treated as fully lapsed. */
    var graceWindowMillis: Long = 3L * 24 * 60 * 60 * 1000 // 3 days

    /** Build a Free (no subscription) state. */
    fun free(pkg: String): SubscriptionState =
        SubscriptionState(pkg, SubscriptionTier.FREE, SubscriptionStatus.NONE, expiryMillis = null)

    /** Build an active subscription valid for [durationMillis] from now. */
    fun active(pkg: String, tier: SubscriptionTier, durationMillis: Long): SubscriptionState {
        require(tier != SubscriptionTier.FREE) { "FREE tier cannot be an active subscription" }
        return SubscriptionState(pkg, tier, SubscriptionStatus.ACTIVE, clock() + durationMillis)
    }

    /** Build an already-expired subscription (expired [agoMillis] in the past). */
    fun expired(pkg: String, tier: SubscriptionTier, agoMillis: Long): SubscriptionState =
        SubscriptionState(pkg, tier, SubscriptionStatus.EXPIRED, clock() - agoMillis)

    /** Build a canceled-but-still-valid subscription (access until [expiryMillis]). */
    fun canceled(pkg: String, tier: SubscriptionTier, expiryMillis: Long): SubscriptionState =
        SubscriptionState(pkg, tier, SubscriptionStatus.CANCELED, expiryMillis)

    /**
     * Re-evaluate a stored state against the current clock, resolving ACTIVE ->
     * IN_GRACE -> EXPIRED transitions. Idempotent and side-effect free.
     */
    fun evaluate(state: SubscriptionState): SubscriptionState {
        val expiry = state.expiryMillis ?: return state // perpetual
        val now = clock()
        return when {
            now <= expiry -> state.copy(
                status = if (state.status == SubscriptionStatus.CANCELED)
                    SubscriptionStatus.CANCELED else SubscriptionStatus.ACTIVE
            )
            now <= expiry + graceWindowMillis && state.status != SubscriptionStatus.CANCELED ->
                state.copy(status = SubscriptionStatus.IN_GRACE)
            else -> state.copy(status = SubscriptionStatus.EXPIRED)
        }
    }

    /** Whether the evaluated state currently grants entitled (paid) access. */
    fun hasEntitlement(state: SubscriptionState): Boolean {
        val evaluated = evaluate(state)
        return evaluated.tier != SubscriptionTier.FREE &&
            evaluated.status in setOf(
                SubscriptionStatus.ACTIVE,
                SubscriptionStatus.IN_GRACE,
                SubscriptionStatus.CANCELED
            )
    }
}
