package com.sl.scoped_storage_sample

import java.nio.file.Files.delete
import android.os.ParcelFileDescriptor
import android.content.ContentResolver
import android.provider.MediaStore
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.Closeable
import java.io.FileOutputStream
import java.io.IOException


/**
 *    author : Sl
 *    createDate   : 2019-11-0709:42
 *    desc   :
 */


fun closeIO(io: Closeable?) {
    try {
        io?.close()
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

