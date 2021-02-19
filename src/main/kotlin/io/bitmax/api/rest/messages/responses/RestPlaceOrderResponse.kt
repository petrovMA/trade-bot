package io.bitmax.api.rest.messages.responses

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.gson.annotations.SerializedName

@JsonIgnoreProperties(ignoreUnknown = true)
class RestPlaceOrderResponse {
    /**
     * time
     */
    @SerializedName("time")
    var time: Long = 0

    /**
     * the unique identifier, you will need
     */
    @SerializedName("coid")
    var coid: String? = null

    /**
     * symbol
     */
    @SerializedName("symbol")
    var symbol: String? = null

    /**
     * base asset
     */
    @SerializedName("baseAsset")
    var baseAsset: String? = null

    /**
     * quote asset
     */
    @SerializedName("quoteAsset")
    var quoteAsset: String? = null

    /**
     * order side
     */
    @SerializedName("side")
    var side: String? = null

    /**
     * order price - only available for limit and stop limit orders
     */
    @SerializedName("orderPrice")
    var orderPrice: String? = null

    /**
     * order stop price - only available for stop market and stop limit orders
     */
    @SerializedName("stopPrice")
    var stopPrice: String? = null

    /**
     * order quantity
     */
    @SerializedName("orderQty")
    var orderQty: String? = null

    /**
     * filled quantity
     */
    @SerializedName("filledQty")
    var filledQty: String? = null

    /**
     * cumulative fee paid for this order
     */
    @SerializedName("fee")
    var fee: String? = null

    /**
     * the asset of fee
     */
    @SerializedName("feeAsset")
    var feeAsset: String? = null

    /**
     * order status
     */
    @SerializedName("status")
    var status: String? = null
    override fun toString(): String {
        return """
Order:
	time: $time
	coid: $coid
	symbol: $symbol
	baseAsset: $baseAsset
	quoteAsset: $quoteAsset
	side: $side
	orderPrice: $orderPrice
	stopPrice: $stopPrice
	orderQty: $orderQty
	filledQty: $filledQty
	fee: $fee
	feeAsset: $feeAsset
	status: $status"""
    }
}