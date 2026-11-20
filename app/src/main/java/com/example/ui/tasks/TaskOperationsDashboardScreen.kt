package com.example.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.LanStatusItem
import com.example.core.model.TaskOperationsDashboardData
import com.example.domain.repository.TaskRepository
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingEmptyState
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingStatusDot
import com.example.ui.common.NothingTagChip
import com.example.ui.theme.DotMatrixFontFamily
import com.example.ui.theme.NothingAmber
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskOperationsDashboardScreen(
    taskId: String,
    taskRepository: TaskRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dashboardData by remember { mutableStateOf<TaskOperationsDashboardData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val coroutineScope = rememberCoroutineScope()

    suspend fun loadData() {
        val res = taskRepository.fetchTaskOperationsDashboard(taskId)
        if (res.isSuccess) {
            dashboardData = res.getOrNull()
            errorMessage = null
        } else {
            errorMessage = res.exceptionOrNull()?.message ?: "Failed to load dashboard"
        }
        isLoading = false
    }

    LaunchedEffect(taskId) {
        while (true) {
            loadData()
            delay(15_000L)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { NothingDotMatrixText("OPS DASHBOARD", fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                loadData()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (isLoading && dashboardData == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        } else if (errorMessage != null && dashboardData == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                NothingEmptyState(
                    title = "Dashboard Unavailable",
                    subtitle = errorMessage ?: "Error",
                    icon = Icons.Default.Warning
                )
            }
        } else {
            val data = dashboardData ?: return@Scaffold
            val filteredLans = when (selectedFilter) {
                "COMPLETED" -> data.lanStatuses.filter { it.status == "completed" }
                "PENDING" -> data.lanStatuses.filter { it.status == "pending" || it.status == "in_progress" }
                "OVERDUE" -> data.lanStatuses.filter { it.isOverdue || it.status == "overdue" }
                else -> data.lanStatuses
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                item {
                    NothingHairlineCard(
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        val priorityColor = when (data.importance) {
                            "CRITICAL" -> NothingRed
                            "HIGH" -> NothingAmber
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val statusCompleted = data.status.equals("completed", true)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NothingTagChip(
                                text = "${data.importance} PRIORITY",
                                containerColor = priorityColor.copy(alpha = 0.12f),
                                contentColor = priorityColor,
                                borderColor = priorityColor.copy(alpha = 0.4f)
                            )
                            NothingTagChip(
                                text = data.status.uppercase(),
                                containerColor = if (statusCompleted) NothingGreen.copy(alpha = 0.12f) else NothingTheme.colors.surfaceElevated,
                                contentColor = if (statusCompleted) NothingGreen else NothingTheme.colors.textPrimary,
                                borderColor = if (statusCompleted) NothingGreen.copy(alpha = 0.4f) else NothingTheme.colors.border
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = data.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (data.executionTime != null || data.deadlineTime != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = NothingTheme.colors.textSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "TIME ${data.executionTime ?: "N/A"} · DEADLINE ${data.deadlineTime ?: "N/A"}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                    color = NothingTheme.colors.textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "COMPLETION",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                color = NothingTheme.colors.textSecondary
                            )
                            Text(
                                text = "${data.completedLans}/${data.totalLans} LANs · ${data.currentProgress}%",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = TechMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (data.currentProgress / 100f).coerceIn(0f, 1f) },
                            color = if (data.currentProgress >= 100) NothingGreen else MaterialTheme.colorScheme.onSurface,
                            trackColor = NothingTheme.colors.surfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryStatBox(
                            label = "TOTAL",
                            count = data.totalLans,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatBox(
                            label = "DONE",
                            count = data.completedLans,
                            color = NothingGreen,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatBox(
                            label = "PENDING",
                            count = data.pendingLans,
                            color = NothingAmber,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatBox(
                            label = "OVERDUE",
                            count = data.overdueLans,
                            color = NothingRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            listOf(
                                "ALL" to "All (${data.lanStatuses.size})",
                                "PENDING" to "Pending (${data.pendingLans})",
                                "COMPLETED" to "Done (${data.completedLans})",
                                "OVERDUE" to "Overdue (${data.overdueLans})"
                            )
                        ) { (key, label) ->
                            val selected = selectedFilter == key
                            FilterChip(
                                selected = selected,
                                onClick = { selectedFilter = key },
                                shape = RoundedCornerShape(999.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NothingTheme.colors.accentPrimary,
                                    selectedLabelColor = NothingTheme.colors.onAccentPrimary,
                                    containerColor = NothingTheme.colors.surface,
                                    labelColor = NothingTheme.colors.textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = NothingTheme.colors.border,
                                    selectedBorderColor = NothingTheme.colors.accentPrimary
                                ),
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                }

                if (filteredLans.isEmpty()) {
                    item {
                        NothingEmptyState(
                            title = "No matching LANs",
                            subtitle = "Switch filters to inspect other rooms.",
                            icon = Icons.Default.Lan
                        )
                    }
                } else {
                    items(filteredLans) { lan ->
                        LanStatusRow(lan = lan)
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryStatBox(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    NothingHairlineCard(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NothingDotMatrixText(
                text = count.toString(),
                fontSize = 22.sp,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            NothingStatusDot(color = color, size = 6.dp)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = TechMonoFontFamily,
                fontSize = 10.sp
            ),
            color = NothingTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LanStatusRow(lan: LanStatusItem) {
    val isDone = lan.status.equals("completed", true)
    val isOverdue = lan.isOverdue || lan.status.equals("overdue", true)

    val badgeColor = when {
        isDone -> NothingGreen
        isOverdue -> NothingRed
        lan.status.equals("in_progress", true) -> NothingAmber
        else -> NothingTheme.colors.textSecondary
    }

    NothingHairlineCard(
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NothingTheme.colors.surfaceElevated)
                    .border(BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isDone -> Icons.Default.CheckCircle
                        isOverdue -> Icons.Default.Warning
                        else -> Icons.Default.Lan
                    },
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = lan.lanNumber,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = TechMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    NothingTagChip(
                        text = lan.status.uppercase(),
                        containerColor = badgeColor.copy(alpha = 0.12f),
                        contentColor = badgeColor,
                        borderColor = badgeColor.copy(alpha = 0.4f)
                    )
                }

                if (lan.employeeName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = NothingTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${lan.employeeName} (${lan.employeeId ?: ""})",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (lan.lastResponseTime != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "UPDATED ${lan.lastResponseTime}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = TechMonoFontFamily,
                            fontSize = 10.sp
                        ),
                        color = NothingTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${lan.progress}%",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = DotMatrixFontFamily,
                    fontSize = 18.sp
                ),
                color = badgeColor
            )
        }
    }
}
