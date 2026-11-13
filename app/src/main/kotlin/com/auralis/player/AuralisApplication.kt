package com.auralis.player

import android.app.Application
import com.auralis.player.audio.EqualizerController
import com.auralis.player.settings.LocaleApplier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AuralisApplication : Application() {

    // Injected only so the singleton is constructed at startup: it starts
    // listening for the player's audio session and re-applies the saved
    // equalizer settings, even if the equalizer screen is never opened.
    @Inject
    lateinit var equalizerController: EqualizerController

    override fun onCreate() {
        super.onCreate()
        // English is the only language. Applying it on every process start also moves anyone who
        // had Persian selected in an older version back to English, ahead of any Activity.
        LocaleApplier.apply(LocaleApplier.DEFAULT_TAG)
        // Deliberately no library scan here: music folders are only scanned when the user taps
        // Refresh in Settings (or adds / re-grants a folder), never automatically on launch.
    }
}
