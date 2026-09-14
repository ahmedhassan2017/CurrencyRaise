package com.example.currencyraise

import android.content.res.Configuration
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizationTest {
    @Test
    fun arabicResourcesAreSelectedWithRtlLayout() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ar"))
        }
        val arabic = base.createConfigurationContext(configuration)

        assertEquals("اختر البنك", arabic.getString(R.string.select_bank))
        assertEquals("تحديث الأسعار", arabic.getString(R.string.refresh_rates))
        assertEquals("لغة التطبيق", arabic.getString(R.string.language_title))
        assertEquals(View.LAYOUT_DIRECTION_RTL, arabic.resources.configuration.layoutDirection)
    }
}
