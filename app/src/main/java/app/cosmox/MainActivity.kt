package app.cosmox

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ClipboardManager
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
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
    private lateinit var servers: TextView
    private lateinit var connectButton: Button
    private val permissionRequest = 4101
    private val prefs by lazy { getSharedPreferences(CosmoVpnService.PREFS, MODE_PRIVATE) }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val error = intent.getStringExtra("error")
            val connected = intent.getBooleanExtra("connected", false)
            status.text = when {
                error != null -> "Ошибка: $error"
                connected -> "●  Подключено • Xray-core"
                else -> "●  Отключено"
            }
            renderConnectionState(connected)
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
        val bg = 0xFF050706.toInt()
        val panel = 0xFF101512.toInt()
        val panel2 = 0xFF171E19.toInt()
        val neon = 0xFFB7FF3C.toInt()
        val white = 0xFFF4F7F2.toInt()
        val muted = 0xFF89958C.toInt()

        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(12))
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(TextView(this).apply {
            text = "CosmoX"
            textSize = 31f
            setTextColor(white)
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            letterSpacing = -0.03f
        })
        brand.addView(label("PRIVATE NETWORK", muted, 10f, 0).apply { letterSpacing = 0.18f })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "●  XRAY"
            textSize = 11f
            setTextColor(neon)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(panel2, 100, 0xFF283629.toInt())
        })
        root.addView(header, params(0, 0, 0, 10))

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
        }
        root.addView(center, LinearLayout.LayoutParams(-1, 0, 1f))

        center.addView(TextView(this).apply {
            text = "ЗАЩИТИ СВОЁ СОЕДИНЕНИЕ"
            textSize = 10f
            letterSpacing = 0.16f
            gravity = android.view.Gravity.CENTER
            setTextColor(muted)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, params(0, 0, 0, 24))

        connectButton = Button(this).apply {
            text = "⏻\\nВКЛЮЧИТЬ"
            textSize = 17f
            isAllCaps = false
            gravity = android.view.Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(neon)
            background = rounded(panel, 1000, neon)
            stateListAnimator = null
            minHeight = dp(190)
            minimumHeight = dp(190)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener {
                if (prefs.getBoolean("connected", false)) {
                    startService(Intent(this@MainActivity, CosmoVpnService::class.java)
                        .setAction(CosmoVpnService.ACTION_STOP))
                    status.text = "Отключаем VPN..."
                } else {
                    if (prefs.getString(CosmoVpnService.KEY_CONFIG, null).isNullOrBlank()) {
                        status.text = "Сначала добавь сервер"
                        return@setOnClickListener
                    }
                    val prepare = VpnService.prepare(this@MainActivity)
                    if (prepare != null) startActivityForResult(prepare, permissionRequest) else startVpn()
                }
            }
        }
        center.addView(connectButton, LinearLayout.LayoutParams(dp(190), dp(190)).apply {
            gravity = android.view.Gravity.CENTER
            bottomMargin = dp(25)
        })

        status = TextView(this).apply {
            text = "●  Отключено"
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setTextColor(muted)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(panel2, 100)
        }
        center.addView(status, params(0, 0, 0, 24))

        val selectedServerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(18), dp(13), dp(18), dp(13))
            background = rounded(panel, 18, 0xFF29332B.toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener { openSavedServerPicker() }
        }
        selectedServerCard.addView(TextView(this).apply {
            text = "ВЫБРАННЫЙ СЕРВЕР"
            textSize = 9f
            letterSpacing = 0.14f
            setTextColor(muted)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        servers = TextView(this).apply {
            text = "Сервер не выбран"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(white)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(5), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        selectedServerCard.addView(servers)
        center.addView(selectedServerCard, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(2)
        })

        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(6), dp(7), dp(6), dp(7))
            background = rounded(panel, 22, 0xFF202821.toInt())
        }

        val importButton = navButton("＋", "Добавить", neon, panel) { importClipboard() }
        val serverButton = navButton("▦", "Серверы", white, panel2) { openSavedServerPicker() }
        val settingsButton = navButton("⚙", "Настройки", muted, panel) {
            status.text = "Настройки появятся позже"
        }
        bottomNav.addView(importButton, LinearLayout.LayoutParams(0, dp(58), 1f))
        bottomNav.addView(serverButton, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
            leftMargin = dp(5)
            rightMargin = dp(5)
        })
        bottomNav.addView(settingsButton, LinearLayout.LayoutParams(0, dp(58), 1f))
        root.addView(bottomNav, params(0, 12, 0, 0))

        setContentView(root)
        renderConnectionState(prefs.getBoolean("connected", false))
    }

    private fun navButton(symbol: String, title: String, color: Int, panel: Int, action: () -> Unit): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            background = rounded(panel, 16)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        item.addView(TextView(this).apply {
            text = symbol
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setTextColor(color)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        item.addView(TextView(this).apply {
            text = title
            textSize = 10f
            gravity = android.view.Gravity.CENTER
            setTextColor(color)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, 0)
        })
        return item
    }

    private fun renderConnectionState(connected: Boolean) {
        if (!::connectButton.isInitialized) return
        val bg = 0xFF050706.toInt()
        val panel = 0xFF101512.toInt()
        val neon = 0xFFB7FF3C.toInt()
        connectButton.text = if (connected) "⏻\\nОТКЛЮЧИТЬ" else "⏻\\nВКЛЮЧИТЬ"
        connectButton.setTextColor(if (connected) bg else neon)
        connectButton.background = if (connected) rounded(neon, 1000) else rounded(panel, 1000, neon)
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }
    }

    private fun actionTile(symbol: String, title: String, subtitle: String, action: () -> Unit): LinearLayout {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(15), dp(10), dp(14))
            background = rounded(0xFF101512.toInt(), 20, 0xFF202821.toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        tile.addView(TextView(this).apply {
            text = symbol
            textSize = 25f
            setTextColor(0xFFB7FF3C.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        tile.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(0xFFF4F7F2.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(3))
        })
        tile.addView(TextView(this).apply {
            text = subtitle
            textSize = 11f
            setTextColor(0xFF89958C.toInt())
        })
        return tile
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
                val selectable = selectableOutbounds(outbounds)
                require(selectable.length() > 0) { "В подписке нет поддерживаемых прокси-серверов" }
                runOnUiThread {
                    showServerPicker(outbounds, text, true)
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Ошибка импорта: ${e.message ?: "неизвестная ошибка"}" }
            }
        }.start()
    }

    private fun selectableOutbounds(outbounds: JSONArray): JSONArray {
        val result = JSONArray()
        for (i in 0 until outbounds.length()) {
            val item = outbounds.optJSONObject(i) ?: continue
            val protocol = item.optString("protocol").lowercase()
            if (protocol !in setOf("freedom", "direct", "blackhole", "block", "dns", "")) {
                if (item.optString("tag").isBlank()) item.put("tag", "proxy-${result.length() + 1}")
                result.put(item)
            }
        }
        return result
    }

    private fun outboundLabel(outbound: JSONObject, index: Int): String {
        val tag = outbound.optString("tag").takeIf { it.isNotBlank() } ?: "Сервер ${index + 1}"
        val protocol = outbound.optString("protocol").uppercase().ifBlank { "PROXY" }
        val servers = outbound.optJSONObject("settings")?.optJSONArray("servers")
        val firstServer = servers?.optJSONObject(0)
        val address = firstServer?.optString("address")?.takeIf { it.isNotBlank() }
        val port = if (firstServer != null && firstServer.has("port")) firstServer.optInt("port") else -1
        val endpoint = if (address != null) "\n$address${if (port > 0) ":$port" else ""}" else ""
        return "$tag  •  $protocol$endpoint"
    }

    private fun showServerPicker(outbounds: JSONArray, source: String, isNewImport: Boolean) {
        val choices = selectableOutbounds(outbounds)
        if (choices.length() == 0) {
            status.text = "Нет доступных серверов"
            return
        }
        val labels = Array(choices.length()) { index ->
            val item = choices.optJSONObject(index) ?: JSONObject()
            item.optString("tag").ifBlank { "Сервер ${index + 1}" }
        }
        AlertDialog.Builder(this)
            .setTitle(if (isNewImport) "Выбери сервер подписки" else "Выбрать сервер")
            .setMessage("Подключение будет использовать выбранный сервер. Выбор можно изменить позже.")
            .setItems(labels) { _, which ->
                try {
                    val chosen = choices.getJSONObject(which)
                    var tag = chosen.optString("tag")
                    if (tag.isBlank()) {
                        tag = "proxy-${which + 1}"
                        chosen.put("tag", tag)
                    }
                    val config = JSONObject()
                        .put("log", JSONObject().put("loglevel", "warning").put("access", "none"))
                        .put("inbounds", JSONArray().put(JSONObject()
                            .put("tag", "tun-in").put("port", 0).put("protocol", "tun")
                            .put("settings", JSONObject().put("name", "CosmoX").put("mtu", 1500))))
                        .put("outbounds", outbounds)
                        .put("dns", JSONObject().put("servers", JSONArray().put("1.1.1.1").put("2606:4700:4700::1111")))
                        .put("routing", JSONObject()
                            .put("domainStrategy", "IPIfNonMatch")
                            .put("rules", JSONArray().put(JSONObject()
                                .put("type", "field")
                                .put("inboundTag", JSONArray().put("tun-in"))
                                .put("outboundTag", tag))))
                    prefs.edit()
                        .putString(CosmoVpnService.KEY_CONFIG, config.toString())
                        .putString("profile_source", source)
                        .putInt("server_count", choices.length())
                        .putString("selected_server_tag", tag)
                        .putString("selected_server_name", chosen.optString("tag").ifBlank { "Сервер ${which + 1}" })
                        .apply()
                    status.text = "Выбран сервер: ${chosen.optString("tag").ifBlank { "Сервер ${which + 1}" }}"
                    updateSavedState()
                } catch (e: Exception) {
                    status.text = "Не удалось выбрать сервер: ${e.message ?: "ошибка"}"
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openSavedServerPicker() {
        val saved = prefs.getString(CosmoVpnService.KEY_CONFIG, null)
        if (saved.isNullOrBlank()) {
            status.text = "Сначала импортируй подписку"
            return
        }
        try {
            val root = JSONObject(saved)
            val outbounds = root.optJSONArray("outbounds") ?: JSONArray()
            showServerPicker(outbounds, prefs.getString("profile_source", "").orEmpty(), false)
        } catch (e: Exception) {
            status.text = "Не удалось открыть список серверов"
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data
        val shared = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val incoming = data?.toString() ?: shared
        if (!incoming.isNullOrBlank()) {
            if (incoming.startsWith("vless://", true) || incoming.startsWith("vmess://", true) ||
                incoming.startsWith("trojan://", true) || incoming.startsWith("ss://", true) ||
                incoming.startsWith("https://", true) || incoming.trimStart().startsWith("{") ||
                incoming.startsWith("hysteria://", true) || incoming.startsWith("hysteria2://", true) ||
                incoming.startsWith("hy2://", true)) importText(incoming)
        }
    }

    /**
     * Проверяет первый импортированный outbound отдельно от Android TUN.
     * Так можно понять, недоступен ли сам VLESS-сервер или проблема в маршрутизации VPN.
     */
    private fun testServer() {
        // libXray pingBatch rejects calls while its managed runXray instance is active.
        if (prefs.getBoolean("connected", false)) {
            status.text = "Отключи VPN перед проверкой сервера"
            return
        }
        val saved = prefs.getString(CosmoVpnService.KEY_CONFIG, null)
        if (saved.isNullOrBlank()) {
            status.text = "Сначала импортируй сервер"
            return
        }
        status.text = "Проверяем сервер напрямую..."
        Thread {
            try {
                val fullConfig = JSONObject(saved)
                val outbounds = fullConfig.optJSONArray("outbounds")
                    ?: error("В конфигурации нет outbounds")
                require(outbounds.length() > 0) { "Нет импортированных серверов" }
                val first = outbounds.optJSONObject(0) ?: error("Первый сервер повреждён")
                val tag = first.optString("tag").takeIf { it.isNotBlank() }
                val outboundOnly = JSONObject().put("outbounds", outbounds)
                val item = JSONObject().put("xrayJson", outboundOnly.toString())
                if (tag != null) item.put("outboundTag", tag)
                val payload = JSONObject()
                    .put("configs", JSONArray().put(item))
                    .put("timeout", 8)
                    .put("url", "https://cp.cloudflare.com/")
                val response = XrayBridge.invoke("pingBatch", payload)
                val data = response.optJSONObject("data") ?: error("Проверка не вернула результат")
                val results = data.optJSONArray("results") ?: error("В ответе нет результатов")
                val result = results.optJSONObject(0) ?: error("Пустой результат проверки")
                val success = result.optBoolean("success", false)
                val delay = result.optInt("delay", -1)
                val detail = result.optString("error").takeIf { it.isNotBlank() }
                runOnUiThread {
                    status.text = if (success) {
                        "Сервер доступен: ${delay} мс. Если сайты не открываются, проблема вероятнее в TUN/маршрутизации."
                    } else {
                        "Сервер не прошёл проверку (${if (delay >= 0) "${delay} мс" else "нет ответа"})." +
                            (detail?.let { " Причина: $it" } ?: "")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Ошибка проверки: ${e.message ?: "неизвестная ошибка"}"
                }
            }
        }.start()
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
        val selected = prefs.getString("selected_server_name", null)
        servers.text = if (count > 0) selected ?: "Сервер не выбран" else "Сервер не выбран"
        val connected = prefs.getBoolean("connected", false)
        status.text = if (connected) "●  Подключено • Xray-core" else "●  Отключено"
        renderConnectionState(connected)
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
