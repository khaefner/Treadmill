import asyncio
from bleak import BleakScanner

async def main():
    print("Scanning for BLE devices for 10 seconds...")
    devices = await BleakScanner.discover(timeout=10.0, return_adv=True)
    for d, adv in devices.values():
        name = d.name or adv.local_name or "Unknown"
        if "I_TL" in name.upper() or "TL" in name.upper() or "TREAD" in name.upper() or "FIT" in name.upper() or "I_" in name.upper():
            print(f"MATCH: Address={d.address}, Name={name}, RSSI={adv.rssi}")
            print(f"  Service UUIDs: {adv.service_uuids}")
            print(f"  Manufacturer Data: {adv.manufacturer_data}")
            print(f"  Service Data: {adv.service_data}")
        else:
            print(f"Found: Address={d.address}, Name={name}, RSSI={adv.rssi}")

if __name__ == "__main__":
    asyncio.run(main())
