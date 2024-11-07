package com.mastertipsy.acr122u

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.acs.smartcard.Reader

class MainActivity : AppCompatActivity() {
    companion object {
        const val ACTION_USB_PERMISSION: String = "com.android.example.USB_PERMISSION"
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            logDebug("${intent?.action}")
            if (ACTION_USB_PERMISSION == intent?.action) {
                synchronized(this) {
                    device?.let { OpenTask(reader, it).execute() }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent?.action) {
                synchronized(this) {
                    CloseTask(reader).execute()
                }
            }
        }
    }

    private lateinit var manager: UsbManager
    private lateinit var reader: Reader
    private lateinit var pendingIntent: PendingIntent
    private var device: UsbDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        manager = getSystemService(Context.USB_SERVICE) as UsbManager
        reader = Reader(manager)
        reader.setOnStateChangeListener { slotNumber, previousState, currentState ->
            logDebug("$slotNumber - $previousState - $currentState")
            if (currentState == Reader.CARD_PRESENT) {
                logDebug("Device is ready to read")
                try {
                    val command = byteArrayOf(
                        0xFF.toByte(),
                        0xCA.toByte(),
                        0x00.toByte(),
                        0x00.toByte(),
                        0x00.toByte(),
                    )
                    val response = ByteArray(256)
                    val byteCount: Int = reader.control(
                        slotNumber,
                        Reader.IOCTL_CCID_ESCAPE,
                        command,
                        command.size,
                        response,
                        response.size,
                    )
                    val uid = StringBuffer()
                    for (i in 0 until (byteCount - 2)) {
                        uid.append(String.format("%02X", response[i]))
                    }
                    logDebug(uid)
                    runOnUiThread { findViewById<TextView>(R.id.uidTextView).text = uid }
                } catch (exc: Exception) {
                    exc.printStackTrace()
                }
            }
        }

        pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(receiver, filter)

        manager.deviceList.values.forEach {
            logDebug("${it.deviceName} - ${it.productName}")
            val isAcr122u = true == it.productName?.contains("ACR122U", true)
            if (reader.isSupported(it) && isAcr122u) {
                device = it
                logDebug("Device assigned: ${device?.productName}")
            }
        }
        device?.let { manager.requestPermission(it, pendingIntent) }
    }

    override fun onDestroy() {
        reader.close()
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun logDebug(content: Any) {
        Log.d(MainActivity::class.java.simpleName, "$content")
    }
}

class OpenTask(
    private val reader: Reader,
    private val usbDevice: UsbDevice
) : AsyncTask<Void, Void, Void?>() {
    override fun doInBackground(vararg params: Void?): Void? {
        return try {
            reader.open(usbDevice)
            null
        } catch (exc: Exception) {
            exc.printStackTrace()
            null
        }
    }
}

class CloseTask(private val reader: Reader) : AsyncTask<Void, Void, Void?>() {
    override fun doInBackground(vararg params: Void?): Void? {
        reader.close()
        return null
    }
}