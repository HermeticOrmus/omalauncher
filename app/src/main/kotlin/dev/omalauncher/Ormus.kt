package dev.omalauncher

import android.content.Context
import android.util.Log
import dev.omalauncher.linux.Proot
import dev.omalauncher.linux.run
import java.io.File
import kotlin.concurrent.thread

/**
 * What makes this build Omalauncher Ormus rather than upstream's: its theme, home widgets, agent and the SSH bridge to
 * Sun. Everything Ormus-only lives here and in assets/ormus, so upstream changes rebase with one-line hooks to fix up.
 */
object Ormus {
    /** In assets/ormus/themes, seeded into the user's themes so it shows before Linux and needs no download. */
    const val THEME = "ormus-aurum"

    /** The agent setup picks first, which becomes the bar's default. */
    const val AGENT = "grok"

    /** Upstream's home widgets this build leaves off home; assets/ormus/widgets takes their place. */
    val HIDDEN_WIDGETS = setOf("10-today.owl", "20-fetch.owl", "50-koi.owl", "60-owl.owl")

    /** Copies the Ormus theme into the user's themes once; a half-copied one never counts, as it's renamed in whole. */
    fun seedTheme(context: Context) {
        val themes = LauncherData.userThemesDir(context)
        val dir = File(themes, THEME)
        if (File(dir, "colors.toml").isFile) return
        val tmp = File(themes, ".$THEME.tmp")
        tmp.deleteRecursively()
        copyAssets(context, "ormus/themes/$THEME", tmp)
        dir.deleteRecursively()
        if (!tmp.renameTo(dir)) Log.w("ormus", "couldn't seed the $THEME theme")
    }

    private fun copyAssets(
        context: Context,
        path: String,
        target: File,
    ) {
        val children = context.assets.list(path).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(path).use { input -> target.outputStream().use { input.copyTo(it) } }
            return
        }
        for (child in children) copyAssets(context, "$path/$child", File(target, child))
    }
}

/**
 * Keeps sshd running in Linux on port 2223 for the keys in assets/ormus/authorized_keys, so Sun reaches this phone over
 * Tailscale, and sets up `ssh sun` and `mosh sun` (assets/ormus/bridge.sh). Supervised like PulseAudio in AudioBridge:
 * guest processes die with their proot, so a daemonizing sshd wouldn't outlive the command that started it.
 */
class OrmusBridge(
    private val context: Context,
    private val proot: Proot,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        thread(name = "ormus-bridge", isDaemon = true) { supervise() }
    }

    private fun supervise() {
        var backoff = 2000L
        while (true) {
            val code =
                runCatching {
                    for (name in listOf("bridge.sh", "authorized_keys", "notif-score")) {
                        proot.hostFile("/tmp/ormus-$name").writeBytes(context.assets.open("ormus/$name").use { it.readBytes() })
                    }
                    proot.run("exec sh /tmp/ormus-bridge.sh /tmp/ormus-authorized_keys") { Log.i(TAG, it) }
                }.getOrElse {
                    Log.w(TAG, "bridge couldn't start", it)
                    -1
                }
            // Installing openssh can lose the race for pacman's lock to the launcher's own updates; the next try wins.
            Log.w(TAG, "bridge exited $code; restarting in ${backoff / 1000} s")
            Thread.sleep(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private companion object {
        const val TAG = "ormus-bridge"
        const val MAX_BACKOFF_MS = 5 * 60_000L
    }
}
