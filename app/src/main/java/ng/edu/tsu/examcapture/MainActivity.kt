package ng.edu.tsu.examcapture

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import ng.edu.tsu.examcapture.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var networkMonitor: NetworkMonitor

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null
    private var lastFailedUrl: String = HOME_URL
    private var showingError = false
    private var pendingPermissionRequest: PermissionRequest? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        pendingPermissionRequest?.let { request ->
            val cameraOk = grants[Manifest.permission.CAMERA] != false && hasCameraPermission()
            val audioOk = grants[Manifest.permission.RECORD_AUDIO] != false &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            val allowed = request.resources.filter { resource ->
                when (resource) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> cameraOk
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> audioOk
                    else -> true
                }
            }
            if (allowed.isNotEmpty()) {
                request.grant(allowed.toTypedArray())
            } else {
                request.deny()
                Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show()
            }
        }
        pendingPermissionRequest = null
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (callback == null) return@registerForActivityResult

        val uris = mutableListOf<Uri>()
        if (result.resultCode == RESULT_OK) {
            val dataUri = result.data?.data
            val clipData = result.data?.clipData
            when {
                clipData != null -> {
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i).uri?.let(uris::add)
                    }
                }
                dataUri != null -> uris.add(dataUri)
                cameraImageUri != null -> uris.add(cameraImageUri!!)
            }
        }
        callback.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
        cameraImageUri = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestAppPermissions()

        networkMonitor = NetworkMonitor(this) { online ->
            runOnUiThread {
                if (!online) {
                    showOffline()
                } else if (showingError) {
                    loadUrl(lastFailedUrl.ifBlank { HOME_URL })
                }
            }
        }

        setupWebView()
        setupErrorActions()
        setupSwipeRefresh()
        setupBackNavigation()

        if (networkMonitor.isOnline()) {
            loadUrl(HOME_URL)
        } else {
            showOffline()
        }
    }

    override fun onStart() {
        super.onStart()
        networkMonitor.start()
    }

    override fun onStop() {
        networkMonitor.stop()
        super.onStop()
    }

    override fun onDestroy() {
        binding.webView.apply {
            stopLoading()
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            destroy()
        }
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            userAgentString = "$userAgentString TSUSAMS/1.0"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleExternalUrl(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return handleExternalUrl(url.orEmpty())
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.swipeRefresh.isRefreshing = false
                binding.pageProgress.visibility = View.GONE
                if (!showingError) {
                    binding.webView.visibility = View.VISIBLE
                    binding.errorView.root.visibility = View.GONE
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request?.isForMainFrame != true) return
                val status = errorResponse?.statusCode ?: return
                if (status >= 400) {
                    lastFailedUrl = request.url?.toString() ?: HOME_URL
                    showPageError()
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame != true) return
                lastFailedUrl = request.url?.toString() ?: HOME_URL
                if (!networkMonitor.isOnline()) {
                    showOffline()
                } else {
                    showPageError()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                lastFailedUrl = failingUrl ?: HOME_URL
                if (!networkMonitor.isOnline()) {
                    showOffline()
                } else {
                    showPageError()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (showingError) return
                binding.pageProgress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                binding.pageProgress.progress = newProgress
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                runOnUiThread {
                    val needsCamera = request.resources.any { resource ->
                        resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                            resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                    }
                    if (needsCamera && !hasCameraPermission()) {
                        pendingPermissionRequest = request
                        permissionLauncher.launch(requiredPermissions())
                    } else {
                        request.grant(request.resources)
                    }
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                if (!hasCameraPermission() || !hasStoragePermission()) {
                    permissionLauncher.launch(requiredPermissions())
                }
                openFileChooser(fileChooserParams)
                return true
            }
        }
    }

    private fun openFileChooser(params: WebChromeClient.FileChooserParams?) {
        val acceptTypes = params?.acceptTypes?.filter { it.isNotBlank() } ?: emptyList()
        val allowMultiple = params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        val imageCapture = Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { intent ->
            val photoFile = createImageFile()
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                photoFile
            )
            cameraImageUri = uri
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            intent.clipData = ClipData.newUri(contentResolver, "TSU Capture", uri)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = when {
                acceptTypes.isEmpty() -> "image/*"
                acceptTypes.size == 1 -> acceptTypes.first()
                else -> "*/*"
            }
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
            if (acceptTypes.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes.toTypedArray())
            }
        }

        val chooser = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, contentIntent)
            putExtra(Intent.EXTRA_TITLE, "Select or capture image")
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(imageCapture))
        }
        fileChooserLauncher.launch(chooser)
    }

    private fun createImageFile(): File {
        val picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: cacheDir
        if (!picturesDir.exists()) {
            picturesDir.mkdirs()
        }
        return File(picturesDir, "tsu_capture_${System.currentTimeMillis()}.jpg")
    }

    private fun setupErrorActions() {
        binding.errorView.retryButton.setOnClickListener {
            if (!networkMonitor.isOnline()) {
                showOffline()
                return@setOnClickListener
            }
            loadUrl(lastFailedUrl.ifBlank { HOME_URL })
        }
        binding.errorView.homeButton.setOnClickListener {
            if (!networkMonitor.isOnline()) {
                showOffline()
                return@setOnClickListener
            }
            loadUrl(HOME_URL)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.tsu_gold, R.color.tsu_navy)
        binding.swipeRefresh.setOnRefreshListener {
            if (!networkMonitor.isOnline()) {
                binding.swipeRefresh.isRefreshing = false
                showOffline()
            } else {
                binding.webView.reload()
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    showingError -> {
                        if (networkMonitor.isOnline()) {
                            loadUrl(HOME_URL)
                        } else {
                            finish()
                        }
                    }
                    binding.webView.canGoBack() -> binding.webView.goBack()
                    else -> finish()
                }
            }
        })
    }

    private fun loadUrl(url: String) {
        showingError = false
        lastFailedUrl = url
        binding.errorView.root.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        binding.webView.loadUrl(url)
    }

    private fun showOffline() {
        showingError = true
        lastFailedUrl = currentOrHomeUrl()
        binding.webView.stopLoading()
        binding.webView.visibility = View.GONE
        binding.pageProgress.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
        binding.errorView.root.visibility = View.VISIBLE
        binding.errorView.errorIcon.setImageResource(R.drawable.ic_wifi_off)
        binding.errorView.errorTitle.setText(R.string.no_internet_title)
        binding.errorView.errorMessage.setText(R.string.no_internet_message)
    }

    private fun showPageError() {
        showingError = true
        binding.webView.stopLoading()
        binding.webView.visibility = View.GONE
        binding.pageProgress.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
        binding.errorView.root.visibility = View.VISIBLE
        binding.errorView.errorIcon.setImageResource(R.drawable.ic_error)
        binding.errorView.errorTitle.setText(R.string.page_error_title)
        binding.errorView.errorMessage.setText(R.string.page_error_message)
    }

    private fun requestAppPermissions() {
        val missing = requiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun currentOrHomeUrl(): String {
        val current = binding.webView.url
        return if (current.isNullOrBlank() || current.startsWith("about:")) HOME_URL else current
    }

    private fun handleExternalUrl(url: String): Boolean {
        if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:")) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            return true
        }
        return false
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.READ_MEDIA_IMAGES
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return permissions.toTypedArray()
    }

    companion object {
        const val HOME_URL = "https://sams.tsuniversity.edu.ng/"
    }
}
