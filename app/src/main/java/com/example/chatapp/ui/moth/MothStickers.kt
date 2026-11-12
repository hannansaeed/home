package com.example.chatapp.ui.moth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.ui.components.PixelStarfield
import com.example.chatapp.ui.theme.*

@Composable
fun EmptyPageView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MothBgDark)
            .testTag("empty_page_view"),
        contentAlignment = Alignment.Center,
    ) {
        // Scattered starfield background
        PixelStarfield()

        SteppedBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            backgroundColor = MothHeaderBg,
            borderColor = TheirBubbleBorder,
            cutSize = 4.dp,
            borderWidth = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "empty",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = LanternAmber,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "no items here for now",
                    fontFamily = PixelifySans,
                    fontSize = 13.sp,
                    color = MutedText,
                )
            }
        }
    }
}
