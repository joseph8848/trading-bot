"""
FinGPT Service - Financial AI Integration
Full capabilities for trading analysis

This module provides:
1. Sentiment Analysis - Analyze news/text for market mood
2. Price Prediction - Forecast price direction
3. Market Forecasting - Weekly/daily outlook
4. News Analysis - Real-time financial news impact
5. Risk Assessment - Position risk evaluation
6. Trading Signal - Combined analysis signal

FinGPT runs LOCALLY (no API limits!)
Configured for CPU mode for maximum compatibility.
"""

import os
import json
import re
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any
import numpy as np

# Try to import FinGPT components
FINGPT_AVAILABLE = False
FINGPT_MODEL = None

try:
    # Check if fingpt is installed
    import torch
    from transformers import AutoTokenizer, AutoModelForCausalLM, pipeline
    
    # Force CPU mode
    DEVICE = "cpu"
    print(f"🔮 FinGPT: Using {DEVICE} for inference")
    
    FINGPT_AVAILABLE = True
except ImportError as e:
    print(f"⚠️ FinGPT dependencies not installed: {e}")
    print("   Run: pip install transformers torch sentencepiece")
    FINGPT_AVAILABLE = False


class FinGPTService:
    """
    Complete FinGPT integration for trading analysis.
    
    Features:
    - Sentiment analysis of financial text
    - Price movement prediction
    - Market regime detection
    - News impact assessment
    - Risk evaluation
    - Combined trading signals
    
    All processing runs LOCALLY - no API limits!
    """
    
    def __init__(self, use_lightweight=True):
        """
        Initialize FinGPT service.
        
        Args:
            use_lightweight: If True, use a lighter model for faster CPU inference
        """
        self.model = None
        self.tokenizer = None
        self.sentiment_pipeline = None
        self.available = False
        self.use_lightweight = use_lightweight
        
        # Sentiment keywords for fast fallback analysis
        self.bullish_keywords = [
            'bullish', 'surge', 'rally', 'soar', 'gain', 'rise', 'jump', 'climb',
            'breakthrough', 'outperform', 'beat', 'exceed', 'strong', 'growth',
            'positive', 'upgrade', 'buy', 'optimistic', 'recovery', 'momentum',
            'breakout', 'support', 'uptrend', 'higher', 'profit', 'success'
        ]
        
        self.bearish_keywords = [
            'bearish', 'crash', 'plunge', 'drop', 'fall', 'decline', 'sink',
            'tumble', 'underperform', 'miss', 'weak', 'negative', 'downgrade',
            'sell', 'pessimistic', 'recession', 'fear', 'breakdown', 'resistance',
            'downtrend', 'lower', 'loss', 'fail', 'warning', 'risk', 'concern'
        ]
        
        # Initialize model if available
        if FINGPT_AVAILABLE:
            self._initialize_model()
        else:
            print("⚠️ FinGPT running in FALLBACK mode (keyword-based analysis)")
            print("   For full AI capabilities, install: pip install transformers torch")
    
    def _initialize_model(self):
        """Initialize the FinGPT model for local inference."""
        try:
            if self.use_lightweight:
                # Use a lightweight sentiment model for CPU
                # This is faster and still effective for trading signals
                print("🔮 Loading lightweight FinGPT sentiment model...")
                
                # Use FinBERT - a BERT model fine-tuned on financial text
                # Much faster on CPU than full LLM models
                from transformers import BertTokenizer, BertForSequenceClassification
                
                model_name = "ProsusAI/finbert"
                self.tokenizer = BertTokenizer.from_pretrained(model_name)
                self.model = BertForSequenceClassification.from_pretrained(model_name)
                self.model.eval()
                
                # Create sentiment pipeline
                self.sentiment_pipeline = pipeline(
                    "sentiment-analysis",
                    model=self.model,
                    tokenizer=self.tokenizer,
                    device=-1  # CPU
                )
                
                print("✅ FinGPT (FinBERT) loaded successfully!")
                self.available = True
                
            else:
                # Full FinGPT model (requires more resources)
                print("🔮 Loading full FinGPT model (this may take a while)...")
                # Note: Full model loading would go here
                # For now, we use the lightweight version for CPU compatibility
                self.available = False
                
        except Exception as e:
            print(f"⚠️ Failed to load FinGPT model: {e}")
            print("   Running in fallback mode")
            self.available = False
    
    def is_available(self) -> bool:
        """Check if FinGPT is available for analysis."""
        return self.available or FINGPT_AVAILABLE
    
    # ========== 1. SENTIMENT ANALYSIS ==========
    
    def analyze_sentiment(self, text: str) -> Dict[str, Any]:
        """
        Analyze sentiment of financial text.
        
        Args:
            text: Financial news, tweet, or analysis text
            
        Returns:
            {
                "sentiment": "bullish" | "bearish" | "neutral",
                "score": 0.0-1.0 (confidence),
                "details": {...}
            }
        """
        if not text or len(text.strip()) < 10:
            return {
                "sentiment": "neutral",
                "score": 0.5,
                "details": {"error": "Text too short"}
            }
        
        try:
            if self.available and self.sentiment_pipeline:
                # Use FinBERT model
                result = self.sentiment_pipeline(text[:512])[0]  # Limit length
                
                label = result['label'].lower()
                score = result['score']
                
                # Map FinBERT labels to our format
                if label == 'positive':
                    sentiment = 'bullish'
                elif label == 'negative':
                    sentiment = 'bearish'
                else:
                    sentiment = 'neutral'
                
                return {
                    "sentiment": sentiment,
                    "score": score,
                    "details": {
                        "model": "FinBERT",
                        "raw_label": label,
                        "raw_score": score
                    }
                }
            else:
                # Fallback: Keyword-based analysis
                return self._keyword_sentiment(text)
                
        except Exception as e:
            print(f"Sentiment analysis error: {e}")
            return self._keyword_sentiment(text)
    
    def _keyword_sentiment(self, text: str) -> Dict[str, Any]:
        """Fallback keyword-based sentiment analysis."""
        text_lower = text.lower()
        
        bullish_count = sum(1 for kw in self.bullish_keywords if kw in text_lower)
        bearish_count = sum(1 for kw in self.bearish_keywords if kw in text_lower)
        total = bullish_count + bearish_count
        
        if total == 0:
            return {"sentiment": "neutral", "score": 0.5, "details": {"method": "keyword"}}
        
        bullish_ratio = bullish_count / total
        
        if bullish_ratio > 0.6:
            sentiment = "bullish"
            score = 0.5 + (bullish_ratio - 0.5)
        elif bullish_ratio < 0.4:
            sentiment = "bearish"
            score = 0.5 + (0.5 - bullish_ratio)
        else:
            sentiment = "neutral"
            score = 0.5
        
        return {
            "sentiment": sentiment,
            "score": min(0.95, score),
            "details": {
                "method": "keyword",
                "bullish_hits": bullish_count,
                "bearish_hits": bearish_count
            }
        }
    
    # ========== 2. PRICE PREDICTION ==========
    
    def predict_movement(self, symbol: str, prices: List[float], 
                         news_context: str = None) -> Dict[str, Any]:
        """
        Predict price movement direction.
        
        Args:
            symbol: Trading symbol (e.g., "EUR/USD")
            prices: Historical price data
            news_context: Optional recent news text
            
        Returns:
            {
                "direction": "up" | "down" | "sideways",
                "confidence": 0.0-1.0,
                "reasoning": "...",
                "factors": [...]
            }
        """
        if not prices or len(prices) < 20:
            return {
                "direction": "sideways",
                "confidence": 0.3,
                "reasoning": "Insufficient price data",
                "factors": []
            }
        
        factors = []
        score = 0  # Positive = up, Negative = down
        
        # Technical factors
        prices_arr = np.array(prices)
        
        # 1. Trend analysis
        short_ma = np.mean(prices_arr[-5:])
        long_ma = np.mean(prices_arr[-20:])
        trend = (short_ma - long_ma) / long_ma * 100
        
        if trend > 0.1:
            score += 2
            factors.append(f"Uptrend: Short MA above Long MA by {trend:.2f}%")
        elif trend < -0.1:
            score -= 2
            factors.append(f"Downtrend: Short MA below Long MA by {abs(trend):.2f}%")
        
        # 2. Momentum
        momentum = (prices_arr[-1] - prices_arr[-10]) / prices_arr[-10] * 100
        if momentum > 0.2:
            score += 1
            factors.append(f"Positive momentum: +{momentum:.2f}%")
        elif momentum < -0.2:
            score -= 1
            factors.append(f"Negative momentum: {momentum:.2f}%")
        
        # 3. RSI-like overbought/oversold
        recent_changes = np.diff(prices_arr[-14:])
        gains = np.mean(recent_changes[recent_changes > 0]) if np.any(recent_changes > 0) else 0
        losses = abs(np.mean(recent_changes[recent_changes < 0])) if np.any(recent_changes < 0) else 0
        
        if losses > 0:
            rs = gains / losses
            rsi = 100 - (100 / (1 + rs))
        else:
            rsi = 100
        
        if rsi > 70:
            score -= 1
            factors.append(f"Overbought (RSI: {rsi:.1f})")
        elif rsi < 30:
            score += 1
            factors.append(f"Oversold (RSI: {rsi:.1f})")
        
        # 4. News sentiment (if provided)
        if news_context:
            news_sentiment = self.analyze_sentiment(news_context)
            if news_sentiment['sentiment'] == 'bullish':
                score += news_sentiment['score']
                factors.append(f"Bullish news sentiment ({news_sentiment['score']:.0%})")
            elif news_sentiment['sentiment'] == 'bearish':
                score -= news_sentiment['score']
                factors.append(f"Bearish news sentiment ({news_sentiment['score']:.0%})")
        
        # 5. Price action patterns
        last_3 = prices_arr[-3:]
        if last_3[0] < last_3[1] < last_3[2]:
            score += 0.5
            factors.append("3 consecutive higher closes")
        elif last_3[0] > last_3[1] > last_3[2]:
            score -= 0.5
            factors.append("3 consecutive lower closes")
        
        # Calculate final prediction
        if score > 1:
            direction = "up"
            confidence = min(0.85, 0.5 + abs(score) * 0.1)
        elif score < -1:
            direction = "down"
            confidence = min(0.85, 0.5 + abs(score) * 0.1)
        else:
            direction = "sideways"
            confidence = 0.5
        
        return {
            "direction": direction,
            "confidence": confidence,
            "reasoning": f"Based on {len(factors)} technical and sentiment factors",
            "factors": factors,
            "raw_score": score
        }
    
    # ========== 3. MARKET FORECASTING ==========
    
    def forecast_market(self, symbol: str, prices: List[float] = None) -> Dict[str, Any]:
        """
        Generate market forecast/outlook.
        
        Args:
            symbol: Trading symbol
            prices: Optional historical prices
            
        Returns:
            {
                "outlook": "bullish" | "bearish" | "neutral",
                "timeframe": "short-term",
                "key_factors": [...],
                "confidence": 0.0-1.0
            }
        """
        key_factors = []
        outlook_score = 0
        
        if prices and len(prices) >= 50:
            prices_arr = np.array(prices)
            
            # Overall trend (50-period)
            start_price = prices_arr[0]
            end_price = prices_arr[-1]
            overall_change = (end_price - start_price) / start_price * 100
            
            if overall_change > 1:
                outlook_score += 2
                key_factors.append(f"Strong upward trend: +{overall_change:.2f}%")
            elif overall_change < -1:
                outlook_score -= 2
                key_factors.append(f"Strong downward trend: {overall_change:.2f}%")
            
            # Volatility
            volatility = np.std(np.diff(prices_arr) / prices_arr[:-1]) * 100
            if volatility > 0.5:
                key_factors.append(f"High volatility: {volatility:.2f}%")
            elif volatility < 0.2:
                key_factors.append(f"Low volatility: {volatility:.2f}%")
            
            # Support/Resistance proximity
            recent_high = np.max(prices_arr[-20:])
            recent_low = np.min(prices_arr[-20:])
            current = prices_arr[-1]
            
            if current >= recent_high * 0.99:
                key_factors.append("Price near resistance level")
                outlook_score -= 0.5
            elif current <= recent_low * 1.01:
                key_factors.append("Price near support level")
                outlook_score += 0.5
        
        # Determine outlook
        if outlook_score > 1:
            outlook = "bullish"
            confidence = min(0.8, 0.5 + outlook_score * 0.1)
        elif outlook_score < -1:
            outlook = "bearish"
            confidence = min(0.8, 0.5 + abs(outlook_score) * 0.1)
        else:
            outlook = "neutral"
            confidence = 0.5
        
        return {
            "outlook": outlook,
            "timeframe": "short-term",
            "key_factors": key_factors if key_factors else ["Insufficient data for detailed analysis"],
            "confidence": confidence,
            "symbol": symbol
        }
    
    # ========== 4. NEWS ANALYSIS ==========
    
    def analyze_news(self, headlines: List[str], symbol: str = None) -> Dict[str, Any]:
        """
        Analyze multiple news headlines for trading impact.
        
        Args:
            headlines: List of news headlines
            symbol: Optional symbol to focus on
            
        Returns:
            {
                "overall_sentiment": "bullish" | "bearish" | "neutral",
                "impact": "high" | "medium" | "low",
                "headline_analysis": [...],
                "trading_recommendation": "..."
            }
        """
        if not headlines:
            return {
                "overall_sentiment": "neutral",
                "impact": "low",
                "headline_analysis": [],
                "trading_recommendation": "No news to analyze"
            }
        
        headline_results = []
        sentiments = []
        
        for headline in headlines[:10]:  # Limit to 10 headlines
            sentiment = self.analyze_sentiment(headline)
            headline_results.append({
                "headline": headline[:100],
                "sentiment": sentiment['sentiment'],
                "score": sentiment['score']
            })
            sentiments.append(sentiment)
        
        # Calculate overall sentiment
        bullish_count = sum(1 for s in sentiments if s['sentiment'] == 'bullish')
        bearish_count = sum(1 for s in sentiments if s['sentiment'] == 'bearish')
        
        total = len(sentiments)
        if bullish_count > bearish_count and bullish_count > total * 0.4:
            overall = "bullish"
            impact = "high" if bullish_count > total * 0.6 else "medium"
        elif bearish_count > bullish_count and bearish_count > total * 0.4:
            overall = "bearish"
            impact = "high" if bearish_count > total * 0.6 else "medium"
        else:
            overall = "neutral"
            impact = "low"
        
        # Trading recommendation
        if impact == "high":
            if overall == "bullish":
                rec = "News strongly supports BUY positions"
            else:
                rec = "News strongly supports SELL positions"
        elif impact == "medium":
            rec = f"News leans {overall}, consider with other factors"
        else:
            rec = "News is mixed/neutral, rely on technical analysis"
        
        return {
            "overall_sentiment": overall,
            "impact": impact,
            "headline_analysis": headline_results,
            "trading_recommendation": rec,
            "stats": {
                "total": total,
                "bullish": bullish_count,
                "bearish": bearish_count,
                "neutral": total - bullish_count - bearish_count
            }
        }
    
    # ========== 5. RISK ASSESSMENT ==========
    
    def assess_risk(self, symbol: str, prices: List[float], 
                   position_size: float = 1.0,
                   account_balance: float = None) -> Dict[str, Any]:
        """
        Assess trading risk for a potential position.
        
        Args:
            symbol: Trading symbol
            prices: Price history
            position_size: Proposed position size multiplier
            account_balance: Optional account balance for percentage calculations
            
        Returns:
            {
                "risk_level": "low" | "medium" | "high" | "extreme",
                "risk_score": 0-100,
                "factors": [...],
                "recommendation": "proceed" | "reduce_size" | "avoid"
            }
        """
        if not prices or len(prices) < 20:
            return {
                "risk_level": "high",
                "risk_score": 70,
                "factors": ["Insufficient price data for proper assessment"],
                "recommendation": "avoid"
            }
        
        prices_arr = np.array(prices)
        risk_score = 0
        factors = []
        
        # 1. Volatility risk
        volatility = np.std(np.diff(prices_arr) / prices_arr[:-1]) * 100
        if volatility > 1.0:
            risk_score += 30
            factors.append(f"High volatility: {volatility:.2f}%")
        elif volatility > 0.5:
            risk_score += 15
            factors.append(f"Moderate volatility: {volatility:.2f}%")
        else:
            factors.append(f"Low volatility: {volatility:.2f}%")
        
        # 2. Trend uncertainty
        short_ma = np.mean(prices_arr[-5:])
        long_ma = np.mean(prices_arr[-20:])
        trend_diff = abs(short_ma - long_ma) / long_ma * 100
        
        if trend_diff < 0.1:
            risk_score += 20
            factors.append("Unclear trend direction")
        
        # 3. Position size risk
        if position_size > 1.5:
            risk_score += 25
            factors.append(f"Large position size: {position_size:.1f}x")
        elif position_size > 1.0:
            risk_score += 10
            factors.append(f"Above-normal position: {position_size:.1f}x")
        
        # 4. Recent loss potential (max drawdown in recent data)
        rolling_max = np.maximum.accumulate(prices_arr[-50:])
        drawdowns = (rolling_max - prices_arr[-50:]) / rolling_max * 100
        max_drawdown = np.max(drawdowns)
        
        if max_drawdown > 2:
            risk_score += 20
            factors.append(f"Recent drawdown: {max_drawdown:.2f}%")
        
        # Determine risk level and recommendation
        if risk_score >= 60:
            risk_level = "extreme"
            recommendation = "avoid"
        elif risk_score >= 40:
            risk_level = "high"
            recommendation = "reduce_size"
        elif risk_score >= 20:
            risk_level = "medium"
            recommendation = "proceed"
        else:
            risk_level = "low"
            recommendation = "proceed"
        
        return {
            "risk_level": risk_level,
            "risk_score": min(100, risk_score),
            "factors": factors,
            "recommendation": recommendation,
            "suggested_size": max(0.25, 1.0 - risk_score / 100)
        }
    
    # ========== 6. TRADING SIGNAL (COMBINED) ==========
    
    def get_signal(self, symbol: str, prices: List[float],
                  news_headlines: List[str] = None) -> Dict[str, Any]:
        """
        Generate a comprehensive trading signal combining all analyses.
        
        This is the MAIN method for trading decisions.
        
        Args:
            symbol: Trading symbol
            prices: Price history
            news_headlines: Optional recent news
            
        Returns:
            {
                "signal": "BUY" | "SELL" | "WAIT",
                "confidence": 0-100,
                "reasoning": "...",
                "components": {
                    "prediction": {...},
                    "forecast": {...},
                    "news": {...},
                    "risk": {...}
                }
            }
        """
        # Gather all analyses
        prediction = self.predict_movement(symbol, prices)
        forecast = self.forecast_market(symbol, prices)
        risk = self.assess_risk(symbol, prices)
        
        news = None
        if news_headlines:
            news = self.analyze_news(news_headlines, symbol)
        
        # Calculate composite signal
        signal_score = 0  # Positive = BUY, Negative = SELL
        reasons = []
        
        # 1. Prediction contribution (40% weight)
        if prediction['direction'] == 'up':
            signal_score += prediction['confidence'] * 40
            reasons.append(f"Price prediction: UP ({prediction['confidence']:.0%})")
        elif prediction['direction'] == 'down':
            signal_score -= prediction['confidence'] * 40
            reasons.append(f"Price prediction: DOWN ({prediction['confidence']:.0%})")
        
        # 2. Forecast contribution (30% weight)
        if forecast['outlook'] == 'bullish':
            signal_score += forecast['confidence'] * 30
            reasons.append(f"Market outlook: BULLISH ({forecast['confidence']:.0%})")
        elif forecast['outlook'] == 'bearish':
            signal_score -= forecast['confidence'] * 30
            reasons.append(f"Market outlook: BEARISH ({forecast['confidence']:.0%})")
        
        # 3. News contribution (20% weight) - if available
        if news:
            if news['overall_sentiment'] == 'bullish':
                weight = 20 if news['impact'] == 'high' else 10
                signal_score += weight
                reasons.append(f"News sentiment: BULLISH ({news['impact']} impact)")
            elif news['overall_sentiment'] == 'bearish':
                weight = 20 if news['impact'] == 'high' else 10
                signal_score -= weight
                reasons.append(f"News sentiment: BEARISH ({news['impact']} impact)")
        
        # 4. Risk adjustment (10% weight)
        if risk['risk_level'] in ['high', 'extreme']:
            # Reduce signal strength in high risk
            signal_score *= 0.5
            reasons.append(f"High risk: Signal reduced ({risk['risk_level']})")
        
        # Determine final signal
        confidence = min(95, abs(signal_score))
        
        if signal_score > 25:
            signal = "BUY"
        elif signal_score < -25:
            signal = "SELL"
        else:
            signal = "WAIT"
            confidence = 50 + abs(signal_score)
        
        return {
            "signal": signal,
            "confidence": confidence,
            "reasoning": " | ".join(reasons),
            "raw_score": signal_score,
            "components": {
                "prediction": prediction,
                "forecast": forecast,
                "news": news,
                "risk": risk
            },
            "position_size": risk.get('suggested_size', 1.0)
        }
    
    def get_status(self) -> Dict[str, Any]:
        """Get FinGPT service status."""
        return {
            "available": self.is_available(),
            "model_loaded": self.available,
            "using_fallback": not self.available,
            "mode": "CPU",
            "model_type": "FinBERT" if self.available else "Keyword-based"
        }


# Singleton instance
_instance = None

def get_fingpt_service() -> FinGPTService:
    """Get the FinGPT service singleton."""
    global _instance
    if _instance is None:
        _instance = FinGPTService(use_lightweight=True)
    return _instance


if __name__ == "__main__":
    # Test the service
    print("=" * 50)
    print("🔮 Testing FinGPT Service")
    print("=" * 50)
    
    service = get_fingpt_service()
    print(f"\nStatus: {service.get_status()}")
    
    # Test sentiment
    print("\n--- Sentiment Analysis ---")
    test_text = "The stock market rallied today on strong earnings reports."
    result = service.analyze_sentiment(test_text)
    print(f"Text: {test_text}")
    print(f"Result: {result}")
    
    # Test prediction with dummy prices
    print("\n--- Price Prediction ---")
    dummy_prices = [1.05 + i * 0.001 + np.random.normal(0, 0.0005) for i in range(50)]
    prediction = service.predict_movement("EUR/USD", dummy_prices)
    print(f"Prediction: {prediction}")
    
    # Test full signal
    print("\n--- Trading Signal ---")
    signal = service.get_signal("EUR/USD", dummy_prices, ["Market rallies on positive data"])
    print(f"Signal: {signal['signal']} ({signal['confidence']:.0f}%)")
    print(f"Reasoning: {signal['reasoning']}")
