package com.example.data

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])

class WebDavBackupTest {

    @Test
    fun testDefaultConfig() {
        val config = WebDavBackup.WebDavConfig()
        assertEquals("", config.serverUrl)
        assertEquals("", config.username)
        assertEquals("", config.password)
        assertFalse(config.enabled)
    }

    @Test
    fun testCustomConfig() {
        val config = WebDavBackup.WebDavConfig(
            serverUrl = "https://dav.example.com/remote.php/dav/files/user/",
            username = "user",
            password = "pass",
            enabled = true
        )
        assertEquals("https://dav.example.com/remote.php/dav/files/user/", config.serverUrl)
        assertEquals("user", config.username)
        assertEquals("pass", config.password)
        assertTrue(config.enabled)
    }

    @Test
    fun testUploadBackupReturnsFalseOnBadUrl() {
        val backup = WebDavBackup(
            serverUrl = "not-a-valid-url",
            username = "test",
            password = "test"
        )
        assertFalse(backup.uploadBackup("hello".encodeToByteArray(), "test.enc"))
    }

    @Test
    fun testDownloadBackupReturnsNullOnBadUrl() {
        val backup = WebDavBackup(
            serverUrl = "not-a-valid-url",
            username = "test",
            password = "test"
        )
        assertNull(backup.downloadBackup("test.enc"))
    }

    @Test
    fun testTestConnectionReturnsFalseOnBadUrl() {
        assertFalse(WebDavBackup.testConnection("not-a-url", "u", "p"))
    }

    @Test
    fun testUploadBackupUrlConstruction() {
        val backup = WebDavBackup(
            serverUrl = "https://dav.example.com/backup",
            username = "alice",
            password = "secret"
        )
        assertFalse(backup.uploadBackup("data".encodeToByteArray(), "n2_backup.enc"))
    }
}
