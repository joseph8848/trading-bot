"""
Auto Paper Trader (Simulation Mode)

This script acts as an automated bot that trades on a virtual balance.
It connects to the local API server, fetches live signals, and automatically 
executes them using the Auto-Learner module.

Watch it trade in real-time without risking real money!
"""

import time
import requests
import json
import os
from datetime import datetime
from colorama import init, Fore, Style

# Initialize colorama for nice console colors
init(autoreset=True)

API_BASE = "http://127.0.0.1:5001"
STATE_FILE = "paper_trader_state.json"

class PaperTrader:
    def __init__(self, starting_balance=10000.0, trade_amount=50.0):
        self.balance = starting_balance
        self.trade_amount = trade_amount
        self.trades_taken = 0
        self.wins = 0
        self.losses = 0
        self.last_trade_time = 0
        self.active_trade_ids = set()
        
        self.load_state()
        print(f"\n{Fore.CYAN}{Style.BRIGHT}===============================================")
        print(f"{Fore.CYAN}🤖 ELITE PAPER TRADER - SIMULATION MODE ACTIVE")
        print(f"{Fore.CYAN}===============================================")
        print(f"💰 Starting Balance : ${self.balance:.2f}")
        print(f"💵 Trade Amount     : ${self.trade_amount:.2f}")
        print(f"📈 Win Rate         : {self.get_win_rate():.1f}% ({self.wins}W / {self.losses}L)")
        print(f"{Fore.CYAN}===============================================\n")

    def get_win_rate(self):
        total = self.wins + self.losses
        return (self.wins / total * 100) if total > 0 else 0.0

    def load_state(self):
        try:
            if os.path.exists(STATE_FILE):
                with open(STATE_FILE, 'r') as f:
                    state = json.load(f)
                self.balance = state.get('balance', self.balance)
                self.wins = state.get('wins', 0)
                self.losses = state.get('losses', 0)
                self.trades_taken = state.get('trades_taken', 0)
        except Exception:
            pass

    def save_state(self):
        try:
            with open(STATE_FILE, 'w') as f:
                json.dump({
                    'balance': self.balance,
                    'wins': self.wins,
                    'losses': self.losses,
                    'trades_taken': self.trades_taken
                }, f)
        except Exception:
            pass

    def run(self):
        """Main loop that polls for signals and executes them."""
        print(f"{Fore.YELLOW}📡 Waiting for live market data to stabilize...")
        time.sleep(5)  # Let server gather data
        
        while True:
            try:
                self.check_trade_outcomes()
                self.poll_signals()
            except requests.exceptions.ConnectionError:
                print(f"{Fore.RED}⚠️ Cannot connect to API Server. Is it running?")
                time.sleep(10)
            except Exception as e:
                print(f"{Fore.RED}⚠️ Error: {e}")
                
            # Wait 5 seconds before checking again
            time.sleep(5)

    def poll_signals(self):
        """Fetch current signals from the dashboard API."""
        response = requests.get(f"{API_BASE}/dashboard/signals", timeout=5)
        if response.status_code != 200:
            return
            
        data = response.json()
        signals = data.get('signals', [])
        
        # Filter for tradeable signals
        tradeable = [s for s in signals if s.get('can_trade') and s.get('signal') != 'WAIT']
        
        # Sort by confidence
        tradeable.sort(key=lambda x: x.get('confidence', 0), reverse=True)
        
        if tradeable:
            best_signal = tradeable[0]
            self.execute_trade(best_signal)
        else:
            # Just print a small heartbeat dot so we know it's alive
            print(".", end="", flush=True)

    def execute_trade(self, signal):
        """Execute a trade if we haven't traded recently."""
        # Enforce a local cooldown to prevent spamming the same signal
        if time.time() - self.last_trade_time < 60:
            return
            
        currency = signal['currency']
        direction = signal['signal']
        confidence = signal['confidence']
        price = signal.get('current_price', 0)
        expiry_seconds = signal.get('optimal_expiry', 300)
        
        # Normalize direction
        if direction in ['CALL', 'BUY']:
            dir_str = f"{Fore.GREEN}▲ BUY{Style.RESET_ALL}"
            api_dir = 'BUY'
        else:
            dir_str = f"{Fore.RED}▼ SELL{Style.RESET_ALL}"
            api_dir = 'SELL'
            
        print(f"\n\n{Fore.MAGENTA}⚡ SIGNAL DETECTED!{Style.RESET_ALL}")
        print(f"Pair: {Fore.CYAN}{currency}{Style.RESET_ALL} | Direction: {dir_str} | Confidence: {Fore.YELLOW}{confidence}%{Style.RESET_ALL} | Expiry: {expiry_seconds//60}m | Price: {price}")
        
        # Send to API to record the trade for Auto-Learner
        try:
            payload = {
                "currency": currency,
                "direction": api_dir,
                "confidence": confidence,
                "expiry_seconds": expiry_seconds,
                "source": "paper_trader"
            }
            res = requests.post(f"{API_BASE}/dashboard/record-trade", json=payload)
            if res.status_code == 200:
                trade_data = res.json()
                if trade_data.get('success'):
                    trade_id = trade_data['trade_id']
                    self.active_trade_ids.add(trade_id)
                    self.last_trade_time = time.time()
                    self.trades_taken += 1
                    
                    # Deduct trade amount from balance
                    self.balance -= self.trade_amount
                    self.save_state()
                    
                    print(f"{Fore.GREEN}✅ Virtual trade placed!{Style.RESET_ALL} Deducted ${self.trade_amount:.2f}.")
                    print(f"💰 New Balance: ${self.balance:.2f} (Trade expires in 5 mins)")
                else:
                    print(f"{Fore.RED}❌ Server rejected trade: {trade_data.get('error')}{Style.RESET_ALL}")
        except Exception as e:
            print(f"{Fore.RED}❌ Failed to place virtual trade: {e}{Style.RESET_ALL}")

    def check_trade_outcomes(self):
        """Ask the server for learning stats to see if our trades won/lost."""
        if not self.active_trade_ids:
            return
            
        response = requests.get(f"{API_BASE}/dashboard/learning-stats", timeout=5)
        if response.status_code != 200:
            return
            
        data = response.json()
        recent_trades = data.get('recent_trades', [])
        
        completed_ids = []
        for trade_id in list(self.active_trade_ids):
            # Look for our trade in the recent completed trades
            trade = next((t for t in recent_trades if t.get('trade_id') == trade_id), None)
            
            if trade:
                status = trade.get('status')
                currency = trade.get('currency')
                direction = trade.get('direction')
                
                print(f"\n{Fore.BLUE}🔔 TRADE COMPLETED: {currency} {direction}{Style.RESET_ALL}")
                
                if status == 'WON':
                    self.wins += 1
                    # Payout is typically 85% of investment + original investment back
                    payout = self.trade_amount + (self.trade_amount * 0.85)
                    self.balance += payout
                    print(f"{Fore.GREEN}🏆 WON! +${(self.trade_amount * 0.85):.2f} profit{Style.RESET_ALL}")
                elif status == 'LOST':
                    self.losses += 1
                    print(f"{Fore.RED}💥 LOST. -${self.trade_amount:.2f} loss{Style.RESET_ALL}")
                else:
                    # Expired/Error — refund the money
                    self.balance += self.trade_amount
                    print(f"{Fore.YELLOW}⚠️ {status} - Trade refunded.{Style.RESET_ALL}")
                
                print(f"💰 Current Balance: ${self.balance:.2f} | Win Rate: {self.get_win_rate():.1f}%")
                completed_ids.append(trade_id)
                self.save_state()
                
        # Clean up completed trades from our active tracking set
        for tid in completed_ids:
            self.active_trade_ids.remove(tid)


if __name__ == "__main__":
    try:
        trader = PaperTrader()
        trader.run()
    except KeyboardInterrupt:
        print(f"\n{Fore.YELLOW}🛑 Paper Trader stopped manually.{Style.RESET_ALL}")
