package io.bitmax.api.websocket.messages.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.gson.annotations.SerializedName;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WebSocketSummary {

    /**
     * symbol
     */
    @SerializedName("s")
    private String symbol;

    /**
     * base asset
     */
    @SerializedName("ba")
    private String baseAsset;

    /**
     * quote asset
     */
    @SerializedName("qa")
    private String quoteAsset;

    /**
     * for market summary data, the interval is always 1d
     */
    @SerializedName("i")
    private String interval;

    /**
     * timestamp in UTC
     */
    @SerializedName("t")
    private long timestamp;

    /**
     * open
     */
    @SerializedName("o")
    private double open;

    /**
     * close
     */
    @SerializedName("c")
    private double close;

    /**
     * high
     */
    @SerializedName("h")
    private double high;

    /**
     * low
     */
    @SerializedName("l")
    private double low;

    /**
     * volume
     */
    @SerializedName("v")
    private double volume;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getBaseAsset() {
        return baseAsset;
    }

    public void setBaseAsset(String baseAsset) {
        this.baseAsset = baseAsset;
    }

    public String getQuoteAsset() {
        return quoteAsset;
    }

    public void setQuoteAsset(String quoteAsset) {
        this.quoteAsset = quoteAsset;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getOpen() {
        return open;
    }

    public void setOpen(double open) {
        this.open = open;
    }

    public double getClose() {
        return close;
    }

    public void setClose(double close) {
        this.close = close;
    }

    public double getHigh() {
        return high;
    }

    public void setHigh(double high) {
        this.high = high;
    }

    public double getLow() {
        return low;
    }

    public void setLow(double low) {
        this.low = low;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    @Override
    public String toString() {
        return "Summary:\n\tsymbol: " + symbol + '\n' +
                "\tbaseAsset: " + baseAsset + '\n' +
                "\tquoteAsset: " + quoteAsset + '\n' +
                "\tinterval: " + interval + '\n' +
                "\ttimestamp: " + timestamp + '\n' +
                "\topen: " + open + '\n' +
                "\tclose: " + close + '\n' +
                "\thigh: " + high + '\n' +
                "\tlow: " + low + '\n' +
                "\tvolume: " + volume;
    }
}
