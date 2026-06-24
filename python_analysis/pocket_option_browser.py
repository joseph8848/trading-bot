"""
Pocket Option Browser Controller - Manual Login with Bot Control
Opens a visible Chrome window for you to login, then bot executes trades.

This approach:
- Opens visible browser (you can see what's happening)
- You login manually (no security issues)
- Bot detects when you're logged in
- Bot clicks BUY/SELL buttons when signals are strong
"""

try:
    from selenium import webdriver
    from selenium.webdriver.common.by import By
    from selenium.webdriver.support.ui import WebDriverWait
    from selenium.webdriver.support import expected_conditions as EC
    from selenium.webdriver.chrome.options import Options
    from selenium.webdriver.chrome.service import Service
    from webdriver_manager.chrome import ChromeDriverManager
    from selenium.common.exceptions import TimeoutException, NoSuchElementException
    SELENIUM_AVAILABLE = True
except ImportError:
    SELENIUM_AVAILABLE = False
    print("⚠️ Selenium not installed. Browser automation disabled.")
import time
import logging
from typing import Optional, Dict
from datetime import datetime
import threading

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger('PocketOptionBrowser')


class PocketOptionBrowser:
    """
    Browser controller for Pocket Option - opens visible window for manual login.
    After you login, the bot can execute trades automatically.
    """
    
    # URLs
    DEMO_URL = "https://pocketoption.com/en/cabinet/demo-quick-high-low/"
    REAL_URL = "https://pocketoption.com/en/cabinet/quick-high-low/"
    LOGIN_URL = "https://pocketoption.com/en/login/"
    
    def __init__(self, demo_mode: bool = True):
        """
        Initialize browser controller.
        
        Args:
            demo_mode: If True, trade on demo account (default: True for safety)
        """
        self.demo_mode = demo_mode
        self.driver = None
        self.is_ready = False
        self.balance = 0.0
        self.trade_history = []
        self._lock = threading.Lock()
        self._check_thread = None
        self._running = False
        
    def _create_driver(self):
        """Create Chrome browser using USER'S EXISTING PROFILE to bypass security detection."""
        import os
        
        chrome_options = Options()
        
        # ===== USE USER'S CHROME PROFILE - MOST RELIABLE =====
        # This makes the browser appear as the user's normal trusted browser
        user_data_dir = os.path.expanduser("~") + "\\AppData\\Local\\Google\\Chrome\\User Data"
        
        # Check if Chrome profile exists
        if os.path.exists(user_data_dir):
            logger.info(f"🔐 Using YOUR Chrome profile for trusted login")
            chrome_options.add_argument(f"--user-data-dir={user_data_dir}")
            chrome_options.add_argument("--profile-directory=Default")
        else:
            logger.warning("Chrome profile not found, using fresh browser")
        
        # Window settings
        chrome_options.add_argument("--window-size=1400,900")
        chrome_options.add_argument("--start-maximized")
        
        # MINIMAL flags - don't add suspicious options
        chrome_options.add_argument("--disable-blink-features=AutomationControlled")
        chrome_options.add_experimental_option("excludeSwitches", ["enable-automation", "enable-logging"])
        chrome_options.add_experimental_option("useAutomationExtension", False)
        
        # Keep browser open even if script ends
        chrome_options.add_experimental_option("detach", True)
        
        # Disable password saving prompts
        prefs = {
            "credentials_enable_service": False,
            "profile.password_manager_enabled": False,
        }
        chrome_options.add_experimental_option("prefs", prefs)
        
        try:
            service = Service(ChromeDriverManager().install())
            self.driver = webdriver.Chrome(service=service, options=chrome_options)
            
            # Remove automation flag
            self.driver.execute_cdp_cmd("Page.addScriptToEvaluateOnNewDocument", {
                "source": "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});"
            })
            
            # Set page load timeout
            self.driver.set_page_load_timeout(90)
            
            logger.info("✅ Chrome opened with YOUR profile - should be trusted!")
            return True
        except Exception as e:
            logger.error(f"Failed to create browser with profile: {e}")
            # Fallback: try without profile
            try:
                logger.info("Trying fallback without profile...")
                chrome_options2 = Options()
                chrome_options2.add_argument("--start-maximized")
                chrome_options2.add_argument("--disable-blink-features=AutomationControlled")
                chrome_options2.add_experimental_option("excludeSwitches", ["enable-automation"])
                chrome_options2.add_experimental_option("detach", True)
                
                self.driver = webdriver.Chrome(service=service, options=chrome_options2)
                self.driver.execute_cdp_cmd("Page.addScriptToEvaluateOnNewDocument", {
                    "source": "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});"
                })
                logger.info("✅ Chrome opened (fallback mode)")
                return True
            except Exception as e2:
                logger.error(f"Fallback also failed: {e2}")
                return False
    
    def open_for_login(self) -> bool:
        """
        Open browser and navigate to Pocket Option for manual login.
        Returns True when browser is opened successfully.
        """
        with self._lock:
            try:
                if not self.driver:
                    if not self._create_driver():
                        return False
                
                # Navigate to login page
                logger.info("📌 Opening Pocket Option - Please login manually...")
                self.driver.get(self.LOGIN_URL)
                
                # Start background thread to check for login completion
                self._running = True
                self._check_thread = threading.Thread(target=self._check_login_loop, daemon=True)
                self._check_thread.start()
                
                return True
                
            except Exception as e:
                logger.error(f"Failed to open browser: {e}")
                return False
    
    # Comprehensive JavaScript for FULL broker control
    JS_OBSERVER_SCRIPT = """
    if (!window.eliteBotController) {
        // ========== STATE ==========
        window.botState = {
            balance: 0,
            currency: "UNKNOWN", 
            payout: 0,
            logged_in: false,
            lastUpdate: 0,
            ssid: null,
            tradeAmount: 0,
            lastTradeResult: null,
            isDemo: true,
            connectionStatus: "initializing"
        };

        // ========== BALANCE DETECTION (Multiple Strategies) ==========
        function detectBalance() {
            let balance = 0;
            let foundEl = null;

            // Strategy 1: Direct selectors (PRIORITY ORDER - most reliable first)
            const selectors = [
                // BEST: Main demo balance (discovered from actual page)
                ".balance-info-block__balance .js-balance-demo",
                ".balance-info-block__balance .js-hd",
                ".js-balance-demo",
                // Header balance variations
                ".balance-info-block__balance .js-balance-value",
                ".balance-info-block .balance__value",
                ".header-balance .balance__value",
                ".main-balance .value",
                // Trading panel
                ".trading-panel__balance-value",
                ".trading-panel-balance__value",
                ".trading-panel .balance-value",
                // Sidebar
                ".sidebar-balance .value",
                ".right-sidebar .balance-value",
                // Generic
                ".balance-value",
                ".balance__value", 
                "[data-test='balance-value']",
                ".js-balance-value",
                // Demo specific
                ".demo-balance .value",
                ".demo-account-balance"
            ];
            
            for (let sel of selectors) {
                try {
                    let el = document.querySelector(sel);
                    if (el && el.offsetParent !== null && el.innerText) {
                        // Handle numbers with comma thousands separator (e.g., 26,033.84)
                        let text = el.innerText.trim();
                        let cleanNum = text.replace(/,/g, '').replace(/[^0-9.]/g, '');
                        let val = parseFloat(cleanNum);
                        if (val > 0 && val < 100000000) { // Reasonable range
                            balance = val;
                            foundEl = el;
                            break;
                        }
                    }
                } catch(e) {}
            }

            // Strategy 2: Find any element containing $ with reasonable value
            if (balance === 0) {
                const allElements = document.querySelectorAll("span, div, p, a");
                for (let el of allElements) {
                    if (el.childElementCount === 0) {
                        let txt = el.innerText ? el.innerText.trim() : "";
                        // Match patterns like "$10,000.00" or "10000.00 $" or "$50.00"
                        if (txt.match(/^\\$[0-9,]+\\.?[0-9]*$|^[0-9,]+\\.?[0-9]*\\s*\\$$/) && txt.length < 20) {
                            let val = parseFloat(txt.replace(/[^0-9.]/g, ''));
                            // Accept values in demo range (typically 1000-100000)
                            if (val >= 1 && val < 100000000) {
                                balance = val;
                                foundEl = el;
                                break;
                            }
                        }
                    }
                }
            }

            // Strategy 3: Look for balance in page data/scripts
            if (balance === 0) {
                const scripts = document.querySelectorAll('script');
                for (let script of scripts) {
                    if (script.innerText.includes('balance')) {
                        const match = script.innerText.match(/"balance":\\s*([0-9.]+)/);
                        if (match) {
                            balance = parseFloat(match[1]);
                            break;
                        }
                    }
                }
            }

            if (balance > 0) {
                window.botState.balance = balance;
                window.botState.logged_in = true;
                window.botState.connectionStatus = "connected";
            }
            
            // Visual indicator
            if (foundEl) {
                foundEl.style.boxShadow = "0 0 10px 3px #00ff00";
                document.title = "[BOT: $" + balance.toFixed(2) + "] Pocket Option";
            }
            
            return balance;
        }

        // ========== CURRENCY DETECTION ==========
        function detectCurrency() {
            let currency = "UNKNOWN";
            const selectors = [
                ".current-symbol",
                ".asset-select__button-text",
                ".asset-name",
                ".selected-asset-name",
                ".pair-name",
                ".trading-pair",
                ".asset-pair__name"
            ];
            
            for (let sel of selectors) {
                try {
                    let el = document.querySelector(sel);
                    if (el && el.innerText) {
                        let text = el.innerText.trim();
                        if (text.length > 2 && text.length < 30) {
                            currency = text;
                            break;
                        }
                    }
                } catch(e) {}
            }
            
            if (currency !== "UNKNOWN") {
                window.botState.currency = currency;
            }
            return currency;
        }

        // ========== PAYOUT DETECTION ==========
        function detectPayout() {
            let payout = 0;
            const selectors = [".payout-value", ".percent-value", ".payout__value", ".profit-percent"];
            
            for (let sel of selectors) {
                try {
                    let el = document.querySelector(sel);
                    if (el && el.innerText) {
                        payout = parseFloat(el.innerText.replace(/[^0-9]/g, ''));
                        if (payout > 0) break;
                    }
                } catch(e) {}
            }
            
            window.botState.payout = payout;
            return payout;
        }

        // ========== TRADE AMOUNT DETECTION ==========
        function detectTradeAmount() {
            const selectors = [
                ".trading-panel input[type='text']",
                ".trading-panel input[type='number']",
                ".amount-input",
                ".trade-amount input",
                "input.js-amount-input",
                ".trading-panel__amount input"
            ];
            
            for (let sel of selectors) {
                try {
                    let el = document.querySelector(sel);
                    if (el && el.value) {
                        let val = parseFloat(el.value.replace(/[^0-9.]/g, ''));
                        if (val > 0) {
                            window.botState.tradeAmount = val;
                            return val;
                        }
                    }
                } catch(e) {}
            }
            return 0;
        }

        // ========== SSID EXTRACTION (For API access) ==========
        function extractSSID() {
            // Try to get from cookies
            const cookies = document.cookie.split(';');
            for (let cookie of cookies) {
                const [name, value] = cookie.trim().split('=');
                if (name === 'ci_session' || name === 'ssid' || name === 'session') {
                    window.botState.ssid = value;
                    return value;
                }
            }
            
            // Try localStorage
            try {
                const session = localStorage.getItem('session') || localStorage.getItem('ssid');
                if (session) {
                    window.botState.ssid = session;
                    return session;
                }
            } catch(e) {}
            
            return null;
        }

        // ========== TRADE EXECUTION ==========
        window.executeTrade = function(direction) {
            const isCall = direction.toUpperCase() === 'CALL' || direction.toUpperCase() === 'BUY';
            const selectors = isCall ? 
                [".btn-call", ".call-btn", ".buy-btn", "[data-action='call']", ".trading-panel__btn--call", "button.call"] :
                [".btn-put", ".put-btn", ".sell-btn", "[data-action='put']", ".trading-panel__btn--put", "button.put"];
            
            for (let sel of selectors) {
                try {
                    let btn = document.querySelector(sel);
                    if (btn && btn.offsetParent !== null) {
                        btn.click();
                        console.log("🚀 Trade executed: " + direction);
                        return { success: true, direction: direction, time: Date.now() };
                    }
                } catch(e) {}
            }
            
            return { success: false, error: "Trade button not found" };
        };

        // ========== SET TRADE AMOUNT ==========
        window.setTradeAmount = function(amount) {
            const selectors = [
                ".trading-panel input[type='text']",
                ".trading-panel input[type='number']",
                ".amount-input",
                "input.js-amount-input",
                ".trade-amount input"
            ];
            
            for (let sel of selectors) {
                try {
                    let input = document.querySelector(sel);
                    if (input) {
                        input.value = amount;
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.dispatchEvent(new Event('change', { bubbles: true }));
                        console.log("💰 Amount set to: $" + amount);
                        window.botState.tradeAmount = amount;
                        return { success: true, amount: amount };
                    }
                } catch(e) {}
            }
            
            return { success: false, error: "Amount input not found" };
        };

        // ========== CHANGE CURRENCY ==========
        window.changeCurrency = function(symbol) {
            // Step 1: Click asset selector to open dropdown
            const assetSelectors = [
                ".current-symbol",
                ".asset-select-button", 
                ".asset-select__button",
                ".pair-selector"
            ];
            
            let assetBtn = null;
            for (let sel of assetSelectors) {
                assetBtn = document.querySelector(sel);
                if (assetBtn) break;
            }
            
            if (!assetBtn) return { success: false, error: "Asset selector not found" };
            
            assetBtn.click();
            
            // Step 2: Wait then search and select
            setTimeout(function() {
                // Find search input
                const searchInputs = document.querySelectorAll("input[type='search'], input[type='text'], .search-input");
                for (let input of searchInputs) {
                    if (input.offsetParent !== null) {
                        input.value = symbol;
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        break;
                    }
                }
                
                // Step 3: Click matching result
                setTimeout(function() {
                    const items = document.querySelectorAll(".asset-item, .currency-item, .pair-item, .items-list__item");
                    for (let item of items) {
                        if (item.innerText.includes(symbol)) {
                            item.click();
                            console.log("🔄 Currency changed to: " + symbol);
                            return;
                        }
                    }
                }, 500);
            }, 300);
            
            return { success: true, message: "Currency change initiated: " + symbol };
        };

        // ========== SET TRADE DURATION ==========
        window.setTradeDuration = function(minutes) {
            const selectors = [
                ".trading-panel__time-select",
                ".expiration-select",
                ".time-select",
                "select.js-time-select"
            ];
            
            for (let sel of selectors) {
                try {
                    let select = document.querySelector(sel);
                    if (select) {
                        // Look for matching option
                        const options = select.querySelectorAll('option');
                        for (let opt of options) {
                            if (opt.text.includes(minutes + ' min') || opt.value == minutes * 60) {
                                select.value = opt.value;
                                select.dispatchEvent(new Event('change', { bubbles: true }));
                                console.log("⏱️ Duration set to: " + minutes + " min");
                                return { success: true, minutes: minutes };
                            }
                        }
                    }
                } catch(e) {}
            }
            
            return { success: false, error: "Duration selector not found" };
        };

        // ========== MAIN UPDATE LOOP ==========
        function updateAllState() {
            try {
                detectBalance();
                detectCurrency();
                detectPayout();
                detectTradeAmount();
                extractSSID();
                
                window.botState.isDemo = window.location.href.includes('demo');
                window.botState.lastUpdate = Date.now();
                
            } catch(e) {
                console.error("Bot state update error:", e);
            }
        }

        // Run update loop every 500ms
        window.eliteBotController = setInterval(updateAllState, 500);
        updateAllState(); // Initial run
        
        console.log("🚀 Elite Bot Controller v2.0 - FULL CONTROL ACTIVE");
        console.log("Available commands: executeTrade('CALL'/'PUT'), setTradeAmount(10), changeCurrency('EUR/USD'), setTradeDuration(1)");
    }
    return window.botState;
    """

    def _sync_state(self):
        """Execute JS to get latest state from browser."""
        try:
            # Inject/Run JS
            state = self.driver.execute_script(self.JS_OBSERVER_SCRIPT)
            
            if state:
                if state.get('balance', 0) > 0:
                    self.balance = float(state['balance'])
                    self.is_ready = True # Auto-ready if we see balance
                
                self.current_currency = state.get('currency', "UNKNOWN")
                self.current_payout = state.get('payout', 0)
                
                # logger.info(f"🔄 Sync: ${self.balance} | {self.current_currency} | {self.current_payout}%")
                
        except Exception as e:
            # logger.debug(f"Sync failed: {e}")
            pass

    def _check_login_loop(self):
        """Background loop to detect when user has logged in."""
        logger.info("👀 Waiting for you to login...")
        
        while self._running:
            try:
                self._sync_state()
                
                if self.balance > 0:
                    if not self.is_ready:
                        logger.info(f"🎉 LOGIN DETECTED! Balance: ${self.balance}")
                        self.is_ready = True
                        # Navigate to demo trading page only if not there
                        curr = self.driver.current_url
                        if "demo" in self.DEMO_URL and "demo" not in curr:
                             self.driver.get(self.DEMO_URL)
                    
                time.sleep(1) # Fast poll
                
            except Exception as e:
                time.sleep(1)
    
    def set_currency(self, symbol: str) -> bool:
        """
        Change the active currency on the broker using JavaScript.
        """
        if not self.is_ready or not self.driver:
            return False
        
        with self._lock:
            try:
                result = self.driver.execute_script(f"return window.changeCurrency('{symbol}')")
                if result and result.get('success'):
                    logger.info(f"✅ Changed currency to {symbol}")
                    self.current_currency = symbol
                    return True
                return False
            except Exception as e:
                logger.error(f"Failed to set currency: {e}")
                return False

    def execute_trade(self, direction: str, amount: float = None) -> Optional[Dict]:
        """
        Execute a trade on the broker.
        
        Args:
            direction: 'CALL'/'BUY' or 'PUT'/'SELL'
            amount: Optional trade amount (uses current if not specified)
        
        Returns:
            Trade info dict if successful
        """
        if not self.is_ready or not self.driver:
            logger.error("Not ready - cannot execute trade")
            return None
        
        with self._lock:
            try:
                # Set amount first if specified
                if amount:
                    self.set_trade_amount(amount)
                    time.sleep(0.3)
                
                # Execute trade via JavaScript
                result = self.driver.execute_script(f"return window.executeTrade('{direction}')")
                
                if result and result.get('success'):
                    trade_info = {
                        "trade_id": f"po_{int(time.time() * 1000)}",
                        "direction": direction.upper(),
                        "amount": amount or self.balance * 0.01,  # Estimate if not specified
                        "currency": self.current_currency,
                        "time": datetime.now().isoformat(),
                        "status": "executed"
                    }
                    self.trade_history.append(trade_info)
                    logger.info(f"✅ Trade executed: {direction.upper()}")
                    return trade_info
                else:
                    error = result.get('error', 'Unknown error') if result else 'No response'
                    logger.error(f"Trade failed: {error}")
                    return None
                    
            except Exception as e:
                logger.error(f"Trade execution error: {e}")
                return None

    def set_trade_amount(self, amount: float) -> bool:
        """Set the trade amount on the broker."""
        if not self.is_ready or not self.driver:
            return False
        
        try:
            result = self.driver.execute_script(f"return window.setTradeAmount({amount})")
            if result and result.get('success'):
                logger.info(f"✅ Amount set to ${amount}")
                return True
            return False
        except Exception as e:
            logger.error(f"Failed to set amount: {e}")
            return False

    def set_trade_duration(self, minutes: int) -> bool:
        """Set the trade duration in minutes."""
        if not self.is_ready or not self.driver:
            return False
        
        try:
            result = self.driver.execute_script(f"return window.setTradeDuration({minutes})")
            if result and result.get('success'):
                logger.info(f"✅ Duration set to {minutes} min")
                return True
            return False
        except Exception as e:
            logger.error(f"Failed to set duration: {e}")
            return False

    def get_balance(self) -> float:
        """Get current account balance."""
        self._sync_state()
        return self.balance

    def get_ssid(self) -> Optional[str]:
        """
        Get the SSID from browser for WebSocket API access.
        
        Pocket Option uses a special auth format for WebSocket:
        42["auth",{"session":"...","isDemo":1,"uid":...,"platform":1}]
        
        We need to capture this from cookies, storage, or network.
        """
        if not self.driver:
            logger.warning("No browser driver - cannot get SSID")
            return None
        
        try:
            # Get all cookies for debugging
            cookies = self.driver.get_cookies()
            logger.info(f"📝 Found {len(cookies)} cookies in browser")
            
            # Log cookie names for debugging
            cookie_names = [c.get('name', 'unknown') for c in cookies]
            logger.info(f"Cookie names: {cookie_names[:10]}...")  # First 10
            
            # Method 1: Check for known Pocket Option auth cookies
            for cookie in cookies:
                name = cookie.get('name', '').lower()
                # Pocket Option might use various cookie names
                if any(x in name for x in ['ssid', 'session', 'auth', 'token', 'ci_session', 'po_']):
                    ssid = cookie.get('value')
                    if ssid and len(ssid) > 20:
                        logger.info(f"✅ SSID candidate from cookie '{cookie.get('name')}': {ssid[:30]}...")
                        return ssid
            
            # Method 2: Check localStorage for the full auth message
            ssid = self.driver.execute_script("""
                // Check localStorage for auth data
                for (let i = 0; i < localStorage.length; i++) {
                    let key = localStorage.key(i);
                    let value = localStorage.getItem(key);
                    
                    // Look for Pocket Option auth patterns
                    if (value && (
                        value.includes('"session"') || 
                        value.includes('auth') ||
                        key.toLowerCase().includes('ssid') ||
                        key.toLowerCase().includes('session')
                    )) {
                        console.log('Found:', key, value.substring(0, 100));
                        return value;
                    }
                }
                
                // Check sessionStorage
                for (let i = 0; i < sessionStorage.length; i++) {
                    let key = sessionStorage.key(i);
                    let value = sessionStorage.getItem(key);
                    if (value && value.includes('"session"')) {
                        return value;
                    }
                }
                
                return null;
            """)
            if ssid:
                logger.info(f"✅ SSID from storage: {ssid[:50]}...")
                return ssid
            
            # Method 3: Try to intercept WebSocket messages
            # Pocket Option establishes WebSocket and sends auth immediately
            ssid = self.driver.execute_script("""
                // Check if there's a WebSocket connection we can inspect
                if (window.__wsAuth) return window.__wsAuth;
                
                // Try to find user/session data in global scope
                if (window.TRADING_APP && window.TRADING_APP.user) {
                    return JSON.stringify(window.TRADING_APP.user);
                }
                
                // Look for uid (user ID) which is part of SSID
                if (window.userId || window.USER_ID || window.uid) {
                    return window.userId || window.USER_ID || window.uid;
                }
                
                return null;
            """)
            if ssid:
                logger.info(f"✅ Session data from globals: {ssid[:50]}...")
                return ssid
            
            # Method 4: Look for any long cookie value that might be a session
            for cookie in cookies:
                value = cookie.get('value', '')
                if len(value) > 30:  # Session tokens are usually long
                    logger.info(f"📝 Long cookie '{cookie.get('name')}': {len(value)} chars")
                    # Return the first long cookie as potential SSID
                    if len(value) > 50:
                        return value
            
            # Method 5: Build SSID from available data (demo mode)
            # This is a fallback - construct a minimal auth message
            user_data = self.driver.execute_script("""
                try {
                    // Try to get user ID from page
                    let uid = null;
                    
                    // Look for user ID in various places
                    if (window._userId) uid = window._userId;
                    if (document.body.innerHTML.match(/"uid":(\d+)/)) {
                        uid = document.body.innerHTML.match(/"uid":(\d+)/)[1];
                    }
                    
                    return {uid: uid, isDemo: 1};
                } catch(e) { return null; }
            """)
            if user_data and user_data.get('uid'):
                # Construct minimal SSID
                import json
                ssid = json.dumps({"session": "auto", "isDemo": 1, "uid": user_data['uid'], "platform": 1})
                logger.info(f"⚠️ Constructed SSID from user data: {ssid}")
                return ssid
            
            logger.warning("❌ Could not find SSID in browser - none of the methods worked")
            logger.warning("Cookies available: " + str([c.get('name') for c in cookies]))
            return None
            
        except Exception as e:
            logger.error(f"SSID extraction error: {e}")
            import traceback
            traceback.print_exc()
            return None

    def get_status(self) -> Dict:
        """Get current status for API - verifies browser is actually accessible."""
        browser_open = False
        
        # Actually verify browser is accessible, not just driver exists
        if self.driver:
            try:
                # Try to get window handles - this will fail if browser was closed
                handles = self.driver.window_handles
                browser_open = len(handles) > 0
            except:
                # Browser was closed - clean up
                logger.info("Browser window was closed - resetting state")
                self.driver = None
                self.is_ready = False
                self.balance = 0.0
                browser_open = False
        
        return {
            "browser_open": browser_open,
            "logged_in": browser_open and (self.is_ready or (self.balance > 0)),
            "demo_mode": self.demo_mode,
            "balance": self.balance if browser_open else 0.0,
            "currency": getattr(self, 'current_currency', "UNKNOWN") if browser_open else "UNKNOWN",
            "payout": getattr(self, 'current_payout', 0) if browser_open else 0,
            "trades_executed": len(self.trade_history),
            "message": "Connected & Synced" if (browser_open and self.balance > 0) else "Waiting for login..."
        }
    
    def close(self):
        """Close browser."""
        self._running = False
        if self.driver:
            try:
                self.driver.quit()
                logger.info("Browser closed")
            except:
                pass
            finally:
                self.driver = None
                self.is_ready = False


# Global instance
browser_controller = PocketOptionBrowser(demo_mode=True)


# API Functions
def open_browser_for_login() -> Dict:
    """Open browser for manual login. Closes any existing session first."""
    global browser_controller
    
    # Close any existing browser session first
    if browser_controller:
        try:
            browser_controller.close()
            logger.info("Closed existing browser session")
        except:
            pass
    
    # Create fresh browser controller
    browser_controller = PocketOptionBrowser(demo_mode=True)
    success = browser_controller.open_for_login()
    return {
        "success": success,
        "message": "Browser opened! Please login to Pocket Option manually." if success else "Failed to open browser"
    }


def check_login_status() -> Dict:
    """Check if user has logged in."""
    return browser_controller.get_status()


def execute_browser_trade(direction: str, amount: float = None) -> Optional[Dict]:
    """Execute trade via browser."""
    return browser_controller.execute_trade(direction, amount)


def get_browser_balance() -> float:
    """Get current balance."""
    return browser_controller.get_balance()

def set_browser_currency(symbol: str) -> bool:
    """Set active currency."""
    return browser_controller.set_currency(symbol)

def set_browser_amount(amount: float) -> bool:
    """Set trade amount."""
    return browser_controller.set_trade_amount(amount)

def set_browser_duration(minutes: int) -> bool:
    """Set trade duration in minutes."""
    return browser_controller.set_trade_duration(minutes)



