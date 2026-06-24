"""
Test Pocket Option Connection
"""
import requests

ssid = '42["auth",{"sessionToken":"141be5a780c20cc75715d24fc44c7165","uid":"78534594","lang":"en","currentUrl":"cabinet","isChart":1}]'

print("Testing Pocket Option Connection...")
print(f"SSID: {ssid[:50]}...")

try:
    response = requests.post(
        'http://localhost:5001/pocket-option/connect',
        json={
            'ssid': ssid,
            'demo_mode': True
        },
        timeout=30
    )
    print(f"Status: {response.status_code}")
    print(f"Raw response: {response.text[:500]}")
    if response.status_code == 200:
        print(f"JSON: {response.json()}")
except Exception as e:
    print(f"Error: {e}")
    import traceback
    traceback.print_exc()
