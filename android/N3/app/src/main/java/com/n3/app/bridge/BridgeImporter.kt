package com.n3.app.bridge

import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object BridgeImporter {
    private const val TAG = "N3/BridgeImport"

    fun importOvpn(stream: InputStream, fileName: String = ""): BridgeElement? = try {
        val content = BufferedReader(InputStreamReader(stream)).readText()
        val name = fileName.ifBlank { extractOvpnName(content) }
        BridgeElement(type = BridgeType.OPENVPN, name = name, config = content)
    } catch (e: Exception) { Log.e(TAG, "OVPN import: ${e.message}"); null }

    fun importWireGuard(stream: InputStream, fileName: String = ""): BridgeElement? = try {
        val content = BufferedReader(InputStreamReader(stream)).readText()
        val name = fileName.ifBlank { extractWgName(content) }
        BridgeElement(type = BridgeType.WIREGUARD, name = name, config = content)
    } catch (e: Exception) { Log.e(TAG, "WG import: ${e.message}"); null }

    fun importV2Ray(text: String): BridgeElement? = try {
        when {
            text.startsWith("vmess://") -> parseVmess(text)
            text.startsWith("vless://") -> parseVless(text)
            text.startsWith("trojan://") -> parseTrojan(text)
            text.startsWith("ss://") -> parseShadowsocks(text)
            else -> { Log.w(TAG, "Unknown link format"); null }
        }
    } catch (e: Exception) { Log.e(TAG, "V2Ray import: ${e.message}"); null }

    private fun extractOvpnName(content: String): String {
        val m = Regex("""^#\s*(\S.*)""").find(content)
        return m?.groupValues?.get(1)?.take(40) ?: "OpenVPN-${content.hashCode().toString().take(8)}"
    }

    private fun extractWgName(content: String): String {
        val m = Regex("""\[Interface].*?\n#\s*(\S.*)""").find(content)
        return m?.groupValues?.get(1)?.take(40) ?: "WireGuard-${content.hashCode().toString().take(8)}"
    }

    private fun parseVmess(link: String): BridgeElement {
        val b64 = link.removePrefix("vmess://")
        val json = try {
            String(Base64.decode(b64, Base64.URL_SAFE), Charsets.UTF_8)
        } catch (e: Exception) {
            String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
        }
        val name = org.json.JSONObject(json).optString("ps", "V2Ray")
        return BridgeElement(type = BridgeType.V2RAY, name = name, config = link)
    }

    private fun parseVless(link: String): BridgeElement {
        val name = Uri.parse(link).getQueryParameter("remark") ?: "VLESS"
        return BridgeElement(type = BridgeType.V2RAY, name = name, config = link)
    }

    private fun parseTrojan(link: String): BridgeElement {
        val hashIdx = link.indexOf('#')
        val name = if (hashIdx >= 0) link.substring(hashIdx + 1) else "Trojan"
        return BridgeElement(type = BridgeType.V2RAY, name = name, config = link)
    }

    private fun parseShadowsocks(link: String): BridgeElement {
        val hashIdx = link.indexOf('#')
        val name = if (hashIdx >= 0) link.substring(hashIdx + 1) else "Shadowsocks"
        return BridgeElement(type = BridgeType.V2RAY, name = name, config = link)
    }
}
