"""
Live Market Data Feed
Fetches real-time forex prices using yfinance and populates the price cache.

Replaces the old approach of relying on Java or random prices.
Runs as a background thread, updating prices every 30 seconds.
"""

import threading
import time
from datetime import datetime
from typing import Dict, List, Optional

# Try to import yfinance
try:
    import yfinance as yf
    YFINANCE_AVAILABLE = True
except ImportError:
    YFINANCE_AVAILABLE = False
    print("⚠️ yfinance not installed — run: pip install yfinance")

# Try to import requests as fallback
try:
    import requests
    REQUESTS_AVAILABLE = True
except ImportError:
    REQUESTS_AVAILABLE = False


# Yahoo Finance ticker mapping for forex pairs
FOREX_TICKERS = {
    "EUR/USD": "EURUSD=X",
    "GBP/USD": "GBPUSD=X",
    "USD/JPY": "USDJPY=X",
    "AUD/USD": "AUDUSD=X",
    "USD/CAD": "USDCAD=X",
    "NZD/USD": "NZDUSD=X",
    "EUR/GBP": "EURGBP=X",
    "XAU/USD": "GC=F",       # Gold futures
    "XAG/USD": "SI=F",       # Silver futures
    "OIL/USD": "CL=F",       # Crude oil futures
    "BTC/USD": "BTC-USD",    # Bitcoin
    "ETH/USD": "ETH-USD",    # Ethereum
}

MAX_PRICE_HISTORY = 200  # Keep 200 price points per symbol


class LiveDataFeed:
    """
    Background service that fetches real-time market prices
    and populates the shared price_cache dictionary.
    """
    
    def __init__(self, price_cache: dict, update_interval: int = 30):
        """
        Args:
            price_cache: Shared dict reference (same as api_server's price_cache)
            update_interval: Seconds between price updates (default 30)
        """
        self.price_cache = price_cache
        self.update_interval = update_interval
        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._last_update: Dict[str, float] = {}
        self._errors: Dict[str, str] = {}
        self._update_count = 0
        
        print(f"📡 LiveDataFeed initialized — updating every {update_interval}s")
        if not YFINANCE_AVAILABLE:
            print("   ⚠️ yfinance not available, will use fallback methods")
    
    def start(self):
        """Start the background price feed."""
        if self._running:
            return
        
        self._running = True
        self._thread = threading.Thread(target=self._feed_loop, daemon=True, name="LiveDataFeed")
        self._thread.start()
    
    def stop(self):
        """Stop the background feed."""
        self._running = False
        if self._thread:
            self._thread.join(timeout=5)
    
    def _feed_loop(self):
        """Main loop — fetches prices periodically."""
        # Initial fetch with a small delay to let server start
        time.sleep(3)
        
        while self._running:
            try:
                self._fetch_all_prices()
                self._update_count += 1
            except Exception as e:
                print(f"⚠️ LiveDataFeed error: {e}")
            
            time.sleep(self.update_interval)
    
    def _fetch_all_prices(self):
        """Fetch prices for all configured currency pairs."""
        if not YFINANCE_AVAILABLE:
            self._fetch_fallback()
            return
        
        try:
            # Batch fetch — more efficient than individual requests
            tickers_str = " ".join(FOREX_TICKERS.values())
            data = yf.download(
                tickers_str, 
                period="1d",      # Last trading day
                interval="1m",    # 1-minute bars
                progress=False,
                threads=True
            )
            
            if data.empty:
                # Market might be closed, try 5d period
                data = yf.download(
                    tickers_str,
                    period="5d",
                    interval="5m",
                    progress=False,
                    threads=True
                )
            
            if data.empty:
                return
            
            for pair, ticker in FOREX_TICKERS.items():
                try:
                    # Handle multi-ticker DataFrame structure
                    if len(FOREX_TICKERS) > 1 and isinstance(data.columns, type(data.columns)):
                        # Multi-ticker download: columns are MultiIndex (Price, Ticker)
                        try:
                            close_prices = data['Close'][ticker].dropna().tolist()
                        except (KeyError, TypeError):
                            try:
                                close_prices = data[('Close', ticker)].dropna().tolist()
                            except (KeyError, TypeError):
                                continue
                    else:
                        close_prices = data['Close'].dropna().tolist()
                    
                    if close_prices:
                        # Append to existing cache or create new
                        existing = self.price_cache.get(pair, [])
                        
                        # Only add new prices (avoid duplicates)
                        if existing:
                            # Add the latest price(s) that are different
                            new_prices = [p for p in close_prices[-5:] if p != existing[-1]]
                            if new_prices:
                                existing.extend(new_prices)
                        else:
                            existing = close_prices
                        
                        # Trim to max history
                        if len(existing) > MAX_PRICE_HISTORY:
                            existing = existing[-MAX_PRICE_HISTORY:]
                        
                        self.price_cache[pair] = existing
                        self._last_update[pair] = time.time()
                        self._errors.pop(pair, None)
                        
                except Exception as e:
                    self._errors[pair] = str(e)
            
            if self._update_count % 10 == 0:  # Log every 10th update
                active = sum(1 for p in self.price_cache if len(self.price_cache[p]) >= 20)
                print(f"📡 Live feed update #{self._update_count}: {active}/{len(FOREX_TICKERS)} pairs active")
                
        except Exception as e:
            print(f"⚠️ yfinance batch fetch error: {e}")
            # Fall back to individual fetches
            self._fetch_individual()
    
    def _fetch_individual(self):
        """Fetch prices one at a time (fallback if batch fails)."""
        for pair, ticker in FOREX_TICKERS.items():
            try:
                data = yf.download(ticker, period="1d", interval="1m", progress=False)
                if not data.empty:
                    close_prices = data['Close'].dropna().tolist()
                    if close_prices:
                        existing = self.price_cache.get(pair, [])
                        if existing:
                            last_known = existing[-1]
                            new_prices = [p for p in close_prices[-5:] if p != last_known]
                            if new_prices:
                                existing.extend(new_prices)
                        else:
                            existing = close_prices
                        
                        if len(existing) > MAX_PRICE_HISTORY:
                            existing = existing[-MAX_PRICE_HISTORY:]
                        
                        self.price_cache[pair] = existing
                        self._last_update[pair] = time.time()
                        self._errors.pop(pair, None)
            except Exception as e:
                self._errors[pair] = str(e)
            
            time.sleep(0.5)  # Rate limit
    
    def _fetch_fallback(self):
        """Fallback when yfinance is not available — try free API."""
        if not REQUESTS_AVAILABLE:
            return
        
        # Use exchangerate.host as free fallback (no API key needed)
        base_pairs = {
            "EUR/USD": ("EUR", "USD"),
            "GBP/USD": ("GBP", "USD"),
            "USD/JPY": ("USD", "JPY"),
            "AUD/USD": ("AUD", "USD"),
            "USD/CAD": ("USD", "CAD"),
            "NZD/USD": ("NZD", "USD"),
        }
        
        for pair, (base, quote) in base_pairs.items():
            try:
                url = f"https://api.exchangerate.host/latest?base={base}&symbols={quote}"
                resp = requests.get(url, timeout=5)
                if resp.status_code == 200:
                    data = resp.json()
                    rate = data.get("rates", {}).get(quote)
                    if rate:
                        existing = self.price_cache.get(pair, [])
                        existing.append(float(rate))
                        if len(existing) > MAX_PRICE_HISTORY:
                            existing = existing[-MAX_PRICE_HISTORY:]
                        self.price_cache[pair] = existing
                        self._last_update[pair] = time.time()
            except Exception:
                pass
            time.sleep(0.3)
    
    def get_status(self) -> Dict:
        """Get the current status of the live data feed."""
        now = time.time()
        pairs_status = {}
        
        for pair in FOREX_TICKERS:
            last = self._last_update.get(pair, 0)
            count = len(self.price_cache.get(pair, []))
            error = self._errors.get(pair)
            
            pairs_status[pair] = {
                "price_count": count,
                "has_enough_data": count >= 20,
                "last_update_ago": f"{int(now - last)}s" if last > 0 else "never",
                "current_price": self.price_cache.get(pair, [None])[-1] if self.price_cache.get(pair) else None,
                "error": error
            }
        
        return {
            "running": self._running,
            "update_count": self._update_count,
            "update_interval": f"{self.update_interval}s",
            "yfinance_available": YFINANCE_AVAILABLE,
            "pairs": pairs_status
        }
