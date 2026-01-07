package com.codestersunion.stickerprinter

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AdminReceiver : DeviceAdminReceiver() {

    // This is optional, but helpful to know it worked
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Device Owner Enabled", Toast.LENGTH_SHORT).show()
    }
}