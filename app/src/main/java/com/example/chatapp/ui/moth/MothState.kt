package com.example.chatapp.ui.moth

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MessageType {
    REGULAR,
    DISAPPEARING,
    VIEW_ONCE
}

enum class ViewOnceStatus {
    UNOPENED,
    REVEALED,
    BURNED
}

data class MothMessage(
    val id: String,
    val text: String,
    val isMine: Boolean,
    val type: MessageType = MessageType.REGULAR,
    val reactionCount: Int = 0,
    val hasReacted: Boolean = false,
    val viewOnceStatus: ViewOnceStatus = ViewOnceStatus.UNOPENED,
    val embersRemaining: Int = 10,
    val time: String = "now",
)

data class MothConversation(
    val id: String,
    val name: String,
    val preview: String,
    val time: String,
    val avatarColor: Color,
    val unreadCount: Int = 0,
    val isLit: Boolean = true,
    val isDisappearing: Boolean = false,
    val isViewOnce: Boolean = false,
    val messages: List<MothMessage> = emptyList(),
)

class MothState(private val scope: CoroutineScope) {
    var currentUsername by mutableStateOf("moth_fan")
    var userAvatarColor by mutableStateOf(Color(0xFFB8A9E8))
    var sealedCipherEnabled by mutableStateOf(true)
    var emberChimesEnabled by mutableStateOf(true)

    var conversations by mutableStateOf(createInitialConversations())
    var activeChatId by mutableStateOf<String?>(null)
    var isContactTyping by mutableStateOf(false)

    fun getActiveConversation(): MothConversation? {
        return conversations.firstOrNull { it.id == activeChatId }
    }

    fun openConversation(idOrName: String) {
        val target = conversations.firstOrNull { it.id == idOrName || it.name.equals(idOrName, ignoreCase = true) }
            ?: run {
                // If not found, create a new conversation for this contact
                val newConv = MothConversation(
                    id = idOrName.lowercase(),
                    name = idOrName,
                    preview = "lantern ignited",
                    time = "now",
                    avatarColor = Color(0xFF82D8A7),
                    isLit = true,
                    messages = listOf(
                        MothMessage(
                            id = "intro_${System.currentTimeMillis()}",
                            text = "greetings, moth hours are upon us",
                            isMine = false,
                        )
                    )
                )
                conversations = listOf(newConv) + conversations
                newConv
            }

        // Mark as read
        conversations = conversations.map {
            if (it.id == target.id) it.copy(unreadCount = 0) else it
        }
        activeChatId = target.id
    }

    fun sendMessage(
        chatId: String,
        text: String,
        type: MessageType = MessageType.REGULAR,
    ) {
        if (text.isBlank()) return

        val newMsg = MothMessage(
            id = "msg_${System.currentTimeMillis()}",
            text = text.trim(),
            isMine = true,
            type = type,
            embersRemaining = 10,
        )

        conversations = conversations.map { conv ->
            if (conv.id == chatId) {
                conv.copy(
                    preview = if (type == MessageType.VIEW_ONCE) "view once" else text.trim(),
                    time = "now",
                    messages = conv.messages + newMsg,
                )
            } else conv
        }

        // Trigger in-character atmospheric reply from the contact!
        triggerSimulatedReply(chatId, text)
    }

    private fun triggerSimulatedReply(chatId: String, userText: String) {
        val conv = conversations.firstOrNull { it.id == chatId } ?: return
        scope.launch {
            delay(800)
            isContactTyping = true
            delay(1600)
            isContactTyping = false

            val replyText = generateReplyForContact(conv.name, userText)
            val replyMsg = MothMessage(
                id = "reply_${System.currentTimeMillis()}",
                text = replyText,
                isMine = false,
                type = MessageType.REGULAR,
            )

            conversations = conversations.map { c ->
                if (c.id == chatId) {
                    c.copy(
                        preview = replyText,
                        time = "now",
                        messages = c.messages + replyMsg,
                    )
                } else c
            }
        }
    }

    private fun generateReplyForContact(name: String, query: String): String {
        val lower = query.lowercase()
        return when (name.lowercase()) {
            "support" -> listOf(
                "your transmission is received and sealed.",
                "lantern signal is stable across the network.",
                "all channels are end-to-end encrypted.",
                "let us know if you need assistance with your wicks or cipher."
            ).random()
            else -> listOf("lantern shines bright", "received loud and clear", "sealed by the shore").random()
        }
    }

    fun toggleReaction(chatId: String, messageId: String) {
        conversations = conversations.map { conv ->
            if (conv.id == chatId) {
                conv.copy(
                    messages = conv.messages.map { msg ->
                        if (msg.id == messageId) {
                            val newHasReacted = !msg.hasReacted
                            val newCount = if (newHasReacted) msg.reactionCount + 1 else (msg.reactionCount - 1).coerceAtLeast(0)
                            msg.copy(reactionCount = newCount, hasReacted = newHasReacted)
                        } else msg
                    }
                )
            } else conv
        }
    }

    fun revealViewOnce(chatId: String, messageId: String) {
        conversations = conversations.map { conv ->
            if (conv.id == chatId) {
                conv.copy(
                    messages = conv.messages.map { msg ->
                        if (msg.id == messageId && msg.type == MessageType.VIEW_ONCE) {
                            when (msg.viewOnceStatus) {
                                ViewOnceStatus.UNOPENED -> msg.copy(viewOnceStatus = ViewOnceStatus.REVEALED)
                                ViewOnceStatus.REVEALED -> msg.copy(viewOnceStatus = ViewOnceStatus.BURNED)
                                ViewOnceStatus.BURNED -> msg
                            }
                        } else msg
                    }
                )
            } else conv
        }
    }

    fun tickEmbers(chatId: String, messageId: String) {
        conversations = conversations.map { conv ->
            if (conv.id == chatId) {
                val updatedMessages = conv.messages.mapNotNull { msg ->
                    if (msg.id == messageId && msg.type == MessageType.DISAPPEARING) {
                        if (msg.embersRemaining <= 1) null // Dissolves message when burned out!
                        else msg.copy(embersRemaining = msg.embersRemaining - 1)
                    } else msg
                }
                conv.copy(messages = updatedMessages)
            } else conv
        }
    }

    fun createNewChat(name: String, initialMessage: String) {
        if (name.isBlank()) return
        val colors = listOf(Color(0xFFB8A9E8), Color(0xFF82D8A7), Color(0xFFF38D71), Color(0xFFFFD152), Color(0xFF70D6FF))
        val newConv = MothConversation(
            id = "chat_${System.currentTimeMillis()}",
            name = name.trim(),
            preview = initialMessage.ifBlank { "lantern lit" },
            time = "now",
            avatarColor = colors.random(),
            isLit = true,
            messages = if (initialMessage.isNotBlank()) {
                listOf(
                    MothMessage(
                        id = "init_${System.currentTimeMillis()}",
                        text = initialMessage.trim(),
                        isMine = true,
                    )
                )
            } else emptyList()
        )
        conversations = listOf(newConv) + conversations
        activeChatId = newConv.id
    }
}

private fun createInitialConversations(): List<MothConversation> {
    return listOf(
        MothConversation(
            id = "support",
            name = "support",
            preview = "how can we help your lantern tonight?",
            time = "now",
            avatarColor = Color(0xFFFFB43B), // Amber Lantern
            unreadCount = 1,
            isLit = true,
            messages = listOf(
                MothMessage(
                    id = "sup_1",
                    text = "welcome to moth hours. your channel is sealed and encrypted.",
                    isMine = false,
                    type = MessageType.REGULAR,
                    time = "now",
                ),
                MothMessage(
                    id = "sup_2",
                    text = "how can we help your lantern tonight?",
                    isMine = false,
                    type = MessageType.REGULAR,
                    time = "now",
                ),
            ),
        ),
    )
}
