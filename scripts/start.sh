#!/usr/bin/env bash
# start.sh — Plug-and-play startup script for BLE Tag Tracker
#
# Usage:
#   ./scripts/start.sh              # Start backend + DB + scanner
#   ./scripts/start.sh --backend    # Start backend + DB only (no scanner)
#   ./scripts/start.sh --clean      # Clean up and restart everything

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

info()  { echo -e "${CYAN}[INFO]${NC} $*"; }
ok()    { echo -e "${GREEN}[ OK ]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERR ]${NC} $*" >&2; }

# ── Clean mode ───────────────────────────────────────────────────────
if [[ "${1:-}" == "--clean" ]]; then
    info "Stopping any running containers..."
    docker compose down 2>/dev/null || true
    rm -rf "$PROJECT_DIR/.scanner-venv"
    info "Cleaned up. Restarting..."
fi

# ── Load .env if present ─────────────────────────────────────────────
if [[ -f "$PROJECT_DIR/.env" ]]; then
    info "Loading .env..."
    set -a; source "$PROJECT_DIR/.env"; set +a
fi

# ── Start Docker services ────────────────────────────────────────────
info "Starting backend and database..."

if ! docker compose up -d --build; then
    error "Failed to start Docker services"
    exit 1
fi

# Wait for database health check
info "Waiting for database to be ready..."
for i in $(seq 1 30); do
    if docker compose exec -T db pg_isready -U postgres &>/dev/null; then
        ok "Database is ready"
        break
    fi
    sleep 1
done

# Wait for backend health check
info "Waiting for backend to be ready..."
API_HOST="${BACKEND_API_HOST:-localhost}"
API_PORT="${SERVER_PORT:-8081}"

for i in $(seq 1 30); do
    if curl -sf "http://${API_HOST}:${API_PORT}/api/tags" &>/dev/null; then
        ok "Backend is ready (http://${API_HOST}:${API_PORT})"
        break
    fi
    sleep 2
done

ok "Open http://${API_HOST}:${API_PORT} in your browser"

# ── Seed default tags if DB is empty ─────────────────────────────────
TAGS_FILE="$PROJECT_DIR/ble-scanner/tags.json"
if [[ -f "$TAGS_FILE" ]]; then
    TAG_COUNT=$(curl -sf "http://${API_HOST}:${API_PORT}/api/tags" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
    if [[ "$TAG_COUNT" == "0" ]]; then
        info "No tags found — seeding default tags from tags.json..."

        python3 <<EOF
import json, subprocess

tags = json.load(open("$TAGS_FILE"))
api_base = "http://${API_HOST}:${API_PORT}"

for serial, info in tags.items():
    payload = {
        "serialNumber": serial,
        "name": info.get("name", ""),
        "description": "",
        "location": "",
        "ibeaconUuids": info.get("ibeacon_uuids", []),
        "serviceUuids": info.get("service_uuids", []),
        "mfgSignatures": info.get("mfg_signatures", [])
    }
    cmd = ["curl", "-sf", "-X", "POST",
           api_base + "/api/tags",
           "-H", "Content-Type: application/json",
           "-d", json.dumps(payload)]
    result = subprocess.run(cmd, capture_output=True)
    if result.returncode == 0:
        print(f"[ OK ] Registered: {serial} ({info.get('name', '')})")
    else:
        print(f"[ERR] Failed to register: {serial}")
EOF
    else
        info "Database has $TAG_COUNT tag(s), skipping seed"
    fi
fi

# ── Start scanner if not skipped ─────────────────────────────────────
if [[ "${1:-}" == "--backend" ]]; then
    info "Skipping scanner (--backend flag)"
    exit 0
fi

info "Setting up BLE scanner..."
SCANNER_DIR="$PROJECT_DIR/ble-scanner"
VENV_DIR="$PROJECT_DIR/.scanner-venv"

if [[ ! -f "$VENV_DIR/bin/activate" ]]; then
    if python3 -m venv "$VENV_DIR" 2>/dev/null; then
        info "Created Python virtual environment at $VENV_DIR"
    else
        warn "venv module not available, using system Python for scanner"
        VENV_DIR=""
    fi
fi

if [[ -n "$VENV_DIR" ]] && [[ -f "$VENV_DIR/bin/activate" ]]; then
    source "$VENV_DIR/bin/activate"
fi

if ! python -c "import bleak" &>/dev/null; then
    info "Installing scanner dependencies..."
    pip install -q --upgrade pip
    pip install -q -r "$SCANNER_DIR/requirements.txt"
fi

# Determine scanner API URL
if docker compose ps --status running backend &>/dev/null; then
    SCANNER_API_URL="http://localhost:${API_PORT}"
else
    # Fallback: try direct Java mode or environment variable
    SCANNER_API_URL="${SCANNER_API_URL:-http://localhost:${API_PORT}}"
fi

echo ""
echo "═══════════════════════════════════════════════════════"
echo ""

if command -v osascript &>/dev/null && [[ "$(uname)" == "Darwin" ]]; then
    # macOS: open scanner in a new Terminal window
    if [[ -n "$VENV_DIR" ]] && [[ -f "$VENV_DIR/bin/activate" ]]; then
        osascript <<EOF
tell application "Terminal"
    activate
    do script "cd '$SCANNER_DIR' && source '$VENV_DIR/bin/activate' && python3 scanner.py --mode forward --api-url $SCANNER_API_URL"
end tell
EOF
    else
        osascript <<EOF
tell application "Terminal"
    activate
    do script "cd '$SCANNER_DIR' && python3 scanner.py --mode forward --api-url $SCANNER_API_URL"
end tell
EOF
    fi
    ok "Scanner started in a new Terminal window"
else
    # Linux / other: print command for user to run
    echo "Run this command in a new terminal to start the scanner:"
    echo ""
    echo "    cd $SCANNER_DIR"
    if [[ -n "$VENV_DIR" ]] && [[ -f "$VENV_DIR/bin/activate" ]]; then
        echo "    source $VENV_DIR/bin/activate"
    fi
    echo "    python3 scanner.py --mode forward --api-url $SCANNER_API_URL"
    echo ""
fi

echo "═══════════════════════════════════════════════════════"
echo ""
info "All systems running. Logs:"
echo "    docker compose logs -f          # All services"
echo ""
