package com.example.ui.incidents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.common.NothingDivider
import com.example.ui.theme.NothingTheme

@Composable
fun SystemIncidentRegisterScreen(
    viewModel: SystemIncidentViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()

    val tabs = listOf(
        Triple("Quick Entry", Icons.Filled.EditNote, Icons.Outlined.EditNote),
        Triple("History", Icons.Filled.History, Icons.Outlined.History),
        Triple("Reports", Icons.Filled.Assessment, Icons.Outlined.Assessment)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NothingTheme.colors.background)
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = NothingTheme.colors.surfaceElevated,
            border = BorderStroke(1.dp, NothingTheme.colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .height(44.dp)
                .testTag("incident_subtabs")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                tabs.forEachIndexed { index, (title, selectedIcon, unselectedIcon) ->
                    val isSelected = currentTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (isSelected) NothingTheme.colors.accentPrimary
                                else androidx.compose.ui.graphics.Color.Transparent
                            )
                            .clickable { viewModel.selectTab(index) }
                            .testTag("subtab_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelected) selectedIcon else unselectedIcon,
                                contentDescription = title,
                                tint = if (isSelected) NothingTheme.colors.onAccentPrimary else NothingTheme.colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) NothingTheme.colors.onAccentPrimary else NothingTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        NothingDivider()

        when (currentTab) {
            0 -> QuickEntryScreen(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
            1 -> IncidentHistoryScreen(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
            2 -> ReportsScreen(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
