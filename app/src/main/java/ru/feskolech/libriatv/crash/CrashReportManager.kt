package ru.feskolech.libriatv.crash

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.acra.file.CrashReportPersister
import org.acra.file.ReportLocator
import org.acra.ReportField
import ru.feskolech.libriatv.BuildConfig
import ru.feskolech.libriatv.data.repo.SettingsStore

@Singleton
class CrashReportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
) {
    val available: Boolean get() = BuildConfig.CRASH_REPORT_URL.isNotBlank()

    fun pending(): Boolean = available && ReportLocator(context).unapprovedReports.any(File::isFile)

    suspend fun automatic(): Boolean = available && settings.automaticCrashReports()

    suspend fun setAutomatic(value: Boolean) = settings.setAutomaticCrashReports(value)

    fun discard() {
        if (available) ReportLocator(context).unapprovedReports.forEach { it.delete() }
    }

    suspend fun sendPending(): Boolean = withContext(Dispatchers.IO) {
        if (!available) return@withContext false
        val reports = ReportLocator(context).unapprovedReports.filter(File::isFile)
        if (reports.isEmpty()) return@withContext true
        val client = OkHttpClient()
        reports.all { file ->
            runCatching {
                val report = CrashReportPersister().load(file)
                report.put(ReportField.STACK_TRACE, sanitizeCrashStack(report.getString(ReportField.STACK_TRACE).orEmpty()))
                val payload = report.toJSON()
                val request = Request.Builder().url(BuildConfig.CRASH_REPORT_URL)
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .apply {
                        if (BuildConfig.CRASH_REPORT_LOGIN.isNotEmpty()) {
                            header("Authorization", Credentials.basic(
                                BuildConfig.CRASH_REPORT_LOGIN, BuildConfig.CRASH_REPORT_PASSWORD))
                        }
                    }.build()
                client.newCall(request).execute().use { response ->
                    response.isSuccessful && file.delete()
                }
            }.getOrDefault(false)
        }
    }

}

/** Exception messages can contain credentials, so only type names and non-sensitive frames survive. */
internal fun sanitizeCrashStack(stack: String): String = stack.lineSequence().mapNotNull { line ->
    val trimmed = line.trim()
    val type = trimmed.substringBefore(':')
    when {
        trimmed.startsWith("at ") && !Regex("(?i)authorization|bearer|token|password|login").containsMatchIn(trimmed) -> trimmed
        trimmed.startsWith("Caused by: ") -> "Caused by: " + trimmed.removePrefix("Caused by: ").substringBefore(':')
        type.endsWith("Exception") || type.endsWith("Error") -> type
        else -> null
    }
}.joinToString("\n")
