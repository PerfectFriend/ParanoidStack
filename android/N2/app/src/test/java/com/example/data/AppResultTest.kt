package com.example.data

import org.junit.Test
import org.junit.Assert.*

class AppResultTest {
    
    @Test
    fun testSuccess() {
        val result: AppResult<String> = "hello".asSuccess()
        assertTrue(result is AppResult.Success)
        assertEquals("hello", (result as AppResult.Success).data)
    }
    
    @Test
    fun testError() {
        val ex = AppException.NetworkException("connection failed")
        val result: AppResult<Nothing> = ex.asError()
        assertTrue(result is AppResult.Error)
        assertEquals("connection failed", (result as AppResult.Error).exception.message)
    }
    
    @Test
    fun testGetOrNull() {
        val success: AppResult<String> = "data".asSuccess()
        assertEquals("data", success.getOrNull())
        assertNull(AppException.StorageException("error").asError().getOrNull())
    }
    
    @Test
    fun testRunCatchingApp() {
        val result = runCatchingApp { "success" }
        assertTrue(result is AppResult.Success)
        assertEquals("success", (result as AppResult.Success).data)
        
        val error = runCatchingApp { throw AppException.ProtocolException("protocol error") }
        assertTrue(error is AppResult.Error)
    }
    
    @Test
    fun testExceptionHierarchy() {
        assertTrue(AppException.NetworkException("") is AppException)
        assertTrue(AppException.CryptoException("") is AppException)
        assertTrue(AppException.ProtocolException("") is AppException)
        assertTrue(AppException.StorageException("") is AppException)
        assertTrue(AppException.TimeoutException() is AppException)
        assertTrue(AppException.NotFoundException() is AppException)
        assertTrue(AppException.UnauthorizedException() is AppException)
    }
}
