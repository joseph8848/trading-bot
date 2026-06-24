package com.elitebot.simulation;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * IMPROVED Price Simulator v2.0 - Creates REAL, Predictable Trends
 * 
 * Key improvements:
 * - Trend phases last 30-180 seconds (not 0.5 seconds)
 * - Larger price movements allowed (10% deviation)
 * - Clear uptrend/downtrend/consolidation phases
 * - Reduced random noise - trends dominate
 * - Realistic pullbacks within trends
 */
public class PriceSimulator {
    
    // Currency pairs, crypto, and commodities with their base prices and volatility
    private static final Map<String, double[]> CURRENCY_CONFIG = new HashMap<>();
    static {
        // Major Forex pairs (all available on Pocket Option)
        CURRENCY_CONFIG.put("EUR/USD", new double[]{1.0850, 0.0002});   // Base price, volatility
        CURRENCY_CONFIG.put("GBP/USD", new double[]{1.2650, 0.0003});
        CURRENCY_CONFIG.put("USD/JPY", new double[]{149.50, 0.05});
        CURRENCY_CONFIG.put("AUD/USD", new double[]{0.6580, 0.0002});
        CURRENCY_CONFIG.put("USD/CAD", new double[]{1.3520, 0.0002});
        CURRENCY_CONFIG.put("USD/CHF", new double[]{0.8850, 0.0002});
        CURRENCY_CONFIG.put("NZD/USD", new double[]{0.6050, 0.0002});
        // Cross pairs (available on Pocket Option)
        CURRENCY_CONFIG.put("EUR/GBP", new double[]{0.8580, 0.0002});
        CURRENCY_CONFIG.put("EUR/JPY", new double[]{162.20, 0.05});
        CURRENCY_CONFIG.put("GBP/JPY", new double[]{189.00, 0.08});
        CURRENCY_CONFIG.put("AUD/JPY", new double[]{98.40, 0.04});
        // Cryptocurrency (if available)
        CURRENCY_CONFIG.put("BTC/USD", new double[]{43500.0, 50.0});
        // Commodities
        CURRENCY_CONFIG.put("XAU/USD", new double[]{2050.0, 2.0});     // Gold
    }

    
    // Trend phases for each currency
    private enum TrendPhase { UPTREND, DOWNTREND, CONSOLIDATION }
    private final Map<String, TrendPhase> currentPhase = new ConcurrentHashMap<>();
    private final Map<String, Integer> phaseTicksRemaining = new ConcurrentHashMap<>();
    private final Map<String, Integer> pullbackCounter = new ConcurrentHashMap<>();
    
    private final Map<String, Double> currentPrices = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> priceHistory = new ConcurrentHashMap<>();
    private final Map<String, Double> trendStrength = new ConcurrentHashMap<>();
    
    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final List<Consumer<Map<String, Double>>> priceListeners = new CopyOnWriteArrayList<>();
    
    private ScheduledFuture<?> simulationTask;
    private boolean running = false;
    
    private static final int HISTORY_SIZE = 2000;
    
    // Phase duration: 60-360 ticks = 30-180 seconds at 500ms intervals
    private static final int MIN_PHASE_TICKS = 60;
    private static final int MAX_PHASE_TICKS = 360;
    
    public PriceSimulator() {
        initializePrices();
    }
    
    private void initializePrices() {
        for (Map.Entry<String, double[]> entry : CURRENCY_CONFIG.entrySet()) {
            String currency = entry.getKey();
            double basePrice = entry.getValue()[0];
            
            currentPrices.put(currency, basePrice);
            priceHistory.put(currency, new ArrayList<>(Collections.nCopies(HISTORY_SIZE, basePrice)));
            trendStrength.put(currency, 0.0);
            currentPhase.put(currency, TrendPhase.CONSOLIDATION);
            phaseTicksRemaining.put(currency, 0);
            pullbackCounter.put(currency, 0);
        }
    }
    
    public void start() {
        if (running) return;
        running = true;
        simulationTask = scheduler.scheduleAtFixedRate(this::updateAllPrices, 0, 500, TimeUnit.MILLISECONDS);
    }
    
    public void stop() {
        if (!running) return;
        running = false;
        if (simulationTask != null) {
            simulationTask.cancel(false);
        }
    }
    
    public void shutdown() {
        stop();
        scheduler.shutdown();
    }
    
    private void updateAllPrices() {
        try {
            for (String currency : CURRENCY_CONFIG.keySet()) {
                updatePrice(currency);
            }
            
            Map<String, Double> snapshot = new HashMap<>(currentPrices);
            for (Consumer<Map<String, Double>> listener : priceListeners) {
                listener.accept(snapshot);
            }
        } catch (Exception e) {
            System.err.println("Price simulation error: " + e.getMessage());
        }
    }
    
    private void updatePrice(String currency) {
        double[] config = CURRENCY_CONFIG.get(currency);
        double basePrice = config[0];
        double volatility = config[1];
        
        double currentPrice = currentPrices.get(currency);
        
        // Check if we need a new phase
        int ticksRemaining = phaseTicksRemaining.get(currency);
        if (ticksRemaining <= 0) {
            startNewPhase(currency, volatility);
        } else {
            phaseTicksRemaining.put(currency, ticksRemaining - 1);
        }
        
        TrendPhase phase = currentPhase.get(currency);
        double strength = trendStrength.get(currency);
        
        // Calculate price change based on phase
        double change = 0;
        double noise = random.nextGaussian() * volatility * 0.3;  // Reduced noise (30% of volatility)
        
        // Handle pullbacks (realistic counter-trend moves)
        int pullback = pullbackCounter.get(currency);
        boolean inPullback = pullback > 0;
        if (inPullback) {
            pullbackCounter.put(currency, pullback - 1);
        }
        
        switch (phase) {
            case UPTREND:
                if (inPullback) {
                    change = -strength * 0.5 + noise;  // Small pullback
                } else {
                    change = strength + noise;
                    // Random pullback chance (10%)
                    if (random.nextDouble() < 0.10) {
                        pullbackCounter.put(currency, 3 + random.nextInt(5));  // 3-7 ticks pullback
                    }
                }
                break;
                
            case DOWNTREND:
                if (inPullback) {
                    change = strength * 0.5 + noise;  // Small bounce
                } else {
                    change = -strength + noise;
                    if (random.nextDouble() < 0.10) {
                        pullbackCounter.put(currency, 3 + random.nextInt(5));
                    }
                }
                break;
                
            case CONSOLIDATION:
                // Range-bound with mean reversion
                double deviation = (currentPrice - basePrice) / basePrice;
                change = -deviation * volatility * 5 + noise * 2;  // Pull toward center
                break;
        }
        
        // Apply momentum for smoother trends
        List<Double> history = priceHistory.get(currency);
        if (history.size() >= 2) {
            double lastChange = history.get(history.size() - 1) - history.get(history.size() - 2);
            change = change * 0.6 + lastChange * 0.4;  // 40% momentum
        }
        
        double newPrice = currentPrice + change;
        
        // Allow 10% deviation from base (was 2% - way too small!)
        double maxDeviation = basePrice * 0.10;
        newPrice = Math.max(basePrice - maxDeviation, Math.min(basePrice + maxDeviation, newPrice));
        
        currentPrices.put(currency, newPrice);
        
        history.add(newPrice);
        if (history.size() > HISTORY_SIZE) {
            history.remove(0);
        }
    }
    
    /**
     * Start a new trend phase with random duration
     */
    private void startNewPhase(String currency, double volatility) {
        // Choose phase: 35% uptrend, 35% downtrend, 30% consolidation
        double roll = random.nextDouble();
        TrendPhase newPhase;
        if (roll < 0.35) {
            newPhase = TrendPhase.UPTREND;
        } else if (roll < 0.70) {
            newPhase = TrendPhase.DOWNTREND;
        } else {
            newPhase = TrendPhase.CONSOLIDATION;
        }
        
        currentPhase.put(currency, newPhase);
        
        // Duration: 60-360 ticks (30-180 seconds)
        int duration = MIN_PHASE_TICKS + random.nextInt(MAX_PHASE_TICKS - MIN_PHASE_TICKS);
        phaseTicksRemaining.put(currency, duration);
        
        // Trend strength: 0.5x to 1.5x volatility
        double strength = volatility * (0.5 + random.nextDouble());
        trendStrength.put(currency, strength);
        
        // Reset pullback
        pullbackCounter.put(currency, 0);
        
        System.out.println("📊 " + currency + " → " + newPhase + " for " + (duration/2) + "s");
    }
    
    public void addPriceListener(Consumer<Map<String, Double>> listener) {
        priceListeners.add(listener);
    }
    
    public void removePriceListener(Consumer<Map<String, Double>> listener) {
        priceListeners.remove(listener);
    }
    
    public double getCurrentPrice(String currency) {
        return currentPrices.getOrDefault(currency, 0.0);
    }
    
    public List<Double> getPriceHistory(String currency) {
        return new ArrayList<>(priceHistory.getOrDefault(currency, Collections.emptyList()));
    }
    
    public Map<String, Double> getAllCurrentPrices() {
        return new HashMap<>(currentPrices);
    }
    
    public Set<String> getAvailableCurrencies() {
        return CURRENCY_CONFIG.keySet();
    }
    
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Get the current trend phase for a currency (useful for debugging)
     */
    public String getCurrentTrendPhase(String currency) {
        TrendPhase phase = currentPhase.get(currency);
        return phase != null ? phase.name() : "UNKNOWN";
    }
    
    /**
     * Calculate volatility for a currency based on recent price movements
     */
    public double calculateVolatility(String currency) {
        List<Double> history = priceHistory.get(currency);
        if (history == null || history.size() < 10) return 0.0;
        
        List<Double> recent = history.subList(history.size() - 10, history.size());
        double mean = recent.stream().mapToDouble(d -> d).average().orElse(0);
        double variance = recent.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0);
        
        return Math.sqrt(variance);
    }
}
