package tv.anime.app.ui

import android.os.Build

/**
 * Some x86/x86_64 emulators/devices can behave inconsistently with hardware bitmaps.
 * Disabling hardware bitmaps there tends to improve image reliability.
 */
internal val AllowHardwareBitmaps: Boolean by lazy {
    Build.SUPPORTED_ABIS.none { it.contains("x86", ignoreCase = true) }
}
