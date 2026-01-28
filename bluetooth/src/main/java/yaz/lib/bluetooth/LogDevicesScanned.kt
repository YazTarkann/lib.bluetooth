/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * Fichier : LogDevicesScanned.kt
 * Module : bluetooth
 *
 * Description :
 *      Gere les logs des devices scannes.
 *
 * Auteur : @YazTarkann
 * Créé le : 22/01/2026
 * Dernière modification : 23/01/2026
 */

package yaz.lib.bluetooth

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

class LogDevicesScanned(
    context: Context,
    private val flushIntervalMs: Long = 5000, // flush toutes les 5s
    private val bufferSize: Int = 50          // flush si > 50 logs
) {

    private var logFileName = "ScannedDevices.txt"
    private val logFile: File = File(context.filesDir, logFileName)
    private var maxLines = 3000

    private val logs = mutableListOf<HiddenFragBluetooth.InfoDevice>()
    private val loggedDevices = mutableSetOf<String>()
    private val deviceMapName = mutableMapOf<String, HiddenFragBluetooth.InfoDevice>()
    private val deviceMapAddress = mutableMapOf<String, HiddenFragBluetooth.InfoDevice>()

    private val buffer = mutableListOf<HiddenFragBluetooth.InfoDevice>()
    private var lastFlushTime = System.currentTimeMillis()

    init {
        loadLogs()
    }

    private fun serialize(info: HiddenFragBluetooth.InfoDevice): String {
        return "${info.address};${info.name};${info.beacon};${info.rssi}"
    }

    private fun deserialize(line: String): HiddenFragBluetooth.InfoDevice? {
        val parts = line.split(";")
        if (parts.size != 4) return null
        return HiddenFragBluetooth.InfoDevice(
            address = parts[0],
            name = parts[1],
            beacon = parts[2],
            rssi = parts[3].toIntOrNull() ?: 0
        )
    }

    private fun loadLogs() {
        if (!logFile.exists()) return

        logFile.readLines().forEach { line ->
            deserialize(line)?.let { info ->
                logs.add(info)
                loggedDevices.add(serialize(info))
                deviceMapName[info.name] = info
                deviceMapAddress[info.address] = info
            }
        }
    }

    fun findDeviceByName(name: String): HiddenFragBluetooth.InfoDevice? {
        return deviceMapName[name]
    }

    fun findDeviceByAddress(address: String): HiddenFragBluetooth.InfoDevice? {
        return deviceMapAddress[address]
    }

    fun addLog(info: HiddenFragBluetooth.InfoDevice) = synchronized(this) {
        val message = serialize(info)

        if (loggedDevices.contains(message)) return

        logs.add(info)
        loggedDevices.add(message)
        deviceMapName[info.name] = info
        deviceMapAddress[info.address] = info

        buffer.add(info)

        // Limite maxLines
        while (logs.size > maxLines) {
            val removed = logs.removeAt(0)
            loggedDevices.remove(serialize(removed))
            deviceMapName.remove(removed.name)
            deviceMapAddress.remove(removed.address)
        }

        val now = System.currentTimeMillis()
        if (now - lastFlushTime > flushIntervalMs || buffer.size >= bufferSize) {
            flush()
        }
    }

    fun flush() = synchronized(this) {
        if (buffer.isEmpty()) return

        logFile.appendText(buffer.joinToString("\n") { serialize(it) } + "\n")
        Log.e("DEBUG", "flush : ${logFile.readLines().size}")
        buffer.clear()
        lastFlushTime = System.currentTimeMillis()
    }

    fun readLog(): List<HiddenFragBluetooth.InfoDevice> {
        return logs.toList() // renvoie copie sûre
    }

    fun deleteAllLine() {
        logFile.writeText("")
    }

    fun exportToDownloads(context: Context): Boolean {
        if (!logFile.exists()) return false

        return runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val downloadDir =
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )

                val outputFile = File(downloadDir, logFileName)
                logFile.copyTo(outputFile, overwrite = true)
            } else {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, logFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Failed to create download URI")

                resolver.openOutputStream(uri)?.use { outputStream ->
                    logFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: error("Failed to open output stream")
            }
        }.isSuccess
    }
}