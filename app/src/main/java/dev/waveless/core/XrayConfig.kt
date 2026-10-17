package dev.waveless.core

import dev.waveless.data.AppSettings
import dev.waveless.data.Profile
import dev.waveless.data.Protocol
import dev.waveless.data.RoutingMode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Builds xray-core JSON configs from a [Profile] and [AppSettings]. */
object XrayConfig {
    const val PROXY_TAG = "proxy"
    const val TUN_TAG = "tun"

    /** DNS server address handed to Android; queries to it are answered by xray's DNS module. */
    const val TUN_DNS = "172.19.0.2"
    const val TUN_ADDRESS = "172.19.0.1"
    const val TUN_ADDRESS6 = "fdfe:dcba:9876::1"
    const val TUN_MTU = 1500

    fun forTun(profile: Profile, settings: AppSettings): String = buildJsonObject {
        putJsonObject("log") { put("loglevel", "warning") }
        putJsonObject("dns") {
            put("servers", strings(dnsServers(settings.remoteDns)))
            put("queryStrategy", if (settings.ipv6) "UseIP" else "UseIPv4")
        }
        putJsonArray("inbounds") {
            addJsonObject {
                put("tag", TUN_TAG)
                put("port", 0)
                put("protocol", "tun")
                putJsonObject("settings") {
                    put("name", "xray0")
                    put("mtu", TUN_MTU)
                }
                putJsonObject("sniffing") {
                    put("enabled", true)
                    put("destOverride", strings(listOf("http", "tls", "quic")))
                    put("routeOnly", true)
                }
            }
        }
        putJsonArray("outbounds") {
            add(outbound(profile))
            addJsonObject { put("tag", "direct"); put("protocol", "freedom") }
            addJsonObject { put("tag", "block"); put("protocol", "blackhole") }
            addJsonObject { put("tag", "dns-out"); put("protocol", "dns") }
        }
        putJsonObject("routing") {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", routingRules(settings))
        }
        putJsonObject("stats") {}
        putJsonObject("policy") {
            putJsonObject("system") {
                put("statsOutboundUplink", true)
                put("statsOutboundDownlink", true)
            }
        }
    }.toString()

    /** Minimal config for `Libv2ray.measureOutboundDelay`, which only keeps outbounds. */
    fun forDelayTest(profile: Profile): String = buildJsonObject {
        putJsonObject("log") { put("loglevel", "none") }
        putJsonArray("outbounds") { add(outbound(profile)) }
    }.toString()

    private fun routingRules(settings: AppSettings) = buildJsonArray {
        addJsonObject {
            put("inboundTag", strings(listOf(TUN_TAG)))
            put("port", "53")
            put("outboundTag", "dns-out")
        }
        if (settings.blockAds) {
            addJsonObject {
                put("domain", strings(listOf("geosite:category-ads-all")))
                put("outboundTag", "block")
            }
        }
        if (settings.routingMode != RoutingMode.GLOBAL) {
            addJsonObject {
                put("ip", strings(listOf("geoip:private")))
                put("outboundTag", "direct")
            }
        }
        if (settings.routingMode == RoutingMode.BYPASS_RU) {
            addJsonObject {
                put("domain", strings(listOf("geosite:category-ru", "domain:ru", "domain:su", "domain:xn--p1ai")))
                put("outboundTag", "direct")
            }
            addJsonObject {
                put("ip", strings(listOf("geoip:ru")))
                put("outboundTag", "direct")
            }
        }
    }

    fun outbound(p: Profile): JsonObject = buildJsonObject {
        put("tag", PROXY_TAG)
        put("protocol", if (p.protocol == Protocol.SHADOWSOCKS) "shadowsocks" else p.protocol.scheme)
        putJsonObject("settings") {
            when (p.protocol) {
                Protocol.VLESS -> putJsonArray("vnext") {
                    addJsonObject {
                        server(p)
                        putJsonArray("users") {
                            addJsonObject {
                                put("id", p.secret)
                                put("encryption", p.encryption.ifEmpty { "none" })
                                if (p.flow.isNotEmpty()) put("flow", p.flow)
                            }
                        }
                    }
                }

                Protocol.VMESS -> putJsonArray("vnext") {
                    addJsonObject {
                        server(p)
                        putJsonArray("users") {
                            addJsonObject {
                                put("id", p.secret)
                                put("security", p.method.ifEmpty { "auto" })
                            }
                        }
                    }
                }

                Protocol.TROJAN -> putJsonArray("servers") {
                    addJsonObject { server(p); put("password", p.secret) }
                }

                Protocol.SHADOWSOCKS -> putJsonArray("servers") {
                    addJsonObject {
                        server(p)
                        put("method", p.method)
                        put("password", p.secret)
                    }
                }
            }
        }
        if (p.protocol != Protocol.SHADOWSOCKS) put("streamSettings", streamSettings(p))
    }

    private fun streamSettings(p: Profile) = buildJsonObject {
        put("network", p.network)
        put("security", p.security)
        when (p.security) {
            "tls" -> putJsonObject("tlsSettings") {
                putIfNotEmpty("serverName", p.sni)
                putIfNotEmpty("fingerprint", p.fingerprint)
                putIfNotEmpty("pinnedPeerCertSha256", p.pinnedPeerCertSha256)
                if (p.alpn.isNotEmpty()) put("alpn", strings(p.alpn.split(',').map { it.trim() }))
            }

            "reality" -> putJsonObject("realitySettings") {
                putIfNotEmpty("serverName", p.sni)
                put("fingerprint", p.fingerprint.ifEmpty { "chrome" })
                put("publicKey", p.publicKey)
                putIfNotEmpty("shortId", p.shortId)
                putIfNotEmpty("spiderX", p.spiderX)
            }
        }
        when (p.network) {
            "ws" -> putJsonObject("wsSettings") {
                putIfNotEmpty("path", p.path)
                putIfNotEmpty("host", p.host)
            }

            "httpupgrade" -> putJsonObject("httpupgradeSettings") {
                putIfNotEmpty("path", p.path)
                putIfNotEmpty("host", p.host)
            }

            "xhttp", "splithttp" -> putJsonObject("xhttpSettings") {
                putIfNotEmpty("path", p.path)
                putIfNotEmpty("host", p.host)
                putIfNotEmpty("mode", p.mode)
            }

            "grpc" -> putJsonObject("grpcSettings") {
                put("serviceName", p.serviceName)
                put("multiMode", p.mode == "multi")
            }

            "tcp" -> if (p.headerType == "http") putJsonObject("tcpSettings") {
                putJsonObject("header") {
                    put("type", "http")
                    putJsonObject("request") {
                        put("path", strings(listOf(p.path.ifEmpty { "/" })))
                        if (p.host.isNotEmpty()) {
                            putJsonObject("headers") { put("Host", strings(p.host.split(','))) }
                        }
                    }
                }
            }
        }
    }

    private fun JsonObjectBuilder.server(p: Profile) {
        put("address", p.address)
        put("port", p.port)
    }

    private fun JsonObjectBuilder.putIfNotEmpty(key: String, value: String) {
        if (value.isNotEmpty()) put(key, value)
    }

    private fun strings(values: List<String>) = JsonArray(values.map(::JsonPrimitive))

    fun dnsServers(raw: String): List<String> =
        raw.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("1.1.1.1") }
}
