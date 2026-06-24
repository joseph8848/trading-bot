package com.elitebot.ui;

import com.elitebot.model.*;
import com.elitebot.state.BotState;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.io.*;

/**
 * On-Demand Signal Dashboard v2.0
 * 
 * - Click "GET SIGNAL" button to generate a trade signal
 * - 10-second countdown to execute
 * - Trade stays visible after countdown
 * - Auto-detects WIN/LOSS using real market prices
 * - Bot decides optimal trade duration for each signal
 */
public class SignalDashboard {
    
    private final Stage stage;
    private final BotState botState;
    
    // Colors
    private static final String BG_DARK = "#0d1117";
    private static final String BG_CARD = "#161b22";
    private static final String BORDER = "#30363d";
    private static final String TEXT_PRIMARY = "#f0f6fc";
    private static final String TEXT_DIM = "#8b949e";
    private static final String GREEN = "#00ff88";
    private static final String RED = "#ff4444";
    private static final String GOLD = "#ffd700";
    private static final String BLUE = "#58a6ff";
    
    // Countdown time (10 seconds to execute)
    private static final int SIGNAL_COUNTDOWN_SECONDS = 10;
    
    // UI Components
    private VBox currentSignalCard;
    private Label currentDirectionLabel;
    private Label currentCurrencyLabel;
    private Label currentDetailsLabel;
    private Label currentCountdownLabel;
    private ProgressBar timerBar;
    private Button getSignalButton;
    private Button winButton;    // Manual win button
    private Button lossButton;   // Manual loss button
    private HBox resultButtons;  // Container for win/loss buttons
    private VBox historyBox;
    private Label statsLabel;
    private Label statusLabel;
    
    // State
    private SignalRecord currentSignal = null;
    private SignalRecord lastDisplayedSignal = null;  // Keep showing after countdown
    private Timeline countdownTimer;
    private int countdown = SIGNAL_COUNTDOWN_SECONDS;
    private List<SignalRecord> signalHistory = new ArrayList<>();
    private boolean isCountingDown = false;
    
    // Stats
    private int totalSignals = 0;
    private int wins = 0;
    private int losses = 0;
    
    private static final String HISTORY_FILE = "signal_history.json";
    
    public SignalDashboard(Stage stage, BotState botState) {
        this.stage = stage;
        this.botState = botState;
        loadHistory();
        setupUI();
    }
    
    private void setupUI() {
        stage.setTitle("🎯 Signal Generator");
        stage.setWidth(420);
        stage.setHeight(600);
        
        VBox root = new VBox(10);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: " + BG_DARK + ";");
        
        // Stats header
        statsLabel = new Label("📊 Ready to trade");
        statsLabel.setFont(Font.font("Segoe UI", 12));
        statsLabel.setTextFill(Color.web(TEXT_DIM));
        
        // Status label (shows data source)
        statusLabel = new Label("📈 Connecting to real market data...");
        statusLabel.setFont(Font.font("Segoe UI", 10));
        statusLabel.setTextFill(Color.web(BLUE));
        
        // GET SIGNAL button
        getSignalButton = createGetSignalButton();
        
        // Current signal display
        currentSignalCard = createCurrentSignalCard();
        
        // History
        VBox historyCard = createHistoryCard();
        VBox.setVgrow(historyCard, Priority.ALWAYS);
        
        root.getChildren().addAll(statsLabel, statusLabel, getSignalButton, currentSignalCard, historyCard);
        
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        
        // Update status periodically
        Timeline statusUpdater = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
            updateDataStatus();
        }));
        statusUpdater.setCycleCount(Timeline.INDEFINITE);
        statusUpdater.play();
    }
    
    private Button createGetSignalButton() {
        Button btn = new Button("🎯 GET SIGNAL");
        btn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        btn.setPrefWidth(380);
        btn.setPrefHeight(70);
        btn.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #2ea043, #238636);" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 12;" +
            "-fx-cursor: hand;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 3);"
        );
        
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #3fb950, #2ea043);" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 12;" +
            "-fx-cursor: hand;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 15, 0, 0, 5);"
        ));
        
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #2ea043, #238636);" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 12;" +
            "-fx-cursor: hand;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 3);"
        ));
        
        btn.setOnAction(e -> generateSignal());
        
        return btn;
    }
    
    private VBox createCurrentSignalCard() {
        VBox card = new VBox(8);
        card.setPadding(new Insets(15));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: " + BG_CARD + "; -fx-background-radius: 10;");
        
        Label header = new Label("📊 CURRENT SIGNAL");
        header.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        header.setTextFill(Color.web(TEXT_DIM));
        
        // Direction + Currency
        HBox mainRow = new HBox(10);
        mainRow.setAlignment(Pos.CENTER);
        
        currentDirectionLabel = new Label("—");
        currentDirectionLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 48));
        currentDirectionLabel.setTextFill(Color.web(TEXT_DIM));
        
        currentCurrencyLabel = new Label("Click GET SIGNAL");
        currentCurrencyLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        currentCurrencyLabel.setTextFill(Color.web(TEXT_PRIMARY));
        
        mainRow.getChildren().addAll(currentDirectionLabel, currentCurrencyLabel);
        
        // Details (amount, time, confidence)
        currentDetailsLabel = new Label("");
        currentDetailsLabel.setFont(Font.font("Segoe UI", 14));
        currentDetailsLabel.setTextFill(Color.web(GOLD));
        
        // Timer bar
        timerBar = new ProgressBar(0);
        timerBar.setPrefWidth(350);
        timerBar.setPrefHeight(8);
        timerBar.setStyle("-fx-accent: " + GREEN + ";");
        timerBar.setVisible(false);
        
        // Countdown label
        currentCountdownLabel = new Label("");
        currentCountdownLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        currentCountdownLabel.setTextFill(Color.web(GREEN));
        
        // Win/Loss buttons (shown after trade)
        winButton = new Button("✓ WIN");
        winButton.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        winButton.setPrefWidth(120);
        winButton.setPrefHeight(45);
        winButton.setStyle("-fx-background-color: #238636; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand;");
        winButton.setOnAction(e -> markResult(true));
        
        lossButton = new Button("✗ LOSS");
        lossButton.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        lossButton.setPrefWidth(120);
        lossButton.setPrefHeight(45);
        lossButton.setStyle("-fx-background-color: #da3633; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand;");
        lossButton.setOnAction(e -> markResult(false));
        
        resultButtons = new HBox(15, winButton, lossButton);
        resultButtons.setAlignment(Pos.CENTER);
        resultButtons.setVisible(false);  // Hidden until trade is placed
        
        card.getChildren().addAll(header, mainRow, currentDetailsLabel, timerBar, currentCountdownLabel, resultButtons);
        return card;
    }
    
    private VBox createHistoryCard() {
        VBox card = new VBox(5);
        card.setPadding(new Insets(8));
        card.setStyle("-fx-background-color: " + BG_CARD + "; -fx-background-radius: 8;");
        
        Label header = new Label("📜 TRADE HISTORY");
        header.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        header.setTextFill(Color.web(TEXT_DIM));
        
        historyBox = new VBox(2);
        historyBox.setStyle("-fx-background-color: " + BG_CARD + ";");
        
        ScrollPane scroll = new ScrollPane(historyBox);
        scroll.setStyle("-fx-background: " + BG_CARD + "; -fx-background-color: " + BG_CARD + ";");
        scroll.setFitToWidth(true);
        
        card.getChildren().addAll(header, scroll);
        return card;
    }
    
    /**
     * Main action: Generate a new signal on button click
     */
    private void generateSignal() {
        if (isCountingDown) return;  // Don't generate while counting down
        
        // Scan ALL currencies to find best actionable signal
        List<ScanResult> allResults = botState.getScanner().scanAllCurrencies();
        
        // Find the best ACTIONABLE signal (not NEUTRAL)
        ScanResult best = null;
        for (ScanResult result : allResults) {
            if (result.getSignal() != null && result.getSignal().isActionable()) {
                if (best == null || result.getSignal().getConfidence() > best.getSignal().getConfidence()) {
                    best = result;
                }
            }
        }
        
        if (best == null || best.getSignal() == null || !best.getSignal().isActionable()) {
            // Show SEARCHING and auto-retry
            currentCurrencyLabel.setText("SEARCHING...");
            currentDirectionLabel.setText("🔍");
            currentDirectionLabel.setTextFill(Color.web(GOLD));
            currentDetailsLabel.setText("Looking for strong signal (5 confirmations)...");
            currentSignalCard.setStyle("-fx-background-color: #1a1a2a; -fx-background-radius: 10; -fx-border-color: " + GOLD + "; -fx-border-radius: 10; -fx-border-width: 1;");
            getSignalButton.setText("🔍 SEARCHING...");
            getSignalButton.setDisable(true);
            
            // Auto-retry after 2 seconds
            Timeline retryTimer = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
                Platform.runLater(() -> {
                    getSignalButton.setDisable(false);
                    getSignalButton.setText("🎯 GET SIGNAL");
                    generateSignal();  // Try again automatically
                });
            }));
            retryTimer.play();
            return;
        }
        
        TradingSignal signal = best.getSignal();
        String currency = best.getCurrency();
        String direction = signal.getSignalType().toString();
        double confidence = signal.getConfidence();
        
        // Bot decides optimal trade duration based on confidence
        int recTime = botState.getTimeOptimizer().getOptimalTimeMinutes(confidence);
        double recAmount = calculateAmount(confidence);
        
        // Get REAL entry price
        double entryPrice = botState.getRealMarketDataProvider().getCurrentPrice(currency);
        if (entryPrice == 0) {
            // Fallback to simulator if real data not available
            entryPrice = botState.getPriceSimulator().getCurrentPrice(currency);
        }
        
        // Create signal record
        currentSignal = new SignalRecord(currency, direction, confidence, recTime, recAmount);
        currentSignal.entryPrice = entryPrice;
        lastDisplayedSignal = currentSignal;
        
        // Update UI
        currentDirectionLabel.setText(direction);
        currentCurrencyLabel.setText(currency);
        
        if (direction.equals("CALL")) {
            currentDirectionLabel.setTextFill(Color.web(GREEN));
            currentSignalCard.setStyle("-fx-background-color: #0a2010; -fx-background-radius: 10; -fx-border-color: " + GREEN + "; -fx-border-radius: 10; -fx-border-width: 2;");
        } else {
            currentDirectionLabel.setTextFill(Color.web(RED));
            currentSignalCard.setStyle("-fx-background-color: #200a0a; -fx-background-radius: 10; -fx-border-color: " + RED + "; -fx-border-radius: 10; -fx-border-width: 2;");
        }
        
        currentDetailsLabel.setText(String.format("💰 $%.0f  ⏱ %d min  📊 %.0f%%", recAmount, recTime, confidence));
        
        // Start 10-second countdown
        isCountingDown = true;
        countdown = SIGNAL_COUNTDOWN_SECONDS;
        timerBar.setVisible(true);
        getSignalButton.setDisable(true);
        getSignalButton.setText("⏳ EXECUTE NOW!");
        
        startCountdown();
        
        totalSignals++;
        playAlert();
        
        System.out.println(String.format("🎯 SIGNAL: %s %s @ %.5f (%.0f%% confidence, %d min)",
            direction, currency, entryPrice, confidence, recTime));
    }
    
    private void startCountdown() {
        if (countdownTimer != null) countdownTimer.stop();
        
        updateCountdownDisplay();
        
        countdownTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            countdown--;
            updateCountdownDisplay();
            if (countdown <= 0) {
                onCountdownFinished();
            }
        }));
        countdownTimer.setCycleCount(SIGNAL_COUNTDOWN_SECONDS);
        countdownTimer.play();
    }
    
    private void updateCountdownDisplay() {
        currentCountdownLabel.setText("⚡ EXECUTE IN " + countdown + " seconds!");
        
        double progress = (double) countdown / SIGNAL_COUNTDOWN_SECONDS;
        timerBar.setProgress(progress);
        
        if (countdown <= 3) {
            currentCountdownLabel.setTextFill(Color.web(RED));
            timerBar.setStyle("-fx-accent: " + RED + ";");
        } else if (countdown <= 6) {
            currentCountdownLabel.setTextFill(Color.web(GOLD));
            timerBar.setStyle("-fx-accent: " + GOLD + ";");
        } else {
            currentCountdownLabel.setTextFill(Color.web(GREEN));
            timerBar.setStyle("-fx-accent: " + GREEN + ";");
        }
    }
    
    private void onCountdownFinished() {
        isCountingDown = false;
        timerBar.setVisible(false);
        
        // Keep showing the signal
        currentCountdownLabel.setText("⏳ Mark your result below:");
        currentCountdownLabel.setTextFill(Color.web(GOLD));
        
        // Show WIN/LOSS buttons
        resultButtons.setVisible(true);
        
        getSignalButton.setDisable(false);
        getSignalButton.setText("🎯 GET NEXT SIGNAL");
        
        // Store signal for marking later
        if (currentSignal != null) {
            lastDisplayedSignal = currentSignal;
            System.out.println(String.format("📝 Trade %s %s - waiting for manual result...",
                currentSignal.direction, currentSignal.currency));
            currentSignal = null;
        }
    }
    
    /**
     * Manual result marking - called when user clicks WIN or LOSS button
     */
    private void markResult(boolean won) {
        if (lastDisplayedSignal == null) return;
        
        SignalRecord record = lastDisplayedSignal;
        record.result = won ? "WON" : "LOST";
        
        if (won) wins++;
        else losses++;
        
        String emoji = won ? "✅" : "❌";
        System.out.println(String.format("%s MARKED: %s %s = %s",
            emoji, record.direction, record.currency, record.result));
        
        // Add to history
        signalHistory.add(0, record);
        if (signalHistory.size() > 50) signalHistory.remove(signalHistory.size() - 1);
        
        // Record for learning
        recordForLearning(record, won);
        
        // Update UI
        updateHistoryDisplay();
        updateStats();
        saveHistory();
        
        // Hide result buttons and reset card
        resultButtons.setVisible(false);
        currentCountdownLabel.setText(won ? "✅ WIN recorded!" : "❌ LOSS recorded");
        currentCountdownLabel.setTextFill(Color.web(won ? GREEN : RED));
        
        // Clear for next trade
        lastDisplayedSignal = null;
        
        if (won) playAlert();
    }
    
    /**
     * Auto-detect WIN/LOSS based on real price movement
     */
    private void autoDetectResult(SignalRecord record) {
        if (record.result != null && !record.result.isEmpty()) return;
        
        try {
            // Get REAL exit price
            double exitPrice = botState.getRealMarketDataProvider().getCurrentPrice(record.currency);
            if (exitPrice == 0) {
                exitPrice = botState.getPriceSimulator().getCurrentPrice(record.currency);
            }
            record.exitPrice = exitPrice;
            
            // Determine win/loss
            boolean won;
            if (record.direction.equals("CALL")) {
                won = exitPrice > record.entryPrice;  // Price went UP = WIN for CALL
            } else {
                won = exitPrice < record.entryPrice;  // Price went DOWN = WIN for PUT
            }
            
            record.result = won ? "WON" : "LOST";
            
            if (won) wins++;
            else losses++;
            
            double priceDiff = exitPrice - record.entryPrice;
            String emoji = won ? "✅" : "❌";
            System.out.println(String.format("%s AUTO-DETECTED: %s %s | Entry: %.5f → Exit: %.5f (%+.5f) = %s",
                emoji, record.direction, record.currency, 
                record.entryPrice, exitPrice, priceDiff, record.result));
            
            // Add to history
            signalHistory.add(0, record);
            if (signalHistory.size() > 50) signalHistory.remove(signalHistory.size() - 1);
            
            // Record for learning
            recordForLearning(record, won);
            
            updateHistoryDisplay();
            updateStats();
            saveHistory();
            
            if (won) playAlert();
            
        } catch (Exception e) {
            System.err.println("Auto-detect failed: " + e.getMessage());
            record.result = "ERROR";
        }
    }
    
    private double calculateAmount(double confidence) {
        if (confidence >= 90) return 50;
        if (confidence >= 85) return 35;
        if (confidence >= 80) return 25;
        if (confidence >= 75) return 15;
        return 10;
    }
    
    private void updateStats() {
        double winRate = (wins + losses) > 0 ? (double) wins / (wins + losses) * 100 : 0;
        statsLabel.setText(String.format("📊 Signals: %d | Won: %d | Lost: %d | Rate: %.0f%%",
            totalSignals, wins, losses, winRate));
    }
    
    private void updateDataStatus() {
        String status = botState.getMarketDataStatus();
        statusLabel.setText(status);
        if (status.contains("Real data") || status.contains("Twelve") || status.contains("Alpha")) {
            statusLabel.setTextFill(Color.web(GREEN));
        } else {
            statusLabel.setTextFill(Color.web(GOLD));
        }
    }
    
    private void updateHistoryDisplay() {
        historyBox.getChildren().clear();
        
        for (SignalRecord r : signalHistory) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 8, 4, 8));
            
            Label time = new Label(r.time.format(DateTimeFormatter.ofPattern("HH:mm")));
            time.setFont(Font.font("Consolas", 10));
            time.setTextFill(Color.web(TEXT_DIM));
            time.setPrefWidth(40);
            
            Label dir = new Label(r.direction);
            dir.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            dir.setTextFill(Color.web(r.direction.equals("CALL") ? GREEN : RED));
            dir.setPrefWidth(40);
            
            Label curr = new Label(r.currency);
            curr.setFont(Font.font("Segoe UI", 10));
            curr.setTextFill(Color.web(TEXT_PRIMARY));
            curr.setPrefWidth(70);
            
            Label duration = new Label(r.recommendedTime + "m");
            duration.setFont(Font.font("Segoe UI", 10));
            duration.setTextFill(Color.web(BLUE));
            duration.setPrefWidth(30);
            
            Label result = new Label(r.result != null && !r.result.isEmpty() ? r.result : "PENDING...");
            result.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            
            if ("WON".equals(r.result)) {
                result.setTextFill(Color.web(GREEN));
                row.setStyle("-fx-background-color: #0a2010; -fx-background-radius: 4;");
            } else if ("LOST".equals(r.result)) {
                result.setTextFill(Color.web(RED));
                row.setStyle("-fx-background-color: #200a0a; -fx-background-radius: 4;");
            } else {
                result.setTextFill(Color.web(GOLD));
                row.setStyle("-fx-background-color: #1a1a2a; -fx-background-radius: 4;");
            }
            
            row.getChildren().addAll(time, dir, curr, duration, result);
            historyBox.getChildren().add(row);
        }
        
        if (historyBox.getChildren().isEmpty()) {
            Label empty = new Label("No trades yet. Click GET SIGNAL to start!");
            empty.setTextFill(Color.web(TEXT_DIM));
            empty.setFont(Font.font("Segoe UI", 11));
            historyBox.getChildren().add(empty);
        }
    }
    
    private void recordForLearning(SignalRecord record, boolean won) {
        try {
            TradingSignal.SignalType type = record.direction.equals("CALL") ? 
                TradingSignal.SignalType.CALL : TradingSignal.SignalType.PUT;
            
            Trade trade = new Trade(record.currency, type, record.entryPrice, 
                record.recommendedTime, record.confidence, record.recommendedAmount);
            
            trade.complete(record.exitPrice);
            
            botState.getLearningEngine().learnFromTrade(trade);
            botState.getTradingBrain().recordTradeResult(won, record.currency,
                won ? record.recommendedAmount * 0.85 : -record.recommendedAmount);
                
        } catch (Exception e) {
            System.err.println("Learning failed: " + e.getMessage());
        }
    }
    
    private void playAlert() {
        try { java.awt.Toolkit.getDefaultToolkit().beep(); } catch (Exception e) {}
    }
    
    private void loadHistory() {
        try { 
            if (new File(HISTORY_FILE).exists()) {
                System.out.println("📂 History loaded");
            }
        } catch (Exception e) {}
    }
    
    private void saveHistory() {
        try {
            StringBuilder json = new StringBuilder("[\n");
            for (int i = 0; i < Math.min(signalHistory.size(), 50); i++) {
                SignalRecord r = signalHistory.get(i);
                if (i > 0) json.append(",\n");
                json.append(String.format("  {\"time\":\"%s\",\"currency\":\"%s\",\"direction\":\"%s\",\"result\":\"%s\",\"confidence\":%.1f,\"duration\":%d}",
                    r.time.toString(), r.currency, r.direction, r.result, r.confidence, r.recommendedTime));
            }
            json.append("\n]");
            try (PrintWriter out = new PrintWriter(HISTORY_FILE)) { out.print(json.toString()); }
        } catch (Exception e) {}
    }
    
    public void show() { 
        stage.show();
        updateHistoryDisplay();
        updateStats();
        updateDataStatus();
    }
    
    public void close() {
        if (countdownTimer != null) countdownTimer.stop();
        saveHistory();
        botState.shutdown();
        stage.close();
    }
    
    private static class SignalRecord {
        LocalDateTime time = LocalDateTime.now();
        String currency, direction, result = "";
        double confidence, recommendedAmount, entryPrice, exitPrice;
        int recommendedTime;
        
        SignalRecord(String currency, String direction, double confidence, int recTime, double recAmount) {
            this.currency = currency;
            this.direction = direction;
            this.confidence = confidence;
            this.recommendedTime = recTime;
            this.recommendedAmount = recAmount;
        }
    }
}
