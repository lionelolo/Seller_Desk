package com.sellerdesk.app

import android.app.Application
import android.content.Context
import com.sellerdesk.app.alert.AlertCenter
import com.sellerdesk.app.data.CredentialStore
import com.sellerdesk.app.data.EldoradoApi

/** Tiny service locator shared by the UI and the background service. */
object Graph {
    lateinit var store: CredentialStore
    lateinit var api: EldoradoApi

    fun init(context: Context) {
        if (!::store.isInitialized) {
            store = CredentialStore(context)
            api = EldoradoApi(store)
        }
    }
}

class SellerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        AlertCenter.ensureChannels(this)
    }
}
