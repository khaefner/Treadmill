import asyncio
from bleak import BleakScanner, BleakClient

ADDRESS = "F4:7E:6A:FA:2E:7F"

async def main():
    device = await BleakScanner.find_device_by_address(ADDRESS, timeout=10.0)
    if not device:
        print("Device not found")
        return

    async with BleakClient(device) as client:
        print(f"Connected: {client.is_connected}")
        for service in client.services:
            print(f"\nService: {service.uuid}")
            for char in service.characteristics:
                if "read" in char.properties:
                    try:
                        val = await client.read_gatt_char(char.uuid)
                        print(f"  Char {char.uuid}: {val.hex()} ({list(val)})")
                    except Exception as e:
                        print(f"  Char {char.uuid}: Read failed ({e})")
                else:
                        print(f"  Char {char.uuid}: (not readable, props: {char.properties})")

if __name__ == "__main__":
    asyncio.run(main())
