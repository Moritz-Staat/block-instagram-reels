package uk.staatsprojekte.blockreels

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * Placeholder entry point.
 *
 * Exists so the project produces an installable, launchable APK and so the Hilt and Compose wiring
 * is exercised by something real. The home screen, onboarding and settings replace this entirely in
 * the app UI milestone.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Injected to prove the Hilt graph reaches an activity - and it is the real clock, used later. */
    @Inject
    lateinit var clock: Clock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlaceholderContent(now = ZonedDateTime.now(clock).toString())
                }
            }
        }
    }
}

@Composable
private fun PlaceholderContent(now: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(24.dp)) {
        Text(
            text = "Block Reels",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(R.string.placeholder_body),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Injected clock says: $now",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
