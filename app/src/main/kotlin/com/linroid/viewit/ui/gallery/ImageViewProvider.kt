package com.linroid.viewit.ui.gallery

import android.content.pm.ApplicationInfo
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import butterknife.bindView
import com.bumptech.glide.Glide
import com.linroid.viewit.App
import com.linroid.viewit.R
import com.linroid.viewit.data.ImageRepo
import com.linroid.viewit.data.model.Image
import com.linroid.viewit.ui.BaseActivity
import com.linroid.viewit.ui.viewer.ImageViewerActivity
import com.trello.rxlifecycle.kotlin.bindToLifecycle
import me.drakeet.multitype.ItemViewProvider
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import javax.inject.Inject

/**
 * @author linroid <linroid@gmail.com>
 * @since 07/01/2017
 */
class ImageViewProvider(val activity: BaseActivity, val info:ApplicationInfo) : ItemViewProvider<Image, ImageViewProvider.ViewHolder>() {

    @Inject lateinit var imageRepo: ImageRepo

    init {
        App.graph.inject(this)
    }

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): ViewHolder {
        return ViewHolder(inflater.inflate(R.layout.item_image, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, image: Image) {
//        imageRepo.mountImage(image)
//                .observeOn(AndroidSchedulers.mainThread())
//                .bindToLifecycle(holder.itemView)
//                .subscribe ({ file->
//                    Timber.i(file.absolutePath)
//                    Glide.with(holder.image.context).load(file).centerCrop().into(holder.image)
//                }, { error ->
//                    Timber.e(error)
//                })
        Glide.with(holder.itemView.context).load(image.path).centerCrop().into(holder.image)
        holder.itemView.setOnClickListener {
            ImageViewerActivity.navTo(activity, info, holder.adapterPosition)
        }
        holder.itemView.setOnLongClickListener {
            Toast.makeText(holder.itemView.context, image.source.absolutePath, Toast.LENGTH_SHORT).show()
            true
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView by bindView(R.id.image)
    }
}
