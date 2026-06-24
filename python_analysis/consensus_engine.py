"""
Multi-AI Consensus Engine
Combines signals from multiple AI systems for robust trading decisions

AI Systems:
1. FinGPT - Financial sentiment, prediction, forecasting (weight: 0.40)
2. Python ML - Technical analysis, indicators (weight: 0.35)
3. Java Brain - Learned patterns, historical performance (weight: 0.25)

The consensus engine:
- Gathers signals from all available AIs
- Applies weighted voting
- Requires minimum agreement before trading
- Learns which AI combinations work best over time
- Provides detailed reasoning for decisions

NO API LIMITS - All processing is local!
"""

import json
import os
from datetime import datetime
from typing import Dict, List, Optional, Any
import numpy as np

# Import local AI modules
from fingpt_service import get_fingpt_service, FinGPTService
from signal_generator import SignalGenerator


class ConsensusEngine:
    """
    Multi-AI Consensus Trading Engine
    
    Combines signals from multiple AI systems to make robust trading decisions.
    Only trades when sufficient AI agreement is reached.
    """
    
    # Default weights for each AI system
    DEFAULT_WEIGHTS = {
        "fingpt": 0.40,      # Financial AI specialist - highest weight
        "python_ml": 0.35,   # Technical analysis
        "java_brain": 0.25   # Learned patterns from Java
    }
    
    def __init__(self, config_path: str = None):
        """
        Initialize the Consensus Engine.
        
        Args:
            config_path: Optional path to config file for custom weights
        """
        # AI weights (can be adjusted based on performance)
        self.weights = self.DEFAULT_WEIGHTS.copy()
        
        # Minimum agreement required (0-1) — 60% weighted agreement for reliable trades
        self.min_agreement = 0.6
        
        # Whether to require FinGPT agreement for trades
        self.require_fingpt = True
        
        # Initialize AI components
        self.fingpt = get_fingpt_service()
        self.python_ml = SignalGenerator()
        
        # Performance tracking for adaptive weights
        self.performance_history = {
            "fingpt": {"correct": 0, "total": 0},
            "python_ml": {"correct": 0, "total": 0},
            "java_brain": {"correct": 0, "total": 0}
        }
        
        # Load config if available
        self.config_path = config_path or "ai_config.json"
        self._load_config()
        
        print("⚖️ Consensus Engine initialized")
        print(f"   Weights: FinGPT={self.weights['fingpt']:.0%}, "
              f"Python={self.weights['python_ml']:.0%}, "
              f"Java={self.weights['java_brain']:.0%}")
    
    def _load_config(self):
        """Load configuration from file if it exists."""
        if os.path.exists(self.config_path):
            try:
                with open(self.config_path, 'r') as f:
                    config = json.load(f)
                
                if 'consensus' in config:
                    cons_config = config['consensus']
                    if 'weights' in cons_config:
                        self.weights.update(cons_config['weights'])
                    if 'min_agreement' in cons_config:
                        self.min_agreement = cons_config['min_agreement']
                    if 'require_fingpt_agreement' in cons_config:
                        self.require_fingpt = cons_config['require_fingpt_agreement']
                
                print(f"   Loaded config from {self.config_path}")
            except Exception as e:
                print(f"   Could not load config: {e}")
    
    def _save_config(self):
        """Save current configuration to file."""
        config = {
            "consensus": {
                "min_agreement": self.min_agreement,
                "weights": self.weights,
                "require_fingpt_agreement": self.require_fingpt
            },
            "performance": self.performance_history
        }
        
        try:
            with open(self.config_path, 'w') as f:
                json.dump(config, f, indent=2)
        except Exception as e:
            print(f"Failed to save config: {e}")
    
    def get_consensus(self, symbol: str, prices: List[float],
                     java_signal: Dict = None,
                     news_headlines: List[str] = None) -> Dict[str, Any]:
        """
        Get consensus trading signal from all AI systems.
        
        This is the MAIN method for trading decisions.
        
        Args:
            symbol: Trading symbol (e.g., "EUR/USD")
            prices: Historical price data
            java_signal: Signal from Java Brain (optional)
                        {"direction": "BUY"/"SELL"/"WAIT", "confidence": 0-100}
            news_headlines: Optional list of recent news headlines
            
        Returns:
            {
                "consensus_signal": "BUY" | "SELL" | "WAIT",
                "consensus_confidence": 0-100,
                "agreement_level": "2/3", "3/3", etc.,
                "should_trade": True/False,
                "reasoning": "Detailed explanation...",
                "individual_signals": {
                    "fingpt": {...},
                    "python_ml": {...},
                    "java_brain": {...}
                },
                "ai_agreement": {...}
            }
        """
        individual_signals = {}
        votes = {}  # AI name -> (direction, confidence)
        
        # ===== 1. Get FinGPT Signal =====
        try:
            fingpt_result = self.fingpt.get_signal(symbol, prices, news_headlines)
            individual_signals["fingpt"] = fingpt_result
            
            fingpt_direction = fingpt_result.get("signal", "WAIT")
            fingpt_confidence = fingpt_result.get("confidence", 50) / 100
            votes["fingpt"] = (fingpt_direction, fingpt_confidence)
            
        except Exception as e:
            print(f"FinGPT error: {e}")
            individual_signals["fingpt"] = {"error": str(e)}
            votes["fingpt"] = ("WAIT", 0.5)
        
        # ===== 2. Get Python ML Signal =====
        try:
            if prices and len(prices) >= 20:
                python_result = self.python_ml.generate_signal(symbol, prices)
                individual_signals["python_ml"] = python_result
                
                python_signal = python_result.get("signal", "WAIT")
                python_confidence = python_result.get("confidence", 50) / 100
                
                # Map to standard direction
                if python_signal == "CALL":
                    python_direction = "BUY"
                elif python_signal == "PUT":
                    python_direction = "SELL"
                else:
                    python_direction = "WAIT"
                
                votes["python_ml"] = (python_direction, python_confidence)
            else:
                individual_signals["python_ml"] = {"error": "Insufficient price data"}
                votes["python_ml"] = ("WAIT", 0.5)
                
        except Exception as e:
            print(f"Python ML error: {e}")
            individual_signals["python_ml"] = {"error": str(e)}
            votes["python_ml"] = ("WAIT", 0.5)
        
        # ===== 3. Get Java Brain Signal (passed from Java) =====
        if java_signal:
            individual_signals["java_brain"] = java_signal
            java_direction = java_signal.get("direction", "WAIT")
            java_confidence = java_signal.get("confidence", 50) / 100
            votes["java_brain"] = (java_direction, java_confidence)
        else:
            individual_signals["java_brain"] = {"note": "No Java signal provided"}
            votes["java_brain"] = ("WAIT", 0.5)
        
        # ===== 4. Calculate Weighted Consensus =====
        consensus = self._calculate_consensus(votes)
        
        # ===== 5. Check Agreement Level =====
        agreement = self._check_agreement(votes, consensus["direction"])
        
        # ===== 6. Determine if We Should Trade =====
        should_trade = (
            consensus["weighted_confidence"] >= self.min_agreement and
            consensus["direction"] != "WAIT" and
            agreement["count"] >= 2  # At least 2 AIs must agree
        )
        
        # If FinGPT is required and disagrees, don't trade
        if self.require_fingpt and votes.get("fingpt", ("WAIT", 0))[0] != consensus["direction"]:
            if consensus["direction"] != "WAIT":
                should_trade = False
                agreement["notes"].append("FinGPT disagrees - trading blocked")
        
        # ===== 7. Build Reasoning =====
        reasoning_parts = []
        for ai_name, (direction, conf) in votes.items():
            reasoning_parts.append(f"{ai_name}: {direction} ({conf:.0%})")
        
        reasoning = f"Consensus: {agreement['ratio']} agree on {consensus['direction']}. " + \
                   " | ".join(reasoning_parts)
        
        if agreement["notes"]:
            reasoning += " | " + "; ".join(agreement["notes"])
        
        return {
            "consensus_signal": consensus["direction"],
            "consensus_confidence": round(consensus["weighted_confidence"] * 100, 1),
            "agreement_level": agreement["ratio"],
            "should_trade": should_trade,
            "reasoning": reasoning,
            "individual_signals": individual_signals,
            "ai_agreement": {
                "agreeing_ais": agreement["agreeing"],
                "disagreeing_ais": agreement["disagreeing"],
                "total_ais": len(votes)
            },
            "position_size": self._calculate_position_size(consensus, agreement)
        }
    
    def _calculate_consensus(self, votes: Dict[str, tuple]) -> Dict[str, Any]:
        """
        Calculate weighted consensus from AI votes.
        
        Args:
            votes: Dict of AI name -> (direction, confidence)
            
        Returns:
            {
                "direction": "BUY" | "SELL" | "WAIT",
                "weighted_confidence": 0-1,
                "buy_score": float,
                "sell_score": float
            }
        """
        buy_score = 0.0
        sell_score = 0.0
        wait_score = 0.0
        total_weight = 0.0
        
        for ai_name, (direction, confidence) in votes.items():
            weight = self.weights.get(ai_name, 0.2)
            total_weight += weight
            
            weighted_vote = weight * confidence
            
            if direction == "BUY":
                buy_score += weighted_vote
            elif direction == "SELL":
                sell_score += weighted_vote
            else:
                wait_score += weighted_vote
        
        # Normalize scores
        if total_weight > 0:
            buy_score /= total_weight
            sell_score /= total_weight
            wait_score /= total_weight
        
        # Determine direction
        max_score = max(buy_score, sell_score, wait_score)
        
        if max_score == buy_score and buy_score > 0.3:
            direction = "BUY"
            confidence = buy_score
        elif max_score == sell_score and sell_score > 0.3:
            direction = "SELL"
            confidence = sell_score
        else:
            direction = "WAIT"
            confidence = wait_score
        
        return {
            "direction": direction,
            "weighted_confidence": confidence,
            "buy_score": buy_score,
            "sell_score": sell_score,
            "wait_score": wait_score
        }
    
    def _check_agreement(self, votes: Dict[str, tuple], 
                        consensus_direction: str) -> Dict[str, Any]:
        """Check how many AIs agree with the consensus direction."""
        agreeing = []
        disagreeing = []
        notes = []
        
        for ai_name, (direction, confidence) in votes.items():
            if direction == consensus_direction:
                agreeing.append(ai_name)
            elif direction != "WAIT":
                disagreeing.append(ai_name)
        
        total = len(votes)
        count = len(agreeing)
        
        # Add notes for notable situations
        if "fingpt" in disagreeing:
            notes.append("FinGPT disagrees")
        if count == total:
            notes.append("Perfect agreement!")
        
        return {
            "agreeing": agreeing,
            "disagreeing": disagreeing,
            "count": count,
            "ratio": f"{count}/{total}",
            "notes": notes
        }
    
    def _calculate_position_size(self, consensus: Dict, agreement: Dict) -> float:
        """Calculate recommended position size based on agreement."""
        base_size = 1.0
        
        # Reduce size if not all AIs agree
        agreement_ratio = agreement["count"] / max(1, agreement["count"] + len(agreement["disagreeing"]))
        
        if agreement_ratio >= 1.0:
            size_multiplier = 1.0  # Full agreement
        elif agreement_ratio >= 0.66:
            size_multiplier = 0.75  # 2/3 agree
        else:
            size_multiplier = 0.5  # Weak agreement
        
        # Adjust by confidence
        confidence_multiplier = min(1.0, consensus["weighted_confidence"])
        
        return round(base_size * size_multiplier * confidence_multiplier, 2)
    
    def record_result(self, trade_result: Dict):
        """
        Record trade result for performance tracking and weight adaptation.
        
        Args:
            trade_result: {
                "won": True/False,
                "profit": float,
                "signals_used": ["fingpt", "python_ml", "java_brain"],
                "symbol": "EUR/USD"
            }
        """
        won = trade_result.get("won", False)
        signals_used = trade_result.get("signals_used", [])
        
        for ai_name in signals_used:
            if ai_name in self.performance_history:
                self.performance_history[ai_name]["total"] += 1
                if won:
                    self.performance_history[ai_name]["correct"] += 1
        
        # Adapt weights if we have enough data
        self._adapt_weights()
        
        # Save updated performance
        self._save_config()
    
    def _adapt_weights(self):
        """Adapt AI weights based on historical performance."""
        min_trades = 20  # Need at least 20 trades before adapting
        
        # Check if we have enough data
        total_trades = sum(h["total"] for h in self.performance_history.values())
        if total_trades < min_trades * len(self.performance_history):
            return
        
        # Calculate accuracy for each AI
        accuracies = {}
        for ai_name, history in self.performance_history.items():
            if history["total"] > 0:
                accuracies[ai_name] = history["correct"] / history["total"]
            else:
                accuracies[ai_name] = 0.5  # Default
        
        # Calculate new weights (proportional to accuracy)
        total_accuracy = sum(accuracies.values())
        if total_accuracy > 0:
            new_weights = {}
            for ai_name, accuracy in accuracies.items():
                # Blend old weight with accuracy-based weight
                old_weight = self.weights.get(ai_name, 0.33)
                accuracy_weight = accuracy / total_accuracy
                new_weights[ai_name] = 0.7 * old_weight + 0.3 * accuracy_weight
            
            # Normalize weights to sum to 1
            weight_sum = sum(new_weights.values())
            for ai_name in new_weights:
                self.weights[ai_name] = new_weights[ai_name] / weight_sum
            
            print(f"⚖️ Adapted weights: {self.weights}")
    
    def get_status(self) -> Dict[str, Any]:
        """Get current status of the consensus engine."""
        return {
            "weights": self.weights,
            "min_agreement": self.min_agreement,
            "require_fingpt": self.require_fingpt,
            "performance": self.performance_history,
            "fingpt_status": self.fingpt.get_status(),
            "python_ml_available": True
        }


# Singleton instance
_engine = None

def get_consensus_engine() -> ConsensusEngine:
    """Get the consensus engine singleton."""
    global _engine
    if _engine is None:
        _engine = ConsensusEngine()
    return _engine


if __name__ == "__main__":
    # Test the consensus engine
    print("=" * 50)
    print("⚖️ Testing Consensus Engine")
    print("=" * 50)
    
    engine = get_consensus_engine()
    print(f"\nStatus: {json.dumps(engine.get_status(), indent=2)}")
    
    # Test with dummy data
    print("\n--- Testing Consensus ---")
    dummy_prices = [1.05 + i * 0.001 + np.random.normal(0, 0.0005) for i in range(50)]
    
    # Simulate Java signal
    java_signal = {
        "direction": "BUY",
        "confidence": 72
    }
    
    result = engine.get_consensus(
        symbol="EUR/USD",
        prices=dummy_prices,
        java_signal=java_signal,
        news_headlines=["Market rallies on positive economic data"]
    )
    
    print(f"\nConsensus Signal: {result['consensus_signal']}")
    print(f"Confidence: {result['consensus_confidence']}%")
    print(f"Agreement: {result['agreement_level']}")
    print(f"Should Trade: {result['should_trade']}")
    print(f"Position Size: {result['position_size']}")
    print(f"Reasoning: {result['reasoning']}")
