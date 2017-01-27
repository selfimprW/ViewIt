package com.linroid.viewit.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.support.annotation.IntDef
import com.linroid.rxshell.RxShell
import com.linroid.viewit.App
import com.linroid.viewit.R
import com.linroid.viewit.data.model.Image
import com.linroid.viewit.data.scanner.ExternalImageScanner
import com.linroid.viewit.data.scanner.InternalImageScanner
import com.linroid.viewit.utils.APP_EXTERNAL_PATHS
import com.linroid.viewit.utils.FileUtils
import com.linroid.viewit.utils.RootUtils
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.lang.kotlin.filterNotNull
import rx.lang.kotlin.onErrorReturnNull
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import timber.log.Timber
import java.io.File
import java.util.*
import javax.inject.Inject

/**
 * @author linroid <linroid@gmail.com>
 * @since 08/01/2017
 */
const val UPDATE_EVENT = 0x1L
const val REMOVE_EVENT = 0x2L
const val INSERT_EVENT = 0x3L

@IntDef(UPDATE_EVENT, REMOVE_EVENT, INSERT_EVENT)
annotation class ImageEventType

const val SORT_BY_DEFAULT = 0x1L
const val SORT_BY_SIZE = 0x2L
const val SORT_BY_TIME = 0x3L

@IntDef(SORT_BY_DEFAULT, SORT_BY_SIZE, SORT_BY_TIME)
annotation class ImageSortType

class ImageRepo {
    @Inject
    lateinit var context: Context
    @Inject
    lateinit var packageManager: PackageManager
    @Inject
    lateinit var rxShell: RxShell
    @Inject
    lateinit var internalScanner: InternalImageScanner
    @Inject
    lateinit var externalScanner: ExternalImageScanner

    init {
        App.graph.inject(this)
    }

    private val subjects = HashMap<String, PublishSubject<ImageEvent>>()
    private val cacheDir: File = File(context.cacheDir, "mounts")
    private val imagesMap = HashMap<String, MutableList<Image>>()

    fun sort(appInfo: ApplicationInfo, @ImageSortType sortType: Long): Observable<List<Image>> {
        val subject = getSubject(appInfo.packageName)
        val images = getImages(appInfo.packageName)
        if (images.size == 0) return scan(appInfo, sortType)
        return sort(Observable.from(images), sortType)
                .toList()
                .doOnNext {
                    images.clear()
                    images.addAll(it)
                    subject.onNext(ImageEvent(UPDATE_EVENT, 0, it.size, images))
                }
    }

    private fun sort(observable: Observable<Image>, @ImageSortType sortType: Long): Observable<Image> {
        when (sortType) {
            SORT_BY_DEFAULT -> return observable.sorted { image, image2 -> -image.path.compareTo(image2.path) }
            SORT_BY_SIZE -> return observable.sorted { image, image2 -> -image.size.compareTo(image2.size) }
            SORT_BY_TIME -> return observable.sorted { image, image2 -> -image.lastModified.compareTo(image2.lastModified) }
        }
        return observable
    }

    fun scan(appInfo: ApplicationInfo, @ImageSortType sortType: Long): Observable<List<Image>> {
        Timber.d("scan images for ${appInfo.packageName}, sortType:$sortType")
        val subject = getSubject(appInfo.packageName)
        val images = getImages(appInfo.packageName)

        val externalData: File = context.externalCacheDir.parentFile.parentFile
        var observable = externalScanner.scan(appInfo.packageName, File(externalData, appInfo.packageName))

        if (RootUtils.isRootAvailable()) {
            val packInfo: PackageInfo = packageManager.getPackageInfo(appInfo.packageName, 0)
            val dataDir = packInfo.applicationInfo.dataDir
            val internal = internalScanner.scan(appInfo.packageName, File(dataDir))
                    .doOnError { error -> Timber.e(error, "scan internal image failed") }
                    .onErrorReturnNull().filterNotNull()
            observable = observable.concatWith(internal)
        }

        if (APP_EXTERNAL_PATHS.containsKey(appInfo.packageName)) {
            val sdcard = Environment.getExternalStorageDirectory()
            val dirs = ArrayList<File>()
            APP_EXTERNAL_PATHS[appInfo.packageName]?.forEach {
                if (it.isEmpty().not()) {
                    dirs.add(File(sdcard, it))
                }
            }
            observable = observable.concatWith(externalScanner.scan(appInfo.packageName, dirs))
        }
        return sort(observable, sortType)
                .toList()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext {
                    images.clear()
                    images.addAll(it)
                    subject.onNext(ImageEvent(UPDATE_EVENT, 0, it.size, images))
                }
    }

    fun register(appInfo: ApplicationInfo): PublishSubject<ImageEvent> {
        return getSubject(appInfo.packageName)
    }

    fun destroy(appInfo: ApplicationInfo) {
        val packageName = appInfo.packageName
        if (subjects.containsKey(packageName)) {
            val subject = subjects[packageName]
            subjects.remove(packageName)
        }
    }

    private fun getSubject(packageName: String): PublishSubject<ImageEvent> {
        val subject: PublishSubject<ImageEvent>?
        if (subjects.containsKey(packageName)) {
            subject = subjects[packageName]
        } else {
            subject = PublishSubject.create()
            subjects.put(packageName, subject)
        }
        return subject!!
    }

    fun getImages(appInfo: ApplicationInfo): MutableList<Image> {
        return getImages(appInfo.packageName)
    }

    fun getImageAt(position: Int, appInfo: ApplicationInfo): Image {
        return getImages(appInfo.packageName)[position]
    }

    private fun getImages(packageName: String): MutableList<Image> {
        val images: MutableList<Image>?
        if (imagesMap.containsKey(packageName)) {
            images = imagesMap[packageName]
        } else {
            images = ArrayList<Image>()
            imagesMap.put(packageName, images)
        }
        return images!!
    }

    fun mountImage(image: Image, appInfo: ApplicationInfo): Observable<Image> {
        val packageCacheDir: File = File(cacheDir, appInfo.packageName)
        val packInfo: PackageInfo = packageManager.getPackageInfo(appInfo.packageName, 0)
        val dataDir: String = packInfo.applicationInfo.dataDir
        val relativePath: String = image.path.substringAfter(dataDir)

        val cacheFile = File(packageCacheDir, relativePath)
        image.mountPath = cacheFile.absolutePath
        if (cacheFile.exists()) {
            return Observable.just(image)
        }
        val targetDir = cacheFile.parentFile
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        if (!cacheFile.exists()) {
            cacheFile.createNewFile()
        }
        return rxShell.copyFile(image.path, cacheFile.absolutePath)
//                .flatMap { RxShell.instance().chown(cacheFile.absolutePath, uid, uid) }
                .map { image }
    }

    fun saveImage(image: Image, appInfo: ApplicationInfo): Observable<File> {
        return Observable.create<File> { subscriber ->
            if (Environment.getExternalStorageState() != android.os.Environment.MEDIA_MOUNTED) {
                subscriber.onError(IllegalStateException(context.getString(R.string.msg_save_image_failed_without_sdcard)));
                return@create
            }
            val targetName = "${packageManager.getApplicationLabel(appInfo)}_${System.currentTimeMillis()}.${image.postfix()}"
            @SuppressLint("SdCardPath")
            val pictureDirectory = "/sdcard${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)}";
            val saveDirectory = File(pictureDirectory, context.getString(R.string.app_name));
            val desFile: File;
            try {
                if (!saveDirectory.exists()) {
                    saveDirectory.mkdirs();
                }
                desFile = File(saveDirectory, targetName);
                if (!desFile.exists()) {
                    desFile.createNewFile();
                }
                FileUtils.copyFile(image.file(), desFile);
                subscriber.onNext(desFile)
                subscriber.onCompleted()
            } catch (error: Exception) {
                subscriber.onError(error)
            }
        }.subscribeOn(Schedulers.io())
    }

    fun deleteImage(position: Int, appInfo: ApplicationInfo): Observable<Boolean> {
        val images = getImages(appInfo.packageName)
        val image = images[position]
        return Observable.just(image.path)
                .flatMap { path ->
                    if (RootUtils.isRootFile(context, path)) {
                        return@flatMap rxShell.deleteFile(path)
                    } else {
                        File(image.path).delete()
                        return@flatMap Observable.just(true)
                    }
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext {
                    images.removeAt(position)
                    getSubject(appInfo.packageName).onNext(ImageEvent(REMOVE_EVENT, position, 1, images))
                }
    }

    data class ImageEvent(@ImageEventType val type: Long, val position: Int, val effectCount: Int, val images: List<Image>)

}
