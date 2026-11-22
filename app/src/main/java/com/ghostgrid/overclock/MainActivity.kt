
package com.ghostgrid.overclock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.btnStartHud).setOnClickListener { ensurePermissionsThenStart() }
        findViewById<View>(R.id.btnStopHud).setOnClickListener {
            stopService(Intent(this, HudService::class.java))
            toast("HUD offline")
        }

        loadAppGrid()
    }

    private fun ensurePermissionsThenStart() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            toast("grant \"display over other apps\" then press start again")
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
            return
        }
        startService(Intent(this, HudService::class.java))
        toast("HUD online ⚡")
    }

    private fun loadAppGrid() {
        val pm = packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).sortedBy { pm.getApplicationLabel(it.activityInfo.applicationInfo).toString() }

        val grid = findViewById<GridView>(R.id.appGrid)
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(p: Int) = apps[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
                val v = cv ?: LayoutInflater.from(this@MainActivity)
                    .inflate(R.layout.grid_item, parent, false)
                val ri = apps[p]
                v.findViewById<ImageView>(R.id.appIcon).setImageDrawable(
                    ri.activityInfo.applicationInfo.loadIcon(pm))
                v.findViewById<TextView>(R.id.appLabel).text =
                    pm.getApplicationLabel(ri.activityInfo.applicationInfo)
                return v
            }
        }
        grid.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val i = pm.getLaunchIntentForPackage(apps[pos].activityInfo.packageName)
            i?.let { startActivity(it) }
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
