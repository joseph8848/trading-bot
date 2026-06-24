package com.elitebot.learning;

import com.elitebot.model.Trade;
import com.elitebot.model.TradingSignal;

import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptive Learning System for the Trading Bot.
 * Learns from past trades to improve future predictions.
 * 
 * Features:
 * - Tracks win/loss rates by signal type and confidence level
 * - Adjusts minimum confidence thresholds based on performance
 * - Learns which currencies perform better at different times
 * - Persists learned data to file for continuous improvement
 */
public class LearningEngine {
    
    private static final String DATA_FILE = "bot_learning_data.json";
    
    // Learning data structures
    private final Map<String, CurrencyStats> currencyPerformance = new ConcurrentHashMap<>();
    private final Map<Integer, ConfidenceBucket> confidenceBuckets = new ConcurrentHashMap<>();
    
    // Adaptive thresholds (learned over time) - RAISED for v2.0
    private double minConfidenceThreshold = 80.0;  // Raised from 60!
    private double optimalConfidenceThreshold = 85.0;  // Raised from 70!
    
    // Session stats
    private int sessionTrades = 0;
    private int sessionWins = 0;
    private int totalHistoricalTrades = 0;
    private int totalHistoricalWins = 0;
    
    // Pattern memory
    private final List<TradePattern> recentPatterns = new ArrayList<>();
    private static final int MAX_PATTERNS = 1000;
    
    public LearningEngine() {
        initializeConfidenceBuckets();
        loadLearningData();
    }
    
    private void initializeConfidenceBuckets() {
        // Buckets: 50-60, 60-70, 70-80, 80-90, 90-100
        for (int i = 50; i <= 90; i += 10) {
            confidenceBuckets.put(i, new ConfidenceBucket(i, i + 10));
        }
    }
    
    /**
     * Learn from a completed trade
     */
    public void learnFromTrade(Trade trade) {
        if (trade == null || trade.isPending()) return;
        
        sessionTrades++;
        totalHistoricalTrades++;
        
        boolean won = trade.isWon();
        if (won) {
            sessionWins++;
            totalHistoricalWins++;
        }
        
        // Update currency performance
        String currency = trade.getCurrency();
        currencyPerformance.computeIfAbsent(currency, k -> new CurrencyStats(currency));
        currencyPerformance.get(currency).recordTrade(won, trade.getConfidence());
        
        // Update confidence bucket
        int bucketKey = ((int) trade.getConfidence() / 10) * 10;
        bucketKey = Math.max(50, Math.min(90, bucketKey));
        ConfidenceBucket bucket = confidenceBuckets.get(bucketKey);
        if (bucket != null) {
            bucket.recordTrade(won);
        }
        
        // Store pattern for learning
        TradePattern pattern = new TradePattern(
            trade.getCurrency(),
            trade.getDirection(),
            trade.getConfidence(),
            trade.getDurationMinutes(),
            won
        );
        addPattern(pattern);
        
        // Adapt thresholds based on learning
        adaptThresholds();
        
        // Periodically save learning data
        if (sessionTrades % 10 == 0) {
            saveLearningData();
        }
    }
    
    /**
     * Adapt confidence thresholds based on learned performance
     */
    private void adaptThresholds() {
        if (totalHistoricalTrades < 20) return;  // Need minimum data
        
        // Find the confidence bucket with best win rate
        double bestWinRate = 0;
        int bestBucket = 70;
        
        for (Map.Entry<Integer, ConfidenceBucket> entry : confidenceBuckets.entrySet()) {
            ConfidenceBucket bucket = entry.getValue();
            if (bucket.getTotalTrades() >= 5) {  // Minimum sample size
                double winRate = bucket.getWinRate();
                if (winRate > bestWinRate) {
                    bestWinRate = winRate;
                    bestBucket = entry.getKey();
                }
            }
        }
        
        // Adjust optimal threshold (slowly move toward best bucket)
        optimalConfidenceThreshold = optimalConfidenceThreshold * 0.9 + bestBucket * 0.1;
        
        // Adjust minimum threshold based on overall performance
        double overallWinRate = getOverallWinRate();
        if (overallWinRate < 0.5 && minConfidenceThreshold < 80) {
            // Losing more than winning, be more selective
            minConfidenceThreshold += 1;
        } else if (overallWinRate > 0.65 && minConfidenceThreshold > 55) {
            // Winning consistently, can be slightly less selective
            minConfidenceThreshold -= 0.5;
        }
    }
    
    /**
     * Get adjusted signal confidence based on learned patterns
     */
    public double getAdjustedConfidence(String currency, TradingSignal.SignalType direction, double rawConfidence) {
        CurrencyStats stats = currencyPerformance.get(currency);
        if (stats == null || stats.getTotalTrades() < 10) {
            return rawConfidence;  // Not enough data to adjust
        }
        
        // Adjust based on currency's historical performance
        double currencyMultiplier = stats.getWinRate() / 0.5;  // 1.0 = average, >1 = good performer
        currencyMultiplier = Math.max(0.8, Math.min(1.2, currencyMultiplier));  // Clamp
        
        return rawConfidence * currencyMultiplier;
    }
    
    /**
     * Check if we should take this trade based on learned patterns
     * ADAPTIVE: Prioritizes RECENT performance, filters out old data
     */
    public boolean shouldTrade(String currency, double confidence) {
        // Always respect minimum threshold
        if (confidence < minConfidenceThreshold) {
            return false;
        }
        
        // FILTER: Only use RECENT patterns (last 50 trades)
        int recentWins = 0;
        int recentTrades = 0;
        int currencyRecentWins = 0;
        int currencyRecentTrades = 0;
        
        // Copy to avoid ConcurrentModificationException
        List<TradePattern> patternsCopy = new ArrayList<>(recentPatterns);
        int lookback = Math.min(50, patternsCopy.size());
        for (int i = patternsCopy.size() - lookback; i < patternsCopy.size(); i++) {
            TradePattern p = patternsCopy.get(i);
            recentTrades++;
            if (p.won) recentWins++;
            
            if (p.currency.equals(currency)) {
                currencyRecentTrades++;
                if (p.won) currencyRecentWins++;
            }
        }
        
        // If recent performance is poor, be more cautious
        if (recentTrades >= 10) {
            double recentWinRate = (double) recentWins / recentTrades;
            if (recentWinRate < 0.4 && confidence < 80) {
                return false;
            }
        }
        
        // If this currency is performing poorly RECENTLY, avoid it
        if (currencyRecentTrades >= 5) {
            double currencyWinRate = (double) currencyRecentWins / currencyRecentTrades;
            if (currencyWinRate < 0.35 && confidence < 85) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get the best currency to trade based on learned performance
     */
    public String getBestPerformingCurrency() {
        return currencyPerformance.values().stream()
            .filter(s -> s.getTotalTrades() >= 10)
            .max(Comparator.comparingDouble(CurrencyStats::getWinRate))
            .map(CurrencyStats::getCurrency)
            .orElse(null);
    }
    
    /**
     * Get learning insights for display
     */
    public String getLearningInsights() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📊 Learned from %d trades (%.1f%% win rate)\n", 
            totalHistoricalTrades, getOverallWinRate() * 100));
        sb.append(String.format("🎯 Optimal confidence: %.0f%%+\n", optimalConfidenceThreshold));
        
        // Best/worst currency
        String best = getBestPerformingCurrency();
        if (best != null) {
            CurrencyStats stats = currencyPerformance.get(best);
            sb.append(String.format("⭐ Best: %s (%.1f%% win rate)\n", best, stats.getWinRate() * 100));
        }
        
        return sb.toString();
    }
    
    private void addPattern(TradePattern pattern) {
        recentPatterns.add(pattern);
        if (recentPatterns.size() > MAX_PATTERNS) {
            recentPatterns.remove(0);
        }
    }
    
    public double getOverallWinRate() {
        return totalHistoricalTrades > 0 ? (double) totalHistoricalWins / totalHistoricalTrades : 0.5;
    }
    
    public double getSessionWinRate() {
        return sessionTrades > 0 ? (double) sessionWins / sessionTrades : 0;
    }
    
    public double getMinConfidenceThreshold() {
        return minConfidenceThreshold;
    }
    
    public double getOptimalConfidenceThreshold() {
        return optimalConfidenceThreshold;
    }
    
    public int getTotalHistoricalTrades() {
        return totalHistoricalTrades;
    }
    
    // ==================== PERSISTENCE ====================
    
    public void saveLearningData() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(DATA_FILE))) {
            writer.println("# Elite Bot Learning Data - " + LocalDate.now());
            writer.println("totalTrades=" + totalHistoricalTrades);
            writer.println("totalWins=" + totalHistoricalWins);
            writer.println("minConfidence=" + minConfidenceThreshold);
            writer.println("optimalConfidence=" + optimalConfidenceThreshold);
            
            // Save currency stats
            for (CurrencyStats stats : currencyPerformance.values()) {
                writer.println("currency=" + stats.getCurrency() + "," + 
                    stats.getTotalTrades() + "," + stats.getWins());
            }
            
            // Save confidence buckets
            for (ConfidenceBucket bucket : confidenceBuckets.values()) {
                writer.println("bucket=" + bucket.getMinConfidence() + "," + 
                    bucket.getTotalTrades() + "," + bucket.getWins());
            }
            
            System.out.println("💾 Learning data saved (" + totalHistoricalTrades + " trades)");
        } catch (IOException e) {
            System.err.println("Failed to save learning data: " + e.getMessage());
        }
    }
    
    public void loadLearningData() {
        File file = new File(DATA_FILE);
        if (!file.exists()) {
            System.out.println("🆕 Starting fresh learning session");
            return;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) continue;
                
                String[] parts = line.split("=");
                if (parts.length != 2) continue;
                
                switch (parts[0]) {
                    case "totalTrades":
                        totalHistoricalTrades = Integer.parseInt(parts[1]);
                        break;
                    case "totalWins":
                        totalHistoricalWins = Integer.parseInt(parts[1]);
                        break;
                    case "minConfidence":
                        minConfidenceThreshold = Double.parseDouble(parts[1]);
                        break;
                    case "optimalConfidence":
                        optimalConfidenceThreshold = Double.parseDouble(parts[1]);
                        break;
                    case "currency":
                        String[] cparts = parts[1].split(",");
                        if (cparts.length == 3) {
                            CurrencyStats stats = new CurrencyStats(cparts[0]);
                            stats.setStats(Integer.parseInt(cparts[1]), Integer.parseInt(cparts[2]));
                            currencyPerformance.put(cparts[0], stats);
                        }
                        break;
                    case "bucket":
                        String[] bparts = parts[1].split(",");
                        if (bparts.length == 3) {
                            int key = Integer.parseInt(bparts[0]);
                            ConfidenceBucket bucket = confidenceBuckets.get(key);
                            if (bucket != null) {
                                bucket.setStats(Integer.parseInt(bparts[1]), Integer.parseInt(bparts[2]));
                            }
                        }
                        break;
                }
            }
            System.out.println("🧠 Loaded learning data: " + totalHistoricalTrades + " historical trades");
        } catch (IOException e) {
            System.err.println("Failed to load learning data: " + e.getMessage());
        }
    }
    
    // ==================== INNER CLASSES ====================
    
    private static class CurrencyStats {
        private final String currency;
        private int totalTrades = 0;
        private int wins = 0;
        private double avgWinConfidence = 0;
        private double avgLossConfidence = 0;
        
        CurrencyStats(String currency) {
            this.currency = currency;
        }
        
        void recordTrade(boolean won, double confidence) {
            totalTrades++;
            if (won) {
                wins++;
                avgWinConfidence = (avgWinConfidence * (wins - 1) + confidence) / wins;
            } else {
                int losses = totalTrades - wins;
                avgLossConfidence = (avgLossConfidence * (losses - 1) + confidence) / losses;
            }
        }
        
        void setStats(int total, int w) {
            this.totalTrades = total;
            this.wins = w;
        }
        
        String getCurrency() { return currency; }
        int getTotalTrades() { return totalTrades; }
        int getWins() { return wins; }
        double getWinRate() { return totalTrades > 0 ? (double) wins / totalTrades : 0.5; }
    }
    
    private static class ConfidenceBucket {
        private final int minConfidence;
        private final int maxConfidence;
        private int totalTrades = 0;
        private int wins = 0;
        
        ConfidenceBucket(int min, int max) {
            this.minConfidence = min;
            this.maxConfidence = max;
        }
        
        void recordTrade(boolean won) {
            totalTrades++;
            if (won) wins++;
        }
        
        void setStats(int total, int w) {
            this.totalTrades = total;
            this.wins = w;
        }
        
        int getMinConfidence() { return minConfidence; }
        int getTotalTrades() { return totalTrades; }
        int getWins() { return wins; }
        double getWinRate() { return totalTrades > 0 ? (double) wins / totalTrades : 0.5; }
    }
    
    private static class TradePattern {
        final String currency;
        final TradingSignal.SignalType direction;
        final double confidence;
        final int duration;
        final boolean won;
        
        TradePattern(String currency, TradingSignal.SignalType direction, 
                     double confidence, int duration, boolean won) {
            this.currency = currency;
            this.direction = direction;
            this.confidence = confidence;
            this.duration = duration;
            this.won = won;
        }
    }
}
