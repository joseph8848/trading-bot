// Popup script - updates UI with current status (Fixed Version)

document.addEventListener('DOMContentLoaded', () => {
    updateStatus();

    // Refresh every 2 seconds
    setInterval(updateStatus, 2000);

    // Reconnect button
    const reconnectBtn = document.getElementById('reconnectBtn');
    if (reconnectBtn) {
        reconnectBtn.addEventListener('click', () => {
            chrome.runtime.sendMessage({ type: 'RECONNECT' }, (response) => {
                const statusText = document.getElementById('statusText');
                if (statusText) {
                    statusText.textContent = 'Reconnecting...';
                }
            });
        });
    }
});

function updateStatus() {
    chrome.runtime.sendMessage({ type: 'GET_BOT_STATUS' }, (response) => {
        // Handle no response
        if (chrome.runtime.lastError || !response) {
            const statusDot = document.getElementById('statusDot');
            const statusText = document.getElementById('statusText');
            if (statusDot) statusDot.className = 'status-dot disconnected';
            if (statusText) statusText.textContent = 'Waiting for connection...';
            return;
        }

        const statusDot = document.getElementById('statusDot');
        const statusText = document.getElementById('statusText');
        const tradesCount = document.getElementById('tradesCount');
        const lastSignal = document.getElementById('lastSignal');

        // Connection status
        if (statusDot && statusText) {
            if (response.serverConnected) {
                statusDot.className = 'status-dot connected';
                statusText.textContent = 'Connected to Bot ✓';
            } else {
                statusDot.className = 'status-dot disconnected';
                statusText.textContent = 'Disconnected';
            }
        }

        // Trade count
        if (tradesCount) {
            tradesCount.textContent = response.tradesExecuted || 0;
        }

        // Last signal
        if (lastSignal && response.lastSignal) {
            const sig = response.lastSignal;
            const time = new Date(sig.time).toLocaleTimeString();
            lastSignal.innerHTML = `
                <span class="signal-${sig.direction.toLowerCase()}">${sig.direction}</span>
                $${sig.amount} on ${sig.currency}
                <br><small>${time}</small>
            `;
        }
    });
}
