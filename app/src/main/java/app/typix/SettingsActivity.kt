package app.typix

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class SettingsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(p, p * 2, p, p) }
        root.addView(TextView(this).apply { text = "Typix"; textSize = 28f })
        root.addView(TextView(this).apply { text = "Type Sinhala the way you speak it. No internet, no tracking." })
        root.addView(Button(this).apply { text = "1. Enable Typix keyboard"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) } })
        root.addView(Button(this).apply { text = "2. Switch to Typix"
            setOnClickListener { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() } })
        setContentView(root)
    }
}
