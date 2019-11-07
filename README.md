# 1 背景介绍
为了使用户能够更好地控制自己的文件，并限制文件混乱，AndroidQ修改了外部存储权限。这种外部存储的新特性被称为**分区存储（Scoped Storage）**。官方翻译称为分区储存，也有称为沙盒模式。

外部存储空间被分为两部分
* **1.App-specific directory 沙盒目录**
    * APP只能在Context.getExternalFilesDir()目录下通过File的方式创建文件，APP卸载的时候，这个目录下的文件会被删除；
    * 其他路径下无法通过File的方式创建文件
* **2.Public Directory 公共目录**

    公共目录包括：多媒体公共目录（photos, images, videos, audio）和下载文件目录（Downloads）
    * APP通过MediaStore或者SAF（System Access Framework）的方式访问其中的文件
    * APP卸载后，文件不会被删除
    * 通过MediaStore访问其他应用创建的多媒体文件的时候，需要READ_EXTERNAL_STORAGE权限

![](https://user-gold-cdn.xitu.io/2019/11/6/16e3f36835b9e7c9?w=1780&h=680&f=jpeg&s=164731)

Android Q规定了APP有两种外部存储空间视图模式：Legacy View、Filtered View。
* Legacy View 兼容模式。与AndroidQ之前一样，申请权限后App可访问外部存储，拥有完整的访问权限，可以使用File的方式访问文件。
* Filtered View 分区存储。APP只能直接访问App-specific目录，访问公共目录或者其他APP的App-specific目录，只能通过MediaStore、SAF、或者其他APP提供的ContentProvider、FileProvider等方式访问。

在AndroidQ上，target SDK大于或等于29的APP默认被赋予Filtered View。APP可以在AndroidManifest.xml中设置requestLegacyExternalStorage来修改外部存储空间视图模式，true为Legacy View，false为Filtered View。

```
    //默认是false，也就是Filtered View
    android:requestLegacyExternalStorage="true"
```
可以通过Environment.isExternalStorageLegacy()方法判断运行模式。

AndroidQ除了划分外部储存空间访问权限外,还增加了媒体数据限制，默认删除图片中位置信息，如需获取需要在清单文件中注册 ACCESS_MEDIA_LOCATION 

```
// Get location data from the ExifInterface class.
val photoUri = MediaStore.setRequireOriginal(photoUri)
contentResolver.openInputStream(photoUri).use { stream ->
    ExifInterface(stream).run {
        // If lat/long is null, fall back to the coordinates (0, 0).
        val latLong = ?: doubleArrayOf(0.0, 0.0)
    }
}
```
同时通过MediaProvide获得的data字段将不再可靠，增加了文件的Pending状态，增加了Media.RELATIVE_PATH相对路径字段等等，稍后将详细介绍。
# 2 兼容性影响

Scoped Storage对于通过文件路径操作App-specific（以下简称沙盒）之外的目录以及APP之间的数据数据共享都产生很大的影响。请参考以下事项
* 2.1 无法新建文件

    问题原因：直接使用沙盒目录以外的路径新建文件。
    
    原因分析：Q之前的应用，可以通过Environment.getExternalStorageDirectory()等路径操作外部文件，而在Android Q上，APP只允许在沙盒目录下通过路径创建文件，也就是Context.getExternalFilesDir()目录下，可以通过File的方式操作。
    
    解决办法：
    * 如果在App沙盒目录下新建文件，请参考3.1
    * 如果需要在多媒体和下载公共的集合目录下新建文件，请参考3.2
    * 如果要在任意目录下新建文件，请参考3.3
* 2.2 无法访问文件
    
    问题原因：1.直接使用路径访问沙盒目录以外的文件         2.使用MediaStore接口访问非多媒体文件
    
    原因分析：1.AndroidQ默认只允许访问沙盒目录下的文件，也就是说只有Context.getExternalFilesDir()目录下的文件，可以通过File的方式访问；2.在AndroidQ上MediaStore只能访问公共目录下的多媒体文件

    解决办法：
    * 使用MediaStroe接口访问公共目录下的多媒体文件。 请参考3.2
    * 使用SAF访问任意目录下的文件。请参考3.3
    
    **注意：** 通过MediaStore接口查询到的DATA将在AndroidQ上开始废弃，不应该用它来访问文件或者判断文件是否存在；从MediaStore接口或者SAF获取到文件Uri后，请利用Uri打开FD或者输入输出流，而不要再去转换成文件路径访问。
* 2.3 无法修改文件
    
    问题原因1：直接使用路径访问沙盒目录以外的文件
    
    问题分析1：同 2.2
    
    解决办法1：同 2.2
    
    问题原因2：使用MediaStore接口获取到多媒体文件的Uri后，要修改文件的FD或者OutputStream，失败
    
    问题分析2：在AndroidQ上，修改和删除其他App创建的多媒体文件时，需要用户授权
    
    解决办法2：从MediaStore接口获取到其他APP创建的多媒体文件Uri后，打开OutputStream或FD时，需要catch RecoverableSecurityException，由MediaProvider弹出弹框给用户选择是否允许APP修改或删除，授权成功后才能删除，请参考3.2.6；
    
    问题原因3：根据SAF获取到文件或者目录的Uri，修改或者删除失败
    
    问题分析3：使用SAF获取的Uri，需要检查Uri权限的时效性，设备重启或者用户手动删除权限，则会失败
    
    解决办法3：使用SAF获取到文件或目录的Uri时，用户已经授权读写，可以直接使用，但要注意Uri权限的时效，请参见3.3.6

* 2.4 无法分享文件

    问题原因：使用了file://URI分享文件
    
    问题分析：该文件保存在APP的沙盒目录下，其他APP没有权限访问
    
    解决办法：参考3.4，使用FileProvider适配，将file://类型的Uri转换成content://类型的
* 2.5 应用卸载后文件删除

    问题原因：将文件保存在APP的沙盒目录下
    
    问题分析：该文件保存在app的沙盒目录下，其他APP没有权限访问
    
    解决办法：APP应该将想要保存的文件通过MediaStore接口保存在公共目录下。默认会将非多媒体文件保存在Downloads目录下。如果APP想要卸载的时候保存沙盒目录下的文件，可以在AndroidManifest.xml中声明android:hasFragileUserData="true"，这样在APP卸载时就会有弹出框提示用户是否保留应用数据。
* 2.6 OAT升级问题
    
    问题原因：OAT升级以后，APP被卸载，重新安装后无法访问到APP数据
    
    问题分析：分区储存（Scoped Storage）特性只针对AndroidQ上新安装的APP生效。设备从AndroidQ之前的版本升级到AndroidQ，这时候已安装的APP将获得Legacy View视图。而卸载后安装，APP获得Filtered View视图，无法通过路径访问到旧的数据，从而导致该问题
    
    解决办法：APP主动开启沙盒模式之前，一定要做好历史文件的迁移工资，将之前通过File路径方式保存在沙盒目录和公共目录以外的文件，迁移到沙盒目录和公共目录集合。
# 3.适配指导
Scoped Storage不会强制生效，可以自己决定是否开启新特性。建议先不主动开启，安装新特性的要求，做好沙盒目录和公共文件的存储方式，将老数据迁移，确定没有问题以后再开启。

[谷歌适配文档](https://developer.android.google.cn/preview/privacy/scoped-storage_https://developer.android.google.cn/preview/privacy/scoped-storag)
https://developer.android.google.cn/preview/privacy/scoped-storage
## 3.1 访问App-specific目录文件
无需任何权限，可以直接通过File的方式操作App-specific目录下的文件。
| App-specific目录 | 接口（所有存储设备） | 接口（Primary External Storage） |
|------|------------|------------|
| Media  | getExternalMediaDirs()          | NA         |
| Obb    | getObbDirs()                    | getObbDir()        |
| Cache  | getExternalCacheDirs()            | getExternalCacheDir()       |
| Data   | getExternalFilesDirs(String type)       | getExternalFilesDir(String type)       |

创建文件

```
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
```

## 3.2 使用MediaStore访问公共目录
[Goole官方文档](https://developer.android.google.cn/reference/android/provider/MediaStore)https://developer.android.google.cn/reference/android/provider/MediaStore

### 3.2.1 MediaStore Uri和路径对应表

MediaStore提供下列Uri，可以用MediaProvider查询对应的Uri数据
![](https://user-gold-cdn.xitu.io/2019/11/6/16e401cd0aca3f1b?w=950&h=1136&f=png&s=166204)

在AndroidQ上，所有的外部存储设备都会被命令，即Volume Name。MediaStore可以通过Volume Name 获取对应的Uri

```
    MediaStore.getExternalVolumeNames(this).forEach { volumeName ->
            Log.d(TAG, "uri：${MediaStore.Images.Media.getContentUri(volumeName)}")
        }
```
MediaProvider
通过ContentResolver.insert（uri）方法中的uri确定存放路径。Uri路径格式：
`content://media/<volumeName>/<Uri路径>`，下表对应Uri路径为相对路径

![表二](https://user-gold-cdn.xitu.io/2019/11/6/16e405d78f36adca?w=1430&h=1062&f=png&s=364130)
### 3.2.2 使用MediaStore创建文件
通过ContentResolver的insert方法，将多媒体文件保存在公共集合目录，不同的Uri对应不同的公共目录，详见3.2.1；其中RELATIVE_PATH的一级目录必须是Uri对应的一级目录，二级目录或者二级以上的目录，可以随意的创建和指定

```
        val values = ContentValues()
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "Image.png")
        values.put(MediaStore.Images.Media.DESCRIPTION, "This is an image")
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        values.put(MediaStore.Images.Media.TITLE, "Image.png")
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/sl")
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
            }
        } catch (e: IOException) {
            Log.d(TAG, "创建失败：${e.message}")
        } finally {
            closeIO(os)
        }

```

### 3.2.3 使用MediaStore查询文件
通过ContentResolver.query接口查询文件Uri

```
        val external = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME}=?"
        val args = arrayOf("Image.png")
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val cursor = contentResolver.query(external, projection, selection, args, null)
        if (cursor != null && cursor.moveToFirst()) {
            queryUri = ContentUris.withAppendedId(external, cursor.getLong(0))
            Log.d(TAG, "查询成功，Uri路径$queryUri")
            cursor.close()
        }

```

### 3.2.4 使用MediaStore读取文件
通过ContentResolver.query查询得到的Uri之后，可以通过contentResolver.openFileDescriptor，根据文件描述符选择对应的打开方式。"r"表示读，"w"表示写

```
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
```
或者访问Thumbnail，通过ContentResolver.loadThumbnail,传入size，返回指定大小的缩略图

```
     getContentResolver().loadThumbnail(uri,Size(640, 480), null)
```
Native访问文件
* 通过openFileDescriptor返回ParcelFileDescriptor
* 过ParcelFileDescriptor.detachFd()读取FD
* 将FD传递给Native层代码
* 通过close接口关闭FD

```
String fileOpenMode = "r";
ParcelFileDescriptor parcelFd = resolver.openFileDescriptor(uri, fileOpenMode); if (parcelFd != null) {
int fd = parcelFd.detachFd();
// Pass the integer value "fd" into your native code. Remember to call
// close(2) on the file descriptor when you're done using it.
```

### 3.2.5 使用MediaStore修改文件
使用MediaStore修改其他APP创新建的多媒体文件，需要注意一下两点

* 1.需要判断是否有`READ_EXTERNAL_STORAGE`权限
* 2.需要`catch RecoverableSecurityException`，由MediaProvider弹出弹框给用户选择是否允许APP修改或删除图片/视频/音频文件。用户操作的结果，将通过onActivityResult回调返回到APP。如果用户允许，APP将获得该Uri的修改权限，直到设备重启。

```
            //首先判断是否有READ_EXTERNAL_STORAGE权限
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
                    //捕获 RecoverableSecurityException异常，发起请求
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
            }
```

![media修改](https://user-gold-cdn.xitu.io/2019/11/7/16e445b02e3a1dd8?w=1080&h=1920&f=png&s=267376)
### 3.2.6 使用MediaStore删除文件
删除自己创建的多媒体文件不需要权限，其他APP创建的，与修改类型，需要用户授权，同3.2.5

```
getContentResolver().delete(imageUri, null, null);
```

## 3.3 使用Storage Access Framework
Android 4.4（API 级别 19）引入了存储访问框架Storage Access Framework (SAF)。借助 SAF，用户可轻松在其所有首选文档存储提供程序中浏览并打开文档、图像及其他文件。用户可通过易用的标准界面，以统一方式在所有应用和提供程序中浏览文件，以及访问最近使用的文件。

SAF google官方文档 https://developer.android.google.cn/guide/topics/providers/document-provider

SAF本地存储服务的围绕 DocumentsProvider实现的，通过Intent调用DocumentUI，由用户在DocumentUI上选择要创建、授权的文件以及目录等，授权成功后再onActivityResult回调用拿到指定的Uri，根据这个Uri可进行读写等操作，这时候已经赋予文件读写权限，不需要再动态申请权限

### 3.3.1 使用SAF搜索单个文件
通过Intent.ACTION_OPEN_DOCUMENT调文件选择界面，用户选择并返回一个或多个现有文档，所有选定的文档均具有持久的读写权限授予，直至设备重启。如果重启后仍然需要参考3.3.6

```
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
```
### 3.3.2 使用SAF创建文件
可通过使用 Intent.ACTION_CREATE_DOCUMENT，可以提供 MIME 类型和文件名，但最终结果由用户决定

```
    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        // Filter to only show results that can be "opened", such as
        // a file (as opposed to a list of contacts or timezones).
        addCategory(Intent.CATEGORY_OPENABLE)

        // Create a file with the requested MIME type.
        type = mimeType
        putExtra(Intent.EXTRA_TITLE, fileName)
    }

    startActivityForResult(intent, WRITE_REQUEST_CODE)

```
### 3.3.3 使用SAF删除文件
如果您获得了文档的 URI，并且文档的 Document.COLUMN_FLAGS 包含 FLAG_SUPPORTS_DELETE，则便可删除该文档。这个的包含我理解为获取到的Document.COLUMN_FLAGS>FLAG_SUPPORTS_DELETE，个人理解，有问题欢迎指正

```
 val deleted = DocumentsContract.deleteDocument(contentResolver, uri)
```
### 3.3.4 使用SAF编辑文件
这里的Uri，是通过用户选择授权的Uri，通过Uri获取ParcelFileDescriptor或者打开OutputStream进行修改
```
    try {
        contentResolver.openFileDescriptor(uri, "w")?.use {
            // use{} lets the document provider know you're done by automatically closing the stream
            FileOutputStream(it.fileDescriptor).use {
                it.write(
                    ("Overwritten by MyCloud at ${System.currentTimeMillis()}\n").toByteArray()
                )
            }
        }
    } catch (e: FileNotFoundException) {
        e.printStackTrace()
    } catch (e: IOException) {
        e.printStackTrace()
    }

```
### 3.3.5 使用SAF获取目录
使 用ACTION_OPEN_DOCUMENT_TREE的intent，拉起DocumentUI让用户主动授权的方式 获取，获得用户主动授权之后，应用就可以临时获得该目录下面的所有文件和目录的读写 权限，可以通过DocumentFile操作目录和其下的文件

```
 val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
 startActivityForResult(intent, REQUEST_CODE_FOR_DOCUMENT_DIR)
 ...
 
 if (requestCode == REQUEST_CODE_FOR_DOCUMENT_DIR) {
            //选择目录
    if (resultCode == Activity.RESULT_OK) {
        val treeUri = data?.data
        if (treeUri != null) {
            //implementation 'androidx.documentfile:documentfile:1.0.1'
            val root = DocumentFile.fromTreeUri(this, treeUri)
            root?.listFiles()?.forEach { it ->
            Log.d(TAG, "目录下文件名称：${it.name}")
            }
        }
    }
```

### 3.3.6 使用SAF保留权限
通过用户授权的Uri，就默认获取了该Uri的读写权限，直到设备重启。可以通过保存权限来永久的获取该权限，不需要每次重启手机之后又要重新让用户主动授权
参考代码：
* 本地保存用户授权的Uri

```
    if (resultCode == Activity.RESULT_OK) {
        //创建文档
        val uri = data?.data
        if (uri != null) {
            val sp = getSharedPreferences("DirPermission", Context.MODE_PRIVATE)
            sp.edit {
                this.putString("uri", uri.toString())
                this.commit()
            }
            ...
        }
    }
```
* 调用的时候判断Uri的权限

```
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
```
用户可以通过APP设置界面主动清除储存空间权限
### 3.3.7 使用自定义DocumentsProvider
Android默认提供的ExternalStorageProvider、DownloadStorageProivder和MediaDocumentsProvider会显示在SAF调起的DocumentUI界面中。ExternalStorageProvider展示了所有外部存储设备的所有目录及文件，包括App-specific目录，所以App-specific目录下的文件也可以通过SAF授权给其他APP。APP也可以自定义DocumentsProvider来提供向外授权。

自定义的DocumentsProivder将作为第三方DocumentsProvider展示在SAF调起的界面中。DocumentsProvider的使用方法请参考官方文档。
DocumentsProvider相关的Google官方文档：
https://developer.android.google.cn/reference/kotlin/android/provider/DocumentsProvider

## 3.4 分享处理
APP可以选择以下的方式，将自身App-specific目录下的文件分享给其他APP读写
### 3.4.1 使用FileProvider
FileProvider相关的Google官方文档：
https://developer.android.google.cn/reference/androidx/core/content/FileProvider
https://developer.android.com/training/secure-file-sharing/setup-sharing

FileProvider属于在Android7.0的行为变更，各种帖子很多，这里就不详细介绍了。
为了避免和已有的三方库冲突，建议采用extends FileProvider的方式

```
public class TakePhotoProvider extends FileProvider {
}

...

<application>
        <provider
            android:name=".TakePhotoProvider"
            android:authorities="${applicationId}.fileProvider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/take_file_path" />
        </provider>
</application>
```
### 3.4.2 使用ContentProvider
APP可以实现自定义ContentProvider来向外提供APP私有文件。这种方式十分适用于内部文件分享，不希望有UI交互的情况。
ContentProvider相关的Google官方文档：
https://developer.android.google.cn/guide/topics/providers/content-providers
### 3.4.3 使用DocumentsProvider
详见3.3.7

## 3.5 细节适配
### 3.5.1 图片的地理位置信息
Android Q上，默认情况下APP不能获取图片的地理位置信息。如果APP需要访问图片上的Exif Metadata，可以采用以下方式：
* 1.在manifest中申请ACCESS_MEDIA_LOCATION权限
* 2.调用MediaStore.setRequireOriginal返回新Uri

```
// Get location data from the ExifInterface class.
val photoUri = MediaStore.setRequireOriginal(photoUri)
contentResolver.openInputStream(photoUri).use { stream ->
    ExifInterface(stream).run {
        // If lat/long is null, fall back to the coordinates (0, 0).
        val latLong = ?: doubleArrayOf(0.0, 0.0)
    }
}
```
### 3.5.2 MediaStore DATA字段不再可靠
在Android Q中DATA（即_data）字段开始废弃，不再表示文件的真实路径。读写文件或判断文件是否存在，不应该使用DATA字段，而要使用openFileDescriptor。
同时也无法直接使用路径访问公共目录的文件。
### 3.5.3 MediaStore.Files接口
通过MediaStore.Files接口访问文件时，只返回多媒体文件（图片、视频、音频）。其他类型文件，例如PDF文件，无法访问到。
### 3.5.4 MediaStore 文件增加Pending状态
AndroidQ上，MediaStore中添加MediaStore.Images.Media.IS_PENDING flag，用来表示文件的Pending状态，0是可见，其他不可见，

如果没有设置setIncludePending接口，查询不到设置IS_PENDIN flag的文件，可以用来下载，或者生产截图等等

```
ContentValues values = new ContentValues();
values.put(MediaStore.Images.Media.DISPLAY_NAME, "myImage.PNG");
values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
values.put(MediaStore.Images.Media.IS_PENDING, 1);
ContentResolver resolver = context.getContentResolver();
Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
Uri item = resolver.insert(uri, values);
try {
    ParcelFileDescriptor pfd = resolver.openFileDescriptor(item, "w", null);
    // write data into the pending image.
} catch (IOException e) {
    LogUtil.log("write image fail");
}
// clear IS_PENDING flag after writing finished.
values.clear();
values.put(MediaStore.Images.Media.IS_PENDING, 0);
resolver.update(item, values, null, null);

```
### 3.5.5 MediaStore 相对路径
AndroidQ中，通过MediaSore将多媒体没见储存在公共目录下，除了默认的一级目录，还可以指定次级目录，对应的一级目录详见3.2.1表二

```
val values = ContentValues()
//Pictures为一级目录对应Environment.DIRECTORY_PICTURES，sl为二级目录
values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/sl")
val external = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
val insertUri = contentResolver.insert(external, values)

values.clear()
//DCIM为一级目录对应Environment.DIRECTORY_DCIM，sl为二级目录,sl2为三级目录
values.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/sl/sl2")
contentResolver.update(insertUri,values,null,null)
```

其中AndroidQActivity.kt 中有MediaStore的操作示例

StorageAccessFrameworkActivity.kt 有SAF的操作示例