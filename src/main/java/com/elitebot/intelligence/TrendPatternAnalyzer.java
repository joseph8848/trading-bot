package com.elitebot.intelligence;

import java.util.*;

/**
 * Trend Pattern Analyzer
 * 
 * Analyzes market trends like professional traders:
 * - Trend direction (up, down, sideways)
 * - Pattern detection (double top/bottom, breakouts)
 * - Support/resistance levels
 * - Predictions based on past movements
 */
public class TrendPatternAnalyzer {
    
    public enum Pattern {
        UPTREND, DOWNTREND, SIDEWAYS,
        DOUBLE_TOP, DOUBLE_BOTTOM,
        BREAKOUT_UP, BREAKOUT_DOWN,
        REVERSAL_BULLISH, REVERSAL_BEARISH,
        UNKNOWN
    }
    
    public enum Prediction {
        STRONG_UP, LIKELY_UP, NEUTRAL, LIKELY_DOWN, STRONG_DOWN
    }
    
    private Pattern currentPattern = Pattern.UNKNOWN;
    private Prediction currentPrediction = Prediction.NEUTRAL;
    private double predictionConfidence = 50;
    private double supportLevel = 0;
    private double resistanceLevel = 0;
    private int trendStrength = 0;
    
    public void analyzePatterns(List<Double> prices) {
        if (prices == null || prices.size() < 30) {
            currentPattern = Pattern.UNKNOWN;
            currentPrediction = Prediction.NEUTRAL;
            return;
        }
        
        calculateLevels(prices);
        currentPattern = detectTrend(prices);
        makePrediction(prices);
    }
    
    private void calculateLevels(List<Double> prices) {
        int lookback = Math.min(30, prices.size());
        double min = Double.MAX_VALUE, max = Double.MIN_VALUE;
        for (int i = prices.size() - lookback; i < prices.size(); i++) {
            if (prices.get(i) < min) min = prices.get(i);
            if (prices.get(i) > max) max = prices.get(i);
        }
        supportLevel = min;
        resistanceLevel = max;
    }
    
    private Pattern detectTrend(List<Double> prices) {
        int size = prices.size();
        double shortMA = avg(prices.subList(size - 5, size));
        double medMA = avg(prices.subList(size - 15, size));
        double longMA = avg(prices.subList(size - 30, size));
        
        if (shortMA > medMA && medMA > longMA) {
            trendStrength = 70;
            return Pattern.UPTREND;
        } else if (shortMA < medMA && medMA < longMA) {
            trendStrength = 70;
            return Pattern.DOWNTREND;
        }
        trendStrength = 30;
        return Pattern.SIDEWAYS;
    }
    
    private void makePrediction(List<Double> prices) {
        int bullish = 0, bearish = 0;
        int size = prices.size();
        
        // Trend following
        if (currentPattern == Pattern.UPTREND) bullish += 25;
        if (currentPattern == Pattern.DOWNTREND) bearish += 25;
        
        // Momentum
        double momentum = prices.get(size - 1) - prices.get(size - 5);
        if (momentum > 0) bullish += 20; else bearish += 20;
        
        // Position in range (mean reversion)
        double current = prices.get(size - 1);
        double range = resistanceLevel - supportLevel;
        if (range > 0) {
            double pos = (current - supportLevel) / range;
            if (pos < 0.3) bullish += 15;  // Near support
            if (pos > 0.7) bearish += 15;  // Near resistance
        }
        
        int net = bullish - bearish;
        predictionConfidence = 50 + Math.abs(net);
        
        if (net >= 35) currentPrediction = Prediction.STRONG_UP;
        else if (net >= 15) currentPrediction = Prediction.LIKELY_UP;
        else if (net <= -35) currentPrediction = Prediction.STRONG_DOWN;
        else if (net <= -15) currentPrediction = Prediction.LIKELY_DOWN;
        else currentPrediction = Prediction.NEUTRAL;
    }
    
    private double avg(List<Double> list) {
        return list.stream().mapToDouble(d -> d).average().orElse(0);
    }
    
    public boolean suggestsCall() {
        return currentPrediction == Prediction.STRONG_UP || currentPrediction == Prediction.LIKELY_UP;
    }
    
    public boolean suggestsPut() {
        return currentPrediction == Prediction.STRONG_DOWN || currentPrediction == Prediction.LIKELY_DOWN;
    }
    
    public double getConfidenceBoost() {
        switch (currentPrediction) {
            case STRONG_UP: case STRONG_DOWN: return 10;
            case LIKELY_UP: case LIKELY_DOWN: return 5;
            default: return 0;
        }
    }
    
    public Pattern getCurrentPattern() { return currentPattern; }
    public Prediction getCurrentPrediction() { return currentPrediction; }
    public double getPredictionConfidence() { return predictionConfidence; }
    public int getTrendStrength() { return trendStrength; }
}
