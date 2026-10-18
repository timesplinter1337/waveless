package dev.waveless.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.waveless.data.Protocol

/**
 * Server avatar: the protocol picks the Material shape, the content is the country flag
 * leading the server name, or an outlined globe when the name has none.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServerBadge(
    protocol: Protocol,
    flag: String?,
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
        if (flag != null) {
            val fontSize = with(LocalDensity.current) { (size * 0.5f).toSp() }
            Text(flag, style = TextStyle(fontSize = fontSize, lineHeight = fontSize))
        } else {
            Icon(
                Icons.Outlined.Language,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}
