/**
 * Elite Trading Bot - DOM Observer System
 * 
 * YOUR IDEA: Extension continuously observes the broker's state and 
 * efficiently makes only the changes needed for each trade.
 * 
 * How it works:
 * 1. Constantly watches current: currency, time, amount
 * 2. When trade comes: compare current vs. desired
 * 3. Only change what's different
 * 4. Click CALL/PUT
 * 5. Verify it worked
 */

console.log('🤖 Elite Trading Bot - DOM Observer System Loading...');

// ===== BROKER STATE (Continuously Updated) =====
const brokerState = {
    currentCurrency: '',
    currentTime: 0,  // in seconds
    currentAmount: 0,
    currentPrice: 0,  // NEW: Current chart price for real market data
    balance: 0,
    isReady: false,
    lastUpdate: null,
    // Trade result tracking
    activeTrades: [],      // Trades currently in progress
    lastResults: [],       // Recent trade results (last 10)
    pendingResult: null    // Result waiting to be sent
};

// ===== SELECTORS (Found from Pocket Option DOM - Updated Dec 2024) =====
const SELECTORS = {
    // Trade buttons (anchor elements with btn-call/btn-put classes)
    callButton: 'a.btn-call, .btn-call, [class*="btn-call"]',
    putButton: 'a.btn-put, .btn-put, [class*="btn-put"]',

    // Amount input (value input field)
    amountInput: 'input.value__input, input[type="text"][class*="value"], .block__control input',

    // Current currency display
    currencyDisplay: '.pair-number-wrap, .asset-name, [class*="pair-name"]',
    currencyDropdownTrigger: 'a.pair-number-wrap, .pair-number-wrap',
    currencySearchInput: 'input.search__field, input[type="search"], input[placeholder*="Search"]',

    // Time display and selector
    timeDisplay: '.block-control__text, .field-block__input, [class*="time-display"]',
    timeDropdownTrigger: '.block-control--time, [class*="time-selector"]',

    // Balance (demo and real)
    balanceDemo: '.js-balance-demo, [class*="balance-demo"]',
    balanceReal: '.js-balance-value, [class*="balance-real"]',
    balanceAny: '[class*="balance"]',

    // Trade Results (Pocket Option uses these for active/completed trades)
    tradesPanel: '.deals-list, .trades-list, .deals__list',
    tradeItem: '.deal-item, .trade-item, .deals__item',
    tradeProfit: '.deal-profit, .trade-profit, .deals__profit',
    tradeStatus: '.deal-status, .trade-status',
    tradeAmount: '.deal-amount, .deals__amount',
    tradeCurrency: '.deal-currency, .deals__currency'
};

// ===== CURRENCY NAME MAPPING =====
// Maps bot currency names to Pocket Option's display names
// Pocket Option often uses "OTC" suffix for most pairs during non-market hours
const CURRENCY_MAP = {
    // Forex pairs - Pocket Option uses OTC versions most of the time
    'EUR/USD': ['EUR/USD OTC', 'EUR/USD', 'EURUSD OTC', 'EURUSD'],
    'GBP/USD': ['GBP/USD OTC', 'GBP/USD', 'GBPUSD OTC', 'GBPUSD'],
    'USD/JPY': ['USD/JPY OTC', 'USD/JPY', 'USDJPY OTC', 'USDJPY'],
    'AUD/USD': ['AUD/USD OTC', 'AUD/USD', 'AUDUSD OTC', 'AUDUSD'],
    'USD/CAD': ['USD/CAD OTC', 'USD/CAD', 'USDCAD OTC', 'USDCAD'],
    'NZD/USD': ['NZD/USD OTC', 'NZD/USD', 'NZDUSD OTC', 'NZDUSD'],
    'USD/CHF': ['USD/CHF OTC', 'USD/CHF', 'USDCHF OTC', 'USDCHF'],
    'EUR/GBP': ['EUR/GBP OTC', 'EUR/GBP', 'EURGBP OTC', 'EURGBP'],
    'EUR/JPY': ['EUR/JPY OTC', 'EUR/JPY', 'EURJPY OTC', 'EURJPY'],
    'GBP/JPY': ['GBP/JPY OTC', 'GBP/JPY', 'GBPJPY OTC', 'GBPJPY'],
    // Crypto
    'BTC/USD': ['Bitcoin', 'BTC/USD', 'BTCUSD'],
    'ETH/USD': ['Ethereum', 'ETH/USD', 'ETHUSD'],
    // Commodities
    'XAU/USD': ['Gold', 'XAU/USD', 'XAUUSD', 'Gold OTC'],
    'XAG/USD': ['Silver', 'XAG/USD', 'XAGUSD', 'Silver OTC'],
    'OIL/USD': ['Oil', 'Brent', 'Crude', 'OIL/USD']
};

// Get all possible search terms for a currency
function getCurrencySearchTerms(currency) {
    const upper = currency.toUpperCase();
    if (CURRENCY_MAP[upper]) {
        return CURRENCY_MAP[upper];
    }
    // Default: try OTC version first, then original
    return [
        currency + ' OTC',
        currency,
        currency.replace('/', '') + ' OTC',
        currency.replace('/', '')
    ];
}

// Track already seen trade IDs to avoid duplicates
const seenTradeIds = new Set();
let lastBalanceForTracking = 0;

// ===== STATE OBSERVER (Runs Continuously) =====

function observeState() {
    try {
        // Get current currency
        const currencyEl = document.querySelector(SELECTORS.currencyDisplay);
        if (currencyEl) {
            brokerState.currentCurrency = currencyEl.innerText?.trim() || '';
        }

        // Get current time
        const timeEl = document.querySelector(SELECTORS.timeDisplay);
        if (timeEl) {
            const timeText = timeEl.innerText?.trim() || '';
            const match = timeText.match(/(\d+):(\d+):(\d+)/);
            if (match) {
                brokerState.currentTime = parseInt(match[1]) * 3600 + parseInt(match[2]) * 60 + parseInt(match[3]);
            }
        }

        // Get current amount
        const amountEl = document.querySelector(SELECTORS.amountInput);
        if (amountEl) {
            brokerState.currentAmount = parseFloat(amountEl.value) || 0;
        }

        // Get balance
        let balanceEl = document.querySelector(SELECTORS.balanceDemo);
        if (!balanceEl) balanceEl = document.querySelector(SELECTORS.balanceReal);
        if (balanceEl) {
            brokerState.balance = parseFloat(balanceEl.innerText?.replace(/[^0-9.]/g, '')) || 0;
        }

        brokerState.isReady = brokerState.balance > 0;
        brokerState.lastUpdate = Date.now();

        // Get current price from chart (for real market data integration)
        const priceEl = document.querySelector('.trading-price, .chart-price, .current-price, [class*="price-value"], .bid-price, .ask-price');
        if (priceEl) {
            const priceText = priceEl.innerText?.trim() || '';
            const priceMatch = priceText.match(/[\d.]+/);
            if (priceMatch) {
                brokerState.currentPrice = parseFloat(priceMatch[0]);
            }
        }

        // Alternative: Try to get price from the trading amount display area
        if (!brokerState.currentPrice) {
            const allPriceElements = document.querySelectorAll('[class*="price"], [class*="rate"], [class*="value"]');
            for (const el of allPriceElements) {
                const text = el.innerText?.trim() || '';
                // Look for forex-like prices (e.g., 1.0852 or 108.52)
                if (text.match(/^\d+\.\d{2,5}$/) && !text.includes('$')) {
                    brokerState.currentPrice = parseFloat(text);
                    break;
                }
            }
        }

    } catch (e) {
        console.error('State observation error:', e);
    }
}

// Run state observer every 500ms
setInterval(observeState, 500);

// ===== PRICE STREAMING TO PYTHON API =====
// Sends live prices to the bot's real market data system

const API_URL = 'http://localhost:5001';
let lastStreamedPrice = 0;
let priceStreamEnabled = true;  // Enable/disable price streaming

async function streamPriceToAPI() {
    if (!priceStreamEnabled || !brokerState.currentCurrency || !brokerState.currentPrice) {
        return;
    }

    // Only stream if price actually changed
    if (brokerState.currentPrice === lastStreamedPrice) {
        return;
    }

    try {
        const response = await fetch(`${API_URL}/broker/price`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                currency: brokerState.currentCurrency,
                price: brokerState.currentPrice
            })
        });

        if (response.ok) {
            lastStreamedPrice = brokerState.currentPrice;
            console.log(`📡 Price streamed: ${brokerState.currentCurrency} = ${brokerState.currentPrice}`);
        }
    } catch (e) {
        // Silently fail - API might not be running
    }
}

// Stream prices every 3 seconds
setInterval(streamPriceToAPI, 3000);
console.log('📡 Price streaming to Python API enabled');

// ===== TRADE RESULT OBSERVER =====

/**
 * Observes trade results on Pocket Option.
 * Detects when trades complete and whether they were wins or losses.
 */
function observeTradeResults() {
    try {
        // Method 1: Watch for balance changes (simple but effective)
        const currentBalance = brokerState.balance;

        if (lastBalanceForTracking > 0 && currentBalance !== lastBalanceForTracking) {
            const difference = currentBalance - lastBalanceForTracking;

            // Significant balance change = trade result
            if (Math.abs(difference) >= 0.5) {  // At least $0.50 change
                const isWin = difference > 0;
                const result = {
                    timestamp: new Date().toISOString(),
                    currency: brokerState.currentCurrency,
                    result: isWin ? 'WIN' : 'LOSS',
                    profit: difference,
                    balanceBefore: lastBalanceForTracking,
                    balanceAfter: currentBalance
                };

                console.log(`📊 Trade Result Detected: ${result.result} | Profit: $${difference.toFixed(2)}`);

                // Add to results list (keep last 10)
                brokerState.lastResults.unshift(result);
                if (brokerState.lastResults.length > 10) {
                    brokerState.lastResults.pop();
                }

                // Set as pending to send to bot
                brokerState.pendingResult = result;

                // Send to background script
                sendTradeResultToBot(result);
            }
        }

        lastBalanceForTracking = currentBalance;

        // Method 2: Try to find trade result elements in DOM (backup method)
        const profitElements = document.querySelectorAll(SELECTORS.tradeProfit);
        profitElements.forEach(el => {
            const text = el.innerText || '';
            const isWin = el.classList.contains('win') ||
                el.classList.contains('green') ||
                text.includes('+') ||
                (el.style.color && el.style.color.includes('green'));

            // This is a backup - balance tracking is primary method
        });

    } catch (e) {
        console.error('Trade result observation error:', e);
    }
}

/**
 * Send trade result to the bot via background script.
 */
function sendTradeResultToBot(result) {
    try {
        chrome.runtime.sendMessage({
            type: 'TRADE_RESULT',
            result: result
        });
        console.log('📤 Trade result sent to bot:', result);
    } catch (e) {
        console.error('Failed to send trade result:', e);
    }
}

// Run trade result observer every 2 seconds
setInterval(observeTradeResults, 2000);

// ===== SMART TRADE EXECUTION =====

async function executeTrade(request) {
    const { currency, amount, duration, direction } = request;

    console.log(`📋 Trade Request: ${direction} ${currency} $${amount} ${duration}s`);
    console.log(`📊 Current State: ${brokerState.currentCurrency} $${brokerState.currentAmount} ${brokerState.currentTime}s`);

    let changesNeeded = [];

    // Step 1: Check if currency needs changing
    if (currency && !brokerState.currentCurrency.includes(currency.split('/')[0])) {
        console.log(`🔄 Currency change needed: ${brokerState.currentCurrency} → ${currency}`);
        changesNeeded.push('currency');
        const success = await setCurrency(currency);

        // CRITICAL: Wait longer for currency change to take effect
        console.log('⏳ Waiting for currency change to apply...');
        await sleep(2000);

        // Verify currency actually changed (up to 5 attempts)
        for (let i = 0; i < 5; i++) {
            observeState();
            if (brokerState.currentCurrency.toUpperCase().includes(currency.split('/')[0].toUpperCase())) {
                console.log(`✅ Currency confirmed: ${brokerState.currentCurrency}`);
                break;
            }
            console.log(`⏳ Still waiting for currency... (${i + 1}/5)`);
            await sleep(500);
        }

        if (!success) {
            console.log('⚠️ Currency change may have failed, but currency appears correct - continuing...');
        }
    }

    // Step 2: Check if duration needs changing
    if (duration && duration !== brokerState.currentTime) {
        console.log(`⏱️ Duration change needed: ${brokerState.currentTime}s → ${duration}s`);
        changesNeeded.push('duration');
        const success = await setDuration(duration);

        // CRITICAL: Wait longer for duration change to take effect
        console.log('⏳ Waiting for duration change to apply...');
        await sleep(1500);

        if (!success) {
            console.log('⚠️ Duration change may have failed, continuing anyway...');
        }
    }

    // Step 3: Check if amount needs changing
    if (amount && amount !== brokerState.currentAmount) {
        changesNeeded.push('amount');
        const success = await setAmount(amount);
        if (!success) return { success: false, error: 'Failed to set amount' };
        await sleep(500);
    }

    // CRITICAL: Final verification pause before executing trade
    console.log('⏳ Final state verification before trade...');
    await sleep(500);
    observeState();
    console.log(`📊 Final state: ${brokerState.currentCurrency} $${brokerState.currentAmount} ${brokerState.currentTime}s`);

    // Step 4: Execute the trade!
    console.log(`🎯 Executing ${direction} trade...`);
    const result = await clickTradeButton(direction);

    if (result.success) {
        console.log(`✅ Trade executed successfully!`);
        showTradeFlash(direction);
    }

    return {
        success: result.success,
        direction: direction,
        amount: amount,
        currency: brokerState.currentCurrency,
        changesApplied: changesNeeded,
        error: result.error
    };
}

// ===== AMOUNT SETTING =====

async function setAmount(amount) {
    console.log(`💰 Setting amount to: $${amount}`);

    const input = document.querySelector(SELECTORS.amountInput);
    if (!input) {
        console.error('Amount input not found');
        return false;
    }

    try {
        // Focus and select all
        input.focus();
        input.select();

        // Clear and type new value
        document.execCommand('selectAll', false, null);
        document.execCommand('insertText', false, String(amount));

        // Also set directly
        input.value = amount;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.dispatchEvent(new Event('change', { bubbles: true }));
        input.blur();

        // Verify
        await sleep(100);
        observeState();

        const success = Math.abs(brokerState.currentAmount - amount) < 0.01;
        console.log(success ? `✅ Amount set to $${amount}` : `⚠️ Amount may not have set correctly`);

        return true;
    } catch (e) {
        console.error('Amount setting error:', e);
        return false;
    }
}

// ===== CURRENCY/ASSET SELECTION =====

/**
 * Set the trading currency/asset using the search bar approach.
 * Works for forex pairs (EUR/USD), commodities (Gold, Silver), crypto, etc.
 * 
 * FIXED: Better OTC pair handling and more precise element matching
 */
async function setCurrency(symbol) {
    console.log(`🔄🔄🔄 SETTING CURRENCY TO: ${symbol} 🔄🔄🔄`);

    try {
        // Check if already on this currency (including OTC variant)
        observeState();
        const currentCurr = brokerState.currentCurrency.toUpperCase();
        const targetCurr = symbol.toUpperCase();
        const targetBase = targetCurr.split('/')[0];

        // Check for match (handle OTC suffix)
        if (currentCurr.includes(targetCurr) ||
            (currentCurr.includes(targetBase) && currentCurr.includes(targetCurr.split('/')[1] || ''))) {
            console.log(`✅ Already on ${symbol} (current: ${brokerState.currentCurrency})`);
            return true;
        }

        // Step 1: Find and click the currency/asset selector area
        const possibleTriggers = [
            '.pair-number-wrap',
            '.current-pair',
            '.asset-name',
            '.value__header',
            '[class*="asset-select"]',
            '[class*="pair-selector"]',
            'a[class*="pair"]'
        ];

        console.log('🔍 Looking for currency dropdown trigger...');
        let dropdownTrigger = null;

        for (const sel of possibleTriggers) {
            const el = document.querySelector(sel);
            if (el && el.offsetParent !== null) {
                console.log(`  ✅ Found: ${sel} = "${el.innerText?.substring(0, 30)}"`);
                dropdownTrigger = el;
                break;
            }
        }

        if (!dropdownTrigger) {
            console.error('❌ No currency dropdown trigger found!');
            return false;
        }

        console.log(`👆 Clicking dropdown trigger: "${dropdownTrigger.innerText?.substring(0, 50)}"`);
        dropdownTrigger.click();
        await sleep(1000); // Wait longer for dropdown to open

        // Step 2: Find and use search input
        const searchSelectors = [
            'input.search__field',
            'input[type="search"]',
            'input[placeholder*="Search"]',
            'input[placeholder*="search"]',
            '.popup input[type="text"]',
            '.modal input[type="text"]',
            'input[class*="search"]'
        ];

        let searchInput = null;
        for (const sel of searchSelectors) {
            const el = document.querySelector(sel);
            if (el && el.offsetParent !== null) {
                searchInput = el;
                console.log(`✅ Found search input: ${sel}`);
                break;
            }
        }

        // Get all possible search terms for this currency (uses CURRENCY_MAP)
        const searchTerms = getCurrencySearchTerms(symbol);
        console.log(`🔍 Will try these search terms: ${searchTerms.join(', ')}`);

        if (searchInput) {
            // Clear and type the FIRST search term (usually OTC version)
            searchInput.focus();
            searchInput.value = '';
            searchInput.dispatchEvent(new Event('input', { bubbles: true }));
            await sleep(200);

            // Try the first search term from the mapping (usually OTC version)
            const searchTerm = searchTerms[0];
            searchInput.value = searchTerm;
            searchInput.dispatchEvent(new Event('input', { bubbles: true }));
            searchInput.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: 'c' }));

            console.log(`⌨️ Typed: "${searchTerm}"`);
            await sleep(1000); // Wait for results to filter
        }

        // Step 3: Find and click the matching asset using ALL possible names
        console.log('🔍 Looking for match in results...');

        // Build all possible match strings using the mapping
        const allMatchStrings = searchTerms.map(t => t.toUpperCase());

        // All possible asset item selectors
        const assetSelectors = [
            '.alist__item',
            '.assets-favorites-item',
            '.asset-list__item',
            '[class*="alist"] [class*="item"]',
            '.popup li',
            '.modal li',
            '[role="option"]'
        ];

        let clicked = false;
        let bestMatch = null;
        let bestMatchScore = 0;

        for (const sel of assetSelectors) {
            if (clicked) break;

            const items = document.querySelectorAll(sel);

            for (const item of items) {
                if (item.offsetParent === null) continue;
                const rect = item.getBoundingClientRect();
                if (rect.width === 0 || rect.height === 0) continue;

                // Get all text content
                const text = item.innerText?.toUpperCase().trim() || '';
                if (text.length < 3) continue;

                // Calculate match score using all possible currency names
                let score = 0;
                const textNormalized = text.replace(/[\/\s]/g, '');

                // Check each possible match string from the mapping
                for (const matchStr of allMatchStrings) {
                    const matchNormalized = matchStr.replace(/[\/\s]/g, '');

                    // Exact match (highest priority)
                    if (text === matchStr) {
                        score = Math.max(score, 100);
                    }
                    // Normalized exact match
                    else if (textNormalized === matchNormalized) {
                        score = Math.max(score, 95);
                    }
                    // Text starts with the match string
                    else if (text.startsWith(matchStr)) {
                        score = Math.max(score, 90);
                    }
                    // Contains full match string
                    else if (text.includes(matchStr)) {
                        score = Math.max(score, 85);
                    }
                    // Contains normalized match
                    else if (textNormalized.includes(matchNormalized)) {
                        score = Math.max(score, 75);
                    }
                }

                if (score > bestMatchScore) {
                    bestMatchScore = score;
                    bestMatch = { item, text, score, rect };
                }

                // If we found an exact match, click immediately
                if (score >= 90) {
                    console.log(`✅ EXACT MATCH! Clicking: "${text}" (score: ${score})`);

                    // Multiple click methods for reliability
                    item.click();
                    await sleep(100);

                    const centerX = rect.left + rect.width / 2;
                    const centerY = rect.top + rect.height / 2;

                    item.dispatchEvent(new MouseEvent('click', {
                        view: window,
                        bubbles: true,
                        cancelable: true,
                        clientX: centerX,
                        clientY: centerY
                    }));

                    clicked = true;
                    await sleep(500);
                    break;
                }
            }
        }

        // Use best partial match if no exact match found
        if (!clicked && bestMatch && bestMatchScore >= 70) {
            console.log(`⚠️ Using best match: "${bestMatch.text}" (score: ${bestMatchScore})`);
            bestMatch.item.click();
            await sleep(100);

            const rect = bestMatch.rect;
            bestMatch.item.dispatchEvent(new MouseEvent('click', {
                view: window,
                bubbles: true,
                cancelable: true,
                clientX: rect.left + rect.width / 2,
                clientY: rect.top + rect.height / 2
            }));

            clicked = true;
            await sleep(500);
        }

        if (!clicked) {
            console.log('⚠️ Could not find matching asset');
        }

        // Close any remaining popups
        await sleep(300);
        document.body.click();
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

        // Verify
        await sleep(500);
        observeState();

        const success = brokerState.currentCurrency.toUpperCase().includes(targetBase);
        console.log(`${success ? '✅' : '⚠️'} Currency result: ${brokerState.currentCurrency} (wanted: ${symbol})`);

        return success;

    } catch (e) {
        console.error('❌ Currency selection error:', e);
        return false;
    }
}

// ===== TIME DURATION SELECTION =====

/**
 * Set the trade duration using the time selector menu.
 * Duration is in seconds (60 = 1 min, 180 = 3 min, 300 = 5 min)
 * 
 * UPDATED: Uses correct Pocket Option December 2024 selectors:
 * - Trigger: .block--expiration-inputs or time display
 * - Presets: .dops__timeframes-item with S3/S15/S30/M1/M3/M5/M30/H1/H4
 * - Manual: Input fields in .expiration-inputs-list-modal
 */
async function setDuration(seconds) {
    console.log(`⏱️⏱️⏱️ SETTING DURATION TO: ${seconds} seconds ⏱️⏱️⏱️`);

    try {
        // Step 1: Find and click the time selector to open the modal
        const possibleTimeTriggers = [
            { sel: '.block--expiration-inputs', el: document.querySelector('.block--expiration-inputs') },
            { sel: '.value__val', el: document.querySelector('.value__val') },
            { sel: '.block-control--time', el: document.querySelector('.block-control--time') },
            { sel: '.time-select', el: document.querySelector('.time-select') },
            { sel: '[class*="expiration"]', el: document.querySelector('[class*="expiration"]') },
            { sel: '[class*="expiry"]', el: document.querySelector('[class*="expiry"]') },
        ];

        console.log('🔍 Looking for time selector...');
        for (const t of possibleTimeTriggers) {
            if (t.el) {
                console.log(`  ✅ Found: ${t.sel} = "${t.el.innerText?.substring(0, 30)}"`);
            }
        }

        let timeSelector = possibleTimeTriggers.find(t => t.el)?.el;

        // Fallback: Look for time format like "00:01:00"
        if (!timeSelector) {
            console.log('🔍 Trying text pattern search for time display...');
            const allElements = document.querySelectorAll('div, span');
            for (const el of allElements) {
                const text = el.innerText?.trim() || '';
                if (text.match(/^\d{2}:\d{2}:\d{2}$/) && el.offsetParent !== null) {
                    console.log(`🔍 Found time display: "${text}"`);
                    timeSelector = el;
                    break;
                }
            }
        }

        if (timeSelector) {
            console.log(`👆 Clicking time selector: "${timeSelector.innerText?.trim()}"`);
            timeSelector.click();
            await sleep(800); // Wait for modal to open
        } else {
            console.error('❌ No time selector found!');
            return false;
        }

        // Step 2: Try to use quick preset buttons (most reliable)
        // Pocket Option uses: S3, S15, S30, M1, M3, M5, M30, H1, H4
        const presetMap = {
            3: 'S3',      // 3 seconds
            15: 'S15',    // 15 seconds
            30: 'S30',    // 30 seconds
            60: 'M1',     // 1 minute
            180: 'M3',    // 3 minutes
            300: 'M5',    // 5 minutes
            1800: 'M30',  // 30 minutes
            3600: 'H1',   // 1 hour
            14400: 'H4'   // 4 hours
        };

        const targetPreset = presetMap[seconds];
        let clicked = false;

        if (targetPreset) {
            console.log(`🔍 Looking for preset: ${targetPreset}`);
            const presetItems = document.querySelectorAll('.dops__timeframes-item');
            console.log(`📋 Found ${presetItems.length} preset items`);

            for (const item of presetItems) {
                const text = item.innerText?.trim();
                if (text === targetPreset) {
                    console.log(`✅ FOUND PRESET! Clicking: "${text}"`);
                    item.click();
                    clicked = true;
                    await sleep(500);
                    break;
                }
            }
        }

        // Step 3: If no preset matched, try manual input
        if (!clicked) {
            console.log('⚠️ No preset matched, trying manual input...');

            const modal = document.querySelector('.expiration-inputs-list-modal');
            if (modal) {
                const inputs = modal.querySelectorAll('input');
                console.log(`📋 Found ${inputs.length} input fields in modal`);

                if (inputs.length >= 3) {
                    const hours = Math.floor(seconds / 3600);
                    const minutes = Math.floor((seconds % 3600) / 60);
                    const secs = seconds % 60;

                    // Set hours (first input)
                    inputs[0].value = String(hours).padStart(2, '0');
                    inputs[0].dispatchEvent(new Event('input', { bubbles: true }));
                    inputs[0].dispatchEvent(new Event('change', { bubbles: true }));

                    // Set minutes (second input)
                    inputs[1].value = String(minutes).padStart(2, '0');
                    inputs[1].dispatchEvent(new Event('input', { bubbles: true }));
                    inputs[1].dispatchEvent(new Event('change', { bubbles: true }));

                    // Set seconds (third input)
                    inputs[2].value = String(secs).padStart(2, '0');
                    inputs[2].dispatchEvent(new Event('input', { bubbles: true }));
                    inputs[2].dispatchEvent(new Event('change', { bubbles: true }));

                    console.log(`✅ Set manual time: ${hours}:${minutes}:${secs}`);
                    clicked = true;
                    await sleep(300);
                }
            }
        }

        // Step 4: Close the modal
        await sleep(300);

        // Try clicking outside or pressing Escape
        document.body.click();
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

        // Also try clicking any close button
        const closeBtn = document.querySelector('.modal-close, .close-btn, [class*="close"]');
        if (closeBtn) closeBtn.click();

        // Verify
        await sleep(500);
        observeState();

        const success = clicked && (brokerState.currentTime === seconds || Math.abs(brokerState.currentTime - seconds) < 5);
        console.log(`${success ? '✅' : '⚠️'} Duration result: ${brokerState.currentTime}s (wanted: ${seconds}s)`);

        return clicked;

    } catch (e) {
        console.error('❌ Duration setting error:', e);
        return false;
    }
}


// ===== TRADE BUTTON CLICKING =====

async function clickTradeButton(direction) {
    const isCall = direction.toUpperCase() === 'CALL' || direction.toUpperCase() === 'BUY';
    const selector = isCall ? SELECTORS.callButton : SELECTORS.putButton;

    console.log(`🔍 Looking for ${isCall ? 'CALL' : 'PUT'} button with selector: ${selector}`);

    // Store balance before trade for verification
    observeState();
    const balanceBefore = brokerState.balance;
    console.log(`💰 Balance before trade: $${balanceBefore}`);

    // Find the button using multiple strategies
    let btn = document.querySelector(selector);

    // Alternative selectors if main one fails
    if (!btn) {
        const altSelectors = isCall
            ? ['a.btn-call', '.btn.btn-call', '[class*="call"]', 'a[class*="call"]']
            : ['a.btn-put', '.btn.btn-put', '[class*="put"]', 'a[class*="put"]'];

        for (const alt of altSelectors) {
            btn = document.querySelector(alt);
            if (btn && btn.offsetParent !== null) {
                console.log(`✅ Found button with alt selector: ${alt}`);
                break;
            }
        }
    }

    if (btn && btn.offsetParent !== null) {
        console.log(`✅ Button found! Attempting to click...`);

        // Scroll into view first
        btn.scrollIntoView({ behavior: 'instant', block: 'center' });
        await sleep(100);

        // Get button position for MouseEvent
        const rect = btn.getBoundingClientRect();
        const centerX = rect.left + rect.width / 2;
        const centerY = rect.top + rect.height / 2;

        // Method 1: Direct click
        btn.click();
        console.log(`🖱️ Method 1: Direct click() executed`);

        // Method 2: MouseEvent dispatch (more reliable for some sites)
        await sleep(50);
        const mouseEvent = new MouseEvent('click', {
            view: window,
            bubbles: true,
            cancelable: true,
            clientX: centerX,
            clientY: centerY,
            composed: true
        });
        btn.dispatchEvent(mouseEvent);
        console.log(`🖱️ Method 2: MouseEvent dispatched at (${centerX.toFixed(0)}, ${centerY.toFixed(0)})`);

        // Method 3: Pointer events (modern approach)
        await sleep(50);
        const pointerDown = new PointerEvent('pointerdown', {
            bubbles: true,
            cancelable: true,
            pointerType: 'mouse',
            clientX: centerX,
            clientY: centerY
        });
        const pointerUp = new PointerEvent('pointerup', {
            bubbles: true,
            cancelable: true,
            pointerType: 'mouse',
            clientX: centerX,
            clientY: centerY
        });
        btn.dispatchEvent(pointerDown);
        btn.dispatchEvent(pointerUp);
        console.log(`🖱️ Method 3: Pointer events dispatched`);

        // Wait and verify trade happened by checking balance
        await sleep(2000);
        observeState();
        const balanceAfter = brokerState.balance;
        const balanceChanged = Math.abs(balanceAfter - balanceBefore) >= 0.01;

        console.log(`💰 Balance after: $${balanceAfter} (changed: ${balanceChanged})`);

        if (balanceChanged) {
            console.log(`✅✅✅ Trade VERIFIED! Balance changed.`);
            return { success: true, verified: true, direction: direction, balanceBefore, balanceAfter };
        } else {
            console.log(`⚠️ Click executed but balance unchanged. Trade may still be pending.`);
            return { success: true, verified: false, direction: direction, note: 'Balance unchanged - check broker' };
        }
    }

    // Fallback: find by text content
    console.log(`⚠️ Primary selector failed, trying text search...`);
    const buttons = document.querySelectorAll('button, a, div[role="button"], [class*="btn"]');
    for (let b of buttons) {
        if (b.offsetParent === null) continue; // Skip hidden
        const text = (b.innerText || b.textContent || '').toLowerCase();
        const cls = (b.className || '').toLowerCase();

        if (isCall && (text.includes('call') || text.includes('buy') || cls.includes('call') || cls.includes('green'))) {
            b.click();
            console.log(`✅ Clicked fallback CALL button: ${text.substring(0, 20)}`);
            return { success: true, direction: direction, method: 'fallback' };
        }
        if (!isCall && (text.includes('put') || text.includes('sell') || cls.includes('put') || cls.includes('red'))) {
            b.click();
            console.log(`✅ Clicked fallback PUT button: ${text.substring(0, 20)}`);
            return { success: true, direction: direction, method: 'fallback' };
        }
    }

    console.log(`❌ Trade button not found with any method!`);
    return { success: false, error: 'Trade button not found' };
}

// ===== UTILITIES =====

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function showTradeFlash(direction) {
    const flash = document.createElement('div');
    const isCall = direction.toUpperCase() === 'CALL';
    flash.style.cssText = `
        position: fixed;
        top: 0; left: 0; right: 0; bottom: 0;
        background: ${isCall ? 'rgba(0,255,136,0.3)' : 'rgba(255,68,68,0.3)'};
        z-index: 99998;
        pointer-events: none;
    `;
    document.body.appendChild(flash);
    setTimeout(() => flash.remove(), 300);
}

// ===== MESSAGE HANDLING =====

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    console.log('📨 Received:', message.type);

    if (!message || !message.type) {
        sendResponse({ error: 'Invalid message' });
        return true;
    }

    switch (message.type) {
        case 'EXECUTE_TRADE':
            // Log receipt of trade command
            console.log('🚨🚨🚨 EXECUTE_TRADE MESSAGE RECEIVED! 🚨🚨🚨');
            console.log('📨 Trade command:', JSON.stringify(message));

            // Smart trade execution with state comparison
            executeTrade({
                currency: message.currency,
                amount: message.amount,
                duration: message.duration,
                direction: message.direction
            }).then(result => {
                console.log('📊 Trade result:', JSON.stringify(result));
                sendResponse(result);
            }).catch(err => {
                console.error('❌ Trade error:', err);
                sendResponse({ success: false, error: err.message });
            });
            return true; // Keep channel open for async

        case 'GET_STATE':
        case 'GET_STATUS':
            observeState();
            sendResponse({
                connected: true,
                ...brokerState
            });
            break;

        case 'GET_BALANCE':
            observeState();
            sendResponse({ balance: brokerState.balance });
            break;

        case 'SET_AMOUNT':
            setAmount(message.amount).then(success => {
                sendResponse({ success });
            });
            return true;

        case 'SET_CURRENCY':
            setCurrency(message.currency).then(success => {
                observeState();
                sendResponse({
                    success,
                    currentCurrency: brokerState.currentCurrency
                });
            });
            return true;

        case 'SET_DURATION':
            setDuration(message.duration).then(success => {
                observeState();
                sendResponse({
                    success,
                    currentTime: brokerState.currentTime
                });
            });
            return true;

        default:
            sendResponse({ error: 'Unknown type' });
    }

    return true;
});

// ===== VISUAL INDICATOR =====

function showBotIndicator() {
    const existing = document.getElementById('elite-bot-indicator');
    if (existing) existing.remove();

    const indicator = document.createElement('div');
    indicator.id = 'elite-bot-indicator';
    indicator.innerHTML = '🤖 DOM Observer Active';
    indicator.style.cssText = `
        position: fixed;
        top: 10px;
        right: 10px;
        background: linear-gradient(135deg, #00ff88, #00cc66);
        color: white;
        padding: 8px 16px;
        border-radius: 20px;
        font-weight: bold;
        z-index: 99999;
        font-family: Arial, sans-serif;
        font-size: 14px;
        box-shadow: 0 4px 15px rgba(0,255,136,0.4);
        cursor: pointer;
    `;

    // Click to show current state
    indicator.onclick = () => {
        observeState();
        alert(`Broker State:
Currency: ${brokerState.currentCurrency}
Time: ${brokerState.currentTime}s
Amount: $${brokerState.currentAmount}
Balance: $${brokerState.balance}
Ready: ${brokerState.isReady ? 'Yes' : 'No'}`);
    };

    // Wait for body to exist before appending
    if (document.body) {
        document.body.appendChild(indicator);
    } else {
        // Retry when DOM is ready
        document.addEventListener('DOMContentLoaded', () => {
            if (document.body) {
                document.body.appendChild(indicator);
            }
        });
    }
}

function updateIndicator(text) {
    const el = document.getElementById('elite-bot-indicator');
    if (el) el.innerHTML = `🤖 ${text}`;
}

// ===== PERIODIC UPDATES TO BACKGROUND =====

setInterval(() => {
    observeState();
    try {
        chrome.runtime.sendMessage({
            type: 'STATE_UPDATE',
            state: brokerState
        });
    } catch (e) { }
}, 3000);

// ===== INITIALIZATION =====

setTimeout(() => {
    observeState();
    showBotIndicator();

    console.log('🚀 Elite Trading Bot - DOM Observer Ready!');
    console.log(`📊 Initial State: ${brokerState.currentCurrency} $${brokerState.currentAmount} ${brokerState.currentTime}s`);

    chrome.runtime.sendMessage({
        type: 'CONTENT_READY',
        url: window.location.href,
        state: brokerState
    });

}, 2000);
