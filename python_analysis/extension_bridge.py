"""
Extension Bridge - DOM Observer Mode

Sends trade signals to Chrome extension which uses DOM observation
to efficiently set amount and execute trades.
"""

import asyncio
import json
import threading
from datetime import datetime
from typing import Optional, Dict

try:
    import websockets
    WEBSOCKETS_AVAILABLE = True
except ImportError:
    WEBSOCKETS_AVAILABLE = False
    print("⚠️ websockets not installed. Run: pip install websockets")


class DOMObserverBridge:
    """
    Bridge for DOM Observer trading system.
    Extension watches broker state and makes minimal changes for each trade.
    """
    
    def __init__(self):
        self.extensions = set()
        self.loop = None
        self.server = None
        self._running = False
        self._thread = None
        
        # Trade tracking
        self.trade_count = 0
        self.last_trade_result = None
        self.trade_results = []
        
        # Broker state (from extension)
        self.current_balance = 0
        self.current_currency = ''
        self.current_amount = 0
        
    async def handler(self, websocket):
        """Handle WebSocket connections from extension."""
        self.extensions.add(websocket)
        print(f"🔌 Extension connected! ({len(self.extensions)} active)")
        
        try:
            await websocket.send(json.dumps({
                'type': 'WELCOME',
                'mode': 'DOM_OBSERVER'
            }))
            
            async for message in websocket:
                await self.handle_message(websocket, message)
                
        except Exception as e:
            print(f"Handler error: {e}")
        finally:
            self.extensions.discard(websocket)
            print(f"🔌 Extension disconnected ({len(self.extensions)} active)")
    
    async def handle_message(self, websocket, message):
        """Process messages from extension."""
        try:
            data = json.loads(message)
            msg_type = data.get('type', '')
            
            if msg_type == 'EXTENSION_CONNECTED':
                print(f"✅ Extension connected in {data.get('mode', 'unknown')} mode")
                
            elif msg_type == 'STATUS':
                self.current_balance = data.get('balance', 0)
                self.current_currency = data.get('currency', '')
                self.current_amount = data.get('amount', 0)
                
            elif msg_type == 'STATE_UPDATE':
                state = data.get('state', {})
                self.current_balance = state.get('balance', self.current_balance)
                self.current_currency = state.get('currentCurrency', self.current_currency)
                self.current_amount = state.get('currentAmount', self.current_amount)
                
            elif msg_type == 'TRADE_RESULT':
                result = {
                    'success': data.get('success', False),
                    'direction': data.get('direction'),
                    'amount': data.get('amount'),
                    'currency': data.get('currency'),
                    'error': data.get('error'),
                    'time': datetime.now().isoformat()
                }
                self.trade_results.append(result)
                self.last_trade_result = result
                
                if data.get('success'):
                    self.trade_count += 1
                    print(f"✅ Trade executed: {data.get('direction')} ${data.get('amount')}")
                else:
                    print(f"❌ Trade failed: {data.get('error')}")
                    
            elif msg_type == 'PONG':
                pass
                
        except Exception as e:
            print(f"Message error: {e}")
    
    async def send_trade_signal(self, currency: str, direction: str, amount: float, duration: int = 60):
        """Send trade signal to extension for DOM-based execution."""
        if not self.extensions:
            print("⚠️ No extensions connected!")
            return False
        
        signal = {
            'type': 'TRADE_SIGNAL',
            'currency': currency,
            'direction': direction.upper(),
            'amount': amount,
            'duration': duration,
            'timestamp': datetime.now().isoformat()
        }
        
        print(f"📤 Sending trade: {direction} ${amount} on {currency}")
        
        for ws in list(self.extensions):
            try:
                await ws.send(json.dumps(signal))
            except:
                self.extensions.discard(ws)
        
        return True
    
    def send_trade_sync(self, currency: str, direction: str, amount: float, duration: int = 60) -> bool:
        """Synchronous wrapper for send_trade_signal."""
        if not self.loop or not self._running:
            print("⚠️ Bridge not running")
            return False
        
        try:
            future = asyncio.run_coroutine_threadsafe(
                self.send_trade_signal(currency, direction, amount, duration),
                self.loop
            )
            return future.result(timeout=10)
        except Exception as e:
            print(f"Send error: {e}")
            return False
    
    def get_status(self) -> Dict:
        """Get current bridge status."""
        return {
            'extensions_connected': len(self.extensions),
            'mode': 'DOM_OBSERVER',
            'balance': self.current_balance,
            'currency': self.current_currency,
            'amount': self.current_amount,
            'trade_count': self.trade_count,
            'last_trade': self.last_trade_result
        }
    
    async def _run_server(self, port: int):
        """Run the WebSocket server."""
        async with websockets.serve(self.handler, "localhost", port):
            print(f"🌐 DOM Observer Bridge on ws://localhost:{port}")
            await asyncio.Future()
    
    def start(self, port: int = 5002):
        """Start the bridge in a background thread."""
        if not WEBSOCKETS_AVAILABLE:
            print("❌ Cannot start - websockets not installed")
            return False
        
        def run_loop():
            self.loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self.loop)
            self._running = True
            try:
                self.loop.run_until_complete(self._run_server(port))
            except Exception as e:
                print(f"Bridge error: {e}")
            finally:
                self._running = False
        
        self._thread = threading.Thread(target=run_loop, daemon=True)
        self._thread.start()
        
        import time
        time.sleep(0.5)
        
        return self._running or self._thread.is_alive()
    
    def stop(self):
        """Stop the bridge."""
        self._running = False
        if self.loop:
            self.loop.call_soon_threadsafe(self.loop.stop)


# Global instance
dom_bridge = DOMObserverBridge()


# ===== BACKWARD COMPATIBLE EXPORTS =====

def start_extension_server(port: int = 5002):
    """Start the DOM Observer bridge."""
    print("🔌 Starting DOM Observer Trading Bridge...")
    return dom_bridge.start(port)


def send_trade_to_extension(direction: str, amount: float, currency: str, duration: int = 60) -> bool:
    """Send trade signal to extension."""
    return dom_bridge.send_trade_sync(currency, direction, amount, duration)


def get_extension_status() -> dict:
    """Get bridge status."""
    status = dom_bridge.get_status()
    return {
        'connected': status.get('extensions_connected', 0) > 0,
        'extensions_connected': status.get('extensions_connected', 0),
        'mode': 'DOM_OBSERVER',
        'balance': status.get('balance', 0),
        'total_trades': status.get('trade_count', 0)
    }


# Legacy aliases
extension_bridge = dom_bridge
hybrid_bridge = dom_bridge
ExtensionBridge = DOMObserverBridge
HybridTradingBridge = DOMObserverBridge


def start_hybrid_bridge(port: int = 5002):
    return start_extension_server(port)


def execute_trade(currency: str, direction: str, amount: float, duration: int = 60) -> dict:
    success = send_trade_to_extension(direction, amount, currency, duration)
    return {'success': success}


def get_status() -> dict:
    return get_extension_status()
