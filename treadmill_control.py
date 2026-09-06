import asyncio
import sys
from bleak import BleakScanner, BleakClient

ADDRESS = "F4:7E:6A:FA:2E:7F"
CHAR_CONTROL = "00001534-1412-efde-1523-785feabcd123"

def build_speed_command(speed_mph_or_kph: float) -> bytearray:
    """
    Constructs a control command frame for target speed.
    Speed is converted to integer units (e.g. 2.5 -> 25 or 250 depending on scale).
    """
    speed_val = int(round(speed_mph_or_kph * 10))
    low_byte = speed_val & 0xFF
    high_byte = (speed_val >> 8) & 0xFF
    
    # Generic iFit control packet header pattern
    packet = bytearray([0x02, 0x01, low_byte, high_byte, 0x00, 0x00, 0x00, 0x00])
    return packet

def build_incline_command(incline_pct: float) -> bytearray:
    """
    Constructs a control command frame for target incline grade percentage.
    """
    incline_val = int(round(incline_pct * 10))
    low_byte = incline_val & 0xFF
    high_byte = (incline_val >> 8) & 0xFF
    
    packet = bytearray([0x02, 0x02, low_byte, high_byte, 0x00, 0x00, 0x00, 0x00])
    return packet

async def send_command(cmd_bytes: bytearray):
    print(f"Finding device {ADDRESS}...")
    device = await BleakScanner.find_device_by_address(ADDRESS, timeout=10.0)
    if not device:
        print("Device not found!")
        return

    print(f"Connecting to {device.name}...")
    async with BleakClient(device, timeout=15.0) as client:
        print(f"Sending control packet to {CHAR_CONTROL}:")
        print(f"  HEX: {cmd_bytes.hex()}")
        print(f"  Bytes: {list(cmd_bytes)}")
        await client.write_gatt_char(CHAR_CONTROL, cmd_bytes, response=True)
        print("Command successfully sent to treadmill!")

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--speed":
        target_spd = float(sys.argv[2])
        cmd = build_speed_command(target_spd)
        asyncio.run(send_command(cmd))
    elif len(sys.argv) > 1 and sys.argv[1] == "--incline":
        target_inc = float(sys.argv[2])
        cmd = build_incline_command(target_inc)
        asyncio.run(send_command(cmd))
    else:
        print("Usage:")
        print("  python treadmill_control.py --speed <mph/kph>")
        print("  python treadmill_control.py --incline <pct>")
