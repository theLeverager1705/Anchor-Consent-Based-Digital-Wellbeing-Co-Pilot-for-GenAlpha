package com.anchor.copilot.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.copilot.ui.theme.Anchor

@Composable
fun AnchorCard(
    modifier: Modifier = Modifier,
    color: Color = Anchor.Card,
    padding: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(16.dp),
        color = color,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun CardTitle(title: String, sub: String? = null) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (sub != null) {
        Spacer(Modifier.height(4.dp))
        Text(sub, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
fun Kicker(text: String, color: Color = Anchor.Accent) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
    Spacer(Modifier.height(4.dp))
}

@Composable
fun PageTitle(text: String, sub: String? = null) {
    Text(text, style = MaterialTheme.typography.headlineMedium)
    if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(16.dp))
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = Anchor.Primary, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Anchor.White, disabledContainerColor = color.copy(alpha = 0.5f), disabledContentColor = Anchor.White),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Anchor.White) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = Anchor.Primary, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(2.dp, if (enabled) color else color.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (enabled) color else color.copy(alpha = 0.5f)) }
}

@Composable
fun Banner(modifier: Modifier = Modifier, color: Color = Anchor.GoodBg, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

@Composable
fun EmojiButton(emoji: String, selected: Boolean, size: Dp = 60.dp, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, label = "emojiScale")
    val border by animateColorAsState(if (selected) Anchor.Secondary else Anchor.Line, label = "emojiBorder")
    Box(
        Modifier.size(size).scale(scale).clip(RoundedCornerShape(14.dp))
            .background(if (selected) Anchor.GoodBg else Anchor.White)
            .border(2.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size.value * 0.47f).sp) }
}

@Composable
fun StreakBadge(days: Int) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(Anchor.Dark).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { Text("🔥 $days day streak", color = Anchor.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Anchor.Secondary, height: Dp = 10.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(8.dp)).background(Anchor.Line)) {
        Box(Modifier.fillMaxWidth(animated).height(height).clip(RoundedCornerShape(8.dp)).background(color))
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Anchor.White, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Anchor.Dark)
            Text(label, fontSize = 11.sp, color = Anchor.Muted, maxLines = 1)
        }
    }
}

@Composable
fun MoodTag(label: String) {
    val (bg, fg) = when (label) {
        "happy" -> Anchor.GoodBg to Anchor.Good
        "sad" -> Anchor.SadBg to Anchor.Sad
        "angry", "anxious" -> Anchor.WarnBg to Anchor.Accent
        else -> Anchor.Line to Anchor.Muted
    }
    Text(
        label,
        Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp),
        color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
    )
}

@Composable
fun ToggleRow(title: String, sub: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Anchor.Primary, checkedThumbColor = Anchor.White),
        )
    }
}

@Composable
fun StarterText(text: String) {
    Text("Conversation starter: $text", fontSize = 13.sp, fontStyle = FontStyle.Italic, color = Anchor.Primary, lineHeight = 18.sp)
}

@Composable
fun Footnote(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(text, fontSize = 12.sp, color = Anchor.Muted, lineHeight = 17.sp)
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun BulletLine(icon: String, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(icon, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

fun sproutEmoji(stage: Int): String = listOf("🌱", "🌿", "🪴", "🌳", "🌸🌳")[(stage - 1).coerceIn(0, 4)]
