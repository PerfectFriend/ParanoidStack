package com.example.ui.screens.network

import org.junit.Test
import org.junit.Assert.*

class NetworkPanelTest {
    
    @Test
    fun testNetworkStatsDefaults() {
        val stats = com.example.data.BandwidthMonitor.BandwidthStats(
            totalSentMB = 0.0,
            totalReceivedMB = 0.0,
            uploadSpeedKbps = 0.0,
            downloadSpeedKbps = 0.0,
            sessionDurationSec = 0
        )
        assertEquals(0.0, stats.totalSentMB, 0.001)
        assertEquals(0.0, stats.totalReceivedMB, 0.001)
    }
}
