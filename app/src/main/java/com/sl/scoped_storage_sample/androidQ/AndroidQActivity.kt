package com.sl.scoped_storage_sample.androidQ

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.annotation.RequiresApi
import com.sl.scoped_storage_sample.R
import com.sl.scoped_storage_sample.closeIO
import kotlinx.android.synthetic.main.activity_android_q.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.Exception
import java.security.Permission

private const val TAG = "AndroidQActivity"
private const val SENDER_REQUEST_CODE = 0x10

class AndroidQActivity : AppCompatActivity() {
    private var queryUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_android_q)
        createAppSpecificFile()
        createFileByMediaStore()
        queryFileByMediaStore()
        readFileByMediaStore()
        loadThumbnail()
        updateFileByMediaStore()
        deleteFileByMediaStore()
    }


    /**
     * 在App-Specific目录下创建文件
     */
    private fun createAppSpecificFile() {
        createAppSpecificFileBtn.setOnClickListener {
            val documents = getExternalFilesDirs(Environment.DIRECTORY_DOCUMENTS)
            if (documents.isNotEmpty()) {
                val dir = documents[0]
                var os: FileOutputStream? = null
                try {
                    val newFile = File(dir.absolutePath, "MyDocument")
                    os = FileOutputStream(newFile)
                    os.write("create a file".toByteArray(Charsets.UTF_8))
                    os.flush()
                    Log.d(TAG, "创建成功")
                    dir.listFiles()?.forEach { file: File? ->
                        if (file != null) {
                            Log.d(TAG, "Documents 目录下的文件名：" + file.name)
                        }
                    }

                } catch (e: IOException) {
                    e.printStackTrace()
                    Log.d(TAG, "创建失败")

                } finally {
                    closeIO(os)
                }

            }
        }
    }

    /**
     * 使用MediaStore创建文件
     */
    private fun createFileByMediaStore() {
        createFileByMediaStoreBtn.setOnClickListener {
            createBitmap()
        }
    }

    private fun createBitmap() {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "NewImage.png")
        values.put(MediaStore.Images.Media.DESCRIPTION, "This is an image")
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        values.put(MediaStore.Images.Media.TITLE, "Image.png")
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/sl")
        values.put(MediaStore.Images.Media.IS_PENDING, 0)

        val external = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val insertUri = contentResolver.insert(external, values)
        var os: OutputStream? = null
        try {
            if (insertUri != null) {
                os = contentResolver.openOutputStream(insertUri)
            }
            if (os != null) {
                val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
                //创建了一个红色的图片
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.RED)
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, os)
                Log.d(TAG, "创建Bitmap成功")


                if (insertUri != null) {
                    values.clear()
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/sl2")
                    contentResolver.update(insertUri,values,null,null)
                }
            }
        } catch (e: IOException) {
            Log.d(TAG, "创建失败：${e.message}")
        } finally {
            closeIO(os)
        }

    }

    /**
     * 通过MediaStore查询文件
     */
    private fun queryFileByMediaStore() {
        queryFileByMediaStoreBtn.setOnClickListener {
            queryUri = queryUri("NewImage.png")
        }

    }

    private fun queryUri(displayName: String): Uri? {
        val external = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME}=?"
        val args = arrayOf(displayName)
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val cursor = contentResolver.query(external, projection, selection, args, null)
        if (cursor != null && cursor.moveToNext()) {
            val queryUri = ContentUris.withAppendedId(external, cursor.getLong(0))
            Log.d(TAG, "查询成功，Uri路径$queryUri")
            cursor.close()
            return queryUri
        }
        return null
    }

    /**
     * 根据查询到的uri，获取bitmap
     */
    private fun readFileByMediaStore() {
        readFileByMediaStoreBtn.setOnClickListener {
            if (queryUri != null) {
                var pfd: ParcelFileDescriptor? = null
                try {
                    pfd = contentResolver.openFileDescriptor(queryUri!!, "r")
                    if (pfd != null) {
                        val bitmap = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
                        imageIv.setImageBitmap(bitmap)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    closeIO(pfd)
                }

            } else {
                Log.d(TAG, "还未查询到Uri")
            }

        }
    }

    /**
     * 根据查询到的Uri，获取Thumbnail
     */
    private fun loadThumbnail() {
        loadThumbnailBtn.setOnClickListener {
            queryUri?.let {
                val bitmap = contentResolver.loadThumbnail(it, Size(100, 200), null)
                imageIv.setImageBitmap(bitmap)
            }
        }
    }

    /**
     * 根据查询得到的Uri，修改文件
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateFileByMediaStore() {

        updateFileByMediaStoreBtn.setOnClickListener {
            //需要READ_EXTERNAL_STORAGE权限
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                //这里的img 是我相册里的，如果运行demo，可以换成你自己的
                val queryUri = queryUri("IMG_20191106_223612.jpg")
                var os: OutputStream? = null
                try {
                    queryUri?.let { uri ->
                        os = contentResolver.openOutputStream(uri)
                    }

                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e1: RecoverableSecurityException) {
                    e1.printStackTrace()
                    try {
                        startIntentSenderForResult(
                            e1.userAction.actionIntent.intentSender,
                            SENDER_REQUEST_CODE,
                            null,
                            0,
                            0,
                            0
                        )
                    } catch (e2: IntentSender.SendIntentException) {
                        e2.printStackTrace()
                    }
                }
            } else {
                Log.d(TAG, "没有READ_EXTERNAL_STORAGE权限，请动态申请")
            }
        }


    }

    /**
     * 删除MediaStore文件
     */

    private fun deleteFileByMediaStore() {

        deleteFileByMediaStoreBtn.setOnClickListener {
            //需要READ_EXTERNAL_STORAGE权限
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                //这里的img 是我相册里的，如果运行demo，可以换成你自己的
                val queryUri = queryUri("IMG_20191106_223612.jpg")
                try {
                    if (queryUri != null) {
                        contentResolver.delete(queryUri, null, null)
                    }

                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e1: RecoverableSecurityException) {
                    e1.printStackTrace()
                    try {
                        startIntentSenderForResult(
                            e1.userAction.actionIntent.intentSender,
                            SENDER_REQUEST_CODE,
                            null,
                            0,
                            0,
                            0
                        )
                    } catch (e2: IntentSender.SendIntentException) {
                        e2.printStackTrace()
                    }
                }
            } else {
                Log.d(TAG, "没有READ_EXTERNAL_STORAGE权限，请动态申请")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SENDER_REQUEST_CODE) {
            if (requestCode == Activity.RESULT_OK) {
                Log.d(TAG, "授权成功")
                //do something
            } else {
                Log.d(TAG, "授权失败")
            }

        }
    }

}
