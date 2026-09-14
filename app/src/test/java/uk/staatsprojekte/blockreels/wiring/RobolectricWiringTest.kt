package uk.staatsprojekte.blockreels.wiring

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Proves Robolectric runs on the JUnit Platform next to Jupiter, via the vintage engine.
 *
 * This is the **only** reason JUnit4 is on the test classpath. Robolectric is reserved for the few
 * places where a real [Context] is genuinely unavoidable; everything with real logic in it -
 * `ScreenMatcher`, `BudgetTracker`, `DayBoundary` - is framework-free and tested with Jupiter.
 * New tests go in Jupiter unless they need a [Context].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class RobolectricWiringTest {

    @Test
    fun androidContextIsAvailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat(context.packageName).isEqualTo("uk.staatsprojekte.blockreels.debug")
    }

    @Test
    fun appResourcesResolve() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat(context.getString(uk.staatsprojekte.blockreels.R.string.app_name))
            .isEqualTo("Block Reels")
    }
}

/**
 * Robolectric needs a concrete SDK level. Pinned to the project's `minSdk` so the tests exercise
 * the oldest platform the app actually supports.
 */
private const val ROBOLECTRIC_SDK = 30
