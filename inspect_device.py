import asyncio
from bleak import BleakScanner, BleakClient

ADDRESS = "F4:7E:6A:FA:2E:7F"

async def main():
    print(f"Finding device {ADDRESS}...")
    device = await BleakScanner.find_device_by_address(ADDRESS, timeout=10.0)
    if not device:
        print("Device not found during scan!")
        return

    print(f"Connecting to {device.name} ({device.address})...")
    async with BleakClient(device) as client:
        print(f"Connected: {client.is_connected}")
        print("Services:")
        for service in client.services:
            print(f"[Service] {service.uuid} ({service.description})")
            for char in service.characteristics:
                print(f"  [Char] {char.uuid} ({char.description}) - Props: {','.join(char.properties)}")
                for desc in char.descriptors:
                    print(f"    [Desc] {desc.uuid}")

if __name__ == "__main__":
    asyncio.run(main())
