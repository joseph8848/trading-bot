"""
Auto-Learner Module
Automatically tracks trade outcomes by checking market prices after expiry.

No more manual win/loss input needed!

How it works:
1. When a signal is acted upon, record: currency, direction, entry price, expiry time
2. Background thread checks pending trades every 10 seconds
3. When a trade expires, fetch the current price
4. Compare entry vs exit price to determine WIN/LOSS
5. Automatically feed result back into the consensus engine for weight adaptation
"""

import json
import os
import time
import threading
from datetime import datetime, timedelta
from typing import Dict, List, Optional
from dataclasses import dataclass, asdict


@dataclass
class PendingTrade:
    """A trade awaiting outcome verification."""
    trade_id: str
    currency: str
    direction: str          # "BUY" / "SELL" / "CALL" / "PUT"
    entry_price: float
    entry_time: str         # ISO format
    expiry_seconds: int     # e.g., 300 for 5 minutes
    expiry_timestamp: float # unix timestamp when trade expires
    confidence: float
    source: str             # "consensus", "fingpt", "python_ml"
    signals_used: List[str] # Which AIs contributed
    status: str = "PENDING" # PENDING, WON, LOST, EXPIRED, ERROR
    exit_price: Optional[float] = None
    result_time: Optional[str] = None
    pnl: Optional[float] = None


class AutoLearner:
    """
    Automatically determines trade outcomes by checking market prices after expiry.
    Feeds results back into the consensus engine for adaptive learning.
    """
    
    HISTORY_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "trade_history.json")
    MAX_HISTORY = 500  # Keep last 500 trades
    
    def __init__(self, price_cache_ref: dict, consensus_engine_ref=None, risk_filter_ref=None):
        """
        Args:
            price_cache_ref: Reference to the api_server price_cache dict (live prices)
            consensus_engine_ref: Reference to the consensus engine for weight updates
            risk_filter_ref: Reference to the risk filter for tracking wins/losses
        """
        self.price_cache = price_cache_ref
        self.consensus_engine = consensus_engine_ref
        self.risk_filter = risk_filter_ref
        
        self.pending_trades: List[PendingTrade] = []
        self.completed_trades: List[Dict] = []
        self._lock = threading.Lock()
        self._running = False
        self._thread: Optional[threading.Thread] = None
        
        # Load history
        self._load_history()
        
        print("📚 Auto-Learner initialized — trade outcomes will be tracked automatically")
    
    def record_signal(self, currency: str, direction: str, entry_price: float,
                      expiry_seconds: int = 300, confidence: float = 0.0,
                      source: str = "consensus", signals_used: Optional[List[str]] = None) -> str:
        """
        Record a new trade signal for outcome tracking.
        
        Args:
            currency: e.g., "EUR/USD"
            direction: "BUY"/"SELL"/"CALL"/"PUT"
            entry_price: Price at entry
            expiry_seconds: Trade duration in seconds (default 5 min)
            confidence: Signal confidence 0-100
            source: Which system generated the signal
            signals_used: List of AI systems that agreed
            
        Returns:
            trade_id for reference
        """
        trade_id = f"T{int(time.time() * 1000)}"
        
        # Normalize direction
        norm_direction = direction.upper()
        if norm_direction in ("CALL", "BUY"):
            norm_direction = "BUY"
        elif norm_direction in ("PUT", "SELL"):
            norm_direction = "SELL"
        
        trade = PendingTrade(
            trade_id=trade_id,
            currency=currency,
            direction=norm_direction,
            entry_price=entry_price,
            entry_time=datetime.now().isoformat(),
            expiry_seconds=expiry_seconds,
            expiry_timestamp=time.time() + expiry_seconds,
            confidence=confidence,
            source=source,
            signals_used=signals_used or [source]
        )
        
        with self._lock:
            self.pending_trades.append(trade)
        
        print(f"📝 Trade recorded: {trade_id} — {norm_direction} {currency} @ {entry_price} "
              f"(expires in {expiry_seconds}s, conf: {confidence:.0f}%)")
        
        return trade_id
    
    def start(self):
        """Start the background outcome checker thread."""
        if self._running:
            return
        
        self._running = True
        self._thread = threading.Thread(target=self._checker_loop, daemon=True, name="AutoLearner")
        self._thread.start()
        print("🔄 Auto-Learner background checker started")
    
    def stop(self):
        """Stop the background checker."""
        self._running = False
        if self._thread:
            self._thread.join(timeout=5)
    
    def _checker_loop(self):
        """Background loop that checks pending trades for expiry."""
        while self._running:
            try:
                self._check_pending_trades()
            except Exception as e:
                print(f"⚠️ Auto-Learner error: {e}")
            time.sleep(10)  # Check every 10 seconds
    
    def _check_pending_trades(self):
        """Check all pending trades for expiry and determine outcomes."""
        now = time.time()
        trades_to_resolve: List[PendingTrade] = []
        
        with self._lock:
            for trade in self.pending_trades:
                # Add 5-second buffer after expiry to let price settle
                if now >= trade.expiry_timestamp + 5:
                    trades_to_resolve.append(trade)
        
        for trade in trades_to_resolve:
            self._resolve_trade(trade)
    
    def _resolve_trade(self, trade: PendingTrade):
        """Determine if a trade won or lost."""
        # Get current price for the currency
        current_prices = self.price_cache.get(trade.currency, [])
        
        if not current_prices:
            # No price data — mark as expired/unknown
            trade.status = "EXPIRED"
            trade.result_time = datetime.now().isoformat()
            print(f"❓ Trade {trade.trade_id} expired — no price data for {trade.currency}")
        else:
            exit_price = current_prices[-1] if isinstance(current_prices, list) else current_prices
            trade.exit_price = exit_price
            trade.result_time = datetime.now().isoformat()
            
            # Determine outcome
            price_diff = exit_price - trade.entry_price
            
            if trade.direction == "BUY":
                won = price_diff > 0  # Price went up = win for BUY
            else:  # SELL
                won = price_diff < 0  # Price went down = win for SELL
            
            trade.status = "WON" if won else "LOST"
            
            # Calculate P&L (for binary options: typically 80-92% payout on win, 100% loss)
            payout_rate = 0.85  # 85% payout (typical for Pocket Option)
            trade.pnl = payout_rate if won else -1.0  # As multiplier
            
            emoji = "✅" if won else "❌"
            diff_pips = abs(price_diff) * 10000 if trade.entry_price < 50 else abs(price_diff)
            print(f"{emoji} Trade {trade.trade_id}: {trade.direction} {trade.currency} — "
                  f"{'WON' if won else 'LOST'} "
                  f"(entry: {trade.entry_price:.5f}, exit: {exit_price:.5f}, "
                  f"diff: {diff_pips:.1f} pips)")
            
            # Feed result back into consensus engine
            self._feed_back_result(trade, won)
        
        # Move from pending to completed
        with self._lock:
            if trade in self.pending_trades:
                self.pending_trades.remove(trade)
            self.completed_trades.append(asdict(trade))
            
            # Trim history
            if len(self.completed_trades) > self.MAX_HISTORY:
                self.completed_trades = self.completed_trades[-self.MAX_HISTORY:]
        
        # Save to disk
        self._save_history()
    
    def _feed_back_result(self, trade: PendingTrade, won: bool):
        """Feed trade result back into AI systems for learning."""
        # Update consensus engine weights
        if self.consensus_engine:
            try:
                self.consensus_engine.record_result({
                    "won": won,
                    "profit": trade.pnl or 0,
                    "signals_used": trade.signals_used,
                    "symbol": trade.currency
                })
                print(f"  📊 Consensus weights updated from trade result")
            except Exception as e:
                print(f"  ⚠️ Failed to update consensus: {e}")
        
        # Update risk filter
        if self.risk_filter:
            try:
                self.risk_filter.record_trade_result(won, trade.pnl or 0)
            except Exception as e:
                print(f"  ⚠️ Failed to update risk filter: {e}")
    
    def get_stats(self) -> Dict:
        """Get learning statistics."""
        total = len(self.completed_trades)
        wins = sum(1 for t in self.completed_trades if t.get("status") == "WON")
        losses = sum(1 for t in self.completed_trades if t.get("status") == "LOST")
        expired = sum(1 for t in self.completed_trades if t.get("status") == "EXPIRED")
        
        # Per-currency stats
        currency_stats: Dict[str, Dict] = {}
        for t in self.completed_trades:
            curr = t.get("currency", "UNKNOWN")
            if curr not in currency_stats:
                currency_stats[curr] = {"wins": 0, "losses": 0, "total": 0}
            currency_stats[curr]["total"] += 1
            if t.get("status") == "WON":
                currency_stats[curr]["wins"] += 1
            elif t.get("status") == "LOST":
                currency_stats[curr]["losses"] += 1
        
        # Add win rates
        for curr in currency_stats:
            s = currency_stats[curr]
            decided = s["wins"] + s["losses"]
            s["win_rate"] = f"{(s['wins'] / decided * 100):.1f}%" if decided > 0 else "N/A"
        
        # Per-source stats
        source_stats: Dict[str, Dict] = {}
        for t in self.completed_trades:
            src = t.get("source", "unknown")
            if src not in source_stats:
                source_stats[src] = {"wins": 0, "losses": 0, "total": 0}
            source_stats[src]["total"] += 1
            if t.get("status") == "WON":
                source_stats[src]["wins"] += 1
            elif t.get("status") == "LOST":
                source_stats[src]["losses"] += 1
        
        for src in source_stats:
            s = source_stats[src]
            decided = s["wins"] + s["losses"]
            s["win_rate"] = f"{(s['wins'] / decided * 100):.1f}%" if decided > 0 else "N/A"
        
        decided = wins + losses
        overall_wr = f"{(wins / decided * 100):.1f}%" if decided > 0 else "N/A"
        
        return {
            "total_tracked": total,
            "pending": len(self.pending_trades),
            "wins": wins,
            "losses": losses,
            "expired": expired,
            "win_rate": overall_wr,
            "currency_breakdown": currency_stats,
            "source_breakdown": source_stats,
            "recent_trades": self.completed_trades[-10:]  # Last 10
        }
    
    def get_pending(self) -> List[Dict]:
        """Get currently pending trades."""
        with self._lock:
            return [asdict(t) for t in self.pending_trades]
    
    def _load_history(self):
        """Load trade history from disk."""
        try:
            if os.path.exists(self.HISTORY_FILE):
                with open(self.HISTORY_FILE, 'r') as f:
                    self.completed_trades = json.load(f)
                print(f"  📂 Loaded {len(self.completed_trades)} historical trades")
        except Exception as e:
            print(f"  ⚠️ Could not load trade history: {e}")
            self.completed_trades = []
    
    def _save_history(self):
        """Save trade history to disk."""
        try:
            with open(self.HISTORY_FILE, 'w') as f:
                json.dump(self.completed_trades, f, indent=2, default=str)
        except Exception as e:
            print(f"  ⚠️ Could not save trade history: {e}")


# Module-level singleton
_auto_learner: Optional[AutoLearner] = None

def get_auto_learner(price_cache: dict = None, consensus_engine=None, risk_filter=None) -> AutoLearner:
    """Get or create the auto-learner singleton."""
    global _auto_learner
    if _auto_learner is None:
        _auto_learner = AutoLearner(
            price_cache_ref=price_cache or {},
            consensus_engine_ref=consensus_engine,
            risk_filter_ref=risk_filter
        )
        _auto_learner.start()
    return _auto_learner
