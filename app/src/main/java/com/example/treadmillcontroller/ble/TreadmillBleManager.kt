package com.example.treadmillcontroller.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.UUID

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Scanning : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class TreadmillMetrics(
    val rawBytesHex: String = "",
    val speedMph: Float = 0.0f,
    val inclinePct: Float = 0.0f,
    val distanceMiles: Float = 0.0f,
    val elapsedSeconds: Int = 0,
    val heartRateBpm: Int = 0
)

class TreadmillBleManager(private val context: Context) {

    companion object {
        private const val TAG = "TreadmillBleManager"
        const val TARGET_MAC_ADDRESS = "F4:7E:6A:FA:2E:7F"

        val CHAR_CONTROL_UUID: UUID = UUID.fromString("00001534-1412-efde-1523-785feabcd123")
        val CHAR_NOTIFY_UUID: UUID = UUID.fromString("00001535-1412-efde-1523-785feabcd123")
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private fun hexToBytes(hex: String): ByteArray {
            val len = hex.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }

        // Magic Incantation required by treadmill to initialize communication
        val INIT_PACKETS = listOf(
            byteArrayOf(0xfe.toByte(), 0x02, 0x2c, 0x04),
            hexToBytes("0012020402280428900701cec4b0aaa2a8949696"),
            hexToBytes("0112aca8a2bad0dccefe14003a52786486a6fc18"),
            hexToBytes("ff08324aa0880200004400000000000000000000")
        )

        // Alternating keep-alive / telemetry query heartbeats (written to #1534 every 1s)
        val HEARTBEAT_1 = listOf(
            byteArrayOf(0xfe.toByte(), 0x02, 0x14, 0x03),
            hexToBytes("001202040210041002000a1b9430000040500080"),
            hexToBytes("ff02182700000000000000000000000000000000")
        )

        val HEARTBEAT_2 = listOf(
            byteArrayOf(0xfe.toByte(), 0x02, 0x19, 0x03),
            hexToBytes("001202040215041502000f800a41000000000000"),
            hexToBytes("ff07000000810010860000000000000000000000")
        )
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothGatt: BluetoothGatt? = null
    private var lastConnectedDevice: BluetoothDevice? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _metrics = MutableStateFlow(TreadmillMetrics())
    val metrics: StateFlow<TreadmillMetrics> = _metrics.asStateFlow()

    private var controlCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var keepAliveJob: Job? = null
    private var reconnectJob: Job? = null
    private var scanTimeoutJob: Job? = null

    private val gattMutex = Mutex()
    private var pendingWriteDeferred: CompletableDeferred<Int>? = null
    private var isUserDisconnect = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val addressMatch = device.address.equals(TARGET_MAC_ADDRESS, ignoreCase = true)
            val name = device.name ?: ""
            val nameMatch = name.contains("Treadmill", ignoreCase = true) ||
                    name.contains("iFit", ignoreCase = true) ||
                    name.contains("I_TL", ignoreCase = true)

            if (addressMatch || nameMatch) {
                Log.d(TAG, "Found target device: ${device.name} [${device.address}]")
                scanTimeoutJob?.cancel()
                stopScan()
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            _connectionState.value = ConnectionState.Error("BLE Scan failed (code $errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "Connected to GATT server. Discovering services...")
                        _connectionState.value = ConnectionState.Connecting
                        gatt?.discoverServices()
                    } else {
                        Log.w(TAG, "Connected with error status=$status")
                        handleDisconnect(gatt)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server (status=$status).")
                    handleDisconnect(gatt)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                _connectionState.value = ConnectionState.Error("GATT service discovery failed (status=$status)")
                return
            }

            Log.d(TAG, "Services discovered successfully. Binding characteristics...")
            controlCharacteristic = null
            notifyCharacteristic = null

            for (service in gatt.services) {
                service.getCharacteristic(CHAR_CONTROL_UUID)?.let {
                    controlCharacteristic = it
                    Log.d(TAG, "Found Control characteristic (#1534)")
                }
                service.getCharacteristic(CHAR_NOTIFY_UUID)?.let {
                    notifyCharacteristic = it
                    Log.d(TAG, "Found Notify/Telemetry characteristic (#1535)")
                }
            }

            if (controlCharacteristic != null && notifyCharacteristic != null) {
                _connectionState.value = ConnectionState.Connected
                Log.d(TAG, "Successfully bound treadmill characteristics.")

                // Request high priority for fast BLE transfers
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

                // Subscribe to notifications on #1535
                val charNotify = notifyCharacteristic!!
                gatt.setCharacteristicNotification(charNotify, true)
                val desc = charNotify.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                if (desc != null) {
                    Log.d(TAG, "Writing CCCD descriptor for #1535...")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(desc)
                    }
                } else {
                    Log.w(TAG, "CCCD descriptor not found on #1535; starting init directly")
                    startSessionWorkflow()
                }
            } else {
                Log.w(TAG, "Target control/notify characteristics not found!")
                _connectionState.value = ConnectionState.Error("Treadmill characteristics not found")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            Log.d(TAG, "onDescriptorWrite: desc=${descriptor?.uuid} status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                startSessionWorkflow()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            pendingWriteDeferred?.complete(status)
        }

        @Deprecated("Used for backward compatibility")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            @Suppress("DEPRECATION")
            val bytes = characteristic?.value ?: byteArrayOf()
            parseNotification(bytes)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            parseNotification(value)
        }
    }

    private fun startSessionWorkflow() {
        scope.launch {
            delay(200L)
            Log.d(TAG, "Sending initial Magic Incantation sequence...")
            val success = writeSequence(INIT_PACKETS)
            Log.d(TAG, "Magic Incantation sent result=$success")
            delay(500L)
            startKeepAliveLoop()
        }
    }

    private suspend fun writeGattPacket(packet: ByteArray): Boolean {
        val gatt = bluetoothGatt ?: return false
        val charControl = controlCharacteristic ?: return false

        return gattMutex.withLock {
            val deferred = CompletableDeferred<Int>()
            pendingWriteDeferred = deferred
            try {
                val initiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val res = gatt.writeCharacteristic(
                        charControl,
                        packet,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                    res == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    charControl.value = packet
                    @Suppress("DEPRECATION")
                    charControl.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(charControl)
                }

                if (!initiated) {
                    pendingWriteDeferred = null
                    return@withLock false
                }

                withTimeout(1500L) {
                    val status = deferred.await()
                    status == BluetoothGatt.GATT_SUCCESS
                }
            } catch (e: Exception) {
                Log.e(TAG, "GATT packet write error: ${e.message}")
                false
            } finally {
                pendingWriteDeferred = null
            }
        }
    }

    private suspend fun writeSequence(packets: List<ByteArray>): Boolean {
        for (packet in packets) {
            val ok = writeGattPacket(packet)
            if (!ok) {
                Log.w(TAG, "Failed writing packet in sequence: ${packet.joinToString("") { "%02X".format(it) }}")
                return false
            }
            delay(30L)
        }
        return true
    }

    private fun startKeepAliveLoop() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            Log.d(TAG, "Starting periodic keep-alive heartbeat loop (1.0s interval)")
            var useHeartbeat1 = true
            while (isActive && _connectionState.value is ConnectionState.Connected) {
                val packets = if (useHeartbeat1) HEARTBEAT_1 else HEARTBEAT_2
                useHeartbeat1 = !useHeartbeat1

                val success = writeSequence(packets)
                if (!success) {
                    Log.w(TAG, "Heartbeat write failed")
                }
                delay(1000L)
            }
        }
    }

    private fun handleDisconnect(gatt: BluetoothGatt?) {
        keepAliveJob?.cancel()
        pendingWriteDeferred?.cancel()
        try {
            gatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing gatt: ${e.message}")
        }
        bluetoothGatt = null

        if (isUserDisconnect) {
            _connectionState.value = ConnectionState.Disconnected
        } else {
            _connectionState.value = ConnectionState.Disconnected
            Log.d(TAG, "Unexpected disconnect. Auto-reconnecting in 2s...")
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(2000L)
                if (!isUserDisconnect && _connectionState.value is ConnectionState.Disconnected) {
                    lastConnectedDevice?.let { dev ->
                        Log.d(TAG, "Auto-reconnecting to ${dev.address}...")
                        connectToDevice(dev)
                    } ?: run {
                        startScan()
                    }
                }
            }
        }
    }

    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _connectionState.value = ConnectionState.Error("Bluetooth is disabled")
            return
        }

        isUserDisconnect = false
        reconnectJob?.cancel()

        try {
            val directDevice = bluetoothAdapter.getRemoteDevice(TARGET_MAC_ADDRESS)
            if (directDevice != null) {
                Log.d(TAG, "Directly connecting to known target device: $TARGET_MAC_ADDRESS")
                connectToDevice(directDevice)
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct connect fallback to scan: ${e.message}")
        }

        _connectionState.value = ConnectionState.Scanning
        val scanner: BluetoothLeScanner? = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            _connectionState.value = ConnectionState.Error("BLE Scanner not available")
            return
        }

        val filters = listOf(
            ScanFilter.Builder().setDeviceAddress(TARGET_MAC_ADDRESS).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting scan with filters: ${e.message}")
            scanner.startScan(null, settings, scanCallback)
        }

        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(15000L)
            if (_connectionState.value is ConnectionState.Scanning) {
                stopScan()
                _connectionState.value = ConnectionState.Error("Treadmill not found during scan")
            }
        }
    }

    fun stopScan() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan: ${e.message}")
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        lastConnectedDevice = device
        _connectionState.value = ConnectionState.Connecting
        Log.d(TAG, "Connecting to GATT server on ${device.address} (TRANSPORT_LE)...")
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        isUserDisconnect = true
        reconnectJob?.cancel()
        scanTimeoutJob?.cancel()
        keepAliveJob?.cancel()
        pendingWriteDeferred?.cancel()
        stopScan()

        bluetoothGatt?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting: ${e.message}")
            }
        }
        bluetoothGatt = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun parseNotification(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val hexStr = bytes.joinToString("") { "%02X".format(it) }

        var currentSpeed = _metrics.value.speedMph
        var currentIncline = _metrics.value.inclinePct
        var currentDist = _metrics.value.distanceMiles
        var currentTimer = _metrics.value.elapsedSeconds

        // Check for TreadmillStateResponse packet 0 (starts with 0x00 and contains speed, incline, distance)
        // Format: 00 12 01 04 02 2e 04 2e 02 02 [speed_2b] [incline_2b] ... [dist_4b]
        if (bytes.size >= 14 && bytes[0].toInt() == 0x00 && bytes[2].toInt() == 0x01 && bytes[3].toInt() == 0x04) {
            if (bytes.size > 5 && (bytes[5].toInt() and 0xFF) == 0x2e) {
                // Speed: uint16 little-endian (KPH * 100) -> MPH
                val speedRaw = (bytes[10].toInt() and 0xFF) or ((bytes[11].toInt() and 0xFF) shl 8)
                if (speedRaw != 0xFFFF) {
                    currentSpeed = (speedRaw / 100.0f) * 0.621371f
                }

                // Incline: uint16 little-endian (PCT * 100) -> %
                val inclineRaw = (bytes[12].toInt() and 0xFF) or ((bytes[13].toInt() and 0xFF) shl 8)
                if (inclineRaw != 0xFFFF) {
                    currentIncline = inclineRaw / 100.0f
                }

                if (bytes.size >= 20) {
                    val distMeters = (bytes[16].toLong() and 0xFF) or
                            ((bytes[17].toLong() and 0xFF) shl 8) or
                            ((bytes[18].toLong() and 0xFF) shl 16) or
                            ((bytes[19].toLong() and 0xFF) shl 24)
                    currentDist = (distMeters / 1000.0f) * 0.621371f
                }
            }
        } else if (bytes.size >= 11 && bytes[0].toInt() == 0x01 && bytes[1].toInt() == 0x12) {
            // TreadmillStateResponse packet 1 (starts with 01 12; byte[5] == 0x01 contains the workout timer)
            if ((bytes[5].toInt() and 0xFF) == 0x01) {
                val timerSec = (bytes[9].toInt() and 0xFF) or ((bytes[10].toInt() and 0xFF) shl 8)
                if (timerSec != currentTimer) {
                    Log.d(TAG, "Workout elapsed timer: ${timerSec}s")
                }
                currentTimer = timerSec
            }
        } else if (bytes.size >= 4 && bytes[0].toInt() == 0x02 && bytes[1].toInt() == 0x01) {
            // Command ack frame [0x02, 0x01, low, high, ...]
            val val16 = (bytes[2].toInt() and 0xFF) or ((bytes[3].toInt() and 0xFF) shl 8)
            currentSpeed = val16 / 10.0f
        } else if (bytes.size >= 4 && bytes[0].toInt() == 0x02 && bytes[1].toInt() == 0x02) {
            val val16 = (bytes[2].toInt() and 0xFF) or ((bytes[3].toInt() and 0xFF) shl 8)
            currentIncline = val16 / 10.0f
        }

        _metrics.value = _metrics.value.copy(
            rawBytesHex = hexStr,
            speedMph = currentSpeed,
            inclinePct = currentIncline,
            distanceMiles = currentDist,
            elapsedSeconds = currentTimer
        )
    }

    fun start(speedMph: Float = 1.0f) {
        val target = if (speedMph >= 0.5f) speedMph else 1.0f
        setSpeed(target)
    }

    fun stop() {
        setSpeed(0.0f)
    }

    fun setSpeed(speedMph: Float) {
        val speedKph100 = Math.round(speedMph * 1.609344f * 100f).coerceIn(0, 3000)
        val lowByte = (speedKph100 and 0xFF).toByte()
        val highByte = ((speedKph100 shr 8) and 0xFF).toByte()

        val p1 = byteArrayOf(0xfe.toByte(), 0x02, 0x0d, 0x02)
        val p2 = byteArrayOf(
            0xff.toByte(), 0x0d, 0x02, 0x04, 0x02, 0x09, 0x04, 0x09, 0x02, 0x01, 0x01,
            lowByte, highByte,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )

        scope.launch {
            Log.d(TAG, "Sending speed command ${speedMph}mph (KPH*100=$speedKph100, hex=${lowByte.toUByte().toString(16)},${highByte.toUByte().toString(16)})")
            writeSequence(listOf(p1, p2))
        }
    }

    fun setIncline(inclinePct: Float) {
        val inc100 = Math.round(inclinePct * 100f).coerceIn(0, 2000)
        val lowByte = (inc100 and 0xFF).toByte()
        val highByte = ((inc100 shr 8) and 0xFF).toByte()

        val p1 = byteArrayOf(0xfe.toByte(), 0x02, 0x0d, 0x02)
        val p2 = byteArrayOf(
            0xff.toByte(), 0x0d, 0x02, 0x04, 0x02, 0x09, 0x04, 0x09, 0x02, 0x01, 0x02,
            lowByte, highByte,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )

        scope.launch {
            Log.d(TAG, "Sending incline command ${inclinePct}% (inc100=$inc100, hex=${lowByte.toUByte().toString(16)},${highByte.toUByte().toString(16)})")
            writeSequence(listOf(p1, p2))
        }
    }
}
