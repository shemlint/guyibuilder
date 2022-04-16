package com.example.guyiandroid

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.Window
import android.webkit.*
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import java.io.File
import java.util.*
import kotlin.concurrent.schedule


class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    val mChromeClient = MyWebChromeClient(this)
    val LOGID = "MyWebApp"

    val CHANNEL_ID = "GuyiBasicNotifications"
    val NOT_ACTION = "com.example.desa.ACTION_NOTIFY"
    val SENT_ACTION = "com.example.desa.sendsuccess"
    val SEND_FAIL_ACTION = "com.example.desa.sendfail"
    val ACTION_START = "com.lint.guyiandroid.ACTION_START"

    var sendNotificationClick = { id: Int -> }
    var sendMessage = { mes: String -> }
    var ttsEngine: TextToSpeech? = null
    var openingAppName = ""
    var splashImage: ImageView? = null
    var splashScreen: ConstraintLayout? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        try {
            this.supportActionBar?.hide()
        } catch (e: Exception) {
        }
        setContentView(R.layout.webview)
        createNotificationChannel()

        splashScreen =
            findViewById<ConstraintLayout>(R.id.spash_screen) //?:ConstraintLayout=null
        splashImage = findViewById<ImageView>(R.id.splash_image)

        val myweb: WebView = findViewById(R.id.contents)
        val myWebAppInterface = WebAppInterface(this, myweb)
        myWebAppInterface.customFinish = { finish() }
        sendMessage = { mes: String -> myWebAppInterface.sendMessage(mes) }
        sendNotificationClick =
            { id: Int -> myWebAppInterface.sendMessage("notification clicked $id ") }
        ttsEngine = TextToSpeech(this, this)
        myWebAppInterface.speak = { text: String, now: Boolean ->
            Unit
            ttsEngine?.speak(
                text,
                if (now) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null
            )
        }
        myWebAppInterface.voices = {
            if (ttsEngine != null) {
                val voices = ttsEngine!!.voices
                var names = voices.map { it.name }
                names.toTypedArray()
            } else {
                arrayOf("")
            }

        }
        myWebAppInterface.prop_setVoice = { voiceName: String, speed: Double ->
            if (ttsEngine != null) {
                var voice: Voice? = null
                for (tmpVoice in ttsEngine!!.voices) {
                    Log.d(LOGID, "$voiceName<>${tmpVoice?.name} ")
                    if (tmpVoice.name == voiceName) {
                        Log.d(LOGID, "Not found voice")
                        voice = tmpVoice
                        break
                    }
                }
                if (voice != null && speed >= 0 && speed <= 10) {
                    ttsEngine!!.setVoice(voice)
                    ttsEngine!!.setSpeechRate(speed.toFloat())
                    true
                } else {
                    Log.d(LOGID, "NOt conds" + speed + voice?.name)
                    false
                }
            } else {
                false
            }
        }

        myweb.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            setAppCacheEnabled(true)
            databaseEnabled = true
            setGeolocationDatabasePath(filesDir.path)
        }
        myweb.addJavascriptInterface(myWebAppInterface, "Phone")
        val updateLoadProgress = { progress: Int ->
            if (progress == 100) {
//                    splashScreen!!.visibility = View.GONE
                Log.d("my app", "Finished loading app")
            }
        }
        mChromeClient.progressCallback = updateLoadProgress
        myweb.webChromeClient = mChromeClient
        myweb.setDownloadListener { url: String,
                                    userAgent: String,
                                    contentDisposition: String,
                                    mimetype: String,
                                    contentLength: Long ->
            val i = Intent(Intent.ACTION_VIEW).apply {
               // data = Uri.parse(url)
            }
           // startActivity(i)

        }

        myweb.evaluateJavascript(
            """
            window.guyi_os="android";
            window.PHONE="Android-${Build.VERSION.RELEASE}";
        """.trimIndent()
        ) {}
        myweb.loadUrl("file:///android_asset/guyi.html")
        myweb.evaluateJavascript(
            """
                try{
                    window.addEventListener('message',(e)=>Android.gotMessage(e.data) );
                }catch(e){
                     console.log(e.message);
                }
                """.trimIndent()
        ) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        gotIntent(intent)

        //testing interfaces below

    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            if (ttsEngine != null) {
                val result = ttsEngine!!.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.d(LOGID, "Language not supported or missing data")
                    ttsEngine = null
                } else {
                    Log.d(LOGID, "Speech engine running")
                }
            }
        } else {
            ttsEngine = null
        }
    }

    private fun openSplashScreen(name: String) {
        try {
            var filesPath = this.filesDir.absolutePath
            val newFile = File("$filesPath/splash_screens")
            if (!newFile.isDirectory()) {
                newFile.mkdirs()
            }
            var path = "$filesPath/$name"
            val splashFile = File(path)

            if (splashFile.exists() && splashFile.isFile) {
                val splashData = splashFile.readText()
                //val decodedString = Base64.decode(splashData, Base64.DEFAULT)

            }
            val defData = this.assets.open("splash.txt").bufferedReader().readText()
            val decodedString = Base64.decode(defData, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
            // splashImage!!.setImageBitmap(bitmap)
            splashScreen!!.visibility = View.VISIBLE
            mes("data" + defData.slice(0..120))
        } catch (e: Exception) {
            mes("Error ${e.message}")
        }
    }

    fun mes(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        Log.d(LOGID, message)
    }

    private fun gotIntent(intent: Intent) {
        if (intent.extras == null) return
        Log.d(LOGID, "new intent here $intent ${intent.action} ")
        if (intent.action == Intent.ACTION_MAIN) {
            Log.d(LOGID, "App started")
        }
        if (intent.action == NOT_ACTION) {
            val id = intent.extras?.getInt("notid", 10) ?: 12
            sendNotificationClick(id)
            Log.d(LOGID, "Got id $id")
        }
        if (intent.action == SENT_ACTION) {
            val no = intent.extras?.getString("no", "00") ?: "00-none"
            sendMessage("send success,$no")
            Log.d(LOGID, "sent $no")

        }
        if (intent.action == SEND_FAIL_ACTION) {
            val no = intent.extras?.getString("no", "00") ?: "00-none"
            sendMessage("send delivered,$no")
            Log.d(LOGID, "sent delivered $no")

        }
        if (intent.action == ACTION_START) {
            val appName = intent.extras?.getString("name", "") ?: ""
            sendMessage("startapp,$appName")
            //openSplashScreen(appName)
            Log.d(LOGID, "starting app $appName")
        }

    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            gotIntent(intent)
        }

    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Guyi basic notifications"
            val descriptionText = "Send by any app using guyi"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == mChromeClient.REQ_CODE_STORAGE) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED
            ) {
                Log.i("PERMISSION GOTTEN", "Permission granted")
                Toast.makeText(
                    this.applicationContext,
                    "Strorage Permissions Granted",
                    Toast.LENGTH_SHORT
                )
                    .show()
                mChromeClient.getFile()

            } else {
                Log.i("PERMISSION GOTTEN", "Permission denied")
                Toast.makeText(
                    this.applicationContext,
                    "Storage permissions denied",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }

        }
        if (requestCode == mChromeClient.REQ_CODE_CAMERA) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED
                && grantResults[2] == PackageManager.PERMISSION_GRANTED
            ) {
                Log.i("PERMISSION GOTTEN", "Permission camera")
                Toast.makeText(
                    this.applicationContext,
                    "Camera permission Granted",
                    Toast.LENGTH_SHORT
                )
                    .show()
                mChromeClient.grantPermission()

            } else {
                Log.i("PERMISSION GOTTEN", "Permission denied")
                Toast.makeText(
                    this.applicationContext,
                    "Camera Permission Denied",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }
        if (requestCode == mChromeClient.REQ_CODE_LOCATION) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED
            ) {
                Log.i("PERMISSION GOTTEN", "Permission camera")
                Toast.makeText(
                    this.applicationContext,
                    "Location permission Granted",
                    Toast.LENGTH_SHORT
                )
                    .show()
                mChromeClient.grantLocationPermission()

            } else {
                Log.i("PERMISSION GOTTEN", "Permission denied")
                Toast.makeText(
                    this.applicationContext,
                    "Location Permission Denied",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }

        }
        if (requestCode == mChromeClient.REQ_CODE_SMS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                Log.i("PERMISSION GOTTEN", "Permission sms")
                Toast.makeText(
                    this.applicationContext,
                    "SMS permission Granted",
                    Toast.LENGTH_SHORT
                )
                    .show()
                sendMessage("sms permission got")
            } else {
                Log.i("PERMISSION GOTTEN", "Permission sm denied")
                Toast.makeText(
                    this.applicationContext,
                    "SMS Permission Denied",
                    Toast.LENGTH_SHORT
                )
                    .show()
                sendMessage("sms permissions denied")
            }

        }
        if (requestCode == mChromeClient.REQ_CODE_CONTACTS)
            if (requestCode == mChromeClient.REQ_CODE_CALL) {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    Log.i("PERMISSION GOTTEN", "Permission call")
                    Toast.makeText(
                        this.applicationContext,
                        "Call permission Granted",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                    sendMessage("call permmision got")
                } else {
                    Log.i("PERMISSION GOTTEN", "Permission contacts denied")
                    Toast.makeText(
                        this.applicationContext,
                        "Call Permission Denied",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                    sendMessage("call permission denied")
                }

            }
        if (requestCode == mChromeClient.REQ_CODE_SENDSMS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                Log.i("PERMISSION GOTTEN", "Permission sendsms")
                Toast.makeText(
                    this.applicationContext,
                    "SendSMS permission Granted",
                    Toast.LENGTH_SHORT
                )
                    .show()
                sendMessage("sendsms permmision got")
            } else {
                Log.i("PERMISSION GOTTEN", "Permission sendsms denied")
                Toast.makeText(
                    this.applicationContext,
                    "Call Permission Denied",
                    Toast.LENGTH_SHORT
                )
                    .show()
                sendMessage("sendsms permission denied")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == mChromeClient.PICK_APP_FILE) {
            if (resultCode == Activity.RESULT_OK) {
                data?.data?.also { uri ->
                    mChromeClient.sendFile(uri)
                }
            } else {
                mChromeClient.sendFile(null)
            }
        }
    }
}

class Listener(private val sendMessage: (String) -> Unit) : RecognitionListener {
    val LOGID = "MyWebAppListener"

    override fun onReadyForSpeech(p0: Bundle?) {
        Log.d(LOGID, "Ready")
        sendMessage("speech, ready")
        //TODO("Not yet implemented")
    }

    override fun onBeginningOfSpeech() {
        Log.d(LOGID, "Beginning")
        sendMessage("speech, beginning")
        //TODO("Not yet implemented")
    }

    override fun onRmsChanged(p0: Float) {
        // Log.d(LOGID, "rms")
        // TODO("Not yet implemented")
    }

    override fun onBufferReceived(p0: ByteArray?) {
        Log.d(LOGID, "Buffer")
        // TODO("Not yet implemented")
    }

    override fun onEndOfSpeech() {
        Log.d(LOGID, "End ")
        sendMessage("speech, end")
        //TODO("Not yet implemented")
    }

    override fun onError(p0: Int) {
        Log.d(LOGID, "Error")
        sendMessage("speech, error $p0")
        //TODO("Not yet implemented")
    }

    override fun onResults(p0: Bundle?) {
        Log.d(LOGID, "Result" + p0)
        if (p0 != null) {
            val list = p0.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val strlist = list?.joinToString(",")
            Log.d(LOGID, "Results $strlist")
            sendMessage("results, $strlist")
        } else {
            Log.d(LOGID, "error, empty")
            sendMessage("error, empty")
        }
        //TODO("Not yet implemented")
    }

    override fun onPartialResults(p0: Bundle?) {
        Log.d(LOGID, "Partial Results" + p0)
        if (p0 != null) {
            val list = p0.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val strlist = list?.joinToString(",")
            Log.d(LOGID, "Results $strlist")
            sendMessage("partial, $strlist")
        } else {
            Log.d(LOGID, "error, empty")
            sendMessage("error, empty")
        }//TODO("Not yet implemented")
    }

    override fun onEvent(p0: Int, p1: Bundle?) {
        Log.d(LOGID, "Event")
        //TODO("Not yet implemented")
    }
}

class MyWebChromeClient(mContext: AppCompatActivity) : WebChromeClient() {

    val mContext: AppCompatActivity = mContext
    val PICK_APP_FILE = 100
    val REQ_CODE_STORAGE = 1000
    val REQ_CODE_CAMERA = 1001
    val REQ_CODE_LOCATION = 1002
    val REQ_CODE_SMS = 1003
    val REQ_CODE_CONTACTS = 1004
    val REQ_CODE_CALL = 1005
    val REQ_CODE_SENDSMS = 1006

    var filePathCallback: ValueCallback<Array<Uri>>? = null
    var progressCallback = { t: Int -> }
    var requestPermissions = { false }
    var permissionRequest: PermissionRequest? = null
    var locationCallback: GeolocationPermissions.Callback? = null
    var locationOrigin = ""

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {

        locationCallback = callback
        locationOrigin = origin ?: ""
        askPermission("location")
    }

    override fun onPermissionRequest(request: PermissionRequest?) {
        // super.onPermissionRequest(request)
        if (request != null) {
            Log.d("mywebapp", request.resources.toString())
            //request.grant(request.resources)
            permissionRequest = request
            askPermission("camera")

        } else {
            Log.d("mywebapp", "request null")
        }

    }

    fun grantLocationPermission() {
        locationCallback?.invoke(locationOrigin, true, true)
    }

    fun grantPermission(grant: Boolean = true) {
        if (grant) {
            permissionRequest?.grant(permissionRequest?.resources)
        } else {
            permissionRequest?.grant(permissionRequest?.resources)
        }
    }

    override fun onConsoleMessage(message: ConsoleMessage): Boolean {

        Log.d(
            "MyAndroidWeb",
            "${message.message()}  -- ${message.lineNumber()} ${message.messageLevel()} --${message.sourceId()}"
        )
        return true
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        Log.d("myweb progress ", "at => $newProgress")
        //progressCallback?.let { it(newProgress) }
        progressCallback(newProgress)
        super.onProgressChanged(view, newProgress)

    }

    private fun askPermission(type: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (type == "storage") {
                val permissionStorage1 = ActivityCompat.checkSelfPermission(
                    mContext.applicationContext,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
                val permissionStorage2 = ActivityCompat.checkSelfPermission(
                    mContext.applicationContext,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                if (permissionStorage1 != PackageManager.PERMISSION_GRANTED || permissionStorage2 != PackageManager.PERMISSION_GRANTED) {
                    mContext.requestPermissions(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ),
                        REQ_CODE_STORAGE
                    )
                } else {
                    getFile()

                }
            }
            if (type == "camera") {
                val camPermission = ActivityCompat.checkSelfPermission(
                    mContext.applicationContext,
                    Manifest.permission.CAMERA
                )
                val audioPermission = ActivityCompat.checkSelfPermission(
                    mContext.applicationContext,
                    Manifest.permission.RECORD_AUDIO
                )
                val audioSettingsPermission = ActivityCompat.checkSelfPermission(
                    mContext.applicationContext,
                    Manifest.permission.MODIFY_AUDIO_SETTINGS
                )
                if (camPermission != PackageManager.PERMISSION_GRANTED || audioPermission != PackageManager.PERMISSION_GRANTED
                    || audioSettingsPermission != PackageManager.PERMISSION_GRANTED
                ) {
                    mContext.requestPermissions(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.MODIFY_AUDIO_SETTINGS
                        ),
                        REQ_CODE_CAMERA
                    )
                } else {
                    grantPermission()

                }
            }
            if (type == "location") {
                val permissionFine = ActivityCompat.checkSelfPermission(
                    mContext.applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                val permissionCoarse = ActivityCompat.checkSelfPermission(
                    mContext.applicationContext,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (permissionFine != PackageManager.PERMISSION_GRANTED && permissionCoarse != PackageManager.PERMISSION_GRANTED) {
                    mContext.requestPermissions(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        REQ_CODE_LOCATION
                    )
                } else {
                    grantLocationPermission()
                }
            }
        }
    }


    fun getFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, "Project File")

        }
        mContext.startActivityForResult(intent, PICK_APP_FILE)

    }

    fun sendFile(uri: Uri?) {
        if (uri == null) {
            filePathCallback?.onReceiveValue(null)
        } else {
            filePathCallback?.onReceiveValue(arrayOf(uri))
            // Toast.makeText(mContext, "found uri", Toast.LENGTH_SHORT).show()
        }
        //Toast.makeText(mContext, "send file", Toast.LENGTH_SHORT).show()


    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        this.filePathCallback = filePathCallback
        //Toast.makeText(mContext, "Showing file", Toast.LENGTH_SHORT).show()
        askPermission("storage")
        return true
        //return super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
    }


}



