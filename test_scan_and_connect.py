import asyncio
from bleak import BleakScanner, BleakClient

ADDRESS = "F4:7E:6A:FA:2E:7F"
SERVICE_UUID = "00001533-1412-efde-1523-785feabcd123"
NOTIFY_UUID  = "00001535-1412-efde-1523-785feabcd123"
WRITE_UUID   = "00001534-1412-efde-1523-785feabcd123"

def notification_callback(sender, data):
    print(f"\n[METRICS NOTIFICATION] len={len(data)}: {data.hex()}")
    print(f"  BYTES: {list(data)}")

async def main():
    print(f"Scanning for device {ADDRESS}...")
    device = await BleakScanner.find_device_by_address(ADDRESS, timeout=10.0)
    if not device:
        print(f"Device {ADDRESS} not found!")
        return

    print(f"Connecting to {device.name} ({device.address})...")
    async with BleakClient(device, timeout=15.0) as client:
        print(f"Connected: {client.is_connected}")
        
        print("\nDiscovered GATT Services:")
        for service in client.services:
            print(f" Service: {service.uuid} ({service.description})")
            for char in service.characteristics:
                print(f"   Char: {char.uuid} ({char.description}) - Props: {char.properties}")

        # Try subscribing to 1535 or any notify characteristic
        for service in client.services:
            for char in service.characteristics:
                if "notify" in char.properties or "indicate" in char.properties:
                    print(f"\nSubscribing to notifications on {char.uuid}...")
                    try:
                        await client.start_notify(char.uuid, notification_callback)
                        print(f"Successfully subscribed to {char.uuid}")
                    except Exception as e:
                        print(f"Failed to subscribe to {char.uuid}: {e}")

        print("\nListening for incoming treadmill metrics (30s)...")
        for i in range(30):
            await asyncio.sleep(1)
            print(".", end="", flush=True)

if __name__ == "__main__":
    asyncio.run(main())
