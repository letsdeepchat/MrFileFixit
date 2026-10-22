package com.mrfilefixit.core.ui.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/** Phone, foldable and tablet. */
@Preview(name = "Phone", device = "spec:width=411dp,height=891dp", showBackground = true)
@Preview(name = "Phone landscape", device = "spec:width=891dp,height=411dp", showBackground = true)
@Preview(name = "Foldable", device = "spec:width=673dp,height=841dp", showBackground = true)
@Preview(name = "Tablet", device = "spec:width=1280dp,height=800dp,dpi=240", showBackground = true)
annotation class DevicePreviews

/** Light and dark. */
@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    showBackground = true,
)
annotation class ThemePreviews

/** Default and 200% font scale, plus Hindi to catch truncation in longer strings. */
@Preview(name = "Font 100%", fontScale = 1f, showBackground = true)
@Preview(name = "Font 200%", fontScale = 2f, showBackground = true)
@Preview(name = "Hindi", locale = "hi", showBackground = true)
annotation class FontScalePreviews

/** Everything at once, for the main screens. */
@DevicePreviews
@ThemePreviews
@FontScalePreviews
annotation class AllPreviews
