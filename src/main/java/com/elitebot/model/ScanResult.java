package com.elitebot.model;

/**
 * Represents the result of scanning a single currency pair.
 * Contains the signal, confidence, and recommended trade duration.
 */
public class ScanResult implements Comparable<ScanResult> {
    
    private final String currency;
    private final TradingSignal signal;
    private final int recommendedTimeMinutes;
    private final double volatility;
    
    public ScanResult(String currency, TradingSignal signal, int recommendedTimeMinutes, double volatility) {
        this.currency = currency;
        this.signal = signal;
        this.recommendedTimeMinutes = recommendedTimeMinutes;
        this.volatility = volatility;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public TradingSignal getSignal() {
        return signal;
    }
    
    public int getRecommendedTimeMinutes() {
        return recommendedTimeMinutes;
    }
    
    public double getVolatility() {
        return volatility;
    }
    
    public double getConfidence() {
        return signal.getConfidence();
    }
    
    public boolean isActionable() {
        return signal.isActionable();
    }
    
    /**
     * Compare by confidence (descending order - highest confidence first)
     */
    @Override
    public int compareTo(ScanResult other) {
        return Double.compare(other.getConfidence(), this.getConfidence());
    }
    
    @Override
    public String toString() {
        return String.format("%s: %s %s (%.1f%%) - %d min", 
            currency, 
            signal.getSignalType(),
            signal.getStrengthLabel(),
            signal.getConfidence(),
            recommendedTimeMinutes);
    }
}
