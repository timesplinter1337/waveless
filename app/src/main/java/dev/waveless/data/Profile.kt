package dev.waveless.data

import kotlinx.serialization.Serializable

@Serializable
enum class Protocol(val scheme: String, val label: String) {
    VLESS("vless", "VLESS"),
    VMESS("vmess", "VMess"),
    TROJAN("trojan", "Trojan"),
    SHADOWSOCKS("ss", "Shadowsocks"),
}

/** One outbound server. Field names follow the de-facto share link parameters. */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val protocol: Protocol,
    val address: String,
    val port: Int,
    /** UUID for VLESS/VMess, password for Trojan/Shadowsocks. */
    val secret: String,
    /** VMess `security` (cipher) or Shadowsocks `method`. */
    val method: String = "",
    /** VLESS `encryption`. */
    val encryption: String = "none",
    val flow: String = "",
    val network: String = "tcp",
    val headerType: String = "",
    val host: String = "",
    val path: String = "",
    val serviceName: String = "",
    /** xhttp `mode`, or gRPC `multi` when [network] is grpc. */
    val mode: String = "",
    val security: String = "none",
    val sni: String = "",
    val alpn: String = "",
    val fingerprint: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val pinnedPeerCertSha256: String = "",
    val subscriptionId: String? = null,
)

@Serializable
data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val updatedAt: Long = 0,
)
