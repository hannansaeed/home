package com.example.chatapp.di

import android.content.Context
import androidx.room.Room
import com.example.chatapp.R
import com.example.chatapp.data.CompositeChatRepository
import com.example.chatapp.data.local.ChatDatabase
import com.example.chatapp.data.local.LocalAccountDao
import com.example.chatapp.data.local.LocalChatDao
import com.example.chatapp.data.local.MessageDao
import com.example.chatapp.domain.ChatRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun firestore(@ApplicationContext ctx: Context): FirebaseFirestore {
        val databaseId = ctx.getString(R.string.firestore_database_id)
        return FirebaseFirestore.getInstance(databaseId)
    }

    @Provides
    @Singleton
    fun auth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun database(@ApplicationContext ctx: Context): ChatDatabase {
        try {
            ctx.deleteDatabase("chat.db")
        } catch (_: Exception) {}

        return Room.databaseBuilder(ctx, ChatDatabase::class.java, "chatapp_local.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun accountDao(db: ChatDatabase): LocalAccountDao = db.accountDao()

    @Provides
    fun localChatDao(db: ChatDatabase): LocalChatDao = db.chatDao()

    @Provides
    fun messageDao(db: ChatDatabase): MessageDao = db.messageDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun repo(impl: CompositeChatRepository): ChatRepository
}
