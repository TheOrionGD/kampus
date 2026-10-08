package com.example.kampus

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Cloudinary Unsigned Image Upload Helper for Kampus Platform.
 * Direct HTTPS CDN uploads for student profile photos, posters, and campus media.
 */
object CloudinaryHelper {
    private const val TAG = "CloudinaryHelper"

    // Cloudinary Credentials & Configuration
    private const val CLOUD_NAME = "s5nphyoh"
    private const val UPLOAD_PRESET = "kampus_preset"
    private const val DEFAULT_FOLDER = "kampus_uploads"

    private const val UPLOAD_URL = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload"
    private val gson = Gson()

    /**
     * Uploads bitmap image directly to Cloudinary using Unsigned Upload Preset.
     * Returns the HTTPS CDN secure_url string on success, or null on failure.
     */
    suspend fun uploadBitmapToCloudinary(
        bitmap: Bitmap,
        folder: String = DEFAULT_FOLDER
    ): String? = withContext(Dispatchers.IO) {
        try {
            // Compress bitmap to JPEG byte array
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
            val imageBytes = byteArrayOutputStream.toByteArray()

            val boundary = "---KampusCloudinaryBoundary${System.currentTimeMillis()}"
            val LINE_FEED = "\r\n"

            val url = URL(UPLOAD_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                useCaches = false
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                connectTimeout = 15000
                readTimeout = 20000
            }

            val outputStream = DataOutputStream(connection.outputStream)

            // Add upload_preset field
            outputStream.writeBytes("--$boundary$LINE_FEED")
            outputStream.writeBytes("Content-Disposition: form-data; name=\"upload_preset\"$LINE_FEED$LINE_FEED")
            outputStream.writeBytes("$UPLOAD_PRESET$LINE_FEED")

            // Add folder field
            val targetFolder = if (folder.isNotBlank()) folder else DEFAULT_FOLDER
            outputStream.writeBytes("--$boundary$LINE_FEED")
            outputStream.writeBytes("Content-Disposition: form-data; name=\"folder\"$LINE_FEED$LINE_FEED")
            outputStream.writeBytes("$targetFolder$LINE_FEED")

            // Add file binary payload
            val filename = "kampus_img_${UUID.randomUUID().toString().take(8)}.jpg"
            outputStream.writeBytes("--$boundary$LINE_FEED")
            outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"$LINE_FEED")
            outputStream.writeBytes("Content-Type: image/jpeg$LINE_FEED$LINE_FEED")
            outputStream.write(imageBytes)
            outputStream.writeBytes(LINE_FEED)

            // Finish multipart data
            outputStream.writeBytes("--$boundary--$LINE_FEED")
            outputStream.flush()
            outputStream.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val responseStr = reader.use { it.readText() }
                val jsonObject = gson.fromJson(responseStr, JsonObject::class.java)
                val secureUrl = jsonObject?.get("secure_url")?.asString ?: jsonObject?.get("url")?.asString

                if (!secureUrl.isNullOrBlank()) {
                    Log.d(TAG, "CLOUDINARY_UPLOAD_SUCCESS: $secureUrl")
                    return@withContext secureUrl
                }
            } else {
                val errorStream = connection.errorStream
                val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "CLOUDINARY_UPLOAD_ERROR: HTTP $responseCode - $errorResponse")
            }
        } catch (e: Exception) {
            Log.e(TAG, "CLOUDINARY_UPLOAD_EXCEPTION: ${e.message}")
        }
        return@withContext null
    }
}
