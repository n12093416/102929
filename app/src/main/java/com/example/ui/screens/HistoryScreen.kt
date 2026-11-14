package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.remote.GeminiToolType
import com.example.data.repository.WorkspaceRecord
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    records: List<WorkspaceRecord>,
    selectedToolFilter: String?,
    onSelectFilter: (String?) -> Unit,
    onDeleteRecord: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val filteredRecords = if (selectedToolFilter == null) {
        records
    } else {
        records.filter { it.toolId == selectedToolFilter }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("클라우드 작업 보관함 (Firestore)", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Text("Google 계정별로 Cloud Firestore에 실시간 동기화됩니다", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            if (records.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    modifier = Modifier.testTag("history_clear_all_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = CoralError, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("전체 삭제", color = CoralError)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedToolFilter == null,
                onClick = { onSelectFilter(null) },
                label = { Text("전체 (${records.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                    selectedLabelColor = CyanPrimary
                )
            )
            GeminiToolType.entries.forEach { tool ->
                FilterChip(
                    selected = selectedToolFilter == tool.name,
                    onClick = { onSelectFilter(tool.name) },
                    label = { Text(tool.title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = CyanPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("저장된 AI 작업 기록이 없습니다", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "작업실에서 8가지 Gemini API 중 하나를 실행한 뒤 우측 상단 저장 아이콘을 눌러보세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredRecords, key = { it.id }) { record ->
                    HistoryRecordCard(
                        record = record,
                        onCopy = { clipboard.setText(AnnotatedString(record.responseText)) },
                        onDelete = { onDeleteRecord(record.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordCard(
    record: WorkspaceRecord,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    val timeText = formatter.format(record.createdAt?.toDate() ?: Date())

    Card(
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = StudioSurfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = record.toolTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(timeText, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }

                Row {
                    IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "복사", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "삭제", tint = CoralError, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Q. ${record.prompt}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = record.responseText,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
            if (record.metadataSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(record.metadataSummary, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}
