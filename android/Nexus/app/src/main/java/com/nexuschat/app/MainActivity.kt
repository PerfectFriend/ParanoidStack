package com.nexuschat.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.nexuschat.app.bridges.*
import com.nexuschat.app.services.*
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "NexusChat/Main"
    }

    private lateinit var webView: WebView
    private var torService: TorService? = null
    private var smpService: SmpServerService? = null
    private var v2rayService: V2RayService? = null
    private var torBound = false
    private var smpBound = false
    private var v2rayBound = false
    private var transportManager: TransportManager? = null
    private var recoveryManager: ErrorRecoveryManager? = null
    private var coverTraffic: CoverTrafficScheduler? = null
    private var dnsOverTor: DnsOverTor? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val details = mutableMapOf<String, Boolean>()
        results.forEach { (perm, granted) ->
            Log.i(TAG, "Permission $perm: $granted")
            val key = perm.substringAfterLast('.')
            details[key] = granted
        }
        val json = com.google.gson.Gson().toJson(details)
        webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('permissionsResult',{detail:$json}))", null)
    }

    private var filePickerCallback: ValueCallback<Array<Uri>>? = null
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        filePickerCallback?.onReceiveValue(uris.toTypedArray())
        filePickerCallback = null
    }

    private val torConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            torService = (binder as TorService.TorBinder).getService()
            torBound = true
            Log.i(TAG, "TorService connected")
            recoveryManager?.reportSuccess(ErrorRecoveryManager.ServiceType.TOR)
            webView.evaluateJavascript(
                "window.nativeTorReady && window.nativeTorReady(${torService?.getSocksPort()})", null
            )
        }
        override fun onServiceDisconnected(name: ComponentName) {
            torBound = false
            recoveryManager?.reportFailure(ErrorRecoveryManager.ServiceType.TOR, "service disconnected")
        }
    }

    private val smpConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            smpService = (binder as SmpServerService.SmpBinder).getService()
            smpBound = true
            Log.i(TAG, "SmpServerService connected")
            recoveryManager?.reportSuccess(ErrorRecoveryManager.ServiceType.SMP)
            onSmpBound()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            smpBound = false
            recoveryManager?.reportFailure(ErrorRecoveryManager.ServiceType.SMP, "service disconnected")
        }
    }

    private val v2rayConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            v2rayService = (binder as V2RayService.V2RayBinder).getService()
            v2rayBound = true
            Log.i(TAG, "V2RayService connected")
            recoveryManager?.reportSuccess(ErrorRecoveryManager.ServiceType.V2RAY)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            v2rayBound = false
            recoveryManager?.reportFailure(ErrorRecoveryManager.ServiceType.V2RAY, "service disconnected")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)

        initInfrastructure()
        configureWebView()
        addJavaScriptBridges()
        loadApp()
        requestPermissions()
        startAllServices()

        intent?.data?.let { handleDeepLink(it) }
    }

    private fun initInfrastructure() {
        transportManager = TransportManager.getInstance(this)
        transportManager?.start()

        recoveryManager = ErrorRecoveryManager.getInstance()
        ErrorRecoveryManager.ServiceType.values().forEach { recoveryManager?.registerService(it) }
        recoveryManager?.setRecoveryHandler { serviceType ->
            runOnUiThread { restartService(serviceType) }
        }

        dnsOverTor = DnsOverTor.getInstance()
        dnsOverTor?.start()

        val orch = BridgeOrchestrator.getInstance(this)
        orch.addBridge(BridgeOrchestrator.BridgeConfig(
            protocol = BridgeOrchestrator.BridgeProtocol.OBFS4,
            address = "85.31.186.98", port = 443,
            fingerprint = "D2B4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8",
            transportPlugin = "obfs4proxy",
            localListenPort = 9443,
            args = mapOf("cert" to "F5CB4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8", "iat-mode" to "0")
        ))
        orch.addBridge(BridgeOrchestrator.BridgeConfig(
            protocol = BridgeOrchestrator.BridgeProtocol.MEEK,
            address = "meek.azureedge.net", port = 443,
            transportPlugin = "meek-client",
            localListenPort = 9555
        ))
        orch.addBridge(BridgeOrchestrator.BridgeConfig(
            protocol = BridgeOrchestrator.BridgeProtocol.DOMAIN_FRONT,
            address = "www.google.com", port = 443,
            localListenPort = 443
        ))
        orch.setStatusListener { protocol, available ->
            Log.i(TAG, "Bridge ${protocol.name}: ${if (available) "available" else "unavailable"}")
        }
        orch.start()
        transportManager?.setBridgeOrchestrator(orch)

        coverTraffic = CoverTrafficScheduler.getInstance()
        coverTraffic?.setPacketHandler { data ->
            transportManager?.recordBytesSent(transportManager?.activeTransport ?: TransportType.TOR, data.size.toLong())
            scope.launch {
                try {
                    val (client, _) = transportManager?.getClient(10) ?: return@launch
                    val req = okhttp3.Request.Builder()
                        .url("https://check.torproject.org/api/ip")
                        .header("Cache-Control", "no-cache")
                        .method("GET", null)
                        .build()
                    client.newCall(req).enqueue(object : okhttp3.Callback {
                        override fun onResponse(call: okhttp3.Call, resp: okhttp3.Response) {
                            resp.close()
                        }
                        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
                    })
                } catch (_: Exception) {}
            }
        }
        TrafficPadding.getInstance()
        ProtocolObfuscator.getInstance()
        coverTraffic?.start()

        Log.i(TAG, "All infrastructure initialized: BridgeOrchestrator, TransportManager, ErrorRecovery, DNS, CoverTraffic")
    }

    private fun restartService(serviceType: ErrorRecoveryManager.ServiceType) {
        Log.i(TAG, "Recovery restarting: $serviceType")
        when (serviceType) {
            ErrorRecoveryManager.ServiceType.TOR -> {
                if (!torBound) startTorService()
            }
            ErrorRecoveryManager.ServiceType.V2RAY -> {
                if (!v2rayBound) startV2RayService()
            }
            ErrorRecoveryManager.ServiceType.XRAY -> {
                XRaySubprocess.getInstance().start(XRaySubprocess.XRayConfig())
            }
            ErrorRecoveryManager.ServiceType.SMP -> {
                if (!smpBound) startSmpService()
            }
            ErrorRecoveryManager.ServiceType.SNOWFLAKE -> {}
            ErrorRecoveryManager.ServiceType.CHAIN_PROXY -> {
                ChainProxy.getInstance().start()
            }
            ErrorRecoveryManager.ServiceType.WIREGUARD -> {
                val vpnIntent = Intent(this, NexusVpnService::class.java).apply {
                    putExtra("action", "start")
                }
                ContextCompat.startForegroundService(this, vpnIntent)
            }
            ErrorRecoveryManager.ServiceType.DNS -> {
                DnsOverTor.getInstance().start()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            userAgentString = "NexusChat/1.0 (Android; Privacy)"
        }

        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return when {
                    url.startsWith("simplex://") -> { handleDeepLink(request.url); true }
                    url.startsWith("file://") -> true
                    url.startsWith("javascript:") -> true
                    else -> false
                }
            }
            override fun onPageFinished(view: WebView, url: String) {
                Log.i(TAG, "WebView loaded: $url")
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest,
                                         error: WebResourceError) {
                Log.e(TAG, "WebView error: ${error.description} for ${request.url}")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val granted = request.resources.filter { res ->
                        res == PermissionRequest.RESOURCE_AUDIO_CAPTURE &&
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                    }.toTypedArray()
                    if (granted.isNotEmpty()) request.grant(granted)
                    else request.deny()
                }
            }

            override fun onShowFileChooser(webView: WebView,
                                           filePathCallback: ValueCallback<Array<Uri>>,
                                           fileChooserParams: FileChooserParams): Boolean {
                filePickerCallback?.onReceiveValue(null)
                filePickerCallback = filePathCallback
                filePickerLauncher.launch("*/*")
                return true
            }

            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                val level = when (msg.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
                    ConsoleMessage.MessageLevel.WARNING -> Log.WARN
                    else -> Log.DEBUG
                }
                Log.println(level, "NexusChat/JS", "${msg.message()} [${msg.sourceId()}:${msg.lineNumber()}]")
                return true
            }
        }
    }

    private fun addJavaScriptBridges() {
        webView.addJavascriptInterface(TorBridge(this, ::onTorReady), "AndroidTor")
        webView.addJavascriptInterface(TailscaleBridge(this) { js ->
            runOnUiThread { webView.evaluateJavascript(js, null) }
        }, "AndroidTailscale")
        webView.addJavascriptInterface(KeystoreBridge(this), "AndroidKeystore")
        webView.addJavascriptInterface(BiometricBridge(this, ::onBioResult), "AndroidBiometric")
        webView.addJavascriptInterface(NotificationBridge(this), "AndroidNotifications")
        webView.addJavascriptInterface(FileBridge(this), "AndroidFiles")
        webView.addJavascriptInterface(ClipboardBridge(this), "AndroidClipboard")
        webView.addJavascriptInterface(SystemBridge(this), "AndroidSystem")
        webView.addJavascriptInterface(SnowflakeBridge(this) { js ->
            runOnUiThread { webView.evaluateJavascript(js, null) }
        }, "AndroidSnowflake")
        webView.addJavascriptInterface(BinaryProgressBridge(this), "AndroidBinary")
        webView.addJavascriptInterface(WalletBridge(this), "AndroidWallet")
        val rtcBridge = WebRtcBridge(this) { js ->
            runOnUiThread { webView.evaluateJavascript(js, null) }
        }
        webView.addJavascriptInterface(rtcBridge, "AndroidWebRTC")
        Log.i(TAG, "All JS bridges registered (including AndroidWallet)")
    }

    private fun loadApp() {
        webView.loadUrl("file:///android_asset/public/index.html")
    }

    private fun requestPermissions() {
        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        val toRequest = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isEmpty()) {
            webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('permissionsResult',{detail:'{}'}))", null)
            return
        }
        val needsRationale = toRequest.any {
            shouldShowRequestPermissionRationale(it)
        }
        if (needsRationale) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Permissions required")
                .setMessage("NexusChat needs microphone access for voice calls, storage access for file sharing, and Bluetooth access for headsets. These are essential for full app functionality.")
                .setCancelable(false)
                .setPositiveButton("Continue") { _, _ ->
                    permLauncher.launch(toRequest.toTypedArray())
                }
                .show()
        } else {
            permLauncher.launch(toRequest.toTypedArray())
        }
    }

    private fun startAllServices() {
        startTorService()
        startV2RayService()
        startSmpService()
        ChainProxy.getInstance().start()
        Log.i(TAG, "All services started: Tor, V2Ray, SMP, ChainProxy")
    }

    private fun startTorService() {
        val intent = Intent(this, TorService::class.java)
        startForegroundService(intent)
        bindService(intent, torConnection, BIND_AUTO_CREATE)
    }

    private fun startV2RayService() {
        val intent = Intent(this, V2RayService::class.java)
        startForegroundService(intent)
        bindService(intent, v2rayConnection, BIND_AUTO_CREATE)
    }

    private fun startSmpService() {
        val prefs = NexusChatApp.securePrefs
        val host = prefs.getString("smp_host", "") ?: ""
        val port = prefs.getString("smp_port", "5223")?.toIntOrNull() ?: 5223
        val intent = Intent(this, SmpServerService::class.java).apply {
            putExtra("host", host)
            putExtra("port", port)
        }
        startForegroundService(intent)
        bindService(intent, smpConnection, BIND_AUTO_CREATE)
    }

    private fun onSmpBound() {
        smpService?.addMessageListener { json ->
            runOnUiThread {
                val safeJson = com.google.gson.Gson().toJson(json)
                webView.evaluateJavascript("window.onSmpMessage && window.onSmpMessage($safeJson)", null)
            }
        }
    }

    private fun handleDeepLink(uri: Uri) {
        Log.i(TAG, "Deep link: $uri")
        val jsUri = com.google.gson.Gson().toJson(uri.toString())
        webView.evaluateJavascript("window.handleDeepLink && window.handleDeepLink($jsUri)", null)
    }

    private fun onTorReady(socksPort: Int) {
        Log.i(TAG, "Tor ready on SOCKS5 port $socksPort")
    }

    private fun onBioResult(success: Boolean) {
        runOnUiThread {
            webView.evaluateJavascript(
                "window.onBiometricResult && window.onBiometricResult($success)", null
            )
        }
    }

    fun showBiometricPrompt(callback: (Boolean) -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            callback(false); return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result); callback(true)
                }
                override fun onAuthenticationFailed() { super.onAuthenticationFailed(); callback(false) }
                override fun onAuthenticationError(code: Int, msg: CharSequence) { callback(false) }
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("NexusChat")
                .setSubtitle("Authenticate to unlock")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
        )
    }

    fun getTransportManager(): TransportManager? = transportManager
    fun getErrorRecovery(): ErrorRecoveryManager? = recoveryManager
    fun getCoverTraffic(): CoverTrafficScheduler? = coverTraffic
    fun getDnsOverTor(): DnsOverTor? = dnsOverTor

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { handleDeepLink(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        coverTraffic?.stop()
        dnsOverTor?.stop()
        ChainProxy.getInstance().stop()
        transportManager?.stop()
        if (v2rayBound) { unbindService(v2rayConnection); v2rayBound = false }
        if (torBound) { unbindService(torConnection); torBound = false }
        if (smpBound) { unbindService(smpConnection); smpBound = false }
        scope.cancel()
        webView.destroy()
    }
}
