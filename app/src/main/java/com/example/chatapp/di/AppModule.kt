package com.example.chatapp.di

import android.content.Context
import androidx.room.Room
import com.example.chatapp.crypto.MessageCrypto
import com.example.chatapp.crypto.PassThroughCrypto
import com.example.chatapp.data.ChatRepositoryImpl
import com.example.chatapp.data.local.ChatDatabase
import com.example.chatapp.data.local.MessageDao
import com.example.chatapp.domain.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build()

    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): ChatDatabase {
        // TODO: derive passphrase from a key stored in Android Keystore, not a constant
        val factory = SupportFactory(SQLiteDatabase.getBytes("dev-passphrase".toCharArray()))
        return Room.databaseBuilder(ctx, ChatDatabase::class.java, "chat.db")
            .openHelperFactory(factory).build()
    }

    @Provides fun messageDao(db: ChatDatabase): MessageDao = db.messageDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun repo(impl: ChatRepositoryImpl): ChatRepository
    @Binds abstract fun crypto(impl: PassThroughCrypto): MessageCrypto
}
