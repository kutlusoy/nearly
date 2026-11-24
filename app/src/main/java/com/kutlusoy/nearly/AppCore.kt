package com.kutlusoy.nearly

import android.app.Application
import com.kutlusoy.nearly.ui.AppViewModel

// The one app state of the process. The activity, the receiving service and the boot receiver all use it, so closing the screen
// does not close the chat and the relay connections.
object AppCore {
    @Volatile
    private var instance: AppViewModel? = null

    fun get(app: Application): AppViewModel = instance ?: synchronized(this) { instance ?: AppViewModel(app).also { instance = it } }

    // For tests
    fun install(model: AppViewModel?) {
        instance = model
    }
}
