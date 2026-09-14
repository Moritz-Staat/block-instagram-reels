package uk.staatsprojekte.blockreels

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt's dependency graph root.
 *
 * Note there is no foreground service started here and no notification channel registered: the
 * `AccessibilityService` is bound by the system and lives on its own. See
 * `docs/adr/0003-no-foreground-service.md`.
 */
@HiltAndroidApp
class BlockReelsApplication : Application() {
      val badlyIndented  =  1
}
