package app.cosmox

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var importedConfig: TextView
    private val vpnPermissionRequest = 4101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 24, 19)
        window.navigationBarColor = android.graphics.Color.rgb(8, 24, 19)
        window.decorView.systemUiVisibility = 0
        buildScreen()
        intent?.data?.let { showImportedLink(it) }
    }

    private fun buildScreen() {
        val bg = 0xFF081813.toInt()
        val panel = 0xFF10271F.toInt()
        val green = 0xFF55E6A5.toInt()
        val white = 0xFFF1FFF8.toInt()
        val muted = 0xFF9AB7A9.toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(22))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val brand = TextView(this).apply {
            text = "CosmoX"
            textSize = 32f
            setTextColor(green)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        content.addView(brand)
        content.addView(label("PRIVATE CONNECTION", muted, 12f, 10))
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(panel)
        }
        hero.addView(label("Защищённое соединение", white, 20f, 8))
        hero.addView(label("Интерфейс готов. Для настоящего подключения нужно добавить и проверить сетевой VPN-движок.", muted, 14f, 18))
        status = label("Движок не настроен", 0xFFFFD28A.toInt(), 14f, 0)
        hero.addView(status)
        content.addView(hero, marginParams(0, 24, 0, 0))

        val connect = Button(this).apply {
            text = "Проверить разрешение VPN"
            setTextColor(bg)
            setBackgroundColor(green)
            textSize = 16f
            setOnClickListener {
                val prepare = VpnService.prepare(this@MainActivity)
                if (prepare != null) startActivityForResult(prepare, vpnPermissionRequest)
                else status.text = "Разрешение Android выдано; сетевой движок ещё не подключён"
            }
        }
        content.addView(connect, marginParams(0, 16, 0, 0))

        val import = Button(this).apply {
            text = "Импортировать VLESS-ссылку"
            setOnClickListener {
                val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "text/plain" }
                try { startActivityForResult(Intent.createChooser(i, "Выбери текстовый файл со ссылкой"), 4102) }
                catch (_: Exception) { status.text = "Не удалось открыть выбор файла" }
            }
        }
        content.addView(import, marginParams(0, 10, 0, 0))
        importedConfig = label("Серверы\nПока не добавлены", white, 15f, 0)
        importedConfig.setPadding(dp(16), dp(16), dp(16), dp(16))
        importedConfig.setBackgroundColor(panel)
        content.addView(importedConfig, marginParams(0, 20, 0, 0))
        content.addView(label("CosmoX • Android • v1.0.0", muted, 12f, 28))
        scroll.addView(content)
        root.addView(scroll, ViewGroup.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun showImportedLink(uri: Uri) {
        importedConfig.text = "Получена ссылка/файл:\n$uri\n\nИмпорт в список серверов будет завершён после подключения VPN-движка."
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == vpnPermissionRequest) {
            status.text = if (resultCode == RESULT_OK) "Разрешение Android выдано; сетевой движок ещё не подключён" else "Разрешение VPN не выдано"
        } else if (requestCode == 4102 && resultCode == RESULT_OK) {
            data?.data?.let { showImportedLink(it) }
        }
    }

    private fun label(value: String, color: Int, size: Float, bottom: Int): TextView =
        TextView(this).apply {
            text = value
            setTextColor(color)
            textSize = size
            if (bottom > 0) setPadding(0, 0, 0, dp(bottom))
        }

    private fun marginParams(left: Int, top: Int, right: Int, bottom: Int) =
        LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(left), dp(top), dp(right), dp(bottom))
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
