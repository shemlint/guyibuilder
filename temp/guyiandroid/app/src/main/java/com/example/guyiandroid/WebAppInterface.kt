package com.example.guyiandroid

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.hardware.Camera
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.SmsManager
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.ShortcutManagerCompat
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class WebAppInterface(
    private val mContext: Context,
    private val myweb: WebView,

    ) {

    val appContext: AppCompatActivity = mContext as AppCompatActivity
    var cam: Camera? = null
    var customFinish = { }
    val LOGID = "MyWebAppJsInterface"
    val CHANNEL_ID = "GuyiBasicNotifications"
    val NOT_ACTION = "com.example.desa.ACTION_NOTIFY"
    var speak = { text: String, now: Boolean -> }
    var voices = { arrayOf<String>() }
    var prop_setVoice = { voice: String, speed: Double -> false }
    var playingSound: Ringtone? = null
    val REQ_CODE_SMS = 1003
    val REQ_CODE_CONTACTS = 1004
    val REQ_CODE_CALL = 1005
    val REQ_CODE_SENDSMS = 1006
    val ACTION_START = "com.lint.guyiandroid.ACTION_START"
    var appLoaded = false
    var waitingMessages = mutableListOf<String>()

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun openApp(): String {
        return try {
            val app = mContext.assets.open("app.guyi").bufferedReader().use{it.readText()};
            app
        } catch (e:Exception) {
            ""
        }
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun notifyWithIcon(
        id: Int,
        title: String,
        content: String,
        visibility: String,
        iconData: String
    ) {
        notify(id, title, content, visibility, iconData)
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun appLoadDone() {
        appLoaded = true
        waitingMessages.forEach { mes ->
            sendMessage(mes)
        }
        waitingMessages.clear()
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun pinShortcut(id: String, name: String, iconData: String): Boolean {
        val res = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val shortcutManager =
                    appContext.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
                val openAppIntent = Intent(mContext, MainActivity::class.java).apply {
                    putExtra("name", name)
                    flags = Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    action = ACTION_START
                }
                var safeIconData = iconData.split(",")[1]
                val decodedString = Base64.decode(safeIconData, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                val icon = Icon.createWithBitmap(bitmap)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (shortcutManager.isRequestPinShortcutSupported) {
                        val pinShortcutInfo = ShortcutInfo.Builder(mContext, id).apply {
                            setShortLabel(name)
                            setLongLabel("Guyi's $name")
                            setIcon(icon)
                            setIntent(openAppIntent)
                        }.build()
                        val pinnedShortcutCallbackIntent =
                            shortcutManager.createShortcutResultIntent(pinShortcutInfo)
                        val successCallback =
                            PendingIntent.getBroadcast(mContext, 0, pinnedShortcutCallbackIntent, 0)
                        shortcutManager.requestPinShortcut(
                            pinShortcutInfo,
                            successCallback.intentSender
                        )

                    }

                }
            }
            true
        } catch (e: Exception) {
            mes("Error ${e.message}")
            false
        }
        return res
    }

    private fun mes(message: String) {
        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show()
        Log.d(LOGID, message)
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun checkPermisson(name: String): Boolean {
        val perName = "android.permission.$name"
        val res = try {
            PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                mContext.applicationContext,
                perName
            )
        } catch (e: Exception) {
            false
        }
        return res
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun sendSms(no: String, body: String = ""): String {
        val res = try {
            val permissionOk = askPermission("sendsms")
            if (!permissionOk) return "asking permission"
            val smsmanager = SmsManager.getDefault()
            val sendIntent = Intent("com.example.desa.sendsuccess").apply {
                putExtra("no", "$no,$body")
            }
            val psendIntent: PendingIntent =
                PendingIntent.getActivity(
                    mContext,
                    1300,
                    sendIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            val failIntent = Intent("com.example.desa.sendfail").apply {
                putExtra("no", "$no,$body")
            }
            val pfailIntent: PendingIntent =
                PendingIntent.getActivity(
                    mContext,
                    1302,
                    failIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )

            smsmanager.sendTextMessage(no, null, body, psendIntent, pfailIntent)
            "started sending"
        } catch (e: Exception) {
            "error ${e.message}"
        }
        return res
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun call(no: String, sim: Int = 0): String {
        val permissionOk = askPermission("call")
        if (!permissionOk) return "asking permission"
        val safeNo = no.replace("#", Uri.encode("#"))
        val res = try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$safeNo")
                putExtra("com.android.phone.extra.slot", sim)
                putExtra("simSlot", sim)
            }
            mContext.startActivity(callIntent)
            "started"
        } catch (e: Exception) {
            "error ${e.message}"
        }
        return res
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun getSms(type: String = "inbox"): String {
        val uri: String = when (type) {
            "inbox" -> "content://sms/inbox"
            "draft" -> "content://sms/draft"
            "sent" -> "content://sms/sent"
            "contacts" -> "CONTACTS"
            else -> type
        }
        var permissionsGranted = true
        if (arrayOf("inbox", "draft", "sent").contains(type)) {
            permissionsGranted = askPermission("sms")
        } else if (type == "contacts") {
            permissionsGranted = askPermission("contacts")
        }
        if (!permissionsGranted) return "asking-permmisions"
        var result = ""
        try {
            val cursor =
                mContext.contentResolver.query(
                    if (type == "contacts") ContactsContract.Contacts.CONTENT_URI else
                        Uri.parse(uri),
                    null,
                    null,
                    null,
                    null
                )
            if (cursor?.moveToFirst() == true) {
                var msgArray = mutableListOf<String>()//: MutableList<String>;
                do {
                    var msgData = ""
                    for (idx in 0 until cursor.columnCount) {
                        msgData += "<>" + cursor.columnNames[idx] + ":" + cursor.getString(idx)
                    }
                    msgArray.add(msgData)

                } while (cursor.moveToNext())
                result = msgArray.joinToString("<,>")
            }
        } catch (e: Exception) {
            result = "error ${e.message}"
        }
        Log.d(LOGID, result)
        return result
    }

    private fun askPermission(type: String): Boolean {//dup with one in chrome client
        var permissionsOk = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (type == "sms") {
                val permissionSms = ActivityCompat.checkSelfPermission(
                    mContext.applicationContext,
                    Manifest.permission.READ_SMS
                )

                if (permissionSms != PackageManager.PERMISSION_GRANTED) {
                    permissionsOk = false
                    appContext.requestPermissions(
                        arrayOf(
                            Manifest.permission.READ_SMS
                        ),
                        REQ_CODE_SMS
                    )
                }
            }
            if (type == "contacts") {
                val permissionSms = ActivityCompat.checkSelfPermission(
                    mContext.applicationContext,
                    Manifest.permission.READ_CONTACTS
                )

                if (permissionSms != PackageManager.PERMISSION_GRANTED) {
                    permissionsOk = false
                    appContext.requestPermissions(
                        arrayOf(
                            Manifest.permission.READ_CONTACTS
                        ),
                        REQ_CODE_CONTACTS
                    )
                }
            }
            if (type == "call") {
                val permissionSms = ActivityCompat.checkSelfPermission(
                    mContext.applicationContext,
                    Manifest.permission.CALL_PHONE
                )

                if (permissionSms != PackageManager.PERMISSION_GRANTED) {
                    permissionsOk = false
                    appContext.requestPermissions(
                        arrayOf(
                            Manifest.permission.CALL_PHONE
                        ),
                        REQ_CODE_CALL
                    )
                }
            }
            if (type == "sendsms") {
                val permissionSms = ActivityCompat.checkSelfPermission(
                    mContext.applicationContext,
                    Manifest.permission.SEND_SMS
                )

                if (permissionSms != PackageManager.PERMISSION_GRANTED) {
                    permissionsOk = false
                    appContext.requestPermissions(
                        arrayOf(
                            Manifest.permission.SEND_SMS
                        ),
                        REQ_CODE_SENDSMS
                    )
                }
            }
        }
        return permissionsOk
    }


    @JavascriptInterface
    @SuppressWarnings("unused")
    fun startApp(): String {
        val appData = try {
            val reader = BufferedReader(InputStreamReader(mContext.assets.open("app.guyi")))
            val data = reader.readText()
            reader.close()
            data
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
        return appData

    }

    @SuppressWarnings("unused")
    fun setBadge(count: Int) {
        /*
             val done=try{
                 ShortcutBadger.applyCount(mContext,count)
                 true
             }catch(e:Exception){
                 e.printStackTrace()
                 false
             }
             return done
         */
    }

    @SuppressWarnings("unused")
    fun removeBadge() {
        /*
       val done=try{

              ShortcutBadger.removeCount(mContext)
              true
          }else
          {
              false
          }
          return done
          */
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun playSound(type: String): Boolean {
        return try {
            playingSound?.stop()
            if (type === "none") return true
            val urlInt = when (type) {
                "alarm" -> RingtoneManager.TYPE_ALARM
                "ringtone" -> RingtoneManager.TYPE_RINGTONE
                "notification" -> RingtoneManager.TYPE_NOTIFICATION
                else -> RingtoneManager.TYPE_NOTIFICATION
            }
            val url = RingtoneManager.getDefaultUri(urlInt)
            val ring = RingtoneManager.getRingtone(mContext, url)
            playingSound = ring
            ring.play()
            true
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun storeApp(app: String): String {
        return writeFile("guyisavedapp.guyi", app)
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun getApp(): String {
        return readFile("guyisavedapp.guyi")
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun flashOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                var cameraId = ""
                val camManager =
                    mContext.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager;
                if (camManager != null) {
                    cameraId = camManager.getCameraIdList()[0]
                    camManager.setTorchMode(cameraId, false)

                }
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        } else {
            cam = Camera.open()
            val params = cam!!.parameters
            params.flashMode = Camera.Parameters.FLASH_MODE_TORCH
            cam!!.parameters = params
            cam!!.startPreview()

        }
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun flashOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                var cameraId = ""
                val camManager =
                    mContext.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                if (camManager != null) {
                    cameraId = camManager.getCameraIdList()[0]
                    camManager.setTorchMode(cameraId, false)
                }

            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        } else {
            cam = Camera.open()
            val params = cam!!.parameters
            params.flashMode = Camera.Parameters.FLASH_MODE_OFF
            cam!!.parameters = params
            cam!!.stopPreview()

        }
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun exit() {
        appContext.finish()
    }

    @SuppressWarnings("unused")
    fun notify(id: Int, title: String, content: String, visibility: String, iconData: String = "") {

        val intent = Intent(mContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            action = NOT_ACTION
            putExtra("notid", id)
        }
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(mContext, id, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        var builder = NotificationCompat.Builder(mContext, CHANNEL_ID).apply {
            setSmallIcon(R.mipmap.ic_launcher)
            setContentTitle(title)
            //.setContentText()
            setStyle(
                NotificationCompat.BigTextStyle().bigText(content)
            )
            setPriority(NotificationCompat.PRIORITY_DEFAULT)
            setAutoCancel(true)
            setContentIntent(pendingIntent)
            try {
                var safeIconData = iconData.split(",")[1]
                val decodedString = Base64.decode(safeIconData, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                setLargeIcon(bitmap)
            } catch (e: Exception) {
                //mes("setting large icon error")
            }
        }
        with(NotificationManagerCompat.from(mContext)) {
            notify(id, builder.build())
        }

    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun notifyBasic(id: Int, title: String, content: String, visibility: String) {
        notify(id, title, content, visibility)

    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun stt() {
        Log.d(LOGID, "Starting recognition")
        //recognize()
        myweb.post(Runnable {
            Log.d(LOGID, "in recognize starting")
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, "en-US")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                //putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,"")
            }
            val sr = SpeechRecognizer.createSpeechRecognizer(mContext)
            val listener = Listener { mes -> sendMessage(mes) }
            sr.setRecognitionListener(listener)
            sr.startListening(intent)

        })
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun tts(text: String, now: Boolean) {
        speak(text, now)

    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun getVoices(): String {
        return voices().joinToString(",")
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun setVoice(voiceName: String, speed: Double): Boolean {
        return prop_setVoice(voiceName, speed)
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun showToast(toast: String, length: String = "long") {
        if (length == "long") {
            Toast.makeText(mContext, toast, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()
        }

    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun writeFile(url: String, data: String): String {
        var result = ""
        try {
            val path = mContext.filesDir.absolutePath
            val newFile = File("$path/$url")
            if (newFile.exists()) {
                newFile.writeText(data)

            } else {
                newFile.createNewFile()
                newFile.writeText(data)
            }
            result = "done"
        } catch (e: Exception) {
            result = "error"
        }
        return result

    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun readFile(url: String): String {
        var result = ""
        try {
            val path = mContext.filesDir.absolutePath
            val openFile = File("$path/$url")
            result = if (openFile.exists() && openFile.isFile) {
                openFile.readText()
            } else {
                ""
            }
        } catch (e: Exception) {
            result = ""
        }
        return result

    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun getFiles(): String {
        var result = ""
        result = try {
            val path = mContext.filesDir.absolutePath
            val file = File(path)
            if (!file.exists()) return "Not a file"
            if (!file.isDirectory) {
                "Not Directory"
            } else {
                val urls = file.list()
                urls.joinToString(",")

            }

        } catch (e: Exception) {
            "error"
        }
        return result
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun deleteFile(url: String): String {
        var result = ""
        try {
            val path = mContext.filesDir.absolutePath
            val openFile = File("$path/$url")
            if (openFile.exists()) {
                openFile.delete()
                result = "done"
            } else {
                result = "not a file"
            }

        } catch (e: Exception) {
            result = "error"
        }
        return result
    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun showDialog(
        message: String = "",
        title: String = "",
        buttons: String = "OK,CANCEL",
        cancelable: Boolean = true
    ): String {
        var buts = buttons.split(",")
        var result = ""
        if (buts.size < 2) {
            buts = arrayOf("OK", "CANCEL").toList()
        }
        val alertDialog: AlertDialog? = mContext?.let {

            val builder = AlertDialog.Builder(mContext)
            builder.apply {
                setPositiveButton(buts[0],
                    DialogInterface.OnClickListener { dialog, id ->
                        result = buts[0]
                        writeFile("result.txt", result)
                        sendMessage(buts[0])
                    })
                setNegativeButton(buts[1],
                    DialogInterface.OnClickListener { dialog, id ->
                        result = buts[1]
                        writeFile("result.txt", result)
                        sendMessage(buts[1])
                    })
                setOnDismissListener {
                    writeFile("result.txt", "dismissed")
                    sendMessage("dismissed")
                }
            }
            if (title.isNotEmpty()) {
                builder.setTitle(title)
            }
            builder.setCancelable(cancelable)
            builder.setMessage(message)
            builder.create()
        }
        writeFile("result.txt", "")
        alertDialog?.show()
        return result

    }

    @JavascriptInterface
    @SuppressWarnings("unused")
    fun gotMessage(message: String = "") {
        Log.d(LOGID, message)
        if (message == "ready") {
            try {
                val reader = BufferedReader(InputStreamReader(mContext.assets.open("guyi.html")))
                val app = reader.readText()
                sendMessage(app)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    }

    fun sendMessage(message: String = ""): Unit {
        Log.d(LOGID, "Sent $message")
        if (appLoaded) {
            myweb.post {
                myweb.evaluateJavascript(
                    """
                    window.postMessage(`$message`)
                    """.trimIndent()
                ) {

                }
            }
        } else {
            waitingMessages.add(message)
        }

    }

    @JavascriptInterface
    @SuppressWarnings("unused")

    fun test() {
        myweb.post(Runnable {

            myweb.evaluateJavascript(
                """
             window.postMessage('minimal=trdue')
             window.getMessages('Testing 1 2 3')
             console.log('Running ',window.__GUYI__)
             window.postMessage(`Test done`)
         """.trimIndent()
            ) {
                //showToast("Got test data $it")

            }
        })
    }

}