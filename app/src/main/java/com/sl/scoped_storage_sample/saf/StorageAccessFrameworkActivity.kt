package com.sl.scoped_storage_sample.saf

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.android.synthetic.main.activity_storage_access_framework.*
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import java.io.*
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile


/**
 * 读取文件REQUEST_CODE
 */
private const val REQUEST_CODE_FOR_SINGLE_FILE: Int = 0x01
/**
 * 创建文件REQUEST_CODE
 */
private const val WRITE_REQUEST_CODE: Int = 0x02
/**
 * 编辑文档
 */
private const val EDIT_REQUEST_CODE: Int = 0x03
/**
 * 选择目录
 */
private const val REQUEST_CODE_FOR_DOCUMENT_DIR: Int = 0x04

class StorageAccessFrameworkActivity : AppCompatActivity() {
    private val TAG = "SAF"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.sl.scoped_storage_sample.R.layout.activity_storage_access_framework)
//        Log.d(TAG,"externalMediaDirs :${externalMediaDirs[0]}")
//        Log.d(TAG,"externalCacheDir :$externalCacheDir")
//        Log.d(TAG,"obbDir :${obbDir}")

        selectSingleFile()
        createFile("text/plain", "sl.txt")
//        createFile("img/png", "sl.png")
        deleteFile()
//        renameFile()
        editDocument()
        getDocumentTree()
        MediaStore.getExternalVolumeNames(this).forEach { volumeName ->
            Log.d(TAG, "volumeName：${MediaStore.Images.Media.getContentUri(volumeName)}")
            Log.d(TAG, "getExternalStorageState：${Environment.getExternalStorageState()}")
            Log.d(TAG, "EXTERNAL_CONTENT_URI：${MediaStore.Images.Media.EXTERNAL_CONTENT_URI}")
        }
    }


    /**
     * 选择一个文件，这里打开一个图片作为演示
     */
    private fun selectSingleFile() {

        safSelectSingleFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                // Filter to only show results that can be "opened", such as a
                // file (as opposed to a list of contacts or timezones)
                addCategory(Intent.CATEGORY_OPENABLE)

                // Filter to show only images, using the image MIME data type.
                // If one wanted to search for ogg vorbis files, the type would be "audio/ogg".
                // To search for all documents available via installed storage providers,
                // it would be "*/*".
                type = "image/*"
            }
            startActivityForResult(intent, REQUEST_CODE_FOR_SINGLE_FILE)
        }
    }

    private fun createFile(mimeType: String, fileName: String) {
        createFileBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                // Filter to only show results that can be "opened", such as
                // a file (as opposed to a list of contacts or timezones).
                addCategory(Intent.CATEGORY_OPENABLE)

                // Create a file with the requested MIME type.
                type = mimeType
                putExtra(Intent.EXTRA_TITLE, fileName)
            }

            startActivityForResult(intent, WRITE_REQUEST_CODE)
        }
    }

    /**
     * 如果您获得了文档的 URI，并且文档的 Document.COLUMN_FLAGS 包含 FLAG_SUPPORTS_DELETE，则便可删除该文档
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private fun deleteFile() {
        deleteFileBtn.setOnClickListener {
            val string = createFileUriTv.text.toString()
            if (string.isNotEmpty()) {
                val uri = Uri.parse(string)
                if (checkUriFlag(uri, DocumentsContract.Document.FLAG_SUPPORTS_DELETE)) {
                    val deleted = DocumentsContract.deleteDocument(contentResolver, uri)
                    Toast.makeText(this, "删除$deleted", Toast.LENGTH_SHORT).show()
                    if (deleted) {
                        createFileUriTv.text = ""
                    }
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun renameFile() {
        renameFileBtn.setOnClickListener {
            val string = createFileUriTv.text.toString()
            if (string.isNotEmpty()) {
                val uri = Uri.parse(string)
                if (checkUriFlag(uri, DocumentsContract.Document.FLAG_SUPPORTS_RENAME)) {
                    try {
                        //如果文件名已存在，会报错java.lang.IllegalStateException: File already exists:
                        DocumentsContract.renameDocument(contentResolver, uri, "slzs.txt")
                        Toast.makeText(this, "重命名成功", Toast.LENGTH_SHORT).show()
                    } catch (e: FileNotFoundException) {
                        Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show()
                    }

                }

            }
        }
    }

    private fun editDocument() {

        editDocumentBtn.setOnClickListener {

            // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's
            // file browser.
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                // Filter to only show results that can be "opened", such as a
                // file (as opposed to a list of contacts or timezones).
                addCategory(Intent.CATEGORY_OPENABLE)

                // Filter to show only text files.
                type = "text/plain"
            }

            startActivityForResult(intent, EDIT_REQUEST_CODE)
        }

    }

    /**
     * 使用saf选择目录
     */
    @TargetApi(Build.VERSION_CODES.Q)
    private fun getDocumentTree() {
        getDocumentTreeBtn.setOnClickListener {

            val sp = getSharedPreferences("DirPermission", Context.MODE_PRIVATE)
            val uriString = sp.getString("uri", "")
            if (!uriString.isNullOrEmpty()) {
                try {
                    val treeUri = Uri.parse(uriString)
                    val takeFlags: Int = intent.flags and
                            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    // Check for the freshest data.
                    contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                    Log.d(TAG, "已经获得永久访问权限")
                    val root = DocumentFile.fromTreeUri(this, treeUri)
                    root?.listFiles()?.forEach { it ->
                        Log.d(TAG, "目录下文件名称：${it.name}")
                    }
                } catch (e: SecurityException) {
                    Log.d(TAG, "uri 权限失效，调用目录获取")
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    startActivityForResult(intent, REQUEST_CODE_FOR_DOCUMENT_DIR)
                }
            } else {
                Log.d(TAG, "没有永久访问权限，调用目录获取")
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(intent, REQUEST_CODE_FOR_DOCUMENT_DIR)
            }


        }

    }

    private fun checkUriFlag(uri: Uri, flag: Int): Boolean {
        val cursor = contentResolver.query(uri, null, null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val columnFlags =
                cursor.getInt(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS))
            Log.i(
                TAG,
                "Column Flags：$columnFlags  Flag：$flag"
            )
            if (columnFlags >= flag) {
                return true
            }
            cursor.close()
        }
        return false
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_FOR_SINGLE_FILE) {
            if (resultCode == Activity.RESULT_OK) {
                //获取文档
                val uri = data?.data
                if (uri != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        dumpImageMetaData(uri)
                    }
                    GetBitmapFromUriAsyncTask().execute(uri)

                    Log.d(TAG, "图片的line :${readTextFromUri(uri)}")
                }
            }
        } else if (requestCode == WRITE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                //创建文档
                val uri = data?.data
                if (uri != null) {
                    Toast.makeText(this, "创建文件成功", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "创建文件成功")
                    createFileUriTv.text = uri.toString()
                    createFileUriTv.visibility = View.VISIBLE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        dumpImageMetaData(uri)
                    }
                }
            }
        } else if (requestCode == EDIT_REQUEST_CODE) {
            //编辑文档
            if (resultCode == Activity.RESULT_OK) {
                val uri = data?.data
                if (uri != null) {
                    alterDocument(uri)
                }

            }
        } else if (requestCode == REQUEST_CODE_FOR_DOCUMENT_DIR) {
            //选择目录
            if (resultCode == Activity.RESULT_OK) {
                val treeUri = data?.data
                if (treeUri != null) {
                    savePersistablePermission(treeUri)

                    val root = DocumentFile.fromTreeUri(this, treeUri)
                    root?.listFiles()?.forEach { it ->
                        Log.d(TAG, "目录下文件名称：${it.name}")
                    }
                }
            }
        }
    }

    /**
     * 永久保留权限
     */
    private fun savePersistablePermission(uri: Uri) {
        val sp = getSharedPreferences("DirPermission", Context.MODE_PRIVATE)
        sp.edit {
            this.putString("uri", uri.toString())
            this.commit()
        }
        val takeFlags: Int = intent.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        // Check for the freshest data.
        contentResolver.takePersistableUriPermission(uri, takeFlags)
    }

    /**
     * 获取文档元数据
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun dumpImageMetaData(uri: Uri) {
        val cursor = contentResolver.query(uri, null, null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val displayName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            Log.i(TAG, "Display Name：$displayName")

            val sizeIndex: Int = cursor.getColumnIndex(OpenableColumns.SIZE)
            val size: String = if (!cursor.isNull(sizeIndex)) {
                cursor.getString(sizeIndex)
            } else {
                "Unknown"
            }
            Log.i(TAG, "Size：$size")
        }

    }

    /**
     * 通过Uri 获取Bitmap，耗时操作不应该在主线程
     */
    @Throws(IOException::class)
    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        val parcelFileDescriptor: ParcelFileDescriptor? =
            contentResolver.openFileDescriptor(uri, "r")
        if (parcelFileDescriptor != null) {
            val fileDescriptor: FileDescriptor = parcelFileDescriptor.fileDescriptor
            val image: Bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor)
            parcelFileDescriptor.close()
            return image
        }
        return null

    }

    /**
     * 通过Uri获取InputStream
     */
    private fun readTextFromUri(uri: Uri): String {
        val stringBuffer = StringBuffer()
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    stringBuffer.append(line)
                    line = reader.readLine()
                }
                inputStream.close()
            }
        }
        return stringBuffer.toString()
    }

    /**
     * 通过Uri获取Bitmap
     */
    internal inner class GetBitmapFromUriAsyncTask : AsyncTask<Uri, Void, Bitmap>() {
        override fun doInBackground(vararg params: Uri): Bitmap? {
            val uri = params[0]
            return getBitmapFromUri(uri)
        }

        override fun onPostExecute(bitmap: Bitmap?) {
            super.onPostExecute(bitmap)
            showIv.setImageBitmap(bitmap)
        }


    }

    private fun alterDocument(uri: Uri) {
        try {
            contentResolver.openFileDescriptor(uri, "w")?.use {
                // use{} lets the document provider know you're done by automatically closing the stream
                FileOutputStream(it.fileDescriptor).use {
                    it.write(
                        ("Overwritten by MyCloud at ${System.currentTimeMillis()}\n").toByteArray()
                    )
                    Log.d(TAG, "编辑成功")
                }
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
