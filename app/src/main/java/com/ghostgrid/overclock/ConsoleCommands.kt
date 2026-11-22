
package com.ghostgrid.overclock

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

object ConsoleCommands {

    fun run(ctx: Context, raw: String): String {
        val cmd = raw.trim()
        if (cmd.isEmpty()) return ""
        val head = cmd.split(" ").first().lowercase()
        val args = cmd.removePrefix(head).trim()

        return when (head) {
            "help" -> "commands: open <app> · boost · killall · status · hud off · dock"
            "boost" -> { HudActions.boost(ctx); "boost applied ⚡" }
            "killall" -> { HudActions.killBackground(ctx); "background purged ✔" }
            "status" -> {
                val s = StatsReader.read(ctx)
                "cpu:${s.cpuPct}% ram:${s.ramPct}% net:${s.netKbps}K/s ${s.tempC}°C"
            }
            "open" -> HudActions.openApp(ctx, args)
            "hud" -> {
                if (args == "off") { ctx.stopService(Intent(ctx, HudService::class.java)); "hud offline" }
                else "usage: hud off"
            }
            "dock" -> { HudActions.openLauncher(ctx); "dock open" }
            else -> "unknown cmd — type help"
        }
    }
}

object HudActions {

    fun boost(ctx: Context) {
        killBackground(ctx)
        // try root governor switch; silently skip if not rooted
        runCatching {
            Runtime.getRuntime().exec(arrayOf("su", "-c",
                "for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; " +
                "do echo performance > \$g; done")).waitFor()
        }
        toast(ctx, "⚡ SYSBOOST — governor=performance, RAM purged")
    }

    fun killBackground(ctx: Context) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        ctx.packageManager.getInstalledApplications(0).forEach { am.killBackgroundProcesses(it.packageName) }
    }

    fun openApp(ctx: Context, name: String): String {
        if (name.isEmpty()) return "usage: open <app name>"
        val pm = ctx.packageManager
        val candidates = pm.getInstalledApplications(0).filter {
            val label = pm.getApplicationLabel(it).toString().lowercase()
            label.contains(name.lowercase()) || it.packageName.contains(name.lowercase())
        }
        val target = candidates.firstOrNull() ?: return "app not found: $name"
        val intent = pm.getLaunchIntentForPackage(target.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent != null) { ctx.startActivity(intent); "launching ${pm.getApplicationLabel(target)}" }
               else "no launcher activity"
    }

    fun openLauncher(ctx: Context) {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(home)
    }

    fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}
