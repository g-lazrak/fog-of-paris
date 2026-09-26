package com.glazrak.fogofparis.data

import com.glazrak.fogofparis.domain.CellId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyCellParsingTest {

    @Test
    fun parses_prototype_format_including_negative_indices() {
        assertEquals(CellId(12, 34), parseLegacyCell("12,34"))
        assertEquals(CellId(-5, 0), parseLegacyCell("-5,0"))
    }

    @Test
    fun malformed_text_gives_null() {
        assertNull(parseLegacyCell(""))
        assertNull(parseLegacyCell("12"))
        assertNull(parseLegacyCell("a,b"))
        assertNull(parseLegacyCell("1,2,3"))
    }
}
