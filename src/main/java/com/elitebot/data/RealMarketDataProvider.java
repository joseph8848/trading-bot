package com.elitebot.data;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.*;

/**
 * Real Market Data Provider v2.0
 * 
 * Fetches REAL market prices from multiple sources:
 * 1. Twelve Data API (PRIMARY - fast, 800 req/day free)
 * 2. Alpha Vantage API (BACKUP - 5 req/min free)
 * 3. Pocket Option (via Python bridge to extension)
 * 
 * This replaces PriceSimulator for live trading!
 */
public class RealMarketDataProvider {
    
    // Twelve Data API (PRIMARY - faster, more reliable)
    private static final String TWELVE_DATA_KEY = "demo";  // Free demo key works for forex
    private static final String TWELVE_DATA_URL = "https://api.twelvedata.com/price";
    
    // Alpha Vantage API (BACKUP)
    private static final String ALPHA_VANTAGE_KEY = "P9SKF3OMKY35SA1N";
    private static final String ALPHA_VANTAGE_URL = "https://www.alphavantage.co/query";
    
    // Python API server (connects to Pocket Option extension)
    private static final String PYTHON_API_URL = "http://localhost:5001";
    
    // JSON parser
    private final Gson gson = new Gson();
    
    // Price cache (avoid hitting API too frequently)
    private final Map<String, CachedPrices> priceCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 10000; // 10 seconds cache (was 3s)
    
    // Price history (stores recent prices for analysis)
    private final Map<String, List<Double>> priceHistory = new ConcurrentHashMap<>();
    private static final int HISTORY_SIZE = 200;
    
    // Fallback to simulated data
    private com.elitebot.simulation.PriceSimulator fallbackSimulator;
    private boolean useFallback = true;  // Changed to true - safer default
    
    // Status tracking
    private boolean brokerConnected = false;
    private boolean twelveDataConnected = false;
    private boolean alphaVantageConnected = false;
    private String lastError = "";
    private int apiCallCount = 0;
    
    public RealMarketDataProvider() {
        initializePriceHistory();
        System.out.println("📊 RealMarketDataProvider v2.0 - Twelve Data + Alpha Vantage!");
    }
    
    public void setFallbackSimulator(com.elitebot.simulation.PriceSimulator simulator) {
        this.fallbackSimulator = simulator;
    }
    
    private void initializePriceHistory() {
        // Initialize history for currencies available on Pocket Option (non-OTC)
        String[] currencies = {
            "EUR/USD", "GBP/USD", "USD/JPY", "AUD/USD", "USD/CAD",
            "EUR/GBP", "NZD/USD", "USD/CHF", "EUR/JPY", "GBP/JPY"
        };
        for (String currency : currencies) {
            priceHistory.put(currency, new ArrayList<>());
        }
    }
    
    /**
     * Get verified prices - tries multiple sources in order:
     * 1. Twelve Data (fast, primary)
     * 2. Alpha Vantage (backup)
     * 3. Broker (Pocket Option via extension)
     * 4. Fallback simulator (if all else fails)
     */
    public List<Double> getVerifiedPrices(String currency) {
        // Check cache first
        CachedPrices cached = priceCache.get(currency);
        if (cached != null && !cached.isExpired()) {
            return new ArrayList<>(priceHistory.getOrDefault(currency, Collections.emptyList()));
        }
        
        // Try sources in order of reliability/speed
        Double priceToUse = null;
        String source = "";
        
        // 1. Try Twelve Data first (fastest, most reliable)
        priceToUse = getPriceFromTwelveData(currency);
        if (priceToUse != null) {
            source = "TWELVE_DATA";
            twelveDataConnected = true;
        }
        
        // 2. If Twelve Data failed, try Alpha Vantage
        if (priceToUse == null) {
            priceToUse = getPriceFromAlphaVantage(currency);
            if (priceToUse != null) {
                source = "ALPHA_VANTAGE";
                alphaVantageConnected = true;
            }
        }
        
        // 3. Try broker (Pocket Option extension)
        if (priceToUse == null) {
            priceToUse = getPriceFromBroker(currency);
            if (priceToUse != null) {
                source = "BROKER";
                brokerConnected = true;
            }
        }
        
        // 4. Fallback to simulator
        if (priceToUse == null && useFallback && fallbackSimulator != null) {
            List<Double> simulated = fallbackSimulator.getPriceHistory(currency);
            if (!simulated.isEmpty()) {
                priceToUse = simulated.get(simulated.size() - 1);
                source = "SIMULATED";
            }
        }
        
        // Update price history
        if (priceToUse != null) {
            List<Double> history = priceHistory.computeIfAbsent(currency, k -> new ArrayList<>());
            history.add(priceToUse);
            if (history.size() > HISTORY_SIZE) {
                history.remove(0);
            }
            
            // Update cache
            priceCache.put(currency, new CachedPrices(priceToUse, source));
            apiCallCount++;
        }
        
        return new ArrayList<>(priceHistory.getOrDefault(currency, Collections.emptyList()));
    }
    
    /**
     * Get price from Twelve Data API (PRIMARY SOURCE)
     * Free tier: 800 requests/day, real-time forex data
     */
    private Double getPriceFromTwelveData(String currency) {
        try {
            // Convert EUR/USD to EURUSD format for Twelve Data
            String symbol = currency.replace("/", "");
            
            String urlString = TWELVE_DATA_URL + 
                "?symbol=" + symbol +
                "&apikey=" + TWELVE_DATA_KEY;
            
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                if (json.has("price")) {
                    double price = json.get("price").getAsDouble();
                    System.out.println("📈 Twelve Data: " + currency + " = " + price);
                    return price;
                }
            }
        } catch (Exception e) {
            // Silently fail - will try next source
            lastError = "Twelve Data: " + e.getMessage();
        }
        return null;
    }
    
    /**
     * Get price history (used by TradingBrain)
     */
    public List<Double> getPriceHistory(String currency) {
        // Try to get fresh data first
        if (priceHistory.getOrDefault(currency, Collections.emptyList()).isEmpty()) {
            getVerifiedPrices(currency);
        }
        return new ArrayList<>(priceHistory.getOrDefault(currency, Collections.emptyList()));
    }
    
    /**
     * Get current price from Pocket Option broker (via Python API)
     */
    private Double getPriceFromBroker(String currency) {
        try {
            String urlString = PYTHON_API_URL + "/broker/price?currency=" + 
                               URLEncoder.encode(currency, "UTF-8");
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                if (json.has("success") && json.get("success").getAsBoolean()) {
                    brokerConnected = true;
                    if (json.has("price")) {
                        return json.get("price").getAsDouble();
                    }
                }
            }
        } catch (Exception e) {
            brokerConnected = false;
            // Silently fail - we'll try API
        }
        return null;
    }
    
    /**
     * Get price from Alpha Vantage API
     */
    private Double getPriceFromAlphaVantage(String currency) {
        try {
            // Convert currency pair to Alpha Vantage format
            String[] parts = currency.split("/");
            if (parts.length != 2) return null;
            
            String from = parts[0];
            String to = parts[1];
            
            // Forex/Crypto endpoint - same for both
            String urlString = ALPHA_VANTAGE_URL + 
                "?function=CURRENCY_EXCHANGE_RATE" +
                "&from_currency=" + from +
                "&to_currency=" + to +
                "&apikey=" + ALPHA_VANTAGE_KEY;
            
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                if (json.has("Realtime Currency Exchange Rate")) {
                    JsonObject rateData = json.getAsJsonObject("Realtime Currency Exchange Rate");
                    if (rateData.has("5. Exchange Rate")) {
                        alphaVantageConnected = true;
                        return rateData.get("5. Exchange Rate").getAsDouble();
                    }
                }
            }
        } catch (Exception e) {
            // Will try fallback
            lastError = "Alpha Vantage: " + e.getMessage();
        }
        return null;
    }
    
    /**
     * Get current price (latest from history)
     */
    public double getCurrentPrice(String currency) {
        List<Double> history = getPriceHistory(currency);
        if (history.isEmpty()) return 0;
        return history.get(history.size() - 1);
    }
    
    /**
     * Check if we're using real data
     */
    public boolean isUsingRealData() {
        return brokerConnected || twelveDataConnected || alphaVantageConnected;
    }
    
    /**
     * Get status message
     */
    public String getStatusMessage() {
        StringBuilder sb = new StringBuilder();
        if (twelveDataConnected) sb.append("📈 Twelve Data ");
        if (alphaVantageConnected) sb.append("📊 Alpha Vantage ");
        if (brokerConnected) sb.append("🔗 Broker ");
        
        if (sb.length() > 0) {
            return "Real data: " + sb.toString().trim() + " (calls: " + apiCallCount + ")";
        } else {
            return "⚠️ Using simulated data - no API connected";
        }
    }
    
    public boolean isBrokerConnected() { return brokerConnected; }
    public boolean isTwelveDataConnected() { return twelveDataConnected; }
    public boolean isAlphaVantageConnected() { return alphaVantageConnected; }
    public int getApiCallCount() { return apiCallCount; }
    public void setUseFallback(boolean useFallback) { this.useFallback = useFallback; }
    
    /**
     * Cache entry for prices
     */
    private static class CachedPrices {
        final double price;
        final String source;
        final long timestamp;
        
        CachedPrices(double price, String source) {
            this.price = price;
            this.source = source;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION_MS;
        }
    }
}
