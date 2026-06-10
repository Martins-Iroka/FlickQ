package com.martdev.flickq.adminkobweb.theme

import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.fontFamily

/** Montserrat for headings/brand; Inter (the body default) is applied globally in `initSilk`. */
fun Modifier.montserrat(): Modifier = fontFamily("Montserrat", "system-ui", "sans-serif")
