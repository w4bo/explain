package it.unibo.explain

import it.unibo.Intention
import it.unibo.antlr.gen.ExplainParser
import it.unibo.antlr.gen.ExplainParser.CContext
import it.unibo.antlr.gen.ExplainParser.ExplainContext
import it.unibo.antlr.gen.ExplainBaseListener
import it.unibo.conversational.database.QueryGenerator
import org.antlr.v4.runtime.tree.ErrorNode
import org.apache.commons.lang3.tuple.Triple
import java.util.stream.Collectors

/**
 * How to interpret an Explain syntax
 */
class ExplainListenerCustom(d: Intention?, accumulateAttributes: Boolean) : ExplainBaseListener() {
    var explain: Explain? = null

    override fun visitErrorNode(node: ErrorNode) {
        throw IllegalArgumentException("Invalid explain syntax")
    }

    override fun exitC(ctx: CContext) {
        explain!!.setCube(ctx.cube.name)
    }

    override fun exitExplain(ctx: ExplainContext) {
        ctx.mc.stream().map { obj: ExplainParser.IdContext -> obj.text }.collect(Collectors.toList()).firstOrNull()
            ?.let { explain!!.explainMeasure = it.lowercase() } // set measure of the explain operator
        explain!!.measures = QueryGenerator.getMeasures(explain!!.cube).stream() // set all measures
            .map { it.lowercase() }.collect(Collectors.toSet())
        ctx.gc?.let { explain!!.addAttribute(true, *ctx.gc.stream() // set all attributes
            .map { t: ExplainParser.IdContext -> t.name }.toArray()) }
    }

    override fun exitCondition(ctx: ExplainParser.ConditionContext) {
        explain!!.addClause(Triple.of(
            ctx.attr.text, 
            if (ctx.op == null) ctx.`in`.text else ctx.op.text, 
            ctx.`val`.stream().map { obj: ExplainParser.ValueContext -> obj.text }.collect(Collectors.toList())
        ))
    }

    init {
        explain =
            if (d == null) {
                Explain(accumulateAttributes)
            } else {
                Explain(d, accumulateAttributes)
            }
    }
}