package org.app.core.base.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CommonModule {
    private const val REQUEST_TIME_OUT: Long = 60

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        val logging = HttpLoggingInterceptor()
        logging.level = HttpLoggingInterceptor.Level.BODY
        return logging
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        logging: HttpLoggingInterceptor,
        @ApplicationContext context: Context
    ): OkHttpClient {
        val interceptor = Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .build()
            )
        }
        return OkHttpClient.Builder()
            .readTimeout(REQUEST_TIME_OUT, TimeUnit.SECONDS)
            .connectTimeout(REQUEST_TIME_OUT, TimeUnit.SECONDS)
            .addInterceptor(interceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .serializeNulls() // To allow sending null values
            .create()
    }

    @DefaultDispatcher
    @Singleton
    @Provides
    fun defaultDispatcher(): CoroutineDispatcher =
        Executors.newCachedThreadPool().asCoroutineDispatcher()

    @DecodingDispatcher
    @Singleton
    @Provides
    fun decodingDispatcher(): CoroutineDispatcher =
        Executors.newFixedThreadPool(
            2 * Runtime.getRuntime().availableProcessors() + 1
        ).asCoroutineDispatcher()

    @EncodingDispatcher
    @Singleton
    @Provides
    fun encodingDispatcher(): CoroutineDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @IoDispatcher
    @Singleton
    @Provides
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @UiDispatcher
    @Singleton
    @Provides
    fun uiDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate
}


@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class EncodingDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DecodingDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class UiDispatcher