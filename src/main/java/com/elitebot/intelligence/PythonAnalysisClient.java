package com.elitebot.intelligence;

import java.io.*;
import java.net.*;
import java.util.*;
import com.google.gson.*;

/**
 * Python Analysis Client
 * 
 * Connects to the Python analysis server for advanced signal generation
 * and market analysis.
 * 
 * The Python module provides:
 * - Multi-indicator confluence scoring
 * - RSI, MACD, momentum analysis
 * - Risk management (session awareness, loss limits)
 * - Market condition detection
 */
public class PythonAnalysisClient {
    
    private static final String DEFAULT_HOST = "http://localhost:5001";
    private String serverUrl;
    private boolean isConnected = false;
    private Gson gson = new Gson();
    
    public PythonAnalysisClient() {
        this(DEFAULT_HOST);
    }
    
    public PythonAnalysisClient(String serverUrl) {
        this.serverUrl = serverUrl;
        checkConnection();
    }
    
    /**
     * Check if Python server is running
     */
    public boolean checkConnection() {
        try {
            String response = sendGet("/");
            isConnected = response.contains("running");
            if (isConnected) {
                System.out.println("🐍 Python Analysis Server connected");
            }
            return isConnected;
        } catch (Exception e) {
            isConnected = false;
            System.out.println("⚠️ Python Analysis Server not available - using Java-only mode");
            return false;
        }
    }
    
    /**
     * Send prices to Python for caching
     */
    public boolean updatePrices(String symbol, List<Double> prices) {
        if (!isConnected) return false;
        
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("symbol", symbol);
            data.put("prices", prices);
            
            String response = sendPost("/prices", gson.toJson(data));
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            return result.get("success").getAsBoolean();
        } catch (Exception e) {
            System.err.println("Failed to update prices: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get full market analysis from Python
     */
    public AnalysisResult getFullAnalysis(String symbol, List<Double> prices) {
        if (!isConnected) {
            return AnalysisResult.unavailable("Python server not connected");
        }
        
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("symbol", symbol);
            data.put("prices", prices);
            
            String response = sendPost("/analyze", gson.toJson(data));
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            if (!result.get("success").getAsBoolean()) {
                return AnalysisResult.unavailable(result.get("error").getAsString());
            }
            
            return parseAnalysisResult(result);
            
        } catch (Exception e) {
            System.err.println("Analysis failed: " + e.getMessage());
            return AnalysisResult.unavailable(e.getMessage());
        }
    }
    
    /**
     * Get quick signal (CALL/PUT/WAIT)
     */
    public SignalResult getQuickSignal(String symbol, List<Double> prices) {
        if (!isConnected) {
            return new SignalResult("WAIT", 0, 1.0, "Python server not connected");
        }
        
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("symbol", symbol);
            data.put("prices", prices);
            
            String response = sendPost("/signal", gson.toJson(data));
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            return new SignalResult(
                result.get("signal").getAsString(),
                result.get("confidence").getAsDouble(),
                result.get("position_size").getAsDouble(),
                result.get("reason").getAsString()
            );
            
        } catch (Exception e) {
            return new SignalResult("WAIT", 0, 1.0, "Error: " + e.getMessage());
        }
    }
    
    /**
     * Record trade result for Python risk tracking
     */
    public boolean recordTradeResult(boolean won, double profitLoss, String symbol) {
        if (!isConnected) return false;
        
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("won", won);
            data.put("profit_loss", profitLoss);
            data.put("symbol", symbol);
            
            String response = sendPost("/record", gson.toJson(data));
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            return result.get("success").getAsBoolean();
            
        } catch (Exception e) {
            System.err.println("Failed to record trade: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get risk status from Python
     */
    public Map<String, Object> getRiskStatus() {
        if (!isConnected) return Collections.emptyMap();
        
        try {
            String response = sendGet("/status");
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            Map<String, Object> status = new HashMap<>();
            JsonObject riskStatus = result.getAsJsonObject("risk_status");
            
            status.put("consecutive_losses", riskStatus.get("consecutive_losses").getAsInt());
            status.put("daily_trades", riskStatus.get("daily_trades").getAsInt());
            status.put("daily_losses", riskStatus.get("daily_losses").getAsInt());
            status.put("can_trade", riskStatus.get("can_trade").getAsBoolean());
            status.put("active_session", riskStatus.get("active_session").getAsString());
            
            return status;
            
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
    
    private AnalysisResult parseAnalysisResult(JsonObject json) {
        AnalysisResult result = new AnalysisResult();
        
        result.signal = json.get("signal").getAsString();
        result.signalType = json.get("signal_type").getAsString();
        result.confidence = json.get("confidence").getAsDouble();
        result.canTrade = json.get("can_trade").getAsBoolean();
        result.riskScore = json.get("risk_score").getAsDouble();
        result.positionSize = json.get("position_size").getAsDouble();
        result.riskLevel = json.get("risk_level").getAsString();
        result.marketCondition = json.get("market_condition").getAsString();
        result.trendStrength = json.get("trend_strength").getAsDouble();
        result.volatilityLevel = json.get("volatility_level").getAsString();
        result.momentum = json.get("momentum").getAsDouble();
        result.recommendation = json.get("recommendation").getAsString();
        
        // Parse indicators
        JsonObject indicators = json.getAsJsonObject("indicators");
        result.rsi = indicators.get("rsi").getAsDouble();
        result.rsiSignal = indicators.get("rsi_signal").getAsString();
        result.macd = indicators.get("macd").getAsDouble();
        result.macdSignal = indicators.get("macd_signal").getAsString();
        
        // Parse warnings
        result.warnings = new ArrayList<>();
        JsonArray warningsArray = json.getAsJsonArray("warnings");
        for (JsonElement w : warningsArray) {
            result.warnings.add(w.getAsString());
        }
        
        result.available = true;
        return result;
    }
    
    private String sendGet(String endpoint) throws IOException {
        URL url = new URL(serverUrl + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        
        return readResponse(conn);
    }
    
    private String sendPost(String endpoint, String jsonBody) throws IOException {
        URL url = new URL(serverUrl + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes("UTF-8"));
        }
        
        return readResponse(conn);
    }
    
    private String readResponse(HttpURLConnection conn) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }
    
    public boolean isConnected() {
        return isConnected;
    }
    
    // ==================== POCKET OPTION LIVE TRADING ====================
    
    /**
     * Execute a trade on Pocket Option via browser automation.
     * 
     * @param direction "BUY" or "SELL"
     * @param amount Trade amount (optional, 0 for default)
     * @param asset Asset to trade (optional, null for current)
     * @return true if trade was queued successfully
     */
    public boolean executePocketOptionTrade(String direction, double amount, String asset) {
        if (!isConnected) {
            System.out.println("⚠️ Cannot execute Pocket Option trade - server not connected");
            return false;
        }
        
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("direction", direction);
            if (amount > 0) {
                data.put("amount", amount);
            }
            if (asset != null && !asset.isEmpty()) {
                data.put("asset", asset);
            }
            
            String response = sendPost("/pocket-live/trade", gson.toJson(data));
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            if (result.get("success").getAsBoolean()) {
                System.out.println("💰 Pocket Option trade queued: " + direction);
                return true;
            } else {
                System.err.println("Failed to queue Pocket Option trade: " + result.get("error").getAsString());
                return false;
            }
        } catch (Exception e) {
            System.err.println("Pocket Option trade error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Execute a trade on Pocket Option (simple version)
     */
    public boolean executePocketOptionTrade(String direction) {
        return executePocketOptionTrade(direction, 0, null);
    }
    
    /**
     * Get pending Pocket Option trades
     */
    public int getPendingPocketOptionTrades() {
        try {
            String response = sendGet("/pocket-live/pending");
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            return result.get("count").getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }
    
    // ==================== BROWSER-BASED POCKET OPTION CONTROL ====================
    
    /**
     * Open Pocket Option browser for manual login.
     * Call this first, then user logs in, then you can trade.
     * 
     * @return true if browser opened successfully
     */
    public boolean openBrowserForLogin() {
        if (!isConnected) {
            System.out.println("⚠️ Cannot open browser - Python server not connected");
            return false;
        }
        
        try {
            String response = sendPost("/browser/open", "{}");
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            if (result.get("success").getAsBoolean()) {
                System.out.println("🌐 Browser opened! Please login to Pocket Option...");
                return true;
            } else {
                System.err.println("Failed to open browser: " + result.get("message").getAsString());
                return false;
            }
        } catch (Exception e) {
            System.err.println("Browser open error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get current browser/broker status including balance and currency.
     * Call this to check if user has logged in and to get current state.
     * 
     * @return BrowserStatus with all current broker information
     */
    public BrowserStatus getBrowserStatus() {
        BrowserStatus status = new BrowserStatus();
        
        try {
            String response = sendGet("/browser/status");
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            status.browserOpen = result.has("browser_open") && result.get("browser_open").getAsBoolean();
            status.loggedIn = result.has("logged_in") && result.get("logged_in").getAsBoolean();
            status.demoMode = result.has("demo_mode") && result.get("demo_mode").getAsBoolean();
            status.balance = result.has("balance") ? result.get("balance").getAsDouble() : 0.0;
            status.currency = result.has("currency") ? result.get("currency").getAsString() : "";
            status.payout = result.has("payout") ? result.get("payout").getAsDouble() : 0.0;
            status.tradesExecuted = result.has("trades_executed") ? result.get("trades_executed").getAsInt() : 0;
            status.message = result.has("message") ? result.get("message").getAsString() : "";
            
        } catch (Exception e) {
            System.err.println("Failed to get browser status: " + e.getMessage());
        }
        
        return status;
    }
    
    /**
     * Execute a trade via browser automation.
     * 
     * @param direction "CALL" or "PUT"
     * @param amount Optional trade amount (0 for default)
     * @return true if trade executed successfully
     */
    public boolean executeBrowserTrade(String direction, double amount) {
        if (!isConnected) {
            System.out.println("⚠️ Cannot execute trade - server not connected");
            return false;
        }
        
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("direction", direction);
            if (amount > 0) {
                data.put("amount", amount);
            }
            
            String response = sendPost("/browser/trade", gson.toJson(data));
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            if (result.get("success").getAsBoolean()) {
                System.out.println("💰 Trade executed via browser: " + direction);
                return true;
            } else {
                String error = result.has("error") ? result.get("error").getAsString() : "Unknown error";
                System.err.println("Trade failed: " + error);
                return false;
            }
        } catch (Exception e) {
            System.err.println("Browser trade error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Execute a trade via browser (simple version)
     */
    public boolean executeBrowserTrade(String direction) {
        return executeBrowserTrade(direction, 0);
    }
    
    /**
     * Change the active currency/asset on the broker.
     * 
     * @param symbol Currency pair like "EUR/USD" or "BTC/USD"
     * @return true if currency changed successfully
     */
    public boolean setBrowserCurrency(String symbol) {
        if (!isConnected) return false;
        
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("symbol", symbol);
            
            String response = sendPost("/browser/currency", gson.toJson(data));
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            if (result.get("success").getAsBoolean()) {
                System.out.println("📊 Currency change requested: " + symbol);
                return true;
            }
        } catch (Exception e) {
            System.err.println("Currency change error: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Change currency and WAIT for verification.
     * Polls broker status until currency matches or timeout.
     * 
     * @param symbol Target currency pair
     * @param maxWaitMs Maximum time to wait (e.g., 3000 for 3 seconds)
     * @return true if currency verified to have changed
     */
    public boolean setCurrencyAndWait(String symbol, int maxWaitMs) {
        if (!isConnected) return false;
        
        // Get current currency first
        BrowserStatus currentStatus = getBrowserStatus();
        String cleanSymbol = symbol.replace("/", "").toUpperCase();
        
        // Already on this currency?
        if (currentStatus.currency.replace("/", "").toUpperCase().contains(cleanSymbol)) {
            System.out.println("✅ Already on " + symbol);
            return true;
        }
        
        // Request currency change
        System.out.println("📊 Changing currency to " + symbol + "...");
        if (!setBrowserCurrency(symbol)) {
            System.err.println("❌ Currency change request failed");
            return false;
        }
        
        // Poll for verification
        int pollInterval = 200; // ms
        int maxAttempts = maxWaitMs / pollInterval;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException e) {
                break;
            }
            
            BrowserStatus status = getBrowserStatus();
            
            // Check if currency changed
            if (status.currency.replace("/", "").toUpperCase().contains(cleanSymbol)) {
                System.out.println("✅ Currency verified: " + status.currency + " (attempt " + attempt + ")");
                return true;
            }
            
            // Check if browser is still open
            if (!status.browserOpen) {
                System.err.println("❌ Browser closed during currency change!");
                return false;
            }
        }
        
        System.err.println("⏰ Currency change TIMEOUT - still on " + getBrowserStatus().currency);
        return false;
    }
    
    // ===== TRADE HEALTH MONITORING =====
    private int consecutiveTradeFailures = 0;
    private long lastSuccessfulTradeTime = 0;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    
    /**
     * Record a successful trade execution.
     */
    public void recordTradeSuccess() {
        consecutiveTradeFailures = 0;
        lastSuccessfulTradeTime = System.currentTimeMillis();
    }
    
    /**
     * Record a failed trade execution.
     */
    public void recordTradeFailure() {
        consecutiveTradeFailures++;
        if (consecutiveTradeFailures >= MAX_CONSECUTIVE_FAILURES) {
            System.err.println("🚨 " + MAX_CONSECUTIVE_FAILURES + " CONSECUTIVE TRADE FAILURES! Broker may be disconnected.");
        }
    }
    
    /**
     * Check if broker trading is healthy.
     * Returns false if too many consecutive failures.
     */
    public boolean isBrokerHealthy() {
        // Check consecutive failures
        if (consecutiveTradeFailures >= MAX_CONSECUTIVE_FAILURES) {
            return false;
        }
        
        // Check if browser is still open and logged in
        BrowserStatus status = getBrowserStatus();
        return status.browserOpen && status.loggedIn;
    }
    
    /**
     * Get consecutive failure count for monitoring.
     */
    public int getConsecutiveFailures() {
        return consecutiveTradeFailures;
    }
    
    /**
     * Reset failure count (after reconnection).
     */
    public void resetFailureCount() {
        consecutiveTradeFailures = 0;
    }
    
    /**
     * Set the trade amount on the broker.
     * 
     * @param amount Trade amount in USD
     * @return true if amount set successfully
     */
    public boolean setBrowserAmount(double amount) {
        if (!isConnected) return false;
        
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("amount", amount);
            
            String response = sendPost("/browser/amount", gson.toJson(data));
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            return result.get("success").getAsBoolean();
        } catch (Exception e) {
            System.err.println("Set amount error: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Set the trade duration on the broker.
     * 
     * @param minutes Trade duration in minutes
     * @return true if duration set successfully
     */
    public boolean setBrowserDuration(int minutes) {
        if (!isConnected) return false;
        
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("minutes", minutes);
            
            String response = sendPost("/browser/duration", gson.toJson(data));
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            return result.get("success").getAsBoolean();
        } catch (Exception e) {
            System.err.println("Set duration error: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Get current balance from the browser.
     */
    public double getBrowserBalance() {
        return getBrowserStatus().balance;
    }
    
    /**
     * Close the browser connection.
     */
    public boolean closeBrowser() {
        try {
            String response = sendPost("/browser/close", "{}");
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            return result.get("success").getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if browser is connected and user is logged in.
     */
    public boolean isBrowserReady() {
        BrowserStatus status = getBrowserStatus();
        return status.browserOpen && status.loggedIn;
    }
    
    /**
     * Browser status data class - contains all broker state from browser
     */
    public static class BrowserStatus {
        public boolean browserOpen = false;
        public boolean loggedIn = false;
        public boolean demoMode = false;
        public double balance = 0.0;
        public String currency = "";
        public double payout = 0.0;
        public int tradesExecuted = 0;
        public String message = "";
        
        public boolean isReady() {
            return browserOpen && loggedIn;
        }
        
        @Override
        public String toString() {
            return String.format("Browser[open=%b, logged_in=%b, balance=%.2f, currency=%s]",
                browserOpen, loggedIn, balance, currency);
        }
    }
    
    // ===== HYBRID TRADING (WebSocket + Browser Fallback) =====
    
    /**
     * Connect to broker using hybrid method (browser for login, then WebSocket for trading).
     * 
     * @return true if connection initiated successfully
     */
    public boolean connectHybrid() {
        if (!isConnected) {
            System.out.println("⚠️ Cannot connect - server not connected");
            return false;
        }
        
        try {
            String response = sendPost("/hybrid/connect", "{}");
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            if (result.get("success").getAsBoolean()) {
                System.out.println("🔌 Hybrid connection initiated - opening browser for login");
                return true;
            } else {
                String error = result.has("error") ? result.get("error").getAsString() : "Unknown error";
                System.err.println("Hybrid connect failed: " + error);
                return false;
            }
        } catch (Exception e) {
            System.err.println("Hybrid connect error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get hybrid trader status (includes WebSocket connection status).
     * 
     * @return HybridStatus object with connection info
     */
    public HybridStatus getHybridStatus() {
        HybridStatus status = new HybridStatus();
        
        if (!isConnected) return status;
        
        try {
            String response = sendGet("/hybrid/status");
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            status.isConnected = result.has("is_connected") && result.get("is_connected").getAsBoolean();
            status.websocketConnected = result.has("websocket_connected") && result.get("websocket_connected").getAsBoolean();
            status.browserOpen = result.has("browser_open") && result.get("browser_open").getAsBoolean();
            status.loggedIn = result.has("logged_in") && result.get("logged_in").getAsBoolean();
            status.balance = result.has("balance") ? result.get("balance").getAsDouble() : 0;
            status.currency = result.has("currency") ? result.get("currency").getAsString() : "";
            status.totalTrades = result.has("total_trades") ? result.get("total_trades").getAsInt() : 0;
            status.tradesLastHour = result.has("trades_last_hour") ? result.get("trades_last_hour").getAsInt() : 0;
            status.consecutiveFailures = result.has("consecutive_failures") ? result.get("consecutive_failures").getAsInt() : 0;
            status.ssidAvailable = result.has("ssid_available") && result.get("ssid_available").getAsBoolean();
            
        } catch (Exception e) {
            System.err.println("Hybrid status error: " + e.getMessage());
        }
        
        return status;
    }
    
    /**
     * Execute a trade using hybrid method (WebSocket primary, browser fallback).
     * This is the recommended way to trade as it's fast and reliable.
     * 
     * @param asset Currency pair (e.g., "EUR/USD" or "EURUSD")
     * @param direction "CALL"/"BUY" or "PUT"/"SELL"
     * @param amount Trade amount in USD
     * @param durationSeconds Trade duration in seconds (e.g., 60 for 1 minute)
     * @return true if trade executed successfully
     */
    public boolean executeHybridTrade(String asset, String direction, double amount, int durationSeconds) {
        if (!isConnected) {
            System.out.println("⚠️ Cannot execute trade - server not connected");
            return false;
        }
        
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("asset", asset);
            // Normalize direction
            String normalizedDirection = direction.toUpperCase();
            if (normalizedDirection.equals("BUY") || normalizedDirection.equals("UP")) {
                normalizedDirection = "call";
            } else if (normalizedDirection.equals("SELL") || normalizedDirection.equals("DOWN")) {
                normalizedDirection = "put";
            } else {
                normalizedDirection = direction.toLowerCase();
            }
            data.put("direction", normalizedDirection);
            data.put("amount", amount);
            data.put("duration", durationSeconds);
            
            String response = sendPost("/hybrid/trade", gson.toJson(data));
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            if (result.get("success").getAsBoolean()) {
                String method = result.has("method") ? result.get("method").getAsString() : "unknown";
                System.out.println("✅ Hybrid trade executed via " + method + ": " + direction + " $" + amount + " " + asset);
                return true;
            } else {
                String error = result.has("error") ? result.get("error").getAsString() : "Unknown error";
                System.err.println("Hybrid trade failed: " + error);
                return false;
            }
        } catch (Exception e) {
            System.err.println("Hybrid trade error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Convenience method for hybrid trade with default duration.
     */
    public boolean executeHybridTrade(String asset, String direction, double amount) {
        return executeHybridTrade(asset, direction, amount, 60);
    }
    
    /**
     * Get balance from hybrid trader.
     */
    public double getHybridBalance() {
        if (!isConnected) return 0;
        
        try {
            String response = sendGet("/hybrid/balance");
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            if (result.get("success").getAsBoolean()) {
                return result.get("balance").getAsDouble();
            }
        } catch (Exception e) {
            System.err.println("Hybrid balance error: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Disconnect hybrid trader.
     */
    public boolean disconnectHybrid() {
        try {
            String response = sendPost("/hybrid/disconnect", "{}");
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            return result.get("success").getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if hybrid trader is ready (WebSocket or browser connected).
     */
    public boolean isHybridReady() {
        HybridStatus status = getHybridStatus();
        return status.isConnected && (status.websocketConnected || status.loggedIn);
    }
    
    /**
     * Hybrid status data class
     */
    public static class HybridStatus {
        public boolean isConnected = false;
        public boolean websocketConnected = false;
        public boolean browserOpen = false;
        public boolean loggedIn = false;
        public double balance = 0.0;
        public String currency = "";
        public int totalTrades = 0;
        public int tradesLastHour = 0;
        public int consecutiveFailures = 0;
        public boolean ssidAvailable = false;
        
        public boolean isReady() {
            return isConnected && (websocketConnected || loggedIn);
        }
        
        @Override
        public String toString() {
            return String.format("Hybrid[connected=%b, ws=%b, browser=%b, balance=%.2f]",
                isConnected, websocketConnected, loggedIn, balance);
        }
    }
    
    // ===== CHROME EXTENSION TRADING (NEW - RELIABLE!) =====
    // This sends trades to the Chrome extension which clicks buttons on Pocket Option
    
    /**
     * Execute trade via Chrome Extension - MOST RELIABLE METHOD!
     * The extension runs in the user's normal browser and clicks the trade buttons.
     * 
     * @param currency Currency pair (e.g., "EUR/USD")
     * @param direction "CALL" or "PUT"
     * @param amount Trade amount in USD
     * @param durationSeconds Trade duration in seconds (e.g., 60, 120, 180)
     * @return true if trade signal sent to extension successfully
     */
    public boolean executeExtensionTrade(String currency, String direction, double amount, int durationSeconds) {
        if (!isConnected) {
            System.out.println("⚠️ Cannot execute trade - server not connected");
            return false;
        }
        
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("currency", currency);
            data.put("direction", direction.toUpperCase());
            data.put("amount", amount);
            data.put("duration", durationSeconds);  // NOW INCLUDES DURATION!
            
            System.out.println("📤 Sending to extension: " + currency + " " + direction + " $" + amount + " for " + durationSeconds + "s");
            
            String response = sendPost("/extension/trade", gson.toJson(data));
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            if (result.get("success").getAsBoolean()) {
                System.out.println("✅ Trade sent to extension: " + direction + " $" + amount + " on " + currency + " (" + durationSeconds + "s)");
                return true;
            } else {
                String error = result.has("message") ? result.get("message").getAsString() : "No extension connected";
                System.err.println("Extension trade failed: " + error);
                return false;
            }
        } catch (Exception e) {
            System.err.println("Extension trade error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Legacy method without duration - defaults to 60 seconds
     */
    public boolean executeExtensionTrade(String currency, String direction, double amount) {
        return executeExtensionTrade(currency, direction, amount, 60);
    }
    
    /**
     * Get Chrome extension connection status.
     */
    public ExtensionStatus getExtensionStatus() {
        ExtensionStatus status = new ExtensionStatus();
        
        try {
            String response = sendGet("/extension/status");
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            status.extensionsConnected = result.has("extensions_connected") ? 
                result.get("extensions_connected").getAsInt() : 0;
            status.bridgeRunning = result.has("bridge_running") && 
                result.get("bridge_running").getAsBoolean();
            status.totalTrades = result.has("total_trades") ? 
                result.get("total_trades").getAsInt() : 0;
                
        } catch (Exception e) {
            System.err.println("Extension status error: " + e.getMessage());
        }
        
        return status;
    }
    
    /**
     * Check if extension is connected and ready.
     */
    public boolean isExtensionReady() {
        ExtensionStatus status = getExtensionStatus();
        return status.bridgeRunning && status.extensionsConnected > 0;
    }
    
    /**
     * Extension status data class
     */
    public static class ExtensionStatus {
        public int extensionsConnected = 0;
        public boolean bridgeRunning = false;
        public int totalTrades = 0;
        
        public boolean isReady() {
            return bridgeRunning && extensionsConnected > 0;
        }
        
        @Override
        public String toString() {
            return String.format("Extension[connected=%d, bridge=%b, trades=%d]",
                extensionsConnected, bridgeRunning, totalTrades);
        }
    }
    
    // ===== PURE WEBSOCKET API TRADING (RECOMMENDED) =====
    // This is the reliable approach - no browser automation for trades!
    
    /**
     * Connect to Pocket Option via WebSocket API.
     * Automatically extracts SSID from browser if logged in.
     * 
     * @return true if WebSocket connected successfully
     */
    public boolean connectWebSocket() {
        if (!isConnected) {
            System.out.println("⚠️ Cannot connect WebSocket - server not connected");
            return false;
        }
        
        try {
            String response = sendPost("/ws/connect", "{}");
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            if (result.get("success").getAsBoolean()) {
                System.out.println("🚀 WebSocket API connected to Pocket Option!");
                return true;
            } else {
                String error = result.has("error") ? result.get("error").getAsString() : "Unknown error";
                System.err.println("WebSocket connect failed: " + error);
                return false;
            }
        } catch (Exception e) {
            System.err.println("WebSocket connect error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Execute trade via WebSocket API - RECOMMENDED METHOD!
     * This sends all parameters directly, no browser automation.
     * 
     * @param asset Currency pair (e.g., "EURUSD")
     * @param direction "CALL"/"BUY" or "PUT"/"SELL"
     * @param amount Trade amount in USD
     * @param durationSeconds Trade duration in seconds
     * @return true if trade executed successfully
     */
    public boolean executeWebSocketTrade(String asset, String direction, double amount, int durationSeconds) {
        if (!isConnected) {
            System.out.println("⚠️ Cannot execute trade - server not connected");
            return false;
        }
        
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("asset", asset);
            
            // Normalize direction
            String normalizedDirection = direction.toUpperCase();
            if (normalizedDirection.equals("BUY") || normalizedDirection.equals("UP")) {
                normalizedDirection = "call";
            } else if (normalizedDirection.equals("SELL") || normalizedDirection.equals("DOWN")) {
                normalizedDirection = "put";
            } else if (normalizedDirection.equals("CALL")) {
                normalizedDirection = "call";
            } else {
                normalizedDirection = "put";
            }
            data.put("direction", normalizedDirection);
            data.put("amount", amount);
            data.put("duration", durationSeconds);
            
            String response = sendPost("/ws/trade", gson.toJson(data));
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            if (result.get("success").getAsBoolean()) {
                String tradeId = result.has("trade_id") ? result.get("trade_id").getAsString() : "unknown";
                System.out.println("✅ WebSocket trade SUCCESS: " + direction + " $" + amount + " " + asset + " [" + tradeId + "]");
                return true;
            } else {
                String error = result.has("error") ? result.get("error").getAsString() : "Trade failed";
                System.err.println("❌ WebSocket trade failed: " + error);
                return false;
            }
        } catch (Exception e) {
            System.err.println("WebSocket trade error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get WebSocket connection status.
     */
    public WebSocketStatus getWebSocketStatus() {
        WebSocketStatus status = new WebSocketStatus();
        
        if (!isConnected) return status;
        
        try {
            String response = sendGet("/ws/status");
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            status.connected = result.has("websocket_connected") && result.get("websocket_connected").getAsBoolean();
            status.authenticated = result.has("authenticated") && result.get("authenticated").getAsBoolean();
            status.balance = result.has("balance") ? result.get("balance").getAsDouble() : 0;
            status.demoMode = result.has("demo_mode") && result.get("demo_mode").getAsBoolean();
            status.ssidAvailable = result.has("ssid_available") && result.get("ssid_available").getAsBoolean();
            status.pendingTrades = result.has("pending_trades") ? result.get("pending_trades").getAsInt() : 0;
            
        } catch (Exception e) {
            System.err.println("WebSocket status error: " + e.getMessage());
        }
        
        return status;
    }
    
    /**
     * Check if WebSocket is ready for trading.
     */
    public boolean isWebSocketReady() {
        WebSocketStatus status = getWebSocketStatus();
        return status.connected;
    }
    
    /**
     * WebSocket status data class
     */
    public static class WebSocketStatus {
        public boolean connected = false;
        public boolean authenticated = false;
        public double balance = 0.0;
        public boolean demoMode = true;
        public boolean ssidAvailable = false;
        public int pendingTrades = 0;
        
        @Override
        public String toString() {
            return String.format("WebSocket[connected=%b, auth=%b, balance=%.2f]",
                connected, authenticated, balance);
        }
    }
    
    /**
     * Get the current broker (Pocket Option) balance
     */
    public double getBrokerBalance() {
        try {
            String response = sendGet("/broker/balance");
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            if (result.get("success").getAsBoolean()) {
                return result.get("balance").getAsDouble();
            }
        } catch (Exception e) {
            System.err.println("Failed to get broker balance: " + e.getMessage());
        }
        return 0.0;
    }
    
    /**
     * Sync a balance value to the broker state
     */
    public boolean syncBrokerBalance(double balance) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("balance", balance);
            
            String response = sendPost("/broker/sync-balance", gson.toJson(data));
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            return result.get("success").getAsBoolean();
        } catch (Exception e) {
            System.err.println("Failed to sync broker balance: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Record a trade result from the broker
     */
    public boolean recordBrokerResult(boolean won, double profit, String asset, double amount) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("won", won);
            data.put("profit", profit);
            data.put("asset", asset);
            data.put("amount", amount);
            
            String response = sendPost("/broker/record-result", gson.toJson(data));
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            return result.get("success").getAsBoolean();
        } catch (Exception e) {
            System.err.println("Failed to record broker result: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get full broker state including balance, trades, and win rate
     */
    public BrokerState getBrokerState() {
        try {
            String response = sendGet("/broker/state");
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            if (result.get("success").getAsBoolean()) {
                BrokerState state = new BrokerState();
                state.balance = result.get("balance").getAsDouble();
                state.totalTrades = result.get("total_trades").getAsInt();
                state.totalProfit = result.get("total_profit").getAsDouble();
                state.winRate = result.get("win_rate").getAsString();
                state.lastSync = result.has("last_sync") && !result.get("last_sync").isJsonNull() 
                    ? result.get("last_sync").getAsString() : null;
                return state;
            }
        } catch (Exception e) {
            System.err.println("Failed to get broker state: " + e.getMessage());
        }
        return new BrokerState();
    }
    
    /**
     * Broker state data class
     */
    public static class BrokerState {
        public double balance = 0.0;
        public int totalTrades = 0;
        public double totalProfit = 0.0;
        public String winRate = "0%";
        public String lastSync = null;
        
        public boolean isSynced() {
            return lastSync != null;
        }
    }
    
    // ==================== MULTI-AI CONSENSUS METHODS ====================
    
    /**
     * Get Multi-AI Consensus Signal
     * 
     * This is the MAIN method for trading decisions!
     * Combines signals from FinGPT, Python ML, and Java Brain.
     * 
     * @param symbol Currency pair (e.g., "EUR/USD")
     * @param prices Historical price data
     * @param javaDirection Java Brain's signal direction ("BUY", "SELL", "WAIT")
     * @param javaConfidence Java Brain's confidence (0-100)
     * @return ConsensusResult with combined signal from all AIs
     */
    public ConsensusResult getConsensusSignal(String symbol, List<Double> prices,
                                               String javaDirection, double javaConfidence) {
        if (!isConnected) {
            return ConsensusResult.unavailable("Python server not connected");
        }
        
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("symbol", symbol);
            data.put("prices", prices);
            
            // Include Java Brain's signal for consensus
            Map<String, Object> javaSignal = new HashMap<>();
            javaSignal.put("direction", javaDirection);
            javaSignal.put("confidence", javaConfidence);
            data.put("java_signal", javaSignal);
            
            String response = sendPost("/consensus", gson.toJson(data));
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            return parseConsensusResult(result);
            
        } catch (Exception e) {
            System.err.println("Consensus signal failed: " + e.getMessage());
            return ConsensusResult.unavailable("Error: " + e.getMessage());
        }
    }
    
    /**
     * Get FinGPT signal only (without consensus)
     */
    public SignalResult getFinGPTSignal(String symbol, List<Double> prices) {
        if (!isConnected) {
            return new SignalResult("WAIT", 0, 1.0, "Python server not connected");
        }
        
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("symbol", symbol);
            data.put("prices", prices);
            
            String response = sendPost("/fingpt/signal", gson.toJson(data));
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            String signal = result.get("signal").getAsString();
            double confidence = result.get("confidence").getAsDouble();
            double posSize = result.has("position_size") ? result.get("position_size").getAsDouble() : 1.0;
            String reason = result.has("reasoning") ? result.get("reasoning").getAsString() : "";
            
            return new SignalResult(signal, confidence, posSize, reason);
            
        } catch (Exception e) {
            return new SignalResult("WAIT", 0, 1.0, "FinGPT error: " + e.getMessage());
        }
    }
    
    /**
     * Record trade result for consensus weight adaptation
     */
    public boolean recordConsensusResult(boolean won, double profitLoss, String symbol,
                                          List<String> aisUsed) {
        if (!isConnected) return false;
        
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("won", won);
            data.put("profit", profitLoss);
            data.put("symbol", symbol);
            data.put("signals_used", aisUsed);
            
            String response = sendPost("/consensus/record", gson.toJson(data));
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            return result.get("success").getAsBoolean();
            
        } catch (Exception e) {
            System.err.println("Failed to record consensus result: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get Multi-AI system status
     */
    public Map<String, Object> getMultiAIStatus() {
        if (!isConnected) return Collections.emptyMap();
        
        try {
            String response = sendGet("/fingpt/status");
            JsonObject result = JsonParser.parseString(response).getAsJsonObject();
            
            Map<String, Object> status = new HashMap<>();
            
            // Parse FinGPT status
            if (result.has("fingpt")) {
                JsonObject fingpt = result.getAsJsonObject("fingpt");
                status.put("fingpt_available", fingpt.get("available").getAsBoolean());
                status.put("fingpt_mode", fingpt.get("mode").getAsString());
                status.put("fingpt_model", fingpt.get("model_type").getAsString());
            }
            
            // Parse consensus status
            if (result.has("consensus")) {
                JsonObject consensus = result.getAsJsonObject("consensus");
                status.put("consensus_min_agreement", consensus.get("min_agreement").getAsDouble());
                status.put("consensus_require_fingpt", consensus.get("require_fingpt").getAsBoolean());
            }
            
            return status;
            
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
    
    private ConsensusResult parseConsensusResult(JsonObject json) {
        ConsensusResult result = new ConsensusResult();
        
        result.available = json.has("success") && json.get("success").getAsBoolean();
        
        if (!result.available) {
            result.error = json.has("error") ? json.get("error").getAsString() : "Unknown error";
            result.consensusSignal = "WAIT";
            result.shouldTrade = false;
            return result;
        }
        
        result.consensusSignal = json.get("consensus_signal").getAsString();
        result.consensusConfidence = json.get("consensus_confidence").getAsDouble();
        result.agreementLevel = json.get("agreement_level").getAsString();
        result.shouldTrade = json.get("should_trade").getAsBoolean();
        result.reasoning = json.get("reasoning").getAsString();
        result.positionSize = json.has("position_size") ? json.get("position_size").getAsDouble() : 1.0;
        
        // Parse individual signals if available
        if (json.has("individual_signals")) {
            result.individualSignals = new HashMap<>();
            JsonObject signals = json.getAsJsonObject("individual_signals");
            for (String key : signals.keySet()) {
                result.individualSignals.put(key, signals.get(key).toString());
            }
        }
        
        // Parse AI agreement info
        if (json.has("ai_agreement")) {
            JsonObject agreement = json.getAsJsonObject("ai_agreement");
            result.totalAIs = agreement.get("total_ais").getAsInt();
            
            result.agreeingAIs = new ArrayList<>();
            if (agreement.has("agreeing_ais")) {
                for (JsonElement ai : agreement.getAsJsonArray("agreeing_ais")) {
                    result.agreeingAIs.add(ai.getAsString());
                }
            }
        }
        
        return result;
    }
    
    // ==================== RESULT CLASSES ====================
    
    public static class SignalResult {
        public String signal;      // "CALL", "PUT", "WAIT"
        public double confidence;  // 0-100
        public double positionSize; // multiplier
        public String reason;
        
        public SignalResult(String signal, double confidence, double positionSize, String reason) {
            this.signal = signal;
            this.confidence = confidence;
            this.positionSize = positionSize;
            this.reason = reason;
        }
        
        public boolean shouldTrade() {
            // Raised from 60 to 80 based on learning data
            return !signal.equals("WAIT") && confidence >= 80;
        }
        
        public boolean isCall() {
            return signal.equals("CALL");
        }
        
        public boolean isPut() {
            return signal.equals("PUT");
        }
    }
    
    public static class AnalysisResult {
        public boolean available = false;
        public String error;
        
        // Signal
        public String signal;
        public String signalType;
        public double confidence;
        
        // Risk
        public boolean canTrade;
        public double riskScore;
        public double positionSize;
        public String riskLevel;
        
        // Market
        public String marketCondition;
        public double trendStrength;
        public String volatilityLevel;
        public double momentum;
        
        // Indicators
        public double rsi;
        public String rsiSignal;
        public double macd;
        public String macdSignal;
        
        // Recommendation
        public String recommendation;
        public List<String> warnings;
        
        public static AnalysisResult unavailable(String error) {
            AnalysisResult result = new AnalysisResult();
            result.available = false;
            result.error = error;
            result.signal = "WAIT";
            result.canTrade = false;
            result.warnings = Collections.emptyList();
            return result;
        }
        
        public boolean shouldTrade() {
            // Raised from 60 to 80 based on learning data
            return available && canTrade && !signal.equals("WAIT") && confidence >= 80;
        }
    }
    
    /**
     * Result from Multi-AI Consensus endpoint
     */
    public static class ConsensusResult {
        public boolean available = false;
        public String error;
        
        // Consensus decision
        public String consensusSignal;      // "BUY", "SELL", "WAIT"
        public double consensusConfidence;  // 0-100
        public String agreementLevel;       // "2/3", "3/3", etc.
        public boolean shouldTrade;
        public String reasoning;
        public double positionSize;
        
        // Individual AI signals (for debugging/display)
        public Map<String, String> individualSignals;
        
        // Agreement info
        public List<String> agreeingAIs;
        public int totalAIs;
        
        public static ConsensusResult unavailable(String error) {
            ConsensusResult result = new ConsensusResult();
            result.available = false;
            result.error = error;
            result.consensusSignal = "WAIT";
            result.consensusConfidence = 0;
            result.shouldTrade = false;
            result.positionSize = 0;
            result.reasoning = error;
            result.individualSignals = new HashMap<>();
            result.agreeingAIs = new ArrayList<>();
            return result;
        }
        
        public boolean isCall() {
            return "BUY".equals(consensusSignal);
        }
        
        public boolean isPut() {
            return "SELL".equals(consensusSignal);
        }
        
        public boolean isWait() {
            return "WAIT".equals(consensusSignal) || !shouldTrade;
        }
        
        @Override
        public String toString() {
            return String.format("Consensus[%s %.0f%% %s trade=%b]", 
                consensusSignal, consensusConfidence, agreementLevel, shouldTrade);
        }
    }
}
