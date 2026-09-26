package com.github.tomasbjerre.wisp.location

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * Thin wrapper around the platform's BLE APIs that finds, connects to, and reads a
 * standard Heart Rate Service monitor — see specs/heart-rate.md#connecting. Best-effort:
 * without Bluetooth, the permission, or a monitor in range, [start] never crashes, it
 * just never calls back. All the decision logic lives in [HeartRateMeasurement] and
 * [HeartRateRecorder].
 */
@SuppressLint("MissingPermission") // Guarded by hasPermission(), which lint can't see through.
class HeartRateMonitor(
    private val context: Context,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var onReading: ((Int) -> Unit)? = null
    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private val candidates = mutableMapOf<String, ScanResult>()

    private val adapter: BluetoothAdapter? get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun start(onReading: (Int) -> Unit) {
        stop()
        if (!hasPermission() || adapter?.isEnabled != true) return
        this.onReading = onReading
        startScan()
    }

    fun stop() {
        onReading = null
        handler.removeCallbacksAndMessages(null)
        stopScan()
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
    }

    private fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        candidates.clear()
        scanning = true
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HEART_RATE_SERVICE)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            scanCallback,
        )
        // Collect for a moment before choosing, so the strongest signal wins rather than
        // whichever advertised first — see specs/heart-rate.md#connecting.
        handler.postDelayed(::connectToStrongest, SCAN_WINDOW_MILLIS)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private fun connectToStrongest() {
        stopScan()
        val best = candidates.values.maxByOrNull { it.rssi }
        if (best == null) {
            // Nothing in range yet — keep looking until the session ends.
            handler.postDelayed(::startScan, RETRY_MILLIS)
            return
        }
        gatt = best.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun reconnectLater() {
        gatt?.close()
        gatt = null
        if (onReading != null) handler.postDelayed(::startScan, RETRY_MILLIS)
    }

    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult,
            ) {
                candidates[result.device.address] = result
            }
        }

    private val gattCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> gatt.discoverServices()
                    BluetoothProfile.STATE_DISCONNECTED -> handler.post(::reconnectLater)
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                val characteristic = gatt.getService(HEART_RATE_SERVICE)?.getCharacteristic(HEART_RATE_MEASUREMENT)
                if (characteristic == null) {
                    gatt.disconnect()
                    return
                }
                gatt.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic.getDescriptor(CLIENT_CONFIG) ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
            }

            // API 33+ delivers the value as an argument.
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) = deliver(value)

            // Below API 33 the value is read off the characteristic.
            @Deprecated("Replaced by the overload above on API 33+")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                @Suppress("DEPRECATION")
                deliver(characteristic.value ?: return)
            }

            private fun deliver(value: ByteArray) {
                val bpm = HeartRateMeasurement.parse(value) ?: return
                handler.post { onReading?.invoke(bpm) }
            }
        }

    private fun hasPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            REQUIRED_PERMISSIONS_S.all { isGranted(it) }
        } else {
            // Legacy install-time permissions; scanning also needs the location grant
            // recording already has.
            true
        }

    private fun isGranted(p: String) = ContextCompat.checkSelfPermission(context, p) == GRANTED

    companion object {
        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val GRANTED = PackageManager.PERMISSION_GRANTED
        private const val SCAN_WINDOW_MILLIS = 4_000L
        private const val RETRY_MILLIS = 5_000L

        /** What must be granted before [start] can do anything, on Android 12+. */
        val REQUIRED_PERMISSIONS_S = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    }
}
