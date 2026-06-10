package com.martdev.flickq.adminkobweb.theme

import com.varabyte.kobweb.compose.ui.graphics.Color

/**
 * CineAdmin palette, lifted from the Figma admin redesign. Deep-navy surfaces, a red primary
 * CTA, amber/green accents, and warm-tinted heading/body text.
 */
object AdminColors {
    val Bg = Color.rgb(0x051424)
    val BgDeep = Color.rgb(0x010f1f)
    val Sidebar = Color.rgb(0x081320)
    val Surface = Color.rgb(0x0d1b2c)
    val SurfaceAlt = Color.rgb(0x122131)
    val Border = Color.rgb(0x1e2d3f)
    val BorderWarm = Color.rgb(0x5e3f3b)

    val Primary = Color.rgb(0xe50914)
    val PrimaryHover = Color.rgb(0xf01622)
    val OnPrimary = Color.rgb(0xfff7f6)

    val Amber = Color.rgb(0xf59e0b)
    val Success = Color.rgb(0x22c55e)

    val Heading = Color.rgb(0xd4e4fa)
    val Body = Color.rgb(0xe9bcb6)
    val BodyStrong = Color.rgb(0xffdad5)
    val Muted = Color.rgb(0x8a93a2)
    val White = Color.rgb(0xffffff)

    /** Neutral chip / secondary-button / avatar fill. */
    val Chip = Color.rgb(0x273647)

    /** Selected nav-link background: 10%-opacity red wash. */
    val PrimaryWash = Primary.copyf(alpha = 0.1f)
    val SuccessWash = Success.copyf(alpha = 0.1f)
    val AmberWash = Amber.copyf(alpha = 0.2f)
    val SuccessChip = Success.copyf(alpha = 0.2f)
}
