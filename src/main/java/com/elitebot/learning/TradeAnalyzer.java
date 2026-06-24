package com.elitebot.learning;

import com.elitebot.model.Trade;
import com.elitebot.model.TradingSignal;
import java.util.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * SMART TRADE ANALYZER
 * 
 * Analyzes losing trades to find patterns and mistakes.
 * Called during "analysis breaks" after consecutive losses.
 * 
 * Learns:
 * - Which currencies are losing
 * - Which time periods are bad
 * - Which confidence levels are unreliable
 * - Pattern mistakes (e.g., trading against trend)
 */
public class TradeAnalyzer {
    
    // Recent trades for analysis
    private final List<TradeRecord> recentTrades = new ArrayList<>();
    private static final int MAX_HISTORY = 100;
    
    // Learned insights
    private final Set<String> blacklistedCurrencies = new HashSet<>();
    private final Set<Integer> badHours = new HashSet<>();  // Hours to avoid
    private double learnedMinConfidence = 75.0;
    private final Map<String, Double> currencyConfidenceBoosts = new HashMap<>();
    
    // Analysis results
    private String lastAnalysisReport = "";
    private List<String> learnedMistakes = new ArrayList<>();
    
    public TradeAnalyzer() {
        // Pre-blacklist crypto based on historical data
        blacklistedCurrencies.add("BTC/USD");
        blacklistedCurrencies.add("ETH/USD");
    }
    
    /**
     * Record a trade for analysis
     */
    public void recordTrade(Trade trade) {
        if (trade == null || trade.isPending()) return;
        
        TradeRecord record = new TradeRecord(
            trade.getCurrency(),
            trade.getDirection(),
            trade.getConfidence(),
            trade.isWon(),
            LocalDateTime.now(),
            trade.getDurationMinutes()
        );
        
        recentTrades.add(record);
        if (recentTrades.size() > MAX_HISTORY) {
            recentTrades.remove(0);
        }
    }
    
    /**
     * SMART ANALYSIS - Called during break after consecutive losses
     * Analyzes last N losing trades and learns from mistakes
     */
    public AnalysisResult analyzeRecentLosses(int numLosses) {
        List<TradeRecord> losses = new ArrayList<>();
        
        // Get the recent losing trades
        for (int i = recentTrades.size() - 1; i >= 0 && losses.size() < numLosses; i--) {
            TradeRecord rec = recentTrades.get(i);
            if (!rec.won) {
                losses.add(rec);
            }
        }
        
        if (losses.isEmpty()) {
            return new AnalysisResult("No recent losses to analyze", new ArrayList<>());
        }
        
        StringBuilder report = new StringBuilder();
        List<String> insights = new ArrayList<>();
        
        report.append("🔍 SMART LOSS ANALYSIS\n");
        report.append("═══════════════════════════\n");
        report.append("Analyzing last ").append(losses.size()).append(" losses...\n\n");
        
        // 1. CURRENCY ANALYSIS
        Map<String, Integer> currencyLosses = new HashMap<>();
        for (TradeRecord loss : losses) {
            currencyLosses.merge(loss.currency, 1, Integer::sum);
        }
        
        for (Map.Entry<String, Integer> entry : currencyLosses.entrySet()) {
            if (entry.getValue() >= 3) {
                String currency = entry.getKey();
                report.append("❌ ").append(currency).append(" caused ")
                      .append(entry.getValue()).append(" losses\n");
                
                // Temporarily blacklist this currency
                blacklistedCurrencies.add(currency);
                insights.add("Blacklisted " + currency + " (too many losses)");
            }
        }
        
        // 2. CONFIDENCE ANALYSIS
        double avgLossConfidence = losses.stream()
            .mapToDouble(t -> t.confidence)
            .average()
            .orElse(70);
        
        if (avgLossConfidence < learnedMinConfidence + 10) {
            // Losses happening at current confidence level - need to raise it
            learnedMinConfidence = Math.min(90, learnedMinConfidence + 5);
            report.append("📊 Avg loss confidence: ").append(String.format("%.0f", avgLossConfidence)).append("%\n");
            report.append("   → Raising min confidence to ").append(String.format("%.0f", learnedMinConfidence)).append("%\n");
            insights.add("Raised minimum confidence to " + (int)learnedMinConfidence + "%");
        }
        
        // 3. TIME ANALYSIS
        Map<Integer, Integer> hourLosses = new HashMap<>();
        for (TradeRecord loss : losses) {
            int hour = loss.timestamp.getHour();
            hourLosses.merge(hour, 1, Integer::sum);
        }
        
        for (Map.Entry<Integer, Integer> entry : hourLosses.entrySet()) {
            if (entry.getValue() >= 2) {
                int hour = entry.getKey();
                badHours.add(hour);
                report.append("⏰ Hour ").append(hour).append(":00 had ")
                      .append(entry.getValue()).append(" losses\n");
                insights.add("Avoiding hour " + hour + ":00");
            }
        }
        
        // 4. DIRECTION ANALYSIS
        int callLosses = 0, putLosses = 0;
        for (TradeRecord loss : losses) {
            if (loss.direction == TradingSignal.SignalType.CALL) callLosses++;
            else if (loss.direction == TradingSignal.SignalType.PUT) putLosses++;
        }
        
        if (callLosses > putLosses * 2) {
            report.append("📉 Too many CALL losses - market trending DOWN\n");
            insights.add("Prefer PUT signals (market bearish)");
            currencyConfidenceBoosts.put("PUT_BOOST", 10.0);
        } else if (putLosses > callLosses * 2) {
            report.append("📈 Too many PUT losses - market trending UP\n");
            insights.add("Prefer CALL signals (market bullish)");
            currencyConfidenceBoosts.put("CALL_BOOST", 10.0);
        }
        
        // 5. DURATION ANALYSIS
        Map<Integer, Integer> durationLosses = new HashMap<>();
        for (TradeRecord loss : losses) {
            durationLosses.merge(loss.duration, 1, Integer::sum);
        }
        
        int worstDuration = durationLosses.entrySet().stream()
            .max(Comparator.comparingInt(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .orElse(1);
        
        if (durationLosses.getOrDefault(worstDuration, 0) >= 3) {
            report.append("⏱️ ").append(worstDuration).append("min trades failing often\n");
            insights.add("Avoid " + worstDuration + " minute trades");
        }
        
        report.append("\n═══════════════════════════\n");
        report.append("✅ Learned ").append(insights.size()).append(" lessons\n");
        report.append("🔄 Will apply fixes and resume trading\n");
        
        this.lastAnalysisReport = report.toString();
        this.learnedMistakes = insights;
        
        return new AnalysisResult(report.toString(), insights);
    }
    
    /**
     * Check if a currency is blacklisted
     */
    public boolean isCurrencyBlacklisted(String currency) {
        return blacklistedCurrencies.contains(currency);
    }
    
    /**
     * Check if current hour is bad for trading
     */
    public boolean isBadHour() {
        int currentHour = LocalDateTime.now().getHour();
        return badHours.contains(currentHour);
    }
    
    /**
     * Get learned minimum confidence
     */
    public double getLearnedMinConfidence() {
        return learnedMinConfidence;
    }
    
    /**
     * Get confidence boost for direction
     */
    public double getDirectionBoost(TradingSignal.SignalType direction) {
        if (direction == TradingSignal.SignalType.CALL) {
            return currencyConfidenceBoosts.getOrDefault("CALL_BOOST", 0.0);
        } else if (direction == TradingSignal.SignalType.PUT) {
            return currencyConfidenceBoosts.getOrDefault("PUT_BOOST", 0.0);
        }
        return 0;
    }
    
    /**
     * Clear a currency from blacklist (after some wins)
     */
    public void rehabilitateCurrency(String currency) {
        // Don't rehabilitate crypto - they're permanently bad
        if (currency.contains("BTC") || currency.contains("ETH")) return;
        blacklistedCurrencies.remove(currency);
    }
    
    /**
     * Get last analysis report for UI
     */
    public String getLastAnalysisReport() {
        return lastAnalysisReport;
    }
    
    /**
     * Get learned mistakes list
     */
    public List<String> getLearnedMistakes() {
        return new ArrayList<>(learnedMistakes);
    }
    
    /**
     * Get blacklisted currencies
     */
    public Set<String> getBlacklistedCurrencies() {
        return new HashSet<>(blacklistedCurrencies);
    }
    
    /**
     * Reset learned lessons (fresh start)
     */
    public void reset() {
        blacklistedCurrencies.clear();
        blacklistedCurrencies.add("BTC/USD");  // Keep crypto blacklisted
        blacklistedCurrencies.add("ETH/USD");
        badHours.clear();
        learnedMinConfidence = 75.0;
        currencyConfidenceBoosts.clear();
        learnedMistakes.clear();
    }
    
    // ==================== INNER CLASSES ====================
    
    public static class TradeRecord {
        final String currency;
        final TradingSignal.SignalType direction;
        final double confidence;
        final boolean won;
        final LocalDateTime timestamp;
        final int duration;
        
        TradeRecord(String currency, TradingSignal.SignalType direction, 
                    double confidence, boolean won, LocalDateTime timestamp, int duration) {
            this.currency = currency;
            this.direction = direction;
            this.confidence = confidence;
            this.won = won;
            this.timestamp = timestamp;
            this.duration = duration;
        }
    }
    
    public static class AnalysisResult {
        public final String report;
        public final List<String> insights;
        
        AnalysisResult(String report, List<String> insights) {
            this.report = report;
            this.insights = insights;
        }
    }
}
