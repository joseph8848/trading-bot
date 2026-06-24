"""
Pocket Option Trade Bridge - Connects Trading Bot to Pocket Option
Listens for trade signals from the bot and executes them via browser automation
"""

import time
import threading
import logging
from datetime import datetime
from typing import Optional, Dict, Callable
import json

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger('TradeBridge')


class PocketOptionTradeBridge:
    """
    Bridge between Trading Bot and Pocket Option.
    Executes trades on Pocket Option when the bot generates signals.
    """
    
    def __init__(self, browser_click_callback: Callable = None):
        """
        Initialize the trade bridge.
        
        Args:
            browser_click_callback: Function to call for clicking browser elements
                                   Signature: callback(selector: str) -> bool
        """
        self.browser_click_callback = browser_click_callback
        self.is_connected = False
        self.trade_count = 0
        self.win_count = 0
        self.last_trade_time = None
        self.trade_history = []
        self.min_trade_interval = 60  # Minimum seconds between trades
        self._running = False
        
        # Trade selectors (from DOM inspection)
        self.BUY_SELECTOR = ".btn-call"
        self.SELL_SELECTOR = ".btn-put"
        
    def connect(self, click_callback: Callable) -> bool:
        """
        Connect the bridge with a browser click callback.
        
        Args:
            click_callback: Function that clicks elements in the browser
        """
        self.browser_click_callback = click_callback
        self.is_connected = True
        logger.info("Trade bridge connected to browser")
        return True
        
    def execute_trade(self, signal: str, amount: float = None, asset: str = None) -> Optional[Dict]:
        """
        Execute a trade based on signal.
        
        Args:
            signal: "BUY" or "SELL" (also accepts "CALL"/"PUT", "UP"/"DOWN")
            amount: Trade amount (optional - uses platform default)
            asset: Asset to trade (optional - uses current selected)
            
        Returns:
            Trade info dict if successful
        """
        if not self.is_connected or not self.browser_click_callback:
            logger.error("Trade bridge not connected")
            return None
            
        # Rate limiting
        if self.last_trade_time:
            elapsed = (datetime.now() - self.last_trade_time).total_seconds()
            if elapsed < self.min_trade_interval:
                wait_time = self.min_trade_interval - elapsed
                logger.warning(f"Rate limit: waiting {wait_time:.0f}s before next trade")
                return None
                
        # Normalize signal
        signal_upper = signal.upper()
        is_buy = signal_upper in ["BUY", "CALL", "UP"]
        selector = self.BUY_SELECTOR if is_buy else self.SELL_SELECTOR
        direction = "BUY" if is_buy else "SELL"
        
        try:
            logger.info(f"Executing {direction} trade...")
            
            # Click the trade button
            success = self.browser_click_callback(selector)
            
            if success:
                trade_info = {
                    "trade_id": f"po_{int(time.time() * 1000)}",
                    "direction": direction,
                    "amount": amount or 0,
                    "asset": asset or "current",
                    "time": datetime.now().isoformat(),
                    "status": "executed"
                }
                
                self.trade_count += 1
                self.last_trade_time = datetime.now()
                self.trade_history.append(trade_info)
                
                logger.info(f"✅ Trade executed: {direction} (Trade #{self.trade_count})")
                return trade_info
            else:
                logger.error("Failed to click trade button")
                return None
                
        except Exception as e:
            logger.error(f"Trade execution error: {e}")
            return None
            
    def record_result(self, trade_id: str, won: bool, profit: float = 0):
        """Record the result of a trade."""
        for trade in self.trade_history:
            if trade["trade_id"] == trade_id:
                trade["result"] = "WIN" if won else "LOSS"
                trade["profit"] = profit
                if won:
                    self.win_count += 1
                logger.info(f"Trade result: {'WIN' if won else 'LOSS'} (${profit:.2f})")
                break
                
    def get_stats(self) -> Dict:
        """Get trading statistics."""
        win_rate = (self.win_count / self.trade_count * 100) if self.trade_count > 0 else 0
        return {
            "total_trades": self.trade_count,
            "wins": self.win_count,
            "losses": self.trade_count - self.win_count,
            "win_rate": f"{win_rate:.1f}%",
            "is_connected": self.is_connected,
            "last_trade": self.last_trade_time.isoformat() if self.last_trade_time else None
        }
        
    def disconnect(self):
        """Disconnect the bridge."""
        self.is_connected = False
        self._running = False
        logger.info("Trade bridge disconnected")


# Global bridge instance
trade_bridge = PocketOptionTradeBridge()


def create_browser_click_function(page_id: str):
    """
    Create a click function for use with the browser subagent.
    This is a factory function that returns a callback.
    """
    def click_element(selector: str) -> bool:
        """Click an element in the browser."""
        # This will be called from the API server
        # The actual implementation uses browser_click_element
        logger.info(f"Clicking selector: {selector}")
        return True  # Placeholder - actual implementation in API
    return click_element
