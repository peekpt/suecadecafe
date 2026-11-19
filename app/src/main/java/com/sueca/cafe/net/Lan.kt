// Endereço IPv4 da máquina na rede local — é este o endereço que o anfitrião
// mostra aos outros jogadores.
package com.sueca.cafe.net

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object Lan {
    // wlan = Wi-Fi do cliente; ap0/swlan0 = hotspot criado pelo próprio telemóvel.
    private val PREFERRED = listOf("wlan", "ap0", "swlan0", "eth", "rndis")

    fun ipv4(): String {
        val best = ArrayList<Pair<Int, String>>()
        val ifaces = try {
            NetworkInterface.getNetworkInterfaces()
        } catch (e: Exception) {
            null
        } ?: return "127.0.0.1"
        for (nif in Collections.list(ifaces)) {
            try {
                if (!nif.isUp || nif.isLoopback) continue
                val name = nif.name ?: ""
                val rank = PREFERRED.indexOfFirst { name.startsWith(it) }
                    .let { if (it < 0) PREFERRED.size else it }
                for (addr in Collections.list(nif.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        (addr.hostAddress ?: "").let { if (it.isNotEmpty()) best.add(rank to it) }
                        break
                    }
                }
            } catch (e: Exception) {
                // uma interface que não responde não pára o resto da lista
            }
        }
        return best.minByOrNull { it.first }?.second ?: "127.0.0.1"
    }
}
