package com.natura.camera.camera

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a saved photo or video in the NATURA directory.
 */
data class NaturaMediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val isVideo: Boolean,
    val hasDng: Boolean = false,
    val dateAdded: Long = 0L
)

object MediaStoreSaver {
    private const val TAG = "NaturaMediaStore"
    private const val FOLDER_PICTURES = "Pictures/NATURA"
    private const val FOLDER_MOVIES = "Movies/NATURA"

    fun generateBaseName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "NATURA_${sdf.format(Date())}"
    }

    /**
     * Saves a JPEG image to Pictures/NATURA in scoped storage.
     */
    fun saveJpeg(
        context: Context,
        baseName: String,
        jpegBytes: ByteArray
    ): Uri? {
        val resolver = context.contentResolver
        val filename = "$baseName.jpg"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, FOLDER_PICTURES)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(jpegBytes)
                    out.flush()
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing JPEG to MediaStore", e)
                resolver.delete(uri, null, null)
                null
            }
        } else {
            // Android 8.0 - 9 legacy path
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val naturaDir = File(picturesDir, "NATURA")
            if (!naturaDir.exists()) naturaDir.mkdirs()
            val file = File(naturaDir, filename)
            try {
                FileOutputStream(file).use { out ->
                    out.write(jpegBytes)
                    out.flush()
                }
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, file.absolutePath)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing legacy JPEG", e)
                null
            }
        }
    }

    /**
     * Prepares an OutputStream to write DNG raw data into Pictures/NATURA.
     */
    fun createDngStream(
        context: Context,
        baseName: String
    ): Pair<Uri, OutputStream>? {
        val resolver = context.contentResolver
        val filename = "$baseName.dng"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
                put(MediaStore.Images.Media.RELATIVE_PATH, FOLDER_PICTURES)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            val stream = resolver.openOutputStream(uri)
            if (stream != null) {
                Pair(uri, stream)
            } else {
                resolver.delete(uri, null, null)
                null
            }
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val naturaDir = File(picturesDir, "NATURA")
            if (!naturaDir.exists()) naturaDir.mkdirs()
            val file = File(naturaDir, filename)
            try {
                val stream = FileOutputStream(file)
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, file.absolutePath)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) Pair(uri, stream) else null
            } catch (e: Exception) {
                Log.e(TAG, "Failed creating legacy DNG file", e)
                null
            }
        }
    }

    fun finishDng(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    /**
     * Prepares a pending video file in Movies/NATURA.
     */
    fun createVideoPendingFile(
        context: Context,
        baseName: String
    ): Pair<Uri, ParcelFileDescriptor>? {
        val resolver = context.contentResolver
        val filename = "${baseName}.mp4"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, FOLDER_MOVIES)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            val pfd = resolver.openFileDescriptor(uri, "rw")
            if (pfd != null) {
                Pair(uri, pfd)
            } else {
                resolver.delete(uri, null, null)
                null
            }
        } else {
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val naturaDir = File(moviesDir, "NATURA")
            if (!naturaDir.exists()) naturaDir.mkdirs()
            val file = File(naturaDir, filename)
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE)
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DATA, file.absolutePath)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                }
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null && pfd != null) Pair(uri, pfd) else null
            } catch (e: Exception) {
                Log.e(TAG, "Failed creating legacy video file", e)
                null
            }
        }
    }

    fun finishVideo(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    /**
     * Queries all saved NATURA photos and videos from MediaStore, sorted latest first.
     */
    fun queryNaturaMedia(context: Context): List<NaturaMediaItem> {
        val resolver = context.contentResolver
        val items = mutableListOf<NaturaMediaItem>()
        val dngBaseNames = mutableSetOf<String>()

        // 1. Query images
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.MIME_TYPE
        )
        val imageSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.Images.Media.DATA} LIKE ?"
        }
        val imageArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf("%NATURA%")
        } else {
            arrayOf("%/NATURA/%")
        }

        try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                imageSelection,
                imageArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                val tempJpegs = mutableListOf<NaturaMediaItem>()

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: ""
                    val dateAdded = cursor.getLong(dateCol)
                    val mime = cursor.getString(mimeCol) ?: ""
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    if (name.endsWith(".dng", ignoreCase = true) || mime.contains("dng")) {
                        val base = name.substringBeforeLast(".")
                        dngBaseNames.add(base)
                    } else {
                        tempJpegs.add(
                            NaturaMediaItem(
                                id = id,
                                uri = uri,
                                displayName = name,
                                isVideo = false,
                                dateAdded = dateAdded
                            )
                        )
                    }
                }

                // Check for DNG companion pairs
                items.addAll(
                    tempJpegs.map { item ->
                        val base = item.displayName.substringBeforeLast(".")
                        if (dngBaseNames.contains(base)) {
                            item.copy(hasDng = true)
                        } else {
                            item
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed querying Natura images", e)
        }

        // 2. Query videos
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED
        )
        val videoSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.Video.Media.DATA} LIKE ?"
        }
        try {
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                videoSelection,
                imageArgs,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: ""
                    val dateAdded = cursor.getLong(dateCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    items.add(
                        NaturaMediaItem(
                            id = id,
                            uri = uri,
                            displayName = name,
                            isVideo = true,
                            dateAdded = dateAdded
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed querying Natura videos", e)
        }

        return items.sortedByDescending { it.dateAdded }
    }
}
