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
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(TextView(this).apply {
            text = "CosmoX"
            textSize = 30f
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
            background = rounded(panel2, 100)
        })
        content.addView(header, params(0, 0, 0, 26))

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(22))
            background = rounded(panel, 28, 0xFF273128.toInt())
        }
        hero.addView(TextView(this).apply {
            text = "YOUR CONNECTION"
            textSize = 10f
            letterSpacing = 0.18f
            setTextColor(muted)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        val orb = TextView(this).apply {
            text = "✦"
            textSize = 42f
            gravity = android.view.Gravity.CENTER
            setTextColor(neon)
            background = rounded(0xFF202D18.toInt(), 100, 0xFF354B24.toInt())
            setPadding(dp(20), dp(10), dp(20), dp(10))
        }
        hero.addView(orb, LinearLayout.LayoutParams(-2, -2).apply {
            topMargin = dp(18)
            bottomMargin = dp(16)
        })
        hero.addView(TextView(this).apply {
            text = "Private by design"
            textSize = 22f
            setTextColor(white)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
        })
        hero.addView(TextView(this).apply {
            text = "Connect to your selected server"
            textSize = 13f
            setTextColor(muted)
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(7), 0, dp(16))
        })
        status = TextView(this).apply {
            text = "●  Отключено"
            textSize = 13f
            setTextColor(neon)
            gravity = android.view.Gravity.CENTER
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = rounded(0xFF202D18.toInt(), 100)
        }
        hero.addView(status)
        content.addView(hero, params(0, 0, 0, 16))

        val connect = Button(this).apply {
            text = "Подключиться"
            textSize = 16f
            isAllCaps = false
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(bg)
            background = rounded(neon, 18)
            stateListAnimator = null
            minHeight = dp(58)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setOnClickListener {
                if (prefs.getString(CosmoVpnService.KEY_CONFIG, null).isNullOrBlank()) {
                    status.text = "Сначала импортируй сервер"
                    return@setOnClickListener
                }
                val prepare = VpnService.prepare(this@MainActivity)
                if (prepare != null) startActivityForResult(prepare, permissionRequest) else startVpn()
            }
        }
        content.addView(connect, params(0, 0, 0, 10))

        val disconnect = Button(this).apply {
            text = "Отключить VPN"
            textSize = 14f
            isAllCaps = false
            setTextColor(white)
            background = rounded(panel2, 16, 0xFF29332B.toInt())
            stateListAnimator = null
            minHeight = dp(50)
            setOnClickListener {
                startService(Intent(this@MainActivity, CosmoVpnService::class.java).setAction(CosmoVpnService.ACTION_STOP))
            }
        }
        content.addView(disconnect, params(0, 0, 0, 22))

        content.addView(label("QUICK ACTIONS", muted, 10f, 10).apply {
            letterSpacing = 0.16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val import = actionTile("＋", "Импорт", "Добавить сервер") { importClipboard() }
        val test = actionTile("⌁", "Проверка", "Пинг сервера") { testServer() }
        actions.addView(import, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(7) })
        actions.addView(test, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(7) })
        content.addView(actions, params(0, 0, 0, 16))

        val serverCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(panel, 20, 0xFF202821.toInt())
        }
        val serverHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        serverHeader.addView(TextView(this).apply {
            text = "Мой сервер"
            textSize = 16f
            setTextColor(white)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        serverHeader.addView(TextView(this).apply {
            text = "● LOCAL"
            textSize = 10f
            setTextColor(neon)
            background = rounded(0xFF202D18.toInt(), 100)
            setPadding(dp(9), dp(6), dp(9), dp(6))
        })
        serverCard.addView(serverHeader)
        servers = label("Серверы не добавлены", muted, 13f, 0).apply {
            setPadding(0, dp(12), 0, 0)
        }
        serverCard.addView(servers)
        serverCard.isClickable = true
        serverCard.isFocusable = true
        serverCard.setOnClickListener { openSavedServerPicker() }
        content.addView(serverCard, params(0, 0, 0, 18))

        content.addView(TextView(this).apply {
            text = "CosmoX  •  Powered by XTLS/libXray"
            textSize = 11f
            setTextColor(0xFF657068.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(6), 0, dp(8))
        })

        scroll.addView(content)
        root.addView(scroll, ViewGroup.LayoutParams(-1, -1))
        setContentView(root)
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
                    showServerPicker(selectable, text, true)
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
        if (outbounds.length() == 0) {
            status.text = "Нет доступных серверов"
            return
        }
        val labels = Array(outbounds.length()) { index ->
            outboundLabel(outbounds.optJSONObject(index) ?: JSONObject(), index)
        }
        AlertDialog.Builder(this)
            .setTitle(if (isNewImport) "Выбери сервер подписки" else "Выбрать сервер")
            .setMessage("Подключение будет использовать выбранный сервер. Выбор можно изменить позже.")
            .setItems(labels) { _, which ->
                try {
                    val chosen = outbounds.getJSONObject(which)
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
                        .putInt("server_count", outbounds.length())
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
            val outbounds = selectableOutbounds(root.optJSONArray("outbounds") ?: JSONArray())
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
        servers.text = if (count > 0) {
            "${selected ?: "Сервер не выбран"}\nСерверов в подписке: $count\nНажми, чтобы сменить сервер"
        } else "Серверы не добавлены"
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
