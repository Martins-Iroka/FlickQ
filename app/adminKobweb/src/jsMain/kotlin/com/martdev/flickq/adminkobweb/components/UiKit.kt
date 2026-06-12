package com.martdev.flickq.adminkobweb.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.adminkobweb.theme.montserrat
import com.martdev.flickq.core.presentation.UiText
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.border
import com.varabyte.kobweb.compose.ui.modifiers.borderBottom
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.cursor
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.flexBasis
import com.varabyte.kobweb.compose.ui.modifiers.flexGrow
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.height
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.opacity
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.compose.ui.modifiers.textAlign
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.thenIf
import com.varabyte.kobweb.silk.components.icons.fa.FaCheck
import com.varabyte.kobweb.silk.components.icons.fa.FaPlus
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.TextArea
import kotlin.time.Instant

/**
 * The page-level CineAdmin UI kit shared by the Movies/Rooms/Showtimes/Reservations pages
 * (extracted after the same private helpers had been copied into a 4th page). Purely visual
 * building blocks — anything feature-specific (status badges per enum, pickers, grids) stays
 * private to its page.
 */

// ---- Table cells ----------------------------------------------------------------------------

typealias CellWidth = org.jetbrains.compose.web.css.CSSNumericValue<out org.jetbrains.compose.web.css.CSSUnitLengthOrPercentage>

/** Fixed-width column when [width] is given, otherwise an equal share of the leftover space. */
@Composable
fun Cell(width: CellWidth?, content: @Composable () -> Unit) {
    val mod = if (width != null) Modifier.width(width) else Modifier.flexGrow(1).flexBasis(0.px)
    Box(modifier = mod) { content() }
}

@Composable
fun HeaderCell(text: String, width: CellWidth?) {
    Cell(width) { SpanText(text, Modifier.color(AdminColors.Muted).fontSize(12.px).fontWeight(FontWeight.SemiBold)) }
}

// ---- Cards ----------------------------------------------------------------------------------

/** Sectioned card with an icon + Montserrat title header and an optional right-aligned chip. */
@Composable
fun FormCard(title: String, icon: @Composable (Modifier) -> Unit, badge: String? = null, body: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm).borderRadius(12.px).padding(24.px),
        verticalArrangement = Arrangement.spacedBy(14.px),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.Border).padding(bottom = 14.px),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                icon(Modifier.color(AdminColors.Primary).fontSize(16.px))
                SpanText(title, Modifier.montserrat().color(AdminColors.Heading).fontSize(18.px).fontWeight(FontWeight.SemiBold))
            }
            badge?.let {
                Box(
                    modifier = Modifier.backgroundColor(AdminColors.Chip).border(1.px, LineStyle.Solid, AdminColors.Border)
                        .borderRadius(6.px).padding(topBottom = 4.px, leftRight = 10.px),
                ) { SpanText(it, Modifier.color(AdminColors.Heading).fontSize(12.px).fontWeight(FontWeight.SemiBold)) }
            }
        }
        body()
    }
}

/** Fixed full-screen scrim with a centered dialog card. */
@Composable
fun Overlay(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.styleModifier {
            property("position", "fixed"); property("inset", "0")
            property("background", "rgba(1,15,31,0.7)"); property("z-index", "50")
        }.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.maxWidth(440.px).fillMaxWidth().backgroundColor(AdminColors.SurfaceAlt)
                .border(1.px, LineStyle.Solid, AdminColors.BorderWarm).borderRadius(12.px).padding(24.px),
            verticalArrangement = Arrangement.spacedBy(16.px),
        ) { content() }
    }
}

// ---- Buttons --------------------------------------------------------------------------------

/** Primary CTA with a leading plus icon ("Add Movie", "Create Showtime", …). */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.backgroundColor(AdminColors.Primary).color(AdminColors.OnPrimary)
            .borderRadius(8.px).padding(topBottom = 11.px, leftRight = 18.px).cursor(Cursor.Pointer).onClick { onClick() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px),
    ) {
        FaPlus(Modifier.color(AdminColors.OnPrimary).fontSize(13.px))
        SpanText(label, Modifier.fontSize(14.px).fontWeight(FontWeight.SemiBold))
    }
}

/** Primary CTA without the leading icon (Generate, confirm dialogs, …). */
@Composable
fun PrimaryButtonPlain(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.backgroundColor(AdminColors.Primary).color(AdminColors.OnPrimary)
            .borderRadius(8.px).padding(topBottom = 11.px, leftRight = 18.px).cursor(Cursor.Pointer).onClick { onClick() },
        contentAlignment = Alignment.Center,
    ) { SpanText(label, Modifier.fontSize(14.px).fontWeight(FontWeight.SemiBold)) }
}

/** Primary CTA with a leading check, dimmed and inert while [enabled] is false. */
@Composable
fun SaveButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.backgroundColor(AdminColors.Primary).color(AdminColors.OnPrimary)
            .borderRadius(8.px).padding(topBottom = 11.px, leftRight = 18.px)
            .thenIf(!enabled) { Modifier.opacity(0.5) }.cursor(Cursor.Pointer)
            .thenIf(enabled) { Modifier.onClick { onClick() } },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px),
    ) {
        FaCheck(Modifier.color(AdminColors.OnPrimary).fontSize(13.px))
        SpanText(label, Modifier.fontSize(14.px).fontWeight(FontWeight.SemiBold))
    }
}

/** Full-width neutral button (dialog secondaries, Retry). */
@Composable
fun SecondaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.Chip)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm).borderRadius(8.px)
            .padding(topBottom = 11.px, leftRight = 18.px).cursor(Cursor.Pointer).onClick { onClick() },
        contentAlignment = Alignment.Center,
    ) { SpanText(label, Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold)) }
}

/** Neutral button that hugs its label (for headers / inline rows). */
@Composable
fun SecondaryButtonInline(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.backgroundColor(AdminColors.Chip)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm).borderRadius(8.px)
            .padding(topBottom = 11.px, leftRight = 18.px).cursor(Cursor.Pointer).onClick { onClick() },
        contentAlignment = Alignment.Center,
    ) { SpanText(label, Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold)) }
}

@Composable
fun PagerButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.backgroundColor(AdminColors.Chip).border(1.px, LineStyle.Solid, AdminColors.Border)
            .borderRadius(6.px).padding(topBottom = 6.px, leftRight = 12.px)
            .thenIf(!enabled) { Modifier.opacity(0.4) }
            .thenIf(enabled) { Modifier.cursor(Cursor.Pointer).onClick { onClick() } },
        contentAlignment = Alignment.Center,
    ) { SpanText(label, Modifier.color(AdminColors.Heading).fontSize(13.px)) }
}

/** 30px square icon action. Stops click propagation so it works inside clickable rows. */
@Composable
fun IconButton(icon: @Composable (Modifier) -> Unit, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(30.px).backgroundColor(AdminColors.Chip).borderRadius(8.px)
            .cursor(Cursor.Pointer).onClick { it.stopPropagation(); onClick() },
        contentAlignment = Alignment.Center,
    ) { icon(Modifier.color(tint).fontSize(13.px)) }
}

// ---- Status / badges ------------------------------------------------------------------------

/** Pill badge with a leading status dot. */
@Composable
fun DotBadge(text: String, bg: Color, fg: Color) {
    Row(
        modifier = Modifier.backgroundColor(bg).borderRadius(9999.px).padding(topBottom = 4.px, leftRight = 10.px),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.px),
    ) {
        Box(Modifier.size(7.px).backgroundColor(fg).borderRadius(9999.px))
        SpanText(text, Modifier.color(fg).fontSize(11.px).fontWeight(FontWeight.SemiBold))
    }
}

/** Centered muted message for loading / empty states. */
@Composable
fun StatusBox(message: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(48.px), contentAlignment = Alignment.Center) {
        SpanText(message, Modifier.color(AdminColors.Muted).fontSize(16.px).textAlign(TextAlign.Center))
    }
}

@Composable
fun ErrorBox(error: UiText, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm).borderRadius(12.px).padding(32.px),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.px),
    ) {
        SpanText(error.plain(), Modifier.color(AdminColors.Body).fontSize(14.px))
        Box(modifier = Modifier.width(140.px)) { SecondaryButton("Retry", onRetry) }
    }
}

// ---- Form fields ----------------------------------------------------------------------------

/** Inline CSS for raw compose-html form fields (Silk has no multiline/date inputs). */
const val FIELD_CSS: String =
    "width:100%;box-sizing:border-box;background-color:#16273a;border:1px solid #30435a;" +
        "border-radius:8px;padding:11px 13px;color:#e9bcb6;font-family:Inter,system-ui,sans-serif;" +
        "font-size:14px;outline:none;"

@Composable
fun FieldLabel(text: String) {
    SpanText(text, Modifier.color(AdminColors.Muted).fontSize(11.px).fontWeight(FontWeight.SemiBold))
}

@Composable
fun TextField(value: String, placeholder: String, onValue: (String) -> Unit) {
    Input(type = InputType.Text) {
        value(value)
        attr("placeholder", placeholder)
        attr("style", FIELD_CSS)
        onInput { onValue(it.value) }
    }
}

@Composable
fun TextAreaField(value: String, onValue: (String) -> Unit) {
    TextArea(value = value, attrs = {
        attr("style", FIELD_CSS + "min-height:150px;resize:vertical;")
        onInput { onValue(it.value) }
    })
}

/** Native date input; value is ISO `yyyy-MM-dd`. */
@Composable
fun DateField(value: String, onValue: (String) -> Unit) {
    Input(type = InputType.Date) {
        value(value)
        attr("style", FIELD_CSS)
        onInput { onValue(it.value) }
    }
}

/** Native datetime-local input over an ISO-instant string (UTC wall-clock round-trip). */
@Composable
fun DateTimeField(iso: String, onValue: (String) -> Unit) {
    Input(type = InputType.DateTimeLocal) {
        value(toLocalInput(iso))
        attr("style", FIELD_CSS)
        onInput { onValue(it.value) }
    }
}

/**
 * Custom dropdown (the native compose-html `Select` proved unreliable).
 * [options] are (value, label) pairs; the blank value renders as [placeholder].
 */
@Composable
fun Dropdown(value: String, placeholder: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == value }?.second ?: placeholder
    Box(modifier = Modifier.fillMaxWidth().styleModifier { property("position", "relative") }) {
        Row(
            modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.Surface)
                .border(1.px, LineStyle.Solid, AdminColors.Border).borderRadius(8.px)
                .padding(topBottom = 11.px, leftRight = 13.px).cursor(Cursor.Pointer).onClick { open = !open },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SpanText(label, Modifier.color(if (value.isBlank()) AdminColors.Muted else AdminColors.Body).fontSize(14.px))
            SpanText("▾", Modifier.color(AdminColors.Muted).fontSize(12.px))
        }
        if (open) {
            Column(
                modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.SurfaceAlt)
                    .border(1.px, LineStyle.Solid, AdminColors.BorderWarm).borderRadius(8.px)
                    .styleModifier {
                        property("position", "absolute"); property("top", "calc(100% + 4px)")
                        property("left", "0"); property("right", "0"); property("z-index", "40")
                        property("max-height", "240px"); property("overflow-y", "auto")
                    },
            ) {
                if (options.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.px)) {
                        SpanText("No options.", Modifier.color(AdminColors.Muted).fontSize(13.px))
                    }
                }
                options.forEach { (v, l) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(topBottom = 10.px, leftRight = 13.px)
                            .cursor(Cursor.Pointer).onClick { onSelect(v); open = false },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpanText(l, Modifier.color(if (v == value) AdminColors.Primary else AdminColors.Body).fontSize(14.px))
                    }
                }
            }
        }
    }
}

// ---- Media ----------------------------------------------------------------------------------

/** Poster image thumb with a chip-coloured placeholder for non-http URLs. */
@Composable
fun PosterThumb(url: String, w: Int, h: Int) {
    if (url.startsWith("http")) {
        Img(src = url, attrs = { attr("style", "width:${w}px;height:${h}px;object-fit:cover;border-radius:4px;") })
    } else {
        Box(modifier = Modifier.width(w.px).height(h.px).backgroundColor(AdminColors.Chip).borderRadius(4.px))
    }
}

// ---- Text helpers ---------------------------------------------------------------------------

fun UiText.plain(): String = when (this) {
    is UiText.DynamicString -> value
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "2024-03-01" → "Mar 01, 2024"; passes through anything unexpected. */
fun formatDate(iso: String): String {
    val parts = iso.split("-")
    if (parts.size != 3) return iso
    val month = parts[1].toIntOrNull()?.let { MONTHS.getOrNull(it - 1) } ?: return iso
    return "$month ${parts[2]}, ${parts[0]}"
}

/** Instant → "Nov 15, 2023" using the UTC wall-clock encoded in its ISO string. */
fun dispDate(instant: Instant): String {
    val iso = instant.toString()
    val parts = iso.take(10).split("-")
    if (parts.size != 3) return iso.take(10)
    val month = parts[1].toIntOrNull()?.let { MONTHS.getOrNull(it - 1) } ?: return iso.take(10)
    return "$month ${parts[2]}, ${parts[0]}"
}

/** Instant → "19:00". */
fun dispTime(instant: Instant): String {
    val iso = instant.toString()
    return if (iso.length >= 16) iso.substring(11, 16) else ""
}

/** Instant → "yyyy-MM-dd" (UTC wall-clock), for date filtering. */
fun isoDay(instant: Instant): String = instant.toString().take(10)

/** Instant (UTC wall-clock) → "yyyy-MM-ddTHH:mm" for a datetime-local input. */
fun toLocalInput(iso: String): String = if (iso.length >= 16) iso.substring(0, 16) else ""

/** datetime-local value → ISO instant string with a UTC offset the VMs can parse. */
fun fromLocalInput(local: String): String = when {
    local.isBlank() -> ""
    local.length == 16 -> "$local:00Z"
    else -> "${local}Z"
}

fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return "${h}h ${m}m"
}

/** 1234567 → "1,234,567". */
fun groupDigits(amount: Long): String =
    amount.toString().reversed().chunked(3).joinToString(",").reversed()

fun formatNaira(amount: Long): String = "₦" + groupDigits(amount)

fun formatNaira(amount: Int): String = formatNaira(amount.toLong())

fun String.titlecaseWord(): String =
    lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
