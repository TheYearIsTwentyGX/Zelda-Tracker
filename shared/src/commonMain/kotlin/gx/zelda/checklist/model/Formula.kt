package gx.zelda.checklist.model

import kotlin.math.floor

/**
 * A checklist summary formula, e.g. `3 + floor(pieces / 4) + containers`: numbers, `+ - * /`,
 * parentheses, `floor`, `min` and `max`, and names. A name is a section id, or `<section id>.value`;
 * [ChecklistCatalog] says what each one means. Dividing by zero gives 0.
 */
class Formula private constructor(val text: String, private val root: Node) {
    /** Every name the formula reads, e.g. `pieces` and `crystals.value`. */
    val names: Set<String> = buildSet { root.collectNames(this) }

    /** The formula's value; [lookup] gives each name's value and may throw for an unknown one. */
    fun eval(lookup: (String) -> Double): Double = root.eval(lookup)

    override fun toString(): String = text

    companion object {
        /** Throws [IllegalArgumentException] with the position of the first syntax error. */
        fun parse(text: String): Formula = Formula(text, Parser(text).parseAll())
    }

    private sealed interface Node {
        fun eval(lookup: (String) -> Double): Double
        fun collectNames(into: MutableSet<String>) {}
    }

    private class Num(val value: Double) : Node {
        override fun eval(lookup: (String) -> Double) = value
    }

    private class Name(val name: String) : Node {
        override fun eval(lookup: (String) -> Double) = lookup(name)
        override fun collectNames(into: MutableSet<String>) {
            into += name
        }
    }

    private class Neg(val operand: Node) : Node {
        override fun eval(lookup: (String) -> Double) = -operand.eval(lookup)
        override fun collectNames(into: MutableSet<String>) = operand.collectNames(into)
    }

    private class Binary(val op: Char, val left: Node, val right: Node) : Node {
        override fun eval(lookup: (String) -> Double): Double {
            val a = left.eval(lookup)
            val b = right.eval(lookup)
            return when (op) {
                '+' -> a + b
                '-' -> a - b
                '*' -> a * b
                else -> if (b == 0.0) 0.0 else a / b
            }
        }

        override fun collectNames(into: MutableSet<String>) {
            left.collectNames(into)
            right.collectNames(into)
        }
    }

    private class Call(val function: String, val args: List<Node>) : Node {
        override fun eval(lookup: (String) -> Double): Double {
            val values = args.map { it.eval(lookup) }
            return when (function) {
                "floor" -> floor(values.single())
                "min" -> values.min()
                else -> values.max()
            }
        }

        override fun collectNames(into: MutableSet<String>) = args.forEach { it.collectNames(into) }
    }

    /** Recursive descent over the text: sums of products of unary terms. */
    private class Parser(private val text: String) {
        private var pos = 0

        fun parseAll(): Node {
            val node = sum()
            skipSpace()
            if (pos < text.length) fail("unexpected '${text[pos]}'")
            return node
        }

        private fun sum(): Node {
            var node = product()
            while (true) {
                val op = peekOp("+-") ?: return node
                pos++
                node = Binary(op, node, product())
            }
        }

        private fun product(): Node {
            var node = unary()
            while (true) {
                val op = peekOp("*/") ?: return node
                pos++
                node = Binary(op, node, unary())
            }
        }

        private fun unary(): Node {
            if (peekOp("-") != null) {
                pos++
                return Neg(unary())
            }
            return primary()
        }

        private fun primary(): Node {
            skipSpace()
            if (pos >= text.length) fail("expected a number, a name or '('")
            val c = text[pos]
            return when {
                c == '(' -> {
                    pos++
                    sum().also { expect(')') }
                }
                c.isDigit() -> number()
                c.isLetter() || c == '_' -> nameOrCall()
                else -> fail("unexpected '$c'")
            }
        }

        private fun number(): Node {
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
            val token = text.substring(start, pos)
            return Num(token.toDoubleOrNull() ?: fail("bad number '$token'", start))
        }

        private fun nameOrCall(): Node {
            val start = pos
            while (pos < text.length && (text[pos].isLetterOrDigit() || text[pos] == '_' || text[pos] == '.')) pos++
            val name = text.substring(start, pos)
            skipSpace()
            if (pos < text.length && text[pos] == '(') {
                if (name !in FUNCTIONS) fail("unknown function '$name'", start)
                pos++
                val args = mutableListOf(sum())
                while (peekOp(",") != null) {
                    pos++
                    args += sum()
                }
                expect(')')
                if (name == "floor" && args.size != 1) fail("floor takes one value", start)
                return Call(name, args)
            }
            if (!NAME.matches(name)) fail("bad name '$name'", start)
            return Name(name)
        }

        private fun peekOp(ops: String): Char? {
            skipSpace()
            return text.getOrNull(pos)?.takeIf { it in ops }
        }

        private fun expect(c: Char) {
            if (peekOp(c.toString()) == null) fail("expected '$c'")
            pos++
        }

        private fun skipSpace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun fail(message: String, at: Int = pos): Nothing =
            throw IllegalArgumentException("Formula \"$text\", position ${at + 1}: $message")
    }
}

private val FUNCTIONS = setOf("floor", "min", "max")
private val NAME = Regex("[a-z_][a-z0-9_]*(\\.value)?")

/**
 * [x] for a summary line: whole numbers plainly ("12"), common fractions as the game would show
 * them ("5¼", "⅔"), anything else to two decimal places at most ("1.35").
 */
fun formatAmount(x: Double): String {
    if (x < 0) return "-" + formatAmount(-x)
    val hundredths = kotlin.math.round(x * 100).toLong()
    val whole = hundredths / 100
    val fraction = when (hundredths % 100) {
        0L -> return whole.toString()
        25L -> "¼"
        50L -> "½"
        75L -> "¾"
        33L -> "⅓"
        67L -> "⅔"
        else -> return "$whole.${(hundredths % 100).toString().padStart(2, '0').trimEnd('0')}"
    }
    return if (whole == 0L) fraction else "$whole$fraction"
}
