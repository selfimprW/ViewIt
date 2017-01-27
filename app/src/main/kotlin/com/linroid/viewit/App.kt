package com.linroid.viewit

import android.app.Application
import com.github.piasy.biv.BigImageViewer
import com.github.piasy.biv.loader.glide.GlideImageLoader
import com.linroid.viewit.ioc.DaggerGlobalGraph
import com.linroid.viewit.ioc.GlobalGraph
import com.linroid.viewit.ioc.module.AndroidModule
import com.linroid.viewit.ioc.module.DataModule
import com.linroid.viewit.ioc.module.PrefModule
import com.linroid.viewit.ioc.module.RepoModule
import timber.log.Timber

/**
 * @author linroid <linroid@gmail.com>
 * @since 07/01/2017
 */
class App : Application() {
    companion object {
        @JvmStatic lateinit var graph: GlobalGraph
        @JvmStatic private lateinit var instance: App;
        fun get(): App = instance
    }

    fun graph(): GlobalGraph = graph

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        graph = DaggerGlobalGraph.builder()
                .androidModule(AndroidModule(this))
                .repoModule(RepoModule())
                .dataModule(DataModule())
                .prefModule(PrefModule())
                .build();
        instance = this;
        BigImageViewer.initialize(GlideImageLoader.with(this));
    }
}
