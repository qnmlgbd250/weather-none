package com.skypulse.weather.di

import com.skypulse.weather.BuildConfig
import com.skypulse.weather.data.remote.CaiyunAlertApi
import com.skypulse.weather.data.remote.CaiyunApi
import com.skypulse.weather.data.remote.WeatherApiService
import com.squareup.moshi.Moshi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://wrapper.cyapi.cn/"
    private const val ALERT_BASE_URL = "https://starplucker.cyapi.cn/"

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
        )
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun provideCaiyunApi(retrofit: Retrofit): CaiyunApi =
        retrofit.create(CaiyunApi::class.java)

    @Provides
    @Singleton
    fun provideCaiyunAlertApi(client: OkHttpClient, moshi: Moshi): CaiyunAlertApi =
        Retrofit.Builder()
            .baseUrl(ALERT_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CaiyunAlertApi::class.java)
}

/**
 * 将 WeatherApiService 接口绑定到 CaiyunApiService 实现。
 * 未来切换 API 提供商时，只需修改这里的绑定。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ApiModule {

    @Binds
    @Singleton
    abstract fun bindWeatherApiService(
        impl: com.skypulse.weather.data.remote.CaiyunApiService
    ): WeatherApiService
}
