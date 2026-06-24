"""
Python Analysis API Server
Exposes trading analysis endpoints for the Java bot to call

Endpoints:
- GET /analyze?symbol=EUR/USD - Full market analysis and signals
- GET /signal?symbol=EUR/USD - Just the trading signal
- POST /record - Record trade result for risk tracking
- GET /status - Get system status
"""

from flask import Flask, jsonify, request
from flask_cors import CORS
import json
import os
import time
from typing import List
from datetime import datetime

from market_analyzer import MarketAnalyzer
from signal_generator import SignalGenerator
from risk_filter import RiskFilter

# ===== MULTI-AI INTEGRATION =====
from fingpt_service import get_fingpt_service
from consensus_engine import get_consensus_engine

# ===== POCKET OPTION INTEGRATION =====
try:
    from pocket_option_service import pocket_option_client, get_pocket_option_asset
except ImportError:
    print("⚠️ pocket_option_service not available (WebSocket API deprecated)")
    pocket_option_client = None
    get_pocket_option_asset = lambda x: x

# ===== POCKET OPTION SELENIUM (Stable Browser Automation) =====
try:
    from pocket_option_selenium import selenium_client, PocketOptionSelenium
except ImportError:
    print("⚠️ pocket_option_selenium not available (use browser method instead)")
    selenium_client = None
    PocketOptionSelenium = None

# ===== POCKET OPTION BROWSER (Manual Login - Most Reliable) =====
from pocket_option_browser import (browser_controller, open_browser_for_login, check_login_status, 
    execute_browser_trade, get_browser_balance, set_browser_currency, set_browser_amount, set_browser_duration)

# ===== CHROME EXTENSION BRIDGE =====
from extension_bridge import start_extension_server, send_trade_to_extension, get_extension_status

# ===== AUTO-LEARNER (Automatic win/loss tracking) =====
from auto_learner import get_auto_learner

# ===== LIVE MARKET DATA =====
from live_data_feed import LiveDataFeed

app = Flask(__name__, static_folder='.', static_url_path='/static')
CORS(app)  # Allow cross-origin requests from Java

# Initialize components
market_analyzer = MarketAnalyzer()
signal_generator = SignalGenerator()
risk_filter = RiskFilter()

# Initialize Multi-AI components
fingpt_service = get_fingpt_service()
consensus_engine = get_consensus_engine()
print("🤖 Multi-AI System initialized: FinGPT + Python ML + Consensus Engine")

# Store recent prices per symbol
price_cache = {}

# Initialize auto-learner (pass references so it can auto-check prices)
auto_learner = get_auto_learner(price_cache, consensus_engine, risk_filter)

# Initialize live data feed
live_feed = LiveDataFeed(price_cache)
live_feed.start()
print("📡 Live data feed started — real market prices flowing")


@app.route('/')
def home():
    """Health check endpoint"""
    return jsonify({
        "status": "running",
        "service": "Python Trading Analysis API",
        "version": "1.0.0",
        "endpoints": ["/analyze", "/signal", "/record", "/status"]
    })


@app.route('/analyze', methods=['GET', 'POST'])
def analyze():
    """
    Full market analysis endpoint
    
    GET params:
        symbol: Currency pair (e.g., EUR/USD)
        
    POST body:
        {
            "symbol": "EUR/USD",
            "prices": [1.05, 1.051, 1.052, ...]
        }
    """
    try:
        if request.method == 'POST':
            data = request.get_json()
            symbol = data.get('symbol', 'EUR/USD')
            prices = data.get('prices', [])
        else:
            symbol = request.args.get('symbol', 'EUR/USD')
            prices = price_cache.get(symbol, [])
        
        if len(prices) < 30:
            return jsonify({
                "success": False,
                "error": "Need at least 30 price points for analysis",
                "prices_received": len(prices)
            }), 400
        
        # Get full analysis
        analysis = market_analyzer.analyze(symbol, prices)
        signal = signal_generator.generate_signal(symbol, prices)
        risk = risk_filter.assess_risk(symbol, signal.confidence)
        
        return jsonify({
            "success": True,
            "symbol": symbol,
            "timestamp": datetime.now().isoformat(),
            
            # Main signal
            "signal": signal.direction,  # "CALL", "PUT", or "WAIT"
            "signal_type": signal.signal.value,
            "confidence": round(signal.confidence, 2),
            
            # Risk assessment
            "can_trade": risk.can_trade,
            "risk_score": round(risk.risk_score, 2),
            "position_size": round(risk.position_size_multiplier, 2),
            "risk_level": signal.risk_level,
            
            # Market analysis
            "market_condition": signal.market_condition,
            "trend_strength": round(signal.trend_strength, 2),
            "volatility_level": signal.volatility_level,
            "momentum": round(signal.momentum_value, 2),
            
            # Individual indicators
            "indicators": {
                "rsi": round(signal.rsi_value, 2),
                "rsi_signal": signal.rsi_signal,
                "macd": round(signal.macd_value, 4),
                "macd_signal": signal.macd_signal,
                "momentum": round(signal.momentum_value, 2),
                "momentum_signal": signal.momentum_signal,
                "trend_signal": signal.trend_signal
            },
            
            # Support/resistance
            "support_levels": analysis.support_levels,
            "resistance_levels": analysis.resistance_levels,
            
            # Recommendations
            "recommendation": signal.recommendation,
            "warnings": risk.warnings,
            "reasons": risk.reasons if not risk.can_trade else []
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route('/signal', methods=['GET', 'POST'])
def get_signal():
    """
    Simple signal endpoint - just returns CALL/PUT/WAIT
    Used for quick signal checks from Java
    """
    try:
        if request.method == 'POST':
            data = request.get_json()
            symbol = data.get('symbol', 'EUR/USD')
            prices = data.get('prices', [])
        else:
            symbol = request.args.get('symbol', 'EUR/USD')
            prices = price_cache.get(symbol, [])
        
        if len(prices) < 30:
            return jsonify({
                "signal": "WAIT",
                "confidence": 0,
                "reason": "Insufficient data"
            })
        
        signal = signal_generator.generate_signal(symbol, prices)
        risk = risk_filter.assess_risk(symbol, signal.confidence)
        
        # Final decision
        if not risk.can_trade:
            final_signal = "WAIT"
            reason = risk.reasons[0] if risk.reasons else "Risk limit"
        else:
            final_signal = signal.direction
            reason = signal.recommendation
        
        return jsonify({
            "signal": final_signal,
            "confidence": round(signal.confidence, 2),
            "position_size": round(risk.position_size_multiplier, 2),
            "reason": reason
        })
        
    except Exception as e:
        return jsonify({
            "signal": "WAIT",
            "confidence": 0,
            "reason": f"Error: {str(e)}"
        })


@app.route('/quick-signal', methods=['POST'])
def quick_signal():
    """
    Quick signal for web dashboard - minimal processing, fast response.
    Used for real-time updates in web UI.
    """
    try:
        data = request.get_json() or {}
        symbol = data.get('symbol', 'EUR/USD')
        
        # Get cached prices — NEVER use random data
        prices = price_cache.get(symbol, [])
        if len(prices) < 20:
            return jsonify({
                "success": True,
                "signal": "WAIT",
                "confidence": 0,
                "currency": symbol,
                "trend": "NO_DATA",
                "recommendation": "Waiting for live price data — no data yet for " + symbol
            })
        
        # Fast signal generation
        signal = signal_generator.generate_signal(symbol, prices)
        
        # Determine final direction
        if signal.direction == "CALL":
            final_signal = "CALL"
        elif signal.direction == "PUT":
            final_signal = "PUT"
        else:
            final_signal = "WAIT"
        
        return jsonify({
            "success": True,
            "signal": final_signal,
            "confidence": round(signal.confidence, 1),
            "currency": symbol,
            "trend": signal.market_condition,
            "recommendation": signal.recommendation
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "signal": "WAIT",
            "confidence": 0,
            "error": str(e)
        })


@app.route('/scanner', methods=['GET'])
def currency_scanner():
    """
    Scan all currencies and return signals - for web dashboard scanner.
    """
    currencies = ['EUR/USD', 'GBP/USD', 'USD/JPY', 'AUD/USD', 'USD/CAD', 'BTC/USD', 'ETH/USD']
    results = []
    
    for currency in currencies:
        try:
            prices = price_cache.get(currency, [])
            if len(prices) < 20:
                results.append({
                    'currency': currency,
                    'signal': 'WAIT',
                    'confidence': 0,
                    'trend': 'NO_DATA'
                })
                continue
            
            signal = signal_generator.generate_signal(currency, prices)
            
            results.append({
                'currency': currency,
                'signal': signal.direction,
                'confidence': round(signal.confidence, 1),
                'trend': signal.market_condition
            })
        except:
            results.append({
                'currency': currency,
                'signal': 'WAIT',
                'confidence': 0,
                'trend': 'UNKNOWN'
            })
    
    # Sort by confidence
    results.sort(key=lambda x: x['confidence'], reverse=True)
    
    return jsonify({
        "success": True,
        "currencies": results,
        "timestamp": datetime.now().isoformat()
    })


# ===== SIGNAL DASHBOARD ENDPOINTS =====

@app.route('/dashboard')
def serve_dashboard():
    """Serve the Signal Dashboard HTML."""
    return app.send_static_file('signal_dashboard.html')


@app.route('/dashboard/signals', methods=['GET'])
def dashboard_signals():
    """
    Get signals for all currencies with pre-alert data.
    Used by the Signal Dashboard for real-time updates.
    """
    currencies = ['EUR/USD', 'GBP/USD', 'USD/JPY', 'AUD/USD', 'USD/CAD', 
                  'BTC/USD', 'ETH/USD', 'XAU/USD', 'NZD/USD', 'OIL/USD']
    signals = []
    
    for currency in currencies:
        prices = price_cache.get(currency, [])
        has_data = len(prices) >= 20
        
        if has_data:
            try:
                signal = signal_generator.generate_signal(currency, prices)
                risk = risk_filter.assess_risk(currency, signal.confidence)
                
                # Determine signal age (how fresh is the price data)
                signal_age = 0  # Will be set by live_feed timestamps
                
                signals.append({
                    'currency': currency,
                    'signal': signal.direction,
                    'confidence': round(signal.confidence, 1),
                    'trend': signal.market_condition,
                    'can_trade': risk.can_trade,
                    'risk_score': risk.risk_score,
                    'position_size': round(risk.position_size_multiplier, 2),
                    'warnings': risk.warnings,
                    'reasons': risk.reasons,
                    'recommendation': signal.recommendation,
                    'current_price': prices[-1] if prices else 0,
                    'price_count': len(prices),
                    'rsi': round(signal.rsi_value, 1),
                    'optimal_expiry': signal.optimal_expiry_seconds,
                    'has_data': True,
                    'timestamp': datetime.now().isoformat()
                })
            except Exception as e:
                signals.append({
                    'currency': currency,
                    'signal': 'ERROR',
                    'confidence': 0,
                    'error': str(e),
                    'has_data': True
                })
        else:
            signals.append({
                'currency': currency,
                'signal': 'WAIT',
                'confidence': 0,
                'trend': 'NO_DATA',
                'has_data': False,
                'price_count': len(prices),
                'can_trade': False
            })
    
    # Sort: tradeable signals first, then by confidence
    signals.sort(key=lambda x: (
        x.get('can_trade', False),
        x.get('confidence', 0)
    ), reverse=True)
    
    return jsonify({
        'success': True,
        'signals': signals,
        'timestamp': datetime.now().isoformat(),
        'pending_trades': len(auto_learner.get_pending())
    })


@app.route('/dashboard/record-trade', methods=['POST'])
def dashboard_record_trade():
    """
    Record that a trade was placed (user clicked in Pocket Option).
    The auto-learner will track the outcome automatically.
    """
    try:
        data = request.get_json() or {}
        currency = data.get('currency', 'EUR/USD')
        direction = data.get('direction', 'BUY')
        expiry_seconds = int(data.get('expiry_seconds', 300))  # Default 5 min
        
        # Get current price as entry price
        prices = price_cache.get(currency, [])
        if not prices:
            return jsonify({'success': False, 'error': 'No price data for ' + currency}), 400
        
        entry_price = prices[-1]
        confidence = data.get('confidence', 0)
        
        # Record in auto-learner
        trade_id = auto_learner.record_signal(
            currency=currency,
            direction=direction,
            entry_price=entry_price,
            expiry_seconds=expiry_seconds,
            confidence=confidence,
            source=data.get('source', 'manual'),
            signals_used=data.get('signals_used', ['manual'])
        )
        
        # Update risk filter last trade time
        risk_filter.last_trade_time = datetime.now()
        
        return jsonify({
            'success': True,
            'trade_id': trade_id,
            'entry_price': entry_price,
            'direction': direction,
            'currency': currency,
            'expiry_seconds': expiry_seconds,
            'message': f'Trade recorded! Outcome will be checked in {expiry_seconds}s'
        })
        
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@app.route('/dashboard/learning-stats', methods=['GET'])
def dashboard_learning_stats():
    """Get auto-learning statistics."""
    return jsonify({
        'success': True,
        **auto_learner.get_stats()
    })


@app.route('/dashboard/pending-trades', methods=['GET'])
def dashboard_pending_trades():
    """Get trades awaiting outcome."""
    return jsonify({
        'success': True,
        'trades': auto_learner.get_pending()
    })


@app.route('/dashboard/price-status', methods=['GET'])
def dashboard_price_status():
    """Get live data feed status."""
    return jsonify({
        'success': True,
        'feed_status': live_feed.get_status(),
        'cached_currencies': {k: len(v) for k, v in price_cache.items()}
    })


@app.route('/signal-queue', methods=['GET'])
def signal_queue():
    """
    Signal Queue endpoint for manual trading assistant.
    Returns up to 5 high-confidence trading signals across all currencies.
    
    Returns:
        {
            "signals": [
                {
                    "currency": "EUR/USD",
                    "direction": "CALL",
                    "confidence": 85.5,
                    "amount": 10,
                    "duration": 3,
                    "trend": "UPTREND"
                },
                ...
            ]
        }
    """
    currencies = ['EUR/USD', 'GBP/USD', 'USD/JPY', 'AUD/USD', 'USD/CAD', 'BTC/USD', 'ETH/USD', 'XAU/USD']
    signals = []
    
    for currency in currencies:
        try:
            # Get cached prices or generate realistic ones
            prices = price_cache.get(currency, [])
            if len(prices) < 20:
                import random
                base = {
                    'EUR/USD': 1.085, 'GBP/USD': 1.27, 'USD/JPY': 149.5, 
                    'AUD/USD': 0.65, 'USD/CAD': 1.35, 'BTC/USD': 42000, 
                    'ETH/USD': 2200, 'XAU/USD': 2050
                }.get(currency, 1.0)
                prices = [base + random.uniform(-base*0.002, base*0.002) for _ in range(50)]
            
            # Generate signal
            signal = signal_generator.generate_signal(currency, prices)
            
            # Only include signals with confidence >= 70% and clear direction
            if signal.confidence >= 70 and signal.direction in ['CALL', 'PUT']:
                # Determine smart amount based on confidence
                if signal.confidence >= 85:
                    amount = 15  # High confidence = higher amount
                elif signal.confidence >= 75:
                    amount = 10
                else:
                    amount = 5
                
                # Determine duration based on volatility
                volatility = signal.volatility_level if hasattr(signal, 'volatility_level') else 'NORMAL'
                if volatility == 'HIGH':
                    duration = 1  # Faster resolution in volatile markets
                elif volatility == 'LOW':
                    duration = 5  # Longer duration in stable markets
                else:
                    duration = 3  # Default 3 minutes
                
                signals.append({
                    'currency': currency,
                    'direction': signal.direction,
                    'confidence': round(signal.confidence, 1),
                    'amount': amount,
                    'duration': duration,
                    'trend': signal.market_condition if hasattr(signal, 'market_condition') else 'NEUTRAL'
                })
        except Exception as e:
            print(f"Error scanning {currency}: {e}")
            continue
    
    # Sort by confidence (highest first) and limit to 5
    signals.sort(key=lambda x: x['confidence'], reverse=True)
    signals = signals[:5]
    
    return jsonify({
        "success": True,
        "signals": signals,
        "total_scanned": len(currencies),
        "timestamp": datetime.now().isoformat()
    })


@app.route('/prices', methods=['POST'])
def update_prices():
    """
    Update price cache for a symbol
    Called by Java to send latest prices
    """
    try:
        data = request.get_json()
        symbol = data.get('symbol', 'EUR/USD')
        prices = data.get('prices', [])
        
        if not prices:
            return jsonify({"success": False, "error": "No prices provided"})
        
        price_cache[symbol] = prices
        
        return jsonify({
            "success": True,
            "symbol": symbol,
            "prices_stored": len(prices)
        })
        
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500


@app.route('/record', methods=['POST'])
def record_trade():
    """
    Record a trade result for risk tracking
    
    POST body:
        {
            "won": true/false,
            "profit_loss": 10.50,
            "symbol": "EUR/USD"
        }
    """
    try:
        data = request.get_json()
        won = data.get('won', False)
        profit_loss = data.get('profit_loss', 0)
        
        risk_filter.record_trade_result(won, profit_loss)
        
        return jsonify({
            "success": True,
            "recorded": {
                "won": won,
                "profit_loss": profit_loss
            },
            "status": risk_filter.get_status()
        })
        
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500


@app.route('/status', methods=['GET'])
def get_status():
    """Get current risk filter status"""
    return jsonify({
        "success": True,
        "timestamp": datetime.now().isoformat(),
        "risk_status": risk_filter.get_status(),
        "cached_symbols": list(price_cache.keys()),
        "prices_per_symbol": {k: len(v) for k, v in price_cache.items()}
    })


@app.route('/reset', methods=['POST'])
def reset():
    """Reset the risk filter (for testing)"""
    global risk_filter
    risk_filter = RiskFilter()
    return jsonify({"success": True, "message": "Risk filter reset"})


# ===================================================================
# MULTI-AI ENDPOINTS
# ===================================================================

@app.route('/consensus', methods=['POST'])
def get_consensus():
    """
    🤖 MAIN ENDPOINT - Multi-AI Consensus Signal
    
    Combines signals from FinGPT, Python ML, and Java Brain
    to make robust trading decisions.
    
    POST body:
        {
            "symbol": "EUR/USD",
            "prices": [1.05, 1.051, ...],
            "java_signal": {
                "direction": "BUY" | "SELL" | "WAIT",
                "confidence": 70
            },
            "news_headlines": ["Optional news headlines..."]
        }
    
    Returns:
        {
            "consensus_signal": "BUY" | "SELL" | "WAIT",
            "consensus_confidence": 85,
            "agreement_level": "2/3",
            "should_trade": true/false,
            "reasoning": "Detailed explanation...",
            "individual_signals": {...},
            "position_size": 0.75
        }
    """
    try:
        data = request.get_json()
        symbol = data.get('symbol', 'EUR/USD')
        prices = data.get('prices', [])
        java_signal = data.get('java_signal', None)
        news_headlines = data.get('news_headlines', None)
        
        if len(prices) < 20:
            return jsonify({
                "consensus_signal": "WAIT",
                "consensus_confidence": 0,
                "should_trade": False,
                "reasoning": "Insufficient price data (need at least 20 points)"
            })
        
        # Get consensus from all AIs
        result = consensus_engine.get_consensus(
            symbol=symbol,
            prices=prices,
            java_signal=java_signal,
            news_headlines=news_headlines
        )
        
        return jsonify({
            "success": True,
            "timestamp": datetime.now().isoformat(),
            **result
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "consensus_signal": "WAIT",
            "consensus_confidence": 0,
            "should_trade": False,
            "error": str(e)
        }), 500


@app.route('/fingpt/signal', methods=['POST'])
def fingpt_signal():
    """
    Get trading signal from FinGPT only
    
    POST body:
        {
            "symbol": "EUR/USD",
            "prices": [...],
            "news_headlines": ["optional news..."]
        }
    """
    try:
        data = request.get_json()
        symbol = data.get('symbol', 'EUR/USD')
        prices = data.get('prices', [])
        news = data.get('news_headlines', None)
        
        result = fingpt_service.get_signal(symbol, prices, news)
        
        return jsonify({
            "success": True,
            "timestamp": datetime.now().isoformat(),
            **result
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "signal": "WAIT",
            "error": str(e)
        }), 500


@app.route('/fingpt/sentiment', methods=['POST'])
def fingpt_sentiment():
    """
    Analyze sentiment of financial text using FinGPT
    
    POST body:
        {
            "text": "The market rallies on strong earnings..."
        }
    """
    try:
        data = request.get_json()
        text = data.get('text', '')
        
        if not text:
            return jsonify({
                "success": False,
                "error": "No text provided"
            }), 400
        
        result = fingpt_service.analyze_sentiment(text)
        
        return jsonify({
            "success": True,
            **result
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route('/fingpt/predict', methods=['POST'])
def fingpt_predict():
    """
    Get price movement prediction from FinGPT
    
    POST body:
        {
            "symbol": "EUR/USD",
            "prices": [...],
            "news_context": "optional news for context"
        }
    """
    try:
        data = request.get_json()
        symbol = data.get('symbol', 'EUR/USD')
        prices = data.get('prices', [])
        news = data.get('news_context', None)
        
        result = fingpt_service.predict_movement(symbol, prices, news)
        
        return jsonify({
            "success": True,
            "timestamp": datetime.now().isoformat(),
            **result
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route('/fingpt/status', methods=['GET'])
def fingpt_status():
    """Get FinGPT service status"""
    return jsonify({
        "success": True,
        "fingpt": fingpt_service.get_status(),
        "consensus": consensus_engine.get_status()
    })


@app.route('/consensus/record', methods=['POST'])
def record_consensus_result():
    """
    Record trade result for consensus engine adaptation
    
    POST body:
        {
            "won": true/false,
            "profit": 10.50,
            "signals_used": ["fingpt", "python_ml", "java_brain"],
            "symbol": "EUR/USD"
        }
    """
    try:
        data = request.get_json()
        consensus_engine.record_result(data)
        
        return jsonify({
            "success": True,
            "message": "Result recorded for weight adaptation"
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


# ===== POCKET OPTION ENDPOINTS =====

@app.route('/pocket-option/connect', methods=['POST'])
def pocket_option_connect():
    """
    Connect to Pocket Option platform.
    
    POST body:
        {
            "ssid": "42[\"auth\",{...}]",  # Session ID from browser
            "demo_mode": true  # Optional, defaults to true
        }
    """
    try:
        data = request.get_json() or {}
        ssid = data.get('ssid')
        demo_mode = data.get('demo_mode', True)  # Default to demo for safety
        
        if not ssid:
            return jsonify({
                "success": False,
                "error": "SSID is required. Get it from browser developer tools."
            }), 400
            
        # Connect
        connected = pocket_option_client.connect(ssid, demo_mode)
        
        return jsonify({
            "success": connected,
            "message": f"Connected to Pocket Option ({'DEMO' if demo_mode else 'REAL'} mode)" if connected else "Connection failed",
            "balance": pocket_option_client.get_balance() if connected else 0
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route('/pocket-option/trade', methods=['POST'])
def pocket_option_trade():
    """
    Execute a trade on Pocket Option.
    
    POST body:
        {
            "asset": "EUR/USD",  # Currency pair
            "direction": "CALL",  # or "PUT"
            "amount": 10.0,
            "duration": 60  # seconds
        }
    """
    try:
        if not pocket_option_client.is_connected():
            return jsonify({
                "success": False,
                "error": "Not connected to Pocket Option"
            }), 400
            
        data = request.get_json() or {}
        asset = data.get('asset', 'EUR/USD')
        direction = data.get('direction', 'CALL').lower()
        amount = float(data.get('amount', 10))
        duration = int(data.get('duration', 60))
        
        # Convert asset name
        po_asset = get_pocket_option_asset(asset)
        
        # Convert direction
        po_direction = "call" if direction in ["call", "buy"] else "put"
        
        # Execute trade
        trade_id = pocket_option_client.execute_trade(po_asset, po_direction, amount, duration)
        
        return jsonify({
            "success": trade_id is not None,
            "trade_id": trade_id,
            "asset": po_asset,
            "direction": po_direction,
            "amount": amount,
            "duration": duration
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route('/pocket-option/result', methods=['GET'])
def pocket_option_result():
    """
    Get result of a trade.
    
    Query params:
        trade_id: ID of the trade
        timeout: Max seconds to wait (default 300)
    """
    try:
        trade_id = request.args.get('trade_id')
        timeout = int(request.args.get('timeout', 300))
        
        if not trade_id:
            return jsonify({
                "success": False,
                "error": "trade_id is required"
            }), 400
            
        result = pocket_option_client.get_trade_result(trade_id, timeout)
        
        return jsonify({
            "success": result is not None,
            "trade_id": trade_id,
            "result": result
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route('/pocket-option/balance', methods=['GET'])
def pocket_option_balance():
    """Get current Pocket Option account balance."""
    return jsonify({
        "success": pocket_option_client.is_connected(),
        "balance": pocket_option_client.get_balance()
    })


@app.route('/pocket-option/status', methods=['GET'])
def pocket_option_status():
    """Get Pocket Option connection status."""
    return jsonify(pocket_option_client.get_status())


@app.route('/pocket-option/disconnect', methods=['POST'])
def pocket_option_disconnect():
    """Disconnect from Pocket Option."""
    pocket_option_client.disconnect()
    return jsonify({
        "success": True,
        "message": "Disconnected from Pocket Option"
    })


# ===== POCKET OPTION SELENIUM ENDPOINTS (More Stable) =====

@app.route('/pocket-selenium/login', methods=['POST'])
def selenium_login():
    """
    Login to Pocket Option via Selenium (headless browser).
    
    POST body:
        {
            "email": "your@email.com",
            "password": "yourpassword",
            "demo_mode": true  # Optional, defaults to true
        }
    """
    global selenium_client
    try:
        data = request.get_json() or {}
        email = data.get('email')
        password = data.get('password')
        demo_mode = data.get('demo_mode', True)
        
        if not email or not password:
            return jsonify({
                "success": False,
                "error": "Email and password are required"
            }), 400
            
        # Create new client with specified mode
        selenium_client = PocketOptionSelenium(headless=True, demo_mode=demo_mode)
        
        success = selenium_client.login(email, password)
        
        return jsonify({
            "success": success,
            "message": "Login successful!" if success else "Login failed",
            "balance": selenium_client.get_balance() if success else 0,
            "demo_mode": demo_mode
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route('/pocket-selenium/trade', methods=['POST'])
def selenium_trade():
    """
    Execute a trade via Selenium.
    
    POST body:
        {
            "direction": "BUY",  # or "SELL", "CALL", "PUT"
            "amount": 10
        }
    """
    try:
        if not selenium_client.is_logged_in():
            return jsonify({
                "success": False,
                "error": "Not logged in. Call /pocket-selenium/login first."
            }), 400
            
        data = request.get_json() or {}
        direction = data.get('direction', 'BUY')
        amount = float(data.get('amount', 10))
        
        result = selenium_client.execute_trade(direction, amount)
        
        return jsonify({
            "success": result is not None,
            "trade": result
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route('/pocket-selenium/result', methods=['GET'])
def selenium_result():
    """Get result of last trade."""
    try:
        timeout = int(request.args.get('timeout', 120))
        result = selenium_client.get_last_trade_result(timeout)
        
        return jsonify({
            "success": result is not None,
            "result": result
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route('/pocket-selenium/balance', methods=['GET'])
def selenium_balance():
    """Get current balance."""
    return jsonify({
        "success": selenium_client.is_logged_in(),
        "balance": selenium_client.get_balance()
    })


@app.route('/pocket-selenium/status', methods=['GET'])
def selenium_status():
    """Get Selenium connection status."""
    return jsonify(selenium_client.get_status())


@app.route('/pocket-selenium/screenshot', methods=['POST'])
def selenium_screenshot():
    """Take a screenshot (for debugging)."""
    try:
        filename = f"screenshot_{int(time.time())}.png"
        filepath = os.path.join(os.path.dirname(os.path.abspath(__file__)), filename)
        selenium_client.take_screenshot(filepath)
        return jsonify({
            "success": True,
            "file": filepath
        })
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route('/pocket-selenium/close', methods=['POST'])
def selenium_close():
    """Close the browser."""
    selenium_client.close()
    return jsonify({
        "success": True,
        "message": "Browser closed"
    })


# ===== POCKET OPTION LIVE TRADING (Browser-Based) =====
# These endpoints execute trades via the browser that's already logged in

# Store for pending browser trade requests
pending_browser_trades = []

@app.route('/pocket-live/trade', methods=['POST'])
def pocket_live_trade():
    """
    Execute a trade on Pocket Option via browser automation.
    The browser must already be logged in (user does this manually).
    
    POST body:
        {
            "direction": "BUY",  # or "SELL"
            "amount": 10,  # Optional
            "asset": "EUR/USD"  # Optional
        }
    
    This queues a trade request that will be executed by the browser subagent.
    """
    try:
        data = request.get_json() or {}
        direction = data.get('direction', 'BUY').upper()
        amount = data.get('amount', 0)
        asset = data.get('asset', 'current')
        
        # Validate direction
        if direction not in ['BUY', 'SELL', 'CALL', 'PUT']:
            return jsonify({
                "success": False,
                "error": "Direction must be BUY or SELL"
            }), 400
        
        # Queue the trade
        trade_request = {
            "id": f"trade_{int(time.time() * 1000)}",
            "direction": direction,
            "amount": amount,
            "asset": asset,
            "time": datetime.now().isoformat(),
            "status": "queued"
        }
        pending_browser_trades.append(trade_request)
        
        return jsonify({
            "success": True,
            "message": f"{direction} trade queued for execution",
            "trade": trade_request
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route('/pocket-live/pending', methods=['GET'])
def pocket_live_pending():
    """Get pending trade requests waiting for browser execution."""
    return jsonify({
        "success": True,
        "pending_trades": pending_browser_trades,
        "count": len(pending_browser_trades)
    })


@app.route('/pocket-live/execute-next', methods=['POST'])
def pocket_live_execute_next():
    """
    Mark the next pending trade as executed.
    Called after the browser subagent clicks the trade button.
    """
    if not pending_browser_trades:
        return jsonify({
            "success": False,
            "error": "No pending trades"
        }), 404
        
    trade = pending_browser_trades.pop(0)
    trade["status"] = "executed"
    trade["executed_at"] = datetime.now().isoformat()
    
    return jsonify({
        "success": True,
        "executed_trade": trade
    })


@app.route('/pocket-live/stats', methods=['GET'])
def pocket_live_stats():
    """Get live trading statistics."""
    return jsonify({
        "pending_trades": len(pending_browser_trades),
        "message": "Use browser automation to execute pending trades"
    })


# ===== BROKER PRICE ENDPOINTS (Real Market Data) =====
# Used by Java RealMarketDataProvider to get live prices from Pocket Option

# Store live prices from extension
broker_prices = {}
broker_price_timestamps = {}

@app.route('/broker/price', methods=['GET'])
def broker_get_price():
    """
    Get current price for a currency pair from the broker (via extension).
    Used by Java RealMarketDataProvider for real market data.
    
    Query params:
        currency: Currency pair (e.g., EUR/USD)
    """
    currency = request.args.get('currency', 'EUR/USD')
    
    # Check if we have a recent price (within 10 seconds)
    import time
    last_update = broker_price_timestamps.get(currency, 0)
    age = time.time() - last_update
    
    if currency in broker_prices and age < 10:
        return jsonify({
            "success": True,
            "price": broker_prices[currency],
            "currency": currency,
            "age_seconds": round(age, 1),
            "source": "broker_extension"
        })
    else:
        return jsonify({
            "success": False,
            "error": "No recent price data from broker",
            "currency": currency,
            "age_seconds": round(age, 1) if currency in broker_prices else -1
        })


@app.route('/broker/price', methods=['POST'])
def broker_update_price():
    """
    Update price from extension.
    Called by Chrome extension when it reads the chart.
    """
    import time
    data = request.get_json() or {}
    currency = data.get('currency', 'EUR/USD')
    price = data.get('price')
    
    if price:
        broker_prices[currency] = float(price)
        broker_price_timestamps[currency] = time.time()
        return jsonify({
            "success": True,
            "currency": currency,
            "price": price,
            "message": "Price updated"
        })
    else:
        return jsonify({
            "success": False,
            "error": "No price provided"
        }), 400


@app.route('/broker/prices', methods=['GET'])
def broker_get_all_prices():
    """Get all cached broker prices."""
    import time
    now = time.time()
    return jsonify({
        "success": True,
        "prices": {
            curr: {
                "price": price,
                "age_seconds": round(now - broker_price_timestamps.get(curr, 0), 1)
            }
            for curr, price in broker_prices.items()
        }
    })


# ===== CHROME EXTENSION TRADE ENDPOINTS =====

@app.route('/extension/trade', methods=['POST'])
def extension_execute_trade():
    """
    Execute trade via Chrome extension.
    This is called by the Java bot to send trade commands to the extension.
    
    Body:
        {
            "currency": "EUR/USD",    // Currency pair to trade
            "direction": "CALL",      // "CALL" or "PUT"
            "amount": 10,             // Trade amount in USD
            "duration": 60            // Duration in SECONDS (e.g., 60, 120, 180)
        }
    """
    try:
        data = request.get_json() or {}
        currency = data.get('currency', 'EUR/USD')
        direction = data.get('direction', 'CALL')
        amount = float(data.get('amount', 10))
        duration = int(data.get('duration', 60))  # Now using duration in seconds!
        
        print(f"📤 Extension trade request: {direction} ${amount} on {currency} for {duration}s")
        
        # Send to extension via bridge
        # Function signature: send_trade_to_extension(direction, amount, currency, duration)
        success = send_trade_to_extension(direction, amount, currency, duration)
        
        if success:
            return jsonify({
                "success": True,
                "message": f"Trade sent to extension: {direction} ${amount} on {currency} ({duration}s)",
                "currency": currency,
                "direction": direction,
                "amount": amount,
                "duration": duration
            })
        else:
            return jsonify({
                "success": False,
                "message": "No extension connected or send failed"
            }), 400
            
    except Exception as e:
        print(f"Extension trade error: {e}")
        return jsonify({"success": False, "error": str(e)}), 500


@app.route('/extension/status', methods=['GET'])
def extension_get_status():
    """Get Chrome extension connection status."""
    try:
        status = get_extension_status()
        return jsonify({
            "success": True,
            "extensions_connected": status.get('extensions_connected', 0),
            "bridge_running": status.get('extensions_connected', 0) > 0,
            "total_trades": status.get('trade_count', 0),
            "current_currency": status.get('currency', ''),
            "current_balance": status.get('balance', 0)
        })
    except Exception as e:
        return jsonify({
            "success": False,
            "extensions_connected": 0,
            "bridge_running": False,
            "error": str(e)
        })


# ===== POCKET OPTION BROWSER (Manual Login - RECOMMENDED) =====
# User logs in manually, bot detects when ready and executes trades

@app.route('/browser/open', methods=['POST'])
def browser_open():
    """
    Open a visible Chrome browser for manual login.
    User logs in themselves - bot will detect when ready.
    
    This is the most reliable method - no security issues!
    """
    try:
        result = open_browser_for_login()
        return jsonify(result)
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route('/browser/status', methods=['GET'])
def browser_status():
    """
    Check if user has logged in and bot is ready.
    Call this periodically until logged_in is True.
    """
    return jsonify(check_login_status())


@app.route('/browser/debug-balance', methods=['GET'])
def browser_debug_balance():
    """
    Debug endpoint - find ALL elements on page that contain dollar amounts.
    This helps identify which element has the correct balance.
    """
    try:
        from pocket_option_browser import browser_controller as bc
        
        if not bc.driver:
            return jsonify({"error": "Browser not open", "is_ready": bc.is_ready})
        
        # JavaScript to find ALL dollar amounts AND large numbers on page
        js_code = """
        const results = [];
        const allElements = document.querySelectorAll('*');
        
        for (let el of allElements) {
            if (el.childElementCount === 0 && el.innerText) {
                const txt = el.innerText.trim();
                // Look for dollar amounts OR plain numbers that could be balances
                if (txt.match(/\\$[0-9,.]+/) || txt.match(/[0-9,.]+\\s*\\$/) || txt.match(/^[0-9,]+\\.\\d{2}$/)) {
                    // Parse the number (remove all non-numeric except decimal)
                    let cleanNum = txt.replace(/[^0-9.]/g, '');
                    // Handle comma as thousands separator
                    if (txt.includes(',') && txt.indexOf(',') < txt.indexOf('.')) {
                        cleanNum = txt.replace(/,/g, '').replace(/[^0-9.]/g, '');
                    }
                    const val = parseFloat(cleanNum);
                    if (val > 0) {
                        results.push({
                            text: txt,
                            value: val,
                            tag: el.tagName,
                            classes: el.className,
                            id: el.id,
                            visible: el.offsetParent !== null,
                            parent_class: el.parentElement ? el.parentElement.className : ''
                        });
                    }
                }
            }
        }
        
        // Also search for any element with text containing large numbers (>100)
        for (let el of allElements) {
            if (el.childElementCount === 0 && el.innerText) {
                const txt = el.innerText.trim();
                // Look for numbers like 26033.84 or 26,033.84
                const match = txt.match(/([0-9,]+\\.\\d{2})/);
                if (match) {
                    const numStr = match[1].replace(/,/g, '');
                    const val = parseFloat(numStr);
                    if (val > 100 && !results.some(r => r.text === txt)) {
                        results.push({
                            text: txt,
                            value: val,
                            tag: el.tagName,
                            classes: el.className,
                            id: el.id,
                            visible: el.offsetParent !== null,
                            parent_class: el.parentElement ? el.parentElement.className : ''
                        });
                    }
                }
            }
        }
        
        // Sort by value descending to find largest (likely main balance)
        results.sort((a, b) => b.value - a.value);
        return results.slice(0, 30); // Return top 30
        """
        
        all_amounts = bc.driver.execute_script(js_code)
        
        return jsonify({
            "success": True,
            "found_amounts": len(all_amounts),
            "amounts": all_amounts,
            "current_detected_balance": bc.balance
        })
        
    except Exception as e:
        import traceback
        return jsonify({"error": str(e), "traceback": traceback.format_exc()})


@app.route('/browser/currency', methods=['POST'])
def browser_set_currency():
    """
    Set the active currency in the browser.
    """
    try:
        status = check_login_status()
        if not status.get('logged_in'):
            return jsonify({"success": False, "error": "Not logged in"}), 400
            
        data = request.get_json() or {}
        symbol = data.get('symbol')
        
        if not symbol:
            return jsonify({"success": False, "error": "Symbol required"}), 400
            
        result = set_browser_currency(symbol)
        
        return jsonify({
            "success": result,
            "message": f"Currency set to {symbol}" if result else "Failed to set currency"
        })
        
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500


@app.route('/browser/amount', methods=['POST'])
def browser_set_amount():
    """
    Set the trade amount in the browser.
    
    POST body: {"amount": 10}
    """
    try:
        status = check_login_status()
        if not status.get('logged_in'):
            return jsonify({"success": False, "error": "Not logged in"}), 400
            
        data = request.get_json() or {}
        amount = data.get('amount')
        
        if amount is None or amount <= 0:
            return jsonify({"success": False, "error": "Valid amount required"}), 400
            
        result = set_browser_amount(float(amount))
        
        return jsonify({
            "success": result,
            "amount": amount,
            "message": f"Amount set to ${amount}" if result else "Failed to set amount"
        })
        
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500


@app.route('/browser/duration', methods=['POST'])
def browser_set_duration():
    """
    Set the trade duration in the browser.
    
    POST body: {"minutes": 1}
    """
    try:
        status = check_login_status()
        if not status.get('logged_in'):
            return jsonify({"success": False, "error": "Not logged in"}), 400
            
        data = request.get_json() or {}
        minutes = data.get('minutes')
        
        if minutes is None or minutes <= 0:
            return jsonify({"success": False, "error": "Valid duration required"}), 400
            
        result = set_browser_duration(int(minutes))
        
        return jsonify({
            "success": result,
            "minutes": minutes,
            "message": f"Duration set to {minutes} min" if result else "Failed to set duration"
        })
        
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500


@app.route('/browser/trade', methods=['POST'])
def browser_trade():
    """
    Execute a trade via the browser.
    User must be logged in first (check /browser/status).
    
    POST body:
        {
            "direction": "BUY",  # or "SELL"
            "amount": 10  # optional
        }
    """
    try:
        status = check_login_status()
        if not status.get('logged_in'):
            return jsonify({
                "success": False,
                "error": "Not logged in yet. Please login in the browser first.",
                "status": status
            }), 400
        
        data = request.get_json() or {}
        direction = data.get('direction', 'BUY')
        amount = data.get('amount')
        
        result = execute_browser_trade(direction, amount)
        
        return jsonify({
            "success": result is not None,
            "trade": result,
            "balance": get_browser_balance()
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route('/browser/balance', methods=['GET'])
def browser_balance():
    """Get current balance from browser."""
    return jsonify({
        "success": True,
        "balance": get_browser_balance()
    })


@app.route('/browser/close', methods=['POST'])
def browser_close():
    """Close the browser."""
    browser_controller.close()
    return jsonify({
        "success": True,
        "message": "Browser closed"
    })


# ===== BROKER SYNC (Balance & Trade Results) =====
# This syncs the bot's balance with the actual Pocket Option broker

broker_state = {
    "balance": 0.0,
    "last_sync": None,
    "total_trades": 0,
    "total_profit": 0.0,
    "trade_results": []  # Recent trade results from broker
}

@app.route('/broker/sync-balance', methods=['POST'])
def broker_sync_balance():
    """
    Sync balance from Pocket Option browser.
    Called by browser automation after reading the balance element.
    
    POST body:
        {
            "balance": 26097.84
        }
    """
    global broker_state
    try:
        data = request.get_json() or {}
        balance = float(data.get('balance', 0))
        
        broker_state["balance"] = balance
        broker_state["last_sync"] = datetime.now().isoformat()
        
        return jsonify({
            "success": True,
            "balance": balance,
            "synced_at": broker_state["last_sync"]
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route('/broker/balance', methods=['GET'])
def broker_get_balance():
    """Get the current synced broker balance."""
    return jsonify({
        "success": True,
        "balance": broker_state["balance"],
        "last_sync": broker_state["last_sync"]
    })


@app.route('/broker/record-result', methods=['POST'])
def broker_record_result():
    """
    Record a trade result from the broker.
    
    POST body:
        {
            "won": true,
            "profit": 8.50,
            "asset": "AUD/USD",
            "amount": 10
        }
    """
    global broker_state
    try:
        data = request.get_json() or {}
        won = data.get('won', False)
        profit = float(data.get('profit', 0))
        
        result = {
            "won": won,
            "profit": profit,
            "asset": data.get('asset', 'unknown'),
            "amount": data.get('amount', 0),
            "time": datetime.now().isoformat()
        }
        
        broker_state["trade_results"].append(result)
        broker_state["total_trades"] += 1
        broker_state["total_profit"] += profit if won else -data.get('amount', 0)
        
        # Keep only last 100 results
        if len(broker_state["trade_results"]) > 100:
            broker_state["trade_results"] = broker_state["trade_results"][-100:]
        
        return jsonify({
            "success": True,
            "result": result,
            "total_trades": broker_state["total_trades"],
            "total_profit": broker_state["total_profit"]
        })
        
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


@app.route('/broker/state', methods=['GET'])
def broker_get_state():
    """Get the full broker sync state."""
    win_count = sum(1 for r in broker_state["trade_results"] if r["won"])
    total = len(broker_state["trade_results"])
    win_rate = (win_count / total * 100) if total > 0 else 0
    
    return jsonify({
        "success": True,
        "balance": broker_state["balance"],
        "last_sync": broker_state["last_sync"],
        "total_trades": broker_state["total_trades"],
        "total_profit": broker_state["total_profit"],
        "recent_trades": len(broker_state["trade_results"]),
        "win_rate": f"{win_rate:.1f}%"
    })


# ========================================
# HYBRID TRADING ENDPOINTS
# WebSocket API (fast) + Browser Fallback
# ========================================

try:
    from hybrid_trader import (
        get_hybrid_trader, hybrid_connect, hybrid_check_status,
        hybrid_execute_trade, hybrid_get_balance, hybrid_disconnect
    )
    HYBRID_AVAILABLE = True
    print("✅ Hybrid Trader module loaded")
except ImportError as e:
    HYBRID_AVAILABLE = False
    print(f"⚠️ Hybrid Trader not available: {e}")


@app.route('/hybrid/connect', methods=['POST'])
def hybrid_connect_endpoint():
    """Connect to broker using hybrid method (browser login + WebSocket)."""
    if not HYBRID_AVAILABLE:
        return jsonify({"success": False, "error": "Hybrid trader not available"}), 500
    
    try:
        result = hybrid_connect()
        return jsonify(result)
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500


@app.route('/hybrid/status', methods=['GET'])
def hybrid_status_endpoint():
    """Get hybrid trader status and try to initialize WebSocket if ready."""
    if not HYBRID_AVAILABLE:
        return jsonify({"success": False, "error": "Hybrid trader not available"}), 500
    
    try:
        result = hybrid_check_status()
        return jsonify(result)
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500


@app.route('/hybrid/trade', methods=['POST'])
def hybrid_trade_endpoint():
    """
    Execute trade using hybrid method:
    1. Try WebSocket (fast) first
    2. Fall back to browser if needed
    
    POST body:
    {
        "asset": "EUR/USD",
        "direction": "call" or "put",
        "amount": 10.00,
        "duration": 60  // seconds
    }
    """
    if not HYBRID_AVAILABLE:
        return jsonify({"success": False, "error": "Hybrid trader not available"}), 500
    
    try:
        data = request.get_json() or {}
        
        asset = data.get("asset", "EURUSD")
        direction = data.get("direction", "call")
        amount = float(data.get("amount", 1.0))
        duration = int(data.get("duration", 60))
        
        result = hybrid_execute_trade(asset, direction, amount, duration)
        return jsonify(result)
        
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500


@app.route('/hybrid/balance', methods=['GET'])
def hybrid_balance_endpoint():
    """Get balance from hybrid trader."""
    if not HYBRID_AVAILABLE:
        return jsonify({"success": False, "error": "Hybrid trader not available"}), 500
    
    try:
        balance = hybrid_get_balance()
        return jsonify({"success": True, "balance": balance})
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500


@app.route('/hybrid/disconnect', methods=['POST'])
def hybrid_disconnect_endpoint():
    """Disconnect hybrid trader."""
    if not HYBRID_AVAILABLE:
        return jsonify({"success": False, "error": "Hybrid trader not available"}), 500
    
    try:
        result = hybrid_disconnect()
        return jsonify(result)
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500


# ===== WEBSOCKET API TRADING (NO BROWSER AUTOMATION) =====
# This is the recommended approach for reliable trading

import asyncio
import threading
from pocket_option_service import PocketOptionService

# Global WebSocket client instance
ws_client = None
ws_thread = None
ws_loop = None
ws_ssid = None

def run_ws_loop(loop):
    """Run asyncio event loop in background thread."""
    asyncio.set_event_loop(loop)
    loop.run_forever()

def init_ws_client():
    """Initialize WebSocket event loop in background thread."""
    global ws_loop, ws_thread
    if ws_loop is None:
        ws_loop = asyncio.new_event_loop()
        ws_thread = threading.Thread(target=run_ws_loop, args=(ws_loop,), daemon=True)
        ws_thread.start()

@app.route('/ws/connect', methods=['POST'])
def ws_connect():
    """
    Connect to Pocket Option WebSocket API.
    
    If browser is logged in, auto-extracts SSID.
    Otherwise, pass SSID in request body.
    """
    global ws_client, ws_ssid
    
    try:
        data = request.get_json() or {}
        ssid = data.get('ssid')
        demo_mode = data.get('demo', True)
        
        # If no SSID provided, try to extract from browser
        if not ssid:
            ssid = browser_controller.get_ssid()
            if ssid:
                print(f"✅ Auto-extracted SSID from browser")
        
        if not ssid:
            return jsonify({
                "success": False,
                "error": "No SSID available. Please login to Pocket Option in browser first.",
                "hint": "Click Connect Broker, login, then try again"
            }), 400
        
        # Store SSID
        ws_ssid = ssid
        
        # Initialize event loop if needed
        init_ws_client()
        
        # Create WebSocket client
        ws_client = PocketOptionService(ssid=ssid, demo_mode=demo_mode)
        
        # Connect asynchronously
        future = asyncio.run_coroutine_threadsafe(ws_client.connect(), ws_loop)
        connected = future.result(timeout=10)
        
        if connected:
            return jsonify({
                "success": True,
                "message": "WebSocket connected to Pocket Option!",
                "demo_mode": demo_mode,
                "authenticated": ws_client.authenticated
            })
        else:
            return jsonify({
                "success": False,
                "error": "WebSocket connection failed"
            }), 500
            
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500


@app.route('/ws/trade', methods=['POST'])
def ws_trade():
    """
    Execute trade via WebSocket API.
    
    This is the RECOMMENDED way to trade - direct WebSocket, no browser clicking!
    
    Body:
        {
            "asset": "EURUSD",      // Currency pair
            "direction": "call",    // "call" or "put"
            "amount": 1,            // Trade amount in $
            "duration": 60          // Duration in seconds
        }
    """
    global ws_client
    
    if not ws_client or not ws_client.connected:
        # Try to auto-connect if browser has SSID
        ssid = browser_controller.get_ssid()
        if ssid:
            init_ws_client()
            ws_client = PocketOptionService(ssid=ssid, demo_mode=True)
            future = asyncio.run_coroutine_threadsafe(ws_client.connect(), ws_loop)
            try:
                future.result(timeout=10)
            except:
                pass
        
        if not ws_client or not ws_client.connected:
            return jsonify({
                "success": False,
                "error": "WebSocket not connected. Call /ws/connect first."
            }), 400
    
    try:
        data = request.get_json()
        asset = data.get('asset', 'EURUSD')
        direction = data.get('direction', 'call').lower()
        amount = float(data.get('amount', 1))
        duration = int(data.get('duration', 60))
        
        # Normalize asset for API (add _otc for OTC markets)
        asset = asset.replace('/', '').upper()
        if not asset.endswith('_otc'):
            asset = f"{asset}_otc"
        
        # Normalize direction
        if direction in ['buy', 'up', 'call']:
            direction = 'call'
        else:
            direction = 'put'
        
        # Execute trade via WebSocket
        future = asyncio.run_coroutine_threadsafe(
            ws_client.execute_trade(
                asset=asset,
                direction=direction,
                amount=amount,
                duration=duration
            ),
            ws_loop
        )
        trade_id = future.result(timeout=10)
        
        if trade_id:
            return jsonify({
                "success": True,
                "trade_id": trade_id,
                "asset": asset,
                "direction": direction,
                "amount": amount,
                "duration": duration,
                "method": "websocket"
            })
        else:
            return jsonify({
                "success": False,
                "error": "Trade execution failed"
            }), 500
            
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500


@app.route('/ws/status', methods=['GET'])
def ws_status():
    """Get WebSocket connection status."""
    global ws_client
    
    connected = ws_client is not None and ws_client.connected
    
    # Also check if browser has SSID available
    browser_ssid = browser_controller.get_ssid() if browser_controller.driver else None
    
    return jsonify({
        "success": True,
        "websocket_connected": connected,
        "authenticated": ws_client.authenticated if ws_client else False,
        "balance": ws_client.balance if ws_client else 0,
        "demo_mode": ws_client.demo_mode if ws_client else True,
        "ssid_available": browser_ssid is not None,
        "pending_trades": len(ws_client.pending_trades) if ws_client else 0
    })


@app.route('/ws/balance', methods=['GET'])
def ws_balance():
    """Get current balance from WebSocket."""
    global ws_client
    
    if not ws_client:
        return jsonify({"success": False, "balance": 0, "error": "Not connected"})
    
    return jsonify({
        "success": True,
        "balance": ws_client.balance,
        "demo_mode": ws_client.demo_mode
    })


@app.route('/ws/disconnect', methods=['POST'])
def ws_disconnect():
    """Disconnect WebSocket connection."""
    global ws_client
    
    if ws_client:
        try:
            future = asyncio.run_coroutine_threadsafe(ws_client.disconnect(), ws_loop)
            future.result(timeout=5)
        except:
            pass
        ws_client = None
    
    return jsonify({"success": True, "message": "WebSocket disconnected"})


# ===== CHROME EXTENSION ENDPOINTS =====

@app.route('/extension/trade', methods=['POST'])
def extension_trade():
    """Send a trade signal to the Chrome extension for execution."""
    data = request.get_json() or {}
    
    direction = data.get('direction', 'CALL').upper()
    amount = float(data.get('amount', 1))
    currency = data.get('currency', 'EUR/USD')
    
    # Send to extension via WebSocket bridge
    success = send_trade_to_extension(direction, amount, currency)
    
    return jsonify({
        "success": success,
        "direction": direction,
        "amount": amount,
        "currency": currency,
        "message": "Trade signal sent to extension" if success else "No extension connected"
    })


@app.route('/extension/status', methods=['GET'])
def extension_status():
    """Get Chrome extension connection status."""
    status = get_extension_status()
    return jsonify({
        "success": True,
        **status
    })


# ===== TRADE RESULT OBSERVATION (From Chrome Extension) =====

# Store observed trade results for the bot to learn from
observed_trade_results = []

@app.route('/trade-result', methods=['POST'])
def receive_trade_result():
    """
    Receive trade result from Chrome extension.
    Called when a trade completes on Pocket Option (detected via balance change).
    """
    try:
        data = request.get_json()
        
        if not data:
            return jsonify({"success": False, "error": "No data provided"}), 400
        
        result = data.get('result', 'UNKNOWN')
        profit = data.get('profit', 0)
        currency = data.get('currency', 'UNKNOWN')
        balance_before = data.get('balanceBefore', 0)
        balance_after = data.get('balanceAfter', 0)
        timestamp = data.get('timestamp', datetime.now().isoformat())
        
        # Create trade result entry
        trade_result = {
            'timestamp': timestamp,
            'currency': currency,
            'result': result,
            'profit': profit,
            'balance_before': balance_before,
            'balance_after': balance_after,
            'received_at': datetime.now().isoformat()
        }
        
        # Store for learning
        observed_trade_results.insert(0, trade_result)
        if len(observed_trade_results) > 100:  # Keep last 100
            observed_trade_results.pop()
        
        # Log it
        icon = "✅" if result == "WIN" else "❌"
        print(f"{icon} Trade Result Received: {result} on {currency} | Profit: ${profit:.2f}")
        print(f"   Balance: ${balance_before:.2f} -> ${balance_after:.2f}")
        
        # Record in risk filter for learning
        is_win = result == "WIN"
        risk_filter.record_trade_result(is_win, abs(profit))
        
        return jsonify({
            "success": True,
            "message": f"Trade result recorded: {result}",
            "total_observed": len(observed_trade_results)
        })
        
    except Exception as e:
        print(f"❌ Error receiving trade result: {e}")
        return jsonify({"success": False, "error": str(e)}), 500


@app.route('/trade-results', methods=['GET'])
def get_trade_results():
    """Get all observed trade results for the bot to learn from."""
    return jsonify({
        "success": True,
        "results": observed_trade_results,
        "count": len(observed_trade_results)
    })


# ===== PAPER TRADING MODE (Learn Without Executing) =====
# This mode allows the bot to learn from real market movements without placing actual trades

paper_trades = []  # Store pending paper trades
paper_results = []  # Store completed paper trade results
paper_trading_enabled = False  # Global toggle

@app.route('/paper-trade/enable', methods=['POST'])
def enable_paper_trading():
    """Enable paper trading mode with configurable duration."""
    global paper_trading_enabled
    data = request.get_json() or {}
    paper_trading_enabled = data.get('enabled', True)
    
    return jsonify({
        "success": True,
        "paper_trading": paper_trading_enabled,
        "message": "Paper trading ENABLED - Bot will learn without real trades" if paper_trading_enabled else "Paper trading DISABLED"
    })

@app.route('/paper-trade/predict', methods=['POST'])
def paper_trade_predict():
    """
    Make a paper trade prediction. The system will:
    1. Record the current price and prediction
    2. Wait for the specified duration (default 60 seconds)
    3. Check the outcome and record win/loss
    
    POST body:
        {
            "currency": "EUR/USD",
            "direction": "CALL" or "PUT",
            "duration": 60  # seconds, default 60 (1 minute)
        }
    """
    global paper_trades
    
    try:
        data = request.get_json() or {}
        currency = data.get('currency', 'EUR/USD')
        direction = data.get('direction', 'CALL').upper()
        duration = int(data.get('duration', 60))  # Default 1 minute
        
        if direction not in ['CALL', 'PUT']:
            return jsonify({"success": False, "error": "Direction must be CALL or PUT"})
        
        # Get current price (you'd get this from broker in real implementation)
        # For now, we'll track it via the extension observer
        trade = {
            "id": f"paper_{int(time.time())}",
            "currency": currency,
            "direction": direction,
            "duration": duration,
            "entry_time": datetime.now().isoformat(),
            "entry_timestamp": time.time(),
            "expiry_timestamp": time.time() + duration,
            "status": "PENDING",
            "entry_price": None,  # Will be filled by price observer
            "exit_price": None,
            "result": None,
            "pnl": None
        }
        
        paper_trades.append(trade)
        
        print(f"📝 Paper trade created: {direction} on {currency} for {duration}s")
        
        return jsonify({
            "success": True,
            "trade": trade,
            "message": f"Paper trade started - will check result in {duration} seconds"
        })
        
    except Exception as e:
        return jsonify({"success": False, "error": str(e)})

@app.route('/paper-trade/update-price', methods=['POST'])
def paper_trade_update_price():
    """
    Update price for paper trades. Called by the price observer.
    
    POST body:
        {
            "currency": "EUR/USD",
            "price": 1.08521
        }
    """
    global paper_trades, paper_results
    
    try:
        data = request.get_json() or {}
        currency = data.get('currency', 'EUR/USD')
        price = float(data.get('price', 0))
        
        if price == 0:
            return jsonify({"success": False, "error": "Invalid price"})
        
        current_time = time.time()
        completed = []
        
        for trade in paper_trades:
            if trade['currency'] == currency and trade['status'] == 'PENDING':
                # Set entry price if not set
                if trade['entry_price'] is None:
                    trade['entry_price'] = price
                    print(f"📊 Paper trade entry price set: {price}")
                
                # Check if trade expired
                if current_time >= trade['expiry_timestamp']:
                    trade['exit_price'] = price
                    trade['status'] = 'COMPLETED'
                    
                    # Determine win/loss
                    price_diff = price - trade['entry_price']
                    
                    if trade['direction'] == 'CALL':
                        trade['result'] = 'WIN' if price_diff > 0 else 'LOSS'
                    else:  # PUT
                        trade['result'] = 'WIN' if price_diff < 0 else 'LOSS'
                    
                    # Calculate simulated P&L (assuming $10 trade, 80% payout)
                    trade['pnl'] = 8.0 if trade['result'] == 'WIN' else -10.0
                    
                    completed.append(trade)
                    paper_results.append(trade)
                    
                    # Record for risk filter learning
                    risk_filter.record_trade_result(
                        trade['result'] == 'WIN',
                        abs(trade['pnl'])
                    )
                    
                    print(f"{'✅' if trade['result'] == 'WIN' else '❌'} Paper trade {trade['result']}: {trade['direction']} on {currency}")
                    print(f"   Entry: {trade['entry_price']:.5f} -> Exit: {price:.5f}")
        
        # Remove completed trades from pending list
        paper_trades = [t for t in paper_trades if t['status'] == 'PENDING']
        
        return jsonify({
            "success": True,
            "pending_trades": len(paper_trades),
            "completed": [t for t in completed]
        })
        
    except Exception as e:
        return jsonify({"success": False, "error": str(e)})

@app.route('/paper-trade/status', methods=['GET'])
def paper_trade_status():
    """Get paper trading status and statistics."""
    wins = sum(1 for r in paper_results if r.get('result') == 'WIN')
    losses = sum(1 for r in paper_results if r.get('result') == 'LOSS')
    total = wins + losses
    win_rate = (wins / total * 100) if total > 0 else 0
    total_pnl = sum(r.get('pnl', 0) for r in paper_results)
    
    return jsonify({
        "success": True,
        "enabled": paper_trading_enabled,
        "pending_trades": len(paper_trades),
        "completed_trades": len(paper_results),
        "stats": {
            "wins": wins,
            "losses": losses,
            "win_rate": round(win_rate, 1),
            "total_pnl": round(total_pnl, 2)
        },
        "recent_trades": paper_results[-10:]  # Last 10 results
    })

@app.route('/paper-trade/results', methods=['GET'])
def paper_trade_results():
    """Get all paper trade results."""
    return jsonify({
        "success": True,
        "results": paper_results,
        "count": len(paper_results)
    })

@app.route('/paper-trade/clear', methods=['POST'])
def paper_trade_clear():
    """Clear paper trading history."""
    global paper_trades, paper_results
    paper_trades = []
    paper_results = []
    return jsonify({
        "success": True,
        "message": "Paper trading history cleared"
    })

@app.route('/paper-trade/auto', methods=['POST'])
def paper_trade_auto():
    """
    Auto paper trading - generates signal and creates paper trade automatically.
    This is for learning without manual intervention.
    
    POST body:
        {
            "currency": "EUR/USD",
            "duration": 60,  # seconds
            "amount": 10     # simulated amount
        }
    """
    try:
        data = request.get_json() or {}
        currency = data.get('currency', 'EUR/USD')
        duration = int(data.get('duration', 60))
        
        # Get signal from the ML engine
        prices = price_cache.get(currency, [])
        if len(prices) < 20:
            # Generate some prices for testing
            import random
            base = {'EUR/USD': 1.085, 'GBP/USD': 1.27, 'USD/JPY': 149.5}.get(currency, 1.0)
            prices = [base + random.uniform(-base*0.002, base*0.002) for _ in range(50)]
        
        signal = signal_generator.generate_signal(currency, prices)
        
        if signal.confidence < 70:
            return jsonify({
                "success": False,
                "reason": f"Signal confidence too low: {signal.confidence:.1f}%"
            })
        
        # Create the paper trade
        trade = {
            "id": f"auto_paper_{int(time.time())}",
            "currency": currency,
            "direction": signal.direction,
            "confidence": signal.confidence,
            "duration": duration,
            "entry_time": datetime.now().isoformat(),
            "entry_timestamp": time.time(),
            "expiry_timestamp": time.time() + duration,
            "status": "PENDING",
            "entry_price": None,
            "exit_price": None,
            "result": None,
            "pnl": None
        }
        
        paper_trades.append(trade)
        
        print(f"🤖 Auto paper trade: {signal.direction} on {currency} (conf: {signal.confidence:.1f}%)")
        
        return jsonify({
            "success": True,
            "trade": trade,
            "signal": {
                "direction": signal.direction,
                "confidence": signal.confidence,
                "trend": signal.market_condition
            }
        })
        
    except Exception as e:
        return jsonify({"success": False, "error": str(e)})


if __name__ == '__main__':
    port = int(os.environ.get('ANALYSIS_PORT', 5001))
    
    print("=" * 60)
    print("🤖 MULTI-AI TRADING ANALYSIS SERVER")
    print("=" * 60)
    print(f"Server starting on http://localhost:{port}")
    print()
    print("AI Systems Active:")
    print(f"  🔮 FinGPT: {'Online' if fingpt_service.is_available() else 'Fallback mode'}")
    print("  🐍 Python ML: Online")
    print("  ⚖️ Consensus Engine: Online")
    print()
    print("Endpoints:")
    print("  GET  /              - Health check")
    print("  POST /analyze       - Full analysis (Python ML)")
    print("  POST /signal        - Quick signal (Python ML)")
    print("  POST /prices        - Update price cache")
    print("  POST /record        - Record trade result")
    print("  GET  /status        - Risk status")
    print()
    print("  🤖 MULTI-AI ENDPOINTS:")
    print("  POST /consensus     - ⭐ Multi-AI consensus signal")
    print("  POST /fingpt/signal - FinGPT trading signal")
    print("  POST /fingpt/sentiment - FinGPT sentiment analysis")
    print("  POST /fingpt/predict - FinGPT price prediction")
    print("  GET  /fingpt/status - FinGPT & Consensus status")
    print("  POST /consensus/record - Record result for learning")
    print()
    print("  💰 POCKET OPTION ENDPOINTS:")
    print("  POST /pocket-option/connect    - Connect with SSID")
    print("  POST /pocket-option/trade      - Execute trade")
    print("  GET  /pocket-option/result     - Get trade result")
    print("  GET  /pocket-option/balance    - Get balance")
    print("  GET  /pocket-option/status     - Connection status")
    print("  POST /pocket-option/disconnect - Disconnect")
    print()
    print("  🔌 CHROME EXTENSION ENDPOINTS:")
    print("  POST /extension/trade  - Send trade to Chrome extension")
    print("  GET  /extension/status - Get extension connection status")
    print("=" * 60)
    
    # Start Chrome Extension WebSocket bridge
    print("🔌 Starting Chrome Extension bridge on ws://localhost:5002...")
    start_extension_server(5002)
    
    app.run(host='0.0.0.0', port=port, debug=False, threaded=True)


