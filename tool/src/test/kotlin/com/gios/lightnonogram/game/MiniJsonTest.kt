package com.gios.lightnonogram.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pack reader used to be a `Regex` whose pattern the JVM accepted and
 * Android's ICU-backed `Pattern` rejected, so every test here passed while the
 * app died on launch. A hand-written scanner can't have that failure mode, but it
 * can have ordinary bugs — hence this suite.
 */
class MiniJsonTest {

    @Test
    fun `parses the shapes the pack actually uses`() {
        val v = MiniJson.parse(
            """{"id":"p","w":10,"h":10,"bits":"AA==","difficulty":3,"passes":4,"source":"heart"}"""
        ).asObject("root")
        assertEquals("p", v.string("id", "root"))
        assertEquals(10, v.int("w", "root"))
        assertEquals(3, v.int("difficulty", "root"))
        assertEquals("heart", v.stringOrNull("source"))
        assertNull(v.stringOrNull("nope"))
    }

    @Test
    fun `handles nesting, arrays and whitespace`() {
        val root = MiniJson.parse(
            """
            {
              "name" : "Pictures" ,
              "version": 1,
              "puzzles": [
                 {"id":"a","w":2,"h":2,"bits":"AA=="},
                 {"id":"b","w":2,"h":2,"bits":"//8="}
              ]
            }
            """.trimIndent()
        ).asObject("root")
        assertEquals("Pictures", root.string("name", "root"))
        val list = root["puzzles"].asArray("puzzles")
        assertEquals(2, list.size)
        assertEquals("b", list[1].asObject("p1").string("id", "p1"))
    }

    @Test
    fun `empty containers`() {
        assertEquals(0, MiniJson.parse("{}").asObject("o").size)
        assertEquals(0, MiniJson.parse("[]").asArray("a").size)
        assertEquals(0, MiniJson.parse("""{"puzzles":[]}""").asObject("o")["puzzles"].asArray("a").size)
    }

    @Test
    fun `string escapes`() {
        val o = MiniJson.parse(
            """{"a":"q\"q","b":"back\\slash","c":"tab\there","d":"nl\nhere","e":"Aé","f":"sl\/ash"}"""
        ).asObject("o")
        assertEquals("q\"q", o.string("a", "o"))
        assertEquals("back\\slash", o.string("b", "o"))
        assertEquals("tab\there", o.string("c", "o"))
        assertEquals("nl\nhere", o.string("d", "o"))
        assertEquals("Aé", o.string("e", "o"))
        assertEquals("sl/ash", o.string("f", "o"))
    }

    @Test
    fun `numbers, signs and literals`() {
        val o = MiniJson.parse(
            """{"a":0,"b":-7,"c":1234567890123,"d":1.5,"e":-2.5e3,"f":true,"g":false,"h":null}"""
        ).asObject("o")
        assertEquals(0, o.int("a", "o"))
        assertEquals(-7, o.int("b", "o"))
        assertEquals(1234567890123L, o["c"])
        assertEquals(1.5, o["d"])
        assertEquals(-2500.0, o["e"])
        assertEquals(true, o["f"])
        assertEquals(false, o["g"])
        assertNull(o["h"])
    }

    @Test
    fun `braces inside strings do not confuse the scanner`() {
        // The exact thing the old regex was trying (and failing) to reason about.
        val o = MiniJson.parse("""{"a":"a{b}c","b":"}{","c":"[]"}""").asObject("o")
        assertEquals("a{b}c", o.string("a", "o"))
        assertEquals("}{", o.string("b", "o"))
        assertEquals("[]", o.string("c", "o"))
    }

    @Test
    fun `malformed input fails with a located message`() {
        for (bad in listOf(
            "{",
            "{\"a\"}",
            "{\"a\":}",
            "[1,]",
            "{\"a\":1}}",
            "\"unterminated",
            "{\"a\":\"bad\\q\"}",
            "{\"a\":\"\\u00\"}",
            "-",
            "tru",
        )) {
            val e = assertFailsWith<IllegalArgumentException>("should have rejected: $bad") {
                MiniJson.parse(bad)
            }
            assertTrue(
                e.message!!.contains("offset") || e.message!!.contains("not an"),
                "message should locate the problem, got: ${e.message}",
            )
        }
    }

    @Test
    fun `a missing required field names the field and the puzzle`() {
        val e = assertFailsWith<IllegalArgumentException> {
            PackReader.parse("""{"puzzles":[{"id":"a","w":10,"h":10}]}""")
        }
        assertTrue(e.message!!.contains("bits"), "should name the field, got: ${e.message}")
        assertTrue(e.message!!.contains("puzzles[0]"), "should name the entry, got: ${e.message}")
    }
}
