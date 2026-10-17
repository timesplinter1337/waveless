package dev.waveless.data

/**
 * A server name split into its leading country flag emoji (if any) and the rest,
 * e.g. "🇩🇪 Frankfurt" → flag "🇩🇪", label "Frankfurt".
 */
data class FlagName(val flag: String?, val label: String) {
    companion object {
        private const val REGIONAL_A = 0x1F1E6
        private const val REGIONAL_Z = 0x1F1FF
        private const val BLACK_FLAG = 0x1F3F4
        private const val TAG_FIRST = 0xE0020
        private const val TAG_LAST = 0xE007E
        private const val CANCEL_TAG = 0xE007F

        /** Only a flag that is the very first symbol of [name] counts. */
        fun of(name: String): FlagName {
            val end = flagEnd(name) ?: return FlagName(null, name)
            val rest = name.substring(end).trimStart(' ', ' ', '|', '-', '–', '—', '·', ',', ':')
            return FlagName(name.substring(0, end), rest.ifEmpty { name.substring(0, end) })
        }

        /** End index of a leading flag: a regional-indicator pair, or a subdivision tag flag (🏴 + tags). */
        private fun flagEnd(s: String): Int? {
            if (s.isEmpty()) return null
            val first = s.codePointAt(0)
            if (first in REGIONAL_A..REGIONAL_Z) {
                val i = Character.charCount(first)
                if (i >= s.length) return null
                val second = s.codePointAt(i)
                return if (second in REGIONAL_A..REGIONAL_Z) i + Character.charCount(second) else null
            }
            if (first == BLACK_FLAG) {
                var i = Character.charCount(first)
                var tags = 0
                while (i < s.length) {
                    val cp = s.codePointAt(i)
                    i += Character.charCount(cp)
                    when (cp) {
                        in TAG_FIRST..TAG_LAST -> tags++
                        CANCEL_TAG -> return if (tags > 0) i else null
                        else -> return null
                    }
                }
            }
            return null
        }
    }
}
