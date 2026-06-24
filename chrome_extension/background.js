/**
 * Elite Trading Bot - Background Script (DOM Observer Mode)
 * 
 * Works with the DOM Observer content script.
 * Receives trade signals from Python server and forwards to content script.
 */

console.log('[BOT] Elite Trading Bot Background - DOM Observer Mode');

let ws = null;
let isConnected = false;
let reconnectAttempts = 0;
const MAX_RECONNECT = 100;
const SERVER_URL = 'ws://localhost:5002';

// Broker state (updated from content script)
let brokerState = {
    connected: false,
    currency: '',
    time: 0,
    amount: 0,
    balance: 0,
    tradesExecuted: 0
};

// ===== SERVER CONNECTION =====

function connectToServer() {
    console.log('[BOT] Connecting to bot server...');

    try {
        ws = new WebSocket(SERVER_URL);

        ws.onopen = () => {
            console.log('[BOT] Connected to bot server!');
            isConnected = true;
            reconnectAttempts = 0;
            brokerState.connected = true;

            ws.send(JSON.stringify({
                type: 'EXTENSION_CONNECTED',
                mode: 'DOM_OBSERVER',
                timestamp: Date.now()
            }));
        };

        ws.onmessage = async (event) => {
            try {
                const data = JSON.parse(event.data);
                console.log('[BOT] From server:', data.type);
                await handleServerMessage(data);
            } catch (e) {
                console.error('[BOT] Message error:', e);
            }
        };

        ws.onclose = () => {
            console.log('[BOT] Disconnected');
            isConnected = false;
            brokerState.connected = false;
            scheduleReconnect();
        };

        ws.onerror = (e) => {
            console.error('[BOT] WebSocket error:', e);
        };

    } catch (e) {
        console.error('[BOT] Connection error:', e);
        scheduleReconnect();
    }
}

function scheduleReconnect() {
    if (reconnectAttempts < MAX_RECONNECT) {
        reconnectAttempts++;
        const delay = Math.min(1000 * reconnectAttempts, 30000);
        console.log('[BOT] Reconnect attempt ' + reconnectAttempts + '/' + MAX_RECONNECT + ' in ' + delay + 'ms...');
        setTimeout(connectToServer, delay);
    } else {
        console.log('[BOT] Max reconnect attempts reached. Use popup to manually reconnect.');
        setTimeout(() => {
            console.log('[BOT] Auto-resetting reconnect counter...');
            reconnectAttempts = 0;
            connectToServer();
        }, 300000);
    }
}

function forceReconnect() {
    console.log('[BOT] Force reconnect triggered!');
    reconnectAttempts = 0;
    if (ws) {
        try {
            ws.close();
        } catch (e) { }
        ws = null;
    }
    isConnected = false;
    brokerState.connected = false;
    connectToServer();
}

// ===== HANDLE SERVER MESSAGES =====

async function handleServerMessage(data) {
    if (!data || !data.type) return;

    switch (data.type) {
        case 'PING':
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({ type: 'PONG' }));
            }
            break;

        case 'WELCOME':
            console.log('[BOT] Server acknowledged');
            break;

        case 'TRADE_SIGNAL':
            const result = await executeTradeViaDOM(data);
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({
                    type: 'TRADE_RESULT',
                    ...result
                }));
            }
            break;

        case 'GET_STATUS':
            await sendStatusToServer();
            break;
    }
}

// ===== EXECUTE TRADE VIA DOM OBSERVER =====

async function executeTradeViaDOM(signal) {
    console.log('[BOT] Executing trade: ' + signal.direction + ' ' + signal.currency + ' $' + signal.amount);

    try {
        const tabs = await chrome.tabs.query({
            url: ['*://*.pocketoption.com/*', '*://*.po.market/*']
        });

        console.log('[BOT] Found ' + tabs.length + ' Pocket Option tabs');

        if (tabs.length === 0) {
            console.log('[BOT] No Pocket Option tabs found!');
            return { success: false, error: 'Pocket Option not open' };
        }

        const tabId = tabs[0].id;
        console.log('[BOT] Sending EXECUTE_TRADE to tab ' + tabId + '...');

        try {
            const response = await chrome.tabs.sendMessage(tabId, {
                type: 'EXECUTE_TRADE',
                currency: signal.currency || 'EUR/USD',
                amount: signal.amount || 1,
                duration: signal.duration || 60,
                direction: signal.direction || 'CALL'
            });

            console.log('[BOT] Response from content script:', response);

            if (response && response.success) {
                brokerState.tradesExecuted++;
                console.log('[BOT] Trade executed!');
            } else {
                console.log('[BOT] Trade failed:', response ? response.error : 'No response');
            }

            return response || { success: false, error: 'No response from content script' };
        } catch (sendErr) {
            console.error('[BOT] sendMessage failed:', sendErr.message);
            console.log('[BOT] Attempting to inject content script...');
            try {
                await chrome.scripting.executeScript({
                    target: { tabId: tabId },
                    files: ['content.js']
                });
                console.log('[BOT] Content script injected, retrying...');

                const retryResponse = await chrome.tabs.sendMessage(tabId, {
                    type: 'EXECUTE_TRADE',
                    currency: signal.currency || 'EUR/USD',
                    amount: signal.amount || 1,
                    duration: signal.duration || 60,
                    direction: signal.direction || 'CALL'
                });
                console.log('[BOT] Retry response:', retryResponse);
                return retryResponse || { success: false, error: 'No response after retry' };
            } catch (injectErr) {
                console.error('[BOT] Script injection failed:', injectErr.message);
                return { success: false, error: sendErr.message };
            }
        }

    } catch (e) {
        console.error('[BOT] Trade execution error:', e);
        return { success: false, error: e.message };
    }
}

// ===== STATUS =====

async function sendStatusToServer() {
    try {
        const tabs = await chrome.tabs.query({
            url: ['*://*.pocketoption.com/*', '*://*.po.market/*']
        });

        let pageState = { connected: false, balance: 0 };

        if (tabs.length > 0) {
            try {
                pageState = await chrome.tabs.sendMessage(tabs[0].id, { type: 'GET_STATE' });
                brokerState.balance = pageState ? (pageState.balance || 0) : 0;
                brokerState.currency = pageState ? (pageState.currentCurrency || '') : '';
                brokerState.amount = pageState ? (pageState.currentAmount || 0) : 0;
            } catch (e) { }
        }

        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({
                type: 'STATUS',
                mode: 'DOM_OBSERVER',
                pageConnected: tabs.length > 0,
                ...brokerState
            }));
        }
    } catch (e) { }
}

// ===== MESSAGES FROM CONTENT SCRIPT =====

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (!message || !message.type) {
        sendResponse({ error: 'Invalid' });
        return true;
    }

    switch (message.type) {
        case 'CONTENT_READY':
            console.log('[BOT] Content script ready');
            if (message.state) {
                brokerState.balance = message.state.balance || 0;
                brokerState.currency = message.state.currentCurrency || '';
            }
            sendResponse({ status: 'ok' });
            break;

        case 'STATE_UPDATE':
            if (message.state) {
                brokerState.balance = message.state.balance || brokerState.balance;
                brokerState.currency = message.state.currentCurrency || brokerState.currency;
                brokerState.amount = message.state.currentAmount || brokerState.amount;
            }
            sendResponse({ status: 'ok' });
            break;

        case 'TRADE_RESULT':
            console.log('[BOT] Trade result received:', message.result);
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({
                    type: 'OBSERVED_TRADE_RESULT',
                    ...message.result,
                    timestamp: Date.now()
                }));
                console.log('[BOT] Sent to WebSocket server');
            }
            sendTradeResultToAPI(message.result);
            sendResponse({ status: 'ok', forwarded: true });
            break;

        case 'GET_BOT_STATUS':
            sendResponse({
                serverConnected: isConnected,
                reconnectAttempts: reconnectAttempts,
                ...brokerState
            });
            break;

        case 'RECONNECT':
            console.log('[BOT] Manual reconnect requested from popup');
            forceReconnect();
            sendResponse({ status: 'reconnecting' });
            break;
    }

    return true;
});

/**
 * Send trade result to Python API via HTTP.
 */
async function sendTradeResultToAPI(result) {
    try {
        const response = await fetch('http://localhost:5001/trade-result', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(result)
        });

        if (response.ok) {
            console.log('[BOT] Trade result sent to API');
        } else {
            console.log('[BOT] API response:', response.status);
        }
    } catch (e) {
        console.log('[BOT] Could not send to API (server may not be running):', e.message);
    }
}

// ===== START =====
setTimeout(connectToServer, 1000);

console.log('[BOT] Background ready - DOM Observer Mode');
