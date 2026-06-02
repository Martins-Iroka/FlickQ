package com.martdev.flickq.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One column of a [DataTable]: a header label, a layout [weight], and a [cell] text extractor. */
class DataColumn<T>(
    val header: String,
    val weight: Float,
    val cell: (T) -> String,
)

/**
 * A lightweight, theme-consistent table for admin list screens: a header strip plus a scrolling
 * body of rows. Each row maps [columns] to text cells; an optional [rowActions] slot renders
 * trailing controls (edit/delete), and [onRowClick] makes the whole row tappable.
 *
 * For paginated lists, pass [onLoadMore] (plus [canLoadMore]/[isLoadingMore]) to render a footer
 * that shows a "Load more" button or a spinner while the next page loads.
 */
@Composable
fun <T> DataTable(
    items: List<T>,
    columns: List<DataColumn<T>>,
    modifier: Modifier = Modifier,
    onRowClick: ((T) -> Unit)? = null,
    rowActions: (@Composable (T) -> Unit)? = null,
    canLoadMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                columns.forEach { column ->
                    Text(
                        text = column.header.uppercase(),
                        color = FlickQColors.GoldHighlight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(column.weight),
                    )
                }
                if (rowActions != null) Text(text = "", modifier = Modifier.weight(0.4f))
            }
        }
        items(items) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FlickQColors.SurfaceNavy, RoundedCornerShape(8.dp))
                    .then(if (onRowClick != null) Modifier.clickable { onRowClick(item) } else Modifier)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                columns.forEach { column ->
                    Text(
                        text = column.cell(item),
                        color = FlickQColors.TicketPaper,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(column.weight),
                    )
                }
                if (rowActions != null) {
                    Row(
                        modifier = Modifier.weight(0.4f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        rowActions(item)
                    }
                }
            }
        }
        if (onLoadMore != null && (canLoadMore || isLoadingMore)) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoadingMore) {
                        CircularProgressIndicator(color = FlickQColors.Gold)
                    } else {
                        TextButton(onClick = onLoadMore) {
                            Text(text = "Load more", color = FlickQColors.GoldHighlight, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
