"""
Pocket Option Selenium Service - Browser automation for Pocket Option
Uses Selenium with headless Chrome for stable, undetectable trading

Features:
- Runs in headless mode (invisible)
- Login via email/password
- Execute trades by clicking BUY/SELL buttons
- Read trade results
- Works with demo account
"""

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from webdriver_manager.chrome import ChromeDriverManager
from selenium.common.exceptions import TimeoutException, NoSuchElementException
import time
import logging
from typing import Optional, Dict
from datetime import datetime
import threading

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger('PocketOptionSelenium')


class PocketOptionSelenium:
    """
    Selenium-based automation for Pocket Option trading.
    Uses headless Chrome browser to execute trades.
    """
    
    # URLs
    LOGIN_URL = "https://pocketoption.com/en/login/"
    TRADE_URL = "https://po.trade/en/cabinet/demo-quick-high-low/"
    DEMO_URL = "https://pocketoption.com/en/cabinet/demo-quick-high-low/"
    
    def __init__(self, headless: bool = True, demo_mode: bool = True):
        """
        Initialize Selenium service.
        
        Args:
            headless: If True, run browser invisibly (default: True)
            demo_mode: If True, use demo account (default: True for safety)
        """
        self.headless = headless
        self.demo_mode = demo_mode
        self.driver = None
        self.logged_in = False
        self.balance = 0.0
        self.trade_history = []
        self._lock = threading.Lock()
        
    def _create_driver(self):
        """Create Chrome WebDriver with appropriate options."""
        chrome_options = Options()
        
        if self.headless:
            chrome_options.add_argument("--headless=new")  # New headless mode
            
        # Common options for stability
        chrome_options.add_argument("--no-sandbox")
        chrome_options.add_argument("--disable-dev-shm-usage")
        chrome_options.add_argument("--disable-gpu")
        chrome_options.add_argument("--window-size=1920,1080")
        chrome_options.add_argument("--disable-blink-features=AutomationControlled")
        chrome_options.add_argument("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        
        # Disable automation flags to avoid detection
        chrome_options.add_experimental_option("excludeSwitches", ["enable-automation"])
        chrome_options.add_experimental_option("useAutomationExtension", False)
        
        try:
            service = Service(ChromeDriverManager().install())
            self.driver = webdriver.Chrome(service=service, options=chrome_options)
            self.driver.implicitly_wait(10)
            logger.info("Chrome WebDriver initialized")
            return True
        except Exception as e:
            logger.error(f"Failed to create WebDriver: {e}")
            return False
            
    def login(self, email: str, password: str) -> bool:
        """
        Login to Pocket Option.
        
        Args:
            email: Account email
            password: Account password
            
        Returns:
            True if login successful
        """
        with self._lock:
            try:
                if not self.driver:
                    if not self._create_driver():
                        return False
                        
                logger.info("Navigating to login page...")
                self.driver.get(self.LOGIN_URL)
                time.sleep(3)
                
                # Find and fill login form using correct selectors from DOM inspection
                # Email input: id='email'
                email_input = WebDriverWait(self.driver, 15).until(
                    EC.presence_of_element_located((By.CSS_SELECTOR, "#email, input[placeholder='Email *']"))
                )
                email_input.clear()
                email_input.send_keys(email)
                logger.info(f"Entered email: {email}")
                
                # Password input: id='password'
                password_input = self.driver.find_element(By.CSS_SELECTOR, "#password, input[placeholder='Password *']")
                password_input.clear()
                password_input.send_keys(password)
                logger.info("Entered password")
                
                time.sleep(1)
                
                # Click login button: .btn-green-light
                login_button = self.driver.find_element(By.CSS_SELECTOR, ".btn-green-light, button.btn-green-light")
                login_button.click()
                logger.info("Clicked login button")
                
                # Wait for redirect to trading page
                time.sleep(8)
                
                # Take screenshot for debugging
                self.take_screenshot("d:/Trading bot/python_analysis/login_result.png")
                
                # Check if logged in by looking for trading elements or cabinet in URL
                current_url = self.driver.current_url
                logger.info(f"Current URL after login: {current_url}")
                
                if "cabinet" in current_url or "trade" in current_url:
                    self.logged_in = True
                    logger.info("Login successful! Navigating to demo trading...")
                    
                    # Navigate to demo trading page
                    self.driver.get(self.DEMO_URL)
                    time.sleep(3)
                    
                    self._update_balance()
                    return True
                else:
                    logger.warning(f"Login may have failed - URL is: {current_url}")
                    # Check for error messages
                    try:
                        error_elem = self.driver.find_element(By.CSS_SELECTOR, ".alert-danger, .error-message, .form-error")
                        logger.error(f"Login error: {error_elem.text}")
                    except NoSuchElementException:
                        pass
                    return False
                    
            except Exception as e:
                logger.error(f"Login failed: {e}")
                # Take screenshot for debugging
                try:
                    self.take_screenshot("d:/Trading bot/python_analysis/login_error.png")
                except:
                    pass
                return False
                
    def login_with_cookies(self, cookies: list) -> bool:
        """
        Login using saved cookies (alternative to email/password).
        
        Args:
            cookies: List of cookie dicts from previous session
        """
        with self._lock:
            try:
                if not self.driver:
                    if not self._create_driver():
                        return False
                        
                # First navigate to domain
                self.driver.get("https://pocketoption.com")
                time.sleep(2)
                
                # Add cookies
                for cookie in cookies:
                    try:
                        self.driver.add_cookie(cookie)
                    except Exception as e:
                        logger.debug(f"Cookie error: {e}")
                        
                # Refresh to apply cookies
                self.driver.refresh()
                time.sleep(3)
                
                # Navigate to trading page
                self.driver.get(self.DEMO_URL if self.demo_mode else self.TRADE_URL)
                time.sleep(3)
                
                if "cabinet" in self.driver.current_url:
                    self.logged_in = True
                    logger.info("Cookie login successful!")
                    self._update_balance()
                    return True
                    
                return False
                
            except Exception as e:
                logger.error(f"Cookie login failed: {e}")
                return False
                
    def navigate_to_trading(self) -> bool:
        """Navigate to the trading page."""
        with self._lock:
            try:
                url = self.DEMO_URL if self.demo_mode else self.TRADE_URL
                mode_str = "DEMO" if self.demo_mode else "REAL"
                logger.info(f"Navigating to trading page ({mode_str})...")
                self.driver.get(url)
                time.sleep(3)
                return True
            except Exception as e:
                logger.error(f"Navigation failed: {e}")
                return False
                
    def _update_balance(self):
        """Read current balance from page."""
        try:
            # Look for balance element
            balance_selectors = [
                ".balance-value",
                ".balance__value", 
                "[data-test='balance']",
                ".js-balance",
                ".trading-info__value"
            ]
            
            for selector in balance_selectors:
                try:
                    balance_elem = self.driver.find_element(By.CSS_SELECTOR, selector)
                    balance_text = balance_elem.text.strip()
                    # Parse balance (remove $ and commas)
                    self.balance = float(balance_text.replace('$', '').replace(',', '').strip())
                    logger.info(f"Balance: ${self.balance:.2f}")
                    return
                except NoSuchElementException:
                    continue
                    
        except Exception as e:
            logger.debug(f"Could not read balance: {e}")
            
    def select_asset(self, asset: str) -> bool:
        """
        Select trading asset.
        
        Args:
            asset: Asset name like "EUR/USD" or "EURUSD_otc"
        """
        with self._lock:
            try:
                # Click on asset selector
                asset_selector = self.driver.find_element(By.CSS_SELECTOR, ".current-asset, .asset-selector, .trading-pair")
                asset_selector.click()
                time.sleep(1)
                
                # Search for asset
                search_input = self.driver.find_element(By.CSS_SELECTOR, ".search-asset input, .asset-search input")
                search_input.clear()
                search_input.send_keys(asset.replace("/", "").replace("_otc", ""))
                time.sleep(1)
                
                # Click on first matching result
                asset_item = self.driver.find_element(By.CSS_SELECTOR, ".asset-item, .asset-list-item")
                asset_item.click()
                time.sleep(1)
                
                logger.info(f"Selected asset: {asset}")
                return True
                
            except Exception as e:
                logger.error(f"Failed to select asset: {e}")
                return False
                
    def set_amount(self, amount: float) -> bool:
        """
        Set trade amount.
        
        Args:
            amount: Trade amount in dollars
        """
        with self._lock:
            try:
                # Find amount input
                amount_input = self.driver.find_element(By.CSS_SELECTOR, ".trading-amount input, .amount-input, input[name='amount']")
                amount_input.clear()
                amount_input.send_keys(str(int(amount)))
                logger.info(f"Set amount: ${amount}")
                return True
            except Exception as e:
                logger.error(f"Failed to set amount: {e}")
                return False
                
    def set_expiry(self, seconds: int) -> bool:
        """
        Set trade expiry time.
        
        Args:
            seconds: Expiry time in seconds
        """
        with self._lock:
            try:
                # Convert to minutes if needed
                minutes = max(1, seconds // 60)
                
                # Click on time selector
                time_selector = self.driver.find_element(By.CSS_SELECTOR, ".trading-time, .expiry-time, .time-selector")
                time_selector.click()
                time.sleep(0.5)
                
                # Select time option
                time_options = self.driver.find_elements(By.CSS_SELECTOR, ".time-option, .time-item")
                for opt in time_options:
                    if f"{minutes}" in opt.text:
                        opt.click()
                        logger.info(f"Set expiry: {minutes} minute(s)")
                        return True
                        
                return False
                
            except Exception as e:
                logger.error(f"Failed to set expiry: {e}")
                return False
                
    def execute_trade(self, direction: str, amount: float = None, expiry: int = 60) -> Optional[Dict]:
        """
        Execute a trade.
        
        Args:
            direction: "BUY" or "SELL" (also accepts "CALL"/"PUT")
            amount: Trade amount (optional, uses current setting if not specified)
            expiry: Expiry time in seconds
            
        Returns:
            Trade info dict with 'trade_id', 'direction', 'amount', 'time'
        """
        with self._lock:
            try:
                # Set amount if specified
                if amount:
                    self.set_amount(amount)
                    
                # Normalize direction
                is_buy = direction.upper() in ["BUY", "CALL", "UP"]
                
                # Find and click the appropriate button
                if is_buy:
                    button_selectors = [
                        ".btn-call",
                        ".btn-buy", 
                        ".trading-btn--call",
                        "button[data-action='call']",
                        ".call-btn"
                    ]
                else:
                    button_selectors = [
                        ".btn-put",
                        ".btn-sell",
                        ".trading-btn--put", 
                        "button[data-action='put']",
                        ".put-btn"
                    ]
                    
                button = None
                for selector in button_selectors:
                    try:
                        button = self.driver.find_element(By.CSS_SELECTOR, selector)
                        if button.is_displayed() and button.is_enabled():
                            break
                    except NoSuchElementException:
                        continue
                        
                if not button:
                    logger.error("Could not find trade button")
                    return None
                    
                # Click the button
                button.click()
                
                trade_info = {
                    "trade_id": f"trade_{int(time.time() * 1000)}",
                    "direction": "BUY" if is_buy else "SELL",
                    "amount": amount or 0,
                    "time": datetime.now().isoformat(),
                    "status": "pending"
                }
                
                self.trade_history.append(trade_info)
                logger.info(f"Trade executed: {trade_info['direction']} ${trade_info['amount']}")
                
                return trade_info
                
            except Exception as e:
                logger.error(f"Trade execution failed: {e}")
                return None
                
    def get_last_trade_result(self, timeout: int = 120) -> Optional[Dict]:
        """
        Wait for and get the result of the last trade.
        
        Args:
            timeout: Max seconds to wait for result
            
        Returns:
            Dict with 'won' and 'profit' keys
        """
        start_time = time.time()
        
        while time.time() - start_time < timeout:
            try:
                # Look for trade result notification
                result_selectors = [
                    ".trade-result",
                    ".deal-result",
                    ".notification--trade"
                ]
                
                for selector in result_selectors:
                    try:
                        result_elem = self.driver.find_element(By.CSS_SELECTOR, selector)
                        result_text = result_elem.text.lower()
                        
                        if "win" in result_text or "profit" in result_text or "+" in result_text:
                            # Parse profit amount
                            import re
                            profit_match = re.search(r'\+?\$?([\d.]+)', result_text)
                            profit = float(profit_match.group(1)) if profit_match else 0
                            
                            return {
                                "won": True,
                                "profit": profit,
                                "completed_at": datetime.now().isoformat()
                            }
                        elif "loss" in result_text or "-" in result_text:
                            return {
                                "won": False,
                                "profit": 0,
                                "completed_at": datetime.now().isoformat()
                            }
                    except NoSuchElementException:
                        continue
                        
            except Exception as e:
                logger.debug(f"Result check error: {e}")
                
            time.sleep(1)
            
        return None
        
    def get_balance(self) -> float:
        """Get current account balance."""
        self._update_balance()
        return self.balance
        
    def is_logged_in(self) -> bool:
        """Check if currently logged in."""
        return self.logged_in and self.driver is not None
        
    def get_cookies(self) -> list:
        """Get current session cookies for later use."""
        if self.driver:
            return self.driver.get_cookies()
        return []
        
    def take_screenshot(self, filename: str = "screenshot.png"):
        """Take a screenshot for debugging."""
        if self.driver:
            self.driver.save_screenshot(filename)
            logger.info(f"Screenshot saved: {filename}")
            
    def close(self):
        """Close the browser and clean up."""
        if self.driver:
            try:
                self.driver.quit()
                logger.info("Browser closed")
            except Exception as e:
                logger.debug(f"Close error: {e}")
            finally:
                self.driver = None
                self.logged_in = False
                
    def __del__(self):
        """Cleanup on object destruction."""
        self.close()
        
    def get_status(self) -> Dict:
        """Get current status."""
        return {
            "logged_in": self.logged_in,
            "demo_mode": self.demo_mode,
            "headless": self.headless,
            "balance": self.balance,
            "trades_executed": len(self.trade_history)
        }


# Global instance for API server
selenium_client = PocketOptionSelenium(headless=True, demo_mode=True)


# Convenience functions for API
def login_pocket_option(email: str, password: str, demo: bool = True) -> bool:
    """Login to Pocket Option."""
    global selenium_client
    selenium_client = PocketOptionSelenium(headless=True, demo_mode=demo)
    return selenium_client.login(email, password)


def execute_pocket_option_trade(direction: str, amount: float = 10) -> Optional[Dict]:
    """Execute a trade on Pocket Option."""
    if not selenium_client.is_logged_in():
        return None
    return selenium_client.execute_trade(direction, amount)


def get_pocket_option_status() -> Dict:
    """Get Pocket Option connection status."""
    return selenium_client.get_status()
