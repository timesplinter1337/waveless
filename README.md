# Waveless

Android VPN client built on [xray-core](https://github.com/XTLS/Xray-core) with a Jetpack Compose
**Material 3 Expressive** UI.

## Features

- **Protocols**: VLESS (incl. XTLS Vision, REALITY), VMess, Trojan, Shadowsocks (incl. 2022 ciphers).
- **Transports**: TCP/RAW (+HTTP header), WebSocket, gRPC, XHTTP, HTTPUpgrade; TLS / REALITY,
  `pinnedPeerCertSha256`.
- **Import**: share links from the clipboard or pasted text, base64 subscriptions with refresh.
- **Routing presets**: everything through the proxy, skip LAN, or skip LAN + Russian domains/IPs
  (`geosite:category-ru`, `geoip:ru`); optional ad blocking (`geosite:category-ads-all`).
- **Full-device tunnel**: `VpnService` hands its TUN fd straight to xray's built-in `tun` inbound,
  so there is no separate tun2socks. DNS to the tunnel is answered by xray's DNS module.
- Latency tests, live traffic stats, Quick Settings tile, always-on VPN support.
- English and Russian UI.

## Material 3 Expressive

Built with `androidx.compose.material3:1.5.0-beta01`; the stable 1.4.0 does not expose the
expressive components:

- `MaterialExpressiveTheme` with `MotionScheme.expressive()` springs, dynamic color on Android 12+
  and a tonal-spot fallback scheme from seed `#1B8A9E`.
- Hero connect button that **morphs between `MaterialShapes`** (cookie → burst → scalloped) and
  hosts a `LoadingIndicator` while connecting.
- Connected `ToggleButton` groups (routing preset, theme), `SegmentedListItem` lists,
  `HorizontalFloatingToolbar` with a vibrant FAB, `LargeFlexibleTopAppBar`, emphasized type styles.
- Adaptive shell via `NavigationSuiteScaffold`: short navigation bar on phones, wide navigation
  rail on tablets/foldables; content width capped on large screens.

## Building

Requirements: JDK 21, Android SDK with platform 37, and Go (only for config validation).

```bash
scripts/fetch-libxray.sh          # downloads the pinned libv2ray.aar (xray-core binding) into app/libs
./gradlew :app:assembleDebug      # APKs in app/build/outputs/apk/debug (per-ABI + universal)
```

The xray-core binding comes prebuilt from
[2dust/AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) and is pinned by version
and SHA-256 in `scripts/fetch-libxray.sh`. Release builds are debug-signed so CI can produce an
installable APK; configure a real signing key before distributing.

## Checks

```bash
scripts/validate-configs.sh                          # unit tests + real xray-core validation
./gradlew :app:testDebugUnitTest -Pscreenshots       # Robolectric screenshots → app/build/screenshots
```

`validate-configs.sh` runs the unit tests, which also write the generated configs for every
transport. It then loads them with the same xray-core version the app ships
(`tools/xray-check`) and sends real traffic through the VLESS, VMess and Shadowsocks outbounds
against local xray servers.

## Layout

```
app/src/main/java/dev/waveless/
  core/      XrayConfig (JSON config builder), XrayCore (libv2ray wrapper)
  data/      Profile model, share-link parser, profile/subscription + settings repositories
  service/   WavelessVpnService (TUN + core lifecycle), Quick Settings tile, VpnState
  ui/        Compose screens (home, servers, settings), theme, ViewModel
tools/xray-check/   Go validator for generated configs
```
