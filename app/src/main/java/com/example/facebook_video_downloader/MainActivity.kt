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
                extractFacebookVideo(fbUrl) // <--- Pointing back to the API!
            } else {
                Toast.makeText(this, "Please enter a valid Facebook link", Toast.LENGTH_SHORT).show()
            }
        }
        // 4. Load history when the app opens!
        refreshHistory()
    }

    private fun extractFacebookVideo(videoUrl: String) {
        // Encode the URL so the API can read it safely
        val encodedUrl = java.net.URLEncoder.encode(videoUrl, "UTF-8")
        val apiUrl = "https://${ApiConfig.RAPID_API_HOST}/external-api/facebook-video-downloader?url=$encodedUrl"

        // Send an empty JSON body as required by this specific API
        val jsonPayload = "{}"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonPayload.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody)
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

                    try {
                        val jsonObject = JSONObject(responseBody!!)
                        val linksObject = jsonObject.getJSONObject("links")

                        // Smart Selection: Try High Quality first, fallback to Low Quality
                        val videoLink = if (linksObject.has("Download High Quality")) {
                            linksObject.getString("Download High Quality")
                        } else {
                            linksObject.getString("Download Low Quality")
                        }

                        downloadAndSaveVideo(videoLink)
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Failed to parse API data.", Toast.LENGTH_LONG).show()
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
                    downloadButton.text = "Download Video"
                    downloadButton.isEnabled = true
                    Toast.makeText(this@MainActivity, "Failed to download MP4", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body
                    val contentLength = body?.contentLength() ?: -1L
                    val inputStream = body?.byteStream()

                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val customDir = File(downloadsDir, "FB_Downloader")
                    if (!customDir.exists()) customDir.mkdirs()

                    val fileName = "FB_Vid_${System.currentTimeMillis()}.mp4"
                    val file = File(customDir, fileName)
                    val outputStream = FileOutputStream(file)

                    // The New Engine: Download chunk by chunk!
                    val buffer = ByteArray(4096)
                    var bytesCopied = 0L
                    var bytesRead: Int
                    var lastProgress = -1

                    // Disable the button so the user can't spam it
                    runOnUiThread {
                        downloadButton.isEnabled = false
                    }

                    while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead

                        // Calculate percentage and update the UI
                        if (contentLength > 0) {
                            val progress = ((bytesCopied * 100) / contentLength).toInt()

                            // Only update the UI if the percentage actually changed (prevents lag)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                runOnUiThread {
                                    downloadButton.text = "Downloading... $progress%"
                                }
                            }
                        } else {
                            // If the API hides the file size, just show MB downloaded
                            val mbDownloaded = bytesCopied / (1024 * 1024)
                            runOnUiThread {
                                downloadButton.text = "Downloading... ${mbDownloaded}MB"
                            }
                        }
                    }

                    outputStream.close()
                    inputStream?.close()

                    runOnUiThread {
                        // Reset the button
                        downloadButton.text = "Download Video"
                        downloadButton.isEnabled = true

                        Toast.makeText(this@MainActivity, "ARCHIVED: Saved to Gallery", Toast.LENGTH_LONG).show()
                        refreshHistory()

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
        runOnUiThread {
            historyContainer.removeAllViews()
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val customDir = File(downloadsDir, "FB_Downloader")

            if (!customDir.exists() || customDir.listFiles().isNullOrEmpty()) {
                val emptyText = TextView(this)
                emptyText.text = "No recent downloads."
                emptyText.setTextColor(android.graphics.Color.parseColor("#B0B3B8"))
                historyContainer.addView(emptyText)
                return@runOnUiThread
            }

            val files = customDir.listFiles()?.filter { it.extension == "mp4" }?.sortedByDescending { it.lastModified() } ?: return@runOnUiThread
            val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy • HH:mm", java.util.Locale.getDefault())

            // Automatically calculate the perfect thumbnail size for the user's screen
            val imageSize = (80 * resources.displayMetrics.density).toInt()
            val marginEnd = (16 * resources.displayMetrics.density).toInt()

            for (file in files) {
                // 1. Create a Horizontal Container (The Card)
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setBackgroundColor(android.graphics.Color.parseColor("#242526"))
                    setPadding(40, 40, 40, 40)
                    gravity = android.view.Gravity.CENTER_VERTICAL

                    val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    params.setMargins(0, 0, 0, 20)
                    layoutParams = params

                    // The entire card remains clickable
                    setOnClickListener { playVideoInApp(file.absolutePath) }
                }

                // 2. Create the Thumbnail Image Block
                val thumbnailView = android.widget.ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(imageSize, imageSize).apply {
                        setMargins(0, 0, marginEnd, 0) // Keep it separated from the text
                    }
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(android.graphics.Color.parseColor("#18191A")) // Dark loading placeholder
                }

                // 3. Extract the Video Frame in the Background! (Prevents lag)
                Thread {
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(file.absolutePath)
                        // Grab a high-quality frame at the 1-second mark
                        val bitmap = retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        retriever.release()

                        // Push the finished image back to the main screen
                        if (bitmap != null) {
                            runOnUiThread { thumbnailView.setImageBitmap(bitmap) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()

                // 4. Create the Text Block (I removed the play icon since we have a picture now!)
                val textView = TextView(this).apply {
                    val dateString = dateFormat.format(java.util.Date(file.lastModified()))
                    text = "${file.name}\n$dateString"
                    setTextColor(android.graphics.Color.parseColor("#E4E6EB"))
                    textSize = 14f
                }

                // 5. Assemble the Card and put it on the screen
                card.addView(thumbnailView)
                card.addView(textView)
                historyContainer.addView(card)
            }
        }
    }

    private fun playVideoInApp(videoPath: String) {
        val dialog = android.app.Dialog(this)
        val videoView = android.widget.VideoView(this)
        val mediaController = android.widget.MediaController(this)

        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)
        videoView.setVideoPath(videoPath)

        val layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT // This keeps it as a neat popup!
        )
        videoView.layoutParams = layoutParams

        dialog.setContentView(videoView)
        dialog.show()
        videoView.start()
    }
}