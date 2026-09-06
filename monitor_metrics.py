import asyncio
import time
from bleak import BleakScanner, BleakClient

ADDRESS = "F4:7E:6A:FA:2E:7F"
NOTIFY_CHAR = "00001535-1412-efde-1523-785feabcd123"
WRITE_CHAR  = "00001534-1412-efde-1523-785feabcd123"

def notification_callback(sender, data: bytearray):
    t = time.strftime("%H:%M:%S")
    print(f"[{t}] NOTIFY ({len(data)}b): {data.hex()}")

async def main():
    print(f"Connecting to {ADDRESS}...")
    device = await BleakScanner.find_device_by_address(ADDRESS, timeout=10.0)
    if not device:
        print("Device not found")
        return

    async with BleakClient(device) as client:
        print(f"Connected to {device.name}!")
        
        # Subscribe to notifications if emitted
        await client.start_notify(NOTIFY_CHAR, notification_callback)
        print(f"Subscribed to {NOTIFY_CHAR}")

        print("\nPolling characteristics every 1 second (press Ctrl+C to stop)...")
        prev_val_1535 = None
        prev_val_1534 = None

        for idx in range(30):
            try:
                val_1535 = await client.read_gatt_char(NOTIFY_CHAR)
                val_1534 = await client.read_gatt_char(WRITE_CHAR)

                t = time.strftime("%H:%M:%S")
                
                # Check if changed
                chg_1535 = " (CHANGED!)" if val_1535 != prev_val_1535 and prev_val_1535 is not None else ""
                chg_1534 = " (CHANGED!)" if val_1534 != prev_val_1534 and prev_val_1534 is not None else ""

                print(f"[{t}] #1535 ({len(val_1535)}b): {val_1535.hex()}{chg_1535}")
                print(f"[{t}] #1534 ({len(val_1534)}b): {val_1534.hex()}{chg_1534}")

                prev_val_1535 = val_1535
                prev_val_1534 = val_1534

            except Exception as e:
                print(f"Error reading chars: {e}")

            await asyncio.sleep(1.0)

if __name__ == "__main__":
    asyncio.run(main())
