package com.example.translatekeyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val editApiKey = findViewById<EditText>(R.id.editApiKey)
        val editSourceLang = findViewById<EditText>(R.id.editSourceLang)
        val editTargetLang = findViewById<EditText>(R.id.editTargetLang)
        val saveStatus = findViewById<TextView>(R.id.saveStatus)

        // Load existing values
        editApiKey.setText(Prefs.getApiKey(this))
        editSourceLang.setText(Prefs.getSourceLang(this))
        editTargetLang.setText(Prefs.getTargetLang(this))

        findViewById<Button>(R.id.btnEnableKeyboard).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        findViewById<Button>(R.id.btnSwitchKeyboard).setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            Prefs.setApiKey(this, editApiKey.text.toString().trim())
            Prefs.setLanguages(
                this,
                editSourceLang.text.toString().trim(),
                editTargetLang.text.toString().trim()
            )
            saveStatus.text = "Saved."
        }
    }
}
