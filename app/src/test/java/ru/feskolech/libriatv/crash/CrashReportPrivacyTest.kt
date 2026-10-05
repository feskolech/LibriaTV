package ru.feskolech.libriatv.crash

import org.junit.Assert.assertEquals
import org.junit.Test

class CrashReportPrivacyTest {
    @Test fun stripsExceptionMessagesAndSensitiveFrames() {
        val stack = """
            java.lang.IllegalStateException: Authorization: Bearer secret-token login=alice
                at ru.feskolech.libriatv.ui.Main.render(Main.kt:42)
                at ru.feskolech.libriatv.data.Auth.password(Auth.kt:8)
            Caused by: java.io.IOException: token=secret-token
                at ru.feskolech.libriatv.data.Api.call(Api.kt:12)
        """.trimIndent()

        assertEquals("""
            java.lang.IllegalStateException
            at ru.feskolech.libriatv.ui.Main.render(Main.kt:42)
            Caused by: java.io.IOException
            at ru.feskolech.libriatv.data.Api.call(Api.kt:12)
        """.trimIndent(), sanitizeCrashStack(stack))
    }
}
