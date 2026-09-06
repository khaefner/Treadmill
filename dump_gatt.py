import asyncio
from bleak import BleakClient

ADDRESS = "F4:7E:6A:FA:2E:7F"

async def main():
    async with BleakClient(ADDRESS) as client:
        print(f"Connected: {client.is_connected}")
        for s in client.services:
            print(f"Service: {s.uuid} ({s.description})")
            for c in s.characteristics:
                print(f"  Char: {c.uuid} ({c.description}) handle={c.handle} props={c.properties}")
                for d in c.descriptors:
                    print(f"    Desc: {d.uuid} handle={d.handle}")

if __name__ == "__main__":
    asyncio.run(main())
