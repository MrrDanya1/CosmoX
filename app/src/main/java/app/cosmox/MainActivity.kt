package app.cosmox

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ClipboardManager
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var source: EditText
    private lateinit var servers: TextView
    private val permissionRequest = 4101
    private val prefs by lazy { getSharedPreferences(CosmoVpnService.PREFS, MODE_PRIVATE) }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val error = intent.getStringExtra("error")
            status.text = when {
                error != null -> "Ошибка: $error"
                intent.getBooleanExtra("connected", false) -> "Подключено • Xray-core"
                else -> "Отключено"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF081813.toInt()
        window.navigationBarColor = 0xFF081813.toInt()
        buildUi()
        if (savedInstanceState == null) handleIncomingIntent(intent)
        updateSavedState()
        val filter = IntentFilter("app.cosmox.STATUS")
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(stateReceiver, filter)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun buildUi() {
        val bg = 0xFF081813.toInt()
        val panel = 0xFF10271F.toInt()
        val green = 0xFF55E6A5.toInt()
        val white = 0xFFF1FFF8.toInt()
        val muted = 0xFF9AB7A9.toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(TextView(this).apply {
            text = "CosmoX"; textSize = 34f; setTextColor(green)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        content.addView(label("XRAY CORE • PRIVATE CONNECTION", muted, 12f, 20))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            setBackgroundColor(panel)
        }
        card.addView(label("Защищённое соединение", white, 20f, 8))
        status = label("Отключено", 0xFFFFD28A.toInt(), 14f, 0)
        card.addView(status)
        content.addView(card, params(0, 0, 0, 16))

        source = EditText(this).apply {
            hint = "VLESS / VMess / Trojan / Shadowsocks или URL подписки"
            setHintTextColor(muted)
            setTextColor(white)
            setLinkTextColor(green)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minLines = 3
            maxLines = 7
            gravity = Gravity.TOP
            setBackgroundColor(panel)
        }
        content.addView(source, params(0, 0, 0, 10))
        content.addView(Button(this).apply {
            text = "Импортировать из буфера обмена"
            setOnClickListener { importClipboard() }
        }, params(0, 0, 0, 8))
        content.addView(Button(this).apply {
            text = "Импортировать сервер / подписку"
            setOnClickListener { importSource() }
        }, params(0, 0, 0, 8))
        content.addView(Button(this).apply {
            text = "Подключиться"
            setTextColor(bg)
            setBackgroundColor(green)
            setOnClickListener {
                if (prefs.getString(CosmoVpnService.KEY_CONFIG, null).isNullOrBlank()) {
                    status.text = "Сначала импортируй сервер"
                    return@setOnClickListener
                }
                val prepare = VpnService.prepare(this@MainActivity)
                if (prepare != null) startActivityForResult(prepare, permissionRequest) else startVpn()
            }
        }, params(0, 0, 0, 8))
        content.addView(Button(this).apply {
            text = "Отключиться"
            setOnClickListener { startService(Intent(this@MainActivity, CosmoVpnService::class.java).setAction(CosmoVpnService.ACTION_STOP)) }
        }, params(0, 0, 0, 16))
        servers = label("Серверы не добавлены", white, 14f, 0)
        servers.setPadding(dp(16), dp(16), dp(16), dp(16))
        servers.setBackgroundColor(panel)
        content.addView(servers)
        content.addView(label("CosmoX • Android • XTLS/libXray", muted, 12f, 20))
        scroll.addView(content)
        root.addView(scroll, ViewGroup.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun importClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            status.text = "Буфер обмена пуст"
            return
        }
        val copiedText = runCatching { clip.getItemAt(0).coerceToText(this)?.toString().orEmpty() }.getOrDefault("").trim()
        if (copiedText.isBlank()) {
            status.text = "В буфере нет текста со ссылкой или конфигурацией"
            return
        }
        source.setText(copiedText)
        val kind = detectImportType(copiedText)
        status.text = "Обнаружено: $kind • импорт..."
        importText(copiedText)
    }

    private fun detectImportType(text: String): String {
        val value = text.trim()
        val lower = value.lowercase()
        return when {
            value.startsWith("{") || value.startsWith("[") -> {
                val json = runCatching { JSONObject(value) }.getOrNull()
                if (json?.has("outbounds") == true) "Xray JSON" else "JSON-конфигурация"
            }
            lower.contains("hysteria2://") || lower.contains("hysteria://") || lower.contains("hy2://") ->
                "Hysteria (не поддерживается Xray-core)"
            lower.contains("vless://") -> "VLESS"
            lower.contains("vmess://") -> "VMess"
            lower.contains("trojan://") -> "Trojan"
            lower.contains("ss://") -> "Shadowsocks"
            lower.contains("socks://") -> "SOCKS"
            lower.contains("http://") || lower.contains("https://") -> "URL подписки / ссылка"
            else -> "ссылка или список серверов"
        }
    }

    private fun importSource() {
        importText(source.text.toString().trim())
    }

    private fun importText(text: String) {
        if (text.isEmpty()) {
            status.text = "Скопируй ссылку, подписку или JSON-конфигурацию"
            return
        }
        val detectedType = detectImportType(text)
        if (detectedType.startsWith("Hysteria")) {
            status.text = "Найден Hysteria. Xray-core не запускает протокол Hysteria; нужен отдельный движок sing-box."
            return
        }
        status.text = "Импорт: $detectedType..."
        Thread {
            try {
                val input = if (text.startsWith("https://", true) || text.startsWith("http://", true)) {
                    val connection = URL(text).openConnection() as HttpURLConnection
                    connection.connectTimeout = 12000
                    connection.readTimeout = 15000
                    connection.setRequestProperty("User-Agent", "CosmoX/1.0")
                    try {
                        val bytes = connection.inputStream.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= 4 * 1024 * 1024) { "Подписка слишком большая (лимит 4 МБ)" }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        String(bytes, StandardCharsets.UTF_8)
                    } finally { connection.disconnect() }
                } else text
                val normalizedInput = input.trim()
                val response = XrayBridge.invoke("convertShareLinksToXrayJson", JSONObject().put("text", normalizedInput))
                val data = response.optJSONObject("data") ?: error("Парсер не вернул серверы")
                val outbounds = data.optJSONArray("outbounds") ?: error("В подписке нет поддерживаемых серверов")
                require(outbounds.length() > 0) { "В ссылке не найдено серверов" }
                val first = outbounds.optJSONObject(0)
                if (first != null && first.optString("tag").isBlank()) first.put("tag", "proxy")
                val config = JSONObject()
                    .put("log", JSONObject().put("loglevel", "warning").put("access", "none"))
                    .put("inbounds", JSONArray().put(JSONObject()
                        .put("tag", "tun-in").put("port", 0).put("protocol", "tun")
                        .put("settings", JSONObject().put("name", "CosmoX").put("mtu", 1500))))
                    .put("outbounds", outbounds)
                    .put("dns", JSONObject().put("servers", JSONArray().put("1.1.1.1").put("2606:4700:4700::1111")))
                    .put("routing", JSONObject().put("domainStrategy", "IPIfNonMatch"))
                prefs.edit().putString(CosmoVpnService.KEY_CONFIG, config.toString())
                    .putString("profile_source", text).putInt("server_count", outbounds.length()).apply()
                runOnUiThread {
                    status.text = "Импортировано серверов: ${outbounds.length()}"
                    updateSavedState()
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Ошибка импорта: ${e.message ?: "неизвестная ошибка"}" }
            }
        }.start()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data
        val shared = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val incoming = data?.toString() ?: shared
        if (!incoming.isNullOrBlank()) {
            source.setText(incoming)
            if (incoming.startsWith("vless://", true) || incoming.startsWith("vmess://", true) ||
                incoming.startsWith("trojan://", true) || incoming.startsWith("ss://", true) ||
                incoming.startsWith("https://", true)) importSource()
        }
    }

    private fun startVpn() {
        startService(Intent(this, CosmoVpnService::class.java).setAction(CosmoVpnService.ACTION_START))
        status.text = "Запускаем Xray-core..."
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == permissionRequest) {
            if (resultCode == RESULT_OK) startVpn() else status.text = "Разрешение VPN не выдано"
        }
    }

    private fun updateSavedState() {
        val count = prefs.getInt("server_count", 0)
        servers.text = if (count > 0) "Импортировано серверов: $count\nПервый сервер используется по умолчанию" else "Серверы не добавлены"
        if (prefs.getBoolean("connected", false)) status.text = "Подключено • Xray-core"
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(stateReceiver) }
        super.onDestroy()
    }

    private fun label(text: String, color: Int, size: Float, bottom: Int) =
        TextView(this).apply {
            this.text = text; setTextColor(color); textSize = size
            if (bottom > 0) setPadding(0, 0, 0, dp(bottom))
        }

    private fun params(left: Int, top: Int, right: Int, bottom: Int) =
        LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(left), dp(top), dp(right), dp(bottom)) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
