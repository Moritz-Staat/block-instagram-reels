package uk.staatsprojekte.blockreels.rules

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the `rules.json` bundled into the APK.
 *
 * This is the thin framework-facing shell around [RuleSetParser]: the only thing it knows how to
 * do is turn an asset into a string. Keeping it separate is what lets the model and the parser stay
 * framework-free and unit-testable - see `docs/ARCHITECTURE.md`.
 *
 * For M1 this is the only rule source. The remote fetch, the on-disk cache and the
 * newest-wins fallback chain arrive in M4; this class becomes the last tier of that chain.
 */
@Singleton
class BundledRuleSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val parser: RuleSetParser
) {

    /**
     * Loads and validates the bundled rule set.
     *
     * The asset is committed, byte-identical to the repo root copy, and validated in CI, so a
     * failure here is a programming error rather than a runtime condition. It still returns a
     * [Result] rather than throwing, because a crash inside an accessibility callback takes the
     * whole service down.
     */
    fun load(): Result<RuleSet> = runCatching {
        context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
    }.mapCatching { raw ->
        parser.parse(raw).getOrThrow()
    }

    private companion object {
        const val ASSET_NAME = "rules.json"
    }
}
