package com.example.mytexteditor.ui.editor

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mytexteditor.data.AppLog
import com.example.mytexteditor.data.CodeRunner
import com.example.mytexteditor.data.FileUtils
import com.example.mytexteditor.data.TerminalSession
import com.example.mytexteditor.data.ToolchainInstaller
import com.example.mytexteditor.model.Direction
import com.example.mytexteditor.model.RightMode
import com.example.mytexteditor.state.rememberEditorState
import com.example.mytexteditor.ui.components.HeaderActionPill
import com.example.mytexteditor.ui.theme.EditorColors
import kotlinx.coroutines.launch

@Composable
fun EditorScreen() {
    val state = rememberEditorState()
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // ================= TOOLCHAIN & LSP STATE =================
    var isToolchainReady by remember { mutableStateOf(ToolchainInstaller.isInstalled(context)) }
    var toolchainStatus by remember { mutableStateOf("Đang chuẩn bị...") }

    // Tự động kiểm tra và tải Clang khi mở app
    LaunchedEffect(Unit) {
        if (!isToolchainReady) {
            ToolchainInstaller.installIfNeeded(
                context = context,
                onStatusUpdate = { status ->
                    toolchainStatus = status
                },
                onComplete = {
                    isToolchainReady = true
                }
            )
        }
    }

    // Khi Toolchain sẵn sàng -> Khởi chạy LSP Clangd
    LaunchedEffect(isToolchainReady) {
        if (isToolchainReady) {
            state.initLsp(context)
        }
    }

    // Bắt cử chỉ Back của Android để thoát Zen Mode
    BackHandler(enabled = state.isZenMode) {
        state.isZenMode = false
    }

    LaunchedEffect(Unit) {
        state.syntaxHighlighter.updateText(state.textFieldValue.text)
    }

    // RUNNER STATE
    var isRunningCode by remember { mutableStateOf(false) }
    var isConsoleVisible by remember { mutableStateOf(false) }
    var runJob: kotlinx.coroutines.Job? by remember { mutableStateOf(null) }
    val isTerminalRunning by TerminalSession.isRunning.collectAsState()
    val isRunning = isRunningCode || isTerminalRunning

    val executeCodeAction: (String) -> Unit = { _ ->
        AppLog.i("EDITOR_UI", "▶️ Bắt đầu Build & Chạy C++")
        isRunningCode = true
        isConsoleVisible = true

        runJob = coroutineScope.launch {
            try {
                // 1. BIÊN DỊCH BẰNG CLANG
                val compileRes = CodeRunner.compileCpp(state.textFieldValue.text)

                if (compileRes.isError) {
                    TerminalSession.showError(compileRes.output)
                    isRunningCode = false
                    AppLog.e("EDITOR_UI", "❌ Biên dịch thất bại: ${compileRes.output}")
                } else {
                    // 2. BIÊN DỊCH XONG -> CHẠY LIVE SESSION NGAY
                    val cache = CodeRunner.cacheDir
                    val usr = CodeRunner.filesDir?.let { java.io.File(it, "usr") }
                    val outputFile = java.io.File(cache, "solution.out")

                    if (cache != null && usr != null) {
                        TerminalSession.start(outputFile, usr, this)
                        isRunningCode = false
                    }
                }
            } catch (e: Exception) {
                TerminalSession.showError("Lỗi hệ thống: ${e.message}")
                isRunningCode = false
            }
        }
    }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showMenuDialog by remember { mutableStateOf(false) }
    var pendingNewFileAfterSave by remember { mutableStateOf(false) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            AppLog.i("FILE_OPS", "📂 Người dùng chọn mở file: $uri")
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                } ?: ""
                val name = FileUtils.getFileName(context, uri) ?: "untitled.cpp"
                state.currentUri = uri
                state.fileName = name
                state.updateTextFieldValue(TextFieldValue(content, TextRange(0)), markModified = false)
                state.syntaxHighlighter.updateText(content)
                state.clearHistory()
                state.isModified = false
                AppLog.i("FILE_OPS", "✅ Đã tải file: $name (${content.length} ký tự)")
                Toast.makeText(context, "Đã mở $name", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                AppLog.e("FILE_OPS", "❌ Lỗi khi đọc file: ${e.message}", e)
                Toast.makeText(context, "Lỗi mở file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri != null) {
            AppLog.i("FILE_OPS", "💾 Đang ghi ra file mới: $uri")
            val success = FileUtils.saveToFile(context, uri, state.textFieldValue.text)
            if (success) {
                val name = FileUtils.getFileName(context, uri) ?: state.fileName
                AppLog.i("FILE_OPS", "✅ Đã lưu thành công file: $name")
                if (pendingNewFileAfterSave) {
                    pendingNewFileAfterSave = false
                    state.currentUri = null
                    state.fileName = "untitled.cpp"
                    state.updateTextFieldValue(TextFieldValue("", TextRange(0)), markModified = false)
                    state.clearHistory()
                    state.isModified = false
                } else {
                    state.currentUri = uri
                    state.fileName = name
                    state.isModified = false
                    Toast.makeText(context, "Đã lưu $name!", Toast.LENGTH_SHORT).show()
                }
            } else {
                pendingNewFileAfterSave = false
                AppLog.e("FILE_OPS", "❌ Ghi file thất bại: $uri")
                Toast.makeText(context, "Lỗi lưu file!", Toast.LENGTH_SHORT).show()
            }
        } else {
            pendingNewFileAfterSave = false
        }
    }

    val onSaveAction = {
        AppLog.d("FILE_OPS", "💾 Yêu cầu Lưu file: ${state.fileName} (Uri=${state.currentUri})")
        if (state.currentUri != null) {
            val success = FileUtils.saveToFile(context, state.currentUri!!, state.textFieldValue.text)
            if (success) {
                state.isModified = false
                AppLog.i("FILE_OPS", "✅ Đã lưu file: ${state.fileName}")
                Toast.makeText(context, "Đã lưu!", Toast.LENGTH_SHORT).show()
            } else {
                AppLog.e("FILE_OPS", "❌ Lỗi lưu file: ${state.fileName}")
                Toast.makeText(context, "Lỗi lưu file!", Toast.LENGTH_SHORT).show()
            }
        } else {
            createDocumentLauncher.launch(state.fileName)
        }
    }

    val onSaveAsAction = {
        AppLog.d("FILE_OPS", "💾 Yêu cầu Lưu file mới (Save As): ${state.fileName}")
        createDocumentLauncher.launch(state.fileName)
    }

    val onNewFileAction = {
        AppLog.d("FILE_OPS", "📄 Yêu cầu Tạo file mới (isModified=${state.isModified})")
        if (state.isModified) {
            showNewFileDialog = true
        } else {
            state.currentUri = null
            state.fileName = "untitled.cpp"
            state.updateTextFieldValue(TextFieldValue("", TextRange(0)), markModified = false)
            state.clearHistory()
            state.isModified = false
            Toast.makeText(context, "Đã tạo file mới", Toast.LENGTH_SHORT).show()
        }
    }

    val onOpenFileAction = {
        AppLog.d("FILE_OPS", "📂 Yêu cầu Mở file (Open File Picker)")
        openDocumentLauncher.launch(arrayOf("*/*"))
    }

    val onMenuAction = {
        showMenuDialog = true
    }

    // ================= DIALOG TẠO FILE MỚI =================
    if (showNewFileDialog) {
        Dialog(
            onDismissRequest = { showNewFileDialog = false },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF282828))
                    .border(BorderStroke(1.dp, Color(0xFF3E3E3E)), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(text = "Tạo file mới", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Bạn có muốn lưu file hiện tại trước khi tạo mới không?", color = Color(0xFFCCCCCC), fontSize = 14.sp, lineHeight = 20.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeaderActionPill(text = "Hủy", onClick = { showNewFileDialog = false })
                        Spacer(modifier = Modifier.width(8.dp))
                        HeaderActionPill(
                            text = "Không lưu",
                            bgColor = Color(0xFF552222),
                            onClick = {
                                showNewFileDialog = false
                                state.currentUri = null
                                state.fileName = "untitled.cpp"
                                state.updateTextFieldValue(TextFieldValue("", TextRange(0)), markModified = false)
                                state.clearHistory()
                                state.isModified = false
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        HeaderActionPill(
                            text = "Lưu",
                            isAccent = true,
                            onClick = {
                                showNewFileDialog = false
                                if (state.currentUri != null) {
                                    val success = FileUtils.saveToFile(context, state.currentUri!!, state.textFieldValue.text)
                                    if (success) {
                                        state.currentUri = null
                                        state.fileName = "untitled.cpp"
                                        state.updateTextFieldValue(TextFieldValue("", TextRange(0)), markModified = false)
                                        state.clearHistory()
                                        state.isModified = false
                                    }
                                } else {
                                    pendingNewFileAfterSave = true
                                    createDocumentLauncher.launch(state.fileName)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // ================= MENU DIALOG GNOME (TÍCH HỢP NÚT CẬP NHẬT TOOLCHAIN CLANGD) =================
    if (showMenuDialog) {
        val isClangdInstalled = ToolchainInstaller.isClangdInstalled(context)

        Dialog(
            onDismissRequest = { showMenuDialog = false },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 300.dp, max = 380.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF242424))
                    .border(BorderStroke(1.dp, Color(0xFF3E3E3E)), RoundedCornerShape(18.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Menu Pudding IDE",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Trạng thái IntelliSense Clangd
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E1E1E))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "IntelliSense (clangd)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isClangdInstalled) "Hoạt động (Auto-completion + Signature)" else "Chưa có binary clangd",
                                color = if (isClangdInstalled) Color(0xFF2EC27E) else Color(0xFFF38BA8),
                                fontSize = 11.sp
                            )
                        }

                        if (!isClangdInstalled) {
                            HeaderActionPill(
                                text = "Cập nhật",
                                isAccent = true,
                                onClick = {
                                    showMenuDialog = false
                                    isToolchainReady = false
                                    toolchainStatus = "Đang kết nối lại GitHub..."
                                    ToolchainInstaller.reinstall(
                                        context = context,
                                        onStatusUpdate = { status -> toolchainStatus = status },
                                        onComplete = {
                                            isToolchainReady = true
                                            state.initLsp(context)
                                        }
                                    )
                                }
                            )
                        }
                    }

                    // Tùy chọn file nhanh
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E1E1E))
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showMenuDialog = false
                                    onNewFileAction()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Tạo file mới", color = Color.White, fontSize = 13.sp)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showMenuDialog = false
                                    onOpenFileAction()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Mở file...", color = Color.White, fontSize = 13.sp)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showMenuDialog = false
                                    onSaveAction()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Lưu file", color = Color.White, fontSize = 13.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        HeaderActionPill(text = "Đóng", onClick = { showMenuDialog = false })
                    }
                }
            }
        }
    }

    val onKeyAction = remember(state, context, view, isConsoleVisible, isTerminalRunning) {
        { key: String ->
            AppLog.d("KEYBOARD", "⌨️ Key: '$key' | Cursor: ${state.textFieldValue.selection.start}")

            if (isConsoleVisible && isTerminalRunning) {
                when (key) {
                    "ENTER" -> {
                        AppLog.i("STDIN", "↵ Gửi STDIN vào C++: '${TerminalSession.currentStdin.value}'")
                        TerminalSession.submitStdin()
                    }
                    "BACKSPACE" -> {
                        TerminalSession.backspaceStdin()
                    }
                    "SPACE" -> {
                        TerminalSession.appendStdin(" ")
                    }
                    "TAB" -> {
                        TerminalSession.appendStdin("    ")
                    }
                    else -> {
                        if (key.length == 1) {
                            TerminalSession.appendStdin(key)
                        } else {
                            state.onKey(key, context)
                        }
                    }
                }
            } else {
                state.onKey(key, context)
            }
        }
    }

    val onJoystickAction = remember(state, haptic) {
        { direction: Direction ->
            AppLog.d("JOYSTICK", "🕹️ Joystick: $direction")
            state.onJoystickDirection(direction, haptic)
        }
    }

    val onToggleModeAction = remember(state, haptic) {
        { mode: RightMode ->
            AppLog.d("EDITOR_UI", "🔀 RightMode: $mode")
            state.rightMode = mode
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // GNOME Split Container
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(EditorColors.workspace)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(if (state.isZenMode) 0.dp else 8.dp)
        ) {
            // LEFT PANEL (Ẩn khi Zen Mode)
            AnimatedVisibility(
                visible = !state.isZenMode,
                enter = expandHorizontally(tween(180)) + fadeIn(tween(150)),
                exit = shrinkHorizontally(tween(180)) + fadeOut(tween(150)),
                modifier = Modifier.weight(0.27f).fillMaxHeight()
            ) {
                LeftPanel(
                    isShiftMode = state.isShiftMode,
                    isSymbolMode = state.isSymbolMode,
                    isCtrlPressed = state.isCtrlPressed,
                    onKey = onKeyAction,
                    onNewFile = onNewFileAction,
                    onOpenFile = onOpenFileAction
                )
            }

            // CENTER EDITOR PANEL (Tràn viền 100% khi Zen Mode)
            CenterEditorPanel(
                state = state,
                isConsoleVisible = isConsoleVisible,
                onCloseConsole = { isConsoleVisible = false },
                modifier = Modifier
                    .weight(if (state.isZenMode) 1f else 0.46f)
                    .fillMaxHeight()
            )

            // RIGHT PANEL (Ẩn khi Zen Mode)
            AnimatedVisibility(
                visible = !state.isZenMode,
                enter = expandHorizontally(tween(180)) + fadeIn(tween(150)),
                exit = shrinkHorizontally(tween(180)) + fadeOut(tween(150)),
                modifier = Modifier.weight(0.27f).fillMaxHeight()
            ) {
                RightPanel(
                    rightMode = state.rightMode,
                    isShiftMode = state.isShiftMode,
                    isSymbolMode = state.isSymbolMode,
                    isRunningCode = isRunning,
                    onKey = onKeyAction,
                    onJoystickDirection = onJoystickAction,
                    onToggleMode = onToggleModeAction,
                    onSave = onSaveAction,
                    onSaveAs = onSaveAsAction,
                    onRun = { executeCodeAction("") },
                    onStop = {
                        runJob?.cancel()
                        TerminalSession.stop()
                        isRunningCode = false
                    },
                    onToggleZen = { state.isZenMode = true },
                    onMenu = onMenuAction
                )
            }
        }

        // ================= POPUP TIẾN TRÌNH CÀI ĐẶT CLANG TRÊN MÀN HÌNH =================
        if (!isToolchainReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF242424))
                        .border(BorderStroke(1.dp, Color(0xFF3E3E3E)), RoundedCornerShape(20.dp))
                        .padding(horizontal = 28.dp, vertical = 24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val isError = toolchainStatus.startsWith("Lỗi")
                        if (!isError) {
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    android.widget.ProgressBar(ctx).apply {
                                        indeterminateTintList = android.content.res.ColorStateList.valueOf(
                                            android.graphics.Color.parseColor("#64B5F6")
                                        )
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Text(
                            text = "Khởi tạo môi trường C/C++",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = toolchainStatus,
                            color = if (isError) Color(0xFFEF5350) else Color(0xFFB0BEC5),
                            fontSize = 14.sp
                        )
                        if (isError) {
                            androidx.compose.material3.Button(
                                onClick = {
                                    toolchainStatus = "Đang kết nối lại..."
                                    ToolchainInstaller.installIfNeeded(
                                        context = context,
                                        onStatusUpdate = { status -> toolchainStatus = status },
                                        onComplete = {
                                            isToolchainReady = true
                                            state.initLsp(context)
                                        }
                                    )
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1976D2)
                                )
                            ) {
                                Text("Thử lại", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}