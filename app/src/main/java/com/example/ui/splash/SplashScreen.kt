package com.example.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingDotPatternStrip
import com.example.ui.common.TaskzAmbientBackground
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily

@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onNavigateToMain: () -> Unit,
    onNavigateToAuth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navState by viewModel.navigationState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkSession()
    }

    LaunchedEffect(navState) {
        when (navState) {
            SplashNavigationState.NavigateToMain -> onNavigateToMain()
            SplashNavigationState.NavigateToAuth -> onNavigateToAuth()
            SplashNavigationState.Loading -> Unit
        }
    }

    TaskzAmbientBackground(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
        NothingDotPatternStrip(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(NothingTheme.colors.surface)
                    .border(1.dp, NothingTheme.colors.border, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.taskz_launcher_icon_1791997779230),
                    contentDescription = "Taskz Logo",
                    modifier = Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            NothingDotMatrixText(
                text = "TASKZ",
                fontSize = 34.sp,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "WORKFORCE OPERATIONS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = TechMonoFontFamily,
                    letterSpacing = 2.sp
                ),
                color = NothingTheme.colors.textSecondary
            )

            Spacer(modifier = Modifier.height(34.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onBackground,
                strokeWidth = 2.dp
            )
        }

        NothingDotPatternStrip(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        )
        }
    }
}
