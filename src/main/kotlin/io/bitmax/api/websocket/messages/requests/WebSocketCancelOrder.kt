package io.bitmax.api.websocket.messages.requests;


import com.google.gson.annotations.SerializedName;

/**
 * Cancel an Order with WebSocket
 */
public class WebSocketCancelOrder {

    /**
     * message type
     */
    @SerializedName("messageType")
    private String messageType;

    /**
     * milliseconds since UNIX epoch in UTC
     */
    @SerializedName("time")
    private long time;

    /**
     * a 32-character unique client order Id
     */
    @SerializedName("coid")
    private String coid;

    /**
     * the coid of the order to be canceled
     */
    @SerializedName("origCoid")
    private String origCoid;

    /**
     * symbol
     */
    @SerializedName("symbol")
    private String symbol;

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getCoid() {
        return coid;
    }

    public void setCoid(String coid) {
        this.coid = coid;
    }

    public String getOrigCoid() {
        return origCoid;
    }

    public void setOrigCoid(String origCoid) {
        this.origCoid = origCoid;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public String toString() {
        return "WebSocketCancelOrder:\n\tmessageType: " + messageType +
                "\n\ttime: " + time +
                "\n\tcoid: " + coid +
                "\n\tsymbol: " + symbol +
                "\n\torigCoid: " + origCoid;
    }
}
