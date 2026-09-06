import asyncio
import logging
from bleak import BleakClient, BleakScanner

# Enable debug logging for bleak to see raw D-Bus / GATT communications
logging.basicConfig(level=logging.INFO)

ADDRESS = "F4:7E:6A:FA:2E:7F"

# UUIDs for iFit / I_TL treadmill service
SERVICE_UUID = "00001533-1412-efde-1523-785feabcd123"
WRITE_UUID   = "00001534-1412-efde-1523-785feabcd123"
NOTIFY_UUID  = "00001535-1412-efde-1523-785feabcd123"

def notification_handler(sender, data: bytearray):
    print(f"\n[NOTIFICATION] Received {len(data)} bytes from {sender}:")
    print(f"  HEX:  {data.hex()}")
    print(f"  BYTES: {list(data)}")

async def main():
    print(f"Scanning for {ADDRESS}...")
    device = await BleakScanner.find_device_by_address(ADDRESS, timeout=10.0)
    if not device:
        print("Device not found!")
        return

    print(f"Connecting to {device.name} ({device.address})...")
    async with BleakClient(device, timeout=15.0) as client:
        print(f"Connected! client.is_connected = {client.is_connected}")
        
        print("\n--- Service Discovery ---")
        notify_char = None
        for service in client.services:
            print(f"Service: {service.uuid} ({service.description})")
            for char in service.characteristics:
                print(f"  Char: {char.uuid} ({char.description}) - {char.properties}")
                if char.uuid.lower() == NOTIFY_UUID.lower() or "notify" in char.properties:
                    notify_char = char

        if NOTIFY_UUID in [c.uuid for s in client.services for c in s.characteristics]:
            target_uuid = NOTIFY_UUID
        elif notify_char:
            target_uuid = notify_char.uuid
        else:
            target_uuid = None

        if target_uuid:
            print(f"\nSubscribing to notifications on {target_uuid}...")
            await client.start_notify(target_uuid, notification_handler)
            print("Subscribed! Listening for 20 seconds...")
            await asyncio.sleep(20.0)
            await client.stop_notify(target_uuid)
        else:
            print("No notification characteristic found. Listening for 10s...")
            await asyncio.sleep(10.0)

if __name__ == "__main__":
    asyncio.run(main())
