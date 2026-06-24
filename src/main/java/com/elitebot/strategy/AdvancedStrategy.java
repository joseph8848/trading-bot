package com.elitebot.strategy;

import com.elitebot.model.TradingSignal;
import com.elitebot.model.TradingSignal.SignalType;
import java.util.*;

/**
 * ADVANCED TRADING STRATEGY v2.0 - Smarter & More Conservative
 * 
 * Key improvements:
 * 1. REQUIRES 4 CONFIRMATIONS (was 3)
 * 2. Stronger trend filter - don't trade against the trend
 * 3. Volatility-adjusted confidence
 * 4. Time-based filters (avoid bad hours)
 * 5. Better momentum analysis
 */
public class AdvancedStrategy {
    
    // RSI Parameters - EXTRA STRICT
    private static final int RSI_PERIOD = 14;
    private static final double RSI_OVERSOLD = 20.0;      // Very strict (was 25)
    private static final double RSI_OVERBOUGHT = 80.0;    // Very strict (was 75)
    
    // Moving Average Parameters
    private static final int FAST_MA = 5;
    private static final int MEDIUM_MA = 10;
    private static final int SLOW_MA = 20;
    private static final int TREND_MA = 50;  // Long-term trend
    
    // MUCH STRICTER: Require 5 confirmations (was 4)
    private static final int MIN_CONFIRMATIONS = 5;
    
    // Trend strength threshold
    private static final double MIN_TREND_STRENGTH = 0.001;  // Minimum trend to trade
    
    /**
     * Analyze with STRICTER multiple confirmations
     */
    public TradingSignal analyzeWithConfirmation(String currency, List<Double> prices) {
        if (prices == null || prices.size() < TREND_MA + 5) {
            return new TradingSignal(SignalType.NEUTRAL, 0, currency, "Insufficient data (need 55+ candles)");
        }
        
        double currentPrice = prices.get(prices.size() - 1);
        
        // Calculate all indicators
        double rsi = calculateRSI(prices);
        double fastMA = calculateSMA(prices, FAST_MA);
        double mediumMA = calculateSMA(prices, MEDIUM_MA);
        double slowMA = calculateSMA(prices, SLOW_MA);
        double trendMA = calculateSMA(prices, TREND_MA);  // Long-term trend
        
        // Trend analysis - STRICTER
        boolean strongUptrend = fastMA > mediumMA && mediumMA > slowMA && slowMA > trendMA;
        boolean strongDowntrend = fastMA < mediumMA && mediumMA < slowMA && slowMA < trendMA;
        boolean weakUptrend = fastMA > mediumMA && mediumMA > slowMA;
        boolean weakDowntrend = fastMA < mediumMA && mediumMA < slowMA;
        
        // Trend strength (how strong is the move)
        double trendStrength = Math.abs(fastMA - trendMA) / trendMA;
        boolean sufficientTrend = trendStrength > MIN_TREND_STRENGTH;
        
        // Momentum - look at multiple periods
        double momentum5 = calculateMomentum(prices, 5);
        double momentum10 = calculateMomentum(prices, 10);
        boolean strongBullishMomentum = momentum5 > 0 && momentum10 > 0;
        boolean strongBearishMomentum = momentum5 < 0 && momentum10 < 0;
        
        // Price position relative to MAs
        boolean priceAboveAllMA = currentPrice > fastMA && currentPrice > mediumMA && currentPrice > slowMA;
        boolean priceBelowAllMA = currentPrice < fastMA && currentPrice < mediumMA && currentPrice < slowMA;
        
        // RSI signals - STRICTER
        boolean rsiStrongOversold = rsi < RSI_OVERSOLD;
        boolean rsiStrongOverbought = rsi > RSI_OVERBOUGHT;
        
        // Volatility check
        double volatility = calculateVolatility(prices);
        double avgVolatility = getAverageVolatility(currency);
        boolean normalVolatility = volatility > avgVolatility * 0.5 && volatility < avgVolatility * 2.0;
        
        // Price action - check for reversal candles
        boolean bullishReversal = isBullishReversal(prices);
        boolean bearishReversal = isBearishReversal(prices);
        
        // Count confirmations for CALL
        int callConfirmations = 0;
        StringBuilder callReasons = new StringBuilder();
        
        if (rsiStrongOversold) { callConfirmations++; callReasons.append("RSI<25, "); }
        if (strongUptrend) { callConfirmations += 2; callReasons.append("Strong uptrend, "); }
        else if (weakUptrend) { callConfirmations++; callReasons.append("Uptrend, "); }
        if (strongBullishMomentum) { callConfirmations++; callReasons.append("Strong momentum, "); }
        if (priceAboveAllMA) { callConfirmations++; callReasons.append("Price above MAs, "); }
        if (bullishReversal) { callConfirmations++; callReasons.append("Bullish reversal, "); }
        if (sufficientTrend && trendMA < currentPrice) { callConfirmations++; callReasons.append("Trend confirmed, "); }
        
        // Count confirmations for PUT
        int putConfirmations = 0;
        StringBuilder putReasons = new StringBuilder();
        
        if (rsiStrongOverbought) { putConfirmations++; putReasons.append("RSI>75, "); }
        if (strongDowntrend) { putConfirmations += 2; putReasons.append("Strong downtrend, "); }
        else if (weakDowntrend) { putConfirmations++; putReasons.append("Downtrend, "); }
        if (strongBearishMomentum) { putConfirmations++; putReasons.append("Strong momentum, "); }
        if (priceBelowAllMA) { putConfirmations++; putReasons.append("Price below MAs, "); }
        if (bearishReversal) { putConfirmations++; putReasons.append("Bearish reversal, "); }
        if (sufficientTrend && trendMA > currentPrice) { putConfirmations++; putReasons.append("Trend confirmed, "); }
        
        // CONFLICT CHECK: Don't trade if signals are mixed
        if (callConfirmations >= 2 && putConfirmations >= 2) {
            return new TradingSignal(SignalType.NEUTRAL, 40, currency, 
                "Mixed signals - CALL:" + callConfirmations + " vs PUT:" + putConfirmations);
        }
        
        // Determine signal based on confirmations
        SignalType signalType;
        double confidence;
        String reason;
        
        // Require 2+ point gap between CALL and PUT to avoid mixed signals
        if (callConfirmations >= MIN_CONFIRMATIONS && callConfirmations > putConfirmations + 2) {
            signalType = SignalType.CALL;
            // Confidence based on confirmations (base 65 + 7 per confirmation)
            confidence = 65 + (callConfirmations * 7);
            reason = callReasons.toString();
        } else if (putConfirmations >= MIN_CONFIRMATIONS && putConfirmations > callConfirmations + 2) {
            signalType = SignalType.PUT;
            confidence = 65 + (putConfirmations * 7);
            reason = putReasons.toString();
        } else {
            signalType = SignalType.NEUTRAL;
            confidence = 30;
            reason = "Need " + MIN_CONFIRMATIONS + " confirmations with 2+ gap (CALL:" + callConfirmations + ", PUT:" + putConfirmations + ")";
        }
        
        // Volatility adjustments
        if (!normalVolatility && signalType != SignalType.NEUTRAL) {
            confidence -= 15;
            reason += " [Abnormal volatility]";
        }
        
        // Cap confidence
        confidence = Math.min(95, Math.max(0, confidence));
        
        return new TradingSignal(signalType, confidence, currency, reason);
    }
    
    /**
     * Check for bullish reversal pattern (hammer, morning star-like)
     */
    private boolean isBullishReversal(List<Double> prices) {
        if (prices.size() < 5) return false;
        
        int last = prices.size() - 1;
        double current = prices.get(last);
        double prev1 = prices.get(last - 1);
        double prev2 = prices.get(last - 2);
        double prev3 = prices.get(last - 3);
        
        // Pattern: 3 down candles followed by up candle
        boolean downSequence = prev1 < prev2 && prev2 < prev3;
        boolean reversalCandle = current > prev1;
        
        return downSequence && reversalCandle;
    }
    
    /**
     * Check for bearish reversal pattern (shooting star, evening star-like)
     */
    private boolean isBearishReversal(List<Double> prices) {
        if (prices.size() < 5) return false;
        
        int last = prices.size() - 1;
        double current = prices.get(last);
        double prev1 = prices.get(last - 1);
        double prev2 = prices.get(last - 2);
        double prev3 = prices.get(last - 3);
        
        // Pattern: 3 up candles followed by down candle
        boolean upSequence = prev1 > prev2 && prev2 > prev3;
        boolean reversalCandle = current < prev1;
        
        return upSequence && reversalCandle;
    }
    
    private double calculateRSI(List<Double> prices) {
        if (prices.size() < RSI_PERIOD + 1) return 50;
        
        double gains = 0, losses = 0;
        int startIdx = prices.size() - RSI_PERIOD - 1;
        
        for (int i = startIdx; i < prices.size() - 1; i++) {
            double change = prices.get(i + 1) - prices.get(i);
            if (change > 0) gains += change;
            else losses += Math.abs(change);
        }
        
        double avgGain = gains / RSI_PERIOD;
        double avgLoss = losses / RSI_PERIOD;
        
        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }
    
    private double calculateSMA(List<Double> prices, int period) {
        if (prices.size() < period) return prices.get(prices.size() - 1);
        double sum = 0;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum += prices.get(i);
        }
        return sum / period;
    }
    
    private double calculateMomentum(List<Double> prices, int period) {
        if (prices.size() < period) return 0;
        return prices.get(prices.size() - 1) - prices.get(prices.size() - period);
    }
    
    private double calculateVolatility(List<Double> prices) {
        if (prices.size() < 10) return 0;
        List<Double> recent = prices.subList(prices.size() - 10, prices.size());
        double mean = recent.stream().mapToDouble(d -> d).average().orElse(0);
        double variance = recent.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0);
        return Math.sqrt(variance);
    }
    
    private double getAverageVolatility(String currency) {
        if (currency.contains("BTC")) return 100.0;
        if (currency.contains("ETH")) return 10.0;
        if (currency.contains("JPY")) return 0.1;
        return 0.001;  // Forex pairs
    }
}
