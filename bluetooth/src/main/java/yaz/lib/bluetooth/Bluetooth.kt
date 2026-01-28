/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * Fichier : LogDevicesScanned.kt
 * Module : bluetooth
 *
 * Description :
 *      Expose les fonctions des fragments et des logs a l utilisateur
 *
 * Auteur : @YazTarkann
 * Créé le : 22/01/2026
 * Dernière modification : 23/01/2026
 */

package yaz.lib.bluetooth

import android.bluetooth.le.ScanResult
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.Flow

object Bluetooth {

    /***** Bluetooth *****/
    private fun FragmentActivity.bluetooth(): HiddenFragBluetooth {
        var fragment = supportFragmentManager.findFragmentByTag("hiddenFragBluetooth") as? HiddenFragBluetooth
        if (fragment == null) {
            fragment = HiddenFragBluetooth()
            supportFragmentManager.beginTransaction()
                .add(fragment, "hiddenFragBluetooth")
                .commitNow()
        }
        return fragment
    }

    fun checkBluetooth(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        activity.bluetooth().activateBluetooth { bluetoothEnabled ->
            onResult(bluetoothEnabled)
        }
    }

    fun startScan(activity: FragmentActivity, timeout: Long? = null, lowLatency: Boolean = true, manufacturerFilter: List<Int>? = null): Boolean {
        return activity.bluetooth().startScan(timeout, lowLatency, manufacturerFilter)
    }

    fun connect(activity: FragmentActivity, address: String) {
        if(activity is HiddenFragBluetooth.BleCallback) {
            activity.bluetooth().connect(address, activity)
        } else {
            println("Activity must implement BleCallback interface")
        }
    }

    fun sendData(activity: FragmentActivity, data: String) {
        activity.bluetooth().sendData(data)
    }

    fun disconnect(activity: FragmentActivity) {
        if(activity is HiddenFragBluetooth.BleCallback) {
            activity.bluetooth().disconnect(activity)
        } else {
            println("Activity must implement BleCallback interface")
        }
    }

    fun realTimeBeacon(activity: FragmentActivity): Flow<ScanResult> {
        return activity.bluetooth().scanResults
    }

    fun findDeviceByName(activity: FragmentActivity, name: String): HiddenFragBluetooth.InfoDevice? {
        return activity.bluetooth().findDeviceByName(name)
    }

    fun findDeviceByAddress(activity: FragmentActivity, address: String): HiddenFragBluetooth.InfoDevice? {
        return activity.bluetooth().findDeviceByName(address)
    }

    fun exportLogsToDownloads(activity: FragmentActivity): Boolean {
        return activity.bluetooth().exportToDownloads(activity)
    }

    fun deleteAllLogs(activity: FragmentActivity) {
        activity.bluetooth().deleteAllLine()
    }

    fun readLogs(activity: FragmentActivity): List<HiddenFragBluetooth.InfoDevice> {
        return activity.bluetooth().readLog()
    }

    /***** GPS *****/
    private fun FragmentActivity.gps(): HiddenFragGPS {
        var fragment = supportFragmentManager.findFragmentByTag("hiddenFragGPS") as? HiddenFragGPS
        if (fragment == null) {
            fragment = HiddenFragGPS()
            supportFragmentManager.beginTransaction()
                .add(fragment, "hiddenFragGPS")
                .commitNow()
        }
        return fragment
    }

    fun checkGPS(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        activity.gps().activateLocation { gpsEnabled ->
            onResult(gpsEnabled)
        }
    }
}