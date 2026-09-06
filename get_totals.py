import asyncio
import struct
from bleak import BleakScanner, BleakClient

ADDRESS = "F4:7E:6A:FA:2E:7F"

# UUIDs for Treadmill metrics & totals
HDP_SERVICE = "00001400-555e-e99c-e511-f9f4f8daeb24"
CHAR_1401 = "00001401-555e-e99c-e511-f9f4f8daeb24"
CHAR_1402 = "00001402-555e-e99c-e511-f9f4f8daeb24"
CHAR_1403 = "00001403-555e-e99c-e511-f9f4f8daeb24"
CHAR_1404 = "00001404-555e-e99c-e511-f9f4f8daeb24"

MAIN_SERVICE = "00001533-1412-efde-1523-785feabcd123"
CHAR_1535 = "00001535-1412-efde-1523-785feabcd123"

async def main():
    print(f"Connecting to {ADDRESS} to retrieve total odometer metrics...")
    device = await BleakScanner.find_device_by_address(ADDRESS, timeout=10.0)
    if not device:
        print("Device not found during scan.")
        return

    async with BleakClient(device, timeout=15.0) as client:
        print("Connected!")
        
        print("\n================ TREADMILL CUMULATIVE TOTALS ================")
        
        # Read HDP / Odometer characteristics
        hdp_data = {}
        for char_uuid in [CHAR_1401, CHAR_1402, CHAR_1403, CHAR_1404]:
            try:
                val = await client.read_gatt_char(char_uuid)
                hdp_data[char_uuid] = val
                print(f"Odometer Char {char_uuid[-12:]}: {val.hex()} ({list(val)})")
            except Exception as e:
                print(f"Could not read {char_uuid}: {e}")

        # Read main 75-byte payload
        try:
            p75 = await client.read_gatt_char(CHAR_1535)
            print(f"\nMain Telemetry Frame (#1535) Length: {len(p75)} bytes")
            print(f"Payload Hex: {p75.hex()}")
        except Exception as e:
            print(f"Error reading #1535: {e}")
            p75 = None

        print("\nParsed Cumulative Odometers & Total Metrics:")
        if CHAR_1401 in hdp_data:
            b1401 = hdp_data[CHAR_1401]
            val16 = struct.unpack("<H", b1401)[0] if len(b1401) >= 2 else b1401[0]
            print(f"  - Total Workouts / Sessions Count (#1401): {val16}")
            
        if CHAR_1402 in hdp_data:
            b1402 = hdp_data[CHAR_1402]
            val8 = b1402[0]
            print(f"  - Total Operating Hours (#1402):         {val8} hrs")

        if CHAR_1403 in hdp_data:
            b1403 = hdp_data[CHAR_1403]
            val16 = struct.unpack("<H", b1403)[0] if len(b1403) >= 2 else b1403[0]
            print(f"  - Total Distance / Odometer (#1403):       {val16 / 10.0:.1f} mi (or km)")

        if CHAR_1404 in hdp_data:
            b1404 = hdp_data[CHAR_1404]
            val8 = b1404[0]
            print(f"  - System Maintenance / Status (#1404):     {val8}")

        print("=============================================================\n")

if __name__ == "__main__":
    asyncio.run(main())
