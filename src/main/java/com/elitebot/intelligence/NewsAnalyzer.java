package com.elitebot.intelligence;

import java.net.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/**
 * News Analyzer for Market Awareness
 * 
 * Checks market news to understand current trading environment.
 * Major news events can cause high volatility - good or bad for trading.
 * 
 * News impact levels:
 * - HIGH: Major economic data, central bank decisions, geopolitical events
 * - MEDIUM: Employment data, trade balances, retail sales
 * - LOW: Minor economic indicators
 */
public class NewsAnalyzer {
    
    // News impact on trading
    public enum NewsImpact {
        POSITIVE,    // Good for trading (clear direction expected)
        NEGATIVE,    // Bad for trading (high uncertainty)
        NEUTRAL,     // Normal conditions
        UNKNOWN
    }
    
    // Keywords that indicate high-impact news
    private static final String[] HIGH_IMPACT_KEYWORDS = {
        "fed", "interest rate", "rate decision", "inflation", "cpi",
        "gdp", "employment", "nonfarm", "central bank", "ecb", "boj",
        "recession", "crisis", "crash", "surge", "plunge"
    };
    
    // Keywords that suggest positive trading conditions
    private static final String[] POSITIVE_KEYWORDS = {
        "rally", "surge", "gains", "bullish", "optimism", "growth",
        "strong", "rising", "up", "higher", "positive"
    };
    
    // Keywords that suggest caution
    private static final String[] NEGATIVE_KEYWORDS = {
        "crash", "plunge", "crisis", "fear", "uncertainty", "volatile",
        "risk", "warning", "decline", "bearish", "weak", "lower"
    };
    
    // Cache news to avoid too many requests
    private String cachedNews = "";
    private LocalDateTime lastNewsCheck = LocalDateTime.MIN;
    private static final int NEWS_CACHE_MINUTES = 30;
    
    private NewsImpact currentImpact = NewsImpact.NEUTRAL;
    private int newsScore = 50;  // 0-100, higher = better for trading
    private boolean highImpactNewsActive = false;
    
    /**
     * Check if there's high-impact news that might affect trading
     */
    public void analyzeNews() {
        // Check cache first
        if (Duration.between(lastNewsCheck, LocalDateTime.now()).toMinutes() < NEWS_CACHE_MINUTES) {
            return;  // Use cached analysis
        }
        
        // Try to fetch news
        String news = fetchMarketNews();
        if (news.isEmpty()) {
            // Couldn't fetch news - assume neutral
            currentImpact = NewsImpact.NEUTRAL;
            newsScore = 50;
            return;
        }
        
        cachedNews = news.toLowerCase();
        lastNewsCheck = LocalDateTime.now();
        
        // Analyze the news
        analyzeContent(cachedNews);
    }
    
    /**
     * Fetch market news from the internet
     */
    private String fetchMarketNews() {
        // In a real implementation, this would call a news API
        // For now, we simulate based on time patterns
        
        // Check time-based market events
        int hour = LocalTime.now().getHour();
        DayOfWeek day = LocalDate.now().getDayOfWeek();
        
        StringBuilder newsSimulation = new StringBuilder();
        
        // Major market hours (high activity)
        if (hour >= 8 && hour <= 9 && day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
            newsSimulation.append("european markets opening strong trading activity ");
        }
        if (hour >= 13 && hour <= 14 && day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
            newsSimulation.append("us markets opening high volume expected ");
        }
        
        // Weekend = less liquidity
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            newsSimulation.append("weekend trading lower liquidity caution advised ");
        }
        
        // First Friday of month = NFP (major news)
        if (day == DayOfWeek.FRIDAY && LocalDate.now().getDayOfMonth() <= 7) {
            newsSimulation.append("nonfarm payrolls expected high volatility employment data ");
        }
        
        // End of month = rebalancing
        if (LocalDate.now().getDayOfMonth() >= 28) {
            newsSimulation.append("month end rebalancing institutional flows ");
        }
        
        return newsSimulation.toString();
    }
    
    /**
     * Analyze news content for trading signals
     */
    private void analyzeContent(String content) {
        int positiveScore = 0;
        int negativeScore = 0;
        highImpactNewsActive = false;
        
        // Check for high-impact keywords
        for (String keyword : HIGH_IMPACT_KEYWORDS) {
            if (content.contains(keyword)) {
                highImpactNewsActive = true;
                break;
            }
        }
        
        // Count positive keywords
        for (String keyword : POSITIVE_KEYWORDS) {
            if (content.contains(keyword)) {
                positiveScore += 10;
            }
        }
        
        // Count negative keywords
        for (String keyword : NEGATIVE_KEYWORDS) {
            if (content.contains(keyword)) {
                negativeScore += 10;
            }
        }
        
        // Calculate overall score
        int difference = positiveScore - negativeScore;
        newsScore = Math.max(0, Math.min(100, 50 + difference));
        
        // Determine impact
        if (negativeScore > 30 || highImpactNewsActive) {
            currentImpact = NewsImpact.NEGATIVE;
        } else if (positiveScore > 30) {
            currentImpact = NewsImpact.POSITIVE;
        } else {
            currentImpact = NewsImpact.NEUTRAL;
        }
    }
    
    /**
     * Should we avoid trading due to news?
     */
    public boolean shouldAvoidTrading() {
        return currentImpact == NewsImpact.NEGATIVE && highImpactNewsActive;
    }
    
    /**
     * Get confidence adjustment based on news
     */
    public double getConfidenceAdjustment() {
        switch (currentImpact) {
            case POSITIVE: return 5;   // Lower required confidence
            case NEUTRAL: return 0;
            case NEGATIVE: return -10;  // Higher required confidence
            default: return 0;
        }
    }
    
    /**
     * Get trading multiplier based on news
     */
    public double getTradingMultiplier() {
        if (highImpactNewsActive) {
            return 0.5;  // Half trades during high-impact news
        }
        switch (currentImpact) {
            case POSITIVE: return 1.2;
            case NEUTRAL: return 1.0;
            case NEGATIVE: return 0.7;
            default: return 1.0;
        }
    }
    
    /**
     * Get status message for UI
     */
    public String getStatusMessage() {
        String emoji = "";
        switch (currentImpact) {
            case POSITIVE: emoji = "📈"; break;
            case NEUTRAL: emoji = "📊"; break;
            case NEGATIVE: emoji = "⚠️"; break;
            default: emoji = "❓"; break;
        }
        
        String highImpact = highImpactNewsActive ? " [HIGH IMPACT]" : "";
        return String.format("%s News: %s (%d%%)%s",
            emoji, currentImpact, newsScore, highImpact);
    }
    
    // Getters
    public NewsImpact getCurrentImpact() { return currentImpact; }
    public int getNewsScore() { return newsScore; }
    public boolean isHighImpactNewsActive() { return highImpactNewsActive; }
}
