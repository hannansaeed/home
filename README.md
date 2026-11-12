# ChatApp (Android, Kotlin + Compose)

Layers (dependencies point inward):
  ui (Compose + ViewModel) -> domain (models, ChatRepository) <- data (Room/SQLCipher, WebSocket, crypto)

Where the next features plug in:
- E2EE:         crypto/MessageCrypto.kt (swap PassThroughCrypto for libsignal)
- Reactions:    ChatRepositoryImpl.react + ChatScreen long-press (add an emoji picker)
- Disappearing: Message.expiresAt + MessageDao.purgeExpired (schedule with WorkManager)
- Stickers/GIFs/games: new composables under ui/, new message types in domain/Models.kt
- Privacy:      FLAG_SECURE already on in MainActivity; add BiometricPrompt app lock

Open in Android Studio and let Gradle sync (Studio can generate the Gradle wrapper).
