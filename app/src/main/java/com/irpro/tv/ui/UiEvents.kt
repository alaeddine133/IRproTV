package com.irpro.tv.ui

import kotlinx.coroutines.flow.MutableStateFlow

/** Signal « reprends le focus » envoyé aux écrans quand une fenêtre par-dessus se ferme. */
object UiEvents {
    val refocus = MutableStateFlow(0)
    fun requestRefocus() { refocus.value++ }
}
