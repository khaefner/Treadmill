import asyncio
import time
from bleak import BleakScanner, BleakClient

ADDRESS = "F4:7E:6A:FA:2E:7F"
CHAR_METRICS = "00001535-1412-efde-1523-785feabcd123"
CHAR_CONTROL = "00001534-1412-efde-1523-785feabcd123"

async def main():
    print(f"Connecting to treadmill {ADDRESS}...")
    device = await BleakScanner.find_device_by_address(ADDRESS, timeout=10.0)
    if not device:
        print("Device not found during scan.")
        return

    async with BleakClient(device, timeout=15.0) as client:
        print(f"Connected to {device.name}!")
        
        status_bytes = await client.read_gatt_char(CHAR_METRICS)
        control_bytes = await client.read_gatt_char(CHAR_CONTROL)

        print("\n================ TREADMILL CURRENT STATE ================")
        print(f"Time of Read: {time.strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"Connection Status: ACTIVE (BLE Connected)")
        print(f"Metrics Payload Size: {len(status_bytes)} bytes")
        
        # Analyze key byte indicators
        state_byte = status_bytes[0]
        speed_raw = status_bytes[1] | (status_bytes[2] << 8)
        incline_raw = status_bytes[3]
        
        print(f"\nDecoded Status Parameters:")
        print(f"  - Machine Mode / State Byte[0]: 0x{state_byte:02x} ({'IDLE / READY' if state_byte == 0 else f'Mode {state_byte}'})")
        print(f"  - Speed Indicator Byte[1-2]:    {speed_raw}")
        print(f"  - Incline Indicator Byte[3]:   {incline_raw}")
        print(f"\nRaw Metrics Payload (#1535):")
        print(f"  {status_bytes.hex()}")
        print(f"\nRaw Control Payload (#1534):")
        print(f"  {control_bytes.hex()}")
        print("=========================================================\n")

if __name__ == "__main__":
    asyncio.run(main())
