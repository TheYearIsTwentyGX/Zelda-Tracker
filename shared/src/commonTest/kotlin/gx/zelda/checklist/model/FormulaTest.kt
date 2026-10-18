package gx.zelda.checklist.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FormulaTest {
    private fun eval(text: String, vararg names: Pair<String, Double>): Double =
        Formula.parse(text).eval { name -> names.toMap()[name] ?: error("unknown $name") }

    @Test
    fun arithmeticFollowsPrecedence() {
        assertEquals(7.0, eval("1 + 2 * 3"))
        assertEquals(9.0, eval("(1 + 2) * 3"))
        assertEquals(1.0, eval("8 / 4 / 2"))
        assertEquals(-1.0, eval("2 - 3"))
        assertEquals(1.0, eval("-2 + 3"))
        assertEquals(2.5, eval("2.5"))
    }

    @Test
    fun namesReadSectionValues() {
        assertEquals(8.0, eval("3 + floor(pieces / 4) + containers", "pieces" to 11.0, "containers" to 3.0))
        assertEquals(15.0, eval("crystals.value", "crystals.value" to 15.0))
        assertEquals(setOf("pieces", "containers", "crystals.value"), Formula.parse("pieces + min(containers, crystals.value)").names)
    }

    @Test
    fun functions() {
        assertEquals(2.0, eval("floor(2.75)"))
        assertEquals(1.0, eval("min(4, 1, 3)"))
        assertEquals(4.0, eval("max(4, 1, 3)"))
    }

    @Test
    fun divisionByZeroIsZero() = assertEquals(0.0, eval("5 / 0"))

    @Test
    fun syntaxErrorsSayWhere() {
        listOf("1 +", "(1 + 2", "1 2", "sqrt(4)", "floor(1, 2)", "Pieces", "a.b.c", "3 $ 4", "").forEach { text ->
            val e = assertFailsWith<IllegalArgumentException>(text) { Formula.parse(text) }
            assertTrue(e.message!!.contains("position"), e.message)
        }
    }

    @Test
    fun amountsAreFormattedLikeTheGame() {
        assertEquals("12", formatAmount(12.0))
        assertEquals("5¼", formatAmount(5.25))
        assertEquals("½", formatAmount(0.5))
        assertEquals("3¾", formatAmount(3.75))
        assertEquals("⅔", formatAmount(2.0 / 3))
        assertEquals("1.35", formatAmount(1.35))
        assertEquals("1.1", formatAmount(1.1))
        assertEquals("-2", formatAmount(-2.0))
    }
}
