package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class TeamShort(
    val id: Int?,
    val name: String?,
    val shortName: String?,
    val tla: String?,
    val crest: String?
)

@JsonClass(generateAdapter = true)
data class CompetitionShort(
    val id: Int?,
    val name: String?,
    val code: String?,
    val emblem: String?
)

@JsonClass(generateAdapter = true)
data class Match(
    val id: Int?,
    val utcDate: String?,
    val status: String?,
    val homeTeam: TeamShort?,
    val awayTeam: TeamShort?,
    val competition: CompetitionShort?
)

@JsonClass(generateAdapter = true)
data class TeamMatchesResponse(
    val matches: List<Match>
)

interface FootballApiService {
    @GET("v4/teams/{id}/matches")
    suspend fun getTeamMatches(
        @Path("id") teamId: Int,
        @Query("status") status: String? = null,
        @Header("X-Auth-Token") apiKey: String
    ): TeamMatchesResponse
}

object FootballRetrofitInstance {
    private const val BASE_URL = "https://api.football-data.org/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val apiService: FootballApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(FootballApiService::class.java)
    }
}
