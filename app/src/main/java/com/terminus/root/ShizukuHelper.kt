package com.terminus.root

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

object ShizukuHelper {
    private const val TAG = "ShizukuHelper"
    const val PERMISSION_REQUEST_CODE = 0x5A1

    var onBinderStateChanged: ((Boolean) -> Unit)? = null
    var onPermissionResult: ((Boolean) -> Unit)? = null

    private var initialized = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        onBinderStateChanged?.invoke(true)
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        onBinderStateChanged?.invoke(false)
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == PERMISSION_REQUEST_CODE) {
                onPermissionResult?.invoke(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }

    fun init() {
        if (initialized) return
        initialized = true
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        } catch (t: Throwable) {
            Log.e(TAG, "Shizuku init failed", t)
        }
    }

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        isAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun shouldShowRationale(): Boolean = try {
        isAvailable() && Shizuku.shouldShowRequestPermissionRationale()
    } catch (t: Throwable) {
        false
    }

    fun requestPermission() {
        try {
            if (isAvailable()) {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "requestPermission failed", t)
        }
    }

    fun isRoot(): Boolean = try {
        isAvailable() && Shizuku.getUid() == 0
    } catch (t: Throwable) {
        false
    }
}
