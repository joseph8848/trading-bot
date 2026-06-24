package com.elitebot.strategy;

import com.elitebot.model.ScanResult;
import com.elitebot.model.TradingSignal;
import com.elitebot.simulation.PriceSimulator;
import com.elitebot.data.RealMarketDataProvider;

import java.util.*;

/**
 * Multi-Currency Scanner v3.0I - ACCURACY FOCUSED
 * 
 * - Uses ADVANCED strategy (5 confirmations required)
 * - Real-time market data from Twelve Data API
 * - Logs data source for verification
 */
public class MultiCurrencyScanner {
    
    private final PriceSimulator priceSimulator;
    private RealMarketDataProvider realMarketData;
    private boolean useRealData = true;
    private final AdvancedStrategy advancedStrategy;  // UPGRADED: More accurate!
    private final SimpleStrategy simpleStrategy;      // Fallback
    private final TimeOptimizer timeOptimizer;
    
    // Logging
    private String lastDataSource = "UNKNOWN";
    private int realDataCalls = 0;
    private int simulatedDataCalls = 0;
    
    private final List<String> currencies = Arrays.asList(
        // Currencies available on Pocket Option (non-OTC)
        "EUR/USD",   // Most popular forex pair
        "GBP/USD",   // Major pair
        "USD/JPY",   // Major pair
        "AUD/USD",   // Major pair
        "USD/CAD",   // Major pair
        "EUR/GBP",   // Cross pair
        "NZD/USD"    // Major pair
    );

    
    public MultiCurrencyScanner(PriceSimulator priceSimulator) {
        this.priceSimulator = priceSimulator;
        this.advancedStrategy = new AdvancedStrategy();  // UPGRADED!
        this.simpleStrategy = new SimpleStrategy();
        this.timeOptimizer = new TimeOptimizer();
        System.out.println("📊 Scanner using ADVANCED strategy (5 confirmations required)");
    }
    
    /**
     * Set the real market data provider for live price feeds
     */
    public void setRealMarketDataProvider(RealMarketDataProvider provider) {
        this.realMarketData = provider;
        System.out.println("📊 Scanner now using REAL market data!");
    }
    
    /**
     * Toggle between real and simulated data
     */
    public void setUseRealData(boolean useReal) {
        this.useRealData = useReal;
        System.out.println("📊 Scanner data mode: " + (useReal ? "REAL" : "SIMULATED"));
    }
    
    public boolean isUsingRealData() {
        return useRealData && realMarketData != null;
    }
    
    /**
     * Get price history - uses real data when enabled, falls back to simulator
     */
    private List<Double> getPrices(String currency) {
        if (useRealData && realMarketData != null) {
            List<Double> realPrices = realMarketData.getVerifiedPrices(currency);
            if (realPrices != null && !realPrices.isEmpty()) {
                lastDataSource = "REAL";
                realDataCalls++;
                return realPrices;
            }
        }
        // Fallback to simulator
        lastDataSource = "SIMULATED";
        simulatedDataCalls++;
        return priceSimulator.getPriceHistory(currency);
    }
    
    /**
     * Get volatility - uses real data when available
     */
    private double getVolatility(String currency) {
        if (useRealData && realMarketData != null) {
            List<Double> prices = realMarketData.getPriceHistory(currency);
            if (prices != null && prices.size() >= 10) {
                // Calculate volatility from real prices
                List<Double> recent = prices.subList(prices.size() - 10, prices.size());
                double mean = recent.stream().mapToDouble(d -> d).average().orElse(0);
                double variance = recent.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0);
                return Math.sqrt(variance);
            }
        }
        return priceSimulator.calculateVolatility(currency);
    }
    
    /**
     * Scan all currencies and return results sorted by confidence (best first)
     */
    public List<ScanResult> scanAllCurrencies() {
        List<ScanResult> results = new ArrayList<>();
        
        for (String currency : currencies) {
            ScanResult result = scanCurrency(currency);
            results.add(result);
        }
        
        // Sort by confidence (descending)
        Collections.sort(results);
        
        return results;
    }
    
    /**
     * Scan a specific currency and get its signal
     */
    public ScanResult scanCurrency(String currency) {
        List<Double> prices = getPrices(currency);
        
        // Use ADVANCED strategy for higher accuracy (5 confirmations needed)
        TradingSignal signal = advancedStrategy.analyzeWithConfirmation(currency, prices);
        
        // Log data source periodically
        if (realDataCalls > 0 && realDataCalls % 50 == 0) {
            System.out.println("📊 Data stats: REAL=" + realDataCalls + ", SIM=" + simulatedDataCalls + 
                " | Last: " + lastDataSource);
        }
        
        int recommendedTime = timeOptimizer.getOptimalTimeMinutes(signal.getConfidence());
        double volatility = getVolatility(currency);
        
        return new ScanResult(currency, signal, recommendedTime, volatility);
    }
    
    /**
     * Get the best currency to trade (highest confidence actionable signal)
     */
    public ScanResult getBestOpportunity() {
        List<ScanResult> results = scanAllCurrencies();
        
        // Return the best actionable signal
        for (ScanResult result : results) {
            if (result.isActionable()) {
                return result;
            }
        }
        
        // If no actionable signals, return the top one anyway (for display)
        return results.isEmpty() ? null : results.get(0);
    }
    
    /**
     * Get the best opportunity EXCLUDING specified currencies.
     * Used when the primary best currency already has an active trade.
     * 
     * @param excludeCurrencies Set of currency codes to skip
     * @return Next best actionable opportunity, or null if none found
     */
    public ScanResult getNextBestOpportunity(java.util.Set<String> excludeCurrencies) {
        List<ScanResult> results = scanAllCurrencies();
        
        // Find the best actionable signal that's NOT in the exclusion list
        for (ScanResult result : results) {
            if (result.isActionable() && !excludeCurrencies.contains(result.getCurrency())) {
                System.out.println("🔄 Switched to " + result.getCurrency() + 
                    " (best available, " + (int)result.getConfidence() + "% confidence)");
                return result;
            }
        }
        
        return null;  // No available alternatives
    }
    
    /**
     * Get the best currency code
     */
    public String getBestCurrency() {
        ScanResult best = getBestOpportunity();
        return best != null ? best.getCurrency() : currencies.get(0);
    }
    
    /**
     * Check if any currency has a strong signal right now
     */
    public boolean hasStrongSignal() {
        return scanAllCurrencies().stream()
            .anyMatch(r -> r.getConfidence() >= 85);
    }
    
    /**
     * Get all available currencies
     */
    public List<String> getAvailableCurrencies() {
        return new ArrayList<>(currencies);
    }
    
    /**
     * Get summary of all currency conditions
     */
    public String getMarketSummary() {
        List<ScanResult> results = scanAllCurrencies();
        long callCount = results.stream()
            .filter(r -> r.getSignal().getSignalType() == TradingSignal.SignalType.CALL)
            .count();
        long putCount = results.stream()
            .filter(r -> r.getSignal().getSignalType() == TradingSignal.SignalType.PUT)
            .count();
        
        String sentiment = callCount > putCount ? "BULLISH" :
                          putCount > callCount ? "BEARISH" : "NEUTRAL";
        
        ScanResult best = getBestOpportunity();
        return String.format("Market: %s | Best: %s (%.1f%%)", 
            sentiment, best.getCurrency(), best.getConfidence());
    }
}
