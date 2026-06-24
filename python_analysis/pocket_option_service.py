"""
Pocket Option Service - Connects trading bot to Pocket Option platform
Based on PocketOptionAPI (GitHub: ChipaDevTeam/PocketOptionAPI)

This service handles:
- WebSocket connection to Pocket Option
- Trade execution (BUY/SELL)
- Result monitoring
- Demo and real account support
"""

import asyncio
import websockets
import json
import time
import threading
from datetime import datetime
from typing import Optional, Dict, Any, Callable
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger('PocketOption')

class PocketOptionService:
    """
    Service for connecting to Pocket Option trading platform.
    Always starts in DEMO mode for safety.
    """
    
    # WebSocket URLs (Updated based on actual Pocket Option endpoints)
    WS_URL_DEMO = "wss://demo-api-eu.po.market/socket.io/?EIO=4&transport=websocket"
    WS_URL_REAL = "wss://api-eu.po.market/socket.io/?EIO=4&transport=websocket"
    
    # Alternative URLs if the above don't work
    WS_URL_DEMO_ALT = "wss://ws.pocket-option.com/socket.io/?EIO=4&transport=websocket"
    WS_URL_REAL_ALT = "wss://ws2.pocket-option.com/socket.io/?EIO=4&transport=websocket"
    
    def __init__(self, ssid: str = None, demo_mode: bool = True):
        """
        Initialize Pocket Option service.
        
        Args:
            ssid: Session ID from browser (required for connection)
            demo_mode: If True, trade on demo account (default: True for safety)
        """
        self.ssid = ssid
        self.demo_mode = demo_mode
        self.ws = None
        self.connected = False
        self.authenticated = False
        self.balance = 0.0
        self.pending_trades = {}  # trade_id -> trade_info
        self.completed_trades = {}  # trade_id -> result
        self.price_callbacks = []
        self._ping_task = None
        self._receive_task = None
        self._running = False
        self._loop = None
        
    @property
    def ws_url(self) -> str:
        """Get the appropriate WebSocket URL based on mode."""
        return self.WS_URL_DEMO if self.demo_mode else self.WS_URL_REAL
        
    async def connect(self) -> bool:
        """
        Connect to Pocket Option WebSocket server.
        
        Returns:
            True if connection and authentication successful
        """
        if not self.ssid:
            logger.error("No SSID provided. Cannot connect.")
            return False
            
        try:
            logger.info(f"Connecting to Pocket Option ({'DEMO' if self.demo_mode else 'REAL'} mode)...")
            
            self.ws = await websockets.connect(
                self.ws_url,
                ping_interval=None,
                ping_timeout=None,
                close_timeout=10
            )
            
            self.connected = True
            logger.info("WebSocket connected. Authenticating...")
            
            # Send authentication
            await self._authenticate()
            
            # Start background tasks
            self._running = True
            self._ping_task = asyncio.create_task(self._ping_loop())
            self._receive_task = asyncio.create_task(self._receive_loop())
            
            # Wait a moment for auth response
            await asyncio.sleep(2)
            
            if self.authenticated:
                logger.info(f"Successfully connected! Balance: ${self.balance:.2f}")
                return True
            else:
                logger.warning("Connected but authentication pending...")
                return True  # Still connected, auth may be delayed
                
        except Exception as e:
            logger.error(f"Connection failed: {e}")
            self.connected = False
            return False
            
    async def _authenticate(self):
        """Send authentication message using SSID."""
        if not self.ws:
            return
            
        # Parse SSID if it's in full format
        if self.ssid.startswith('42["auth"'):
            auth_msg = self.ssid
        else:
            # Build auth message
            auth_data = {
                "session": self.ssid,
                "isDemo": 1 if self.demo_mode else 0,
                "platform": 1
            }
            auth_msg = f'42["auth",{json.dumps(auth_data)}]'
            
        await self.ws.send(auth_msg)
        logger.info("Authentication message sent")
        
    async def _ping_loop(self):
        """Send periodic pings to keep connection alive."""
        while self._running and self.ws:
            try:
                await asyncio.sleep(20)
                if self.ws and self._running:
                    await self.ws.send("2")  # Socket.IO ping
            except Exception as e:
                logger.warning(f"Ping failed: {e}")
                break
                
    async def _receive_loop(self):
        """Receive and process messages from server."""
        while self._running and self.ws:
            try:
                message = await asyncio.wait_for(
                    self.ws.recv(),
                    timeout=30
                )
                await self._process_message(message)
            except asyncio.TimeoutError:
                continue
            except websockets.exceptions.ConnectionClosed:
                logger.warning("Connection closed by server")
                self.connected = False
                break
            except Exception as e:
                logger.error(f"Receive error: {e}")
                
    async def _process_message(self, message: str):
        """Process incoming WebSocket message."""
        try:
            # Socket.IO protocol prefixes
            if message.startswith("0"):  # Open
                return
            elif message.startswith("3"):  # Pong
                return
            elif message.startswith("40"):  # Connect
                return
            elif message.startswith("42"):  # Message
                # Parse the event data
                data = json.loads(message[2:])
                event_name = data[0] if isinstance(data, list) else None
                event_data = data[1] if isinstance(data, list) and len(data) > 1 else None
                
                await self._handle_event(event_name, event_data)
                
        except json.JSONDecodeError:
            pass  # Not JSON, ignore
        except Exception as e:
            logger.debug(f"Message processing error: {e}")
            
    async def _handle_event(self, event_name: str, data: Any):
        """Handle specific events from Pocket Option."""
        logger.info(f"Event: {event_name}, Data: {str(data)[:100] if data else 'None'}")
        
        # Handle multiple auth success event names
        if event_name in ["successauth", "auth/success", "success"]:
            self.authenticated = True
            if isinstance(data, dict):
                self.balance = data.get("balance", 0)
            logger.info(f"Authenticated! Balance: ${self.balance:.2f}")
            
        elif event_name == "balance":
            if isinstance(data, (int, float)):
                self.balance = float(data)
            elif isinstance(data, dict):
                self.balance = data.get("balance", self.balance)
                
        elif event_name == "tradeResult":
            # Trade completed - check if win or loss
            if isinstance(data, dict):
                trade_id = data.get("id")
                profit = data.get("profit", 0)
                is_win = profit > 0
                
                if trade_id in self.pending_trades:
                    self.completed_trades[trade_id] = {
                        "won": is_win,
                        "profit": profit,
                        "completed_at": datetime.now().isoformat()
                    }
                    del self.pending_trades[trade_id]
                    logger.info(f"Trade {trade_id}: {'WIN' if is_win else 'LOSS'} (${profit:.2f})")
                    
        elif event_name == "prices":
            # Price updates - call registered callbacks
            for callback in self.price_callbacks:
                try:
                    callback(data)
                except Exception as e:
                    logger.error(f"Price callback error: {e}")
                    
    async def execute_trade(
        self,
        asset: str,
        direction: str,  # "call" or "put"
        amount: float,
        duration: int = 60  # seconds
    ) -> Optional[str]:
        """
        Execute a trade on Pocket Option.
        
        Args:
            asset: Asset to trade (e.g., "EURUSD_otc")
            direction: "call" for UP/BUY, "put" for DOWN/SELL
            amount: Trade amount in dollars
            duration: Trade duration in seconds (default 60)
            
        Returns:
            Trade ID if successful, None otherwise
        """
        if not self.connected or not self.ws:
            logger.error("Not connected to Pocket Option")
            return None
            
        try:
            trade_id = f"trade_{int(time.time() * 1000)}"
            
            trade_msg = {
                "asset": asset,
                "amount": amount,
                "time": duration,
                "action": direction.lower(),  # "call" or "put"
                "isDemo": 1 if self.demo_mode else 0,
                "requestId": trade_id
            }
            
            message = f'42["openOrder",{json.dumps(trade_msg)}]'
            await self.ws.send(message)
            
            # Track pending trade
            self.pending_trades[trade_id] = {
                "asset": asset,
                "direction": direction,
                "amount": amount,
                "duration": duration,
                "opened_at": datetime.now().isoformat()
            }
            
            logger.info(f"Trade executed: {direction.upper()} ${amount} on {asset} for {duration}s")
            return trade_id
            
        except Exception as e:
            logger.error(f"Trade execution failed: {e}")
            return None
            
    async def get_trade_result(self, trade_id: str, timeout: int = 300) -> Optional[Dict]:
        """
        Wait for and get trade result.
        
        Args:
            trade_id: ID of the trade to check
            timeout: Maximum seconds to wait
            
        Returns:
            Dict with 'won' and 'profit' keys, or None if timeout
        """
        start_time = time.time()
        
        while time.time() - start_time < timeout:
            if trade_id in self.completed_trades:
                return self.completed_trades[trade_id]
            await asyncio.sleep(1)
            
        return None
        
    def get_balance(self) -> float:
        """Get current account balance."""
        return self.balance
        
    def is_connected(self) -> bool:
        """Check if connected and authenticated."""
        return self.connected and self.authenticated
        
    def add_price_callback(self, callback: Callable):
        """Add callback to receive price updates."""
        self.price_callbacks.append(callback)
        
    async def disconnect(self):
        """Disconnect from Pocket Option."""
        self._running = False
        
        if self._ping_task:
            self._ping_task.cancel()
        if self._receive_task:
            self._receive_task.cancel()
            
        if self.ws:
            await self.ws.close()
            
        self.connected = False
        self.authenticated = False
        logger.info("Disconnected from Pocket Option")
        
    def __str__(self):
        mode = "DEMO" if self.demo_mode else "REAL"
        status = "Connected" if self.connected else "Disconnected"
        return f"PocketOption({mode}, {status}, Balance: ${self.balance:.2f})"


# Thread-safe wrapper for synchronous access
class PocketOptionClient:
    """
    Thread-safe synchronous wrapper for PocketOptionService.
    Use this from the Flask API server.
    """
    
    def __init__(self):
        self.service: Optional[PocketOptionService] = None
        self._loop: Optional[asyncio.AbstractEventLoop] = None
        self._thread: Optional[threading.Thread] = None
        
    def connect(self, ssid: str, demo_mode: bool = True) -> bool:
        """Connect to Pocket Option (starts async loop in background thread)."""
        if self._loop and self._loop.is_running():
            logger.warning("Already connected. Disconnect first.")
            return False
            
        self.service = PocketOptionService(ssid, demo_mode)
        
        # Create new event loop in background thread
        def run_loop():
            self._loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self._loop)
            
            # Connect
            connected = self._loop.run_until_complete(self.service.connect())
            
            if connected:
                # Keep loop running for message handling
                self._loop.run_forever()
                
        self._thread = threading.Thread(target=run_loop, daemon=True)
        self._thread.start()
        
        # Wait for connection
        time.sleep(3)
        return self.service.is_connected()
        
    def execute_trade(self, asset: str, direction: str, amount: float, duration: int = 60) -> Optional[str]:
        """Execute a trade synchronously."""
        if not self.service or not self._loop:
            return None
            
        future = asyncio.run_coroutine_threadsafe(
            self.service.execute_trade(asset, direction, amount, duration),
            self._loop
        )
        return future.result(timeout=10)
        
    def get_trade_result(self, trade_id: str, timeout: int = 300) -> Optional[Dict]:
        """Get trade result synchronously."""
        if not self.service or not self._loop:
            return None
            
        future = asyncio.run_coroutine_threadsafe(
            self.service.get_trade_result(trade_id, timeout),
            self._loop
        )
        return future.result(timeout=timeout + 10)
        
    def get_balance(self) -> float:
        """Get current balance."""
        return self.service.balance if self.service else 0.0
        
    def is_connected(self) -> bool:
        """Check connection status."""
        return self.service.is_connected() if self.service else False
        
    def get_status(self) -> Dict:
        """Get full status."""
        if not self.service:
            return {"connected": False, "error": "Not initialized"}
            
        return {
            "connected": self.service.connected,
            "authenticated": self.service.authenticated,
            "demo_mode": self.service.demo_mode,
            "balance": self.service.balance,
            "pending_trades": len(self.service.pending_trades),
            "completed_trades": len(self.service.completed_trades)
        }
        
    def disconnect(self):
        """Disconnect from Pocket Option."""
        if self._loop and self.service:
            asyncio.run_coroutine_threadsafe(self.service.disconnect(), self._loop)
            
        if self._loop:
            self._loop.call_soon_threadsafe(self._loop.stop)
            
        self._loop = None
        self._thread = None


# Global instance for API server
pocket_option_client = PocketOptionClient()


# Asset mapping (bot currency -> Pocket Option asset)
ASSET_MAPPING = {
    # Major pairs
    "EUR/USD": "EURUSD_otc",
    "GBP/USD": "GBPUSD_otc",
    "USD/JPY": "USDJPY_otc",
    "AUD/USD": "AUDUSD_otc",
    "USD/CAD": "USDCAD_otc",
    "USD/CHF": "USDCHF_otc",
    "NZD/USD": "NZDUSD_otc",
    # Cross pairs
    "EUR/GBP": "EURGBP_otc",
    "EUR/JPY": "EURJPY_otc",
    "GBP/JPY": "GBPJPY_otc",
    "AUD/JPY": "AUDJPY_otc",
    # Crypto
    "BTC/USD": "BTCUSD_otc",
    # Commodities
    "XAU/USD": "XAUUSD_otc",  # Gold
    "OIL/USD": "CRUDEOIL_otc"  # Crude Oil
}


def get_pocket_option_asset(bot_asset: str) -> str:
    """Convert bot asset name to Pocket Option asset name."""
    return ASSET_MAPPING.get(bot_asset, bot_asset.replace("/", "") + "_otc")
