package uk.staatsprojekte.blockreels

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * Placeholder entry point.
 *
 * Exists so the project produces an installable, launchable APK. The real UI - home screen,
 * onboarding and settings - arrives in the app UI milestone and replaces this entirely.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = getString(R.string.placeholder_body)
                textSize = TEXT_SIZE_SP
                setPadding(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
            },
        )
    }

    private companion object {
        const val TEXT_SIZE_SP = 16f
        const val PADDING_PX = 48
    }
}
