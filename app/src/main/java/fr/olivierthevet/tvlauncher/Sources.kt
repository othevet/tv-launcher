package fr.olivierthevet.tvlauncher

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

data class TvSource(val label: String, val inputId: String)

/** Entrées HDMI avec un câble branché (appareil allumé ou en veille), dans l'ordre des ports. */
fun loadHdmiSources(context: Context): List<TvSource> {
    val tvm = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
    return tvm.tvInputList
        .filter { it.type == TvInputInfo.TYPE_HDMI }
        .filter { tvm.getInputState(it.id) != TvInputManager.INPUT_STATE_DISCONNECTED }
        // Identifiants ".../HW5" à ".../HW8" chez Philips : HDMI 1 à 4
        .sortedBy { it.id }
        .map { info ->
            // Nom donné dans les réglages de la TV ("Bouygues"…), sinon "HDMI 2".
            // Philips y colle un suffixe d'icône ("Bouygues &null") qu'on retire.
            val custom = info.loadCustomLabel(context)?.toString()?.removeSuffix("&null")?.trim()
            TvSource(custom?.takeIf { it.isNotEmpty() } ?: info.loadLabel(context).toString(), info.id)
        }
}

fun launchSource(context: Context, source: TvSource) {
    // Le lecteur TV Philips (org.droidtv.playtv) n'accepte l'URI qu'avec le type "chaîne".
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(
            TvContract.buildChannelUriForPassthroughInput(source.inputId),
            TvContract.Channels.CONTENT_ITEM_TYPE,
        )
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
