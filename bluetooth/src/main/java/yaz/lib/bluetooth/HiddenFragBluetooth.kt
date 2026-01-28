/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * Fichier : LogDevicesScanned.kt
 * Module : bluetooth
 *
 * Description :
 *      Gere les connexion, callback, disconnection bluetooth.
 *      Gere les logs des devices scannés.
 *      Gere l activation du bluetooth.
 *
 * Auteur : @YazTarkann
 * Créé le : 22/01/2026
 * Dernière modification : 23/01/2026
 */

package yaz.lib.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

class HiddenFragBluetooth : Fragment() {
    private var appContext: Context? = null

    val neededPermissions = if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
        )
    }

    // UUID service BLE
    private var serviceUUID: UUID = UUID.fromString("4880c12c-fdcb-4077-8920-a450d7f9b907")
    // UUID characteristic d ecriture
    private var characteristicUUID: UUID = UUID.fromString("fec26ec4-6d71-4442-9f81-55bc21d658d6")

    private val bluetoothAdapter: BluetoothAdapter?
        get() {
            val ctx = appContext ?: return null
            val manager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            return manager.adapter
        }

    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothLauncher : ActivityResultLauncher<Intent>? = null
    private var bluetoothCallback: ((Boolean) -> Unit)? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    private var listDeviceInfo: MutableList<InfoDevice> = mutableListOf()
    private var manufacturerList: List<Int>? = null

    private val _scanResults = MutableSharedFlow<ScanResult>(extraBufferCapacity = 64)
    val scanResults: SharedFlow<ScanResult> = _scanResults

    data class InfoDevice(
        val address: String,
        val name: String,
        val beacon: String,
        val rssi: Int = 0
    )

    interface BleCallback {
        fun onConnected()
        fun onDisconnected()
        fun onDataReceived(data: ByteArray)
    }

    private var isScanning = false

    private lateinit var logManager: LogDevicesScanned

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            Handler(Looper.getMainLooper()).postDelayed({
                val enabled = bluetoothAdapter?.isEnabled ?: false
                bluetoothCallback?.invoke(enabled)
            }, 100)
        }

    }

    override fun onDestroy() {
        super.onDestroy()

        logManager.flush()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        appContext = context.applicationContext
        logManager = LogDevicesScanned(context.applicationContext)
    }

    /***** Active le bluetooth si besoin *****/
    fun activateBluetooth( // Verifie que le bluetooth et la position GPS soient actives
        onResult: (Boolean) -> Unit
    ) {
        bluetoothCallback = onResult

        if(bluetoothAdapter?.isEnabled ?: false) {
            onResult(true)
            return
        }

        if (neededPermissions.any {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(
                context as Activity,
                neededPermissions,
                1
            )
            return
        }

        val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        bluetoothLauncher?.launch(enableIntent)
    }

    /***** Scan *****/
    fun startScan(timeout: Long? = null, lowLatency: Boolean = true, manufacturerFilter: List<Int>? = null): Boolean {
        if (neededPermissions.any {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(
                context as Activity,
                neededPermissions,
                1
            )
            return false
        }
        if (isScanning) {
            isScanning = false
            manufacturerList = null
            bluetoothLeScanner?.stopScan(leScanCallback)
            return false
        } else {
            isScanning = true
            listDeviceInfo.clear()
            if(manufacturerFilter != null) {
                manufacturerList = manufacturerFilter
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setLegacy(true)
                .build()

            if(lowLatency) {
                bluetoothLeScanner?.startScan(null, settings, leScanCallback)
            }
            else {
                bluetoothLeScanner?.startScan(leScanCallback)
            }

            timeout?.let {
                Handler(Looper.getMainLooper()).postDelayed({
                    isScanning = false
                    bluetoothLeScanner?.stopScan(leScanCallback)
                }, it)
            }
            return true
        }
    }

    /***** Connection *****/
    @OptIn(ExperimentalStdlibApi::class)
    fun connect(address: String, callback: BleCallback) {
        if (neededPermissions.any {
                ActivityCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
            }) {
            ActivityCompat.requestPermissions(context as Activity, neededPermissions, 1)
            return
        }

        val device = bluetoothAdapter?.getRemoteDevice(address)
        bluetoothGatt = device?.connectGatt(context, false, object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    println("Connected to ${gatt.device.address}")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    println("Disconnected from ${gatt.device.address}")
                    callback.onDisconnected()
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                super.onServicesDiscovered(gatt, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    println("Services discovered: ${gatt.services}")

                    val service = gatt.getService(serviceUUID)
                    writeCharacteristic = service?.getCharacteristic(characteristicUUID)

                    // Exemple : on prend la première caractéristique writeable
                    writeCharacteristic?.let {
                        if (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
                            gatt.setCharacteristicNotification(it, true)
                            val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            descriptor?.let { d -> gatt.writeDescriptor(d) }
                        }
                    }

                    callback.onConnected()
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                super.onCharacteristicChanged(gatt, characteristic)
                println("Data received: ${characteristic.value.toHexString()}")
                callback.onDataReceived(characteristic.value)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                println("Data written, status: $status")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun sendData(data: String) {
        writeCharacteristic?.let { char ->
            char.value = hexStringToByteArray(data)
            bluetoothGatt?.writeCharacteristic(char)
        } ?: println("No write characteristic available")
    }

    private fun hexStringToByteArray(hexString: String): ByteArray
    {
        if(hexString.length % 2 != 0) return byteArrayOf()

        val len = hexString.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2)
        {
            data[i / 2] = ((Character.digit(hexString[i], 16) shl 4)
                    + Character.digit(hexString[i + 1], 16)).toByte()
        }
        return data
    }

    @SuppressLint("MissingPermission")
    fun disconnect(callback: BleCallback) {
        bluetoothGatt?.let { gatt ->
            gatt.disconnect()
            gatt.close()
            bluetoothGatt = null
            callback.onDisconnected()
            println("Gatt disconnected and closed")
        }
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result ?: return

            result.scanRecord?.let { scanRecord ->
                val bytes = scanRecord.bytes
                val device = result.device
                val address = device.address
                val name = device.name ?: device.address
                var dataHexString = ""
                val rssi = result.rssi

                try {
                    val parsedBeacon = parseBeaconBytes(bytes)
                    dataHexString = parsedBeacon.firstOrNull() ?: "" // Beacon Readable

                    val device = InfoDevice(
                        address=address,
                        name=name,
                        beacon= dataHexString,
                        rssi=rssi)

                    logManager.addLog(device)

                    _scanResults.tryEmit(result)

                    val index = listDeviceInfo.indexOfFirst{it.address == address}
                    if(index == -1) // Nouveau device scanné
                    {
                        listDeviceInfo.add(device)
                    }
                    else // Déjà scanné
                    {
                        listDeviceInfo[index] = device
                    }

                } catch (_: IndexOutOfBoundsException) {}
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun parseBeaconBytes(beacon: ByteArray): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        val manufacturers = manufacturerList

        while(index < beacon.size) {
            val length = beacon[index].toInt() and 0xFF
            if(length == 0) break

            val typeIndex = index + 1
            if(typeIndex >= beacon.size) break

            val type = beacon[typeIndex].toInt() and 0xFF

            if(type == 0xFF && length >= 3) { // Manufacturer Specific Data
                val dataStart = typeIndex + 1
                val manufacturerId = (beacon[dataStart + 1].toInt() and 0xFF shl 8) or
                            (beacon[dataStart].toInt() and 0xFF)
                if (manufacturers == null || manufacturerId in manufacturers) {
                    val dataEnd = minOf(dataStart + (length - 1), beacon.size)

                    val manufacturerData = beacon.copyOfRange(dataStart + 2, dataEnd)
                    result.add(manufacturerData.toHexString())
                }
            }
            index += length + 1
        }
        return result
    }

    /***** Log Scanned Devices *****/
    fun findDeviceByName(name: String): InfoDevice? {
        return logManager.findDeviceByName(name)
    }

    fun findDeviceByAddress(address: String): InfoDevice? {
        return logManager.findDeviceByAddress(address)
    }

    fun exportToDownloads(context: Context): Boolean {
        return logManager.exportToDownloads(context)
    }

    fun deleteAllLine() {
        logManager.deleteAllLine()
    }

    fun readLog(): List<InfoDevice> {
        return logManager.readLog()
    }
}