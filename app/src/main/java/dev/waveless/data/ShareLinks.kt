package dev.waveless.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID

/**
 * Parses and produces the share links used by xray clients
 * (vless://, vmess://, trojan://, ss://) and base64 subscription bodies.
 */
object ShareLinks {

    /** Parses every link found in [text]; falls back to treating the whole text as base64. */
    fun parseAll(text: String, subscriptionId: String? = null): List<Profile> {
        val direct = parseLines(text, subscriptionId)
        if (direct.isNotEmpty()) return direct
        val decoded = decodeBase64OrNull(text) ?: return emptyList()
        return parseLines(decoded, subscriptionId)
    }

    fun parse(link: String, subscriptionId: String? = null): Profile? = runCatching {
        val trimmed = link.trim()
        when (trimmed.substringBefore("://").lowercase()) {
            "vless" -> parseUrlStyle(trimmed, Protocol.VLESS)
            "trojan" -> parseUrlStyle(trimmed, Protocol.TROJAN)
            "vmess" -> parseVmess(trimmed)
            "ss" -> parseShadowsocks(trimmed)
            else -> null
        }
    }.getOrNull()?.copy(subscriptionId = subscriptionId)

    fun toLink(p: Profile): String = when (p.protocol) {
        Protocol.VLESS, Protocol.TROJAN -> buildString {
            append(p.protocol.scheme).append("://").append(enc(p.secret)).append('@')
            append(hostForUrl(p.address)).append(':').append(p.port)
            val q = linkedMapOf(
                "type" to p.network,
                "security" to p.security,
                "encryption" to if (p.protocol == Protocol.VLESS) p.encryption else "",
                "flow" to p.flow,
                "headerType" to p.headerType,
                "host" to p.host,
                "path" to p.path,
                "serviceName" to p.serviceName,
                "mode" to p.mode,
                "sni" to p.sni,
                "alpn" to p.alpn,
                "fp" to p.fingerprint,
                "pbk" to p.publicKey,
                "sid" to p.shortId,
                "spx" to p.spiderX,
                "pcs" to p.pinnedPeerCertSha256,
            ).filterValues { it.isNotEmpty() }
            if (q.isNotEmpty()) {
                append('?')
                append(q.entries.joinToString("&") { "${it.key}=${enc(it.value)}" })
            }
            append('#').append(enc(p.name))
        }

        Protocol.VMESS -> {
            val json = buildJsonObject {
                put("v", "2")
                put("ps", p.name)
                put("add", p.address)
                put("port", p.port.toString())
                put("id", p.secret)
                put("aid", "0")
                put("scy", p.method.ifEmpty { "auto" })
                put("net", p.network)
                put("type", p.headerType.ifEmpty { "none" })
                put("host", p.host)
                put("path", if (p.network == "grpc") p.serviceName else p.path)
                put("tls", if (p.security == "none") "" else p.security)
                put("sni", p.sni)
                put("alpn", p.alpn)
                put("fp", p.fingerprint)
            }
            "vmess://" + Base64.getEncoder().encodeToString(json.toString().toByteArray())
        }

        Protocol.SHADOWSOCKS -> {
            val userInfo = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("${p.method}:${p.secret}".toByteArray())
            "ss://$userInfo@${hostForUrl(p.address)}:${p.port}#${enc(p.name)}"
        }
    }

    private fun parseLines(text: String, subscriptionId: String?): List<Profile> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.contains("://") }
            .mapNotNull { parse(it, subscriptionId) }
            .toList()

    private fun parseUrlStyle(link: String, protocol: Protocol): Profile {
        val u = UrlParts.of(link)
        val q = u.query
        val security = q["security"].orEmpty().ifEmpty {
            if (protocol == Protocol.TROJAN) "tls" else "none"
        }
        val network = q["type"].orEmpty().ifEmpty { "tcp" }.let { if (it == "raw") "tcp" else it }
        return Profile(
            id = newId(),
            name = u.fragment.ifEmpty { "${u.host}:${u.port}" },
            protocol = protocol,
            address = u.host,
            port = u.port,
            secret = u.userInfo,
            encryption = q["encryption"].orEmpty().ifEmpty { "none" },
            flow = q["flow"].orEmpty(),
            network = network,
            headerType = q["headerType"].orEmpty(),
            host = q["host"].orEmpty(),
            path = q["path"].orEmpty(),
            serviceName = q["serviceName"].orEmpty(),
            mode = q["mode"].orEmpty(),
            security = security,
            sni = q["sni"].orEmpty().ifEmpty { q["peer"].orEmpty() },
            alpn = q["alpn"].orEmpty(),
            fingerprint = q["fp"].orEmpty(),
            publicKey = q["pbk"].orEmpty(),
            shortId = q["sid"].orEmpty(),
            spiderX = q["spx"].orEmpty(),
            pinnedPeerCertSha256 = q["pcs"].orEmpty(),
        )
    }

    private fun parseVmess(link: String): Profile {
        val body = decodeBase64OrNull(link.substringAfter("://"))
            ?: error("vmess link is not base64")
        val o = Json.parseToJsonElement(body).jsonObject
        fun s(key: String) = (o[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val network = s("net").ifEmpty { "tcp" }
        val tls = s("tls")
        return Profile(
            id = newId(),
            name = s("ps").ifEmpty { "${s("add")}:${s("port")}" },
            protocol = Protocol.VMESS,
            address = s("add"),
            port = s("port").toInt(),
            secret = s("id"),
            method = s("scy").ifEmpty { "auto" },
            network = network,
            headerType = s("type").takeIf { it != "none" }.orEmpty(),
            host = s("host"),
            path = if (network == "grpc") "" else s("path"),
            serviceName = if (network == "grpc") s("path") else "",
            security = if (tls == "tls" || tls == "reality") tls else "none",
            sni = s("sni"),
            alpn = s("alpn"),
            fingerprint = s("fp"),
        )
    }

    private fun parseShadowsocks(link: String): Profile {
        val withoutScheme = link.substringAfter("://")
        val fragment = withoutScheme.substringAfter('#', "").let(::dec)
        val main = withoutScheme.substringBefore('#').substringBefore('?').trimEnd('/')
        // Legacy form: the whole "method:password@host:port" is base64.
        val expanded = if ('@' in main) main else decodeBase64OrNull(main) ?: error("bad ss link")
        val userPart = expanded.substringBeforeLast('@')
        val hostPart = expanded.substringAfterLast('@')
        val credentials = if (':' in userPart) dec(userPart) else decodeBase64OrNull(userPart)
            ?: error("bad ss credentials")
        val u = UrlParts.of("ss://x@$hostPart")
        return Profile(
            id = newId(),
            name = fragment.ifEmpty { "${u.host}:${u.port}" },
            protocol = Protocol.SHADOWSOCKS,
            address = u.host,
            port = u.port,
            secret = credentials.substringAfter(':'),
            method = credentials.substringBefore(':'),
        )
    }

    private class UrlParts(
        val userInfo: String,
        val host: String,
        val port: Int,
        val query: Map<String, String>,
        val fragment: String,
    ) {
        companion object {
            fun of(link: String): UrlParts {
                val rest = link.substringAfter("://")
                val fragment = dec(rest.substringAfter('#', ""))
                val beforeFragment = rest.substringBefore('#')
                val query = beforeFragment.substringAfter('?', "")
                    .split('&')
                    .filter { '=' in it }
                    .associate { dec(it.substringBefore('=')) to dec(it.substringAfter('=')) }
                val authority = beforeFragment.substringBefore('?').substringBefore('/')
                val userInfo = dec(authority.substringBeforeLast('@', ""))
                val hostPort = authority.substringAfterLast('@')
                val host: String
                val port: String
                if (hostPort.startsWith('[')) {
                    host = hostPort.substring(1, hostPort.indexOf(']'))
                    port = hostPort.substringAfter("]:")
                } else {
                    host = hostPort.substringBeforeLast(':')
                    port = hostPort.substringAfterLast(':')
                }
                require(host.isNotEmpty()) { "missing host" }
                return UrlParts(userInfo, host, port.toInt(), query, fragment)
            }
        }
    }

    fun decodeBase64OrNull(text: String): String? {
        val clean = text.filterNot { it.isWhitespace() }.replace('-', '+').replace('_', '/')
        if (clean.isEmpty()) return null
        val padded = clean.padEnd((clean.length + 3) / 4 * 4, '=')
        return runCatching { String(Base64.getDecoder().decode(padded)) }.getOrNull()
    }

    private fun hostForUrl(host: String) = if (':' in host) "[$host]" else host

    private fun dec(s: String): String = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun newId() = UUID.randomUUID().toString()
}
