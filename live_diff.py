import asyncio
import time
from bleak import BleakScanner, BleakClient

ADDRESS = "F4:7E:6A:FA:2E:7F"
CHAR_METRICS = "00001535-1412-efde-1523-785feabcd123"

async def main():
    print(f"Finding device {ADDRESS}...")
    device = await BleakScanner.find_device_by_address(ADDRESS, timeout=10.0)
    if not device:
        print("Device not found during scan!")
        return

    print(f"Connecting to {device.name} ({device.address})...")
    async with BleakClient(device, timeout=15.0) as client:
        print("Connected! Live tracking byte changes...")
        print("Start or change speed/incline on the treadmill to see live metric updates.\n")
        
        last_data = await client.read_gatt_char(CHAR_METRICS)
        print(f"Baseline Data ({len(last_data)} bytes):")
        print(f"HEX: {last_data.hex()}\n")

        for _ in range(60):  # Monitor for 60 seconds
            await asyncio.sleep(0.5)
            try:
                curr_data = await client.read_gatt_char(CHAR_METRICS)
                if curr_data != last_data:
                    t = time.strftime("%H:%M:%S")
                    changes = []
                    for idx, (old_b, new_b) in enumerate(zip(last_data, curr_data)):
                        if old_b != new_b:
                            changes.append(f"Byte[{idx:02d}]: {old_b} -> {new_b} (0x{new_b:02x})")
                    print(f"[{t}] CHANGES DETECTED:\n  " + "\n  ".join(changes))
                    last_data = curr_data
            except Exception as e:
                print(f"Read error: {e}")

if __name__ == "__main__":
    asyncio.run(main())
