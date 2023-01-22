package it.unibo.explain

import it.unibo.Intention
import it.unibo.antlr.gen.ExplainLexer
import it.unibo.antlr.gen.ExplainParser
import krangl.DataFrame
import krangl.readCSV
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Explain intention in action.
 */
object ExplainExecute {
    private val L = LoggerFactory.getLogger(ExplainExecute::class.java)
    val Vcoord: MutableSet<MutableSet<String>> = mutableSetOf()
    val Vmemb: MutableMap<String, Double> = mutableMapOf()

    fun clear() {
        Vcoord.clear()
        Vmemb.clear()
    }

    fun parse(input: String?): Explain {
        return parse(null, input, true)
    }

    fun parse(input: String?, accumulateAttributes: Boolean): Explain {
        return parse(null, input, accumulateAttributes)
    }

    fun parse(d: Intention?, input: String?): Explain {
        return parse(d, input, true)
    }

    fun parse(d: Intention?, input: String?, accumulateAttributes: Boolean): Explain {
        val charStream: CharStream? = if (input != null) CharStreams.fromString(input) else CharStreams.fromStream(System.`in`)
        val lexer = ExplainLexer(charStream) // new input stream
        val tokens = CommonTokenStream(lexer) // create a buffer of tokens pulled from the lexer
        val parser = ExplainParser(tokens) // create a parser that feeds off the tokens buffer
        val tree: ParseTree = parser.explain() // begin parsing at init rule
        val walker = ParseTreeWalker() // create standard walker
        val extractor = ExplainListenerCustom(d, accumulateAttributes)
        walker.walk(extractor, tree) // initiate walk of tree with listener
        return extractor.explain!!
    }

    @JvmOverloads
    @Throws(Exception::class)
    fun execute(intention: Explain, path: String, pythonPath: String = "src/main/python/"): Pair<JSONObject, DataFrame> {
        val json = JSONObject()
        intention.writeMultidimensionalCube(path)
        L.warn("Computing correlation models...")
        intention.computePython(pythonPath, path, "explain.py")
        L.warn("Computed correlation models")

        val cube = mutableListOf<String>()
        File("${path}cube.txt").useLines { lines -> lines.forEach { cube.add(it) }}
        json.put("cube", JSONObject(cube.joinToString(separator = "")))
        val pivotTable = mutableListOf<String>()
        File("${path}pivot_table.txt").useLines { lines -> lines.forEach { pivotTable.add(it) }}
        json.put("pivot_table", JSONObject(pivotTable.joinToString(separator = "")))
        val fittingComponentTable = mutableListOf<String>()
        File("${path}fitting_component_table.txt")
            .useLines { lines -> lines.forEach { fittingComponentTable.add(it) }}
        json.put("fitting_component_table", JSONObject(fittingComponentTable.joinToString(separator = "")))

        val testComponentTable: DataFrame = DataFrame.readCSV("${path}test_fitting_component_table.csv")

        return Pair(json, testComponentTable)
    }
}