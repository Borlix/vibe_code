package com.example.translatekeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/**
 * Custom IME with a "compose then translate" workflow:
 *  - Letters/punctuation you type accumulate in [composingBuffer] and are shown
 *    as composing text in the target app (the usual underlined in-progress text).
 *  - After a short pause in typing, the buffer is sent to Groq for translation
 *    and the result is shown in the preview bar above the keyboard.
 *  - Pressing the "Translate ➤" key commits the TRANSLATED text into the app
 *    and clears the buffer, ready for the next sentence.
 *  - Backspace edits the buffer normally; Shift capitalizes the next letter.
 */
class TranslateInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var qwertyKeyboard: Keyboard
    private lateinit var translationPreview: TextView
    private lateinit var languagePairLabel: TextView
    private lateinit var statusIndicator: TextView

    private val composingBuffer = StringBuilder()
    private var shiftEnabled = false
    private var lastTranslation = ""

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingTranslateRunnable: Runnable? = null
    private val debounceMs = 500L

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    companion object {
        private const val CODE_SETTINGS = -100
        private const val CODE_TRANSLATE_INSERT = -101
        private const val CODE_TOGGLE_MODE = -102
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.input_view, null)

        keyboardView = view.findViewById(R.id.keyboardView)
        translationPreview = view.findViewById(R.id.translationPreview)
        languagePairLabel = view.findViewById(R.id.languagePairLabel)
        statusIndicator = view.findViewById(R.id.statusIndicator)

        qwertyKeyboard = Keyboard(this, R.xml.keyboard_qwerty)
        keyboardView.keyboard = qwertyKeyboard
        keyboardView.setOnKeyboardActionListener(this)

        refreshLanguageLabel()
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composingBuffer.clear()
        translationPreview.text = "Type something to see the translation..."
        statusIndicator.text = ""
        refreshLanguageLabel()
    }

    private fun refreshLanguageLabel() {
        val source = Prefs.getSourceLang(this)
        val target = Prefs.getTargetLang(this)
        languagePairLabel.text = "${abbreviate(source)} \u2192 ${abbreviate(target)}"
    }

    private fun abbreviate(lang: String): String =
        if (lang.length <= 3) lang.uppercase() else lang.take(3).uppercase()

    // -----------------------------------------------------------------
    // KeyboardView.OnKeyboardActionListener
    // -----------------------------------------------------------------

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return

        when (primaryCode) {
            Keyboard.KEYCODE_SHIFT -> {
                shiftEnabled = !shiftEnabled
                keyboardView.isShifted = shiftEnabled
            }

            Keyboard.KEYCODE_DELETE -> {
                if (composingBuffer.isNotEmpty()) {
                    composingBuffer.deleteCharAt(composingBuffer.length - 1)
                    ic.setComposingText(composingBuffer, 1)
                    scheduleTranslate()
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            }

            CODE_SETTINGS -> {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }

            CODE_TOGGLE_MODE -> {
                Prefs.swapLanguages(this)
                refreshLanguageLabel()
                if (composingBuffer.isNotEmpty()) scheduleTranslate()
            }

            CODE_TRANSLATE_INSERT -> {
                commitTranslation(ic)
            }

            else -> {
                var char = primaryCode.toChar()
                if (shiftEnabled && char.isLetter()) {
                    char = char.uppercaseChar()
                    shiftEnabled = false
                    keyboardView.isShifted = false
                }
                composingBuffer.append(char)
                ic.setComposingText(composingBuffer, 1)
                scheduleTranslate()
            }
        }
    }

    /** Debounce translation calls so we don't hit the API on every keystroke. */
    private fun scheduleTranslate() {
        pendingTranslateRunnable?.let { mainHandler.removeCallbacks(it) }
        val textToTranslate = composingBuffer.toString()
        if (textToTranslate.isBlank()) {
            translationPreview.text = "Type something to see the translation..."
            return
        }
        val runnable = Runnable { runTranslation(textToTranslate) }
        pendingTranslateRunnable = runnable
        mainHandler.postDelayed(runnable, debounceMs)
    }

    private fun runTranslation(text: String) {
        statusIndicator.text = "translating…"
        serviceScope.launch {
            try {
                val apiKey = Prefs.getApiKey(this@TranslateInputMethodService)
                val source = Prefs.getSourceLang(this@TranslateInputMethodService)
                val target = Prefs.getTargetLang(this@TranslateInputMethodService)
                val result = TranslationClient.translate(apiKey, source, target, text)
                // Only show it if the buffer hasn't changed since we started (avoids stale results)
                if (composingBuffer.toString() == text) {
                    lastTranslation = result
                    translationPreview.text = result
                    statusIndicator.text = "\u2713"
                }
            } catch (e: Exception) {
                statusIndicator.text = "error"
                translationPreview.text = "Couldn't translate: ${e.message}"
            }
        }
    }

    /** Commits the last successful translation into the target app and resets the buffer. */
    private fun commitTranslation(ic: android.view.inputmethod.InputConnection) {
        val textToCommit = if (lastTranslation.isNotBlank()) lastTranslation else composingBuffer.toString()
        if (textToCommit.isBlank()) return
        // setComposingText REPLACES the current composing span's content (the original,
        // untranslated text) with the translation, then finishComposingText() finalizes
        // it as normal committed text. Calling commitText() here instead would have
        // appended the translation after the original text rather than replacing it.
        ic.setComposingText("$textToCommit ", 1)
        ic.finishComposingText()
        composingBuffer.clear()
        lastTranslation = ""
        translationPreview.text = "Type something to see the translation..."
        statusIndicator.text = ""
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
