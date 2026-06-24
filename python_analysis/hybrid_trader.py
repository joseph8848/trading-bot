"""
Hybrid Trader - WebSocket API Primary + Browser Fallback

This module provides:
- Fast WebSocket API trading (primary method)
- Browser automation fallback
- SSID extraction from browser session
- Safety features (delays, rate limits)
"""

import time
import random
import threading
import logging
from typing import Optional, Dict, Any
from datetime import datetime, timedelta

# Import our existing services
from pocket_option_service import PocketOptionService, PocketOptionClient
import pocket_option_browser  # Import module to access browser_controller dynamically

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger('HybridTrader')


class HybridTrader:
    """
    Hybrid trading service that uses WebSocket API as primary
    and falls back to browser automation if needed.
    
    Features:
    - Fast WebSocket execution
    - Automatic browser fallback
    - SSID extraction from browser
    - Safety features (delays, rate limits)
    """
    
    # Safety settings
    MIN_TRADE_DELAY = 1.0       # Minimum seconds between trades
    MAX_TRADE_DELAY = 3.0       # Maximum random delay
    MAX_TRADES_PER_HOUR = 30    # Rate limit
    COOLDOWN_AFTER_FAILURE = 5  # Seconds to wait after failed trade
    
    def __init__(self, demo_mode: bool = True):
        """
        Initialize hybrid trader.
        
        Args:
            demo_mode: If True, use demo account (default: True for safety)
        """
        self.demo_mode = demo_mode
        
        # WebSocket client (initialized after SSID extraction)
        self.websocket: Optional[PocketOptionClient] = None
        self.ssid: Optional[str] = None
        
        # State tracking
        self.is_connected = False
        self.websocket_connected = False
        self.use_websocket = True  # Primary method
        
        # Trade tracking for rate limiting
        self.trade_times = []
        self.consecutive_failures = 0
        self.total_trades = 0
        
        # Threading
        self._lock = threading.Lock()
    
    @property
    def browser(self):
        """Get the CURRENT browser_controller dynamically - shares with /browser/* endpoints."""
        return pocket_option_browser.browser_controller
        
    def connect(self) -> Dict[str, Any]:
        """
        Connect to Pocket Option:
        1. Open browser for manual login
        2. Extract SSID after login
        3. Initialize WebSocket connection
        
        Returns:
            Status dict with success, message, etc.
        """
        logger.info("🌐 Opening browser for login...")
        
        # Step 1: Open browser
        if not self.browser.driver:
            success = self.browser.open_for_login()
            if not success:
                return {
                    "success": False,
                    "message": "Failed to open browser",
                    "websocket_connected": False
                }
        
        return {
            "success": True,
            "message": "Browser opened! Please login to Pocket Option.",
            "websocket_connected": False,
            "waiting_for_login": True
        }
    
    def check_and_init_websocket(self) -> bool:
        """
        Check if user is logged in and initialize WebSocket if possible.
        Called periodically after browser is opened.
        
        Returns:
            True if WebSocket is ready
        """
        if self.websocket_connected:
            return True
            
        # Check if logged in
        status = self.browser.get_status()
        if not status.get("logged_in", False):
            return False
            
        # Try to extract SSID
        ssid = self._extract_ssid()
        if not ssid:
            logger.warning("Could not extract SSID - will use browser only")
            self.use_websocket = False
            self.is_connected = True
            return False
            
        # Initialize WebSocket
        logger.info("🔌 Initializing WebSocket connection...")
        try:
            self.websocket = PocketOptionClient()
            success = self.websocket.connect(ssid, self.demo_mode)
            
            if success:
                self.websocket_connected = True
                self.is_connected = True
                self.ssid = ssid
                logger.info("✅ WebSocket connected! Fast trading enabled.")
                return True
            else:
                logger.warning("⚠️ WebSocket connection failed - using browser fallback")
                self.use_websocket = False
                self.is_connected = True
                return False
                
        except Exception as e:
            logger.error(f"WebSocket error: {e}")
            self.use_websocket = False
            self.is_connected = True
            return False
    
    def _extract_ssid(self) -> Optional[str]:
        """
        Extract SSID from browser session (cookies/localStorage).
        
        Returns:
            SSID string if found, None otherwise
        """
        if not self.browser.driver:
            return None
            
        try:
            # Method 1: Check localStorage
            ssid = self.browser.driver.execute_script(
                "return localStorage.getItem('ssid') || sessionStorage.getItem('ssid')"
            )
            if ssid:
                logger.info("✅ SSID extracted from storage")
                return ssid
            
            # Method 2: Check cookies
            cookies = self.browser.driver.get_cookies()
            for cookie in cookies:
                if cookie.get('name', '').lower() in ['ssid', 'session', 'auth']:
                    ssid = cookie.get('value')
                    if ssid:
                        logger.info("✅ SSID extracted from cookies")
                        return ssid
            
            # Method 3: Look for it in page context
            ssid = self.browser.driver.execute_script("""
                // Check various places where SSID might be stored
                if (window.__SSID__) return window.__SSID__;
                if (window.ssid) return window.ssid;
                if (window.PocketOption && window.PocketOption.ssid) return window.PocketOption.ssid;
                
                // Check for WebSocket URL with ssid
                if (window.WebSocket && window.WebSocket.prototype._originalSend) {
                    // Some sites store it during WebSocket init
                }
                
                return null;
            """)
            if ssid:
                logger.info("✅ SSID extracted from page context")
                return ssid
                
            logger.warning("Could not find SSID in browser")
            return None
            
        except Exception as e:
            logger.error(f"SSID extraction error: {e}")
            return None
    
    def execute_trade(
        self,
        asset: str,
        direction: str,  # "call" or "put"
        amount: float,
        duration: int = 60  # seconds
    ) -> Dict[str, Any]:
        """
        Execute a trade using hybrid method:
        1. Apply safety delay
        2. Check rate limits
        3. Try WebSocket first
        4. Fall back to browser if needed
        
        Args:
            asset: Currency pair (e.g., "EURUSD" or "EUR/USD")
            direction: "call"/"buy" for UP, "put"/"sell" for DOWN
            amount: Trade amount in dollars
            duration: Trade duration in seconds
            
        Returns:
            Trade result dict
        """
        with self._lock:
            # ===== SAFETY: Random delay =====
            delay = random.uniform(self.MIN_TRADE_DELAY, self.MAX_TRADE_DELAY)
            logger.info(f"⏳ Safety delay: {delay:.1f}s")
            time.sleep(delay)
            
            # ===== SAFETY: Rate limit check =====
            if not self._check_rate_limit():
                return {
                    "success": False,
                    "error": "Rate limit exceeded (max 30 trades/hour)",
                    "method": "none"
                }
            
            # ===== SAFETY: Cooldown after failures =====
            if self.consecutive_failures >= 3:
                logger.warning(f"⚠️ {self.consecutive_failures} consecutive failures - cooling down")
                time.sleep(self.COOLDOWN_AFTER_FAILURE)
            
            # Normalize direction
            direction = direction.lower()
            if direction in ["buy", "up"]:
                direction = "call"
            elif direction in ["sell", "down"]:
                direction = "put"
            
            # Normalize asset (remove slash, add _otc for OTC markets)
            asset = asset.replace("/", "").upper()
            # Check if OTC market hours (add _otc suffix if needed)
            # For now, assume OTC is available
            
            # ===== TRY WEBSOCKET FIRST =====
            if self.use_websocket and self.websocket_connected:
                result = self._execute_via_websocket(asset, direction, amount, duration)
                if result.get("success"):
                    self.consecutive_failures = 0
                    self._record_trade()
                    return result
                else:
                    logger.warning("WebSocket trade failed, trying browser fallback...")
            
            # ===== FALLBACK TO BROWSER =====
            result = self._execute_via_browser(asset, direction, amount, duration)
            if result.get("success"):
                self.consecutive_failures = 0
                self._record_trade()
            else:
                self.consecutive_failures += 1
                
            return result
    
    def _execute_via_websocket(
        self,
        asset: str,
        direction: str,
        amount: float,
        duration: int
    ) -> Dict[str, Any]:
        """Execute trade via WebSocket API."""
        try:
            if not self.websocket or not self.websocket.is_connected():
                return {"success": False, "error": "WebSocket not connected", "method": "websocket"}
            
            # Convert asset format for API (e.g., "EURUSD" -> "EURUSD_otc")
            api_asset = f"{asset}_otc"  # OTC markets
            
            trade_id = self.websocket.execute_trade(
                asset=api_asset,
                direction=direction,
                amount=amount,
                duration=duration
            )
            
            if trade_id:
                logger.info(f"✅ WebSocket trade executed: {direction.upper()} ${amount} on {asset}")
                return {
                    "success": True,
                    "trade_id": trade_id,
                    "method": "websocket",
                    "asset": asset,
                    "direction": direction,
                    "amount": amount,
                    "duration": duration
                }
            else:
                return {"success": False, "error": "Trade execution failed", "method": "websocket"}
                
        except Exception as e:
            logger.error(f"WebSocket trade error: {e}")
            return {"success": False, "error": str(e), "method": "websocket"}
    
    def _execute_via_browser(self, asset: str, direction: str, amount: float, duration: int) -> Dict[str, Any]:
        """Execute trade via browser automation (fallback).
        
        Args:
            asset: Currency pair (e.g., "EURUSD")
            direction: "call" or "put"
            amount: Trade amount in dollars
            duration: Trade duration in seconds
        """
        try:
            # Convert direction for browser
            browser_direction = "CALL" if direction == "call" else "PUT"
            
            # Format asset for browser (e.g., "EURUSD" -> "EUR/USD")
            if len(asset) >= 6 and "/" not in asset:
                formatted_asset = f"{asset[:3]}/{asset[3:6]}"
            else:
                formatted_asset = asset
            
            logger.info(f"📊 Setting currency: {formatted_asset}")
            logger.info(f"💵 Setting amount: ${amount}")
            logger.info(f"⏱️ Setting duration: {duration} seconds")
            
            # SET CURRENCY before trade
            try:
                self.browser.set_currency(formatted_asset)
            except Exception as e:
                logger.warning(f"Could not set currency: {e}")
            
            # SET AMOUNT before trade
            try:
                self.browser.set_trade_amount(amount)
            except Exception as e:
                logger.warning(f"Could not set amount: {e}")
            
            # SET DURATION before trade (convert seconds to minutes)
            try:
                duration_minutes = max(1, duration // 60)
                self.browser.set_trade_duration(duration_minutes)
            except Exception as e:
                logger.warning(f"Could not set duration: {e}")
            
            # Small delay to let settings apply
            import time
            time.sleep(0.5)
            
            # EXECUTE THE TRADE
            result = self.browser.execute_trade(browser_direction, amount)
            
            if result:
                logger.info(f"✅ Browser trade executed: {browser_direction} ${amount} on {formatted_asset}")
                return {
                    "success": True,
                    "trade": result,
                    "method": "browser",
                    "asset": formatted_asset,
                    "direction": browser_direction,
                    "amount": amount,
                    "duration": duration
                }
            else:
                return {"success": False, "error": "Browser trade failed", "method": "browser"}
                
        except Exception as e:
            logger.error(f"Browser trade error: {e}")
            return {"success": False, "error": str(e), "method": "browser"}
    
    def _check_rate_limit(self) -> bool:
        """Check if we're within rate limits."""
        now = datetime.now()
        hour_ago = now - timedelta(hours=1)
        
        # Remove old trade times
        self.trade_times = [t for t in self.trade_times if t > hour_ago]
        
        # Check limit
        if len(self.trade_times) >= self.MAX_TRADES_PER_HOUR:
            logger.warning(f"Rate limit: {len(self.trade_times)}/{self.MAX_TRADES_PER_HOUR} trades in last hour")
            return False
            
        return True
    
    def _record_trade(self):
        """Record trade time for rate limiting."""
        self.trade_times.append(datetime.now())
        self.total_trades += 1
    
    def get_status(self) -> Dict[str, Any]:
        """Get current hybrid trader status."""
        browser_status = self.browser.get_status() if self.browser else {}
        
        return {
            "is_connected": self.is_connected,
            "websocket_connected": self.websocket_connected,
            "use_websocket": self.use_websocket,
            "browser_open": browser_status.get("browser_open", False),
            "logged_in": browser_status.get("logged_in", False),
            "balance": browser_status.get("balance", 0),
            "currency": browser_status.get("currency", ""),
            "demo_mode": self.demo_mode,
            "total_trades": self.total_trades,
            "trades_last_hour": len(self.trade_times),
            "consecutive_failures": self.consecutive_failures,
            "ssid_available": self.ssid is not None
        }
    
    def get_balance(self) -> float:
        """Get current balance (from WebSocket if available, else browser)."""
        if self.websocket_connected and self.websocket:
            try:
                balance = self.websocket.get_balance()
                if balance > 0:
                    return balance
            except:
                pass
        
        return self.browser.get_balance() if self.browser else 0.0
    
    def disconnect(self):
        """Disconnect WebSocket only (don't close shared browser)."""
        if self.websocket:
            try:
                self.websocket.disconnect()
            except:
                pass
            self.websocket = None
            
        # DON'T close browser - it's shared with other endpoints
        # if self.browser:
        #     self.browser.close()
            
        self.is_connected = False
        self.websocket_connected = False
        logger.info("🔌 Hybrid trader disconnected (browser kept open)")


# ===== GLOBAL INSTANCE =====
hybrid_trader: Optional[HybridTrader] = None


def get_hybrid_trader() -> HybridTrader:
    """Get or create the global hybrid trader instance."""
    global hybrid_trader
    if hybrid_trader is None:
        hybrid_trader = HybridTrader(demo_mode=True)
    return hybrid_trader


# ===== API FUNCTIONS =====

def hybrid_connect() -> Dict[str, Any]:
    """Open browser for login and prepare hybrid trading."""
    trader = get_hybrid_trader()
    return trader.connect()


def hybrid_check_status() -> Dict[str, Any]:
    """Check connection status and init WebSocket if ready."""
    trader = get_hybrid_trader()
    trader.check_and_init_websocket()
    return trader.get_status()


def hybrid_execute_trade(
    asset: str,
    direction: str,
    amount: float,
    duration: int = 60
) -> Dict[str, Any]:
    """Execute trade using hybrid method."""
    trader = get_hybrid_trader()
    return trader.execute_trade(asset, direction, amount, duration)


def hybrid_get_balance() -> float:
    """Get current balance."""
    trader = get_hybrid_trader()
    return trader.get_balance()


def hybrid_disconnect() -> Dict[str, Any]:
    """Disconnect hybrid trader."""
    global hybrid_trader
    if hybrid_trader:
        hybrid_trader.disconnect()
        hybrid_trader = None
    return {"success": True, "message": "Disconnected"}
