"""
Signal Generator Module
Generates trading signals based on technical analysis

Features:
- RSI overbought/oversold signals
- MACD crossover signals
- Momentum confirmation
- Multi-indicator confluence scoring
"""

import numpy as np
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass
from enum import Enum
from market_analyzer import MarketAnalyzer, MarketCondition, calculate_rsi, calculate_macd
from candlestick_patterns import CandlestickAnalyzer, PatternType


class SignalType(Enum):
    """Trading signal types"""
    STRONG_BUY = "STRONG_BUY"
    BUY = "BUY"
    WEAK_BUY = "WEAK_BUY"
    NEUTRAL = "NEUTRAL"
    WEAK_SELL = "WEAK_SELL"
    SELL = "SELL"
    STRONG_SELL = "STRONG_SELL"


@dataclass
class TradingSignal:
    """Complete trading signal with all analysis"""
    signal: SignalType
    confidence: float  # 0-100
    direction: str  # "CALL" or "PUT" or "WAIT"
    
    # Individual indicator signals
    rsi_signal: str
    macd_signal: str
    momentum_signal: str
    trend_signal: str
    pattern_signal: str  # NEW: Candlestick pattern
    
    # Optimal Expiry
    optimal_expiry_seconds: int
    
    # Analysis details
    rsi_value: float
    macd_value: float
    momentum_value: float
    trend_strength: float
    
    # Market context
    market_condition: str
    volatility_level: str
    
    # Recommendation
    recommendation: str
    risk_level: str  # "LOW", "MEDIUM", "HIGH"


class SignalGenerator:
    """Generates trading signals based on multiple indicators"""
    
    def __init__(self):
        self.market_analyzer = MarketAnalyzer()
        self.candlestick_analyzer = CandlestickAnalyzer()  # NEW: Pattern recognition
        
        # Signal weights for confluence - added patterns!
        self.weights = {
            'rsi': 0.20,
            'macd': 0.20,
            'momentum': 0.20,
            'trend': 0.20,
            'pattern': 0.20  # NEW: 20% weight for candlestick patterns
        }
    
    def _determine_expiry(self, market_analysis) -> int:
        """
        Determine the optimal trade expiry time based on market conditions.
        Returns expiry time in seconds.
        """
        # High volatility or fast trend = Shorter expiry (2-3 minutes)
        # Low volatility or slow trend = Longer expiry (5-10 minutes)
        
        condition = market_analysis.condition
        volatility = market_analysis.volatility_level
        trend_strength = market_analysis.trend_strength
        
        base_expiry = 300  # Default 5 minutes
        
        # Adjust based on trend
        if condition in [MarketCondition.STRONG_UPTREND, MarketCondition.STRONG_DOWNTREND]:
            if trend_strength > 70:
                base_expiry = 120  # 2 minutes for very strong, fast trends
            else:
                base_expiry = 180  # 3 minutes for strong trends
        elif condition in [MarketCondition.WEAK_UPTREND, MarketCondition.WEAK_DOWNTREND]:
            base_expiry = 300  # 5 minutes for weak trends
        elif condition == MarketCondition.CHOPPY:
            base_expiry = 600  # 10 minutes to ride out noise
            
        # Adjust based on volatility
        if volatility == "extreme":
            base_expiry = 600  # 10 minutes to ride out extreme swings
        elif volatility == "high":
            # If it's a strong trend, keep it short. If not, make it longer.
            if base_expiry <= 180:
                pass
            else:
                base_expiry = 420  # 7 minutes
                
        return base_expiry

    def generate_signal(self, symbol: str, prices: List[float]) -> TradingSignal:
        """
        Generate a complete trading signal for a symbol
        
        Args:
            symbol: Currency pair (e.g., "EUR/USD")
            prices: List of recent prices (at least 50 recommended)
        
        Returns:
            TradingSignal with all analysis and recommendation
        """
        if len(prices) < 30:
            return self._neutral_signal("Insufficient price data")
        
        # Get market analysis
        market_analysis = self.market_analyzer.analyze(symbol, prices)
        
        # Calculate individual indicators
        rsi = calculate_rsi(prices, 14)
        macd_line, signal_line, histogram = calculate_macd(prices)
        
        # Generate individual signals
        rsi_signal, rsi_score = self._analyze_rsi(rsi)
        macd_signal, macd_score = self._analyze_macd(macd_line, signal_line, histogram)
        momentum_signal, momentum_score = self._analyze_momentum(market_analysis.momentum)
        trend_signal, trend_score = self._analyze_trend(market_analysis.condition, market_analysis.trend_strength)
        
        # NEW: Analyze candlestick patterns
        pattern_result = self.candlestick_analyzer.analyze(prices)
        pattern_signal, pattern_score = self._analyze_pattern(pattern_result)
        
        # Calculate confluence score - now includes patterns!
        confluence_score = (
            rsi_score * self.weights['rsi'] +
            macd_score * self.weights['macd'] +
            momentum_score * self.weights['momentum'] +
            trend_score * self.weights['trend'] +
            pattern_score * self.weights['pattern']  # NEW
        )
        
        # Determine final signal
        signal_type, direction = self._determine_signal(confluence_score, market_analysis)
        
        # Calculate confidence
        confidence = self._calculate_confidence(confluence_score, market_analysis.volatility_level)
        
        # Determine risk level
        risk_level = self._assess_risk(market_analysis.volatility_level, confidence)
        
        # Generate recommendation
        recommendation = self._generate_recommendation(
            signal_type, direction, confidence, risk_level, market_analysis
        )
        
        # Determine optimal expiry
        optimal_expiry = self._determine_expiry(market_analysis)
        
        return TradingSignal(
            signal=signal_type,
            confidence=confidence,
            direction=direction,
            rsi_signal=rsi_signal,
            macd_signal=macd_signal,
            momentum_signal=momentum_signal,
            trend_signal=trend_signal,
            pattern_signal=pattern_signal,
            optimal_expiry_seconds=optimal_expiry,
            rsi_value=rsi,
            macd_value=macd_line,
            momentum_value=market_analysis.momentum,
            trend_strength=market_analysis.trend_strength,
            market_condition=market_analysis.condition.value,
            volatility_level=market_analysis.volatility_level,
            recommendation=recommendation,
            risk_level=risk_level
        )
    
    def _analyze_rsi(self, rsi: float) -> Tuple[str, float]:
        """Analyze RSI for signal"""
        if rsi < 20:
            return "OVERSOLD (Strong Buy)", 80
        elif rsi < 30:
            return "OVERSOLD", 60
        elif rsi < 40:
            return "Approaching oversold", 55
        elif rsi > 80:
            return "OVERBOUGHT (Strong Sell)", -80
        elif rsi > 70:
            return "OVERBOUGHT", -60
        elif rsi > 60:
            return "Approaching overbought", -55
        else:
            return "Neutral", 0
    
    def _analyze_macd(self, macd_line: float, signal_line: float, histogram: float) -> Tuple[str, float]:
        """Analyze MACD for signal"""
        if macd_line > signal_line:
            if histogram > 0:
                if macd_line > 0:
                    return "Bullish above zero", 70
                else:
                    return "Bullish crossover", 60
            else:
                return "Bullish weakening", 30
        elif macd_line < signal_line:
            if histogram < 0:
                if macd_line < 0:
                    return "Bearish below zero", -70
                else:
                    return "Bearish crossover", -60
            else:
                return "Bearish weakening", -30
        else:
            return "Neutral", 0
    
    def _analyze_momentum(self, momentum: float) -> Tuple[str, float]:
        """Analyze momentum for signal"""
        if momentum > 70:
            return "Strong bullish momentum", 80
        elif momentum > 40:
            return "Bullish momentum", 50
        elif momentum > 10:
            return "Weak bullish momentum", 20
        elif momentum < -70:
            return "Strong bearish momentum", -80
        elif momentum < -40:
            return "Bearish momentum", -50
        elif momentum < -10:
            return "Weak bearish momentum", -20
        else:
            return "No clear momentum", 0
    
    def _analyze_trend(self, condition: MarketCondition, strength: float) -> Tuple[str, float]:
        """Analyze trend for signal"""
        if condition == MarketCondition.STRONG_UPTREND:
            return "Strong uptrend", 80
        elif condition == MarketCondition.WEAK_UPTREND:
            return "Weak uptrend", 40
        elif condition == MarketCondition.STRONG_DOWNTREND:
            return "Strong downtrend", -80
        elif condition == MarketCondition.WEAK_DOWNTREND:
            return "Weak downtrend", -40
        elif condition == MarketCondition.VOLATILE:
            return "Volatile - no clear trend", 0
        else:
            return "Ranging", 0
    
    def _analyze_pattern(self, pattern_result) -> Tuple[str, float]:
        """Analyze candlestick pattern for signal"""
        pattern_name = pattern_result.pattern.value
        signal = pattern_result.signal
        strength = pattern_result.strength
        
        if signal == "CALL":
            # Bullish pattern
            if strength >= 75:
                return f"🕯️ {pattern_name} (Strong bullish)", 80
            elif strength >= 65:
                return f"🕯️ {pattern_name} (Bullish)", 60
            else:
                return f"🕯️ {pattern_name} (Weak bullish)", 40
        elif signal == "PUT":
            # Bearish pattern
            if strength >= 75:
                return f"🕯️ {pattern_name} (Strong bearish)", -80
            elif strength >= 65:
                return f"🕯️ {pattern_name} (Bearish)", -60
            else:
                return f"🕯️ {pattern_name} (Weak bearish)", -40
        else:
            # No pattern or WAIT signal
            return "No clear pattern", 0
    
    def _determine_signal(self, confluence_score: float, market_analysis) -> Tuple[SignalType, str]:
        """Determine final signal type and direction"""
        
        # Don't trade in extreme volatility
        if market_analysis.volatility_level == "extreme":
            return SignalType.NEUTRAL, "WAIT"
        
        if confluence_score >= 70:
            return SignalType.STRONG_BUY, "CALL"
        elif confluence_score >= 50:
            return SignalType.BUY, "CALL"
        elif confluence_score >= 30:
            return SignalType.WEAK_BUY, "CALL"
        elif confluence_score <= -70:
            return SignalType.STRONG_SELL, "PUT"
        elif confluence_score <= -50:
            return SignalType.SELL, "PUT"
        elif confluence_score <= -30:
            return SignalType.WEAK_SELL, "PUT"
        else:
            return SignalType.NEUTRAL, "WAIT"
    
    def _calculate_confidence(self, confluence_score: float, volatility_level: str) -> float:
        """Calculate signal confidence"""
        base_confidence = abs(confluence_score)
        
        # Adjust for volatility
        volatility_adjustments = {
            "low": 5,      # Slightly more confident
            "normal": 0,
            "high": -10,   # Less confident
            "extreme": -25
        }
        
        adjustment = volatility_adjustments.get(volatility_level, 0)
        confidence = base_confidence + adjustment
        
        return max(0, min(100, confidence))
    
    def _assess_risk(self, volatility_level: str, confidence: float) -> str:
        """Assess trade risk level"""
        if volatility_level in ["high", "extreme"]:
            return "HIGH"
        elif volatility_level == "normal" and confidence < 60:
            return "MEDIUM"
        elif confidence >= 70:
            return "LOW"
        else:
            return "MEDIUM"
    
    def _generate_recommendation(self, signal_type: SignalType, direction: str, 
                                 confidence: float, risk_level: str, 
                                 market_analysis) -> str:
        """Generate human-readable recommendation"""
        
        if direction == "WAIT":
            return "⏸️ No clear signal - wait for better setup"
        
        # Action recommendation
        if signal_type in [SignalType.STRONG_BUY, SignalType.STRONG_SELL]:
            action = f"✅ {direction} with confidence"
        elif signal_type in [SignalType.BUY, SignalType.SELL]:
            action = f"🔶 {direction} with caution"
        else:
            action = f"⚠️ Weak {direction} signal"
        
        # Risk advice
        risk_advice = ""
        if risk_level == "HIGH":
            risk_advice = " | Reduce position size"
        elif risk_level == "MEDIUM":
            risk_advice = " | Normal position size"
        else:
            risk_advice = " | Can increase position size"
        
        # Volatility note
        vol_note = ""
        if market_analysis.volatility_level == "high":
            vol_note = " | High volatility - use wider stop"
        
        return f"{action} (Conf: {confidence:.0f}%){risk_advice}{vol_note}"
    
    def _neutral_signal(self, reason: str) -> TradingSignal:
        """Return a neutral signal with a reason"""
        return TradingSignal(
            signal=SignalType.NEUTRAL,
            confidence=0,
            direction="WAIT",
            rsi_signal="N/A",
            macd_signal="N/A",
            momentum_signal="N/A",
            trend_signal="N/A",
            pattern_signal="N/A",
            optimal_expiry_seconds=300,
            rsi_value=50,
            macd_value=0,
            momentum_value=0,
            trend_strength=0,
            market_condition="unknown",
            volatility_level="unknown",
            recommendation=f"⏸️ {reason}",
            risk_level="HIGH"
        )


if __name__ == "__main__":
    # Test the signal generator
    generator = SignalGenerator()
    
    # Generate test prices with an uptrend
    np.random.seed(42)
    test_prices = [1.0 + i * 0.002 + np.random.normal(0, 0.001) for i in range(100)]
    
    signal = generator.generate_signal("EUR/USD", test_prices)
    
    print("=" * 50)
    print("TRADING SIGNAL ANALYSIS")
    print("=" * 50)
    print(f"Signal: {signal.signal.value}")
    print(f"Direction: {signal.direction}")
    print(f"Confidence: {signal.confidence:.1f}%")
    print(f"Risk Level: {signal.risk_level}")
    print("-" * 50)
    print(f"RSI ({signal.rsi_value:.1f}): {signal.rsi_signal}")
    print(f"MACD ({signal.macd_value:.4f}): {signal.macd_signal}")
    print(f"Momentum ({signal.momentum_value:.1f}): {signal.momentum_signal}")
    print(f"Trend ({signal.trend_strength:.1f}%): {signal.trend_signal}")
    print("-" * 50)
    print(f"Market: {signal.market_condition}")
    print(f"Volatility: {signal.volatility_level}")
    print("=" * 50)
    print(f"RECOMMENDATION: {signal.recommendation}")
    print("=" * 50)
