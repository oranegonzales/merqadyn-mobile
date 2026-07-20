package dev.merqadyn.mobile.data.remote

import dev.merqadyn.mobile.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import okhttp3.MediaType.Companion.toMediaType

interface MerqadynApi {
    @GET("api/v1/config")
    suspend fun config(): PublicConfigDto

    @GET("api/v1/merchants/{merchantId}/overview")
    suspend fun overview(@Path("merchantId") merchantId: String): OverviewDto

    @GET("api/v1/merchants/{merchantId}/products")
    suspend fun products(@Path("merchantId") merchantId: String): List<ProductDto>

    @GET("api/v1/merchants/{merchantId}/inventory")
    suspend fun inventory(@Path("merchantId") merchantId: String): List<InventoryDto>

    @POST("api/v1/merchants/{merchantId}/sync/batches")
    suspend fun sync(
        @Path("merchantId") merchantId: String,
        @Body request: SyncBatchRequestDto,
    ): SyncBatchResponseDto
}

object ApiFactory {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun create(): MerqadynApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request: Request = if (BuildConfig.ADMIN_USER.isNotBlank() && BuildConfig.ADMIN_PASSWORD.isNotBlank()) {
                    chain.request().newBuilder()
                        .header("Authorization", Credentials.basic(BuildConfig.ADMIN_USER, BuildConfig.ADMIN_PASSWORD))
                        .header("Accept", "application/json")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MerqadynApi::class.java)
    }
}

private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"
