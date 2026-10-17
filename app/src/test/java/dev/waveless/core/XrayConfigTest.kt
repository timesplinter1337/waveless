package dev.waveless.core

import dev.waveless.data.AppSettings
import dev.waveless.data.Profile
import dev.waveless.data.Protocol
import dev.waveless.data.RoutingMode
import dev.waveless.data.ShareLinks
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class XrayConfigTest {
    private val reality = ShareLinks.parse(
        "vless://0b6e1c0e-8b5e-4a3c-9a5e-2f1d3c4b5a69@example.com:443?type=tcp&security=reality" +
            "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179e30d4fc2&sni=www.microsoft.com" +
            "&fp=chrome&flow=xtls-rprx-vision#reality"
    )!!

    private fun parse(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `tun config has tun inbound, proxy first and dns hijack first`() {
        val cfg = parse(XrayConfig.forTun(reality, AppSettings()))
        val inbound = cfg["inbounds"]!!.jsonArray.single().jsonObject
        assertEquals("tun", inbound["protocol"]!!.jsonPrimitive.content)

        val outbounds = cfg["outbounds"]!!.jsonArray.map { it.jsonObject["tag"]!!.jsonPrimitive.content }
        assertEquals(listOf("proxy", "direct", "block", "dns-out"), outbounds)

        val rules = cfg["routing"]!!.jsonObject["rules"]!!.jsonArray
        assertEquals("dns-out", rules.first().jsonObject["outboundTag"]!!.jsonPrimitive.content)
    }

    @Test
    fun `reality stream settings carry key material and no tls block`() {
        val stream = XrayConfig.outbound(reality)["streamSettings"]!!.jsonObject
        val rs = stream["realitySettings"]!!.jsonObject
        assertEquals("SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc", rs["publicKey"]!!.jsonPrimitive.content)
        assertEquals("www.microsoft.com", rs["serverName"]!!.jsonPrimitive.content)
        assertFalse("tlsSettings" in stream)
        val user = XrayConfig.outbound(reality)["settings"]!!.jsonObject["vnext"]!!.jsonArray[0]
            .jsonObject["users"]!!.jsonArray[0].jsonObject
        assertEquals("xtls-rprx-vision", user["flow"]!!.jsonPrimitive.content)
    }

    @Test
    fun `routing presets add the expected direct rules`() {
        fun rules(mode: RoutingMode, ads: Boolean = false): JsonArray =
            parse(XrayConfig.forTun(reality, AppSettings(routingMode = mode, blockAds = ads)))["routing"]!!
                .jsonObject["rules"]!!.jsonArray

        assertEquals(1, rules(RoutingMode.GLOBAL).size)
        assertEquals(2, rules(RoutingMode.BYPASS_LAN).size)
        val ru = rules(RoutingMode.BYPASS_RU, ads = true)
        assertEquals(listOf("dns-out", "block", "direct", "direct", "direct"), ru.map { it.jsonObject["outboundTag"]!!.jsonPrimitive.content })
        assertTrue(ru.toString().contains("geoip:ru"))
    }

    @Test
    fun `shadowsocks has no stream settings`() {
        val ss = Profile("id", "ss", Protocol.SHADOWSOCKS, "h", 1, "pw", method = "aes-128-gcm")
        val out: JsonObject = XrayConfig.outbound(ss)
        assertEquals("shadowsocks", out["protocol"]!!.jsonPrimitive.content)
        assertFalse("streamSettings" in out)
    }

    @Test
    fun `dns servers are split on commas and spaces`() {
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), XrayConfig.dnsServers(" 1.1.1.1, 8.8.8.8 "))
        assertEquals(listOf("1.1.1.1"), XrayConfig.dnsServers(""))
    }

    /**
     * Writes configs for every transport to build/xray-configs so they can be checked
     * against the real xray-core loader (see scripts/validate-configs.sh).
     */
    @Test
    fun `dump sample configs`() {
        val dir = File(System.getProperty("xray.configDump") ?: return).apply { mkdirs() }
        val samples = listOf(
            reality,
            ShareLinks.parse("vless://u-u-i-d@h:443?type=ws&security=tls&path=%2Fws&host=c.example&sni=c.example&alpn=h2%2Chttp%2F1.1#ws")!!,
            ShareLinks.parse("vless://u@h:443?type=xhttp&security=tls&path=%2Fx&mode=packet-up&sni=h#xhttp")!!,
            ShareLinks.parse("vless://u@h:443?type=httpupgrade&security=none&path=%2Fu&host=h#hu")!!,
            ShareLinks.parse("trojan://pw@h:443?type=grpc&serviceName=svc&mode=multi&sni=h#grpc")!!,
            ShareLinks.parse("vless://u@h:80?type=tcp&headerType=http&host=a.com&path=%2F#http")!!,
            Profile("v", "vmess", Protocol.VMESS, "h", 443, "b831381d-6324-4d53-ad4f-8cda48b30811", method = "auto", network = "ws", path = "/", security = "tls"),
            Profile("s", "ss", Protocol.SHADOWSOCKS, "h", 8388, "pw", method = "aes-256-gcm"),
        )
        samples.forEachIndexed { i, p ->
            val settings = AppSettings(routingMode = RoutingMode.entries[i % 3], blockAds = i % 2 == 0, ipv6 = i % 2 == 1)
            File(dir, "tun-$i-${p.name}.json").writeText(XrayConfig.forTun(p, settings))
            File(dir, "delay-$i-${p.name}.json").writeText(XrayConfig.forDelayTest(p))
        }

        // Served by local xray instances in tools/xray-check.
        val uuid = "b831381d-6324-4d53-ad4f-8cda48b30811"
        val e2e = listOf(
            "vless://$uuid@127.0.0.1:18443?type=tcp&security=none#vless-tcp",
            "vless://$uuid@127.0.0.1:18445?type=ws&security=none&path=%2Fws#vless-ws",
            "ss://" + java.util.Base64.getEncoder().encodeToString("aes-128-gcm:pw".toByteArray()) + "@127.0.0.1:18388#ss",
        ).map { ShareLinks.parse(it)!! } +
            Profile("e", "vmess-tcp", Protocol.VMESS, "127.0.0.1", 18444, uuid, method = "aes-128-gcm")
        e2e.forEach { File(dir, "e2e-${it.name}.json").writeText(XrayConfig.forDelayTest(it)) }
    }
}
