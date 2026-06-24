"""
Risk Filter Module
Filters trades based on risk factors

Features:
- Trading session awareness
- Consecutive loss protection
- Daily loss limits
- High-impact news filtering (basic)
"""

from datetime import datetime, time
from typing import Dict, Optional
from dataclasses import dataclass


@dataclass
class RiskAssessment:
    """Result of risk assessment"""
    can_trade: bool
    risk_score: float  # 0-100, higher = more risky
    position_size_multiplier: float  # 0.0 to 1.5
    reasons: list
    warnings: list


class RiskFilter:
    """Filters trades based on risk factors"""
    
    def __init__(self):
        # Session times (UTC)
        self.sessions = {
            "sydney": (time(22, 0), time(7, 0)),   # Sydney open
            "tokyo": (time(0, 0), time(9, 0)),     # Tokyo open
            "london": (time(8, 0), time(17, 0)),   # London open
            "new_york": (time(13, 0), time(22, 0)) # New York open
        }
        
        # Track performance
        self.consecutive_losses = 0
        self.daily_trades = 0
        self.daily_losses = 0
        self.daily_profit_loss = 0.0
        self.last_reset_date = datetime.now().date()
        self.last_trade_time: Optional[datetime] = None
        
        # Limits — PRODUCTION MODE (protect capital)
        self.max_consecutive_losses = 5    # Stop after 5 losses in a row
        self.max_daily_trades = 50         # Max 50 trades per day
        self.max_daily_loss_percent = 10.0 # Max 10% daily drawdown
        self.trade_cooldown_seconds = 60   # 60 seconds between trades
    
    def assess_risk(self, symbol: str, confidence: float, 
                    account_balance: float = 1000.0) -> RiskAssessment:
        """
        Assess if a trade should be taken based on risk factors
        
        Args:
            symbol: Currency pair
            confidence: Signal confidence (0-100)
            account_balance: Current account balance
        
        Returns:
            RiskAssessment with decision and reasons
        """
        self._check_daily_reset()
        
        reasons = []
        warnings = []
        risk_score = 0
        can_trade = True
        position_size = 1.0
        
        # Check consecutive losses
        if self.consecutive_losses >= self.max_consecutive_losses:
            can_trade = False
            reasons.append(f"⛔ {self.consecutive_losses} consecutive losses - take a break")
            risk_score += 40
        elif self.consecutive_losses >= 2:
            warnings.append(f"⚠️ {self.consecutive_losses} losses in a row")
            position_size *= 0.5
            risk_score += 20
        
        # Check daily trade limit
        if self.daily_trades >= self.max_daily_trades:
            can_trade = False
            reasons.append(f"⛔ Daily trade limit reached ({self.max_daily_trades})")
            risk_score += 30
        
        # Check daily loss limit
        loss_percent = (self.daily_profit_loss / account_balance) * 100 if account_balance > 0 else 0
        if loss_percent <= -self.max_daily_loss_percent:
            can_trade = False
            reasons.append(f"⛔ Daily loss limit reached ({loss_percent:.1f}%)")
            risk_score += 50
        elif loss_percent <= -3:
            warnings.append(f"⚠️ Down {abs(loss_percent):.1f}% today")
            position_size *= 0.7
            risk_score += 25
        
        # Check trading session and GOLDEN HOURS
        session_quality = self._get_session_quality()
        
        if session_quality["is_golden_hour"]:
            # London-NY overlap = 70% of forex volume!
            warnings.append("🏆 GOLDEN HOUR - London/NY overlap (best time!)")
            position_size *= 1.2  # Boost size during best hours
            risk_score -= 15
        elif session_quality["is_good_session"]:
            warnings.append(f"✅ {session_quality['session_name']} session active")
            # Normal position size
        elif session_quality["is_quiet"]:
            warnings.append(f"⚠️ Low activity period - reduced liquidity")
            position_size *= 0.6  # Reduce size during quiet periods
            risk_score += 20
        
        # Check trade cooldown
        if self.last_trade_time:
            elapsed = (datetime.now() - self.last_trade_time).total_seconds()
            if elapsed < self.trade_cooldown_seconds:
                can_trade = False
                remaining = int(self.trade_cooldown_seconds - elapsed)
                reasons.append(f"⏳ Cooldown active — wait {remaining}s before next trade")
                risk_score += 20
        
        # Check confidence — STRICT thresholds based on learning data
        # Data shows: 90%+ = 61% win rate (profitable), 70-80% = 26-40% (loses money)
        # Note: Temporarily lowered to 75% for Paper Trading demonstration
        if confidence < 75:
            can_trade = False
            reasons.append(f"⛔ Confidence {confidence:.0f}% too low — need 75%+ to trade profitably")
            risk_score += 30
        elif confidence < 85:
            warnings.append(f"💡 Good confidence ({confidence:.0f}%)")
            position_size *= 0.9
            risk_score += 5
        else:  # 92%+
            warnings.append(f"🔥 High confidence ({confidence:.0f}%) — strong signal")
            position_size *= 1.2
            risk_score -= 15
        
        # Check time of day for the symbol
        best_time = self._is_best_time_for_symbol(symbol)
        if not best_time:
            warnings.append(f"⚠️ Not optimal time for {symbol}")
            position_size *= 0.8
            risk_score += 10
        
        # Normalize risk score
        risk_score = max(0, min(100, risk_score))
        
        # Cap position size
        position_size = max(0.25, min(1.5, position_size))
        
        return RiskAssessment(
            can_trade=can_trade,
            risk_score=risk_score,
            position_size_multiplier=position_size,
            reasons=reasons,
            warnings=warnings
        )
    
    def record_trade_result(self, won: bool, profit_loss: float = 0):
        """Record a trade result for risk tracking"""
        self._check_daily_reset()
        
        self.daily_trades += 1
        self.daily_profit_loss += profit_loss
        
        if won:
            self.consecutive_losses = 0
        else:
            self.consecutive_losses += 1
            self.daily_losses += 1
    
    def _check_daily_reset(self):
        """Reset daily counters if new day"""
        today = datetime.now().date()
        if today != self.last_reset_date:
            self.daily_trades = 0
            self.daily_losses = 0
            self.daily_profit_loss = 0.0
            self.last_reset_date = today
    
    def _get_active_session(self) -> str:
        """Get currently active trading session"""
        now = datetime.utcnow().time()
        
        active = []
        
        for session, (start, end) in self.sessions.items():
            if start < end:
                if start <= now <= end:
                    active.append(session)
            else:  # Crosses midnight
                if now >= start or now <= end:
                    active.append(session)
        
        if len(active) >= 2:
            return f"Session overlap: {', '.join(active)} - Good liquidity"
        elif len(active) == 1:
            return f"{active[0].capitalize()} session active"
        else:
            return "Low activity period - reduced liquidity"
    
    def _get_session_quality(self) -> Dict:
        """
        Assess current trading session quality
        
        Returns dict with:
        - is_golden_hour: True if London-NY overlap (12:00-16:00 UTC)
        - is_good_session: True if major session active
        - is_quiet: True if low activity period
        - quality_score: 0-100
        - session_name: Description of current session
        """
        now = datetime.utcnow().time()
        
        # GOLDEN HOURS: London-NY overlap (12:00-16:00 UTC)
        # This is when 70% of forex volume happens!
        golden_start = time(12, 0)
        golden_end = time(16, 0)
        is_golden = golden_start <= now <= golden_end
        
        active = []
        for session, (start, end) in self.sessions.items():
            if start < end:
                if start <= now <= end:
                    active.append(session)
            else:  # Crosses midnight
                if now >= start or now <= end:
                    active.append(session)
        
        # Calculate quality
        if is_golden:
            return {
                "is_golden_hour": True,
                "is_good_session": True,
                "is_quiet": False,
                "quality_score": 100,
                "session_name": "London-NY Overlap (GOLDEN HOUR)"
            }
        elif len(active) >= 2:
            return {
                "is_golden_hour": False,
                "is_good_session": True,
                "is_quiet": False,
                "quality_score": 85,
                "session_name": f"{', '.join(active).title()} overlap"
            }
        elif len(active) == 1:
            return {
                "is_golden_hour": False,
                "is_good_session": True,
                "is_quiet": False,
                "quality_score": 70,
                "session_name": f"{active[0].title()} session"
            }
        else:
            return {
                "is_golden_hour": False,
                "is_good_session": False,
                "is_quiet": True,
                "quality_score": 30,
                "session_name": "Low activity period"
            }
    
    def _is_best_time_for_symbol(self, symbol: str) -> bool:
        """Check if current time is optimal for the symbol"""
        session_quality = self._get_session_quality()
        
        # Golden hour is good for ALL pairs
        if session_quality["is_golden_hour"]:
            return True
        
        now = datetime.utcnow().time()
        
        # Define optimal times for different pairs
        optimal_times = {
            "EUR/USD": [("london", "new_york")],  # Best during overlap
            "GBP/USD": [("london", "new_york")],
            "USD/JPY": [("tokyo", "new_york")],
            "EUR/JPY": [("tokyo", "london")],
            "AUD/USD": [("sydney", "tokyo")],
            "BTC/USD": [("new_york",)],  # Crypto peaks during US session
            "ETH/USD": [("new_york",)],
        }
        
        # Check if any base currency matches
        for pair, sessions in optimal_times.items():
            if pair in symbol or symbol in pair:
                for session_list in sessions:
                    if any(self._is_session_active(s, now) for s in session_list):
                        return True
        
        # Default: any major session is OK
        return session_quality["is_good_session"]
    
    def _is_session_active(self, session: str, current_time: time) -> bool:
        """Check if a specific session is active"""
        if session not in self.sessions:
            return False
        
        start, end = self.sessions[session]
        
        if start < end:
            return start <= current_time <= end
        else:
            return current_time >= start or current_time <= end
    
    def get_status(self) -> Dict:
        """Get current risk filter status"""
        return {
            "consecutive_losses": self.consecutive_losses,
            "daily_trades": self.daily_trades,
            "daily_losses": self.daily_losses,
            "daily_pnl": self.daily_profit_loss,
            "active_session": self._get_active_session(),
            "can_trade": self.consecutive_losses < self.max_consecutive_losses
        }


if __name__ == "__main__":
    # Test the risk filter
    risk_filter = RiskFilter()
    
    # Simulate some losses
    risk_filter.record_trade_result(won=False, profit_loss=-10)
    risk_filter.record_trade_result(won=False, profit_loss=-10)
    
    # Check risk
    assessment = risk_filter.assess_risk("EUR/USD", confidence=75, account_balance=1000)
    
    print("=" * 50)
    print("RISK ASSESSMENT")
    print("=" * 50)
    print(f"Can Trade: {assessment.can_trade}")
    print(f"Risk Score: {assessment.risk_score:.0f}/100")
    print(f"Position Size: {assessment.position_size_multiplier:.1f}x")
    
    if assessment.reasons:
        print("\nReasons:")
        for r in assessment.reasons:
            print(f"  {r}")
    
    if assessment.warnings:
        print("\nWarnings:")
        for w in assessment.warnings:
            print(f"  {w}")
    
    print("\n" + "=" * 50)
    print("Status:", risk_filter.get_status())
