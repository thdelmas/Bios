package com.bios.app.alerts

import android.content.Context
import android.content.res.Resources

/**
 * Resolves localized text for [ConditionPattern]s at display time.
 *
 * Convention (issue #210):
 *   pattern_<id>_explanation       -> ConditionPattern.explanation
 *   pattern_<id>_suggested_action  -> ConditionPattern.suggestedAction
 *
 * If the resource is present in the active locale's `values-<locale>/strings.xml`,
 * Android returns the overlay value; if a key is missing for a locale, Android
 * falls back to the default `values/strings.xml`. If a key is missing in the
 * default resources too, this resolver falls back to the Kotlin source field
 * (the English text declared on the pattern itself).
 *
 * **Why two-stage fallback?**
 *   - Unit tests without an Android `Context` keep working — they read the
 *     Kotlin field directly.
 *   - Locale overlays may translate only a subset of patterns; absent keys
 *     fall back to the default English `strings.xml` (Android resource
 *     resolution), and patterns not yet declared in `strings.xml` fall back
 *     to the Kotlin source. Empty translations are better than wrong ones.
 *
 * **Where to use:** the alert rendering layer — notifications
 * ([AlertManager]), Compose surfaces (DiagnosticCard, ConditionDetailScreen,
 * AlertCard, ProfessionalReviewScreen) — should call [explanationFor] /
 * [suggestedActionFor] instead of reading `pattern.explanation` / `.suggestedAction`
 * directly when a [Context] is available.
 */
object AlertTextResolver {

    /** Resource key for the explanation field of a pattern. */
    fun explanationKey(patternId: String): String =
        "pattern_${patternId.lowercase()}_explanation"

    /** Resource key for the suggestedAction field of a pattern. */
    fun suggestedActionKey(patternId: String): String =
        "pattern_${patternId.lowercase()}_suggested_action"

    /**
     * Localized explanation for [pattern], falling back to the Kotlin
     * source field when no string resource is declared.
     */
    fun explanationFor(context: Context, pattern: ConditionPattern): String =
        lookup(context, explanationKey(pattern.id)) ?: pattern.explanation

    /**
     * Localized suggestedAction for [pattern]. Returns null when the pattern
     * itself has no suggestedAction declared *and* no resource is present.
     */
    fun suggestedActionFor(context: Context, pattern: ConditionPattern): String? {
        val resolved = lookup(context, suggestedActionKey(pattern.id))
        return resolved ?: pattern.suggestedAction
    }

    /**
     * Returns the resolved string for [resourceName] in the current locale,
     * or null if the resource is absent. Lookup uses
     * [Resources.getIdentifier] so callers don't have to maintain a Kotlin
     * `R.string.*` reference table for every pattern id.
     */
    private fun lookup(context: Context, resourceName: String): String? {
        val resId = context.resources.getIdentifier(
            resourceName, "string", context.packageName
        )
        if (resId == 0) return null
        return try {
            context.resources.getString(resId).takeIf { it.isNotBlank() }
        } catch (_: Resources.NotFoundException) {
            null
        }
    }
}
