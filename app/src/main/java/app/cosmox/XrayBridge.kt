package app.cosmox

import android.net.VpnService
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import org.json.JSONObject

/** Thin facade for the official XTLS/libXray gomobile AAR built by GitHub Actions. */
object XrayBridge {
    private val classNames = listOf("libXray.LibXray", "go.libXray.LibXray")

    private fun apiClass(): Class<*> = classNames.firstNotNullOfOrNull { name ->
        runCatching { Class.forName(name) }.getOrNull()
    } ?: error("libXray AAR is missing or its generated API changed")

    fun invoke(method: String, payload: JSONObject = JSONObject()): JSONObject {
        val request = JSONObject().put("apiVersion", 3).put("method", method).put("payload", payload)
        val clazz = apiClass()
        val call = clazz.methods.firstOrNull {
            it.name.equals("invoke", true) && it.parameterTypes.contentEquals(arrayOf(String::class.java))
        } ?: error("libXray Invoke API not found")
        val raw = call.invoke(null, request.toString()) as? String ?: error("Empty response from Xray-core")
        val response = JSONObject(raw)
        if (!response.optBoolean("success", false)) error(response.optString("error", "Xray-core operation failed"))
        return response
    }

    fun registerProtector(service: VpnService) {
        val clazz = apiClass()
        val method = clazz.methods.firstOrNull {
            it.name.equals("registerDialerController", true) && it.parameterTypes.size == 1
        } ?: error("libXray socket-protection API not found")
        val iface = method.parameterTypes[0]
        val callback = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface), InvocationHandler { proxy, called, args ->
            when (called.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "CosmoXSocketProtector"
                else -> (args?.firstOrNull() as? Number)?.toInt()?.let(service::protect) ?: false
            }
        })
        method.invoke(null, callback)
    }

    fun setDns(service: VpnService, endpoint: String) {
        val clazz = apiClass()
        val method = clazz.methods.firstOrNull {
            it.name.equals("setDNS", true) && it.parameterTypes.size == 2
        } ?: error("libXray DNS API not found")
        val iface = method.parameterTypes[0]
        val callback = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface), InvocationHandler { proxy, called, args ->
            when (called.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "CosmoXDnsProtector"
                else -> (args?.firstOrNull() as? Number)?.toInt()?.let(service::protect) ?: false
            }
        })
        method.invoke(null, callback, endpoint)
    }

    fun resetDns() {
        runCatching {
            apiClass().methods.firstOrNull {
                it.name.equals("resetDNS", true) && it.parameterTypes.isEmpty()
            }?.invoke(null)
        }
    }
}
