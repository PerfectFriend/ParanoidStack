package com.example.service

import org.junit.Test
import org.junit.Assert.*
import java.io.File

class CrashLogHandlerTest {
    
    @Test
    fun testCrashLogContent() {
        val error = RuntimeException("Test crash")
        val sw = java.io.StringWriter()
        error.printStackTrace(java.io.PrintWriter(sw))
        val trace = sw.toString()
        
        assertTrue(trace.contains("RuntimeException"))
        assertTrue(trace.contains("Test crash"))
    }
}
