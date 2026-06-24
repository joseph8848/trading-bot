package com.elitebot.strategy;

/**
 * Determines optimal trade duration based on signal confidence.
 * Higher confidence = longer duration for better payouts.
 */
public class TimeOptimizer {
    
    // Confidence thresholds
    private static final double STRONG_THRESHOLD = 85.0;
    private static final double MEDIUM_THRESHOLD = 70.0;
    private static final double WEAK_THRESHOLD = 60.0;
    
    // Time durations in minutes
    private static final int LONG_DURATION = 5;
    private static final int MEDIUM_DURATION = 3;
    private static final int SHORT_DURATION = 1;
    
    /**
     * Get optimal trade duration based on signal confidence
     * 
     * @param confidence Signal confidence (0-100)
     * @return Recommended duration in minutes
     */
    public int getOptimalTimeMinutes(double confidence) {
        if (confidence >= STRONG_THRESHOLD) {
            return LONG_DURATION;  // Strong signal → 5 min (higher payout)
        } else if (confidence >= MEDIUM_THRESHOLD) {
            return MEDIUM_DURATION;  // Medium signal → 3 min
        } else {
            return SHORT_DURATION;  // Weak signal → 1 min (quick turnaround)
        }
    }
    
    /**
     * Get description of the time selection logic
     */
    public String getRecommendationReason(double confidence) {
        if (confidence >= STRONG_THRESHOLD) {
            return String.format("Strong signal (%.1f%%) → 5 min for higher payout", confidence);
        } else if (confidence >= MEDIUM_THRESHOLD) {
            return String.format("Medium signal (%.1f%%) → 3 min balanced trade", confidence);
        } else if (confidence >= WEAK_THRESHOLD) {
            return String.format("Weak signal (%.1f%%) → 1 min quick trade", confidence);
        } else {
            return String.format("Low confidence (%.1f%%) → Consider waiting", confidence);
        }
    }
    
    /**
     * Check if confidence is high enough to trade
     */
    public boolean shouldTrade(double confidence) {
        return confidence >= WEAK_THRESHOLD;
    }
    
    /**
     * Get available time options for manual selection
     */
    public int[] getAvailableDurations() {
        return new int[]{1, 3, 5};
    }
    
    /**
     * Get confidence category label
     */
    public String getConfidenceCategory(double confidence) {
        if (confidence >= STRONG_THRESHOLD) return "STRONG";
        if (confidence >= MEDIUM_THRESHOLD) return "MEDIUM";
        if (confidence >= WEAK_THRESHOLD) return "WEAK";
        return "NO TRADE";
    }
}
