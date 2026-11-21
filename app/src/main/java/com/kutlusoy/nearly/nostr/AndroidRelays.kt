package com.kutlusoy.nearly.nostr

import android.content.Context

object AndroidRelays {
    // relays.txt of the repository root is copied into the assets by the build
    fun load(context: Context): List<String> =
        RelayList.parse(context.assets.open("relays.txt").bufferedReader().use { it.readText() })
}
