package com.paranoidx.sdk.security

/** Platform-agnostic logger for PX SDK. Set [impl] to route to Android Log, etc. */
object SdkLogger {
    var impl: Logger = PrintLogger
    fun v(tag: String, msg: String) { impl.v(tag, msg) }
    fun d(tag: String, msg: String) { impl.d(tag, msg) }
    fun i(tag: String, msg: String) { impl.i(tag, msg) }
    fun w(tag: String, msg: String) { impl.w(tag, msg) }
    fun e(tag: String, msg: String, e: Throwable? = null) { impl.e(tag, msg, e) }
}

interface Logger {
    fun v(tag: String, msg: String)
    fun d(tag: String, msg: String)
    fun i(tag: String, msg: String)
    fun w(tag: String, msg: String)
    fun e(tag: String, msg: String, e: Throwable? = null)
}

object PrintLogger : Logger {
    override fun v(tag: String, msg: String) { println("VERB/$tag: $msg") }
    override fun d(tag: String, msg: String) { println("DEBUG/$tag: $msg") }
    override fun i(tag: String, msg: String) { println("INFO/$tag: $msg") }
    override fun w(tag: String, msg: String) { println("WARN/$tag: $msg") }
    override fun e(tag: String, msg: String, e: Throwable?) {
        println("ERROR/$tag: $msg")
        e?.printStackTrace()
    }
}
