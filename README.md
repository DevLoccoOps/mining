# BLE Tag Tracker

Real-time person tracking system using BLE iBeacon tags. Register tags via the web UI, scan with a Python BLE scanner, and visualize live positions on a floor plan.

## Architecture

```
┌──────────────────────┐     ┌─────────────────┐     ┌──────────────────┐
│   Browser (UI)       │     │  BLE Scanner    │     │                  │
│  localhost:8081      │     │  (macOS/Linux)  │     │                  │
│  • Floor plan view   │◄────│  Fetches tags   │     │                  │
│  • Live positions    │     │  from backend   │     │                  │
│  • Signal history    │     └────────┬────────┘     │                  │
└──────────┬───────────┘              │ POST        │
           │ GET /api/*               │ signals     │
           │ WS /ws/signals           ▼             │
┌──────────┴───────────┐     ┌─────────────────┐     │
│  Backend (Spring)    │────►│  PostgreSQL     │     │
│  Port: 8081          │     │  :5432          │     │
│  • REST API          │     │  ble_tag_tracker│     │
│  • WebSocket         │     └─────────────────┘     │
│  • Static UI files   │                              │
└──────────────────────┘                              │
```

## Quick Start

### Prerequisites

- **JDK 21** (or Docker to run backend)
- **Python 3.11+** (for BLE scanner)
- **macOS** or Linux with BLE support

### One-command start

```bash
./scripts/start.sh
```

This script:
1. Starts PostgreSQL + Backend via Docker Compose
2. Installs Python dependencies for the scanner
3. Launches the scanner in a new terminal window

Open **http://localhost:8081** in your browser.

## Setup

### 1. Backend & Database

#### Option A: Docker (recommended)

```bash
docker compose up --build -d
```

This starts the backend (port 8081) and PostgreSQL (port 5432). Wait ~15s for the database to be ready.

#### Option B: Local Java

```bash
# Start PostgreSQL locally (or use Docker for DB only)
docker run -d --name ble-db \
  -e POSTGRES_DB=ble_tag_tracker \
  -e POSTGRES_USER=lebohang \
  -e POSTGRES_PASSWORD=lebohang \
  -p 5432:5432 postgres:16-alpine

# Start backend
cd Backend && mvn spring-boot:run
```

### 2. Frontend

The frontend is served automatically from the backend at **http://localhost:8081**. No separate server needed.

### 3. BLE Scanner

```bash
cd ble-scanner
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python3 scanner.py --mode forward
```

The scanner automatically fetches registered tags from the backend API every 30 seconds.

Or use the `--local-tags` flag for offline mode:

```bash
python3 scanner.py --mode forward --local-tags tags.json
```

## Using the System

### Register a Tag

1. Open **http://localhost:8081**
2. Click **"Register Tag"** in the sidebar
3. Enter the tag serial number and iBeacon UUID (discover using the scanner below)

### Discover BLE Tag Identifiers

Run a discovery scan to find nearby BLE tags and extract their iBeacon UUIDs:

```bash
python3 scanner.py --discover
```

Copy an `iBeacon UUID` into the tag registration form.

### Live Tracking

Once a registered tag is in range, you'll see:
- A person icon moving on the floor plan in real-time
- Signal strength (RSSI) history chart
- Tag name and last-seen timestamp in the sidebar

## Configuration

Create a `.env` file in the project root to override defaults:

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=ble_tag_tracker
DB_USER=postgres
DB_PASSWORD=postgres

# Backend server port
SERVER_PORT=8081
```

### Backend Properties

| Property | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `ble_tag_tracker` | Database name |
| `DB_USER` | `lebohang` | Database user |
| `DB_PASSWORD` | *(empty)* | Database password |

## Scanner

### API-based Mode (default)

Fetches registered tags from `GET /api/tags/scanner`. Tags are refreshed every 30 seconds.

```bash
python3 scanner.py --mode forward
python3 scanner.py --mode forward --api-url http://localhost:8081
```

### Offline Mode

Use a local `tags.json` file (no backend required for tag config):

```bash
python3 scanner.py --mode forward --local-tags tags.json
```

### Simulate (dry run)

Print signals to stdout as JSON lines:

```bash
python3 scanner.py --mode simulate
```

### Discover Mode

Scan for 15 seconds and print all BLE device identifiers:

```bash
python3 scanner.py --discover
```

## API Reference

### Tags

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/tags` | Register a new tag |
| GET | `/api/tags` | List all registered tags |
| GET | `/api/tags/{serialNumber}` | Get a single tag |
| DELETE | `/api/tags/{serialNumber}` | Delete a tag |
| GET | `/api/tags/scanner` | Tags in scanner format (for BLE scanner) |

#### Register a Tag Example

```json
{
  "serialNumber": "HGY0ADJ6GZ3",
  "name": "Lebohang",
  "description": "",
  "location": "",
  "ibeaconUuids": ["B2B98DE4-C81C-47C2-B14E-791B3E5587EC"],
  "serviceUuids": [],
  "mfgSignatures": []
}
```

### Signal Readings

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/signals` | Record a signal reading |
| GET | `/api/signals/tags/{serialNumber}` | Get signal history (max 50) |
| GET | `/api/signals/tags/{serialNumber}/latest` | Get latest reading |

### WebSocket

| Endpoint | Direction | Description |
|---|---|---|
| `/ws/signals` | Push | Real-time signal broadcast to connected clients |

## Docker

### Run Everything (Backend + DB)

```bash
docker compose up -d          # Start in background
docker compose logs -f        # View all logs
docker compose down           # Stop and clean up
```

### Scanner in Docker

The scanner container requires Bluetooth access. On macOS, grant Docker Desktop permission:

```
System Settings > Privacy & Security > Bluetooth > Allow Docker to access BLE devices
```

## Troubleshooting

### Scanner can't connect to backend

- Make sure the backend is running: `curl http://localhost:8081/api/tags`
- Check scanner logs for connection errors

### Frontend can't connect to WebSocket

- The frontend uses relative URLs (`/api`, `/ws/signals`) — ensure you're accessing the app through the backend, not serving `index.html` directly
- Check browser console for WebSocket connection errors

### macOS Bluetooth permission denied

- Go to **System Settings > Privacy & Security > Bluetooth** and allow Python/terminal
- After restarting the scanner, grant permission when prompted

### Database connection fails

```bash
# Check if DB is running (Docker mode)
docker compose ps

# Restart everything
docker compose down
docker compose up --build -d
```

### Port 8081 already in use

```bash
# Find what's using the port
lsof -i :8081

# Or change the port via environment variable
SERVER_PORT=8082 docker compose up -d
```

## Project Structure

```
├── Backend/              # Spring Boot 3.2.2, Java 21
│   ├── src/main/java/    # Controllers, services, entities
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── static/       # Frontend served here
│   ├── Dockerfile
│   └── pom.xml
├── Frontend/             # Original source (also copied to Backend/static)
│   └── index.html
├── ble-scanner/          # Python BLE scanner (bleak)
│   ├── scanner.py
│   ├── tags.json         # Offline tag config fallback
│   └── requirements.txt
├── docker-compose.yml    # Backend + DB + Scanner services
└── scripts/
    └── start.sh          # One-command startup script
```
