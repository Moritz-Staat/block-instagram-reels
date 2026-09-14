package uk.staatsprojekte.blockreels.wiring

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Proves each part of the toolchain is actually wired up, not merely declared in the build script.
 *
 * Every one of these would pass trivially if the library were on the classpath but the plugin or
 * runner were misconfigured - so each asserts on a real round-trip rather than on a constructor
 * call. If this class compiles and passes, the stack works.
 */
class ToolchainWiringTest {

    @Test
    fun `jupiter runs on the junit platform`() {
        // If testDebugUnitTest is still on JUnit4 this test is silently never executed, so the
        // assertion matters less than the fact that the class is picked up at all.
        assertThat(2 + 2).isEqualTo(4)
    }

    @Test
    fun `truth assertions are available`() {
        assertThat(listOf("reels", "shorts")).containsExactly("reels", "shorts").inOrder()
    }

    @Test
    fun `kotlinx serialization round-trips a data class`() {
        val json = Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(Target.serializer(), Target("instagram_reels", 5))

        assertThat(json.decodeFromString(Target.serializer(), encoded))
            .isEqualTo(Target("instagram_reels", 5))
    }

    @Test
    fun `serialization ignores unknown keys`() {
        // Forward compatibility for rules.json: a newer file must stay loadable on an older APK.
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(
            Target.serializer(),
            """{"id":"instagram_reels","budgetMinutes":5,"somethingAddedLater":true}"""
        )

        assertThat(decoded.id).isEqualTo("instagram_reels")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `turbine observes a flow with virtual time`() = runTest {
        val flow = MutableStateFlow(0)

        flow.test {
            assertThat(awaitItem()).isEqualTo(0)
            flow.value = 1
            assertThat(awaitItem()).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }

        // The virtual clock must advance without the test actually sleeping.
        val before = testScheduler.currentTime
        advanceTimeBy(60_000)
        assertThat(testScheduler.currentTime - before).isAtLeast(60_000)
    }

    @Test
    fun `datastore round-trips a value`(@TempDir tmp: File) = runTest {
        val key = intPreferencesKey("consumed_seconds")
        val store = PreferenceDataStoreFactory.create { File(tmp, "test.preferences_pb") }

        store.edit { it[key] = 42 }

        assertThat(store.data.first()[key]).isEqualTo(42)
    }

    @Test
    fun `okhttp talks to mockwebserver`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"schemaVersion":1}""").setResponseCode(200))
        server.start()

        try {
            val response = OkHttpClient().newCall(
                Request.Builder().url(server.url("/rules.json")).build()
            ).execute()

            response.use {
                assertThat(it.code).isEqualTo(200)
                assertThat(it.body?.string()).contains("schemaVersion")
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `an injected clock is controllable in tests`() {
        // The pattern DayBoundary and BudgetTracker rely on: never read the system clock directly.
        val fixed = Clock.fixed(Instant.parse("2026-09-14T03:59:00Z"), ZoneOffset.UTC)

        assertThat(Instant.now(fixed)).isEqualTo(Instant.parse("2026-09-14T03:59:00Z"))
    }

    @Serializable
    private data class Target(val id: String, val budgetMinutes: Int)
}
