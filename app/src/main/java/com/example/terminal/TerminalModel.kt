package com.example.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import java.util.UUID

/**
 * Supported Terminal color themes
 */
enum class TerminalTheme(
    val arabicName: String,
    val background: Color,
    val textDefault: Color,
    val primary: Color,
    val secondary: Color,
    val error: Color,
    val accent: Color
) {
    GREEN_HACKER(
        arabicName = "المبرمج الأخضر (الافتراضي)",
        background = Color(0xFF07090E),
        textDefault = Color(0xFF39FF14), // Neon green
        primary = Color(0xFF39FF14),
        secondary = Color(0xFF00FF7F),
        error = Color(0xFFFF3333),
        accent = Color(0xFFADFF2F)
    ),
    AMBER_VINTAGE(
        arabicName = "الكهرمان العتيق",
        background = Color(0xFF150F05),
        textDefault = Color(0xFFFFB000), // Amber
        primary = Color(0xFFFFB000),
        secondary = Color(0xFFFFCC00),
        error = Color(0xFFFF4500),
        accent = Color(0xFFDEB887)
    ),
    CYBERPUNK(
        arabicName = "سايبربانك النيون",
        background = Color(0xFF13091B),
        textDefault = Color(0xFF00FFFF), // Neon Cyan
        primary = Color(0xFFFF007F), // Neon Pink
        secondary = Color(0xFF00FFFF),
        error = Color(0xFFFF2A2A),
        accent = Color(0xFFDFFF00)
    ),
    PAPER_QALAM(
        arabicName = "قلم وورق عتيق",
        background = Color(0xFFF4ECD8),
        textDefault = Color(0xFF2B2625), // Vintage Ink
        primary = Color(0xFFB22222), // Crimson
        secondary = Color(0xFF4A6B82),
        error = Color(0xFFD2143A),
        accent = Color(0xFF8B4513)
    ),
    OLED_DARK(
        arabicName = "شاشة سوداء مطفأة OLED",
        background = Color(0xFF000000),
        textDefault = Color(0xFFE5E5E5), // Ghost White
        primary = Color(0xFF4AC5FF),
        secondary = Color(0xFFBB86FC),
        error = Color(0xFFCF6679),
        accent = Color(0xFF03DAC6)
    )
}

/**
 * The modal/shell execution context of a Terminal session
 */
enum class ShellMode {
    STANDARD,
    M_EDITOR,  // Nano-like integrated Editor
    INTERACTIVE_PYTHON,
    INTERACTIVE_JS
}

/**
 * A line item inside the terminal output buffer supporting ANSI colors
 */
data class TerminalLine(
    val rawText: String,
    val isRtl: Boolean,
    val annotatedString: AnnotatedString
)

/**
 * A single terminal session with its own command log queue and running process
 */
data class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val buffer: List<TerminalLine> = emptyList(),
    val currentDirectory: String = "/data/data/com.arabicterm/files/home",
    val shellMode: ShellMode = ShellMode.STANDARD,
    val packageStatus: Map<String, Boolean> = mapOf("python" to false, "nodejs" to false),
    val fileSystem: Map<String, String> = mapOf(
        "مرحبا.sh" to "echo 'أهلاً بك في مفسر الأوامر العربي!'\n",
        "test.py" to "print('السلام عليكم يا عالم!')\n"
    ),
    val activeEditorFile: String? = null,
    val editorContent: String = "",
    val commandHistory: List<String> = emptyList()
)

object AnsiColorParser {
    /**
     * Parse raw string containing ANSI escape sequences into Composable-friendly styled texts.
     */
    fun parse(rawStr: String, theme: TerminalTheme): AnnotatedString {
        if (!rawStr.contains("\u001b") && !rawStr.contains("\\[")) {
            return buildAnnotatedString { append(rawStr) }
        }

        return buildAnnotatedString {
            var index = 0
            val length = rawStr.length
            var currentColor = theme.textDefault
            var isBold = false
            var isUnderlined = false

            while (index < length) {
                // Check if it's start of ANSI escape sequence: \u001b[
                if (index < length - 2 && rawStr[index] == '\u001b' && rawStr[index + 1] == '[') {
                    val startEscape = index
                    // search for terminal character of escape sequence 'm'
                    var endEscape = startEscape + 2
                    while (endEscape < length && rawStr[endEscape] != 'm') {
                        endEscape++
                    }

                    if (endEscape < length && rawStr[endEscape] == 'm') {
                        val codesSection = rawStr.substring(startEscape + 2, endEscape)
                        val codes = codesSection.split(';')

                        for (codeStr in codes) {
                            val code = codeStr.toIntOrNull() ?: 0
                            when (code) {
                                0 -> { // Reset all
                                    currentColor = theme.textDefault
                                    isBold = false
                                    isUnderlined = false
                                }
                                1 -> isBold = true
                                4 -> isUnderlined = true
                                // ANSI Foreground colors
                                30 -> currentColor = Color.Black
                                31 -> currentColor = theme.error // Red
                                32 -> currentColor = theme.primary // Green
                                33 -> currentColor = theme.accent // Yellow
                                34 -> currentColor = theme.secondary // Blue
                                35 -> currentColor = Color(0xFFFF00FF) // Magenta
                                36 -> currentColor = Color.Cyan
                                37 -> currentColor = Color.White
                                // Bright ANSI colors
                                90 -> currentColor = Color.Gray
                                91 -> currentColor = theme.error
                                92 -> currentColor = theme.primary
                                93 -> currentColor = theme.accent
                                94 -> currentColor = theme.secondary
                                95 -> currentColor = Color(0xFFFF00FF)
                                96 -> currentColor = Color.Cyan
                                97 -> currentColor = Color.White
                            }
                        }
                        index = endEscape + 1
                        continue
                    }
                }

                // Just standard char
                val char = rawStr[index]
                val spanStart = length
                append(char)
                addStyle(
                    style = SpanStyle(
                        color = currentColor,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        textDecoration = if (isUnderlined) TextDecoration.Underline else TextDecoration.None
                    ),
                    start = this.length - 1,
                    end = this.length
                )
                index++
            }
        }
    }
}
