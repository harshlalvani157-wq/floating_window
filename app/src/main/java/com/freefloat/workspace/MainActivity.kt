package com.freefloat.workspace

import android.content.Context
import android.content.Intent
import android.graphics.Color as GColor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freefloat.workspace.floating.FloatingWindowService
import kotlin.math.roundToInt

data class Panel(val id: String, val title: String, val icon: ImageVector, val color: Color)
data class FloatWindow(val id: Int, val panel: Panel, val offset: Offset, val size: IntSize = IntSize(320, 390), val minimized: Boolean = false, val opacity: Float = 1f, val focused: Boolean = true)
data class IntSize(val width: Int, val height: Int)

private val panels = listOf(
    Panel("notes", "Notes", Icons.Default.Notes, Color(0xFFB7A2FF)), Panel("todo", "To-do", Icons.Default.CheckCircle, Color(0xFF72D4B6)),
    Panel("calc", "Calculator", Icons.Default.Calculate, Color(0xFFFFC56E)), Panel("clock", "Clock", Icons.Default.Schedule, Color(0xFF78B7FF)),
    Panel("calendar", "Calendar", Icons.Default.CalendarMonth, Color(0xFFFF8DAD)), Panel("music", "Music", Icons.Default.MusicNote, Color(0xFFCF91FF)),
    Panel("system", "System info", Icons.Default.Insights, Color(0xFF77D7E8)), Panel("web", "Web panel", Icons.Default.Language, Color(0xFF9EA9FF))
)

class WorkspaceViewModel(private val prefs: android.content.SharedPreferences? = null) : ViewModel() {
    var windows by mutableStateOf(listOf<FloatWindow>()); private set
    var sidebarOpen by mutableStateOf(false); private set
    var showOnboarding by mutableStateOf(true); private set
    var showSettings by mutableStateOf(false); private set
    var darkMode by mutableStateOf(false); private set
    var defaultOpacity by mutableFloatStateOf(1f); private set
    private var nextId = 1
    fun open(panel: Panel) { windows = windows + FloatWindow(nextId++, panel, Offset(90f + windows.size * 18f, 130f + windows.size * 18f)); sidebarOpen = false; showOnboarding = false }
    fun close(id: Int) { windows = windows.filterNot { it.id == id } }
    fun minimize(id: Int) { windows = windows.map { if (it.id == id) it.copy(minimized = true) else it } }
    fun restore(id: Int) { windows = windows.map { if (it.id == id) it.copy(minimized = false, focused = true) else it } }
    fun updateOffset(id: Int, delta: Offset) { windows = windows.map { if (it.id == id) it.copy(offset = Offset((it.offset.x + delta.x).coerceIn(8f, 850f), (it.offset.y + delta.y).coerceIn(32f, 1300f))) else it } }
    fun updateOpacity(id: Int, value: Float) { windows = windows.map { if (it.id == id) it.copy(opacity = value) else it } }
    fun resize(id: Int, delta: Offset) { windows = windows.map { if (it.id == id) it.copy(size = IntSize((it.size.width + delta.x).roundToInt().coerceIn(260, 600), (it.size.height + delta.y).roundToInt().coerceIn(220, 720))) else it } }
    fun focus(id: Int) { windows = windows.map { it.copy(focused = it.id == id) } }
    fun toggleSidebar() { sidebarOpen = !sidebarOpen }
    fun dismissOnboarding() { showOnboarding = false }
    fun settings() { showSettings = !showSettings; sidebarOpen = false }
    fun toggleTheme() { darkMode = !darkMode }
    fun updateDefaultOpacity(v: Float) { defaultOpacity = v }
    fun closeAll() { windows = emptyList() }
    fun minimizeAll() { windows = windows.map { it.copy(minimized = true) } }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); setContent { FreeFloatApp() } }
}

@Composable fun FreeFloatApp(vm: WorkspaceViewModel = viewModel()) {
    val context = LocalContext.current
    val colors = if (vm.darkMode) darkColorScheme(primary = Color(0xFFB7A2FF), background = Color(0xFF111018), surface = Color(0xFF1D1A27)) else lightColorScheme(primary = Color(0xFF6750A4), background = Color(0xFFF8F6FF), surface = Color.White)
    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) { detectHorizontalDragGestures(onHorizontalDrag = { change, amount -> if (change.position.x > 0 && amount < -18) { vm.toggleSidebar();  } }) }) {
            WorkspaceCanvas(vm)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) { FloatingActionButton(onClick = vm::toggleSidebar, modifier = Modifier.padding(20.dp), containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.ViewSidebar, "Open workspace sidebar") } }
            AnimatedVisibility(vm.sidebarOpen, enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut(), modifier = Modifier.align(Alignment.CenterEnd)) { Sidebar(vm) }
            if (vm.showOnboarding) Onboarding(vm, context)
            if (vm.showSettings) SettingsSheet(vm)
        }
    }
}

@Composable private fun Onboarding(vm: WorkspaceViewModel, context: Context) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .58f)), contentAlignment = Alignment.Center) {
        Card(Modifier.padding(22.dp).fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.OpenInNew, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)); Spacer(Modifier.width(12.dp)); Text("FreeFloat Workspace", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                Text("A private floating workspace for your own panels", style = MaterialTheme.typography.titleMedium)
                Text("This app uses Display over other apps to keep your workspace available above other apps. It does not embed, read, or control third-party apps.")
                Text("A persistent notification is shown while the workspace service is active.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Layers, null); Spacer(Modifier.width(8.dp)); Text("Grant overlay permission") }
                OutlinedButton(onClick = { vm.dismissOnboarding(); if (Settings.canDrawOverlays(context)) context.startForegroundService(Intent(context, FloatingWindowService::class.java)) }, modifier = Modifier.fillMaxWidth()) { Text("Start workspace") }
                TextButton(onClick = vm::dismissOnboarding, modifier = Modifier.align(Alignment.End)) { Text("Explore without overlay") }
            }
        }
    }
}

@Composable private fun Sidebar(vm: WorkspaceViewModel) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)).pointerInput(Unit) { detectHorizontalDragGestures { _, amount -> if (amount > 18) vm.toggleSidebar() } })
        Surface(Modifier.fillMaxHeight().width(310.dp).align(Alignment.CenterEnd), tonalElevation = 12.dp, shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Widgets, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Text("Workspace", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); IconButton(vm::toggleSidebar) { Icon(Icons.Default.Close, "Close sidebar") } }
            Text("Panels", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) { items(panels) { panel -> PanelCard(panel) { vm.open(panel) } } }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = vm::settings, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Settings, null); Spacer(Modifier.width(4.dp)); Text("Settings") }; OutlinedButton(onClick = vm::minimizeAll, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Remove, null); Spacer(Modifier.width(4.dp)); Text("Minimize") } }
        }
        }
    }
}

@Composable private fun PanelCard(panel: Panel, onClick: () -> Unit) { Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = panel.color.copy(alpha = .18f))) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(panel.icon, panel.title, tint = panel.color); Spacer(Modifier.width(12.dp)); Column { Text(panel.title, fontWeight = FontWeight.SemiBold); Text("Open floating panel", style = MaterialTheme.typography.bodySmall) }; Spacer(Modifier.weight(1f)); Icon(Icons.Default.Add, "Open ${panel.title}") } } }

@Composable private fun WorkspaceCanvas(vm: WorkspaceViewModel) {
    Box(Modifier.fillMaxSize().padding(top = 36.dp)) {
        if (vm.windows.isEmpty()) Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Widgets, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary); Text("Your workspace is ready", style = MaterialTheme.typography.headlineSmall); Text("Open the sidebar to add Notes, Calculator, Clock and more.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        vm.windows.filter { !it.minimized }.forEach { win -> FloatingPanel(win, vm) }
        vm.windows.filter { it.minimized }.forEachIndexed { index, win -> Bubble(win, index, vm) }
        Text("FreeFloat", Modifier.align(Alignment.TopStart).padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable private fun FloatingPanel(win: FloatWindow, vm: WorkspaceViewModel) {
    var menu by remember { mutableStateOf(false) }; var resizing by remember { mutableStateOf(false) }
    val elevation by animateFloatAsState(if (win.focused) 12f else 5f, label = "elevation")
    Card(Modifier.offset { IntOffset(win.offset.x.roundToInt(), win.offset.y.roundToInt()) }.width(win.size.width.dp).height(win.size.height.dp).alpha(win.opacity).pointerInput(win.id) { detectDragGestures(onDragStart = { vm.focus(win.id) }) { change, drag -> vm.updateOffset(win.id, drag);  } }, shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(elevation.dp), border = if (win.focused) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(.5f)) else null) {
        Column {
            Row(Modifier.fillMaxWidth().background(win.panel.color.copy(alpha = .22f)).padding(start = 12.dp, end = 4.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) { Icon(win.panel.icon, win.panel.title, tint = win.panel.color); Spacer(Modifier.width(8.dp)); Text(win.panel.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Window menu") }; IconButton(onClick = { vm.minimize(win.id) }) { Icon(Icons.Default.Minimize, "Minimize window") }; IconButton(onClick = { vm.close(win.id) }) { Icon(Icons.Default.Close, "Close window") } }
            Box(Modifier.fillMaxSize().padding(14.dp)) { PanelContent(win.panel); Box(Modifier.align(Alignment.BottomEnd).size(28.dp).pointerInput(win.id) { detectDragGestures(onDragStart = { resizing = true }) { change, drag -> vm.resize(win.id, drag);  } }.border(2.dp, MaterialTheme.colorScheme.primary.copy(.6f), RoundedCornerShape(4.dp))) }
        }
    }
    if (menu) WindowMenu(win, vm, onDismiss = { menu = false })
}

@Composable private fun WindowMenu(win: FloatWindow, vm: WorkspaceViewModel, onDismiss: () -> Unit) { DropdownMenu(expanded = true, onDismissRequest = onDismiss, offset = androidx.compose.ui.unit.DpOffset(win.offset.x.dp, (win.offset.y + 40).dp)) { DropdownMenuItem(text = { Text("Full screen") }, leadingIcon = { Icon(Icons.Default.Fullscreen, null) }, onClick = { onDismiss(); vm.updateOffset(win.id, Offset(8f - win.offset.x, 40f - win.offset.y)); vm.resize(win.id, Offset(800f - win.size.width, 900f - win.size.height)) }); DropdownMenuItem(text = { Text("Minimize") }, leadingIcon = { Icon(Icons.Default.Minimize, null) }, onClick = { vm.minimize(win.id); onDismiss() }); DropdownMenuItem(text = { Text("Swap panel / app") }, leadingIcon = { Icon(Icons.Default.SwapHoriz, null) }, onClick = { vm.open(panels[(panels.indexOf(win.panel) + 1) % panels.size]); onDismiss() }); DropdownMenuItem(text = { Text("Adjust transparency") }, leadingIcon = { Icon(Icons.Default.Opacity, null) }, onClick = { vm.updateOpacity(win.id, if (win.opacity > .7f) .65f else 1f); onDismiss() }); DropdownMenuItem(text = { Text("Center window") }, leadingIcon = { Icon(Icons.Default.CenterFocusStrong, null) }, onClick = { vm.updateOffset(win.id, Offset(120f - win.offset.x, 280f - win.offset.y)); onDismiss() }); DropdownMenuItem(text = { Text("Close") }, leadingIcon = { Icon(Icons.Default.Close, null) }, onClick = { vm.close(win.id); onDismiss() }) } }

@Composable private fun Bubble(win: FloatWindow, index: Int, vm: WorkspaceViewModel) { Surface(onClick = { vm.restore(win.id) }, modifier = Modifier.padding(start = (16 + index * 58).dp, top = 54.dp).size(52.dp), shape = RoundedCornerShape(18.dp), color = win.panel.color, shadowElevation = 8.dp) { Box(contentAlignment = Alignment.Center) { Icon(win.panel.icon, "Restore ${win.panel.title}", tint = Color.White) } } }

@Composable private fun PanelContent(panel: Panel) { when (panel.id) { "notes" -> NotesPanel(); "todo" -> TodoPanel(); "calc" -> CalcPanel(); "clock" -> ClockPanel(); "calendar" -> CalendarPanel(); "music" -> MusicPanel(); "system" -> SystemPanel(); else -> WebPanel() } }
@Composable private fun NotesPanel() { var text by remember { mutableStateOf("") }; Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Quick notes", style = MaterialTheme.typography.titleMedium); OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().weight(1f), placeholder = { Text("Capture an idea…") }) } }
@Composable private fun TodoPanel() { var task by remember { mutableStateOf("") }; var items by remember { mutableStateOf(listOf("Review workspace", "Try edge gesture")) }; Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("To-do list", style = MaterialTheme.typography.titleMedium); Row { OutlinedTextField(task, { task = it }, Modifier.weight(1f), placeholder = { Text("New task") }); IconButton(onClick = { if (task.isNotBlank()) { items = items + task; task = "" } }) { Icon(Icons.Default.Add, "Add task") } }; items.forEach { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(false, {}); Text(it) } } } }
@Composable private fun CalcPanel() { var value by remember { mutableStateOf("") }; Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(value.ifBlank { "0" }, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium); "123+4567890-×÷C=".chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { row.forEach { key -> val label = key.toString(); Button(onClick = { if (label == "C") value = "" else if (label == "=") value = value else value += label }, modifier = Modifier.weight(1f)) { Text(label) } } } } } }
@Composable private fun ClockPanel() { val time = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }; var now by remember { mutableStateOf(time.format(java.util.Date())) }; LaunchedEffect(Unit) { while (true) { now = time.format(java.util.Date()); kotlinx.coroutines.delay(1000) } }; Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) { Icon(Icons.Default.Schedule, null, Modifier.size(48.dp)); Text(now, style = MaterialTheme.typography.displaySmall); Text("Your local time", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun CalendarPanel() { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("October 2026", style = MaterialTheme.typography.titleLarge); Text("Today", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text("No events scheduled", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun MusicPanel() { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) { Icon(Icons.Default.Album, null, Modifier.size(64.dp), tint = Color(0xFFCF91FF)); Text("FreeFloat Radio", style = MaterialTheme.typography.titleMedium); Text("No track selected", color = MaterialTheme.colorScheme.onSurfaceVariant); Row { IconButton({}) { Icon(Icons.Default.SkipPrevious, "Previous") }; IconButton({}) { Icon(Icons.Default.PlayArrow, "Play") }; IconButton({}) { Icon(Icons.Default.SkipNext, "Next") } } } }
@Composable private fun SystemPanel() { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("System information", style = MaterialTheme.typography.titleMedium); Text("Android ${android.os.Build.VERSION.RELEASE}"); Text("Device: ${android.os.Build.MODEL}"); Text("SDK: ${android.os.Build.VERSION.SDK_INT}"); Text("Overlay workspace: ready") } }
@Composable private fun WebPanel() { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Language, null, Modifier.size(42.dp), tint = Color(0xFF9EA9FF)); Text("Web panel", style = MaterialTheme.typography.titleMedium); Text("A safe in-app panel placeholder. Open links in your browser when needed.", color = MaterialTheme.colorScheme.onSurfaceVariant); OutlinedButton(onClick = {}) { Icon(Icons.Default.OpenInBrowser, null); Spacer(Modifier.width(6.dp)); Text("Open browser") } } }

@Composable private fun SettingsSheet(vm: WorkspaceViewModel) { AlertDialog(onDismissRequest = vm::settings, title = { Text("Workspace settings") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Appearance", fontWeight = FontWeight.Bold); Row(verticalAlignment = Alignment.CenterVertically) { Text("Dark theme", Modifier.weight(1f)); Switch(vm.darkMode, { vm.toggleTheme() }) }; Text("Default transparency: ${(vm.defaultOpacity * 100).roundToInt()}%"); Slider(vm.defaultOpacity, { vm.updateDefaultOpacity(it) }, valueRange = .4f..1f); Text("Gesture: swipe from the extreme right edge, or use the sidebar button. Drag the three-dot header area to move windows and the bottom-right handle to resize.", style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton(onClick = vm::settings) { Text("Done") } }) }
