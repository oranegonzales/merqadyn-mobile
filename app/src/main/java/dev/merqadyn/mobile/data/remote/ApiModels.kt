package dev.merqadyn.mobile.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonEncoder
import java.math.BigDecimal

object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BigDecimal =
        if (decoder is JsonDecoder) decoder.decodeJsonElement().toString().trim('"').toBigDecimal()
        else decoder.decodeString().toBigDecimal()

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        if (encoder is JsonEncoder) encoder.encodeJsonElement(JsonPrimitive(value))
        else encoder.encodeString(value.toPlainString())
    }
}

@Serializable
data class PublicConfigDto(val demoMerchantId: String)

@Serializable
data class RedeemEnrollmentRequestDto(val deviceId: String, val code: String)

@Serializable
data class DeviceCredentialsDto(val merchantId: String, val deviceId: String, val deviceToken: String)

@Serializable
data class ProductDto(
    val id: String,
    val sku: String,
    val name: String,
    val category: String,
    val unit: String,
    @Serializable(with = BigDecimalSerializer::class) val price: BigDecimal,
    val active: Boolean,
    val version: Long,
    val updatedAt: String,
)

@Serializable
data class InventoryDto(
    val id: String,
    val locationId: String,
    val locationCode: String,
    val locationName: String,
    val productId: String,
    val sku: String,
    val productName: String,
    @Serializable(with = BigDecimalSerializer::class) val onHand: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val reserved: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val available: BigDecimal,
    val unit: String,
    val version: Long,
    val updatedAt: String,
)

@Serializable
data class MerchantDto(val id: String, val name: String, val currency: String, val timezone: String)

@Serializable
data class OverviewDto(
    val merchant: MerchantDto,
    val productCount: Int,
    val locationCount: Int,
    val deviceCount: Int,
    @Serializable(with = BigDecimalSerializer::class) val unitsOnHand: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val inventoryValue: BigDecimal,
    val lowStockItems: Int,
    val conflictsLast24Hours: Long,
    val latestCursor: Long,
)

@Serializable
data class MerchantContextDto(
    val merchant: MerchantDto,
    val latestCursor: Long,
)

@Serializable
data class ProductPageDto(
    val items: List<ProductDto>,
    val page: Int,
    val size: Int,
    val hasMore: Boolean,
)

@Serializable
data class InventoryPageDto(
    val items: List<InventoryDto>,
    val page: Int,
    val size: Int,
    val hasMore: Boolean,
)

@Serializable
data class MutationDto(
    val mutationId: String,
    val type: String,
    val entityId: String? = null,
    val baseVersion: Long? = null,
    val payload: kotlinx.serialization.json.JsonObject,
    val occurredAt: String,
)

@Serializable
data class SyncBatchRequestDto(
    val deviceId: String,
    val lastPulledCursor: Long,
    val mutations: List<MutationDto>,
)

@Serializable
data class MutationResultDto(
    val mutationId: String,
    val status: String,
    val entityId: String? = null,
    val entityVersion: Long? = null,
    val message: String,
    val replayed: Boolean = false,
)

@Serializable
data class ChangeDto(
    val cursor: Long,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val entityVersion: Long,
    val payload: kotlinx.serialization.json.JsonObject,
    val occurredAt: String,
)

@Serializable
data class SyncBatchResponseDto(
    val deviceId: String,
    val accepted: Int,
    val replayed: Int,
    val conflicted: Int,
    val rejected: Int,
    val results: List<MutationResultDto>,
    val changes: List<ChangeDto>,
    val nextCursor: Long,
    val hasMore: Boolean,
    val completedAt: String,
)
