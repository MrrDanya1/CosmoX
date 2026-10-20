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
    private lateinit var importMessage: TextView
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

    private lateinit var pageContent: LinearLayout
    private var currentPage = "home"

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(12)); setBackgroundColor(0xFF050706.toInt()) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(TextView(this).apply { text = "CosmoX"; textSize = 31f; setTextColor(0xFFF4F7F2.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD })
        brand.addView(label("PRIVATE NETWORK", 0xFF89958C.toInt(), 10f, 0))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply { text = "● XRAY"; textSize = 11f; setTextColor(0xFFB7FF3C.toInt()); setPadding(dp(12), dp(8), dp(12), dp(8)); background = rounded(0xFF171E19.toInt(), 100, 0xFF283629.toInt()) })
        root.addView(header, params(0, 0, 0, 12))
        pageContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER }
        root.addView(pageContent, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER; setPadding(dp(6), dp(7), dp(6), dp(7)); background = rounded(0xFF101512.toInt(), 22, 0xFF202821.toInt()) }
        nav.addView(navButton("⌂", "Главная", 0xFFF4F7F2.toInt(), 0xFF171E19.toInt()) { showPage("home") }, LinearLayout.LayoutParams(0, dp(58), 1f))
        nav.addView(navButton("▦", "Серверы", 0xFFB7FF3C.toInt(), 0xFF101512.toInt()) { showPage("servers") }, LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(5); rightMargin = dp(5) })
        nav.addView(navButton("⚙", "Настройки", 0xFF89958C.toInt(), 0xFF101512.toInt()) { showPage("settings") }, LinearLayout.LayoutParams(0, dp(58), 1f))
        root.addView(nav, params(0, 12, 0, 0))
        setContentView(root)
        showPage("home")
    }

    private fun showPage(page: String) {
        currentPage = page
        pageContent.removeAllViews()
        when (page) {
            "servers" -> renderServersPage()
            "settings" -> {
                pageContent.gravity = android.view.Gravity.TOP
                pageContent.addView(TextView(this).apply { text = "НАСТРОЙКИ"; textSize = 24f; setTextColor(0xFFF4F7F2.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD }, params(0, 8, 0, 12))
                pageContent.addView(TextView(this).apply { text = "Раздел настроек добавим позже"; textSize = 14f; setTextColor(0xFF89958C.toInt()) })
            }
            else -> renderHomePage()
        }
    }

    private fun renderHomePage() {
        pageContent.gravity = android.view.Gravity.CENTER
        pageContent.addView(TextView(this).apply { text = "ЗАЩИТИ СВОЁ СОЕДИНЕНИЕ"; textSize = 10f; gravity = android.view.Gravity.CENTER; setTextColor(0xFF89958C.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD }, params(0, 0, 0, 18))
        connectButton = Button(this).apply {
            textSize = 17f; isAllCaps = false; gravity = android.view.Gravity.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD; stateListAnimator = null
            setOnClickListener {
                if (prefs.getBoolean("connected", false)) {
                    startService(Intent(this@MainActivity, CosmoVpnService::class.java).setAction(CosmoVpnService.ACTION_STOP))
                    status.text = "Отключаем VPN..."
                } else {
                    if (prefs.getString(CosmoVpnService.KEY_CONFIG, null).isNullOrBlank()) { status.text = "Сначала добавь сервер"; return@setOnClickListener }
                    val prepare = VpnService.prepare(this@MainActivity)
                    if (prepare != null) startActivityForResult(prepare, permissionRequest) else startVpn()
                }
            }
        }
        pageContent.addView(connectButton, LinearLayout.LayoutParams(dp(216), dp(216)).apply { gravity = android.view.Gravity.CENTER; bottomMargin = dp(18) })
        status = TextView(this).apply { textSize = 14f; gravity = android.view.Gravity.CENTER; setTextColor(0xFF89958C.toInt()); setPadding(dp(12), dp(8), dp(12), dp(8)); background = rounded(0xFF171E19.toInt(), 100) }
        pageContent.addView(status, params(0, 0, 0, 18))
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER; setPadding(dp(18), dp(14), dp(18), dp(14)); background = rounded(0xFF101512.toInt(), 18, 0xFF29332B.toInt()); isClickable = true; isFocusable = true; setOnClickListener { showPage("servers") } }
        card.addView(TextView(this).apply { text = "ВЫБРАННЫЙ СЕРВЕР"; textSize = 9f; setTextColor(0xFF89958C.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD })
        servers = TextView(this).apply { textSize = 16f; gravity = android.view.Gravity.CENTER; setTextColor(0xFFF4F7F2.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, dp(5), 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        card.addView(servers)
        pageContent.addView(card, LinearLayout.LayoutParams(-1, -2))
        updateSavedState()
    }

    private fun renderServersPage() {
        pageContent.gravity = android.view.Gravity.TOP
        pageContent.addView(TextView(this).apply { text = "СЕРВЕРЫ"; textSize = 24f; setTextColor(0xFFF4F7F2.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD }, params(0, 8, 0, 5))
        pageContent.addView(TextView(this).apply { text = "Подписки и серверы"; textSize = 12f; setTextColor(0xFF89958C.toInt()) }, params(0, 0, 0, 10))

        val source = prefs.getString("profile_source", "").orEmpty()
        if (source.isNotBlank()) {
            val isUrl = source.startsWith("https://", true) || source.startsWith("http://", true)
            val host = if (isUrl) runCatching { URL(source).host }.getOrDefault("Подписка") else "Импортированный список"
            val profile = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(10), dp(10))
                background = rounded(0xFF101512.toInt(), 16, 0xFF263128.toInt())
            }
            val profileText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            profileText.addView(TextView(this).apply { text = "ПОДПИСКА"; textSize = 9f; setTextColor(0xFFB7FF3C.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD })
            profileText.addView(TextView(this).apply { text = host; textSize = 13f; setTextColor(0xFFF4F7F2.toInt()); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            profileText.addView(TextView(this).apply { text = outboundsCountLabel(); textSize = 11f; setTextColor(0xFF89958C.toInt()) })
            profile.addView(profileText, LinearLayout.LayoutParams(0, -2, 1f))
            if (isUrl) {
                profile.addView(Button(this).apply {
                    text = "↻"; textSize = 20f; isAllCaps = false; setTextColor(0xFFB7FF3C.toInt())
                    background = rounded(0xFF1B2A1D.toInt(), 12); stateListAnimator = null
                    contentDescription = "Обновить подписку"
                    setOnClickListener { importText(source) }
                }, LinearLayout.LayoutParams(dp(48), dp(44)))
            }
            pageContent.addView(profile, params(0, 0, 0, 10))
        }

        importMessage = TextView(this).apply {
            text = prefs.getString("last_import_message", "").orEmpty()
            textSize = 11f; setTextColor(0xFFB7FF3C.toInt()); setPadding(dp(2), 0, dp(2), dp(6))
            visibility = if (text.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        }
        pageContent.addView(importMessage, params(0, 0, 0, 4))

        val outbounds = runCatching { JSONArray(prefs.getString("imported_outbounds_json", "[]")) }.getOrDefault(JSONArray())
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (outbounds.length() == 0) {
            list.addView(TextView(this).apply { text = "Серверов пока нет"; textSize = 14f; gravity = android.view.Gravity.CENTER; setTextColor(0xFF89958C.toInt()); setPadding(0, dp(24), 0, 0) }, LinearLayout.LayoutParams(-1, -2))
        } else {
            val selectedTag = prefs.getString("selected_server_tag", "")
            for (i in 0 until outbounds.length()) {
                val item = outbounds.optJSONObject(i) ?: continue
                val name = item.optString("tag").ifBlank { item.optString("sendThrough").ifBlank { "Сервер " + (i + 1) } }
                val selected = name == selectedTag
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(15), dp(16), dp(15))
                    background = rounded(if (selected) 0xFF1B2A1D.toInt() else 0xFF101512.toInt(), 16, if (selected) 0xFFB7FF3C.toInt() else 0xFF263128.toInt())
                    isClickable = true; isFocusable = true; setOnClickListener { selectServerAt(i) }
                }
                row.addView(TextView(this).apply { text = name; textSize = 15f; setTextColor(0xFFF4F7F2.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(TextView(this).apply { text = if (selected) "✓" else "›"; textSize = 20f; setTextColor(if (selected) 0xFFB7FF3C.toInt() else 0xFF89958C.toInt()) })
                list.addView(row, params(0, 0, 0, 8))
            }
        }
        scroll.addView(list)
        pageContent.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        pageContent.addView(Button(this).apply {
            text = "＋\nВСТАВИТЬ ИЗ БУФЕРА"; textSize = 14f; isAllCaps = false; gravity = android.view.Gravity.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(0xFFB7FF3C.toInt()); background = rounded(0xFF101512.toInt(), 20, 0xFFB7FF3C.toInt()); stateListAnimator = null
            setOnClickListener { importClipboard() }
        }, LinearLayout.LayoutParams(-1, dp(68)).apply { topMargin = dp(8) })
    }

    private fun outboundsCountLabel(): String {
        val count = runCatching { JSONArray(prefs.getString("imported_outbounds_json", "[]")).length() }.getOrDefault(0)
        return "$count серверов"
    }

    private fun navButton(symbol: String, title: String, color: Int, panel: Int, action: () -> Unit): LinearLayout {
        val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER; setPadding(dp(4), dp(5), dp(4), dp(5)); background = rounded(panel, 16); isClickable = true; isFocusable = true; setOnClickListener { action() } }
        item.addView(TextView(this).apply { text = symbol; textSize = 20f; gravity = android.view.Gravity.CENTER; setTextColor(color) })
        item.addView(TextView(this).apply { text = title; textSize = 10f; gravity = android.view.Gravity.CENTER; setTextColor(color); typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, dp(2), 0, 0) })
        return item
    }

    private fun renderConnectionState(connected: Boolean) {
        if (!::connectButton.isInitialized) return
        val panel = 0xFF101512.toInt()
        val neon = 0xFFB7FF3C.toInt()
        connectButton.text = if (connected) "⏻\nОТКЛЮЧИТЬ" else "⏻\nВКЛЮЧИТЬ"
        connectButton.setTextColor(if (connected) 0xFF050706.toInt() else neon)
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
        val sourceText = text.trim()
        if (sourceText.isEmpty()) { status.text = "Скопируй VLESS-ссылку, подписку или JSON"; return }
        status.text = "Проверяем ссылку или подписку..."
        Thread {
            try {
                val input = if (sourceText.startsWith("https://", true) || sourceText.startsWith("http://", true)) {
                    val connection = URL(sourceText).openConnection() as HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 20000
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("User-Agent", "CosmoX/1.1")
                    connection.setRequestProperty("Accept", "text/plain, */*")
                    try {
                        val code = connection.responseCode
                        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                        val bytes = stream?.use { inputStream ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val count = inputStream.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= 8 * 1024 * 1024) { "Подписка слишком большая (лимит 8 МБ)" }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        } ?: byteArrayOf()
                        if (code !in 200..299) error("Сервер подписки вернул HTTP " + code)
                        String(bytes, StandardCharsets.UTF_8).trim()
                    } finally { connection.disconnect() }
                } else sourceText
                val normalized = input.trim().removePrefix("\uFEFF").replace("\r\n", "\n").replace("\r", "\n")
                require(normalized.isNotBlank()) { "Подписка пустая" }
                val parsed = convertSubscriptionInOrder(normalized)
                val selectable = selectableOutbounds(parsed.first)
                require(selectable.length() > 0) { "Не найдено поддерживаемых серверов. Проверь формат подписки: поддерживаются VLESS, VMess, Trojan, Shadowsocks и совместимые ссылки Xray." }
                val message = if (parsed.second > 0) {
                    "Импортировано: " + selectable.length() + " • пропущено неподдерживаемых: " + parsed.second
                } else {
                    "Импортировано серверов: " + selectable.length()
                }
                runOnUiThread {
                    prefs.edit().putString("imported_outbounds_json", selectable.toString()).putString("profile_source", sourceText).putInt("server_count", selectable.length()).putString("last_import_message", message).apply()
                    showPage("servers")
                    if (::status.isInitialized) status.text = message
                }
            } catch (e: Exception) {
                val reason = e.message?.takeIf { it.isNotBlank() } ?: "неизвестная ошибка"
                runOnUiThread {
                    val message = "Ошибка импорта: " + reason
                    if (::status.isInitialized) status.text = message
                    prefs.edit().putString("last_import_message", message).apply()
                    if (currentPage == "servers") {
                        if (::importMessage.isInitialized) {
                            importMessage.text = message
                            importMessage.setTextColor(0xFFFF7777.toInt())
                            importMessage.visibility = android.view.View.VISIBLE
                        } else {
                            pageContent.addView(TextView(this).apply { this.text = message; textSize = 12f; setTextColor(0xFFFF7777.toInt()); setPadding(dp(4), dp(8), dp(4), dp(8)) })
                        }
                    }
                }
            }
        }.start()
    }

    private fun convertSubscriptionInOrder(text: String): Pair<JSONArray, Int> {
        val source = text.trim().removePrefix("\uFEFF")
        if (source.startsWith("{")) {
            val response = XrayBridge.invoke("convertShareLinksToXrayJson", JSONObject().put("text", source))
            val data = response.optJSONObject("data") ?: error("Парсер не вернул список серверов")
            return Pair(data.optJSONArray("outbounds") ?: error("В JSON не найден список серверов"), 0)
        }

        var payload = source
        if (!payload.contains("://")) {
            val compact = payload.filterNot { it.isWhitespace() }
            val decoded = runCatching {
                val bytes = android.util.Base64.decode(compact, android.util.Base64.DEFAULT or android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                String(bytes, StandardCharsets.UTF_8).trim().removePrefix("\uFEFF")
            }.getOrNull()
            if (!decoded.isNullOrBlank() && decoded.contains("://")) payload = decoded
        }

        val lines = payload.replace("\r\n", "\n").replace("\r", "\n").split("\n")
            .map { it.trim() }
            .filter { it.contains("://") && !it.startsWith("#") && !it.startsWith("//") }
        if (lines.isEmpty()) {
            val response = XrayBridge.invoke("convertShareLinksToXrayJson", JSONObject().put("text", source))
            val data = response.optJSONObject("data") ?: error("Формат подписки не распознан")
            return Pair(data.optJSONArray("outbounds") ?: error("Парсер не вернул список серверов"), 0)
        }

        val combined = JSONArray()
        val usedTags = mutableSetOf<String>()
        var skipped = 0
        for (line in lines) {
            try {
                val response = XrayBridge.invoke("convertShareLinksToXrayJson", JSONObject().put("text", line))
                val data = response.optJSONObject("data") ?: error("Некорректная ссылка")
                val outbounds = data.optJSONArray("outbounds") ?: error("Сервер не распознан")
                if (outbounds.length() == 0) { skipped++; continue }
                for (i in 0 until outbounds.length()) {
                    val item = outbounds.optJSONObject(i) ?: continue
                    val originalTag = item.optString("tag").ifBlank { item.optString("sendThrough").ifBlank { "Сервер " + (combined.length() + 1) } }
                    var uniqueTag = originalTag
                    var suffix = 2
                    while (usedTags.contains(uniqueTag)) {
                        uniqueTag = "$originalTag ($suffix)"
                        suffix++
                    }
                    usedTags.add(uniqueTag)
                    item.put("tag", uniqueTag)
                    combined.put(item)
                }
            } catch (_: Exception) {
                skipped++
            }
        }
        if (combined.length() == 0) {
            val response = XrayBridge.invoke("convertShareLinksToXrayJson", JSONObject().put("text", source))
            val data = response.optJSONObject("data") ?: error("Не удалось распознать подписку")
            return Pair(data.optJSONArray("outbounds") ?: error("Парсер не вернул список серверов"), skipped)
        }
        return Pair(combined, skipped)
    }

    private fun selectableOutbounds(outbounds: JSONArray): JSONArray {
        val result = JSONArray()
        val usedTags = mutableSetOf<String>()
        for (i in 0 until outbounds.length()) {
            val item = outbounds.optJSONObject(i) ?: continue
            val protocol = item.optString("protocol").lowercase()
            if (protocol !in setOf("freedom", "direct", "blackhole", "block", "dns", "")) {
                val baseTag = item.optString("tag").ifBlank { item.optString("sendThrough").ifBlank { "Сервер " + (result.length() + 1) } }
                var tag = baseTag
                var suffix = 2
                while (usedTags.contains(tag)) { tag = "$baseTag ($suffix)"; suffix++ }
                usedTags.add(tag)
                item.put("tag", tag)
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

    private fun selectServerAt(index: Int) {
        try {
            val outbounds = JSONArray(prefs.getString("imported_outbounds_json", "[]"))
            require(index in 0 until outbounds.length()) { "Сервер не найден" }
            val chosen = outbounds.getJSONObject(index)
            val tag = chosen.optString("tag").ifBlank { chosen.optString("sendThrough").ifBlank { "proxy-" + (index + 1) } }
            chosen.put("tag", tag)
            outbounds.put(index, chosen)
            val config = JSONObject()
                .put("log", JSONObject().put("loglevel", "warning").put("access", "none"))
                .put("inbounds", JSONArray().put(JSONObject().put("tag", "tun-in").put("port", 0).put("protocol", "tun").put("settings", JSONObject().put("name", "CosmoX").put("mtu", 1500))))
                .put("outbounds", outbounds)
                .put("dns", JSONObject().put("servers", JSONArray().put("1.1.1.1").put("2606:4700:4700::1111")))
                .put("routing", JSONObject().put("domainStrategy", "IPIfNonMatch").put("rules", JSONArray().put(JSONObject().put("type", "field").put("inboundTag", JSONArray().put("tun-in")).put("outboundTag", tag))))
            prefs.edit().putString(CosmoVpnService.KEY_CONFIG, config.toString()).putString("imported_outbounds_json", outbounds.toString()).putString("selected_server_tag", tag).putString("selected_server_name", tag).putInt("server_count", outbounds.length()).apply()
            if (::status.isInitialized) status.text = "Выбран сервер: " + tag
            showPage("servers")
        } catch (e: Exception) {
            if (::status.isInitialized) status.text = "Не удалось выбрать сервер: " + (e.message ?: "ошибка")
        }
    }

    private fun openSavedServerPicker() {
        showPage("servers")
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
        if (::servers.isInitialized) servers.text = prefs.getString("selected_server_name", null) ?: "Сервер не выбран"
        if (::status.isInitialized) {
            val connected = prefs.getBoolean("connected", false)
            status.text = if (connected) "●  Подключено • Xray-core" else "●  Отключено"
            renderConnectionState(connected)
        }
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
