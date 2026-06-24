package com.elitebot.strategy;

import com.elitebot.model.TradingSignal;
import com.elitebot.model.TradingSignal.SignalType;
import java.util.List;

/**
 * Trading strategy using RSI and Moving Average indicators.
 * Analyzes price data and generates trading signals with confidence scores.
 */
public class SimpleStrategy {
    
    // RSI Parameters
    private static final int RSI_PERIOD = 14;
    private static final double RSI_OVERSOLD = 30.0;
    private static final double RSI_OVERBOUGHT = 70.0;
    
    // Moving Average Parameters
    private static final int FAST_MA_PERIOD = 5;
    private static final int SLOW_MA_PERIOD = 12;
    
    /**
     * Analyze market data and generate a trading signal
     */
    public TradingSignal analyze(String currency, List<Double> prices) {
        if (prices == null || prices.size() < SLOW_MA_PERIOD + 5) {
            return new TradingSignal(SignalType.NEUTRAL, 0, currency, "Insufficient data");
        }
        
        // Calculate indicators
        double rsi = calculateRSI(prices);
        double fastMA = calculateSMA(prices, FAST_MA_PERIOD);
        double slowMA = calculateSMA(prices, SLOW_MA_PERIOD);
        double currentPrice = prices.get(prices.size() - 1);
        
        // Trend direction from MA
        boolean maBullish = fastMA > slowMA;
        double maCrossStrength = Math.abs(fastMA - slowMA) / slowMA * 1000;
        
        // RSI signals
        boolean rsiOversold = rsi < RSI_OVERSOLD;
        boolean rsiOverbought = rsi > RSI_OVERBOUGHT;
        double rsiStrength = rsiOversold ? (RSI_OVERSOLD - rsi) : 
                             rsiOverbought ? (rsi - RSI_OVERBOUGHT) : 0;
        
        // Determine signal direction
        SignalType signalType;
        double confidence = 50;  // Base confidence
        StringBuilder reason = new StringBuilder();
        
        // Strong CALL signal: RSI oversold + MA bullish
        if (rsiOversold && maBullish) {
            signalType = SignalType.CALL;
            confidence = 75 + (rsiStrength * 0.5) + (maCrossStrength * 5);
            reason.append("RSI oversold (").append(String.format("%.1f", rsi)).append(") + MA bullish");
        }
        // Strong PUT signal: RSI overbought + MA bearish
        else if (rsiOverbought && !maBullish) {
            signalType = SignalType.PUT;
            confidence = 75 + (rsiStrength * 0.5) + (maCrossStrength * 5);
            reason.append("RSI overbought (").append(String.format("%.1f", rsi)).append(") + MA bearish");
        }
        // Moderate CALL: Just RSI oversold
        else if (rsiOversold) {
            signalType = SignalType.CALL;
            confidence = 65 + (rsiStrength * 0.3);
            reason.append("RSI oversold (").append(String.format("%.1f", rsi)).append(")");
        }
        // Moderate PUT: Just RSI overbought
        else if (rsiOverbought) {
            signalType = SignalType.PUT;
            confidence = 65 + (rsiStrength * 0.3);
            reason.append("RSI overbought (").append(String.format("%.1f", rsi)).append(")");
        }
        // Weak CALL: MA bullish with momentum
        else if (maBullish && maCrossStrength > 1) {
            signalType = SignalType.CALL;
            confidence = 55 + (maCrossStrength * 3);
            reason.append("MA bullish crossover");
        }
        // Weak PUT: MA bearish with momentum
        else if (!maBullish && maCrossStrength > 1) {
            signalType = SignalType.PUT;
            confidence = 55 + (maCrossStrength * 3);
            reason.append("MA bearish crossover");
        }
        // No clear signal
        else {
            signalType = SignalType.NEUTRAL;
            confidence = 40;
            reason.append("No clear pattern");
        }
        
        // Cap confidence at 100
        confidence = Math.min(100, confidence);
        
        return new TradingSignal(signalType, confidence, currency, reason.toString());
    }
    
    /**
     * Calculate Relative Strength Index (RSI)
     */
    private double calculateRSI(List<Double> prices) {
        if (prices.size() < RSI_PERIOD + 1) return 50;
        
        double gains = 0, losses = 0;
        
        int startIdx = prices.size() - RSI_PERIOD - 1;
        for (int i = startIdx; i < prices.size() - 1; i++) {
            double change = prices.get(i + 1) - prices.get(i);
            if (change > 0) {
                gains += change;
            } else {
                losses += Math.abs(change);
            }
        }
        
        double avgGain = gains / RSI_PERIOD;
        double avgLoss = losses / RSI_PERIOD;
        
        if (avgLoss == 0) return 100;
        
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }
    
    /**
     * Calculate Simple Moving Average
     */
    private double calculateSMA(List<Double> prices, int period) {
        if (prices.size() < period) return prices.get(prices.size() - 1);
        
        double sum = 0;
        int startIdx = prices.size() - period;
        for (int i = startIdx; i < prices.size(); i++) {
            sum += prices.get(i);
        }
        return sum / period;
    }
    
    /**
     * Calculate Exponential Moving Average (for future enhancements)
     */
    private double calculateEMA(List<Double> prices, int period) {
        if (prices.size() < period) return prices.get(prices.size() - 1);
        
        double multiplier = 2.0 / (period + 1);
        double ema = prices.get(prices.size() - period);  // Start with SMA
        
        for (int i = prices.size() - period + 1; i < prices.size(); i++) {
            ema = (prices.get(i) - ema) * multiplier + ema;
        }
        return ema;
    }
}
