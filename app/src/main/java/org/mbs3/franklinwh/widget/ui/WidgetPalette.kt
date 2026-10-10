package org.mbs3.franklinwh.widget.ui

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import org.mbs3.franklinwh.widget.data.DisplayFormat

/**
 * Widget colours. The card is translucent over the wallpaper (dark in night mode, light in day
 * mode). Accents use the brighter shade on the dark card and a deeper shade of the same hue on
 * the light card, where the bright shades would be too faint to read.
 */
object WidgetPalette {
    val card: ColorProvider = ColorProvider(day = Color(0xA6FFFFFF), night = Color(0x8C000000))
    val textPrimary: ColorProvider = ColorProvider(day = Color(0xFF1B1B1F), night = Color(0xFFFFFFFF))
    val textSecondary: ColorProvider = ColorProvider(day = Color(0xB31B1B1F), night = Color(0xB3FFFFFF))
    val track: ColorProvider = ColorProvider(day = Color(0x331B1B1F), night = Color(0x33FFFFFF))

    val solar: ColorProvider = ColorProvider(day = Color(0xFFC77800), night = Color(0xFFFFB300))
    val load: ColorProvider = ColorProvider(day = Color(0xFF0288D1), night = Color(0xFF4FC3F7))
    val grid: ColorProvider = ColorProvider(day = Color(0xFF7E57C2), night = Color(0xFFB39DDB))
    val charging: ColorProvider = ColorProvider(day = Color(0xFF388E3C), night = Color(0xFF66BB6A))
    val discharging: ColorProvider = ColorProvider(day = Color(0xFFE65100), night = Color(0xFFFFA726))
    val idle: ColorProvider = ColorProvider(day = Color(0xFF757575), night = Color(0xFF9E9E9E))

    /** Battery accent by direction; [batteryW] is positive when discharging. */
    fun batteryColor(batteryW: Double): ColorProvider = when {
        batteryW < -DisplayFormat.DEADBAND_W -> charging
        batteryW > DisplayFormat.DEADBAND_W -> discharging
        else -> idle
    }
}
