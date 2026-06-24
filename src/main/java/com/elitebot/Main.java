package com.elitebot;

import com.elitebot.ui.SignalDashboard;
import com.elitebot.state.BotState;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Elite Smart Trading Bot - Signal Only Mode
 * 
 * Shows trading signals for manual execution.
 * No automatic DOM manipulation.
 * You execute trades yourself on the broker!
 */
public class Main extends Application {
    
    private SignalDashboard dashboard;
    private BotState botState;
    
    @Override
    public void start(Stage primaryStage) {
        System.out.println("🚀 Starting Signal-Only Trading Bot...");
        System.out.println("📊 You will see signals - execute trades manually!");
        System.out.println("");
        
        botState = new BotState();
        botState.start();  // Start scanning
        
        dashboard = new SignalDashboard(primaryStage, botState);
        dashboard.show();
    }
    
    @Override
    public void stop() {
        if (dashboard != null) {
            dashboard.close();
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}

