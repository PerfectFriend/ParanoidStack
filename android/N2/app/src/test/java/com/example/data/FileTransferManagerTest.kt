package com.example.data

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileTransferManagerTest {
    
    @Test
    fun testFileTransferProgress() {
        val progress = FileTransferManager.FileTransferProgress(
            fileName = "test.txt",
            totalBytes = 1000,
            transferredBytes = 500,
            isComplete = false
        )
        assertEquals("test.txt", progress.fileName)
        assertEquals(1000L, progress.totalBytes)
        assertEquals(500L, progress.transferredBytes)
        assertFalse(progress.isComplete)
    }
    
    @Test
    fun testFileTransferProgressComplete() {
        val progress = FileTransferManager.FileTransferProgress(
            fileName = "done.txt",
            totalBytes = 500,
            transferredBytes = 500,
            isComplete = true
        )
        assertTrue(progress.isComplete)
    }
    
    @Test
    fun testFileToSend() {
        val file = FileTransferManager.FileToSend(
            uri = android.net.Uri.parse("content://test/file.txt"),
            fileName = "file.txt",
            mimeType = "text/plain",
            size = 1024
        )
        assertEquals("file.txt", file.fileName)
        assertEquals("text/plain", file.mimeType)
        assertEquals(1024L, file.size)
    }
}
