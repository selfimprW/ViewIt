package com.linroid.viewit.ui

import android.os.Bundle
import android.support.annotation.CallSuper
import android.support.annotation.LayoutRes
import android.support.v4.app.NavUtils
import android.support.v7.widget.Toolbar
import com.linroid.viewit.R
import com.trello.rxlifecycle.components.support.RxAppCompatActivity

/**
 * @author linroid <linroid@gmail.com>
 * @since 07/01/2017
 */

abstract class BaseActivity : RxAppCompatActivity() {
    var toolbar: Toolbar? = null
    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(provideContentLayoutId())
        toolbar = findViewById(R.id.toolbar) as? Toolbar
        if (toolbar != null) {
            setSupportActionBar(toolbar)
        }
        val parent = NavUtils.getParentActivityName(this)
        if (parent != null && parent.isEmpty().not()) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
    }

    @LayoutRes
    abstract fun provideContentLayoutId(): Int
}