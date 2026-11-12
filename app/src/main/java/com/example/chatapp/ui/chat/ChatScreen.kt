package com.example.chatapp.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chatapp.domain.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Chat · ${state.connection.name.lowercase()}") }) },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(8.dp).navigationBarsPadding().imePadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.draft, onValueChange = vm::onDraftChange,
                    modifier = Modifier.weight(1f), placeholder = { Text("Message") },
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = vm::send) { Text("Send") }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.messages, key = { it.id }) { m ->
                MessageBubble(m, onReact = { vm.react(m.id, "👍") })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(m: Message, onReact: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.isMine) Alignment.End else Alignment.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (m.isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onReact), // long-press = react
        ) { Text(m.text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
        if (m.reactions.isNotEmpty()) {
            Text(
                m.reactions.entries.joinToString(" ") { "${it.key}${it.value.size}" },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
