package com.example.terminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val activeTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    
    val installProgress by viewModel.installProgress.collectAsStateWithLifecycle()
    val installingPackage by viewModel.installingPackage.collectAsStateWithLifecycle()

    val currentSession = sessions.find { it.id == activeSessionId }

    // Always render with RTL layout for the Arabic app wrappers, but let terminal line align based on content
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(300.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Spacer(modifier = Modifier.statusBarsPadding())
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "جِلْسات الطرفية",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = {
                                viewModel.createNewSession()
                                scope.launch { drawerState.close() }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "جلسة جديدة")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        items(sessions) { session ->
                            val isActive = session.id == activeSessionId
                            NavigationDrawerItem(
                                label = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = session.name,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 15.sp
                                        )
                                        if (session.shellMode != ShellMode.STANDARD) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                            ) {
                                                Text(
                                                    text = when (session.shellMode) {
                                                        ShellMode.M_EDITOR -> "محرر"
                                                        ShellMode.INTERACTIVE_PYTHON -> "بايثون"
                                                        ShellMode.INTERACTIVE_JS -> "نود"
                                                        else -> ""
                                                    },
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                },
                                selected = isActive,
                                onClick = {
                                    viewModel.switchSession(session.id)
                                    scope.launch { drawerState.close() }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isActive) Icons.Default.Terminal else Icons.Outlined.Terminal,
                                        contentDescription = null
                                    )
                                },
                                badge = {
                                    if (sessions.size > 1) {
                                        IconButton(
                                            onClick = { viewModel.deleteSession(session.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "حذف الجلسة",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = currentSession?.name ?: "الطرفية العربية",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when (currentSession?.shellMode) {
                                        ShellMode.STANDARD -> "بيئة لينكس افتراضية"
                                        ShellMode.INTERACTIVE_PYTHON -> "مفسر بايثون تفاعلي"
                                        ShellMode.INTERACTIVE_JS -> "مفسر جافا سكريبت تفاعلي"
                                        ShellMode.M_EDITOR -> "محرر نصوص نشط"
                                        null -> ""
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "قائمة الجلسات")
                            }
                        },
                        actions = {
                            // Theme dialog trigger
                            var showThemeSelector by remember { mutableStateOf(false) }
                            IconButton(onClick = { showThemeSelector = true }) {
                                Icon(Icons.Default.Palette, contentDescription = "قوالب الألوان")
                            }

                            IconButton(
                                onClick = { viewModel.createNewSession() }
                            ) {
                                Icon(Icons.Default.AddBox, contentDescription = "تبويب جديد")
                            }

                            if (showThemeSelector) {
                                ThemeSelectorDialog(
                                    currentTheme = activeTheme,
                                    onThemeSelected = { viewModel.selectTheme(it) },
                                    onDismiss = { showThemeSelector = false }
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                containerColor = activeTheme.background,
                modifier = modifier.fillMaxSize()
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(activeTheme.background)
                ) {
                    if (currentSession != null) {
                        when (currentSession.shellMode) {
                            ShellMode.M_EDITOR -> {
                                EditorLayout(
                                    fileName = currentSession.activeEditorFile ?: "unnamed.txt",
                                    content = currentSession.editorContent,
                                    onContentChange = { viewModel.onEditorTextChanged(it) },
                                    onSave = { viewModel.saveEditorFile() },
                                    onExit = { viewModel.exitEditorWithoutSaving() },
                                    theme = activeTheme
                                )
                            }
                            else -> {
                                TerminalConsoleLayout(
                                    session = currentSession,
                                    theme = activeTheme,
                                    viewModel = viewModel
                                )
                            }
                        }
                    } else {
                        // Empty session state
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "يرجى إنشاء جلسة جديدة للبدء",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 16.sp
                            )
                        }
                    }

                    // Floating installer alert
                    AnimatedVisibility(
                        visible = installProgress >= 0,
                        enter = fadeIn(animationSpec = spring()),
                        exit = fadeOut(animationSpec = spring()),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 80.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            elevation = CardDefaults.cardElevation(8.dp),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .padding(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = "جاري تثبيت الحزمة: ${installingPackage}...",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { (installProgress / 100f) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$installProgress%",
                                    fontSize = 11.sp,
                                    modifier = Modifier.align(Alignment.End),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalConsoleLayout(
    session: TerminalSession,
    theme: TerminalTheme,
    viewModel: TerminalViewModel
) {
    val listState = rememberLazyListState()
    var inputVal by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    // Auto-scroll logic when content changes
    LaunchedEffect(session.buffer.size) {
        if (session.buffer.isNotEmpty()) {
            listState.animateScrollToItem(session.buffer.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(session.buffer) { line ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (line.isRtl) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        CompositionLocalProvider(
                            LocalLayoutDirection provides (if (line.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr)
                        ) {
                            Text(
                                text = line.annotatedString,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                ),
                                textAlign = if (line.isRtl) TextAlign.Right else TextAlign.Left,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        CustomTerminalKeyboard(
            inputVal = inputVal,
            onInputChange = { inputVal = it },
            history = session.commandHistory,
            onSend = {
                if (inputVal.isNotEmpty()) {
                    viewModel.onCommandEntered(inputVal)
                    inputVal = ""
                }
            },
            theme = theme,
            viewModel = viewModel,
            session = session
        )

        // Command Prompt Input Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(theme.background)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val prefix = when (session.shellMode) {
                ShellMode.STANDARD -> "$ "
                ShellMode.INTERACTIVE_PYTHON -> ">>> "
                ShellMode.INTERACTIVE_JS -> "> "
                else -> ""
            }

            Text(
                text = prefix,
                color = theme.primary,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                modifier = Modifier.padding(end = 4.dp)
            )

            TextField(
                value = inputVal,
                onValueChange = { inputVal = it },
                textStyle = TextStyle(
                    color = theme.textDefault,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputVal.isNotEmpty()) {
                            viewModel.onCommandEntered(inputVal)
                            inputVal = ""
                        }
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = theme.primary
                ),
                placeholder = {
                    Text(
                        text = "أدخل الأمر هنا...",
                        color = theme.textDefault.copy(alpha = 0.4f),
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier.weight(1f)
            )

            // Submit Button
            IconButton(
                onClick = {
                    if (inputVal.isNotEmpty()) {
                        viewModel.onCommandEntered(inputVal)
                        inputVal = ""
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = theme.secondary.copy(alpha = 0.2f),
                    contentColor = theme.secondary
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "إرسال",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun CustomTerminalKeyboard(
    inputVal: String,
    onInputChange: (String) -> Unit,
    history: List<String>,
    onSend: () -> Unit,
    theme: TerminalTheme,
    viewModel: TerminalViewModel,
    session: TerminalSession
) {
    var historyIndex by remember { mutableStateOf(-1) }

    LaunchedEffect(history.size) {
        historyIndex = -1
    }

    // Special button layout
    val buttons = listOf(
        "TAB" to { onInputChange(inputVal + "    ") },
        "ESC" to { onInputChange(inputVal + "\u001b") },
        "|" to { onInputChange(inputVal + " | ") },
        "-" to { onInputChange(inputVal + "-") },
        "HELP" to { viewModel.onCommandEntered("مساعدة") },
        "CLEAR" to { viewModel.onCommandEntered("تنظيف") },
        "↑" to {
            if (history.isNotEmpty()) {
                if (historyIndex == -1) {
                    historyIndex = history.size - 1
                } else if (historyIndex > 0) {
                    historyIndex--
                }
                onInputChange(history[historyIndex])
            }
        },
        "↓" to {
            if (history.isNotEmpty() && historyIndex != -1) {
                if (historyIndex < history.size - 1) {
                    historyIndex++
                    onInputChange(history[historyIndex])
                } else {
                    historyIndex = -1
                    onInputChange("")
                }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.background.copy(alpha = 0.85f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            buttons.take(4).forEach { (label, action) ->
                KeyButton(label, theme, action, modifier = Modifier.weight(1f))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            buttons.drop(4).forEach { (label, action) ->
                KeyButton(label, theme, action, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun KeyButton(
    label: String,
    theme: TerminalTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(theme.textDefault.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = theme.textDefault,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun EditorLayout(
    fileName: String,
    content: String,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onExit: () -> Unit,
    theme: TerminalTheme
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Editor Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(theme.textDefault.copy(alpha = 0.1f))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = "تعديل: $fileName",
                    color = theme.textDefault,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Discard Button
                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.error.copy(alpha = 0.2f),
                        contentColor = theme.error
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("خروج", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                // Save Button
                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary.copy(alpha = 0.2f),
                        contentColor = theme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("حفظ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Large Editor field
        TextField(
            value = content,
            onValueChange = onContentChange,
            textStyle = TextStyle(
                color = theme.textDefault,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = theme.background.copy(alpha = 0.3f),
                unfocusedContainerColor = theme.background.copy(alpha = 0.3f),
                focusedIndicatorColor = theme.primary.copy(alpha = 0.5f),
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = theme.primary
            ),
            placeholder = {
                Text(
                    text = "اكتب الكود أو النص هنا...",
                    color = theme.textDefault.copy(alpha = 0.3f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
        )
    }
}

@Composable
fun ThemeSelectorDialog(
    currentTheme: TerminalTheme,
    onThemeSelected: (TerminalTheme) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "اختر مظهر الطرفية",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TerminalTheme.values().forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (theme == currentTheme) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else Color.Transparent
                            )
                            .clickable {
                                onThemeSelected(theme)
                                onDismiss()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // color preview dots
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(theme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(theme.textDefault)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = theme.arabicName,
                            fontSize = 14.sp,
                            fontWeight = if (theme == currentTheme) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
