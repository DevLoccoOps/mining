# BLE Tag Scanner Prototype

Scans for BLE advertisements from known tags on macOS. Validates the backend pipeline before ESP32 deployment.

## Setup

Using a virtual environment (recommended):
```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

If `python3 -m venv` is not available (e.g., pyenv Python), install globally:
```bash
pip3 install -r requirements.txt
```

## Configure Tags

Edit `tags.json` with your tag MAC addresses:

```json
{
  "AA:BB:CC:DD:EE:FF": {
    "name": "TAG-001",
    "rssi_threshold": -90
  }
}
```

## Usage

**Dry run** — prints JSON lines to stdout:
```bash
python scanner.py --mode simulate --tags tags.json
```

**Forward to backend** — POSTs signals to the Spring Boot API:
```bash
python scanner.py --mode forward --tags tags.json
```

**Custom backend URL:**
```bash
python scanner.py --mode forward --tags tags.json --url http://localhost:8080/api/signals
```

## Output Format (matches ESP32 payload)

```json
{
  "serialNumber": "TAG-001",
  "rssi": -65,
  "timestamp": "2026-06-21T12:00:00+00:00",
  "address": "AA:BB:CC:DD:EE:FF",
  "localName": "MyTag",
  "manufacturerData": {
    "0x1234": "deadbeef"
  }
}
```

## macOS Bluetooth Permission

On first run, macOS will prompt for Bluetooth permission. Grant it in System Settings.
