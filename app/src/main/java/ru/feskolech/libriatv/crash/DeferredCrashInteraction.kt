package ru.feskolech.libriatv.crash

import android.content.Context
import org.acra.config.CoreConfiguration
import org.acra.interaction.ReportInteraction
import java.io.File

/** Keep ACRA's report local until the TV dialog or the saved opt-in decides what to do. */
class DeferredCrashInteraction : ReportInteraction {
    override fun performInteraction(context: Context, config: CoreConfiguration, reportFile: File) = false
}
