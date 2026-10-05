package ru.feskolech.libriatv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.CoreConfigurationBuilder

@HiltAndroidApp
class LibriaTvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ru.feskolech.libriatv.data.repo.NewEpisodesWorker.schedule(this)
        if (BuildConfig.CRASH_REPORT_URL.isBlank()) return
        ACRA.init(this, CoreConfigurationBuilder()
            .withReportContent(
                ReportField.REPORT_ID,
                ReportField.APP_VERSION_CODE,
                ReportField.APP_VERSION_NAME,
                ReportField.PACKAGE_NAME,
                ReportField.PHONE_MODEL,
                ReportField.ANDROID_VERSION,
                ReportField.STACK_TRACE,
                ReportField.USER_CRASH_DATE,
            )
            .withDeleteUnapprovedReportsOnApplicationStart(false)
            .withAlsoReportToAndroidFramework(false))
    }
}
