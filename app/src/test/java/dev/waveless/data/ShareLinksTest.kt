package dev.waveless.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class ShareLinksTest {

    @Test
    fun `parses vless reality link`() {
        val p = ShareLinks.parse(
            "vless://0b6e1c0e-8b5e-4a3c-9a5e-2f1d3c4b5a69@example.com:443" +
                "?type=tcp&security=reality&pbk=Pk_123-abc&sid=6ba85179e30d4fc2&sni=www.microsoft.com" +
                "&fp=chrome&flow=xtls-rprx-vision&encryption=none#My%20Server%20%F0%9F%87%A9%F0%9F%87%AA"
        )!!
        assertEquals(Protocol.VLESS, p.protocol)
        assertEquals("example.com", p.address)
        assertEquals(443, p.port)
        assertEquals("0b6e1c0e-8b5e-4a3c-9a5e-2f1d3c4b5a69", p.secret)
        assertEquals("reality", p.security)
        assertEquals("Pk_123-abc", p.publicKey)
        assertEquals("6ba85179e30d4fc2", p.shortId)
        assertEquals("www.microsoft.com", p.sni)
        assertEquals("xtls-rprx-vision", p.flow)
        assertEquals("My Server 🇩🇪", p.name)
    }

    @Test
    fun `parses vless ws link with path and ipv6 host`() {
        val p = ShareLinks.parse(
            "vless://uuid@[2001:db8::1]:8443/?type=ws&security=tls&path=%2Fws%3Fed%3D2048&host=cdn.example.com#ws"
        )!!
        assertEquals("2001:db8::1", p.address)
        assertEquals(8443, p.port)
        assertEquals("ws", p.network)
        assertEquals("/ws?ed=2048", p.path)
        assertEquals("cdn.example.com", p.host)
    }

    @Test
    fun `trojan defaults to tls`() {
        val p = ShareLinks.parse("trojan://p%40ss@t.example.org:443?sni=t.example.org#t")!!
        assertEquals(Protocol.TROJAN, p.protocol)
        assertEquals("p@ss", p.secret)
        assertEquals("tls", p.security)
    }

    @Test
    fun `parses vmess json link`() {
        val json = """{"v":"2","ps":"vm","add":"1.2.3.4","port":"10086","id":"abcd","aid":"0","scy":"auto",
            |"net":"grpc","type":"none","host":"","path":"svc","tls":"tls","sni":"s.example"}""".trimMargin()
        val p = ShareLinks.parse("vmess://" + Base64.getEncoder().encodeToString(json.toByteArray()))!!
        assertEquals(Protocol.VMESS, p.protocol)
        assertEquals(10086, p.port)
        assertEquals("grpc", p.network)
        assertEquals("svc", p.serviceName)
        assertEquals("tls", p.security)
        assertEquals("", p.headerType)
    }

    @Test
    fun `parses shadowsocks sip002 and legacy forms`() {
        val userInfo = Base64.getUrlEncoder().withoutPadding().encodeToString("aes-256-gcm:secret".toByteArray())
        val sip002 = ShareLinks.parse("ss://$userInfo@ss.example:8388#ss1")!!
        assertEquals("aes-256-gcm", sip002.method)
        assertEquals("secret", sip002.secret)
        assertEquals(8388, sip002.port)

        val plain = ShareLinks.parse("ss://2022-blake3-aes-128-gcm:a%2Bb%3D@ss.example:443#ss2")!!
        assertEquals("2022-blake3-aes-128-gcm", plain.method)
        assertEquals("a+b=", plain.secret)

        val legacy = ShareLinks.parse(
            "ss://" + Base64.getEncoder().encodeToString("chacha20-ietf-poly1305:pw@10.0.0.1:1234".toByteArray()) + "#old"
        )!!
        assertEquals("10.0.0.1", legacy.address)
        assertEquals(1234, legacy.port)
        assertEquals("pw", legacy.secret)
    }

    @Test
    fun `decodes base64 subscription body`() {
        val body = listOf(
            "vless://a@h1:443?security=tls#one",
            "trojan://b@h2:443#two",
            "garbage line",
        ).joinToString("\n")
        val encoded = Base64.getEncoder().encodeToString(body.toByteArray())
        val profiles = ShareLinks.parseAll(encoded, subscriptionId = "sub")
        assertEquals(listOf("one", "two"), profiles.map { it.name })
        assertEquals(listOf("sub", "sub"), profiles.map { it.subscriptionId })
    }

    @Test
    fun `recognises pasted subscription urls`() {
        assertEquals(
            listOf("https://sub.example.com/api/sub/abc", "http://1.2.3.4:2096/sub/x"),
            ShareLinks.subscriptionUrls(" https://sub.example.com/api/sub/abc \n\nhttp://1.2.3.4:2096/sub/x\n"),
        )
        // Share links, mixed content and prose are not subscriptions.
        assertEquals(emptyList<String>(), ShareLinks.subscriptionUrls("vless://u@h:443#x"))
        assertEquals(emptyList<String>(), ShareLinks.subscriptionUrls("https://a.example/sub\nvless://u@h:443#x"))
        assertEquals(emptyList<String>(), ShareLinks.subscriptionUrls("see https://a.example/sub"))
        assertEquals(emptyList<String>(), ShareLinks.subscriptionUrls("   "))
    }

    @Test
    fun `rejects unsupported links`() {
        assertNull(ShareLinks.parse("hysteria2://x@h:1"))
        assertNull(ShareLinks.parse("vless://missing-port@host"))
    }

    @Test
    fun `round trips through share links`() {
        val links = listOf(
            "vless://u@h.example:443?type=xhttp&security=reality&encryption=none&path=%2Fx&mode=auto" +
                "&sni=a.example&fp=chrome&pbk=key&sid=ab#X%20Y",
            "trojan://pw@h.example:443?type=grpc&security=tls&serviceName=svc&sni=h.example#T",
            "ss://" + Base64.getUrlEncoder().withoutPadding().encodeToString("aes-128-gcm:pw".toByteArray()) + "@h:1#S",
        )
        for (link in links) {
            val first = ShareLinks.parse(link)!!
            val second = ShareLinks.parse(ShareLinks.toLink(first))!!
            assertEquals(first.copy(id = ""), second.copy(id = ""))
        }
        val vmess = ShareLinks.parse(
            "vmess://" + Base64.getEncoder().encodeToString(
                """{"ps":"v","add":"h","port":443,"id":"u","net":"ws","path":"/p","host":"c","tls":"tls"}""".toByteArray()
            )
        )!!
        assertEquals(vmess.copy(id = ""), ShareLinks.parse(ShareLinks.toLink(vmess))!!.copy(id = ""))
    }
}
