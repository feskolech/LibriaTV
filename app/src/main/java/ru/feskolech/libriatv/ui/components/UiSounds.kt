package ru.feskolech.libriatv.ui.components

/** Navigation click sounds are off while a video plays, so seeking with the D-pad stays silent. */
object UiSounds {
    @Volatile var enabled: Boolean = true
}
