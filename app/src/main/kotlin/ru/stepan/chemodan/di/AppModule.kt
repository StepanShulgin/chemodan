package ru.stepan.chemodan.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.stepan.chemodan.data.ChemodanDatabase
import ru.stepan.chemodan.data.CountryApi
import ru.stepan.chemodan.data.FrankfurterApi

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun gson(): Gson = Gson()

    @Provides
    @Singleton
    fun countryApi(gson: Gson): CountryApi =
        Retrofit.Builder()
            .baseUrl("https://raw.githubusercontent.com/")
            .client(OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(CountryApi::class.java)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ChemodanDatabase =
        Room.databaseBuilder(context, ChemodanDatabase::class.java, "chemodan.db")
            .addMigrations(ChemodanDatabase.MIGRATION_1_2)
            .addMigrations(ChemodanDatabase.MIGRATION_2_3)
            .addMigrations(ChemodanDatabase.MIGRATION_3_4)
            .addMigrations(ChemodanDatabase.MIGRATION_4_5)
            .build()

    @Provides
    @Singleton
    fun frankfurterApi(): FrankfurterApi =
        Retrofit.Builder()
            .baseUrl("https://api.frankfurter.dev/")
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .callTimeout(15, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FrankfurterApi::class.java)
}
