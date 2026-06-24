package com.elitebot.intelligence;

import java.util.*;
import java.io.*;
import java.time.*;

/**
 * Market Intelligence System
 * 
 * Makes the bot smarter by:
 * 1. Analyzing market conditions (good/bad for trading)
 * 2. Learning best times to trade
 * 3. Detecting volatile/calm periods
 * 4. Recommending when to trade more or less
 * 
 * This is what separates professional traders from amateurs!
 */
public class MarketIntelligence {
    
    private static final String DATA_FILE = "market_intelligence.dat";
    
    // Market condition scores (0-100, higher = better for trading)
    public enum MarketState {
        EXCELLENT,    // 80-100: Trade aggressively
        GOOD,         // 60-79: Trade normally
        NEUTRAL,      // 40-59: Trade carefully
        POOR,         // 20-39: Minimal trading
        DANGEROUS     // 0-19: Stop trading
    }
    
    // Time-based learning
    private Map<Integer, HourStats> hourlyPerformance = new HashMap<>();
    private Map<DayOfWeek, DayStats> dailyPerformance = new HashMap<>();
    
    // Volatility tracking
    private List<Double> recentVolatility = new ArrayList<>();
    private double averageVolatility = 0;
    private double currentVolatility = 0;
    
    // Win streak tracking for market state
    private int recentWins = 0;
    private int recentLosses = 0;
    private static final int RECENT_WINDOW = 10;
    private List<Boolean> recentResults = new ArrayList<>();
    
    // Market state
    private MarketState currentState = MarketState.NEUTRAL;
    private int marketScore = 50;
    
    // Trading intensity recommendation (0.0 - 2.0)
    // < 1.0 = trade less, 1.0 = normal, > 1.0 = trade more
    private double tradingIntensity = 1.0;
    
    public MarketIntelligence() {
        initializeTimeStats();
        loadData();
    }
    
    private void initializeTimeStats() {
        // Initialize hourly stats (0-23)
        for (int i = 0; i < 24; i++) {
            hourlyPerformance.put(i, new HourStats(i));
        }
        
        // Initialize daily stats
        for (DayOfWeek day : DayOfWeek.values()) {
            dailyPerformance.put(day, new DayStats(day));
        }
    }
    
    /**
     * Analyze current market conditions
     */
    public void analyzeMarket(List<Double> prices, String currency) {
        if (prices == null || prices.size() < 20) return;
        
        // Calculate current volatility
        currentVolatility = calculateVolatility(prices);
        recentVolatility.add(currentVolatility);
        if (recentVolatility.size() > 50) {
            recentVolatility.remove(0);
        }
        averageVolatility = recentVolatility.stream().mapToDouble(d -> d).average().orElse(0);
        
        // Calculate market score based on multiple factors
        int volatilityScore = getVolatilityScore();
        int trendScore = getTrendScore(prices);
        int timeScore = getTimeScore();
        int performanceScore = getPerformanceScore();
        
        // Weighted average
        marketScore = (int)(
            volatilityScore * 0.25 +
            trendScore * 0.25 +
            timeScore * 0.25 +
            performanceScore * 0.25
        );
        
        // Determine market state
        if (marketScore >= 80) currentState = MarketState.EXCELLENT;
        else if (marketScore >= 60) currentState = MarketState.GOOD;
        else if (marketScore >= 40) currentState = MarketState.NEUTRAL;
        else if (marketScore >= 20) currentState = MarketState.POOR;
        else currentState = MarketState.DANGEROUS;
        
        // Calculate trading intensity
        tradingIntensity = calculateTradingIntensity();
    }
    
    /**
     * Get volatility score (too high or too low = bad)
     */
    private int getVolatilityScore() {
        if (averageVolatility == 0) return 50;
        
        double ratio = currentVolatility / averageVolatility;
        
        // Ideal volatility is around 0.8-1.2 of average
        if (ratio >= 0.8 && ratio <= 1.2) return 90;
        if (ratio >= 0.6 && ratio <= 1.5) return 70;
        if (ratio >= 0.4 && ratio <= 2.0) return 50;
        if (ratio >= 0.2 && ratio <= 3.0) return 30;
        return 10;  // Too extreme
    }
    
    /**
     * Get trend score (clear trend = good)
     */
    private int getTrendScore(List<Double> prices) {
        if (prices.size() < 10) return 50;
        
        // Calculate trend strength
        double first = prices.subList(0, 5).stream().mapToDouble(d -> d).average().orElse(0);
        double last = prices.subList(prices.size() - 5, prices.size()).stream().mapToDouble(d -> d).average().orElse(0);
        double middle = prices.subList(prices.size() / 2 - 2, prices.size() / 2 + 3).stream().mapToDouble(d -> d).average().orElse(0);
        
        // Check for consistent trend
        boolean uptrend = first < middle && middle < last;
        boolean downtrend = first > middle && middle > last;
        
        if (uptrend || downtrend) {
            // Clear trend - calculate strength
            double trendStrength = Math.abs(last - first) / first * 100;
            if (trendStrength > 0.5) return 90;
            if (trendStrength > 0.2) return 75;
            return 60;
        }
        
        // Ranging/choppy market
        return 40;
    }
    
    /**
     * Get time score based on historical performance
     */
    private int getTimeScore() {
        int hour = LocalTime.now().getHour();
        DayOfWeek day = LocalDate.now().getDayOfWeek();
        
        HourStats hourStats = hourlyPerformance.get(hour);
        DayStats dayStats = dailyPerformance.get(day);
        
        double hourWinRate = hourStats.getWinRate();
        double dayWinRate = dayStats.getWinRate();
        
        // If not enough data, return neutral
        if (hourStats.getTotalTrades() < 5 && dayStats.getTotalTrades() < 10) {
            return 50;
        }
        
        double avgWinRate = (hourWinRate * 0.6 + dayWinRate * 0.4);
        return (int)(avgWinRate * 100);
    }
    
    /**
     * Get performance score based on recent trades
     */
    private int getPerformanceScore() {
        if (recentResults.isEmpty()) return 50;
        
        long wins = recentResults.stream().filter(b -> b).count();
        double winRate = (double) wins / recentResults.size();
        
        return (int)(winRate * 100);
    }
    
    /**
     * Calculate how intensely we should trade
     */
    private double calculateTradingIntensity() {
        switch (currentState) {
            case EXCELLENT: return 1.5;   // 50% more trades
            case GOOD: return 1.2;        // 20% more trades
            case NEUTRAL: return 1.0;     // Normal
            case POOR: return 0.5;        // Half trades
            case DANGEROUS: return 0.0;   // Stop trading!
            default: return 1.0;
        }
    }
    
    private double calculateVolatility(List<Double> prices) {
        if (prices.size() < 10) return 0;
        List<Double> recent = prices.subList(prices.size() - 10, prices.size());
        double mean = recent.stream().mapToDouble(d -> d).average().orElse(0);
        double variance = recent.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0);
        return Math.sqrt(variance);
    }
    
    /**
     * Record a trade result for learning
     */
    public void recordTrade(boolean won) {
        // Update recent results
        recentResults.add(won);
        if (recentResults.size() > RECENT_WINDOW) {
            recentResults.remove(0);
        }
        
        if (won) recentWins++;
        else recentLosses++;
        
        // Update time-based stats
        int hour = LocalTime.now().getHour();
        DayOfWeek day = LocalDate.now().getDayOfWeek();
        
        hourlyPerformance.get(hour).recordTrade(won);
        dailyPerformance.get(day).recordTrade(won);
        
        // Auto-save periodically
        if ((recentWins + recentLosses) % 20 == 0) {
            saveData();
        }
    }
    
    /**
     * Should we trade right now?
     */
    public boolean shouldTrade() {
        return currentState != MarketState.DANGEROUS && tradingIntensity > 0;
    }
    
    /**
     * Get confidence adjustment based on market conditions
     */
    public double getConfidenceAdjustment() {
        switch (currentState) {
            case EXCELLENT: return 5;   // Lower required confidence
            case GOOD: return 2;
            case NEUTRAL: return 0;
            case POOR: return -5;       // Higher required confidence
            case DANGEROUS: return -15;
            default: return 0;
        }
    }
    
    /**
     * Get best trading hours based on learning
     */
    public String getBestTradingHours() {
        return hourlyPerformance.values().stream()
            .filter(h -> h.getTotalTrades() >= 5)
            .sorted((a, b) -> Double.compare(b.getWinRate(), a.getWinRate()))
            .limit(3)
            .map(h -> String.format("%02d:00 (%.0f%%)", h.getHour(), h.getWinRate() * 100))
            .reduce((a, b) -> a + ", " + b)
            .orElse("Learning...");
    }
    
    /**
     * Get status message for UI
     */
    public String getStatusMessage() {
        String stateEmoji = "";
        switch (currentState) {
            case EXCELLENT: stateEmoji = "🟢"; break;
            case GOOD: stateEmoji = "🔵"; break;
            case NEUTRAL: stateEmoji = "🟡"; break;
            case POOR: stateEmoji = "🟠"; break;
            case DANGEROUS: stateEmoji = "🔴"; break;
        }
        
        return String.format("%s Market: %s (%d%%) | Intensity: %.0f%%",
            stateEmoji, currentState, marketScore, tradingIntensity * 100);
    }
    
    // Getters
    public MarketState getCurrentState() { return currentState; }
    public int getMarketScore() { return marketScore; }
    public double getTradingIntensity() { return tradingIntensity; }
    public double getCurrentVolatility() { return currentVolatility; }
    public double getAverageVolatility() { return averageVolatility; }
    
    // ==================== PERSISTENCE ====================
    
    public void saveData() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(DATA_FILE))) {
            writer.println("# Market Intelligence Data - " + LocalDate.now());
            
            // Save hourly stats
            for (HourStats stats : hourlyPerformance.values()) {
                writer.println("hour=" + stats.getHour() + "," + stats.getTotalTrades() + "," + stats.getWins());
            }
            
            // Save daily stats
            for (DayStats stats : dailyPerformance.values()) {
                writer.println("day=" + stats.getDay().name() + "," + stats.getTotalTrades() + "," + stats.getWins());
            }
            
            System.out.println("📊 Market intelligence saved");
        } catch (IOException e) {
            System.err.println("Failed to save market intelligence: " + e.getMessage());
        }
    }
    
    public void loadData() {
        File file = new File(DATA_FILE);
        if (!file.exists()) return;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) continue;
                String[] parts = line.split("=");
                if (parts.length != 2) continue;
                
                String[] values = parts[1].split(",");
                
                if (parts[0].equals("hour") && values.length == 3) {
                    int hour = Integer.parseInt(values[0]);
                    int total = Integer.parseInt(values[1]);
                    int wins = Integer.parseInt(values[2]);
                    hourlyPerformance.get(hour).setStats(total, wins);
                }
                
                if (parts[0].equals("day") && values.length == 3) {
                    DayOfWeek day = DayOfWeek.valueOf(values[0]);
                    int total = Integer.parseInt(values[1]);
                    int wins = Integer.parseInt(values[2]);
                    dailyPerformance.get(day).setStats(total, wins);
                }
            }
            System.out.println("🧠 Market intelligence loaded");
        } catch (Exception e) {
            System.err.println("Failed to load market intelligence: " + e.getMessage());
        }
    }
    
    // ==================== INNER CLASSES ====================
    
    private static class HourStats {
        private final int hour;
        private int totalTrades = 0;
        private int wins = 0;
        
        HourStats(int hour) { this.hour = hour; }
        
        void recordTrade(boolean won) {
            totalTrades++;
            if (won) wins++;
        }
        
        void setStats(int total, int w) {
            this.totalTrades = total;
            this.wins = w;
        }
        
        int getHour() { return hour; }
        int getTotalTrades() { return totalTrades; }
        int getWins() { return wins; }
        double getWinRate() {
            return totalTrades > 0 ? (double) wins / totalTrades : 0.5;
        }
    }
    
    private static class DayStats {
        private final DayOfWeek day;
        private int totalTrades = 0;
        private int wins = 0;
        
        DayStats(DayOfWeek day) { this.day = day; }
        
        void recordTrade(boolean won) {
            totalTrades++;
            if (won) wins++;
        }
        
        void setStats(int total, int w) {
            this.totalTrades = total;
            this.wins = w;
        }
        
        DayOfWeek getDay() { return day; }
        int getTotalTrades() { return totalTrades; }
        int getWins() { return wins; }
        double getWinRate() {
            return totalTrades > 0 ? (double) wins / totalTrades : 0.5;
        }
    }
}
