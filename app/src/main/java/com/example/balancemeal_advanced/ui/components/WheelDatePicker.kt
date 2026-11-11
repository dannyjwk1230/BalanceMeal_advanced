package com.example.balancemeal_advanced.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shared meal/birth date sheet. [onSelect] receives an ISO YYYY-MM-DD string only on confirmation.
 * Dismissing or scrolling does not change the caller's saved date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WheelDatePicker(
    mode: DatePickerMode,
    initialDate: LocalDate = LocalDate.now(),
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    var selectedIso by rememberSaveable(mode, initialDate, today) {
        mutableStateOf(WheelDateRules.clamp(initialDate, mode, today).toString())
    }
    val date = LocalDate.parse(selectedIso)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    var yearMoving by remember { mutableStateOf(false) }
    var monthMoving by remember { mutableStateOf(false) }
    var dayMoving by remember { mutableStateOf(false) }
    val rowHeight = (48f * maxOf(1f, LocalDensity.current.fontScale * .85f)).dp

    val content: @Composable () -> Unit = {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val visibleRows = if (maxHeight < 440.dp) 3 else 5
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
                    .testTag("wheel_date_picker"),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(if (mode == DatePickerMode.MEAL) "식사 날짜 선택" else "생년월일 선택",
                    style = MaterialTheme.typography.labelLarge, color = Color(0xFF687583))
                Box(Modifier.fillMaxWidth().height(rowHeight * visibleRows), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(rowHeight)
                        .background(Color(0xFFF1F3F5), RoundedCornerShape(16.dp)))
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateWheel(
                            values = WheelDateRules.years(mode, today).toList(),
                            selected = date.year, suffix = "년", label = "년", tag = "date_wheel_year",
                            cyclic = false, rowHeight = rowHeight, visibleRows = visibleRows,
                            modifier = Modifier.weight(1f), onMoving = { yearMoving = it },
                            onValue = { selectedIso = WheelDateRules.change(LocalDate.parse(selectedIso), mode, today, year = it).toString() },
                        )
                        DateWheel(
                            values = WheelDateRules.months(date, today).toList(),
                            selected = date.monthValue, suffix = "월", label = "월", tag = "date_wheel_month",
                            cyclic = true, rowHeight = rowHeight, visibleRows = visibleRows,
                            modifier = Modifier.weight(1f), onMoving = { monthMoving = it },
                            onValue = { selectedIso = WheelDateRules.change(LocalDate.parse(selectedIso), mode, today, month = it).toString() },
                        )
                        DateWheel(
                            values = WheelDateRules.days(date, today).toList(),
                            selected = date.dayOfMonth, suffix = "일", label = "일", tag = "date_wheel_day",
                            cyclic = true, rowHeight = rowHeight, visibleRows = visibleRows,
                            modifier = Modifier.weight(1f), onMoving = { dayMoving = it },
                            onValue = { selectedIso = WheelDateRules.change(LocalDate.parse(selectedIso), mode, today, day = it).toString() },
                        )
                    }
                }
                Button(
                    onClick = {
                        if (!confirming) {
                            confirming = true
                            val result = selectedIso
                            scope.launch {
                                sheetState.hide()
                                onDismissRequest()
                                onSelect(result)
                            }
                        }
                    },
                    enabled = !confirming && !yearMoving && !monthMoving && !dayMoving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("date_picker_confirm"),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("선택", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
    if (LocalInspectionMode.current) {
        // Studio previews cannot capture the separate dialog window.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .22f)), contentAlignment = Alignment.BottomCenter) {
            Surface(color = Color.White, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BottomSheetDefaults.DragHandle(color = Color(0xFFD5DADF), width = 36.dp, height = 4.dp)
                    content()
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFFD5DADF), width = 36.dp, height = 4.dp) },
        ) { content() }
    }
}

@Composable
private fun DateWheel(
    values: List<Int>, selected: Int, suffix: String, label: String, tag: String,
    cyclic: Boolean, rowHeight: Dp, visibleRows: Int, modifier: Modifier,
    onMoving: (Boolean) -> Unit, onValue: (Int) -> Unit,
) {
    // A changed calendar range must rebuild the wheel, e.g. January 31 -> February 28.
    key(values) {
        val base = if (cyclic) values.size * 5000 else 0
        val initialIndex = base + values.indexOf(selected).coerceAtLeast(0)
        val count = if (cyclic) values.size * 10000 else values.size
        val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
        val scope = rememberCoroutineScope()
        val latestOnValue by rememberUpdatedState(onValue)
        val latestOnMoving by rememberUpdatedState(onMoving)
        val centeredIndex by remember(state) {
            derivedStateOf {
                val layout = state.layoutInfo
                val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index ?: initialIndex
            }
        }
        LaunchedEffect(state) {
            snapshotFlow { centeredIndex }.distinctUntilChanged().collect { index ->
                latestOnValue(values[index % values.size])
            }
        }
        LaunchedEffect(state) {
            snapshotFlow { state.isScrollInProgress }.distinctUntilChanged().collect { latestOnMoving(it) }
        }
        DisposableEffect(state) { onDispose { latestOnMoving(false) } }
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = rowHeight * (visibleRows / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier.fillMaxHeight().testTag(tag).semantics {
                contentDescription = "$label 선택"
                stateDescription = "${values[centeredIndex % values.size]}$suffix"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    values[centeredIndex % values.size].toFloat(),
                    values.first().toFloat()..values.last().toFloat(), (values.size - 2).coerceAtLeast(0))
                setProgress { target ->
                    val targetValue = target.roundToInt().coerceIn(values.first(), values.last())
                    val targetIndex = values.indexOf(targetValue)
                    val cycleStart = if (cyclic) centeredIndex - centeredIndex % values.size else 0
                    scope.launch { state.scrollToItem((cycleStart + targetIndex).coerceIn(0, count - 1)) }
                    true
                }
            },
        ) {
            items(count, key = { it }) { index ->
                Box(
                    Modifier.fillMaxWidth().height(rowHeight)
                        .clickable { scope.launch { state.animateScrollToItem(index) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${values[index % values.size]}$suffix",
                        fontSize = 21.sp,
                        fontWeight = if (index == centeredIndex) FontWeight.Bold else FontWeight.Normal,
                        color = Color(0xFF17212D),
                        modifier = Modifier.graphicsLayer {
                            val layout = state.layoutInfo
                            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                            val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
                            val distance = item?.let { abs(it.offset + it.size / 2f - center) / it.size } ?: 3f
                            alpha = (1f - distance * .36f).coerceIn(.16f, 1f)
                        },
                    )
                }
            }
        }
    }
}
