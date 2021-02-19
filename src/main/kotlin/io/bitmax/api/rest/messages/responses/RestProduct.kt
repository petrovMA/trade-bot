package io.bitmax.api.rest.messages.responses

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.gson.annotations.SerializedName

@JsonIgnoreProperties(ignoreUnknown = true)
class RestProduct {
    @SerializedName("symbol")
    var symbol: String? = null

    @SerializedName("domain")
    var domain: String? = null

    @SerializedName("baseAsset")
    var baseAsset: String? = null

    @SerializedName("quoteAsset")
    var quoteAsset: String? = null

    /**
     * price scale - maximum precision for order price allowed to place an order, see below for details
     */
    @SerializedName("priceScale")
    var priceScale = 0

    /**
     * quantity scale - maximum precision for order quantity allowed to place an order, see below for details
     */
    @SerializedName("qtyScale")
    var qtyScale = 0

    @SerializedName("notionalScale")
    var notionalScale = 0

    @SerializedName("minQty")
    var minQty = 0.0

    @SerializedName("maxQty")
    var maxQty = 0.0

    @SerializedName("minNotional")
    var minNotional = 0.0

    @SerializedName("maxNotional")
    var maxNotional = 0.0

    @SerializedName("status")
    var status: String? = null

    @SerializedName("miningStatus")
    var miningStatus: String? = null

    @SerializedName("marginTradable")
    var marginTradable: String? = null
    override fun toString(): String {
        return """
Product:
	symbol: $symbol
	domain: $domain
	baseAsset: $baseAsset
	quoteAsset: $quoteAsset
	priceScale: $priceScale
	qtyScale: $qtyScale
	notionalScale: $notionalScale
	minQty: $minQty
	maxQty: $maxQty
	minNotional: $minNotional
	maxNotional: $maxNotional
	status: $status
	miningStatus: $miningStatus
	marginTradable: $marginTradable"""
    }
}