package com.elitebot.model;

import java.time.LocalDateTime;

/**
 * Represents a completed or pending trade for history tracking.
 */
public class Trade {
    
    public enum TradeStatus {
        PENDING,
        WON,
        LOST,
        CANCELLED
    }
    
    private final String id;
    private final String currency;
    private final TradingSignal.SignalType direction;
    private final double entryPrice;
    private final int durationMinutes;
    private final double confidence;
    private final double amount;
    private final LocalDateTime entryTime;
    
    private TradeStatus status;
    private double exitPrice;
    private LocalDateTime exitTime;
    
    public Trade(String currency, TradingSignal.SignalType direction, 
                 double entryPrice, int durationMinutes, double confidence, double amount) {
        this.id = generateId();
        this.currency = currency;
        this.direction = direction;
        this.entryPrice = entryPrice;
        this.durationMinutes = durationMinutes;
        this.confidence = confidence;
        this.amount = amount;
        this.entryTime = LocalDateTime.now();
        this.status = TradeStatus.PENDING;
    }
    
    private String generateId() {
        return "T" + System.currentTimeMillis() % 100000;
    }
    
    public void complete(double exitPrice) {
        this.exitPrice = exitPrice;
        this.exitTime = LocalDateTime.now();
        
        boolean priceWentUp = exitPrice > entryPrice;
        boolean calledUp = direction == TradingSignal.SignalType.CALL;
        
        this.status = (priceWentUp == calledUp) ? TradeStatus.WON : TradeStatus.LOST;
    }
    
    public void cancel() {
        this.status = TradeStatus.CANCELLED;
        this.exitTime = LocalDateTime.now();
    }
    
    // Getters
    public String getId() { return id; }
    public String getCurrency() { return currency; }
    public TradingSignal.SignalType getDirection() { return direction; }
    public double getEntryPrice() { return entryPrice; }
    public int getDurationMinutes() { return durationMinutes; }
    public double getConfidence() { return confidence; }
    public double getAmount() { return amount; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public TradeStatus getStatus() { return status; }
    public double getExitPrice() { return exitPrice; }
    public LocalDateTime getExitTime() { return exitTime; }
    
    public boolean isPending() { return status == TradeStatus.PENDING; }
    public boolean isWon() { return status == TradeStatus.WON; }
    
    @Override
    public String toString() {
        String result = status == TradeStatus.PENDING ? "⏳" :
                        status == TradeStatus.WON ? "✅" :
                        status == TradeStatus.LOST ? "❌" : "🚫";
        return String.format("%s %s %s %s $%.2f @ %.5f (%d min)", 
            result, id, currency, direction, amount, entryPrice, durationMinutes);
    }
}
