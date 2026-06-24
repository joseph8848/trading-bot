"""
Market Analyzer Module
Provides advanced market analysis for the Elite Trading Bot

Features:
- Market condition detection (trending/ranging/volatile)
- Multi-timeframe analysis
- Momentum indicators
- Volatility calculations
"""

import numpy as np
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass
from enum import Enum


class MarketCondition(Enum):
    """Market condition states"""
    STRONG_UPTREND = "strong_uptrend"
    WEAK_UPTREND = "weak_uptrend"
    RANGING = "ranging"
    WEAK_DOWNTREND = "weak_downtrend"
    STRONG_DOWNTREND = "strong_downtrend"
    VOLATILE = "volatile"


@dataclass
class MarketAnalysis:
    """Result of market analysis"""
    condition: MarketCondition
    trend_strength: float  # 0-100
    volatility: float
    volatility_level: str  # "low", "normal", "high", "extreme"
    momentum: float  # -100 to 100
    support_levels: List[float]
    resistance_levels: List[float]
    recommendation: str


class MarketAnalyzer:
    """Analyzes market conditions for better trading decisions"""
    
    def __init__(self):
        self.price_history: Dict[str, List[float]] = {}
    
    def update_prices(self, symbol: str, prices: List[float]):
        """Update price history for a symbol"""
        self.price_history[symbol] = prices
    
    def analyze(self, symbol: str, prices: Optional[List[float]] = None) -> MarketAnalysis:
        """
        Perform comprehensive market analysis
        
        Args:
            symbol: Currency pair (e.g., "EUR/USD")
            prices: Optional price list (uses stored history if not provided)
        
        Returns:
            MarketAnalysis with all indicators
        """
        if prices is None:
            prices = self.price_history.get(symbol, [])
        
        if len(prices) < 20:
            return MarketAnalysis(
                condition=MarketCondition.RANGING,
                trend_strength=0,
                volatility=0,
                volatility_level="unknown",
                momentum=0,
                support_levels=[],
                resistance_levels=[],
                recommendation="Need more price data"
            )
        
        prices_arr = np.array(prices)
        
        # Calculate indicators
        trend_direction, trend_strength = self._calculate_trend(prices_arr)
        volatility = self._calculate_volatility(prices_arr)
        volatility_level = self._classify_volatility(volatility, prices_arr)
        momentum = self._calculate_momentum(prices_arr)
        support, resistance = self._find_support_resistance(prices_arr)
        
        # Determine market condition
        condition = self._determine_condition(trend_direction, trend_strength, volatility_level, momentum)
        
        # Generate recommendation
        recommendation = self._generate_recommendation(condition, trend_strength, volatility_level, momentum)
        
        return MarketAnalysis(
            condition=condition,
            trend_strength=trend_strength,
            volatility=volatility,
            volatility_level=volatility_level,
            momentum=momentum,
            support_levels=support,
            resistance_levels=resistance,
            recommendation=recommendation
        )
    
    def _calculate_trend(self, prices: np.ndarray) -> Tuple[str, float]:
        """Calculate trend direction and strength using moving averages"""
        if len(prices) < 20:
            return "neutral", 0
        
        # Simple Moving Averages
        sma_5 = np.mean(prices[-5:])
        sma_10 = np.mean(prices[-10:])
        sma_20 = np.mean(prices[-20:])
        
        # Trend direction
        if sma_5 > sma_10 > sma_20:
            direction = "up"
        elif sma_5 < sma_10 < sma_20:
            direction = "down"
        else:
            direction = "neutral"
        
        # Trend strength (based on MA separation)
        if direction != "neutral":
            spread = abs(sma_5 - sma_20) / sma_20 * 100
            strength = min(spread * 20, 100)  # Scale to 0-100
        else:
            strength = 0
        
        return direction, strength
    
    def _calculate_volatility(self, prices: np.ndarray) -> float:
        """Calculate volatility using standard deviation of returns"""
        if len(prices) < 10:
            return 0
        
        returns = np.diff(prices) / prices[:-1]
        volatility = np.std(returns) * 100  # As percentage
        return volatility
    
    def _classify_volatility(self, volatility: float, prices: np.ndarray) -> str:
        """Classify volatility level"""
        # Calculate historical average volatility for comparison
        if len(prices) < 50:
            avg_vol = volatility
        else:
            # Rolling volatility
            rolling_vols = []
            for i in range(10, len(prices)):
                returns = np.diff(prices[i-10:i]) / prices[i-10:i-1]
                rolling_vols.append(np.std(returns) * 100)
            avg_vol = np.mean(rolling_vols) if rolling_vols else volatility
        
        if avg_vol == 0:
            return "normal"
        
        ratio = volatility / avg_vol
        
        if ratio < 0.5:
            return "low"
        elif ratio < 1.5:
            return "normal"
        elif ratio < 2.5:
            return "high"
        else:
            return "extreme"
    
    def _calculate_momentum(self, prices: np.ndarray) -> float:
        """
        Calculate momentum using Rate of Change (ROC)
        Returns value from -100 to 100
        """
        if len(prices) < 14:
            return 0
        
        # 14-period ROC
        current = prices[-1]
        past = prices[-14]
        
        if past == 0:
            return 0
        
        roc = ((current - past) / past) * 100
        
        # Normalize to -100 to 100 range (cap at extremes)
        momentum = max(min(roc * 10, 100), -100)
        return momentum
    
    def _calculate_rsi(self, prices: np.ndarray, period: int = 14) -> float:
        """Calculate Relative Strength Index"""
        if len(prices) < period + 1:
            return 50
        
        deltas = np.diff(prices)
        gains = deltas.copy()
        losses = deltas.copy()
        
        gains[gains < 0] = 0
        losses[losses > 0] = 0
        losses = abs(losses)
        
        avg_gain = np.mean(gains[-period:])
        avg_loss = np.mean(losses[-period:])
        
        if avg_loss == 0:
            return 100
        
        rs = avg_gain / avg_loss
        rsi = 100 - (100 / (1 + rs))
        return rsi
    
    def _find_support_resistance(self, prices: np.ndarray) -> Tuple[List[float], List[float]]:
        """Find support and resistance levels using pivot points"""
        if len(prices) < 20:
            return [], []
        
        # Find local minima (support) and maxima (resistance)
        support_levels = []
        resistance_levels = []
        
        window = 5
        for i in range(window, len(prices) - window):
            # Check for local minimum
            if prices[i] == min(prices[i-window:i+window+1]):
                support_levels.append(prices[i])
            # Check for local maximum
            if prices[i] == max(prices[i-window:i+window+1]):
                resistance_levels.append(prices[i])
        
        # Keep most recent 3
        support_levels = sorted(set(support_levels))[-3:]
        resistance_levels = sorted(set(resistance_levels))[-3:]
        
        return support_levels, resistance_levels
    
    def _determine_condition(self, trend_direction: str, trend_strength: float, 
                            volatility_level: str, momentum: float) -> MarketCondition:
        """Determine overall market condition"""
        
        if volatility_level == "extreme":
            return MarketCondition.VOLATILE
        
        if trend_direction == "up":
            if trend_strength > 60:
                return MarketCondition.STRONG_UPTREND
            else:
                return MarketCondition.WEAK_UPTREND
        elif trend_direction == "down":
            if trend_strength > 60:
                return MarketCondition.STRONG_DOWNTREND
            else:
                return MarketCondition.WEAK_DOWNTREND
        else:
            return MarketCondition.RANGING
    
    def _generate_recommendation(self, condition: MarketCondition, trend_strength: float,
                                 volatility_level: str, momentum: float) -> str:
        """Generate trading recommendation based on analysis"""
        
        recommendations = []
        
        # Condition-based recommendations
        if condition == MarketCondition.VOLATILE:
            recommendations.append("⚠️ HIGH VOLATILITY - Reduce position size or wait")
        elif condition in [MarketCondition.STRONG_UPTREND, MarketCondition.STRONG_DOWNTREND]:
            recommendations.append(f"✅ Strong trend detected - Trade with trend")
        elif condition in [MarketCondition.WEAK_UPTREND, MarketCondition.WEAK_DOWNTREND]:
            recommendations.append("🔶 Weak trend - Use caution")
        else:
            recommendations.append("📊 Ranging market - Wait for breakout or use range strategies")
        
        # Volatility advice
        if volatility_level == "low":
            recommendations.append("📉 Low volatility - Expect smaller moves")
        elif volatility_level == "high":
            recommendations.append("📈 High volatility - Use wider stops")
        
        # Momentum advice
        if abs(momentum) > 70:
            if momentum > 0:
                recommendations.append("🚀 Strong bullish momentum")
            else:
                recommendations.append("💥 Strong bearish momentum")
        
        return " | ".join(recommendations)


# RSI Calculator for signal generation
def calculate_rsi(prices: List[float], period: int = 14) -> float:
    """Standalone RSI calculation"""
    if len(prices) < period + 1:
        return 50
    
    deltas = np.diff(prices)
    gains = np.where(deltas > 0, deltas, 0)
    losses = np.where(deltas < 0, -deltas, 0)
    
    avg_gain = np.mean(gains[-period:])
    avg_loss = np.mean(losses[-period:])
    
    if avg_loss == 0:
        return 100
    
    rs = avg_gain / avg_loss
    return 100 - (100 / (1 + rs))


def calculate_macd(prices: List[float]) -> Tuple[float, float, float]:
    """Calculate MACD indicator"""
    if len(prices) < 26:
        return 0, 0, 0
    
    prices_arr = np.array(prices)
    
    # Build MACD line series (need at least 9 points for signal line EMA-9)
    macd_series = []
    # Calculate MACD for rolling windows so we get enough points for signal line
    start = max(0, len(prices_arr) - 35)  # Get ~35 most recent MACD values
    for i in range(start, len(prices_arr)):
        if i >= 26:
            window = prices_arr[:i + 1]
            ema12 = _ema(window, 12)
            ema26 = _ema(window, 26)
            macd_series.append(ema12 - ema26)
    
    if not macd_series:
        return 0, 0, 0
    
    macd_line = macd_series[-1]
    
    # Signal line is EMA-9 of the MACD series
    if len(macd_series) >= 9:
        signal_line = _ema(np.array(macd_series), 9)
    else:
        signal_line = np.mean(macd_series)
    
    histogram = macd_line - signal_line
    
    return macd_line, signal_line, histogram


def _ema(prices: np.ndarray, period: int) -> float:
    """Calculate Exponential Moving Average"""
    if len(prices) < period:
        return np.mean(prices)
    
    multiplier = 2 / (period + 1)
    ema = prices[-period]
    
    for price in prices[-period+1:]:
        ema = (price * multiplier) + (ema * (1 - multiplier))
    
    return ema


if __name__ == "__main__":
    # Test the analyzer
    analyzer = MarketAnalyzer()
    
    # Generate some test prices (uptrend)
    test_prices = [1.0 + i * 0.001 + np.random.normal(0, 0.0005) for i in range(100)]
    
    result = analyzer.analyze("TEST/USD", test_prices)
    
    print(f"Market Condition: {result.condition.value}")
    print(f"Trend Strength: {result.trend_strength:.1f}%")
    print(f"Volatility: {result.volatility:.4f} ({result.volatility_level})")
    print(f"Momentum: {result.momentum:.1f}")
    print(f"Recommendation: {result.recommendation}")
