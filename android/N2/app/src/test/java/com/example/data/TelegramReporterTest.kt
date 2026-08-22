package com.example.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Test
import org.junit.Assert.*

class TelegramReporterTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    @Test
    fun testDisabledWhenNoToken() {
        val reporter = TelegramReporter(
            botToken = "",
            chatId = "",
            scope = scope
        )
        assertFalse(reporter.isEnabled())
    }

    @Test
    fun testDisabledWhenOnlyToken() {
        val reporter = TelegramReporter(
            botToken = "token:abc",
            chatId = "",
            scope = scope
        )
        assertFalse(reporter.isEnabled())
    }

    @Test
    fun testEnabledWithTokenAndChatId() {
        val reporter = TelegramReporter(
            botToken = "token:abc",
            chatId = "123",
            scope = scope
        )
        assertTrue(reporter.isEnabled())
    }

    @Test
    fun testUpdateConfigEnables() {
        val reporter = TelegramReporter(
            botToken = "",
            chatId = "",
            scope = scope
        )
        assertFalse(reporter.isEnabled())
        reporter.updateConfig("token:abc", "123")
        assertTrue(reporter.isEnabled())
    }

    @Test
    fun testUpdateConfigDisables() {
        val reporter = TelegramReporter(
            botToken = "token:abc",
            chatId = "123",
            scope = scope
        )
        assertTrue(reporter.isEnabled())
        reporter.updateConfig("", "")
        assertFalse(reporter.isEnabled())
    }

    @Test
    fun testReportWhenDisabledDoesNothing() {
        val reporter = TelegramReporter(
            botToken = "",
            chatId = "",
            scope = scope
        )
        reporter.report("test message")
    }

    @Test
    fun testReportNowWhenDisabledDoesNothing() {
        val reporter = TelegramReporter(
            botToken = "",
            chatId = "",
            scope = scope
        )
        reporter.reportNow("test message")
    }
}
