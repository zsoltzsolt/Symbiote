package com.example.symbiote

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.concurrent.thread

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var startStopButton: Button
    private lateinit var timeSeekBar: SeekBar
    private lateinit var timeText: TextView
    private lateinit var timerProgress: ProgressBar
    private lateinit var timerText: TextView
    private lateinit var macAddressEditText: EditText
    private lateinit var innerSpeechEditText: EditText
    private lateinit var connectButton: Button
    private lateinit var terminalTextView: TextView

    private var isCollectingData = false
    private var connected = false
    private lateinit var timer: CountDownTimer
    private var selectedTimeInMinutes: Int = 2

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private val serverUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var serverMacAddress: String = ""

    private var messages: String = ""

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        BluetoothAdapter.STATE_OFF -> {
                            if (terminalTextView.text.equals("/>"))
                                terminalTextView.text = ""
                            terminalTextView.text =
                                "${terminalTextView.text}/> bluetooth turned OFF\n"
                        }

                        BluetoothAdapter.STATE_ON -> {
                            if (terminalTextView.text.equals("/>"))
                                terminalTextView.text = ""
                            terminalTextView.text =
                                "${terminalTextView.text}/> bluetooth turned ON\n"
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        startStopButton = findViewById(R.id.startStopButton)
        timeSeekBar = findViewById(R.id.timeSeekBar)
        timeText = findViewById(R.id.timeText)
        timerProgress = findViewById(R.id.timerProgress)
        timerText = findViewById(R.id.timerText)
        macAddressEditText = findViewById(R.id.macAddressEditText)
        innerSpeechEditText = findViewById(R.id.innerSpeechEditText)
        connectButton = findViewById(R.id.connectButton)
        terminalTextView = findViewById(R.id.terminalTextView)

        terminalTextView.text = "/>"

        startStopButton.setOnClickListener {
            if (isCollectingData) {
                stopDataCollection()
            } else {
                startDataCollection()
            }
        }

        timeSeekBar.min = 1
        timeSeekBar.max = 10
        timeSeekBar.progress = selectedTimeInMinutes
        timeText.text = "Duration: ${selectedTimeInMinutes}m"
        timerText.text = "Remaining: 0s"

        timeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedTimeInMinutes = progress
                timeText.text = "Duration: ${selectedTimeInMinutes}m"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            finish()
        }

        Log.d("BT_Status", bluetoothAdapter.isEnabled.toString() + (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)!= PackageManager.PERMISSION_GRANTED).toString())

        if (!bluetoothAdapter.isEnabled) {
            if (terminalTextView.text.equals("/>"))
                terminalTextView.text = ""
            terminalTextView.text = "${terminalTextView.text}/> activate bluetooth\n"
            bluetoothAdapter.enable()
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                1
            )
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_SCAN), 2)
        }

        connectButton.setOnClickListener {
            if (!isCollectingData) {
                val macAddress = macAddressEditText.text.toString().trim()
                val macAddressRegex = Regex("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}")

                if (macAddress.isNotEmpty()) {
                    if (macAddressRegex.matches(macAddress)) {
                        serverMacAddress = macAddress.uppercase()
                        connectToBluetoothDevice()
                    } else {
                        if (terminalTextView.text.equals("/>"))
                            terminalTextView.text = ""
                        terminalTextView.text = "${terminalTextView.text}/> invalid MAC address\n"
                    }
                } else {
                    if (terminalTextView.text.equals("/>"))
                        terminalTextView.text = ""
                    terminalTextView.text = "${terminalTextView.text}/> please enter a bluetooth MAC address\n"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    private fun startDataCollection() {
        if (connected) {
            if (innerSpeechEditText.text.toString().isBlank()) {
                terminalTextView.text = "${terminalTextView.text}/> inner speech label is empty\n"
            } else {
                isCollectingData = true
                startStopButton.text = "Stop"
                startTimer(selectedTimeInMinutes)
                readDataFromBluetooth()
            }
        } else {
            terminalTextView.text = "${terminalTextView.text}/> device is not connected\n"
        }
    }

    private fun stopDataCollection() {
        isCollectingData = false
        startStopButton.text = "Start"
        timer.cancel()
        timerProgress.progress = 0
        timerText.text = "Remaining: 0s"
        connectButton.text = "Connect"
        connectButton.isEnabled = true
        connectButton.alpha = 1.0f
        connected = false
        terminalTextView.text = "${terminalTextView.text}/> data collection finished\n"
        disconnectFromBluetoothDevice()
    }

    private fun startTimer(minutes: Int) {
        val millisInFuture = minutes * 60 * 1000L
        val secondsTotal = minutes * 60f
        timer = object : CountDownTimer(millisInFuture, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                timerText.text = "Remaining: ${secondsRemaining}s"
                val progress = ((secondsTotal - secondsRemaining) / secondsTotal * 100).toInt()
                Log.d("Remaining", progress.toString())
                timerProgress.progress = progress
            }

            override fun onFinish() {
                stopDataCollection()
            }
        }
        timer.start()
    }

    private fun connectToBluetoothDevice() {
        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(serverMacAddress)
        if (terminalTextView.text.equals("/>"))
            terminalTextView.text = ""
        terminalTextView.text = "${terminalTextView.text}/> connecting ...\n"
        thread {
            try {
                Log.d("Bluetooth", "Attempting to connect to device: $serverMacAddress")
                socket = createBluetoothSocket(device)
                socket?.connect()
                inputStream = socket?.inputStream
                runOnUiThread {
                    terminalTextView.text =
                        "${terminalTextView.text}/> connected to bluetooth device 🔥\n"
                    connectButton.text = "Connected"
                    connectButton.isEnabled = false
                    connectButton.alpha = 0.5f
                }
                connected = true
            } catch (e: IOException) {
                Log.e("Bluetooth-Symbiote", "Failed to connect: ${e.message}")
                e.printStackTrace()
                runOnUiThread {
                    terminalTextView.text =
                        "${terminalTextView.text}/> failed to connect to bluetooth device \n"
                }
                connected = false
            }
        }
    }

    private fun createBluetoothSocket(device: BluetoothDevice): BluetoothSocket? {
        return try {
            device.createRfcommSocketToServiceRecord(serverUUID)
        } catch (e: IOException) {
            Log.e("Bluetooth", "Could not create RFCOMM socket: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun readDataFromBluetooth() {
        thread {
            val buffer = ByteArray(100)
            var bytes: Int

            while (isCollectingData) {
                try {
                    bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        val readMessage = String(buffer, 0, bytes)

                        synchronized(messages) {
                            messages += readMessage
                        }
                    }
                } catch (e: IOException) {
                    Log.e("Bluetooth", "Error reading data: ${e.message}")
                    e.printStackTrace()
                    runOnUiThread {
                        if (timerProgress.progress != 0) {
                            terminalTextView.text =
                                "${terminalTextView.text}/> bluetooth disconnected or no data received.\n"
                            stopDataCollection()
                        }
                    }
                    break
                }
            }
            synchronized(messages) {
                sendCollectedData(messages)
                messages = ""
            }
        }
    }

    private fun disconnectFromBluetoothDevice() {
        try {
            inputStream?.close()
            socket?.close()
            Log.d("Bluetooth", "Disconnected from Bluetooth device")
        } catch (e: IOException) {
            Log.e("Bluetooth", "Error disconnecting: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun sendCollectedData(messages: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val formattedData = formatData(messages)
            val response = postToApi(formattedData)

            withContext(Dispatchers.Main) {
                Log.d("APIResponse", response)
            }
        }
    }

    private fun formatData(messages: String): JSONObject {
        val rows = messages.split("\n")

        val channels = JSONArray()
        val columns = List(8) { mutableListOf<Int>() }

        for (row in rows) {
            if (row.count { it == ',' } == 7) {
                val values = row.split(",")
                for (i in values.indices) {
                    columns[i].add(values[i].trim().toInt())
                }
            }
        }

        for (i in columns.indices) {
            val channel = JSONObject().apply {
                put("waveType", 1)
                put("number", i.toString())
                put("voltage", JSONArray(columns[i]))
            }
            channels.put(channel)
        }

        return JSONObject().apply {
            put("channels", channels)
            put("mentalImage", innerSpeechEditText.text.toString().trim())
        }
    }

    private fun postToApi(data: JSONObject): String {
        val url = URL("http://192.168.1.102:5127/api/record")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream).use { it.write(data.toString()) }

            val responseCode = connection.responseCode
            val responseMessage = connection.inputStream.bufferedReader().use { it.readText() }

            "$responseCode: $responseMessage"
        } catch (e: IOException) {
            e.printStackTrace()
            "Failed to send data: ${e.message}"
        } finally {
            connection.disconnect()
        }
    }
}
