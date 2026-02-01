package com.clauderemote.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.clauderemote.data.remote.ApiService
import com.clauderemote.data.remote.SocketService
import com.clauderemote.data.repository.ClaudeRemoteRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(okHttpClient: OkHttpClient): ApiService {
        return ApiService(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideSocketService(): SocketService {
        return SocketService()
    }

    @Provides
    @Singleton
    fun provideRepository(
        apiService: ApiService,
        socketService: SocketService,
        dataStore: DataStore<Preferences>
    ): ClaudeRemoteRepository {
        return ClaudeRemoteRepository(apiService, socketService, dataStore)
    }
}
