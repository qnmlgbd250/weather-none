package com.skypulse.weather.di

import com.skypulse.weather.BuildConfig
import com.skypulse.weather.data.XiaomiGeocodingApi
import com.skypulse.weather.data.remote.CaiyunAlertApi
import com.skypulse.weather.data.remote.CaiyunApi
import com.skypulse.weather.data.remote.GithubApi
import com.skypulse.weather.data.remote.WeatherApiService
import com.skypulse.weather.data.remote.XiaomiWeatherApi
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
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "weather/${BuildConfig.VERSION_NAME} (${android.os.Build.MANUFACTURER}:${android.os.Build.MODEL}; android/${android.os.Build.VERSION.RELEASE})")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.WEATHER_BASE_URL)
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
            .baseUrl(BuildConfig.ALERT_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CaiyunAlertApi::class.java)

    @Provides
    @Singleton
    fun provideGithubApi(client: OkHttpClient, moshi: Moshi): GithubApi =
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GithubApi::class.java)

    @Provides
    @Singleton
    fun provideXiaomiGeocodingApi(client: OkHttpClient, moshi: Moshi): XiaomiGeocodingApi =
        Retrofit.Builder()
            .baseUrl("https://weatherapi.market.xiaomi.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(XiaomiGeocodingApi::class.java)

    @Provides
    @Singleton
    fun provideXiaomiWeatherApi(client: OkHttpClient, moshi: Moshi): XiaomiWeatherApi =
        Retrofit.Builder()
            .baseUrl("https://weatherapi.market.xiaomi.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(XiaomiWeatherApi::class.java)
}

/**
 * 将 WeatherApiService 接口绑定 to CaiyunApiService 实现。
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
