package com.example.Recorder

import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.Recorder.databinding.ActivityMainBinding
import java.io.IOException
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.provider.MediaStore.Audio.Media
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // BUTTONS
    private lateinit var recordButton: ImageButton
    private lateinit var stopButton: ImageButton
    private lateinit var playButton: ImageButton

    // MEDIA RECORDER
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: String? = null
    private var mediaPlayer: MediaPlayer? = null

    // DATABASE HELPER
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var spinner: Spinner
    private lateinit var spinnerAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view: View = binding.root
        setContentView(view)

        // Initialize DatabaseHelper
        dbHelper = DatabaseHelper(this)

        recordButton = binding.recordButton
        stopButton = binding.stopButton
        playButton = binding.playButton
        spinner = binding.spinner

        // Initialize Spinner Adapter
        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ArrayList())
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter

        // Main functions
        recordButton.setOnClickListener {
            requestPermissionHandler()
            Log.d("Running", "Request for permission to access device resources.")
        }

        stopButton.setOnClickListener {
            stop()
            Log.d("Running", "Recording stopped.")
        }

        playButton.setOnClickListener {
            play()
            Log.d("Running", "Playing recording.")
        }

        loadRecordings()
    }

    private fun record() {
        try {
            Toast.makeText(this, "Recording.", Toast.LENGTH_SHORT).show()
            /*
            Desactivamos todos los botones menos el de detener la grabación, puesto que
            con una grabación en ejecución no resultan de utilidad.
            */
            recordButton.isEnabled = false
            stopButton.isEnabled = true

            //Indicamos el directorio en el que se alojará la grabación al finalizar.
            outputFile = getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.absolutePath +
                    "/recording_${System.currentTimeMillis()}.3gp"

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile)

                prepare()
                start()

            }
            val timestamp = SimpleDateFormat("HH:mm:ss dd-MM-yyyy", Locale.getDefault()).format(Date())
            dbHelper.insertRecording(outputFile!!, timestamp)
            loadRecordings()
        } catch (e: IOException) {
            e.printStackTrace()
            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle("Error")
            builder.setMessage("Unable to record: ${e.message}")
            builder.setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            val dialog = builder.create()
            dialog.show()
        }
    }

    private fun stop() {
        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
        recordButton.isEnabled = true
        stopButton.isEnabled = false
        // Verify if mediaRecorder is not null before stop it.
        if (mediaRecorder != null) {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    private fun play() {
        try {
            val selectedTimestamp = spinner.selectedItem as String
            val cursor = dbHelper.getRecordings()
            var filePath: String? = null

            if (cursor.moveToFirst()) {
                do {
                    val timestamp = cursor.getString(cursor.getColumnIndexOrThrow("timestamp"))
                    if (timestamp == selectedTimestamp) {
                        filePath = cursor.getString(cursor.getColumnIndexOrThrow("file_path"))
                        break
                    }
                } while (cursor.moveToNext())
            }
            cursor.close()

            if (filePath != null && File(filePath).exists()) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    start()
                    Toast.makeText(this@MainActivity, "Playing recording", Toast.LENGTH_SHORT)
                        .show()

                    setOnCompletionListener {
                        release()
                        mediaPlayer = null
                        Toast.makeText(this@MainActivity, "Ready to record", Toast.LENGTH_SHORT)
                            .show()
                        recordButton.isEnabled = true
                        stopButton.isEnabled = false
                        playButton.isEnabled = true
                    }
                }
            }
        } catch (ioe: IOException) {
            ioe.printStackTrace()
            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle("Error")
            builder.setMessage("Unable to record: ${ioe.message}")
            builder.setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            val dialog = builder.create()
            dialog.show()
        } catch (ise: IllegalStateException) {
            ise.printStackTrace()
            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle("Error")
            builder.setMessage("Unable to record: ${ise.message}")
            builder.setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            val dialog = builder.create()
            dialog.show()
        }

    }


// Add recordings to database

    private fun loadRecordings() {
        val cursor = dbHelper.getRecordings()
        val recordingsList = ArrayList<String>()
        if (cursor.moveToFirst()) {
            do {
                val timestamp = cursor.getString(cursor.getColumnIndexOrThrow("timestamp"))
                recordingsList.add(timestamp)
            } while (cursor.moveToNext())
        }
        cursor.close()
        spinnerAdapter.clear()
        spinnerAdapter.addAll(recordingsList)
        spinnerAdapter.notifyDataSetChanged()
    }

// Permissions to access device resources.

    val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted: Boolean ->
            if (isGranted) {
                record()
                Log.d("Running", "Recording audio.")
            } else {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Permission denied")
                builder.setMessage("Audio recording requires microphone permission.")
                builder.setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                val dialog = builder.create()
                dialog.show()
            }
        }

    private fun requestPermissionHandler() {
        when {
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                record()
                Log.d("Running", "Recording audio.")
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) -> {
                    val builder = AlertDialog.Builder(this)
                    builder.setTitle("Permission needed")
                    builder.setMessage("It is necessary to grant permission to access the microphone to record audio.")
                    builder.setPositiveButton("OK") { dialog, _ ->

                        requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                    val dialog = builder.create()
                    dialog.show()
                }
            }
        }
    }
