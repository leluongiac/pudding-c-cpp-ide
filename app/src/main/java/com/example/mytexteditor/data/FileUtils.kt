package com.example.mytexteditor.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

object FileUtils {
    fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        return cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    fun saveToFile(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getFilePath(context: Context, uri: Uri?): String {
        if (uri == null) return "~/untitled.kt"
        if (uri.scheme == "file") {
            return uri.path ?: uri.toString()
        }
        if (uri.scheme == "content") {
            try {
                val docId = DocumentsContract.getDocumentId(uri)
                if (docId.startsWith("primary:")) {
                    return "~/${docId.removePrefix("primary:")}"
                } else if (docId.startsWith("raw:")) {
                    return docId.removePrefix("raw:")
                }
            } catch (e: Exception) {
                // Ignore if not a document uri
            }
            val path = uri.path
            if (path != null) {
                val decoded = Uri.decode(path)
                if (decoded.contains("primary:")) {
                    return "~/${decoded.substringAfter("primary:")}"
                } else if (decoded.contains("/document/")) {
                    return "~/${decoded.substringAfter("/document/")}"
                }
            }
            if (context != null) {
                val name = getFileName(context, uri)
                if (name != null) return "~/$name"
            }
        }
        return uri.toString()
    }

    fun getDirectoryPath(context: Context, uri: Uri?): String {
        val full = getFilePath(context, uri)
        val lastSlash = full.lastIndexOf('/')
        return if (lastSlash != -1) full.substring(0, lastSlash + 1) else "~/"
    }

    fun truncateMiddle(text: String, maxLength: Int = 22): String {
        if (text.length <= maxLength) return text
        val extIndex = text.lastIndexOf('.')
        if (extIndex > 0 && extIndex > text.length - 8) {
            val ext = text.substring(extIndex)
            val base = text.substring(0, extIndex)
            val available = maxLength - ext.length - 3
            if (available > 2) {
                val front = (available + 1) / 2
                val back = available / 2
                return base.take(front) + "..." + base.takeLast(back) + ext
            }
        }
        val available = maxLength - 3
        val front = (available + 1) / 2
        val back = available / 2
        return text.take(front) + "..." + text.takeLast(back)
    }

    fun truncatePathMiddle(path: String, maxLength: Int = 26): String {
        if (path.length <= maxLength) return path
        val normalized = if (path.endsWith("/")) path.dropLast(1) else path
        val segments = normalized.split('/').filter { it.isNotEmpty() }
        val prefix = if (path.startsWith("~")) "~/" else if (path.startsWith("/")) "/" else ""
        if (segments.size >= 3) {
            val first = segments.first()
            val last = segments.last()
            val candidate = "$prefix$first/.../$last/"
            if (candidate.length <= maxLength) return candidate
        }
        val available = maxLength - 5
        val front = (available + 1) / 2
        val back = available / 2
        return path.take(front) + ".../" + path.takeLast(back)
    }
}
