package com.sellerdesk.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.sellerdesk.app.ui.Root
import com.sellerdesk.app.ui.SellerTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SellerTheme {
                Root(vm = vm, requestNotifications = {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Make sure the watcher is alive whenever the app is opened.
        if (Graph.store.isLoggedIn && Graph.store.alertsEnabled) OrderMonitorService.start(this)
    }
}
