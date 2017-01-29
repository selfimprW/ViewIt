package com.linroid.viewit.data

import android.content.pm.ApplicationInfo
import java.util.*

/**
 * @author linroid <linroid@gmail.com>
 * @since 29/01/2017
 */
class ImageRepoManager {
    private val repos = HashMap<String, ImageRepo>()

    fun getRepo(appInfo: ApplicationInfo): ImageRepo {
        if (repos.containsKey(appInfo.packageName)) {
            return repos[appInfo.packageName]!!
        } else {
            val repo = ImageRepo(appInfo)
            repos[appInfo.packageName] = repo
            return repo
        }
    }

    fun removeRepo(appInfo: ApplicationInfo): ImageRepo? {
        return repos.remove(appInfo.packageName)
    }
}