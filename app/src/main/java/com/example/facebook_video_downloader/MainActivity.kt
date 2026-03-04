package com.example.facebook_video_downloader

import android.content.ClipboardManager
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var pasteButton: Button
    private lateinit var downloadButton: Button
    private lateinit var historyContainer: LinearLayout
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Link the UI elements to the dark theme layout
        urlInput = findViewById(R.id.urlInput)
        pasteButton = findViewById(R.id.pasteButton)
        downloadButton = findViewById(R.id.downloadButton)
        historyContainer = findViewById(R.id.historyContainer)

        // 2. Setup the Paste Button
        pasteButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val pasteData = clipboard.primaryClip?.getItemAt(0)?.text
            if (!pasteData.isNullOrEmpty()) {
                urlInput.setText(pasteData)
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Setup the Download Button
        downloadButton.setOnClickListener {
            val fbUrl = urlInput.text.toString().trim()
            if (fbUrl.isNotEmpty()) {
                Toast.makeText(this, "Extracting video data...", Toast.LENGTH_SHORT).show()
                extractFacebookVideo(fbUrl)
            } else {
                Toast.makeText(this, "Please enter a valid Facebook link", Toast.LENGTH_SHORT).show()
            }
        }
        // 4. Load history when the app opens!
        refreshHistory()
    }

    private fun extractFacebookVideo(videoUrl: String) {
        val apiUrl = "https://${ApiConfig.RAPID_API_HOST}/get_media"

        // 1. Package the URL into a JSON body format exactly like the cURL data
        val jsonPayload = """{"url":"$videoUrl"}"""
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonPayload.toRequestBody(mediaType)

        // 2. Build the POST request
        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody) // Send the JSON body
            .addHeader("x-rapidapi-host", ApiConfig.RAPID_API_HOST)
            .addHeader("x-rapidapi-key", ApiConfig.RAPID_API_KEY)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()

                    // IMPORTANT: Log the raw response so we can see what the API hands back!
                    println("API_RESPONSE_DATA: $responseBody")

                    try {
                        val jsonObject = JSONObject(responseBody!!)

                        // TEMPORARY: We need to see the JSON structure in Logcat to know the exact key.
                        // I'm assuming it might be 'url' or 'video' for now.
                        val videoLink = jsonObject.getString("direct_media_url")

                        downloadAndSaveVideo(videoLink)
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Failed to parse API data. Check Logcat!", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "API Error: ${response.code}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun downloadAndSaveVideo(mp4Url: String) {
        val request = Request.Builder().url(mp4Url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed to download MP4", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val inputStream = response.body?.byteStream()

                    // Save to the Downloads/FB_Downloader folder
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val customDir = File(downloadsDir, "FB_Downloader")
                    if (!customDir.exists()) customDir.mkdirs()

                    val fileName = "FB_Vid_${System.currentTimeMillis()}.mp4"
                    val file = File(customDir, fileName)
                    val outputStream = FileOutputStream(file)

                    inputStream?.copyTo(outputStream)
                    outputStream.close()
                    inputStream?.close()

                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "ARCHIVED: Saved to Gallery", Toast.LENGTH_LONG).show()

                        // Tell the UI to update the list immediately!
                        refreshHistory()

                        // Instantly ping the Android Gallery to index the new video!
                        MediaScannerConnection.scanFile(
                            this@MainActivity,
                            arrayOf(file.absolutePath),
                            arrayOf("video/mp4"),
                            null
                        )
                    }
                }
            }
        })
    }

    private fun refreshHistory() {
        historyContainer.removeAllViews()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val customDir = File(downloadsDir, "FB_Downloader")

        if (customDir.exists()) {
            val files = customDir.listFiles()?.sortedByDescending { it.lastModified() }

            files?.forEach { file ->
                val textView = TextView(this).apply {
                    text = "▶ ${file.name}" // Added a play icon!
                    setTextColor(android.graphics.Color.parseColor("#E4E6EB"))
                    setPadding(0, 24, 0, 24) // Added a bit more padding for easier finger tapping
                    textSize = 15f

                    // THE NEW UPGRADE: Make the text clickable!
                    setOnClickListener {
                        showVideoDialog(file)
                    }
                }
                historyContainer.addView(textView)
            }
        }
    }

    private fun showVideoDialog(videoFile: File) {
        // Create a fullscreen black dialog
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        // Initialize the video player and controls
        val videoView = android.widget.VideoView(this)
        val mediaController = android.widget.MediaController(this)

        // Attach the controls to the video player
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)
        videoView.setVideoPath(videoFile.absolutePath)

        // Make the video player fill the screen
        val layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )

        dialog.setContentView(videoView, layoutParams)

        // Auto-play the video as soon as it loads
        videoView.setOnPreparedListener {
            it.start()
        }

        dialog.show()
    }
}