package com.irpro.tv.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.irpro.tv.R
import com.irpro.tv.data.UserData

/** Petits sons de navigation façon console (désactivables dans Paramètres > Apparence et sons). */
object Sfx {
    private var pool: SoundPool? = null
    private var move = 0
    private var select = 0
    private var last = 0L

    fun init(ctx: Context) {
        if (pool != null) return
        pool = SoundPool.Builder().setMaxStreams(2)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build().also {
                move = it.load(ctx, R.raw.nav_move, 1)
                select = it.load(ctx, R.raw.nav_select, 1)
            }
    }

    fun move() {
        if (!UserData.settings.value.navSounds) return
        val now = System.currentTimeMillis()
        if (now - last < 45) return          // pas de crépitement en défilement rapide
        last = now
        pool?.play(move, 0.35f, 0.35f, 1, 0, 1f)
    }

    fun select() { if (UserData.settings.value.navSounds) pool?.play(select, 0.45f, 0.45f, 1, 0, 1f) }
}
