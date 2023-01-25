package it.unibo.explain

import it.unibo.Intention
import org.slf4j.LoggerFactory
import java.io.File

class Explain : Intention {
    /**
     * The measure of the explain operator, which is to be compared with all the others
     */
    var explainMeasure: String = ""

    constructor(explainIntention: Intention?, accumulateAttributes: Boolean): super(explainIntention, accumulateAttributes)
    constructor(accumulateAttributes: Boolean): super(null, accumulateAttributes)

    private fun pathFormatter(path: String): String {
        return if (path.contains(" ")) "\"" else ""
    }

    override fun toPythonCommand(commandPath: String, path: String): String {
        val filename = getFilename()
        val fullCommand = (commandPath.replace("/", File.separator)
                + " --path " + pathFormatter(path) + path.replace("\\", "/") + pathFormatter(path)
                + " --file " + filename
                + " --explain " + explainMeasure
                + " --intention " + json.toString().replace(" ", "__"))
        LoggerFactory.getLogger(Explain::class.java).warn(fullCommand)
        return fullCommand
    }

    override fun toString(): String {
        return "with $cubeSyn " +
                "by ${attributes.reduce { a, b -> "$a, $b" }} " +
                if (clauses.isEmpty()) { "" }
                else { "for ${clauses.toList().map { triple -> triple.left + triple.middle + triple.right }
                    .reduce { a, b -> "$a and $b"} } " } +
                "explain ${measures.reduce { a, b -> "$a, $b" }} "
    }
}