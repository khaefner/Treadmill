import asyncio
import sys
import time
from bleak import BleakScanner, BleakClient

ADDRESS = "F4:7E:6A:FA:2E:7F"
NOTIFY_CHAR_UUID = "00001535-1412-efde-1523-785feabcd123"
WRITE_CHAR_UUID  = "00001534-1412-efde-1523-785feabcd123"

def notification_handler(sender, data: bytearray):
    t = time.strftime("%H:%M:%S")
    print(f"[{t}] [NOTIFICATION] Recv {len(data)} bytes:")
    print(f"       Hex:   {data.hex()}")
    print(f"       Bytes: {list(data)}")

async def run_reader(target_address):
    print(f"Scanning for BLE device {target_address}...")
    device = await BleakScanner.find_device_by_address(target_address, timeout=10.0)
    if not device:
        print(f"Device {target_address} not detected during scan.")
        return False

    print(f"Found device: {device.name} [{device.address}]. Connecting...")
    
    # Try connecting with BLEDevice object
    for attempt in range(1, 4):
        try:
            print(f"Connection attempt {attempt}...")
            async with BleakClient(device, timeout=20.0) as client:
                print(f"Connected! (is_connected={client.is_connected})")

                # Subscribe to notifications
                print(f"Subscribing to notification characteristic ({NOTIFY_CHAR_UUID})...")
                try:
                    await client.start_notify(NOTIFY_CHAR_UUID, notification_handler)
                    print("Subscribed to notifications successfully!")
                except Exception as e:
                    print(f"Notification subscription error: {e}")

                print("\n--- Initial GATT Characteristic Values ---")
                try:
                    val_1535 = await client.read_gatt_char(NOTIFY_CHAR_UUID)
                    print(f"NOTIFY CHAR (#1535) [{len(val_1535)} bytes]:")
                    print(f"  HEX:   {val_1535.hex()}")
                    print(f"  BYTES: {list(val_1535)}")
                except Exception as e:
                    print(f"Could not read #1535: {e}")

                try:
                    val_1534 = await client.read_gatt_char(WRITE_CHAR_UUID)
                    print(f"WRITE CHAR (#1534) [{len(val_1534)} bytes]:")
                    print(f"  HEX:   {val_1534.hex()}")
                    print(f"  BYTES: {list(val_1534)}")
                except Exception as e:
                    print(f"Could not read #1534: {e}")

                print("\n--- Streaming / Polling Metrics Stream (15 Seconds) ---")
                prev_1535 = None
                for i in range(15):
                    await asyncio.sleep(1.0)
                    try:
                        val_1535 = await client.read_gatt_char(NOTIFY_CHAR_UUID)
                        t = time.strftime("%H:%M:%S")
                        chg = " ** CHANGED **" if prev_1535 is not None and val_1535 != prev_1535 else ""
                        print(f"[{t}] Poll #1535 ({len(val_1535)}b): {val_1535.hex()}{chg}")
                        prev_1535 = val_1535
                    except Exception as e:
                        print(f"Poll error: {e}")

                print("\nDone reading metrics stream!")
                return True

        except Exception as e:
            print(f"Attempt {attempt} failed: {e}")
            await asyncio.sleep(2.0)

    return False

if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else ADDRESS
    asyncio.run(run_reader(target))
