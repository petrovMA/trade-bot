package io.bitmax.api.websocket.messages.responses

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class WebSocketBar(
        val m: String,
        /** product symbol */
        val s: String? = null,
        val data: BarData
)

data class BarData(
        /** base asset */
        val ba: String? = null,
        /** quote asset */
        val qa: String? = null,
        /** for market summary data, the interval is always 1d */
        val i: String,
        /** timestamp in UTC */
        val ts: Long = 0,
        /** open */
        val o: String,
        /** open */
        val c: String,
        /** high */
        val h: String,
        /** low */
        val l: String,
        /** volume */
        val v: String
)