package com.sandbox.patchsim.feature.demo

import com.sandbox.patchsim.core.model.SubscriptionTier

/** Describes the bundled demo app and its premium-gated features. */
object DemoApp {
    const val PACKAGE = "com.sandbox.patchsim.demo"

    data class Feature(
        val id: String,
        val name: String,
        val minTier: SubscriptionTier,
        val description: String
    )

    val features = listOf(
        Feature("basic.notes", "Basic Notes", SubscriptionTier.FREE, "Always available to every user."),
        Feature("cloud.sync", "Cloud Sync", SubscriptionTier.PREMIUM, "Requires an active Premium subscription."),
        Feature("themes.pack", "Premium Themes", SubscriptionTier.PREMIUM, "Unlocked with Premium or higher."),
        Feature("ai.assistant", "AI Assistant", SubscriptionTier.PRO, "Requires the Pro tier."),
        Feature("export.pdf", "Unlimited PDF Export", SubscriptionTier.PRO, "Pro-only power feature.")
    )

    /** Tier ordering used to decide whether a feature is unlocked. */
    fun tierRank(tier: SubscriptionTier): Int = when (tier) {
        SubscriptionTier.FREE -> 0
        SubscriptionTier.PREMIUM -> 1
        SubscriptionTier.PRO -> 2
    }

    fun isUnlocked(feature: Feature, currentTier: SubscriptionTier, entitled: Boolean): Boolean {
        if (feature.minTier == SubscriptionTier.FREE) return true
        return entitled && tierRank(currentTier) >= tierRank(feature.minTier)
    }
}
