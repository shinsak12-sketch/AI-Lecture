package com.bosang.search.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bosang.search.core.MatchMethod
import com.bosang.search.data.Roles
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val fDateTime = DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.KOREAN)
    private val fTime = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
    private val fDay = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)

    fun dateTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(fDateTime)
    fun time(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(fTime)
    fun day(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(fDay)
    fun dayKey(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }
}

/** 작은 꼬리표 */
@Composable
fun Tag(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun RoleTag(role: String) = Tag(role, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)

@Composable
fun MethodTag(method: MatchMethod) {
    val cs = MaterialTheme.colorScheme
    when (method) {
        MatchMethod.CALL_LOG -> Tag("통화기록 일치", cs.secondaryContainer, cs.onSecondaryContainer)
        MatchMethod.FILE_NUMBER -> Tag("번호 일치", cs.secondaryContainer, cs.onSecondaryContainer)
        MatchMethod.CONTACT_NAME -> Tag("연락처명 일치", cs.surfaceVariant, cs.onSurfaceVariant)
        MatchMethod.AMBIGUOUS -> Tag("확인 필요 · 동명이인", cs.tertiaryContainer, cs.onTertiaryContainer)
    }
}

/** 관계 고르기: 버튼 + 직접 입력 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RolePicker(role: String, onRole: (String) -> Unit, modifier: Modifier = Modifier) {
    val isCustom = role.isNotEmpty() && role !in Roles.all
    val customMode = isCustom || role == Roles.CUSTOM
    FlowRow(modifier = modifier) {
        Roles.all.forEach { r ->
            FilterChip(
                selected = role == r,
                onClick = { onRole(r) },
                label = { Text(r) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        FilterChip(
            selected = customMode,
            onClick = { onRole(Roles.CUSTOM) },
            label = { Text(Roles.CUSTOM) },
            modifier = Modifier.padding(end = 6.dp),
        )
    }
    if (customMode) {
        OutlinedTextField(
            value = if (role == Roles.CUSTOM) "" else role,
            onValueChange = { onRole(it.ifBlank { Roles.CUSTOM }) },
            singleLine = true,
            placeholder = { Text("예: 동승자, 담당 설계사") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 관계가 실제로 정해졌는지 ("직접 입력"만 눌러두고 비워둔 상태는 아님) */
fun roleReady(role: String): Boolean = role.isNotBlank() && role != Roles.CUSTOM
