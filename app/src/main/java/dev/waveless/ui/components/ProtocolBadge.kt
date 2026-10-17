package dev.waveless.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.waveless.data.Protocol

/** Each protocol gets its own Material shape so servers are recognisable at a glance. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProtocolBadge(
    protocol: Protocol,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = MaterialTheme.colorScheme.tertiaryContainer,
    content: Color = MaterialTheme.colorScheme.onTertiaryContainer,
) {
    val polygon = when (protocol) {
        Protocol.VLESS -> MaterialShapes.Cookie4Sided
        Protocol.VMESS -> MaterialShapes.Pentagon
        Protocol.TROJAN -> MaterialShapes.Clover4Leaf
        Protocol.SHADOWSOCKS -> MaterialShapes.Gem
    }
    Box(
        modifier.size(size).clip(polygon.toShape()).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            protocol.label.take(2).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = content,
        )
    }
}
