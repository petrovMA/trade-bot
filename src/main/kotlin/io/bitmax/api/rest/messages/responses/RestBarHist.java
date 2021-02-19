package io.bitmax.api.rest.messages.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.gson.annotations.SerializedName;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RestBarHist {

    /**
     * message
     */
    @SerializedName("m")
    private String message;

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
     * interval: 1/5/30/60/360/1d
     */
    @SerializedName("i")
    private String interval;

    /**
     * time
     */
    @SerializedName("t")
    private long time;

    /**
     * open
     */
    @SerializedName("o")
    private String open;

    /**
     * close
     */
    @SerializedName("c")
    private String close;

    /**
     * high
     */
    @SerializedName("h")
    private String high;

    /**
     * low
     */
    @SerializedName("l")
    private String low;

    /**
     * volume
     */
    @SerializedName("v")
    private String volume;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

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

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getOpen() {
        return open;
    }

    public void setOpen(String open) {
        this.open = open;
    }

    public String getClose() {
        return close;
    }

    public void setClose(String close) {
        this.close = close;
    }

    public String getHigh() {
        return high;
    }

    public void setHigh(String high) {
        this.high = high;
    }

    public String getLow() {
        return low;
    }

    public void setLow(String low) {
        this.low = low;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    @Override
    public String toString() {
        return "\nBarHist:\n\tmessage: " + message + '\n' +
                "\tsymbol: " + symbol + '\n' +
                "\tbaseAsset: " + baseAsset + '\n' +
                "\tquoteAsset: " + quoteAsset + '\n' +
                "\tinterval: " + interval + '\n' +
                "\ttime: " + time + '\n' +
                "\topen: " + open + '\n' +
                "\tclose: " + close + '\n' +
                "\thigh: " + high + '\n' +
                "\tlow: " + low + '\n' +
                "\tvolume: " + volume;
    }
}
