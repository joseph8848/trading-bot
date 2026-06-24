"""
Candlestick Pattern Recognition Module
Detects key reversal and continuation patterns

Patterns implemented:
- Engulfing (Bullish/Bearish)
- Pin Bar (Hammer/Shooting Star)
- Doji (Indecision)
- Morning/Evening Star
"""

from typing import List, Dict, Optional, Tuple
from dataclasses import dataclass
from enum import Enum


class PatternType(Enum):
    """Candlestick pattern types"""
    BULLISH_ENGULFING = "BULLISH_ENGULFING"
    BEARISH_ENGULFING = "BEARISH_ENGULFING"
    HAMMER = "HAMMER"
    SHOOTING_STAR = "SHOOTING_STAR"
    BULLISH_PIN_BAR = "BULLISH_PIN_BAR"
    BEARISH_PIN_BAR = "BEARISH_PIN_BAR"
    DOJI = "DOJI"
    MORNING_STAR = "MORNING_STAR"
    EVENING_STAR = "EVENING_STAR"
    NO_PATTERN = "NO_PATTERN"


@dataclass
class CandleData:
    """Single candlestick data"""
    open: float
    high: float
    low: float
    close: float
    
    @property
    def body(self) -> float:
        """Absolute body size"""
        return abs(self.close - self.open)
    
    @property
    def upper_wick(self) -> float:
        """Upper wick/shadow size"""
        return self.high - max(self.open, self.close)
    
    @property
    def lower_wick(self) -> float:
        """Lower wick/shadow size"""
        return min(self.open, self.close) - self.low
    
    @property
    def total_range(self) -> float:
        """Total candle range"""
        return self.high - self.low
    
    @property
    def is_bullish(self) -> bool:
        """True if bullish (close > open)"""
        return self.close > self.open
    
    @property
    def is_bearish(self) -> bool:
        """True if bearish (close < open)"""
        return self.close < self.open


@dataclass
class PatternResult:
    """Result of pattern detection"""
    pattern: PatternType
    signal: str  # "CALL", "PUT", "WAIT"
    strength: float  # 0-100
    description: str


class CandlestickAnalyzer:
    """
    Analyzes candlestick patterns for trading signals
    
    Key patterns for binary options:
    - Engulfing: Strong reversal signal
    - Pin Bar: Price rejection, potential reversal
    - Doji: Indecision, wait for confirmation
    """
    
    def __init__(self):
        # Minimum body ratio for valid patterns
        self.min_body_ratio = 0.3  # Body must be at least 30% of previous
        self.doji_threshold = 0.1  # Doji if body < 10% of range
        self.pin_bar_wick_ratio = 2.0  # Wick must be 2x body
    
    def analyze(self, prices: List[float]) -> PatternResult:
        """
        Analyze recent prices for candlestick patterns
        
        Args:
            prices: List of prices (needs at least 8 for OHLC of 2 candles)
        
        Returns:
            PatternResult with detected pattern and signal
        """
        if len(prices) < 8:
            return PatternResult(
                PatternType.NO_PATTERN, 
                "WAIT", 
                0, 
                "Insufficient data"
            )
        
        # Create candles from prices (assumes 4 prices per candle: O,H,L,C)
        # If we have continuous prices, simulate OHLC
        candles = self._create_candles(prices)
        
        if len(candles) < 3:
            return PatternResult(
                PatternType.NO_PATTERN, 
                "WAIT", 
                0, 
                "Need at least 3 candles"
            )
        
        # Check patterns in order of reliability
        
        # 1. Check Engulfing patterns (most reliable)
        engulfing = self._check_engulfing(candles[-2], candles[-1])
        if engulfing.pattern != PatternType.NO_PATTERN:
            return engulfing
        
        # 2. Check Pin Bar patterns
        pin_bar = self._check_pin_bar(candles[-1])
        if pin_bar.pattern != PatternType.NO_PATTERN:
            return pin_bar
        
        # 3. Check Doji
        doji = self._check_doji(candles[-1])
        if doji.pattern != PatternType.NO_PATTERN:
            return doji
        
        # 4. Check Morning/Evening Star (3-candle pattern)
        star = self._check_star_patterns(candles[-3], candles[-2], candles[-1])
        if star.pattern != PatternType.NO_PATTERN:
            return star
        
        return PatternResult(
            PatternType.NO_PATTERN,
            "WAIT",
            0,
            "No clear pattern detected"
        )
    
    def _create_candles(self, prices: List[float], candle_size: int = 4) -> List[CandleData]:
        """
        Create candle data from price list
        
        Simulates OHLC from continuous prices:
        - Open: first price in period
        - High: max price in period
        - Low: min price in period
        - Close: last price in period
        """
        candles = []
        
        for i in range(0, len(prices) - candle_size + 1, candle_size):
            period = prices[i:i + candle_size]
            candle = CandleData(
                open=period[0],
                high=max(period),
                low=min(period),
                close=period[-1]
            )
            candles.append(candle)
        
        return candles
    
    def _check_engulfing(self, prev: CandleData, curr: CandleData) -> PatternResult:
        """
        Check for engulfing patterns
        
        Bullish Engulfing: Bearish candle followed by larger bullish candle
        Bearish Engulfing: Bullish candle followed by larger bearish candle
        """
        # Bullish Engulfing
        if prev.is_bearish and curr.is_bullish:
            if curr.body > prev.body * 1.1:  # Current body bigger by 10%+
                if curr.close >= prev.open and curr.open <= prev.close:
                    strength = min(100, 70 + (curr.body / prev.body - 1) * 30)
                    return PatternResult(
                        PatternType.BULLISH_ENGULFING,
                        "CALL",
                        strength,
                        f"Bullish Engulfing - Strong reversal signal (strength: {strength:.0f}%)"
                    )
        
        # Bearish Engulfing
        if prev.is_bullish and curr.is_bearish:
            if curr.body > prev.body * 1.1:
                if curr.open >= prev.close and curr.close <= prev.open:
                    strength = min(100, 70 + (curr.body / prev.body - 1) * 30)
                    return PatternResult(
                        PatternType.BEARISH_ENGULFING,
                        "PUT",
                        strength,
                        f"Bearish Engulfing - Strong reversal signal (strength: {strength:.0f}%)"
                    )
        
        return PatternResult(PatternType.NO_PATTERN, "WAIT", 0, "No engulfing")
    
    def _check_pin_bar(self, candle: CandleData) -> PatternResult:
        """
        Check for Pin Bar (Hammer/Shooting Star)
        
        Pin Bar characteristics:
        - Long wick (at least 2x body)
        - Small body (less than 1/3 of total range)
        - Wick on one side only
        """
        if candle.total_range == 0:
            return PatternResult(PatternType.NO_PATTERN, "WAIT", 0, "No range")
        
        body_ratio = candle.body / candle.total_range
        
        # Body should be small (less than 1/3)
        if body_ratio > 0.35:
            return PatternResult(PatternType.NO_PATTERN, "WAIT", 0, "Body too large")
        
        # Check for Hammer (Bullish Pin Bar) - long lower wick
        if candle.lower_wick > candle.body * self.pin_bar_wick_ratio:
            if candle.upper_wick < candle.lower_wick * 0.3:  # Small upper wick
                strength = min(100, 65 + (candle.lower_wick / candle.body) * 5)
                pattern = PatternType.HAMMER if candle.is_bullish else PatternType.BULLISH_PIN_BAR
                return PatternResult(
                    pattern,
                    "CALL",
                    strength,
                    f"Hammer/Bullish Pin Bar - Price rejection at lows (strength: {strength:.0f}%)"
                )
        
        # Check for Shooting Star (Bearish Pin Bar) - long upper wick
        if candle.upper_wick > candle.body * self.pin_bar_wick_ratio:
            if candle.lower_wick < candle.upper_wick * 0.3:  # Small lower wick
                strength = min(100, 65 + (candle.upper_wick / candle.body) * 5)
                pattern = PatternType.SHOOTING_STAR if candle.is_bearish else PatternType.BEARISH_PIN_BAR
                return PatternResult(
                    pattern,
                    "PUT",
                    strength,
                    f"Shooting Star/Bearish Pin Bar - Price rejection at highs (strength: {strength:.0f}%)"
                )
        
        return PatternResult(PatternType.NO_PATTERN, "WAIT", 0, "No pin bar")
    
    def _check_doji(self, candle: CandleData) -> PatternResult:
        """
        Check for Doji pattern
        
        Doji: Very small body (open ≈ close)
        Indicates indecision - wait for confirmation
        """
        if candle.total_range == 0:
            return PatternResult(PatternType.NO_PATTERN, "WAIT", 0, "No range")
        
        body_ratio = candle.body / candle.total_range
        
        if body_ratio < self.doji_threshold:
            return PatternResult(
                PatternType.DOJI,
                "WAIT",  # Doji = wait for confirmation
                50,
                "Doji - Market indecision, wait for next candle confirmation"
            )
        
        return PatternResult(PatternType.NO_PATTERN, "WAIT", 0, "No doji")
    
    def _check_star_patterns(self, first: CandleData, middle: CandleData, 
                            last: CandleData) -> PatternResult:
        """
        Check for Morning Star (bullish) and Evening Star (bearish)
        
        Morning Star: Bearish candle + small candle + bullish candle
        Evening Star: Bullish candle + small candle + bearish candle
        """
        # Middle candle should be small
        if first.total_range == 0:
            return PatternResult(PatternType.NO_PATTERN, "WAIT", 0, "No range")
        
        middle_ratio = middle.body / first.body if first.body > 0 else 0
        
        if middle_ratio > 0.5:  # Middle should be less than half of first
            return PatternResult(PatternType.NO_PATTERN, "WAIT", 0, "Middle not small")
        
        # Morning Star (Bullish)
        if first.is_bearish and last.is_bullish:
            if last.close > (first.open + first.close) / 2:  # Close above first midpoint
                strength = min(100, 75 + (last.body / first.body) * 10)
                return PatternResult(
                    PatternType.MORNING_STAR,
                    "CALL",
                    strength,
                    f"Morning Star - Bullish reversal pattern (strength: {strength:.0f}%)"
                )
        
        # Evening Star (Bearish)
        if first.is_bullish and last.is_bearish:
            if last.close < (first.open + first.close) / 2:  # Close below first midpoint
                strength = min(100, 75 + (last.body / first.body) * 10)
                return PatternResult(
                    PatternType.EVENING_STAR,
                    "PUT",
                    strength,
                    f"Evening Star - Bearish reversal pattern (strength: {strength:.0f}%)"
                )
        
        return PatternResult(PatternType.NO_PATTERN, "WAIT", 0, "No star pattern")
    
    def get_pattern_summary(self, prices: List[float]) -> Dict:
        """
        Get a summary of pattern analysis
        """
        result = self.analyze(prices)
        
        return {
            "pattern": result.pattern.value,
            "signal": result.signal,
            "strength": result.strength,
            "description": result.description,
            "should_trade": result.signal != "WAIT" and result.strength >= 65
        }


if __name__ == "__main__":
    import numpy as np
    
    print("=" * 60)
    print("Candlestick Pattern Analyzer Test")
    print("=" * 60)
    
    analyzer = CandlestickAnalyzer()
    
    # Test with simulated prices
    # Simulate a bullish engulfing scenario
    np.random.seed(42)
    
    # Create downtrend followed by reversal
    prices = []
    for i in range(20):
        base = 100 - i * 0.5  # Downtrend
        prices.extend([
            base + np.random.uniform(-0.1, 0.1),
            base + np.random.uniform(0, 0.3),
            base + np.random.uniform(-0.3, 0),
            base - 0.3 + np.random.uniform(-0.1, 0.1)
        ])
    
    # Add a strong bullish reversal
    prices.extend([88, 89, 87, 90])  # Bullish candle
    
    result = analyzer.analyze(prices)
    
    print(f"\nPattern: {result.pattern.value}")
    print(f"Signal: {result.signal}")
    print(f"Strength: {result.strength:.1f}%")
    print(f"Description: {result.description}")
