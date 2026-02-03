package exchange_api.oneinch.rest.response

import com.google.gson.annotations.SerializedName

/**
 * EIP-712 Limit Order V4 struct fields as submitted to 1inch orderbook API.
 */
data class LimitOrderStruct(
    val salt: String,
    val maker: String,
    val receiver: String,
    val makerAsset: String,
    val takerAsset: String,
    val makingAmount: String,
    val takingAmount: String,
    val makerTraits: String
)

/**
 * Request body for POST /orderbook/v4.1/56 (submit limit order).
 */
data class SubmitOrderRequest(
    val orderHash: String,
    val signature: String,
    val data: LimitOrderStruct
)

/**
 * Response from POST /orderbook/v4.1/56.
 * On success the API returns the order hash confirming acceptance.
 */
data class SubmitOrderResponse(
    val success: Boolean?,
    val orderHash: String?
)

/**
 * Single order from the 1inch orderbook API.
 * Used for GET /orderbook/v4.0/56/address/{addr} and GET /orderbook/v4.1/56/order/{hash}.
 */
data class OneInchOrderResponse(
    val orderHash: String?,
    val signature: String?,
    val data: OrderData?,
    val remainingMakerAmount: String?,
    val makerBalance: String?,
    val makerAllowance: String?,
    val makerRate: String?,
    val takerRate: String?,
    @SerializedName("isMakerContract")
    val isMakerContract: Boolean?,
    val orderInvalidReason: String?,
    val createDateTime: String?
) {
    data class OrderData(
        val salt: String?,
        val maker: String?,
        val receiver: String?,
        val makerAsset: String?,
        val takerAsset: String?,
        val makingAmount: String?,
        val takingAmount: String?,
        val makerTraits: String?
    )

    fun isActive(): Boolean = orderInvalidReason == null || orderInvalidReason.isEmpty()

    fun isFilled(): Boolean {
        val remaining = remainingMakerAmount?.toBigIntegerOrNull() ?: return false
        return remaining == java.math.BigInteger.ZERO
    }

    fun isPartiallyFilled(): Boolean {
        if (isFilled()) return false
        val remaining = remainingMakerAmount?.toBigIntegerOrNull() ?: return false
        val original = data?.makingAmount?.toBigIntegerOrNull() ?: return false
        return remaining < original
    }
}

/**
 * Balance API response: GET /balance/v1.2/56/balances/{wallet}
 * Returns a map of token address -> balance in wei (as string).
 */
typealias BalancesResponse = Map<String, String>

/**
 * Price API response: GET /price/v1.1/56/{tokens}
 * Returns a map of token address -> USD price (as string).
 */
typealias TokenPricesResponse = Map<String, String>
