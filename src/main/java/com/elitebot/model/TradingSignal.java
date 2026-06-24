package com.elitebot.model;

import java.time.LocalDateTime;

/**
 * Represents a trading signal with direction, confidence, and metadata.
 */
public class TradingSignal {
    
    public enum SignalType {
        CALL,    // Buy / Up
        PUT,     // Sell / Down
        NEUTRAL  // No clear signal
    }
    
    private final SignalType signalType;
    private final double confidence;  // 0.0 to 100.0
    private final String currency;
    private final LocalDateTime timestamp;
    private final String reason;
    
    public TradingSignal(SignalType signalType, double confidence, String currency, String reason) {
        this.signalType = signalType;
        this.confidence = Math.max(0, Math.min(100, confidence));
        this.currency = currency;
        this.timestamp = LocalDateTime.now();
        this.reason = reason;
    }
    
    public SignalType getSignalType() {
        return signalType;
    }
    
    public double getConfidence() {
        return confidence;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public String getReason() {
        return reason;
    }
    
    public boolean isActionable() {
        return signalType != SignalType.NEUTRAL && confidence >= 60.0;
    }
    
    public String getStrengthLabel() {
        if (confidence >= 85) return "STRONG";
        if (confidence >= 70) return "MEDIUM";
        if (confidence >= 60) return "WEAK";
        return "NO TRADE";
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s %.1f%% - %s (%s)", 
            currency, signalType, confidence, getStrengthLabel(), reason);
    }
}
