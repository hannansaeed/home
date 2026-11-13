package com.example.chatapp.ui.moth

import androidx.compose.runtime.Composable
import com.example.chatapp.ui.calls.CallHistoryEmptyPage

@Composable
fun EmptyPageView(state: MothState) {
    CallHistoryEmptyPage(state = state)
}

