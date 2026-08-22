package com.n3.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import android.webkit.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.zxing.integration.android.IntentIntegrator
import com.n3.app.audit.AuditLogManager
import com.n3.app.bridge.BridgeConfig
import com.n3.app.bridge.BridgeOrchestrator
import com.n3.app.bridges.*
import com.n3.app.crypto.IdentityManager
import com.n3.app.crypto.N3Crypto
import com.n3.app.profile.ProfileManager
import com.n3.app.security.SecurityManager
import com.n3.app.services.SmpService
import com.n3.app.services.TorService
import com.n3.app.util.LocalizedStrings
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    companion object { private const val TAG = "N3/Main" }

    private lateinit var webView: WebView
    private var torService: TorService? = null
    private var smpService: SmpService? = null
    private var torBound = false
    private var smpBound = false
    private lateinit var profileManager: ProfileManager
    private lateinit var bridgeConfig: BridgeConfig
    private lateinit var bridgeOrch: BridgeOrchestrator
    private var bridgeBridge: BridgeBridge? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var bootComplete = false
    private var appLocked = false
    private lateinit var securityManager: SecurityManager
    private lateinit var auditLog: AuditLogManager
    private var cameraCallback: String = ""

    private val qrScanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val intentResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        if (intentResult != null && intentResult.contents != null) {
            val safe = intentResult.contents.replace("\\", "\\\\").replace("'", "\\'")
            evalJs("window.$cameraCallback && window.$cameraCallback('$safe')")
        } else {
            evalJs("window.$cameraCallback && window.$cameraCallback('')")
        }
        cameraCallback = ""
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (perm, granted) ->
            Log.i(TAG, "Permission $perm: $granted")
        }
        evalJs("window.dispatchEvent(new Event('permissionsResult'))")
    }

    private val torConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            torService = (binder as TorService.TorBinder).getService()
            torBound = true
            Log.i(TAG, "TorService connected")
            auditLog.record("TOR_CONNECT", "tor", "SOCKS5 connected")
            evalJs("window.onTorReady()")
        }
        override fun onServiceDisconnected(name: ComponentName) {
            torBound = false
            auditLog.record("TOR_DISCONNECT", "tor", "Service disconnected")
        }
    }

    private val smpConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            smpService = (binder as SmpService.SmpBinder).getService()
            smpBound = true
            Log.i(TAG, "SmpService connected")
            auditLog.record("SMP_CONNECT", "smp", "Connected")
            smpService?.messageListeners?.add { json ->
                runOnUiThread {
                    val safe = json.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
                    evalJs("window.onSmpMessage && window.onSmpMessage('$safe')")
                    auditLog.record("MESSAGE_RECEIVED", "smp", json.take(60))
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            smpBound = false
            auditLog.record("SMP_DISCONNECT", "smp", "Service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.setDecorFitsSystemWindows(false)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)

        profileManager = ProfileManager(this)
        bridgeConfig = BridgeConfig(this)
        bridgeOrch = BridgeOrchestrator(this)
        N3Crypto.init(this)
        securityManager = SecurityManager(this)
        auditLog = AuditLogManager(this)

        configureWebView()
        addBridges()
        loadApp()
        requestPermissions()
        startServices()
        checkAndBoot()

        intent?.data?.let { handleDeepLink(it) }
    }

    private fun checkAndBoot() {
        if (profileManager.hasProfile() && profileManager.isVerified()) {
            IdentityManager.initFromProfile(profileManager)
            evalJs("window.onProfileReady && window.onProfileReady()")
            scope.launch { bootSequence() }
        } else if (profileManager.hasProfile() && !profileManager.isVerified()) {
            evalJs("window.showVerification && window.showVerification()")
        }
    }

    fun startBoot() { scope.launch { bootSequence() } }

    private suspend fun bootSequence() = withContext(Dispatchers.IO) {
        evalJs("window.onBootProgress && window.onBootProgress('checking')")
        val net = bridgeOrch.checkNetwork()
        auditLog.record("NETWORK_CHECK", "system", "IPv4:${net.ipv4} IPv6:${net.ipv6} DNS:${net.dnsWorks} Tor:${net.torReachable} Internet:${net.internetReachable}")
        evalJs("window.onBootProgress && window.onBootProgress('checking_ok')")

        val bridgeList = bridgeConfig.getAll().filter { it.enabled }
        if (bridgeList.isNotEmpty()) {
            evalJs("window.onBootProgress && window.onBootProgress('bridges')")
            val results = bridgeOrch.buildAndTestChain(bridgeList)
            val ok = results.any { it.ok }
            evalJs("window.onBootProgress && window.onBootProgress(${if (ok) "'bridges_ok'" else "'bridges_fail'"})")
        }

        evalJs("window.onBootProgress && window.onBootProgress('simplex')")
        val smpOk = try {
            val proxy = java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", 9050))
            val conn = java.net.URL("http://smp.simplex.chat:5223").openConnection(proxy) as java.net.HttpURLConnection
            conn.connectTimeout = 10000; conn.readTimeout = 5000; conn.responseCode in 200..399
        } catch (e: Exception) { false }

        bootComplete = true
        auditLog.record("BOOT", "system", "Boot complete, SMP: $smpOk, bridges: ${bridgeList.size}")
        evalJs("window.onBootComplete && window.onBootComplete($smpOk)")
        if (securityManager.isBiometricLockEnabled()) { appLocked = true; biometricLockCheck() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = "N3/1.0 (Android)"
        }
        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return when {
                    url.startsWith("n3://") -> { handleDeepLink(request.url); true }
                    url.startsWith("file://") -> true
                    else -> false
                }
            }
            override fun onPageFinished(view: WebView, url: String) { Log.i(TAG, "Loaded: $url") }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val granted = request.resources.filter {
                        (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE &&
                         ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) ||
                        (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE &&
                         ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                    }.toTypedArray()
                    if (granted.isNotEmpty()) request.grant(granted) else request.deny()
                }
            }
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                Log.println(when (msg.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
                    ConsoleMessage.MessageLevel.WARNING -> Log.WARN
                    else -> Log.DEBUG
                }, "N3/JS", "${msg.message()} [${msg.sourceId()}:${msg.lineNumber()}]")
                return true
            }
        }
    }

    private fun addBridges() {
        bridgeBridge = BridgeBridge(this) { msg -> evalJs("window.onBridgeProgress && window.onBridgeProgress('$msg')") }
        webView.addJavascriptInterface(TorBridge(this), "AndroidTor")
        webView.addJavascriptInterface(CryptoBridge(), "AndroidCrypto")
        webView.addJavascriptInterface(KeystoreBridge(), "AndroidKeystore")
        webView.addJavascriptInterface(ClipboardBridge(this), "AndroidClipboard")
        webView.addJavascriptInterface(SystemBridge(this), "AndroidSystem")
        webView.addJavascriptInterface(BiometricBridge(this), "AndroidBiometric")
        webView.addJavascriptInterface(AndroidBridge(this), "Android")
        webView.addJavascriptInterface(ProfileBridge(this), "AndroidProfile")
        bridgeBridge?.let { webView.addJavascriptInterface(it, "AndroidBridge") }
        webView.addJavascriptInterface(ContactBridge(this), "AndroidContacts")
        webView.addJavascriptInterface(StorageBridge(this), "AndroidStorage")
        webView.addJavascriptInterface(SecurityBridge(this), "AndroidSecurity")
        webView.addJavascriptInterface(AuditBridge(this), "AndroidAudit")
        webView.addJavascriptInterface(VoiceBridge(this), "AndroidVoice")
        webView.addJavascriptInterface(CameraBridge { callback ->
            cameraCallback = callback
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                qrScanLauncher.launch(IntentIntegrator(this@MainActivity).createScanIntent())
            } else {
                permLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            }
        }, "AndroidCamera")

        if (profileManager.hasProfile() && profileManager.isVerified()) {
            IdentityManager.initFromProfile(profileManager)
        }
    }

    private fun loadApp() { webView.loadUrl("file:///android_asset/public/index.html") }

    private fun requestPermissions() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val toRequest = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) permLauncher.launch(toRequest.toTypedArray())
    }

    private fun startServices() {
        startForegroundService(Intent(this, TorService::class.java))
        bindService(Intent(this, TorService::class.java), torConnection, BIND_AUTO_CREATE)
    }

    fun connectSmp(host: String, port: Int) {
        val intent = Intent(this, SmpService::class.java).apply {
            putExtra("host", host); putExtra("port", port)
        }
        startForegroundService(intent)
        bindService(intent, smpConnection, BIND_AUTO_CREATE)
    }

    fun sendSmp(json: String) {
        smpService?.send(json)
        auditLog.record("MESSAGE_SENT", "smp", json.take(60))
    }

    private fun handleDeepLink(uri: Uri) {
        Log.i(TAG, "Deep link: $uri")
        val safe = uri.toString().replace("\\", "\\\\").replace("'", "\\'")
        evalJs("window.handleDeepLink && window.handleDeepLink('$safe')")
    }

    fun evalJs(js: String) { runOnUiThread { webView.evaluateJavascript(js, null) } }

    fun isUnlocked(): Boolean = !appLocked

    fun unlock() {
        appLocked = false
        auditLog.record("UNLOCK", "user", "Biometric unlock")
        evalJs("window.onAppUnlock && window.onAppUnlock()")
    }

    private fun biometricLockCheck() {
        if (!securityManager.isBiometricLockEnabled() || !bootComplete || !appLocked) return
        val mgr = androidx.biometric.BiometricManager.from(this)
        if (mgr.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) { appLocked = false; return }
        androidx.biometric.BiometricPrompt(this,
            androidx.core.content.ContextCompat.getMainExecutor(this),
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: androidx.biometric.BiometricPrompt.AuthenticationResult) { unlock() }
                override fun onAuthenticationError(code: Int, msg: CharSequence) { if (code == 13) unlock() }
            }).authenticate(androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle("N3 Locked").setSubtitle("Authenticate to unlock")
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                    or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL).build())
    }

    override fun onResume() {
        super.onResume()
        biometricLockCheck()
    }

    override fun onPause() {
        super.onPause()
        if (bootComplete && securityManager.isBiometricLockEnabled()) {
            appLocked = true
            auditLog.record("LOCK", "system", "App backgrounded")
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { handleDeepLink(it) }
    }

    override fun onDestroy() {
        scope.cancel()
        bridgeBridge?.destroy()
        if (torBound) { unbindService(torConnection); torBound = false }
        if (smpBound) { unbindService(smpConnection); smpBound = false }
        webView.destroy()
        super.onDestroy()
    }
}
