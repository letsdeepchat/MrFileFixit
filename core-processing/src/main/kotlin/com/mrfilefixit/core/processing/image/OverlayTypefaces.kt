package com.mrfilefixit.core.processing.image

import android.content.Context
import android.graphics.Typeface
import com.mrfilefixit.core.domain.image.OverlayFont
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** The bundled fonts for text on a picture, loaded from the app's assets once and kept. */
@Singleton
internal class OverlayTypefaces @Inject constructor(@ApplicationContext private val context: Context) {
    private val loaded = ConcurrentHashMap<OverlayFont, Typeface>()

    /** The font's typeface; the system's bold sans if the asset cannot be read, so text is never lost. */
    operator fun get(font: OverlayFont): Typeface = loaded.getOrPut(font) {
        try {
            Typeface.createFromAsset(context.assets, font.asset)
        } catch (_: RuntimeException) {
            Typeface.DEFAULT_BOLD
        }
    }
}
