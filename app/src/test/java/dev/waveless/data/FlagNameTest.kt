package dev.waveless.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FlagNameTest {
    @Test
    fun `leading country flag is split off`() {
        assertEquals(FlagName("🇩🇪", "Frankfurt"), FlagName.of("🇩🇪 Frankfurt"))
        assertEquals(FlagName("🇳🇱", "Amsterdam"), FlagName.of("🇳🇱Amsterdam"))
        assertEquals(FlagName("🇺🇸", "New York"), FlagName.of("🇺🇸 | New York"))
        assertEquals(FlagName("🇫🇮", "🇸🇪 relay"), FlagName.of("🇫🇮 🇸🇪 relay"))
    }

    @Test
    fun `subdivision tag flags count`() {
        val england = "🏴󠁧󠁢󠁥󠁮󠁧󠁿"
        assertEquals(FlagName(england, "London"), FlagName.of("$england London"))
    }

    @Test
    fun `no flag unless it is the first symbol`() {
        assertEquals(FlagName(null, "Frankfurt 🇩🇪"), FlagName.of("Frankfurt 🇩🇪"))
        assertEquals(FlagName(null, " 🇩🇪 Frankfurt"), FlagName.of(" 🇩🇪 Frankfurt"))
        assertEquals(FlagName(null, "🔥 Fast"), FlagName.of("🔥 Fast"))
        // A lone regional indicator or a plain black flag is not a country flag.
        assertEquals(FlagName(null, "🇩 x"), FlagName.of("🇩 x"))
        assertEquals(FlagName(null, "🏴 pirate"), FlagName.of("🏴 pirate"))
        assertEquals(FlagName(null, ""), FlagName.of(""))
    }

    @Test
    fun `flag-only name keeps the flag as label`() {
        assertEquals(FlagName("🇯🇵", "🇯🇵"), FlagName.of("🇯🇵"))
    }
}
