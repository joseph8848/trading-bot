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

import java.util.List;

/**
 * Main trading dashboard GUI.
 * Premium dark theme with real-time updates.
 */
public class TradingDashboard {
    
    private final Stage stage;
    private final BotState botState;
    
    // UI Components
    private Label priceLabel;
    private Label signalLabel;
    private Label confidenceLabel;
    private Label timeLabel;
    private Label currencyLabel;
    private Label winRateLabel;
    private Label statusLabel;
    private Label marketSummaryLabel;
    
    private Button startStopBtn;
    private Button tradeBtn;
    private ToggleButton pinBtn;
    private ToggleButton autoCurrencyBtn;
    private ToggleButton autoTimeBtn;
    
    private ComboBox<String> currencyCombo;
    private ComboBox<Integer> timeCombo;
    private TextField tradeAmountField;
    private TextField stopLossField;
    private TextField takeProfitField;
    private TextField minAmountField;
    private TextField maxAmountField;
    private ToggleButton autoDynamicAmountBtn;
    private ToggleButton autoTradeBtn;
    private ToggleButton recoveryModeBtn;
    private ToggleButton multiCurrencyBtn;
    private Label balanceLabel;
    private Label profitLossLabel;
    private Label learningLabel;
    private Label riskStatusLabel;
    private Label dynamicAmountStatusLabel;
    
    // Broker connection UI
    private Button brokerConnectBtn;
    private Label brokerStatusLabel;
    
    // Intelligence panel
    private Label marketStateLabel;
    private Label newsLabel;
    private Label bestHoursLabel;
    private Label bestOpportunityLabel;
    
    // AI Brain Status (clickable)
    private Label brainStatusLabel;
    
    private VBox tradeHistoryBox;
    private VBox scanResultsBox;
    
    // Trade Preview Panel - NEW
    private VBox tradePreviewPanel;
    private Label previewCurrencyLabel;
    private Label previewDirectionLabel;
    private Label previewAmountLabel;
    private Label previewDurationLabel;
    private Label countdownLabel;
    private Button executeNowBtn;
    private Button skipTradeBtn;
    private javafx.animation.Timeline countdownTimeline;
    private int countdownSeconds = 30;
    private boolean tradePreviewActive = false;
    private TradingSignal pendingSignal = null;
    private String pendingCurrency = null;
    
    // Trade Journal - Track manual trade results
    private VBox journalResultPanel;
    private Label resultTradeInfoLabel;
    private Button resultWinBtn;
    private Button resultLossBtn;
    private int journalWins = 0;
    private int journalLosses = 0;
    private int journalSkipped = 0;
    private Label journalStatsLabel;
    private java.util.List<JournalEntry> journalEntries = new java.util.ArrayList<>();
    private String lastExecutedCurrency = null;
    private String lastExecutedDirection = null;
    private double lastExecutedAmount = 0;
    
    // Journal Entry class
    private static class JournalEntry {
        String timestamp;
        String currency;
        String direction;
        double amount;
        String result; // "WIN", "LOSS", "SKIPPED"
        
        JournalEntry(String currency, String direction, double amount, String result) {
            this.timestamp = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            this.currency = currency;
            this.direction = direction;
            this.amount = amount;
            this.result = result;
        }
        
        @Override
        public String toString() {
            String icon = result.equals("WIN") ? "✅" : result.equals("LOSS") ? "❌" : "⏭";
            return String.format("%s %s %s %s $%.0f", icon, timestamp, currency, direction, amount);
        }
    }
    
    // Collapsible Settings Panel
    private ToggleButton settingsBtn;
    private VBox collapsibleSettings;
    private boolean settingsExpanded = false;
    
    // Colors
    private static final String BG_DARK = "#0d1117";
    private static final String BG_CARD = "#161b22";
    private static final String BORDER_COLOR = "#30363d";
    private static final String TEXT_PRIMARY = "#c9d1d9";
    private static final String TEXT_SECONDARY = "#8b949e";
    private static final String ACCENT_GREEN = "#238636";
    private static final String ACCENT_RED = "#da3633";
    private static final String ACCENT_BLUE = "#58a6ff";
    private static final String ACCENT_GOLD = "#d29922";
    
    public TradingDashboard(Stage stage) {
        this.stage = stage;
        this.botState = new BotState();
        
        setupUI();
        setupBindings();
    }
    
    private void setupUI() {
        // Window settings - COMPACT for side-by-side with browser
        stage.setTitle("⚡ Signal Assistant");
        stage.setWidth(420);   // Narrow to fit beside browser
        stage.setHeight(650);  // Shorter but still functional
        stage.setAlwaysOnTop(true);  // Keep on top while trading
        
        // Main layout
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG_DARK + ";");
        
        // Header
        root.setTop(createHeader());
        
        // Center content
        root.setCenter(createMainContent());
        
        // Scene
        Scene scene = new Scene(root);
        stage.setScene(scene);
    }
    
    private HBox createHeader() {
        HBox header = new HBox(10);
        header.setPadding(new Insets(10, 15, 10, 15));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: " + BG_CARD + "; -fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 0 0 1 0;");
        
        // Logo/Title - smaller for compact view
        Label title = new Label("⚡ SIGNALS");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        title.setTextFill(Color.web(ACCENT_BLUE));
        
        // Status indicator
        statusLabel = new Label("● STOPPED");
        statusLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        statusLabel.setTextFill(Color.web(TEXT_SECONDARY));
        
        // Hidden broker status (still needed for internal tracking)
        brokerStatusLabel = new Label("");
        brokerStatusLabel.setVisible(false);
        brokerStatusLabel.setManaged(false);
        
        // Hidden broker connect button (not needed - extension auto-connects)
        brokerConnectBtn = new Button("");
        brokerConnectBtn.setVisible(false);
        brokerConnectBtn.setManaged(false);
        
        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Settings toggle button
        settingsBtn = new ToggleButton("⚙️");
        settingsBtn.setSelected(false);
        settingsBtn.setStyle(getButtonStyle(false));
        settingsBtn.setTooltip(new Tooltip("Show/hide advanced settings"));
        settingsBtn.setOnAction(e -> toggleSettings());
        
        // Pin button
        pinBtn = new ToggleButton("📌");
        pinBtn.setSelected(true);  // DEFAULT: pinned on top
        pinBtn.setStyle(getButtonStyle(true));
        pinBtn.setTooltip(new Tooltip("Pin window on top"));
        pinBtn.setOnAction(e -> {
            stage.setAlwaysOnTop(pinBtn.isSelected());
            pinBtn.setStyle(getButtonStyle(pinBtn.isSelected()));
        });
        
        // Start/Stop button
        startStopBtn = new Button("▶ START");
        startStopBtn.setStyle(getAccentButtonStyle(ACCENT_GREEN));
        startStopBtn.setPrefWidth(90);
        startStopBtn.setOnAction(e -> toggleBot());
        
        header.getChildren().addAll(title, statusLabel, spacer, settingsBtn, pinBtn, startStopBtn);
        return header;
    }
    
    /**
     * Toggle the collapsible settings panel.
     */
    private void toggleSettings() {
        settingsExpanded = settingsBtn.isSelected();
        collapsibleSettings.setVisible(settingsExpanded);
        collapsibleSettings.setManaged(settingsExpanded);
        settingsBtn.setStyle(getButtonStyle(settingsExpanded));
        
        // Adjust window height based on settings visibility
        if (settingsExpanded) {
            stage.setHeight(800);  // Taller when settings visible
        } else {
            stage.setHeight(650);  // Compact when settings hidden
        }
    }
    
    private VBox createMainContent() {
        VBox content = new VBox(10);  // Reduced spacing
        content.setPadding(new Insets(10));  // Reduced padding
        
        // COMPACT: Price + Signal side by side, stats removed from here
        HBox topRow = new HBox(10);
        topRow.getChildren().addAll(createPriceCard(), createSignalCard());
        HBox.setHgrow(topRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(topRow.getChildren().get(1), Priority.ALWAYS);
        
        // COMPACT: Currency and Time on same row
        HBox controlsRow = new HBox(10);
        controlsRow.getChildren().addAll(createCurrencyControl(), createTimeControl());
        HBox.setHgrow(controlsRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(controlsRow.getChildren().get(1), Priority.ALWAYS);
        
        // Trade controls row
        HBox tradeRow = new HBox(10);
        tradeRow.setAlignment(Pos.CENTER);
        
        // Auto Trade toggle
        autoTradeBtn = new ToggleButton("🤖 AUTO");
        autoTradeBtn.setSelected(false);
        autoTradeBtn.setStyle(getToggleStyle(false));
        autoTradeBtn.setPrefWidth(100);
        autoTradeBtn.setOnAction(e -> {
            botState.setAutoTradeEnabled(autoTradeBtn.isSelected());
            autoTradeBtn.setStyle(getToggleStyle(autoTradeBtn.isSelected()));
            tradeBtn.setDisable(autoTradeBtn.isSelected());
        });
        
        // Manual Trade button (stays visible)
        tradeBtn = new Button("🚀 TRADE");
        tradeBtn.setStyle(getAccentButtonStyle(ACCENT_BLUE));
        tradeBtn.setPrefWidth(100);
        tradeBtn.setPrefHeight(35);
        tradeBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        tradeBtn.setDisable(true);
        tradeBtn.setOnAction(e -> executeTrade());
        
        // Main trade row - only AUTO and TRADE visible
        tradeRow.getChildren().addAll(autoTradeBtn, tradeBtn);
        HBox.setHgrow(tradeBtn, Priority.ALWAYS);
        
        // Trade Preview Panel - NEW: shows pending signal with countdown
        tradePreviewPanel = createTradePreviewPanel();
        tradePreviewPanel.setVisible(false);
        tradePreviewPanel.setManaged(false);
        
        // ===== COLLAPSIBLE SETTINGS SECTION =====
        collapsibleSettings = new VBox(10);
        collapsibleSettings.setVisible(false);
        collapsibleSettings.setManaged(false);
        collapsibleSettings.setPadding(new Insets(5, 0, 5, 0));
        
        // Advanced toggles row (hidden by default)
        HBox advancedRow = new HBox(10);
        advancedRow.setAlignment(Pos.CENTER_LEFT);
        
        // Recovery Mode toggle
        recoveryModeBtn = new ToggleButton("🔄 RECOVERY");
        recoveryModeBtn.setSelected(false);
        recoveryModeBtn.setStyle(getToggleStyle(false));
        recoveryModeBtn.setPrefWidth(100);
        recoveryModeBtn.setTooltip(new Tooltip("Double trade after loss to recover"));
        recoveryModeBtn.setOnAction(e -> {
            botState.setRecoveryModeEnabled(recoveryModeBtn.isSelected());
            recoveryModeBtn.setStyle(getToggleStyle(recoveryModeBtn.isSelected()));
        });
        
        // Multi-Currency Mode toggle
        multiCurrencyBtn = new ToggleButton("🔀 MULTI");
        multiCurrencyBtn.setSelected(false);
        multiCurrencyBtn.setStyle(getToggleStyle(false));
        multiCurrencyBtn.setPrefWidth(80);
        multiCurrencyBtn.setTooltip(new Tooltip("Trade multiple currencies simultaneously"));
        multiCurrencyBtn.setOnAction(e -> {
            botState.setMultiCurrencyMode(multiCurrencyBtn.isSelected());
            multiCurrencyBtn.setStyle(getToggleStyle(multiCurrencyBtn.isSelected()));
        });
        
        advancedRow.getChildren().addAll(recoveryModeBtn, multiCurrencyBtn);
        
        // Intelligence row (hidden by default)
        HBox intelligenceRow = new HBox(10);
        intelligenceRow.getChildren().addAll(createIntelligenceCard(), createBrainStatusCard(), createBestOpportunityCard());
        HBox.setHgrow(intelligenceRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(intelligenceRow.getChildren().get(1), Priority.ALWAYS);
        HBox.setHgrow(intelligenceRow.getChildren().get(2), Priority.ALWAYS);
        
        // Scanner only (hidden by default) - History moved out
        VBox scannerOnly = createScannerCard();
        VBox.setVgrow(scannerOnly, Priority.ALWAYS);
        
        collapsibleSettings.getChildren().addAll(advancedRow, intelligenceRow, scannerOnly);
        
        // Trade History - ALWAYS VISIBLE
        VBox historyCard = createHistoryCard();
        VBox.setVgrow(historyCard, Priority.ALWAYS);
        
        content.getChildren().addAll(topRow, controlsRow, tradeRow, tradePreviewPanel, historyCard, collapsibleSettings);
        return content;
    }
    
    /**
     * Creates the Trade Preview Panel - shows pending signal with countdown timer.
     * User must click EXECUTE or SKIP before the countdown expires.
     */
    private VBox createTradePreviewPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setAlignment(Pos.CENTER);
        panel.setStyle("-fx-background-color: linear-gradient(to right, #1a2332, #0d1117); " +
                      "-fx-background-radius: 12; " +
                      "-fx-border-color: " + ACCENT_GOLD + "; " +
                      "-fx-border-radius: 12; " +
                      "-fx-border-width: 2;");
        
        // Header with countdown
        HBox header = new HBox(20);
        header.setAlignment(Pos.CENTER);
        
        Label titleLabel = new Label("⚡ TRADE SIGNAL READY");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.web(ACCENT_GOLD));
        
        countdownLabel = new Label("30");
        countdownLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 36));
        countdownLabel.setTextFill(Color.web(TEXT_PRIMARY));
        countdownLabel.setStyle("-fx-background-color: " + BG_DARK + "; " +
                               "-fx-background-radius: 8; " +
                               "-fx-padding: 5 15;");
        
        Label secLabel = new Label("seconds");
        secLabel.setFont(Font.font("Segoe UI", 12));
        secLabel.setTextFill(Color.web(TEXT_SECONDARY));
        
        VBox timerBox = new VBox(2);
        timerBox.setAlignment(Pos.CENTER);
        timerBox.getChildren().addAll(countdownLabel, secLabel);
        
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        
        header.getChildren().addAll(titleLabel, headerSpacer, timerBox);
        
        // Signal details grid
        HBox detailsRow = new HBox(30);
        detailsRow.setAlignment(Pos.CENTER);
        
        // Currency
        VBox currencyBox = new VBox(5);
        currencyBox.setAlignment(Pos.CENTER);
        Label currLabel = new Label("CURRENCY");
        currLabel.setFont(Font.font("Segoe UI", 10));
        currLabel.setTextFill(Color.web(TEXT_SECONDARY));
        previewCurrencyLabel = new Label("EUR/USD");
        previewCurrencyLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        previewCurrencyLabel.setTextFill(Color.web(ACCENT_BLUE));
        currencyBox.getChildren().addAll(currLabel, previewCurrencyLabel);
        
        // Direction
        VBox directionBox = new VBox(5);
        directionBox.setAlignment(Pos.CENTER);
        Label dirLabel = new Label("DIRECTION");
        dirLabel.setFont(Font.font("Segoe UI", 10));
        dirLabel.setTextFill(Color.web(TEXT_SECONDARY));
        previewDirectionLabel = new Label("CALL ↑");
        previewDirectionLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        previewDirectionLabel.setTextFill(Color.web(ACCENT_GREEN));
        directionBox.getChildren().addAll(dirLabel, previewDirectionLabel);
        
        // Amount
        VBox amountBox = new VBox(5);
        amountBox.setAlignment(Pos.CENTER);
        Label amtLabel = new Label("AMOUNT");
        amtLabel.setFont(Font.font("Segoe UI", 10));
        amtLabel.setTextFill(Color.web(TEXT_SECONDARY));
        previewAmountLabel = new Label("$10");
        previewAmountLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        previewAmountLabel.setTextFill(Color.web(ACCENT_GOLD));
        amountBox.getChildren().addAll(amtLabel, previewAmountLabel);
        
        // Duration
        VBox durationBox = new VBox(5);
        durationBox.setAlignment(Pos.CENTER);
        Label durLabel = new Label("DURATION");
        durLabel.setFont(Font.font("Segoe UI", 10));
        durLabel.setTextFill(Color.web(TEXT_SECONDARY));
        previewDurationLabel = new Label("3 min");
        previewDurationLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        previewDurationLabel.setTextFill(Color.web("#9f7aea"));
        durationBox.getChildren().addAll(durLabel, previewDurationLabel);
        
        detailsRow.getChildren().addAll(currencyBox, directionBox, amountBox, durationBox);
        
        // Action buttons
        HBox buttonsRow = new HBox(20);
        buttonsRow.setAlignment(Pos.CENTER);
        
        executeNowBtn = new Button("✅ EXECUTE NOW");
        executeNowBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        executeNowBtn.setPrefWidth(200);
        executeNowBtn.setPrefHeight(50);
        executeNowBtn.setStyle("-fx-background-color: " + ACCENT_GREEN + "; " +
                              "-fx-text-fill: white; " +
                              "-fx-background-radius: 8; " +
                              "-fx-cursor: hand;");
        executeNowBtn.setOnAction(e -> executePreviewTrade());
        
        skipTradeBtn = new Button("⏭ SKIP");
        skipTradeBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        skipTradeBtn.setPrefWidth(120);
        skipTradeBtn.setPrefHeight(50);
        skipTradeBtn.setStyle("-fx-background-color: " + BG_CARD + "; " +
                             "-fx-text-fill: " + TEXT_SECONDARY + "; " +
                             "-fx-background-radius: 8; " +
                             "-fx-border-color: " + BORDER_COLOR + "; " +
                             "-fx-border-radius: 8;");
        skipTradeBtn.setOnAction(e -> skipPreviewTrade());
        
        buttonsRow.getChildren().addAll(executeNowBtn, skipTradeBtn);
        
        panel.getChildren().addAll(header, detailsRow, buttonsRow);
        return panel;
    }
    
    /**
     * Show trade preview with countdown timer.
     * Called when a new actionable signal is detected.
     */
    private void showTradePreview(String currency, TradingSignal signal, double amount, int durationMinutes) {
        pendingSignal = signal;
        pendingCurrency = currency;
        
        // Update preview labels
        previewCurrencyLabel.setText(currency);
        
        boolean isCall = signal.getSignalType() == TradingSignal.SignalType.CALL;
        previewDirectionLabel.setText(isCall ? "CALL ↑" : "PUT ↓");
        previewDirectionLabel.setTextFill(Color.web(isCall ? ACCENT_GREEN : ACCENT_RED));
        
        previewAmountLabel.setText(String.format("$%.0f", amount));
        previewDurationLabel.setText(durationMinutes + " min");
        
        // Reset and start countdown
        countdownSeconds = 30;
        countdownLabel.setText(String.valueOf(countdownSeconds));
        countdownLabel.setTextFill(Color.web(TEXT_PRIMARY));
        
        // Show the panel
        tradePreviewPanel.setVisible(true);
        tradePreviewPanel.setManaged(true);
        tradePreviewActive = true;
        
        // Start countdown timer
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
        
        countdownTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), event -> {
                countdownSeconds--;
                countdownLabel.setText(String.valueOf(countdownSeconds));
                
                // Change color when urgent
                if (countdownSeconds <= 10) {
                    countdownLabel.setTextFill(Color.web(ACCENT_RED));
                } else if (countdownSeconds <= 20) {
                    countdownLabel.setTextFill(Color.web(ACCENT_GOLD));
                }
                
                // Time's up - auto-skip
                if (countdownSeconds <= 0) {
                    skipPreviewTrade();
                }
            })
        );
        countdownTimeline.setCycleCount(30);
        countdownTimeline.play();
        
        // Play alert sound
        playAlertSound();
    }
    
    /**
     * Execute the trade shown in the preview panel.
     */
    private void executePreviewTrade() {
        if (!tradePreviewActive || pendingSignal == null) return;
        
        // Stop countdown
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
        
        // Save trade info for result recording
        lastExecutedCurrency = pendingCurrency;
        lastExecutedDirection = pendingSignal.getSignalType().toString();
        lastExecutedAmount = Double.parseDouble(previewAmountLabel.getText().replace("$", ""));
        
        // Hide preview panel
        hideTradePreview();
        
        // Show result panel for WIN/LOSS input
        showJournalResultPanel();
        
        // Execute the trade
        Trade trade = botState.executeTradeWithSignal(pendingCurrency, pendingSignal);
        if (trade != null) {
            System.out.println("✅ Trade executed from preview: " + trade);
        }
        
        pendingSignal = null;
        pendingCurrency = null;
    }
    
    /**
     * Skip the trade shown in the preview panel.
     */
    private void skipPreviewTrade() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
        
        // Record as skipped in journal
        if (pendingCurrency != null && pendingSignal != null) {
            journalSkipped++;
            JournalEntry entry = new JournalEntry(
                pendingCurrency,
                pendingSignal.getSignalType().toString(),
                Double.parseDouble(previewAmountLabel.getText().replace("$", "")),
                "SKIPPED"
            );
            journalEntries.add(0, entry);
            updateJournalStats();
            System.out.println("⏭ Trade skipped: " + entry);
        }
        
        hideTradePreview();
        pendingSignal = null;
        pendingCurrency = null;
    }
    
    /**
     * Hide the trade preview panel.
     */
    private void hideTradePreview() {
        tradePreviewPanel.setVisible(false);
        tradePreviewPanel.setManaged(false);
        tradePreviewActive = false;
    }
    
    /**
     * Show the journal result panel for WIN/LOSS input.
     */
    private void showJournalResultPanel() {
        if (journalResultPanel == null) {
            createJournalResultPanel();
        }
        
        resultTradeInfoLabel.setText(String.format("📊 %s %s $%.0f - Did you win?",
            lastExecutedCurrency, lastExecutedDirection, lastExecutedAmount));
        
        journalResultPanel.setVisible(true);
        journalResultPanel.setManaged(true);
    }
    
    /**
     * Create the journal result panel for WIN/LOSS buttons.
     */
    private void createJournalResultPanel() {
        journalResultPanel = new VBox(10);
        journalResultPanel.setPadding(new Insets(15));
        journalResultPanel.setAlignment(Pos.CENTER);
        journalResultPanel.setStyle("-fx-background-color: " + BG_CARD + "; " +
                                   "-fx-background-radius: 10; " +
                                   "-fx-border-color: " + ACCENT_BLUE + "; " +
                                   "-fx-border-radius: 10;");
        journalResultPanel.setVisible(false);
        journalResultPanel.setManaged(false);
        
        Label titleLabel = new Label("📝 RECORD RESULT");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        titleLabel.setTextFill(Color.web(ACCENT_BLUE));
        
        resultTradeInfoLabel = new Label("Trade info...");
        resultTradeInfoLabel.setFont(Font.font("Segoe UI", 12));
        resultTradeInfoLabel.setTextFill(Color.web(TEXT_PRIMARY));
        
        HBox buttons = new HBox(15);
        buttons.setAlignment(Pos.CENTER);
        
        resultWinBtn = new Button("✅ WIN");
        resultWinBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        resultWinBtn.setPrefWidth(100);
        resultWinBtn.setPrefHeight(40);
        resultWinBtn.setStyle("-fx-background-color: " + ACCENT_GREEN + "; " +
                             "-fx-text-fill: white; " +
                             "-fx-background-radius: 8;");
        resultWinBtn.setOnAction(e -> recordJournalResult(true));
        
        resultLossBtn = new Button("❌ LOSS");
        resultLossBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        resultLossBtn.setPrefWidth(100);
        resultLossBtn.setPrefHeight(40);
        resultLossBtn.setStyle("-fx-background-color: " + ACCENT_RED + "; " +
                              "-fx-text-fill: white; " +
                              "-fx-background-radius: 8;");
        resultLossBtn.setOnAction(e -> recordJournalResult(false));
        
        buttons.getChildren().addAll(resultWinBtn, resultLossBtn);
        
        // Stats label
        journalStatsLabel = new Label("📊 Session: 0 wins, 0 losses (0%)");
        journalStatsLabel.setFont(Font.font("Segoe UI", 11));
        journalStatsLabel.setTextFill(Color.web(TEXT_SECONDARY));
        
        journalResultPanel.getChildren().addAll(titleLabel, resultTradeInfoLabel, buttons, journalStatsLabel);
        
        // Add to the main layout (after trade preview panel)
        VBox parent = (VBox) tradePreviewPanel.getParent();
        int index = parent.getChildren().indexOf(tradePreviewPanel) + 1;
        parent.getChildren().add(index, journalResultPanel);
    }
    
    /**
     * Record a journal result (WIN or LOSS).
     */
    private void recordJournalResult(boolean won) {
        if (lastExecutedCurrency == null) return;
        
        if (won) {
            journalWins++;
        } else {
            journalLosses++;
        }
        
        JournalEntry entry = new JournalEntry(
            lastExecutedCurrency,
            lastExecutedDirection,
            lastExecutedAmount,
            won ? "WIN" : "LOSS"
        );
        journalEntries.add(0, entry);
        
        // Hide result panel
        journalResultPanel.setVisible(false);
        journalResultPanel.setManaged(false);
        
        // Update stats
        updateJournalStats();
        
        // Log
        System.out.println("📝 Journal recorded: " + entry);
        
        // Clear
        lastExecutedCurrency = null;
        lastExecutedDirection = null;
        lastExecutedAmount = 0;
    }
    
    /**
     * Update the journal stats display.
     */
    private void updateJournalStats() {
        int total = journalWins + journalLosses;
        double winRate = total > 0 ? (double) journalWins / total * 100 : 0;
        
        String statsText = String.format("📊 Session: %d wins, %d losses, %d skipped (%.0f%% win rate)",
            journalWins, journalLosses, journalSkipped, winRate);
        
        if (journalStatsLabel != null) {
            journalStatsLabel.setText(statsText);
            journalStatsLabel.setTextFill(Color.web(winRate >= 55 ? ACCENT_GREEN : 
                                                    winRate >= 45 ? ACCENT_GOLD : ACCENT_RED));
        }
    }
    
    /**
     * Play alert sound when new signal appears.
     */
    private void playAlertSound() {
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Exception e) {
            // Ignore if audio not available
        }
    }
    
    private VBox createPriceCard() {
        VBox card = createCard("PRICE");
        
        currencyLabel = new Label("EUR/USD");
        currencyLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        currencyLabel.setTextFill(Color.web(TEXT_SECONDARY));
        
        priceLabel = new Label("0.00000");
        priceLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 32));
        priceLabel.setTextFill(Color.web(TEXT_PRIMARY));
        
        card.getChildren().addAll(currencyLabel, priceLabel);
        return card;
    }
    
    private VBox createSignalCard() {
        VBox card = createCard("SIGNAL");
        
        signalLabel = new Label("WAITING");
        signalLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        signalLabel.setTextFill(Color.web(TEXT_SECONDARY));
        
        confidenceLabel = new Label("Confidence: --");
        confidenceLabel.setFont(Font.font("Segoe UI", 14));
        confidenceLabel.setTextFill(Color.web(TEXT_SECONDARY));
        
        card.getChildren().addAll(signalLabel, confidenceLabel);
        return card;
    }
    
    private VBox createStatsCard() {
        VBox card = createCard("STATS");
        
        winRateLabel = new Label("Win Rate: 0%");
        winRateLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        winRateLabel.setTextFill(Color.web(ACCENT_GOLD));
        
        marketSummaryLabel = new Label("Market: --");
        marketSummaryLabel.setFont(Font.font("Segoe UI", 12));
        marketSummaryLabel.setTextFill(Color.web(TEXT_SECONDARY));
        
        card.getChildren().addAll(winRateLabel, marketSummaryLabel);
        return card;
    }
    
    private VBox createCurrencyControl() {
        VBox card = createCard("CURRENCY");
        
        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER_LEFT);
        
        autoCurrencyBtn = new ToggleButton("AUTO");
        autoCurrencyBtn.setSelected(true);
        autoCurrencyBtn.setStyle(getToggleStyle(true));
        autoCurrencyBtn.setOnAction(e -> {
            botState.setAutoCurrencyEnabled(autoCurrencyBtn.isSelected());
            currencyCombo.setDisable(autoCurrencyBtn.isSelected());
            autoCurrencyBtn.setStyle(getToggleStyle(autoCurrencyBtn.isSelected()));
        });
        
        currencyCombo = new ComboBox<>();
        currencyCombo.getItems().addAll(botState.getAvailableCurrencies());
        currencyCombo.setValue("EUR/USD");
        currencyCombo.setStyle(getComboStyle());
        currencyCombo.setDisable(true);  // Disabled when AUTO is on
        currencyCombo.setOnAction(e -> botState.setSelectedCurrency(currencyCombo.getValue()));
        
        controls.getChildren().addAll(autoCurrencyBtn, currencyCombo);
        card.getChildren().add(controls);
        return card;
    }
    
    private VBox createTimeControl() {
        VBox card = createCard("TIME MARGIN");
        
        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER_LEFT);
        
        autoTimeBtn = new ToggleButton("AUTO");
        autoTimeBtn.setSelected(true);
        autoTimeBtn.setStyle(getToggleStyle(true));
        autoTimeBtn.setOnAction(e -> {
            botState.setAutoTimeEnabled(autoTimeBtn.isSelected());
            timeCombo.setDisable(autoTimeBtn.isSelected());
            autoTimeBtn.setStyle(getToggleStyle(autoTimeBtn.isSelected()));
        });
        
        timeCombo = new ComboBox<>();
        timeCombo.getItems().addAll(1, 3, 5);
        timeCombo.setValue(3);
        timeCombo.setStyle(getComboStyle());
        timeCombo.setDisable(true);
        timeCombo.setOnAction(e -> botState.setSelectedTimeMinutes(timeCombo.getValue()));
        
        timeLabel = new Label("Recommended: --");
        timeLabel.setFont(Font.font("Segoe UI", 11));
        timeLabel.setTextFill(Color.web(TEXT_SECONDARY));
        
        controls.getChildren().addAll(autoTimeBtn, timeCombo);
        card.getChildren().addAll(controls, timeLabel);
        return card;
    }
    
    private VBox createTradeAmountControl() {
        VBox card = createCard("💰 TRADE SIZING");
        card.setSpacing(8);
        
        // Row 1: Smart Size toggle + Base amount
        HBox row1 = new HBox(8);
        row1.setAlignment(Pos.CENTER_LEFT);
        
        autoDynamicAmountBtn = new ToggleButton("🧠 SMART");
        autoDynamicAmountBtn.setSelected(true);
        autoDynamicAmountBtn.setStyle(getToggleStyle(true));
        autoDynamicAmountBtn.setPrefWidth(85);
        autoDynamicAmountBtn.setTooltip(new Tooltip("AI decides trade size"));
        autoDynamicAmountBtn.setOnAction(e -> {
            botState.setAutoDynamicAmountEnabled(autoDynamicAmountBtn.isSelected());
            autoDynamicAmountBtn.setStyle(getToggleStyle(autoDynamicAmountBtn.isSelected()));
            tradeAmountField.setDisable(autoDynamicAmountBtn.isSelected());
        });
        
        Label baseLabel = new Label("Base:$");
        baseLabel.setFont(Font.font("Segoe UI", 10));
        baseLabel.setTextFill(Color.web(TEXT_SECONDARY));
        
        tradeAmountField = new TextField("15");
        tradeAmountField.setPrefWidth(45);
        tradeAmountField.setStyle(getInputStyle());
        tradeAmountField.setDisable(true);
        tradeAmountField.textProperty().addListener((obs, oldVal, newVal) -> {
            try { botState.setTradeAmount(Double.parseDouble(newVal)); } catch (Exception e) {}
        });
        
        Label minLabel = new Label("Min:$");
        minLabel.setFont(Font.font("Segoe UI", 10));
        minLabel.setTextFill(Color.web(ACCENT_BLUE));
        
        minAmountField = new TextField("5");
        minAmountField.setPrefWidth(40);
        minAmountField.setStyle(getInputStyle());
        minAmountField.textProperty().addListener((obs, oldVal, newVal) -> {
            try { botState.setMinTradeAmount(Double.parseDouble(newVal)); } catch (Exception e) {}
        });
        
        Label maxLabel = new Label("Max:$");
        maxLabel.setFont(Font.font("Segoe UI", 10));
        maxLabel.setTextFill(Color.web(ACCENT_GOLD));
        
        maxAmountField = new TextField("50");
        maxAmountField.setPrefWidth(40);
        maxAmountField.setStyle(getInputStyle());
        maxAmountField.textProperty().addListener((obs, oldVal, newVal) -> {
            try { botState.setMaxTradeAmount(Double.parseDouble(newVal)); } catch (Exception e) {}
        });
        
        row1.getChildren().addAll(autoDynamicAmountBtn, baseLabel, tradeAmountField, 
                                   minLabel, minAmountField, maxLabel, maxAmountField);
        
        // Row 2: Status label
        dynamicAmountStatusLabel = new Label("🎯 Smart: $5-$50");
        dynamicAmountStatusLabel.setFont(Font.font("Segoe UI", 9));
        dynamicAmountStatusLabel.setTextFill(Color.web(ACCENT_BLUE));
        
        // Row 3: Balance + P/L
        HBox row3 = new HBox(10);
        row3.setAlignment(Pos.CENTER_LEFT);
        
        balanceLabel = new Label("💵 $1,000");
        balanceLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        balanceLabel.setTextFill(Color.web(ACCENT_GREEN));
        
        profitLossLabel = new Label("P/L: $0");
        profitLossLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        profitLossLabel.setTextFill(Color.web(TEXT_SECONDARY));
        
        row3.getChildren().addAll(balanceLabel, profitLossLabel);
        
        // Risk status
        riskStatusLabel = new Label("");
        riskStatusLabel.setFont(Font.font("Segoe UI", 9));
        riskStatusLabel.setTextFill(Color.web(ACCENT_GOLD));
        
        card.getChildren().addAll(row1, dynamicAmountStatusLabel, row3, riskStatusLabel);
        return card;
    }
    
    private VBox createStopLossControl() {
        VBox card = createCard("🛡️ RISK LIMITS");
        card.setSpacing(8);
        
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        
        Label slLabel = new Label("SL:$");
        slLabel.setFont(Font.font("Segoe UI", 10));
        slLabel.setTextFill(Color.web(ACCENT_RED));
        
        stopLossField = new TextField("100");
        stopLossField.setPrefWidth(50);
        stopLossField.setStyle(getInputStyle());
        stopLossField.textProperty().addListener((obs, oldVal, newVal) -> {
            try { botState.setStopLoss(Double.parseDouble(newVal)); } catch (Exception e) {}
        });
        
        Label tpLabel = new Label("TP:$");
        tpLabel.setFont(Font.font("Segoe UI", 10));
        tpLabel.setTextFill(Color.web(ACCENT_GREEN));
        
        takeProfitField = new TextField("200");
        takeProfitField.setPrefWidth(50);
        takeProfitField.setStyle(getInputStyle());
        takeProfitField.textProperty().addListener((obs, oldVal, newVal) -> {
            try { botState.setTakeProfit(Double.parseDouble(newVal)); } catch (Exception e) {}
        });
        
        row.getChildren().addAll(slLabel, stopLossField, tpLabel, takeProfitField);
        
        Label hint = new Label("Stop Loss / Take Profit");
        hint.setFont(Font.font("Segoe UI", 9));
        hint.setTextFill(Color.web(TEXT_SECONDARY));
        
        card.getChildren().addAll(row, hint);
        return card;
    }
    
    private String getInputStyle() {
        return "-fx-background-color: " + BG_DARK + ";" +
               "-fx-text-fill: " + TEXT_PRIMARY + ";" +
               "-fx-border-color: " + BORDER_COLOR + ";" +
               "-fx-border-radius: 3;" +
               "-fx-background-radius: 3;" +
               "-fx-font-size: 12;";
    }
    
    private VBox createIntelligenceCard() {
        VBox card = createCard("🧠 MARKET INTELLIGENCE");
        
        // Market State
        marketStateLabel = new Label("🟡 Analyzing market...");
        marketStateLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        marketStateLabel.setTextFill(Color.web(ACCENT_GOLD));
        
        // News
        newsLabel = new Label("📰 Checking news...");
        newsLabel.setFont(Font.font("Segoe UI", 11));
        newsLabel.setTextFill(Color.web(TEXT_SECONDARY));
        newsLabel.setWrapText(true);
        
        // Best Hours
        bestHoursLabel = new Label("⏰ Best hours: Learning...");
        bestHoursLabel.setFont(Font.font("Segoe UI", 11));
        bestHoursLabel.setTextFill(Color.web(TEXT_SECONDARY));
        
        card.getChildren().addAll(marketStateLabel, newsLabel, bestHoursLabel);
        return card;
    }
    
    private VBox createBrainStatusCard() {
        VBox card = createCard("🧠 AI BRAIN STATUS");
        card.setStyle(card.getStyle() + "-fx-cursor: hand;");
        
        brainStatusLabel = new Label("⏳ Brain initializing...");
        brainStatusLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        brainStatusLabel.setTextFill(Color.web(ACCENT_BLUE));
        brainStatusLabel.setWrapText(true);
        
        Label clickHint = new Label("📊 Click for full report");
        clickHint.setFont(Font.font("Segoe UI", 10));
        clickHint.setTextFill(Color.web(TEXT_SECONDARY));
        
        // Make the card clickable
        card.setOnMouseClicked(e -> showBrainReport());
        card.setOnMouseEntered(e -> card.setStyle(card.getStyle() + "-fx-background-color: #1f2937;"));
        card.setOnMouseExited(e -> card.setStyle(card.getStyle().replace("-fx-background-color: #1f2937;", "")));
        
        card.getChildren().addAll(brainStatusLabel, clickHint);
        return card;
    }
    
    private void showBrainReport() {
        // Create a popup dialog with full brain report
        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.setTitle("🧠 AI Brain Report");
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: " + BG_DARK + ";");
        
        // Title
        Label title = new Label("🧠 ULTIMATE TRADING BRAIN REPORT");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web(ACCENT_BLUE));
        
        // Get brain data
        var brain = botState.getTradingBrain();
        
        // Stats section
        StringBuilder stats = new StringBuilder();
        stats.append("═══════════════════════════════════════\n");
        stats.append("📊 PERFORMANCE\n");
        stats.append("───────────────────────────────────────\n");
        stats.append(String.format("Total Trades: %d\n", brain.getTotalBrainTrades()));
        stats.append(String.format("Win Rate: %.1f%%\n", brain.getTotalBrainTrades() > 0 ? 
            (double)brain.getTotalBrainWins()/brain.getTotalBrainTrades()*100 : 0));
        stats.append(String.format("Total Profit: $%.2f\n", brain.getTotalBrainProfit()));
        stats.append(String.format("Current Drawdown: %.1f%%\n", brain.getCurrentDrawdown()));
        stats.append(String.format("Win Streak: %d | Loss Streak: %d\n", 
            brain.getConsecutiveWins(), brain.getConsecutiveLosses()));
        stats.append("\n═══════════════════════════════════════\n");
        stats.append("🔄 CURRENT STATE\n");
        stats.append("───────────────────────────────────────\n");
        stats.append(String.format("Decision: %s\n", brain.getCurrentDecision()));
        stats.append(String.format("Confidence: %.0f%%\n", brain.getDecisionConfidence()));
        stats.append(String.format("Market Regime: %s\n", brain.getCurrentRegime()));
        stats.append(String.format("Position Size: %.0f%%\n", brain.getRecommendedSizeMultiplier() * 100));
        stats.append(String.format("Reason: %s\n", brain.getDecisionReason()));
        stats.append("\n═══════════════════════════════════════\n");
        stats.append("📋 RECENT BRAIN ACTIVITY\n");
        stats.append("───────────────────────────────────────\n");
        for (String log : brain.getBrainLog()) {
            if (brain.getBrainLog().indexOf(log) >= brain.getBrainLog().size() - 10) {
                stats.append(log + "\n");
            }
        }
        stats.append("\n═══════════════════════════════════════\n");
        stats.append("💡 DISCOVERIES & LEARNING\n");
        stats.append("───────────────────────────────────────\n");
        stats.append("• Adapts strategy per market regime\n");
        stats.append("• Learns best trading hours\n");
        stats.append("• Tracks currency performance\n");
        stats.append("• Self-adjusts thresholds\n");
        
        TextArea reportArea = new TextArea(stats.toString());
        reportArea.setEditable(false);
        reportArea.setFont(Font.font("Consolas", 12));
        reportArea.setStyle("-fx-control-inner-background: " + BG_CARD + "; " +
            "-fx-text-fill: " + TEXT_PRIMARY + ";");
        reportArea.setPrefHeight(400);
        reportArea.setPrefWidth(500);
        
        Button closeBtn = new Button("Close");
        closeBtn.setStyle("-fx-background-color: " + ACCENT_BLUE + "; -fx-text-fill: white;");
        closeBtn.setOnAction(e -> dialog.close());
        
        content.getChildren().addAll(title, reportArea, closeBtn);
        
        Scene scene = new Scene(content);
        dialog.setScene(scene);
        dialog.show();
    }
    
    private VBox createBestOpportunityCard() {
        VBox card = createCard("🎯 BEST OPPORTUNITY");
        
        bestOpportunityLabel = new Label("Scanning currencies...");
        bestOpportunityLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        bestOpportunityLabel.setTextFill(Color.web(ACCENT_GREEN));
        bestOpportunityLabel.setWrapText(true);
        
        card.getChildren().add(bestOpportunityLabel);
        return card;
    }
    
    private VBox createScannerCard() {
        VBox card = createCard("CURRENCY SCANNER");
        card.setPrefHeight(200);
        
        scanResultsBox = new VBox(5);
        ScrollPane scroll = new ScrollPane(scanResultsBox);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        
        card.getChildren().add(scroll);
        return card;
    }
    
    private VBox createHistoryCard() {
        VBox card = createCard("TRADE HISTORY");
        card.setPrefHeight(200);
        
        tradeHistoryBox = new VBox(5);
        ScrollPane scroll = new ScrollPane(tradeHistoryBox);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        
        card.getChildren().add(scroll);
        return card;
    }
    
    private VBox createCard(String title) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: " + BG_CARD + "; -fx-background-radius: 8; -fx-border-color: " + BORDER_COLOR + "; -fx-border-radius: 8;");
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        titleLabel.setTextFill(Color.web(TEXT_SECONDARY));
        
        card.getChildren().add(titleLabel);
        return card;
    }
    
    private void setupBindings() {
        botState.addStateListener(state -> Platform.runLater(() -> updateUI()));
        
        // Register signal preview callback - shows trade preview when signal is ready
        botState.setSignalPreviewCallback(data -> {
            Platform.runLater(() -> {
                // Only show if not already showing a preview and bot is running
                if (!tradePreviewActive && botState.isBotRunning()) {
                    showTradePreview(data.currency, data.signal, data.amount, data.duration);
                }
            });
        });
    }
    
    private void updateUI() {
        // Status
        if (botState.isBotRunning()) {
            statusLabel.setText("● RUNNING");
            statusLabel.setTextFill(Color.web(ACCENT_GREEN));
            startStopBtn.setText("⏹ STOP");
            startStopBtn.setStyle(getAccentButtonStyle(ACCENT_RED));
        } else {
            statusLabel.setText("● STOPPED");
            statusLabel.setTextFill(Color.web(TEXT_SECONDARY));
            startStopBtn.setText("▶ START");
            startStopBtn.setStyle(getAccentButtonStyle(ACCENT_GREEN));
        }
        
        // Currency and Price
        String currency = botState.getActiveCurrency();
        currencyLabel.setText(currency);
        
        double price = botState.getCurrentPrice();
        String format = currency.contains("JPY") ? "%.3f" : 
                       currency.contains("BTC") || currency.contains("ETH") ? "%.2f" : "%.5f";
        priceLabel.setText(String.format(format, price));
        
        // Signal
        TradingSignal signal = botState.getCurrentSignal();
        if (signal != null) {
            signalLabel.setText(signal.getSignalType().toString());
            signalLabel.setTextFill(Color.web(
                signal.getSignalType() == TradingSignal.SignalType.CALL ? ACCENT_GREEN :
                signal.getSignalType() == TradingSignal.SignalType.PUT ? ACCENT_RED : TEXT_SECONDARY
            ));
            confidenceLabel.setText(String.format("Confidence: %.1f%% (%s)", 
                signal.getConfidence(), signal.getStrengthLabel()));
            
            tradeBtn.setDisable(!signal.isActionable() || !botState.isBotRunning());
        } else {
            signalLabel.setText("WAITING");
            signalLabel.setTextFill(Color.web(TEXT_SECONDARY));
            confidenceLabel.setText("Confidence: --");
            tradeBtn.setDisable(true);
        }
        
        // Time recommendation
        timeLabel.setText(botState.getTimeRecommendation());
        
        // Stats
        winRateLabel.setText(String.format("Win Rate: %.1f%% (%d/%d)", 
            botState.getWinRate(), botState.getWinCount(), botState.getTotalTrades()));
        marketSummaryLabel.setText(botState.getScanner().getMarketSummary());
        
        // Balance and P/L
        // Show broker balance if connected to Pocket Option browser
        double displayBalance = botState.getBalance();  // Default: simulation balance
        String balancePrefix = "💵";
        
        if (botState.isPocketOptionEnabled()) {
            var pythonClient = botState.getTradingBrain().getPythonClient();
            if (pythonClient != null && pythonClient.isConnected()) {
                // Get balance from browser status - this is the REAL broker balance
                var browserStatus = pythonClient.getBrowserStatus();
                if (browserStatus.browserOpen && browserStatus.loggedIn && browserStatus.balance > 0) {
                    displayBalance = browserStatus.balance;
                    balancePrefix = browserStatus.demoMode ? "🎯 DEMO" : "💰 REAL";
                }
            }
        }
        
        balanceLabel.setText(String.format("%s $%.2f", balancePrefix, displayBalance));
        balanceLabel.setTextFill(Color.web(displayBalance >= 1000 ? ACCENT_GREEN : ACCENT_RED));
        
        double pl = botState.getProfitLoss();
        profitLossLabel.setText(String.format("P/L: %s$%.2f", pl >= 0 ? "+" : "", pl));
        profitLossLabel.setTextFill(Color.web(pl >= 0 ? ACCENT_GREEN : ACCENT_RED));
        
        // Risk status
        if (botState.isStoppedByRisk()) {
            riskStatusLabel.setText(botState.getStopReason());
            riskStatusLabel.setTextFill(Color.web(botState.getStopReason().contains("PROFIT") ? ACCENT_GREEN : ACCENT_RED));
        } else {
            riskStatusLabel.setText(String.format("🧠 AI Learning: %d trades analyzed", 
                botState.getLearningEngine().getTotalHistoricalTrades()));
            riskStatusLabel.setTextFill(Color.web(ACCENT_BLUE));
        }
        
        // Market Intelligence
        marketStateLabel.setText(botState.getMarketIntelligence().getStatusMessage());
        String marketState = botState.getMarketIntelligence().getCurrentState().name();
        marketStateLabel.setTextFill(Color.web(
            marketState.equals("EXCELLENT") || marketState.equals("GOOD") ? ACCENT_GREEN :
            marketState.equals("NEUTRAL") ? ACCENT_GOLD :
            ACCENT_RED
        ));
        
        // News
        newsLabel.setText(botState.getNewsAnalyzer().getStatusMessage());
        
        // Best Hours
        bestHoursLabel.setText("⏰ Best hours: " + botState.getMarketIntelligence().getBestTradingHours());
        
        // Brain Status
        var brain = botState.getTradingBrain();
        brainStatusLabel.setText(String.format("%s | %s | %.0f%% size | %d trades",
            brain.getCurrentDecision(),
            brain.getCurrentRegime(),
            brain.getRecommendedSizeMultiplier() * 100,
            brain.getTotalBrainTrades()));
        brainStatusLabel.setTextFill(Color.web(
            brain.shouldTrade() ? ACCENT_GREEN : 
            brain.getCurrentDecision().toString().contains("AVOID") ? ACCENT_RED : ACCENT_GOLD));
        
        // Best Opportunity
        ScanResult best = botState.getCurrentBestOpportunity();
        if (best != null) {
            bestOpportunityLabel.setText(String.format("🎯 %s - %s (%.0f%% confidence)\n%s",
                best.getCurrency(),
                best.getSignal().getSignalType(),
                best.getSignal().getConfidence(),
                best.getSignal().getReason()));
            bestOpportunityLabel.setTextFill(Color.web(
                best.getSignal().getSignalType() == TradingSignal.SignalType.CALL ? ACCENT_GREEN :
                best.getSignal().getSignalType() == TradingSignal.SignalType.PUT ? ACCENT_RED : TEXT_SECONDARY
            ));
        } else {
            bestOpportunityLabel.setText("Scanning for opportunities...");
            bestOpportunityLabel.setTextFill(Color.web(TEXT_SECONDARY));
        }
        
        // Scanner results
        updateScannerResults();
        
        // Trade history
        updateTradeHistory();
    }
    
    private void updateScannerResults() {
        scanResultsBox.getChildren().clear();
        
        List<ScanResult> results = botState.getScanner().scanAllCurrencies();
        for (ScanResult result : results) {
            Label label = new Label(result.toString());
            label.setFont(Font.font("Consolas", 12));
            label.setTextFill(Color.web(
                result.getSignal().getSignalType() == TradingSignal.SignalType.CALL ? ACCENT_GREEN :
                result.getSignal().getSignalType() == TradingSignal.SignalType.PUT ? ACCENT_RED : TEXT_SECONDARY
            ));
            scanResultsBox.getChildren().add(label);
        }
    }
    
    private void updateTradeHistory() {
        tradeHistoryBox.getChildren().clear();
        
        List<Trade> trades = botState.getTradeHistory();
        int limit = Math.min(10, trades.size());
        for (int i = 0; i < limit; i++) {
            Trade trade = trades.get(i);
            Label label = new Label(trade.toString());
            label.setFont(Font.font("Consolas", 11));
            label.setTextFill(Color.web(
                trade.isWon() ? ACCENT_GREEN :
                trade.getStatus() == Trade.TradeStatus.LOST ? ACCENT_RED : TEXT_SECONDARY
            ));
            tradeHistoryBox.getChildren().add(label);
        }
        
        if (trades.isEmpty()) {
            Label empty = new Label("No trades yet");
            empty.setTextFill(Color.web(TEXT_SECONDARY));
            tradeHistoryBox.getChildren().add(empty);
        }
    }
    
    private void toggleBot() {
        if (botState.isBotRunning()) {
            botState.stop();
        } else {
            botState.start();
        }
    }
    
    private void executeTrade() {
        Trade trade = botState.executeTrade();
        if (trade != null) {
            System.out.println("Trade executed: " + trade);
        }
    }
    
    /**
     * Connect to Pocket Option broker via browser automation.
     */
    private void connectToBroker() {
        var pythonClient = botState.getTradingBrain().getPythonClient();
        
        if (pythonClient == null || !pythonClient.isConnected()) {
            brokerStatusLabel.setText("❌ Python server not running");
            brokerStatusLabel.setTextFill(Color.web(ACCENT_RED));
            return;
        }
        
        // Check if already connected - must verify browser is ACTUALLY open
        var status = pythonClient.getBrowserStatus();
        if (status.browserOpen && status.loggedIn) {
            brokerStatusLabel.setText("✅ Already connected!");
            brokerStatusLabel.setTextFill(Color.web(ACCENT_GREEN));
            return;
        }
        
        // Update UI to show connecting
        brokerConnectBtn.setDisable(true);
        brokerConnectBtn.setText("🔄 CONNECTING...");
        brokerStatusLabel.setText("🌐 Opening browser...");
        brokerStatusLabel.setTextFill(Color.web(ACCENT_GOLD));
        
        // Open browser in background thread
        new Thread(() -> {
            boolean opened = pythonClient.openBrowserForLogin();
            
            Platform.runLater(() -> {
                if (opened) {
                    brokerStatusLabel.setText("👤 Please login to Pocket Option...");
                    brokerStatusLabel.setTextFill(Color.web(ACCENT_BLUE));
                    brokerConnectBtn.setText("⏳ WAITING FOR LOGIN...");
                    
                    // Start polling for login status
                    startBrokerStatusMonitor();
                } else {
                    brokerStatusLabel.setText("❌ Failed to open browser");
                    brokerStatusLabel.setTextFill(Color.web(ACCENT_RED));
                    brokerConnectBtn.setDisable(false);
                    brokerConnectBtn.setText("🌐 CONNECT BROKER");
                }
            });
        }).start();
    }
    
    /**
     * Start monitoring broker status in background.
     */
    private void startBrokerStatusMonitor() {
        new Thread(() -> {
            var pythonClient = botState.getTradingBrain().getPythonClient();
            
            // Poll every 2 seconds for up to 5 minutes
            for (int i = 0; i < 150; i++) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
                
                var status = pythonClient.getBrowserStatus();
                
                Platform.runLater(() -> {
                    if (status.isReady()) {
                        String modeStr = status.demoMode ? "DEMO" : "REAL";
                        brokerStatusLabel.setText(String.format("✅ %s $%.2f", modeStr, status.balance));
                        brokerStatusLabel.setTextFill(Color.web(ACCENT_GREEN));
                        brokerConnectBtn.setText("✅ CONNECTED");
                        brokerConnectBtn.setStyle(getAccentButtonStyle(ACCENT_GREEN));
                        
                        // Enable Pocket Option mode in bot state
                        botState.setPocketOptionEnabled(true);
                        
                        // Update balance display
                        updateUI();
                    } else if (status.browserOpen) {
                        brokerStatusLabel.setText("👤 Waiting for login...");
                    }
                });
                
                // If logged in, we're done monitoring frequent updates
                if (status.isReady()) {
                    // Continue monitoring less frequently
                    while (true) {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            break;
                        }
                        
                        var newStatus = pythonClient.getBrowserStatus();
                        
                        Platform.runLater(() -> {
                            if (newStatus.isReady()) {
                                String modeStr = newStatus.demoMode ? "DEMO" : "REAL";
                                brokerStatusLabel.setText(String.format("✅ %s $%.2f", modeStr, newStatus.balance));
                            } else {
                                brokerStatusLabel.setText("⚠️ Disconnected");
                                brokerStatusLabel.setTextFill(Color.web(ACCENT_RED));
                                brokerConnectBtn.setText("🌐 RECONNECT");
                                brokerConnectBtn.setDisable(false);
                                brokerConnectBtn.setStyle(getAccentButtonStyle(ACCENT_GOLD));
                            }
                            updateUI();
                        });
                        
                        // If browser closed, stop monitoring
                        if (!newStatus.browserOpen) {
                            break;
                        }
                    }
                    break;
                }
            }
            
            // If we exited without login, reset the button
            Platform.runLater(() -> {
                var finalStatus = pythonClient.getBrowserStatus();
                if (!finalStatus.isReady()) {
                    brokerConnectBtn.setDisable(false);
                    brokerConnectBtn.setText("🌐 CONNECT BROKER");
                }
            });
        }).start();
    }
    
    public void show() {
        stage.show();
    }
    
    public void close() {
        botState.shutdown();
        stage.close();
    }
    
    // ==================== STYLES ====================
    
    private String getButtonStyle(boolean active) {
        return "-fx-background-color: " + (active ? ACCENT_BLUE : BG_CARD) + ";" +
               "-fx-text-fill: " + TEXT_PRIMARY + ";" +
               "-fx-background-radius: 5;" +
               "-fx-border-color: " + BORDER_COLOR + ";" +
               "-fx-border-radius: 5;" +
               "-fx-padding: 8 15;";
    }
    
    private String getAccentButtonStyle(String color) {
        return "-fx-background-color: " + color + ";" +
               "-fx-text-fill: white;" +
               "-fx-background-radius: 5;" +
               "-fx-font-weight: bold;" +
               "-fx-padding: 10 20;";
    }
    
    private String getToggleStyle(boolean active) {
        return "-fx-background-color: " + (active ? ACCENT_GREEN : BG_CARD) + ";" +
               "-fx-text-fill: " + (active ? "white" : TEXT_SECONDARY) + ";" +
               "-fx-background-radius: 5;" +
               "-fx-border-color: " + (active ? ACCENT_GREEN : BORDER_COLOR) + ";" +
               "-fx-border-radius: 5;" +
               "-fx-padding: 8 15;" +
               "-fx-font-weight: bold;";
    }
    
    private String getComboStyle() {
        return "-fx-background-color: " + BG_DARK + ";" +
               "-fx-text-fill: " + TEXT_PRIMARY + ";" +
               "-fx-border-color: " + BORDER_COLOR + ";" +
               "-fx-border-radius: 5;" +
               "-fx-background-radius: 5;";
    }
}
