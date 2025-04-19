package com.ultralytics.yolo

import android.app.Activity
import android.content.Context
import android.util.Log
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import io.flutter.plugin.common.StandardMessageCodec

/**
 * Factory for creating YoloPlatformView instances
 */
class YoloPlatformViewFactory : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    private var activity: Activity? = null
    private val TAG = "YoloPlatformViewFactory"
    
    // Store activity reference to pass to the YoloPlatformView
    fun setActivity(activity: Activity?) {
        this.activity = activity
        Log.d(TAG, "Activity set: ${activity?.javaClass?.simpleName}")
    }
    
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val creationParams = args as? Map<String?, Any?>
        
        // Use activity if available, otherwise use the provided context
        val effectiveContext = activity ?: context
        Log.d(TAG, "Creating YoloPlatformView with context: ${effectiveContext.javaClass.simpleName}")
        
        return YoloPlatformView(effectiveContext, viewId, creationParams)
    }
}