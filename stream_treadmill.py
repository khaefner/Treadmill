import asyncio
import time
from bleak import BleakScanner, BleakClient

ADDRESS = "F4:7E:6A:FA:2E:7F"
CHAR_METRICS = "00001535-1412-efde-1523-785feabcd123"
CHAR_CONTROL = "00001534-1412-efde-1523-785feabcd123"

async def main():
    print(f"Finding device {ADDRESS}...")
    device = await BleakScanner.find_device_by_address(ADDRESS, timeout=10.0)
    if not device:
        print("Device not found during scan!")
        return

    print(f"Connecting to {device.name} ({device.address})...")
    async with BleakClient(device, timeout=15.0) as client:
        print("Connected! Streaming metrics every 1.0s (press Ctrl+C to stop)...\n")
        
        last_metrics = None
        for i in range(15):
            t = time.strftime("%H:%M:%S")
            try:
                metrics_data = await client.read_gatt_char(CHAR_METRICS)
                
                # Check byte differences
                if last_metrics and last_metrics != metrics_data:
                    diffs = [f"B{idx}: {last_metrics[idx]}->{val}" for idx, val in enumerate(metrics_data) if last_metrics[idx] != val]
                    diff_str = f" | CHANGED: {', '.join(diffs)}"
                else:
                    diff_str = ""

                print(f"[{t}] Metrics ({len(metrics_data)} bytes): {metrics_data.hex()}{diff_str}")
                last_metrics = metrics_data

            except Exception as e:
                print(f"[{t}] Read Error: {e}")

            await asyncio.sleep(1.0)

if __name__ == "__main__":
    asyncio.run(main())
