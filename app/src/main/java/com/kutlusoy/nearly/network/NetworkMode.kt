package com.kutlusoy.nearly.network

enum class NetworkMode {
    Tor,
    I2p,
    TorAndI2p,
    Direct,
    ;

    val usesTor: Boolean get() = this == Tor || this == TorAndI2p
    val usesI2p: Boolean get() = this == I2p || this == TorAndI2p
}

data class NetworkSettings(
    val mode: NetworkMode = NetworkMode.Tor,
    val i2pIpv6: Boolean = true,
)
