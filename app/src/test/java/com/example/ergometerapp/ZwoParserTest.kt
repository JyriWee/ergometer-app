package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Test

import com.example.ergometerapp.workout.parseZwo

class ZwoParserTest {
    @Test
    fun tagAttributeOnlyIsParsed() {
        val xml = """
            <workout_file>
              <tags>
                <tag name="test"/>
              </tags>
              <workout/>
            </workout_file>
        """.trimIndent()

        val result = parseZwo(xml)

        assertEquals(listOf("test"), result.tags)
    }

    @Test
    fun tagTextOnlyIsParsed() {
        val xml = """
            <workout_file>
              <tags>
                <tag>short</tag>
              </tags>
              <workout/>
            </workout_file>
        """.trimIndent()

        val result = parseZwo(xml)

        assertEquals(listOf("short"), result.tags)
    }

    @Test
    fun tagAttributeOverridesTextAndNoDuplicateIsAdded() {
        val xml = """
            <workout_file>
              <tags>
                <tag name="name">text</tag>
              </tags>
              <workout/>
            </workout_file>
        """.trimIndent()
        val result = parseZwo(xml)

        assertEquals(listOf("name"), result.tags)
    }
}
