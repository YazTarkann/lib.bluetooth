/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * Fichier : LogDevicesScanned.kt
 * Module : bluetooth
 *
 * Description :
 *      Gere l activation du gps.
 *
 * Auteur : @YazTarkann
 * Créé le : 22/01/2026
 * Dernière modification : 22/01/2026
 */

package yaz.lib.bluetooth

import android.content.Context.LOCATION_SERVICE
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

class HiddenFragGPS : Fragment() {
    private var launcherGPS : ActivityResultLauncher<IntentSenderRequest>? = null
    private var gpsCallback: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        launcherGPS = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) {
            Handler(Looper.getMainLooper()).postDelayed({
                val lm = requireContext().getSystemService(LOCATION_SERVICE) as LocationManager
                val enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)

                gpsCallback?.invoke(enabled)
            }, 100)
        }
    }

    fun activateLocation(
        onResult: (Boolean) -> Unit
    ) {
        gpsCallback = onResult

        launcherGPS?.let { launcher ->
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000
            ).build()

            val settingsRequest = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .setAlwaysShow(true)
                .build()

            val client = LocationServices.getSettingsClient(requireContext())

            client.checkLocationSettings(settingsRequest)
                .addOnSuccessListener {
                    onResult(true)
                }
                .addOnFailureListener { e ->
                    if (e is ResolvableApiException) {
                        val intentSenderRequest = IntentSenderRequest.Builder(e.resolution).build()
                        launcher.launch(intentSenderRequest)
                    } else {
                        onResult(false)
                    }
                }
        }
    }
}