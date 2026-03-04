# Facebook Video Downloader (Android)

A sleek, native Android application built with Kotlin that allows users to download public Facebook videos directly to their device. The app features a custom Facebook Dark Theme, a live download progress tracker, an automated download history log, and a built-in media player.

## 🌟 Features

* **Authentic UI:** A polished Facebook Dark Theme built with custom hex codes (`#18191A`, `#1877F2`, etc.) for a native feel.
* **Smart Extraction:** Automatically parses JSON data to find the highest quality (HD) MP4 link available, with a seamless fallback to SD if HD is missing.
* **Live Progress Bar:** The download button transforms into a real-time chunk-by-chunk percentage tracker during active downloads.
* **Gallery Integration:** Videos are saved to a dedicated `Downloads/FB_Downloader` folder and instantly indexed to the native Android Gallery via `MediaScannerConnection`.
* **In-App Media Player:** The "Recent Downloads" history feed dynamically extracts video thumbnails. Tapping any history card launches a custom, floating media player to watch the video without leaving the app.

## ⚠️ Limitations

* **Public Videos Only:** This application is designed specifically for publicly shared content. Due to Facebook's security algorithms and session authentication barriers, **this app cannot download videos from Private Groups or locked private profiles**. 

## 🛠️ Tech Stack

* **Language:** Kotlin
* **Architecture:** Android SDK (Minimum SDK 24 / Target SDK 36)
* **Network Engine:** `OkHttp 4.12.0` (for handling REST API calls and manual byte-stream downloading)
* **JSON Parsing:** Native `org.json.JSONObject`
* **Concurrency:** Built-in Android `Thread` and `runOnUiThread` processing.

## 🔌 API Integration

This project is powered by a third-party RapidAPI endpoint to handle the extraction of Facebook media links.

* **API Used:** [Free Facebook Downloader by joshimuddin](https://rapidapi.com/joshimuddin8212/api/free-facebook-downloader)
* **Endpoint Host:** `free-facebook-downloader.p.rapidapi.com`

## 🚀 Setup & Installation

If you want to clone this repository and run it on your own machine, you will need to supply your own RapidAPI key.

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/YOUR_USERNAME/Facebook_Video_Downloader.git](https://github.com/YOUR_USERNAME/Facebook_Video_Downloader.git)
    ```
2.  **Open in Android Studio:**
    Allow Gradle to sync and download the required `OkHttp` dependencies.
3.  **Get an API Key:**
    * Create an account at [RapidAPI.com](https://rapidapi.com).
    * Subscribe to the free tier of the [Free Facebook Downloader API](https://rapidapi.com/joshimuddin8212/api/free-facebook-downloader).
    * Copy your unique `x-rapidapi-key`.
4.  **Configure the Vault:**
    Navigate to `app/src/main/java/com/example/facebook_video_downloader/ApiConfig.kt` and paste your key:
    ```kotlin
    object ApiConfig {
        const val RAPID_API_KEY = "YOUR_API_KEY_HERE" 
        const val RAPID_API_HOST = "free-facebook-downloader.p.rapidapi.com" 
    }
    ```
5.  **Build and Run!**

## 🤝 Acknowledgments

* Application architecture, Kotlin debugging, and logic design assisted by **Google's Gemini**.

## ⚖️ Disclaimer

This application is built for educational and personal use only. Downloading copyrighted material without permission is against Facebook's Terms of Service. The developer of this repository is not responsible for any misuse of this application or the associated third-party APIs.
