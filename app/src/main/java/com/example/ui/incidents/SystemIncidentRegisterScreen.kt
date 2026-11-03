package com.example.ui.incidents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = currentTab,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("incident_subtabs")
        ) {
            tabs.forEachIndexed { index, (title, selectedIcon, unselectedIcon) ->
                val isSelected = currentTab == index
                Tab(
                    selected = isSelected,
                    onClick = { viewModel.selectTab(index) },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) selectedIcon else unselectedIcon,
                            contentDescription = title,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.testTag("subtab_$index")
                )
            }
        }

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
