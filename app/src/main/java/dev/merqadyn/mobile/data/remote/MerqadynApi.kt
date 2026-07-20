package dev.merqadyn.mobile.data.remote

import dev.merqadyn.mobile.data.ConnectionProfile
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface MerqadynApi {
    @POST("api/v1/device-enrollments/redeem")
    suspend fun redeem(@Body request: RedeemEnrollmentRequestDto): DeviceCredentialsDto

    @GET("api/v1/merchants/{merchantId}/context")
    suspend fun context(@Path("merchantId") merchantId: String): MerchantContextDto

    @GET("api/v1/merchants/{merchantId}/products/page")
    suspend fun productPage(
        @Path("merchantId") merchantId: String,
        @Query("page") page: Int,
        @Query("size") size: Int = 200,
    ): ProductPageDto

    @GET("api/v1/merchants/{merchantId}/inventory/page")
    suspend fun inventoryPage(
        @Path("merchantId") merchantId: String,
        @Query("page") page: Int,
        @Query("size") size: Int = 200,
    ): InventoryPageDto

    @POST("api/v1/merchants/{merchantId}/sync/batches")
    suspend fun sync(
        @Path("merchantId") merchantId: String,
        @Body request: SyncBatchRequestDto,
    ): SyncBatchResponseDto

    @DELETE("api/v1/merchants/{merchantId}/devices/{deviceId}/credential")
    suspend fun revoke(
        @Path("merchantId") merchantId: String,
        @Path("deviceId") deviceId: String,
    )
}

object ApiFactory {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun create(profile: ConnectionProfile): MerqadynApi = create(profile.serverUrl, profile)

    fun createEnrollment(baseUrl: String): MerqadynApi = create(baseUrl, null)

    private fun create(baseUrl: String, profile: ConnectionProfile?): MerqadynApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder().header("Accept", "application/json")
                if (profile != null) {
                    builder.header("X-Merqadyn-Device-Id", profile.deviceId)
                    builder.header("X-Merqadyn-Device-Token", profile.deviceToken)
                }
                chain.proceed(builder.build())
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MerqadynApi::class.java)
    }
}

private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"
