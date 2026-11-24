package com.kutlusoy.nearly.discovery

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.kutlusoy.nearly.nostr.Geohash

// The approximate place of the phone as a cell, 5 characters (about 5 km) for the card, 6 (about 1 km) for the presence. The coordinates are not kept and not sent.
class AndroidAreaSource(private val context: Context) : AreaSource {
    override fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override fun current(length: Int, onResult: (String?) -> Unit) {
        if (!hasPermission()) return onResult(null)
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return onResult(null)
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        val last = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null) return onResult(cell(last, length))
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { it in providers }
        if (provider == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return onResult(null)
        runCatching {
            manager.getCurrentLocation(provider, null, context.mainExecutor) { location -> onResult(location?.let { cell(it, length) }) }
        }.onFailure { onResult(null) }
    }

    private fun cell(location: Location, length: Int) = Geohash.encode(location.latitude, location.longitude, length)
}
