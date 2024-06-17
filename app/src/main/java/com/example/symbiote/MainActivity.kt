package com.example.symbiote

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var startStopButton: Button
    private lateinit var timeSeekBar: SeekBar
    private lateinit var timeText: TextView
    private lateinit var timerProgress: ProgressBar
    private lateinit var timerText: TextView
    private lateinit var macAddressEditText: EditText
    private lateinit var connectButton: Button
    private lateinit var terminalTextView: TextView

    private var isCollectingData = false
    private lateinit var timer: CountDownTimer
    private var selectedTimeInMinutes: Int = 2

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private val serverUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var serverMacAddress: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startStopButton = findViewById(R.id.startStopButton)
        timeSeekBar = findViewById(R.id.timeSeekBar)
        timeText = findViewById(R.id.timeText)
        timerProgress = findViewById(R.id.timerProgress)
        timerText = findViewById(R.id.timerText)
        macAddressEditText = findViewById(R.id.macAddressEditText)
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

        timeSeekBar.max = 10
        timeSeekBar.progress = selectedTimeInMinutes
        timeText.text = "Timer: ${selectedTimeInMinutes}m"

        timeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedTimeInMinutes = progress
                timeText.text = "Timer: ${selectedTimeInMinutes}m"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            finish()
        }

        if (!bluetoothAdapter.isEnabled) {
            bluetoothAdapter.enable()
        }

        // Request Bluetooth permissions at runtime
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_SCAN), 2)
        }

        connectButton.setOnClickListener {
            if (!isCollectingData) {
                val macAddress = macAddressEditText.text.toString().trim()
                if (macAddress.isNotEmpty()) {
                    serverMacAddress = macAddress
                    Log.d("Bluetooth-Symbiote2", serverMacAddress)
                    connectToBluetoothDevice()
                } else {
                    Toast.makeText(this, "Please enter a Bluetooth MAC address", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
        isCollectingData = true
        startStopButton.text = "Stop"
        startTimer(selectedTimeInMinutes)
        connectToBluetoothDevice()
    }

    private fun stopDataCollection() {
        isCollectingData = false
        startStopButton.text = "Start"
        timer.cancel()
        timerProgress.progress = 0
        timerText.text = "Timer: 0s"
        Toast.makeText(this, "Data collection stopped", Toast.LENGTH_SHORT).show()
        disconnectFromBluetoothDevice()
    }

    private fun startTimer(minutes: Int) {
        val millisInFuture = minutes * 60 * 1000L
        val secondsTotal = minutes * 60f
        timer = object : CountDownTimer(millisInFuture, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                timerText.text = "Timer: ${secondsRemaining}s"
                val progress = ((secondsTotal - secondsRemaining) / secondsTotal * 100).toInt()
                Log.d("Timer", progress.toString())
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
        terminalTextView.text = "${terminalTextView.text} connecting ...\n"
        thread {
            try {
                Log.d("Bluetooth", "Attempting to connect to device: $serverMacAddress")
                socket = createBluetoothSocket(device)
                socket?.connect()
                inputStream = socket?.inputStream
                runOnUiThread {
                    terminalTextView.text = "${terminalTextView.text} connected to bluetooth device 🔥\n"
                }
                readDataFromBluetooth()
            } catch (e: IOException) {
                Log.e("Bluetooth-Symbiote", "Failed to connect: ${e.message}")
                e.printStackTrace()
                runOnUiThread {
                    terminalTextView.text = "${terminalTextView.text}/> failed to connect to bluetooth device \n"
                }
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
        val buffer = ByteArray(1024)
        var bytes: Int
        while (isCollectingData) {
            try {
                bytes = inputStream?.read(buffer) ?: -1
                if (bytes > 0) {
                    val readMessage = String(buffer, 0, bytes)
                    Log.d("BluetoothData", readMessage)
                    terminalTextView.text = "${terminalTextView}/> $readMessage\n"
                }
            } catch (e: IOException) {
                Log.e("Bluetooth", "Error reading data: ${e.message}")
                e.printStackTrace()
                break
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
}
