package com.example.terminal

import android.app.Application
import androidx.compose.ui.text.buildAnnotatedString
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String>("")
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    private val _selectedTheme = MutableStateFlow(TerminalTheme.GREEN_HACKER)
    val selectedTheme: StateFlow<TerminalTheme> = _selectedTheme.asStateFlow()

    // Interactive installation progress (-1 means not installing)
    private val _installProgress = MutableStateFlow<Int>(-1)
    val installProgress: StateFlow<Int> = _installProgress.asStateFlow()

    private val _installingPackage = MutableStateFlow<String>("")
    val installingPackage: StateFlow<String> = _installingPackage.asStateFlow()

    // Interactive python variable storage
    private val pythonVariables = mutableMapOf<String, String>()
    // Interactive node JS memory calculations
    private val nodeVariables = mutableMapOf<String, String>()

    init {
        // Create initial default sessions
        createNewSession("جلسة 1")
    }

    val activeSession: TerminalSession?
        get() = _sessions.value.find { it.id == _activeSessionId.value }

    fun selectTheme(theme: TerminalTheme) {
        _selectedTheme.value = theme
        // Clear and rebuild session buffers with new parsed colors
        val updated = _sessions.value.map { session ->
            val rebuiltLines = session.buffer.map { line ->
                line.copy(annotatedString = AnsiColorParser.parse(line.rawText, theme))
            }
            session.copy(buffer = rebuiltLines)
        }
        _sessions.value = updated
    }

    fun createNewSession(customName: String? = null) {
        val nextNum = _sessions.value.size + 1
        val name = customName ?: "جلسة $nextNum"
        val newSession = TerminalSession(name = name)
        
        // Initial welcome messages in Arabic
        val welcomeLines = listOf(
            "\u001b[1;32m=== محاكي الطرفية العربية (ArabicTerm) ===\u001b[0m",
            "\u001b[33mمرحباً بك في بيئة مطوّري الأنظمة المدمجة الذكية!\u001b[0m",
            "نسخة المحاكي: \u001b[36m1.0.0-RTL\u001b[0m | تاريخ التشغيل: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
            "نوع النظام: \u001b[34mأندرويد عربي مدمج\u001b[0m",
            "--------------------------------------------",
            "اكتب \u001b[1;33mمساعدة\u001b[0m أو \u001b[1;33mhelp\u001b[0m لعرض قائمة بالأوامر المتاحة.",
            "قم بتثبيت لغات البرمجة بكتابة \u001b[35mapt install python\u001b[0m أو \u001b[35mapt install nodejs\u001b[0m",
            "ويمكنك استخدام المحرر المدمج بكتابة \u001b[36mnano اسم_الملف\u001b[0m",
            "--------------------------------------------",
            ""
        )

        val initialBuffer = welcomeLines.map {
            TerminalLine(
                rawText = it,
                isRtl = isArabic(it),
                annotatedString = AnsiColorParser.parse(it, _selectedTheme.value)
            )
        }

        val sessionWithWelcome = newSession.copy(buffer = initialBuffer)
        _sessions.value = _sessions.value + sessionWithWelcome
        _activeSessionId.value = sessionWithWelcome.id
    }

    fun deleteSession(sessionId: String) {
        val currentList = _sessions.value
        if (currentList.size <= 1) {
            // Cannot delete last session, just clear it
            val cleared = currentList.map {
                if (it.id == sessionId) {
                    it.copy(buffer = emptyList())
                } else it
            }
            _sessions.value = cleared
            return
        }

        val index = currentList.indexOfFirst { it.id == sessionId }
        val updated = currentList.filter { it.id != sessionId }
        _sessions.value = updated

        if (_activeSessionId.value == sessionId) {
            val newActiveIndex = if (index >= updated.size) updated.size - 1 else index
            _activeSessionId.value = updated[newActiveIndex].id
        }
    }

    fun switchSession(sessionId: String) {
        _activeSessionId.value = sessionId
    }

    /**
     * Main entry point to process terminal input string
     */
    fun onCommandEntered(input: String) {
        val trimmedInput = input.trim()
        if (trimmedInput.isEmpty()) return

        val session = activeSession ?: return

        // Save command to history
        val updatedHistory = session.commandHistory + trimmedInput
        
        // Output prompt command line visually before processing
        val promptChar = when (session.shellMode) {
            ShellMode.STANDARD -> "$"
            ShellMode.INTERACTIVE_PYTHON -> ">>>"
            ShellMode.INTERACTIVE_JS -> ">"
            ShellMode.M_EDITOR -> ""
        }
        
        appendLineToSession(session.id, "$promptChar $trimmedInput")

        // Update session's history
        updateSessionState(session.id) { it.copy(commandHistory = updatedHistory) }

        when (session.shellMode) {
            ShellMode.STANDARD -> processStandardCommand(session, trimmedInput)
            ShellMode.INTERACTIVE_PYTHON -> processPythonInput(session, trimmedInput)
            ShellMode.INTERACTIVE_JS -> processJsInput(session, trimmedInput)
            ShellMode.M_EDITOR -> {
                // Should not happen as input is caught by editor layout, but reset if any
            }
        }
    }

    private fun processStandardCommand(session: TerminalSession, cmdLine: String) {
        val parts = cmdLine.split("\\s+".toRegex())
        if (parts.isEmpty()) return
        val primaryCmd = parts[0]

        when (primaryCmd) {
            "help", "مساعدة" -> {
                val helpText = """
                    ${getAnsiColored("[قائمة الأوامر العربية والانجليزية المدعومة]", "1;33")}
                    ---------------------------------------------
                    ${getAnsiColored("مساعدة / help", "32")}      : عرض شاشة المساعدة هذه.
                    ${getAnsiColored("تنظيف / clear", "32")}      : مسح الشاشة بالكامل.
                    ${getAnsiColored("عرض / ls", "32")}           : عرض الملفات المخزنة في هذا النظام الافتراضي.
                    ${getAnsiColored("قراءة / cat <ملف>", "32")}   : قراءة محتويات ملف نصي.
                    ${getAnsiColored("محرر / nano <ملف>", "32")}   : فتح المحرر المدمج لإنشاء وتعديل الملفات.
                    ${getAnsiColored("حذف / rm <ملف>", "32")}     : حذف ملف محدد.
                    ${getAnsiColored("اطبع / echo <نص>", "32")}    : طباعة النص المدخل في الطرفية.
                    ${getAnsiColored("تحديث / apt update", "32")} : تحديث مستودعات الحزم.
                    ${getAnsiColored("apt install python", "32")} : تثبيت بيئة بايثون التفاعلية.
                    ${getAnsiColored("apt install nodejs", "32")} : تثبيت بيئة جافا سكريبت التفاعلية.
                    ${getAnsiColored("python / بايثون", "32")}  : الدخول لمفسر لغة البايثون (يتطلب تثبيت).
                    ${getAnsiColored("node / نود", "32")}        : الدخول لمفسر جافا سكريبت (يتطلب تثبيت).
                    ${getAnsiColored("تاريخ / history", "32")}    : عرض تاريخ الأوامر السابقة.
                    ${getAnsiColored("خروج / exit", "32")}        : إغلاق الجلسة الحالية.
                    
                    ${getAnsiColored("* بالإضافة لجميع أوامر نظام التشغيل أندرويد الحقيقية مثل:", "90")}
                    ${getAnsiColored("  uname -a, ping -c 2 google.com, getprop, date, env, toybox", "36")}
                    ---------------------------------------------
                """.trimIndent()
                appendLineToSession(session.id, helpText)
            }
            "clear", "تنظيف" -> {
                updateSessionState(session.id) { it.copy(buffer = emptyList()) }
            }
            "ls", "عرض" -> {
                if (session.fileSystem.isEmpty()) {
                    appendLineToSession(session.id, "\u001b[90m(الدليل فارغ)\u001b[0m")
                } else {
                    val filesList = session.fileSystem.keys.joinToString("   ") { filename ->
                        val isScript = filename.endsWith(".sh") || filename.endsWith(".py")
                        if (isScript) "\u001b[1;32m$filename\u001b[0m" else "\u001b[36m$filename\u001b[0m"
                    }
                    appendLineToSession(session.id, filesList)
                }
            }
            "cat", "قراءة" -> {
                if (parts.size < 2) {
                    appendLineToSession(session.id, "\u001b[31mخطأ: يرجى تحديد اسم الملف. مثال: cat test.py\u001b[0m")
                } else {
                    val filename = parts[1]
                    val content = session.fileSystem[filename]
                    if (content != null) {
                        appendLineToSession(session.id, content)
                    } else {
                        appendLineToSession(session.id, "\u001b[31mخطأ: الملف '$filename' غير موجود.\u001b[0m")
                    }
                }
            }
            "nano", "محرر" -> {
                val filename = if (parts.size >= 2) parts[1] else "unnamed.txt"
                val existingContent = session.fileSystem[filename] ?: ""
                updateSessionState(session.id) {
                    it.copy(
                        shellMode = ShellMode.M_EDITOR,
                        activeEditorFile = filename,
                        editorContent = existingContent
                    )
                }
            }
            "rm", "حذف" -> {
                if (parts.size < 2) {
                    appendLineToSession(session.id, "\u001b[31mخطأ: يجب تحديد اسم الملف للحذف.\u001b[0m")
                } else {
                    val filename = parts[1]
                    if (session.fileSystem.containsKey(filename)) {
                        val newFS = session.fileSystem.toMutableMap()
                        newFS.remove(filename)
                        updateSessionState(session.id) { it.copy(fileSystem = newFS) }
                        appendLineToSession(session.id, "\u001b[32mتم حذف الملف '$filename' بنجاح.\u001b[0m")
                    } else {
                        appendLineToSession(session.id, "\u001b[31mخطأ: الملف '$filename' لا وجود له.\u001b[0m")
                    }
                }
            }
            "echo", "اطبع" -> {
                val text = parts.drop(1).joinToString(" ")
                appendLineToSession(session.id, text)
            }
            "apt", "pkg" -> {
                handleAptCommand(session, parts)
            }
            "python", "بايثون" -> {
                if (session.packageStatus["python"] == true) {
                    appendLineToSession(session.id, "\u001b[1;36mبيئة مفسر بايثون العربي 3.10 التفاعلية مجاناً\u001b[0m")
                    appendLineToSession(session.id, "اكتب \u001b[33mexit()\u001b[0m أو \u001b[33mخروج\u001b[0m للعودة للطرفية الأساسية.")
                    updateSessionState(session.id) { it.copy(shellMode = ShellMode.INTERACTIVE_PYTHON) }
                } else {
                    appendLineToSession(session.id, "\u001b[31mعذراً: حزمة 'python' غير مثبتة. قم بتثبيتها أولاً باستخدام الأمر:\u001b[0m")
                    appendLineToSession(session.id, "\u001b[1;33mapt install python\u001b[0m")
                }
            }
            "node", "nodejs", "نود" -> {
                if (session.packageStatus["nodejs"] == true) {
                    appendLineToSession(session.id, "\u001b[1;34mبيئة مفسر Node.js العربية الذكية\u001b[0m")
                    appendLineToSession(session.id, "اكتب \u001b[33mexit\u001b[0m للعودة للطرفية.")
                    updateSessionState(session.id) { it.copy(shellMode = ShellMode.INTERACTIVE_JS) }
                } else {
                    appendLineToSession(session.id, "\u001b[31mعذراً: حزمة 'nodejs' غير مثبتة. قم بتثبيتها أولاً باستخدام الأمر:\u001b[0m")
                    appendLineToSession(session.id, "\u001b[1;33mapt install nodejs\u001b[0m")
                }
            }
            "history", "تاريخ" -> {
                session.commandHistory.forEachIndexed { i, oldCmd ->
                    appendLineToSession(session.id, "  ${i + 1}  $oldCmd")
                }
            }
            "exit", "خروج" -> {
                deleteSession(session.id)
            }
            else -> {
                // Since this command is not a simulated command, run it as a real system shell command on the device sandbox!
                runRealSystemCommand(session.id, cmdLine)
            }
        }
    }

    private fun handleAptCommand(session: TerminalSession, parts: List<String>) {
        if (parts.size < 2) {
            appendLineToSession(session.id, "استخدام الأداة: apt [update / install] [اسم_الحزمة]")
            return
        }

        val action = parts[1]
        if (action == "update") {
            // Simulate package update
            viewModelScope.launch {
                appendLineToSession(session.id, "\u001b[33mجاري جلب قائمة المستودعات من خوادم الطرفية العربية...\u001b[0m")
                _installingPackage.value = "مستودعات الطرفية"
                _installProgress.value = 0
                for (progress in 10..100 step 15) {
                    kotlinx.coroutines.delay(200)
                    _installProgress.value = progress
                }
                _installProgress.value = -1
                appendLineToSession(session.id, "جلب \u001b[32mhttp://packages.arabicterm.com/main\u001b[0m بنجاح.")
                appendLineToSession(session.id, "\u001b[1;32mتم تحديث قائمة الحزم بنجاح! جاهز للتثبيت.\u001b[0m")
            }
        } else if (action == "install") {
            if (parts.size < 3) {
                appendLineToSession(session.id, "\u001b[31mخطأ: يرجى تحديد اسم الحزمة لتثبيتها. مثال: apt install python\u001b[0m")
                return
            }
            val pkg = parts[2].lowercase()
            if (pkg != "python" && pkg != "nodejs") {
                appendLineToSession(session.id, "\u001b[31mخطأ: الحزمة '$pkg' غير متوفرة في مستودعات أندرويد حالياً. الحزم المتاحة: (python, nodejs)\u001b[0m")
                return
            }

            if (session.packageStatus[pkg] == true) {
                appendLineToSession(session.id, "\u001b[32mالحزمة '$pkg' مثبتة بالفعل مسبقاً وتعمل بأحدث إصدار.\u001b[0m")
                return
            }

            // Simulate package installation
            viewModelScope.launch {
                appendLineToSession(session.id, "\u001b[33mجاري تهيئة بيئة التحميل وتخويل الصلاحيات لحزمة $pkg...\u001b[0m")
                _installingPackage.value = pkg
                _installProgress.value = 0
                for (p in 5..100 step 12) {
                    kotlinx.coroutines.delay(250)
                    _installProgress.value = p
                    if (p == 29) {
                        appendLineToSession(session.id, "جاري تنزيل ملفات الحزمة: $pkg-binaries.tar.gz [${p}%]")
                    } else if (p == 65) {
                        appendLineToSession(session.id, "جاري تفريغ وفك الضغط في دليل اللينكس الافتراضي: /data/data/com.arabicterm/files/usr/lib/")
                    } else if (p > 90) {
                        appendLineToSession(session.id, "جاري إعداد المتغيرات والروابط الرمزية (symlinks) لـ $pkg...")
                    }
                }
                _installProgress.value = -1
                
                val updatedStatus = session.packageStatus.toMutableMap()
                updatedStatus[pkg] = true
                updateSessionState(session.id) { it.copy(packageStatus = updatedStatus) }

                appendLineToSession(
                    session.id,
                    "\u001b[1;32m✓ تم تثبيتها بنجاح! اطلب الأداة عن طريق كتابة: $pkg\u001b[0m"
                )
            }
        }
    }

    private fun processPythonInput(session: TerminalSession, input: String) {
        val trimmed = input.trim()
        if (trimmed == "exit()" || trimmed == "خروج") {
            appendLineToSession(session.id, "\u001b[33mتم الخروج من مفسر بايثون العودة للطرفية.\u001b[0m")
            updateSessionState(session.id) { it.copy(shellMode = ShellMode.STANDARD) }
            return
        }

        try {
            val result = interpretPythonCode(trimmed)
            appendLineToSession(session.id, result)
        } catch (e: Exception) {
            appendLineToSession(session.id, "\u001b[31mSyntaxError: صيغة بايثون غير صائبة: ${e.message}\u001b[0m")
        }
    }

    private fun processJsInput(session: TerminalSession, input: String) {
        val trimmed = input.trim()
        if (trimmed == "exit" || trimmed == "خروج") {
            appendLineToSession(session.id, "\u001b[33mتم الخروج من مفسر Node.js.\u001b[0m")
            updateSessionState(session.id) { it.copy(shellMode = ShellMode.STANDARD) }
            return
        }

        try {
            val result = interpretJsCode(trimmed)
            appendLineToSession(session.id, result)
        } catch (e: Exception) {
            appendLineToSession(session.id, "\u001b[31mUncaught ReferenceError: ${e.message}\u001b[0m")
        }
    }

    /**
     * A simulated python parser allowing math, prints & simple assignments
     */
    private fun interpretPythonCode(code: String): String {
        // pattern for print(...)
        if (code.startsWith("print(") && code.endsWith(")")) {
            var content = code.substring(6, code.length - 1).trim()
            if ((content.startsWith("'") && content.endsWith("'")) || (content.startsWith("\"") && content.endsWith("\""))) {
                content = content.substring(1, content.length - 1)
            }
            return content
        }

        // pattern for variable assignment e.g. x = 2
        if (code.contains("=")) {
            val split = code.split("=", limit = 2)
            val varName = split[0].trim()
            val expr = split[1].trim()
            val evalValue = evaluateMathExpression(expr, pythonVariables)
            pythonVariables[varName] = evalValue
            return ""
        }

        // Else try evaluating as standard math expression
        return evaluateMathExpression(code, pythonVariables)
    }

    private fun interpretJsCode(code: String): String {
        if (code.startsWith("console.log(") && code.endsWith(")")) {
            var content = code.substring(12, code.length - 1).trim()
            if ((content.startsWith("'") && content.endsWith("'")) || (content.startsWith("\"") && content.endsWith("\""))) {
                content = content.substring(1, content.length - 1)
            }
            return content
        }

        if (code.startsWith("let ") || code.startsWith("var ") || code.startsWith("const ")) {
            val statement = code.substring(4).trim()
            val split = statement.split("=", limit = 2)
            val varName = split[0].trim()
            val expr = split[1].trim()
            val evalValue = evaluateMathExpression(expr, nodeVariables)
            nodeVariables[varName] = evalValue
            return evalValue
        }

        return evaluateMathExpression(code, nodeVariables)
    }

    private fun evaluateMathExpression(expr: String, vars: Map<String, String>): String {
        // Replace variables
        var parsedExpr = expr
        vars.forEach { (k, v) ->
            parsedExpr = parsedExpr.replace(k, v)
        }

        // simple evaluation
        parsedExpr = parsedExpr.replace(" ", "")
        if (parsedExpr.all { it.isDigit() || it == '+' || it == '-' || it == '*' || it == '/' || it == '(' || it == ')' || it == '.' }) {
            try {
                val value = SimpleMathEvaluator.evaluate(parsedExpr)
                return if (value % 1 == 0.0) value.toInt().toString() else value.toString()
            } catch (e: java.lang.Exception) {
                return "\u001b[31mخطأ في التقييم الرياضي للتعبير\u001b[0m"
            }
        }
        return parsedExpr
    }

    /**
     * Executes real shell commands via ProcessBuilder
     */
    private fun runRealSystemCommand(sessionId: String, cmdLine: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure real execute process command
                val process = ProcessBuilder()
                    .command("sh", "-c", cmdLine)
                    .redirectErrorStream(true)
                    .start()

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String? = reader.readLine()
                var linesCount = 0

                while (line != null) {
                    appendLineToSession(sessionId, line)
                    line = reader.readLine()
                    linesCount++
                }

                process.waitFor()

                if (linesCount == 0) {
                    // Command completed with no printed output
                    val exitValue = process.exitValue()
                    if (exitValue != 0) {
                        appendLineToSession(sessionId, "\u001b[31mتم انتهاء الأمر برمز خطأ: $exitValue\u001b[0m")
                    }
                }
            } catch (e: Exception) {
                appendLineToSession(sessionId, "\u001b[31mخطأ في محرك الطرفية: فشل تشغيل الأمر: ${e.localizedMessage}\u001b[0m")
            }
        }
    }

    private fun appendLineToSession(sessionId: String, textLine: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val lines = textLine.split("\n")
            val isRtlLine = isArabic(textLine)
            val formattedLines = lines.map {
                TerminalLine(
                    rawText = it,
                    isRtl = isRtlLine,
                    annotatedString = AnsiColorParser.parse(it, _selectedTheme.value)
                )
            }

            _sessions.update { currentList ->
                currentList.map { session ->
                    if (session.id == sessionId) {
                        // Max buffer display lines size = 300 to prevent layout memory explosion
                        val currentBuffer = session.buffer
                        val nextBuffer = if (currentBuffer.size + formattedLines.size > 300) {
                            currentBuffer.drop(formattedLines.size) + formattedLines
                        } else {
                            currentBuffer + formattedLines
                        }
                        session.copy(buffer = nextBuffer)
                    } else session
                }
            }
        }
    }

    private fun updateSessionState(sessionId: String, update: (TerminalSession) -> TerminalSession) {
        _sessions.update { list ->
            list.map { if (it.id == sessionId) update(it) else it }
        }
    }

    /**
     * Helper functions for Nano editor
     */
    fun onEditorTextChanged(newContent: String) {
        val session = activeSession ?: return
        updateSessionState(session.id) { it.copy(editorContent = newContent) }
    }

    fun saveEditorFile() {
        val session = activeSession ?: return
        val filename = session.activeEditorFile ?: "saved_file.txt"
        val content = session.editorContent

        val updatedFS = session.fileSystem.toMutableMap()
        updatedFS[filename] = content

        updateSessionState(session.id) {
            it.copy(
                fileSystem = updatedFS,
                shellMode = ShellMode.STANDARD,
                activeEditorFile = null,
                editorContent = ""
            )
        }
        appendLineToSession(session.id, "\u001b[32m[تم حفظ الملف '$filename' والخروج من المحرر بنجاح]\u001b[0m")
    }

    fun exitEditorWithoutSaving() {
        val session = activeSession ?: return
        updateSessionState(session.id) {
            it.copy(
                shellMode = ShellMode.STANDARD,
                activeEditorFile = null,
                editorContent = ""
            )
        }
        appendLineToSession(session.id, "\u001b[33m[تم الخروج من المحرر دون حفظ التغييرات]\u001b[0m")
    }

    /**
     * Checker helper to classify line text for Right-To-Left arabic processing
     */
    private fun isArabic(text: String): Boolean {
        return text.any { it in '\u0600'..'\u06ff' || it in '\u0750'..'\u077f' || it in '\ufb50'..'\ufdff' || it in '\ufe70'..'\ufeff' }
    }

    /**
     * Generate inline ANSI escapes helper
     */
    private fun getAnsiColored(text: String, code: String): String {
        return "\u001b[${code}m${text}\u001b[0m"
    }
}

/**
 * Lightweight arithmetic expression evaluator in Pure Kotlin
 */
object SimpleMathEvaluator {
    fun evaluate(str: String): Double {
        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < str.length) str[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    if (eat('+'.code)) x += parseTerm() // addition
                    else if (eat('-'.code)) x -= parseTerm() // subtraction
                    else return x
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.code)) x *= parseFactor() // multiplication
                    else if (eat('/'.code)) x /= parseFactor() // division
                    else return x
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return +parseFactor() // unary plus
                if (eat('-'.code)) return -parseFactor() // unary minus

                var x: Double
                val startPos = pos
                if (eat('('.code)) { // parentheses
                    x = parseExpression()
                    if (!eat(')'.code)) throw RuntimeException("Missing closing parenthesis")
                } else if (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) { // numbers
                    while (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) nextChar()
                    x = str.substring(startPos, pos).toDouble()
                } else {
                    throw RuntimeException("Unexpected: " + ch.toChar())
                }
                return x
            }
        }.parse()
    }
}
