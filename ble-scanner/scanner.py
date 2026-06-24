#!/usr/bin/env python3
"""BLE Tag Scanner Prototype

Scans for BLE advertisements from **registered tags only**. Uses iBeacon
UUID matching (most reliable) with manufacturer data fallback.

Tags are fetched from the backend API at startup and refreshed periodically,
so registering a tag via the UI makes it immediately scannable.

Usage:
    # Discover all nearby BLE tags and their identifiers:
    python scanner.py --discover

    # Scan (fetches registered tags from backend):
    python scanner.py --mode simulate
    python scanner.py --mode forward
    python scanner.py --mode forward --url http://localhost:8081

    # Use a local tags.json fallback (offline mode):
    python scanner.py --mode forward --local-tags tags.json
"""

import argparse
import asyncio
import json
import re
import sys
from datetime import datetime
from pathlib import Path

try:
    from bleak import BleakScanner, BLEDevice, AdvertisementData
except ImportError:
    print("Error: 'bleak' is required. Install with: pip install bleak")
    sys.exit(1)

try:
    import aiohttp
except ImportError:
    print("Error: 'aiohttp' is required for forward mode. Install with: pip install aiohttp")
    sys.exit(1)

import time as _time

# Apple iBeacon company ID (used in mfg data)
APPLE_MFG_CID = 0x004C


def normalize_uuid(s: str) -> str:
    """Normalize UUID to uppercase without dashes for matching."""
    return s.strip().upper().replace("-", "")


APPLE_CID = 0x004C


def extract_ibeacon_uuid_from_mfg(mfg_data: dict,
                                   registered_uuids: set[str] | None = None) -> str | None:
    """Extract iBeacon UUID from manufacturer data.

    Only returns UUIDs that match known beacon formats (Apple CID 0x4C,
    MikroTik '0215' payload header).

    When *registered_uuids* is provided, only UUIDs present in that set
    are accepted — this eliminates any remaining false positives.

    Returns the UUID as a canonical string, or None.
    """
    registered: set[str] | None = None
    if registered_uuids is not None:
        registered = {normalize_uuid(u) for u in registered_uuids}

    # UUID regex (without dashes) — used only with structural validation
    _uuid_re = re.compile(r'[0-9a-f]{8}[0-9a-f]{4}[0-9a-f]{4}[0-9a-f]{4}[0-9a-f]{12}',
                          re.IGNORECASE)

    def _hex_uuid_to_str(hex32: str) -> str:
        return f"{hex32[:8]}-{hex32[8:12]}-{hex32[12:16]}-{hex32[16:20]}-{hex32[20:32]}"

    def _check(candidate: str | None) -> str | None:
        if candidate is None:
            return None
        norm = normalize_uuid(candidate)
        if registered is not None and norm not in registered:
            return None
        return candidate

    def _search_registered_in_hex(hex_str: str) -> str | None:
        """Search for a registered UUID inside hex string."""
        if registered is None:
            return None
        for reg_norm in registered:
            if reg_norm in hex_str:
                return f"{reg_norm[:8]}-{reg_norm[8:12]}-{reg_norm[12:16]}-{reg_norm[16:20]}-{reg_norm[20:32]}"
        return None

    for cid, raw_bytes in mfg_data.items():
        hex_str = raw_bytes.hex().upper()

        # Structured extraction: known fixed-format payloads
        if cid == APPLE_CID and hex_str.startswith("4C00"):
            uuid_hex = hex_str[8:40]  # bytes 4-19 = 16-byte UUID
            result = _check(_hex_uuid_to_str(uuid_hex))
            if result is not None:
                return result

        # MikroTik custom payload: '0215' header + data containing a UUID.
        if hex_str.startswith("0215"):
            uuid_hex = hex_str[8:40]
            if _uuid_re.fullmatch(uuid_hex):
                result = _check(_hex_uuid_to_str(uuid_hex))
                if result is not None:
                    return result
            # Try substring search for registered UUIDs
            if registered is not None:
                result = _search_registered_in_hex(hex_str)
                if result is not None:
                    return result

        # Fallback for non-standard Apple CID payload (no 4C00 prefix but
        # still contains a UUID). Only accept if the entire data is exactly
        # 32 hex chars (a bare UUID) — prevents substring false positives.
        if cid == APPLE_CID and not hex_str.startswith("4C00") and len(hex_str) == 32:
            if _uuid_re.fullmatch(hex_str):
                result = _check(_hex_uuid_to_str(hex_str))
                if result is not None:
                    return result

        # Search for registered UUIDs anywhere in Apple CID data
        if cid == APPLE_CID:
            result = _search_registered_in_hex(hex_str)
            if result is not None:
                return result

    return None


def matches_registered_tag(device: BLEDevice, adv_data: AdvertisementData,
                           registered: dict) -> str | None:
    """Check if this advertisement matches any registered tag.

    Returns the serial number of the matching tag, or None.
    Matches in order: service UUIDs > iBeacon UUID (from mfg data) > mfg prefix match.
    """
    # Build lookup: normalized UUID -> serial + collect all registered UUIDs
    ibeacon_lookup = {}
    all_ibeacon_uuids: list[str] = []
    mfg_sig_prefixes: dict[str, str] = {}  # prefix -> serial
    service_uuid_lookup = {}

    for serial, info in registered.items():
        # iBeacon UUIDs
        for uuid_str in (info.get("ibeacon_uuids") or []):
            ibeacon_lookup[normalize_uuid(uuid_str)] = serial
            all_ibeacon_uuids.append(uuid_str)

        # mfg signatures -> store first 16 chars as prefix
        for sig in (info.get("mfg_signatures") or []):
            prefix = normalize_uuid(sig)[:16]  # first 8 bytes
            mfg_sig_prefixes[prefix] = serial

        # service UUIDs (from BLE scan)
        for uuid_str in (info.get("service_uuids") or []):
            service_uuid_lookup[normalize_uuid(uuid_str)] = serial

    # 1. Check service UUIDs broadcast by the device
    for svc_uuid in adv_data.service_uuids:
        svc_normalized = normalize_uuid(str(svc_uuid))
        serial = service_uuid_lookup.get(svc_normalized)
        if serial:
            return serial

    # 2. Check iBeacon UUID from mfg data — validated against registered set
    ibeacon_uuid = extract_ibeacon_uuid_from_mfg(
        adv_data.manufacturer_data, set(all_ibeacon_uuids))
    if ibeacon_uuid:
        serial = ibeacon_lookup.get(normalize_uuid(ibeacon_uuid))
        if serial:
            return serial

    # 3. Check mfg data by prefix match (first 16 hex chars = 8 bytes)
    for cid, raw_bytes in adv_data.manufacturer_data.items():
        hex_str = normalize_uuid(raw_bytes.hex())
        prefix = hex_str[:16]
        serial = mfg_sig_prefixes.get(prefix)
        if serial:
            return serial

    return None


# Rate limiting per tag
_last_emit: dict[str, float] = {}


def should_emit(serial_key: str) -> bool:
    """Rate limit to 1 emit per 1 second per tag."""
    now = _time.time()
    last = _last_emit.get(serial_key, 0)
    if now - last < 1:
        return False
    _last_emit[serial_key] = now
    return True


async def emit(signal: dict, mode: str, backend_url: str) -> None:
    """Emit signal to stdout (simulate) or POST to backend (forward)."""
    if mode == "simulate":
        print(json.dumps(signal, default=str), flush=True)
    elif mode == "forward":
        try:
            timeout = aiohttp.ClientTimeout(total=5)
            async with aiohttp.ClientSession(timeout=timeout) as session:
                async with session.post(backend_url, json=signal) as resp:
                    if resp.status >= 400:
                        body = await resp.text()
                        print(f"[WARN] {resp.status} from backend: {body[:200]}")
                    else:
                        print(f"[OK] {signal['serialNumber']} RSSI={signal['rssi']}")
        except Exception as e:
            print(f"[ERR] Failed to post signal: {type(e).__name__}: {e}")


async def fetch_tags_from_api(api_base_url: str, local_tags_path: str | None = None) -> dict:
    """Fetch registered tags from backend API. Falls back to local JSON if unavailable."""
    # Try to fetch from backend API
    api_url = f"{api_base_url.rstrip('/')}/api/tags/scanner"
    try:
        timeout = aiohttp.ClientTimeout(total=10)
        async with aiohttp.ClientSession(timeout=timeout) as session:
            async with session.get(api_url) as resp:
                if resp.status == 200:
                    tags = await resp.json()
                    # Convert list of ScannerTagResponse to dict format for matching
                    return {tag["serialNumber"]: tag for tag in tags}
    except Exception as e:
        print(f"[WARN] Could not fetch tags from backend ({e})")

    # Fallback to local tags.json if available
    if local_tags_path:
        path = Path(local_tags_path)
        if path.exists():
            try:
                with open(path) as f:
                    data = json.load(f)
                    print(f"[INFO] Using local tags from {local_tags_path}")
                    return data
            except Exception as e:
                print(f"[WARN] Failed to load local tags: {e}")

    return {}


async def refresh_tags(api_base_url: str, current_tags: dict, local_tags_path: str | None = None) -> dict:
    """Refresh tags from API. Returns new tag dict if changed, else same."""
    new_tags = await fetch_tags_from_api(api_base_url, local_tags_path)
    if new_tags:
        return new_tags
    return current_tags


async def detection_callback(device: BLEDevice, adv_data: AdvertisementData) -> None:
    """Bleak 3.0 detection callback — only matches registered tags."""
    mode = getattr(detection_callback, "_mode", "simulate")
    backend_url = getattr(detection_callback, "_backend_url", "")
    registered = dict(getattr(detection_callback, "_registered", {}))

    serial_key = matches_registered_tag(device, adv_data, registered)
    if not serial_key:
        return  # Not a registered tag — ignore completely

    tag_info = registered.get(serial_key, {})
    name = tag_info.get("name", serial_key)

    signal = {
        "serialNumber": serial_key,
        "rssi": adv_data.rssi,
        "timestamp": datetime.now().strftime("%Y-%m-%dT%H:%M:%S.%f"),
        "address": device.address,
        "localName": adv_data.local_name or None,
    }

    # Filter out invalid RSSI values (127 = macOS/bleak error sentinel)
    if signal["rssi"] == 127 or signal["rssi"] < -120:
        return

    if should_emit(serial_key):
        await emit(signal, mode, backend_url)


def setup_callback(registered: dict, mode: str = "simulate", backend_url: str = ""):
    """Attach config and mode to callback."""
    detection_callback._mode = mode
    detection_callback._backend_url = backend_url
    detection_callback._registered = registered


async def discover_tags():
    """Scan for all BLE devices and print their identifiers."""
    print("Scanning for BLE devices (15 seconds)...\n")

    # service_uuid -> device info
    svc_sigs: dict[str, dict] = {}
    # mfg signatures -> device info (keyed by CID__hex)
    mfg_sigs: dict[str, dict] = {}
    # extracted iBeacon UUIDs -> device info
    iBeacon_uuids: dict[str, dict] = {}
    # raw device data
    devices: set[str] = set()

    def cb(device: BLEDevice, adv_data: AdvertisementData):
        devices.add(device.address)

        # Service UUIDs
        for uuid in adv_data.service_uuids:
            key = str(uuid).upper()
            if key not in svc_sigs:
                svc_sigs[key] = {
                    "address": device.address,
                    "rssi": adv_data.rssi,
                    "localName": adv_data.local_name or "(none)",
                }
            elif abs(adv_data.rssi - svc_sigs[key]["rssi"]) > 3:
                svc_sigs[key]["rssi"] = adv_data.rssi

        # Manufacturer data signatures and extracted iBeacon UUIDs
        for cid, raw_bytes in adv_data.manufacturer_data.items():
            hex_str = normalize_uuid(raw_bytes.hex())

            # Store full signature (keyed by CID+hex to avoid cross-device collision)
            sig_key = f"{cid:04X}:{hex_str[:20]}..."
            if sig_key not in mfg_sigs:
                mfg_sigs[sig_key] = {
                    "address": device.address,
                    "rssi": adv_data.rssi,
                    "localName": adv_data.local_name or "(none)",
                    "cid": cid,
                }
            elif abs(adv_data.rssi - mfg_sigs[sig_key]["rssi"]) > 3:
                mfg_sigs[sig_key]["rssi"] = adv_data.rssi

            # Extract iBeacon UUID from known structural formats
            beacon_uuid = None
            if cid == APPLE_CID and hex_str.startswith("4C00"):
                uuid_hex = hex_str[8:40]
                beacon_uuid = f"{uuid_hex[:8]}-{uuid_hex[8:12]}-{uuid_hex[12:16]}-{uuid_hex[16:20]}-{uuid_hex[20:32]}"
            elif hex_str.startswith("0215"):
                uuid_hex = hex_str[8:40]
                _uuid_re_test = re.compile(r'[0-9a-f]{8}[0-9a-f]{4}[0-9a-f]{4}[0-9a-f]{4}[0-9a-f]{12}',
                                           re.IGNORECASE)
                if _uuid_re_test.fullmatch(uuid_hex):
                    beacon_uuid = f"{uuid_hex[:8]}-{uuid_hex[8:12]}-{uuid_hex[12:16]}-{uuid_hex[16:20]}-{uuid_hex[20:32]}"

            if beacon_uuid and beacon_uuid not in iBeacon_uuids:
                iBeacon_uuids[beacon_uuid] = {
                    "address": device.address,
                    "rssi": adv_data.rssi,
                    "localName": adv_data.local_name or "(none)",
                    "cid": cid,
                }

    async with BleakScanner(cb):
        await asyncio.sleep(15)

    print(f"\nDiscovered {len(devices)} device(s), "
          f"{len(svc_sigs)} with service UUIDs, "
          f"{len(iBeacon_uuids)} iBeacon UUIDs, "
          f"{len(mfg_sigs)} unique mfg signatures.\n")

    if svc_sigs:
        print("Service UUIDs:")
        for i, (uuid, info) in enumerate(sorted(svc_sigs.items()), 1):
            print(f"  [{i}] UUID: {uuid}")
            print(f"      RSSI:  {info['rssi']} dBm")
            print(f"      Name:  {info['localName']}")
            print(f"      Addr:  {info['address']}")
            print()

    if iBeacon_uuids:
        print("iBeacon UUIDs (extracted from manufacturer data):")
        for i, (uuid, info) in enumerate(sorted(iBeacon_uuids.items()), 1):
            print(f"  [{i}] UUID: {uuid}")
            print(f"      CID:   0x{info['cid']:04X}")
            print(f"      RSSI:  {info['rssi']} dBm")
            print(f"      Name:  {info['localName']}")
            print(f"      Addr:  {info['address']}")
            print()

    if mfg_sigs:
        print("Manufacturer Data Signatures:")
        for i, (sig, info) in enumerate(sorted(mfg_sigs.items(), key=lambda x: -x[1]["rssi"]), 1):
            short = sig[:60] + ("..." if len(sig) > 60 else "")
            print(f"  [{i}] Sig: {short}")
            print(f"      CID:   0x{info['cid']:04X}")
            print(f"      RSSI:  {info['rssi']} dBm")
            print(f"      Name:  {info['localName']}")
            print()


async def main():
    parser = argparse.ArgumentParser(description="BLE Tag Scanner Prototype")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--discover", action="store_true",
                       help="Discover all nearby BLE tags (recommended first step)")
    group.add_argument("--mode", choices=["simulate", "forward"],
                       help="Output mode: simulate (print) or forward (POST to backend)")
    parser.add_argument("--url", default="http://localhost:8081/api/signals",
                        help="Backend signal URL (forward mode only)")
    parser.add_argument("--api-url", default="http://localhost:8081",
                        help="Backend API base URL (for fetching registered tags)")
    parser.add_argument("--local-tags", default=None,
                        help="Path to local tags.json fallback (offline mode)")

    args = parser.parse_args()

    if args.discover:
        await discover_tags()
        return

    # Fetch registered tags from backend API (with local JSON fallback)
    print(f"Fetching tags from backend at {args.api_url}...")
    registered = await fetch_tags_from_api(args.api_url, args.local_tags)

    if not registered:
        # If --local-tags was specified but failed, that's an error
        if args.local_tags:
            print(f"[ERROR] Could not fetch tags from backend and local file {args.local_tags} is unavailable")
            sys.exit(1)
        else:
            print("[WARN] No registered tags found. Scanner will poll for tags...")

    # Validate config
    has_identifiers = False
    for serial, info in registered.items():
        if (info.get("ibeacon_uuids") or info.get("mfg_signatures") or
                info.get("service_uuids")):
            has_identifiers = True
            break

    if not has_identifiers and registered:
        print("Error: Each tag must specify at least one of:")
        print("  - ibeacon_uuids: UUID from BLE iBeacon data")
        print("  - mfg_signatures: hex payload from manufacturer data")
        print("  - service_uuids: UUID broadcast by the BLE device")
        sys.exit(1)

    # Count matching identifier types
    n_uuids = sum(len(i.get("ibeacon_uuids") or []) for i in registered.values())
    n_sigs = sum(len(i.get("mfg_signatures") or []) for i in registered.values())
    n_svc = sum(len(i.get("service_uuids") or []) for i in registered.values())

    print(f"Scanning {len(registered)} tag(s) — matching by:")
    if n_uuids:
        print(f"  iBeacon UUIDs:     {n_uuids}")
    if n_sigs:
        print(f"  Mfg signatures:    {n_sigs}")
    if n_svc:
        print(f"  Service UUIDs:     {n_svc}")

    tag_names = [f"{info.get('name', k)} ({k})" for k, info in registered.items()]
    print(f"\nRegistered: {', '.join(tag_names)}")
    print(f"Mode: {args.mode}, URL: {args.url}")
    print("Scanning for BLE advertisements (press Ctrl+C to stop)...\n")

    setup_callback(registered, args.mode, args.url)

    # Poll for tag updates every 30 seconds
    refresh_task = None
    if args.mode == "forward":
        async def periodic_refresh():
            while True:
                await asyncio.sleep(30)
                new_tags = await refresh_tags(args.api_url, registered, args.local_tags)
                if new_tags and new_tags != registered:
                    print(f"[INFO] Tags updated: {len(new_tags)} tag(s)")
                    setup_callback(new_tags, args.mode, args.url)

        refresh_task = asyncio.create_task(periodic_refresh())

    # Do not filter by service_uuids in the BleakScanner.
    # On macOS, CoreBluetooth only reports devices advertising the filtered
    # service UUIDs, which hides iBeacon-only devices. The detection callback
    # already matches by service UUIDs, iBeacon UUIDs, and mfg signatures.
    scanner_args: dict = {}

    print(f"Bleak scan args: {scanner_args}")

    try:
        async with BleakScanner(detection_callback, **scanner_args):
            if refresh_task:
                await asyncio.gather(
                    asyncio.sleep(float('inf')),
                    refresh_task,
                )
            else:
                while True:
                    await asyncio.sleep(1)
    except asyncio.CancelledError:
        pass
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nScan stopped.")
