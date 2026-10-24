package fr.olivierthevet.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class TvApp(
    val label: String,
    val component: ComponentName,
    val banner: Drawable?,
    val icon: Drawable,
)

/** Apps affichées au premier lancement, avant tout ajout avec "+". */
private val DEFAULT_APPS = listOf(
    "org.jellyfin.androidtv",
    "com.google.android.youtube.tv",
    "com.disney.disneyplus",
    "tv.twitch.android.app",
)

private const val PREFS = "home"
private const val KEY_PINNED = "pinned_apps"

/** Packages affichés sur l'accueil, dans l'ordre. Les autres restent installées mais masquées. */
fun loadPinned(context: Context): List<String> {
    val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PINNED, null)
    return saved?.split(",")?.filter { it.isNotBlank() } ?: DEFAULT_APPS
}

fun savePinned(context: Context, packages: List<String>) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString(KEY_PINNED, packages.joinToString(","))
        .apply()
}

private val TECHNICAL_LABEL = Regex("""^\w+(\.\w+)+$""")

private const val SURFSHARK = "com.surfshark.vpnclient.android"

/**
 * Toutes les apps lançables, triées par nom : version TV (LEANBACK) en priorité,
 * sinon l'activité "téléphone".
 */
fun loadAllApps(context: Context): List<TvApp> {
    val pm = context.packageManager
    val seen = mutableSetOf<String>()
    val apps = mutableListOf<TvApp>()

    for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (info in pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName) continue
            val label = info.loadLabel(pm).toString()
            // Écrans techniques Philips sans vrai nom (ex. "org.droidtv.settings.SetupMenuAlias")
            if (TECHNICAL_LABEL.matches(label) || !seen.add(pkg)) continue
            val component = ComponentName(pkg, info.activityInfo.name)
            apps += TvApp(
                label = label,
                component = component,
                banner = info.activityInfo.loadBanner(pm) ?: info.activityInfo.applicationInfo.loadBanner(pm),
                icon = info.loadIcon(pm),
            )
        }
    }
    return apps.sortedBy { it.label.lowercase() }
}

fun launchApp(context: Context, app: TvApp) {
    val intent = Intent(Intent.ACTION_MAIN)
        .setComponent(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { context.startActivity(intent) }
}

/** Ouvert depuis l'indicateur VPN plutôt que depuis la rangée d'apps. */
fun launchSurfshark(context: Context) {
    val pm = context.packageManager
    val intent = pm.getLeanbackLaunchIntentForPackage(SURFSHARK) ?: pm.getLaunchIntentForPackage(SURFSHARK) ?: return
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Indispensable une fois l'accueil Google désactivé : seul accès aux réglages. */
fun launchSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
