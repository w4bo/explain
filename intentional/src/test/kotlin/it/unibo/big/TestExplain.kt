package it.unibo.big

import it.unibo.Intention
import it.unibo.conversational.database.QueryGenerator
import it.unibo.explain.Explain
import it.unibo.explain.ExplainExecute
import java.io.File
import java.nio.file.Paths
import java.io.IOException
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import krangl.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.streams.toList

data class ExplainQuery(val id: Int, val cardinality: Int, val query: String)

internal class TestExplain {
    private val path = "resources/intention/output/explain/"

    private fun getJsonDataFromFile(filePath: String): String? {
        val jsonString: String
        try {
            jsonString = File(filePath).readText(Charsets.UTF_8)
        } catch (ioException: IOException) {
            ioException.printStackTrace()
            return null
        }
        return jsonString
    }

    @BeforeEach
    fun before() {
        Intention.DEBUG = true
        ExplainExecute.Vmemb.clear()
        ExplainExecute.Vcoord.clear()
    }

    @Test
    fun getMeasuresTest() {
        val intention: Explain = ExplainExecute.parse("with sales_fact_1997 by product_subcategory " +
                "for product_subcategory in ('Bagels', 'Beer', 'Bologna') explain unit_sales")
        assertEquals(listOf("store_cost", "store_sales", "unit_sales"),
            QueryGenerator.getMeasures(intention.cube).stream().map { it.lowercase() }.toList(),
        "the cube measure names don't match")
    }

    @Test
    fun foodTest() {
        try {
            val componentTable: DataFrame = dataFrameOf("Component", "Interest", "Property")(
                "(unit_sales, store_sales)",    "-inf", "15386.71",
                "(unit_sales, store_cost)",     "-inf", "0.99x-56.80"
            )
            val intention: Explain = ExplainExecute.parse("with sales_fact_1997 by product_subcategory for " +
                    "product_subcategory in ('Bagels', 'Beer', 'Bologna') explain unit_sales")
            val desiredTable: DataFrame = ExplainExecute.execute(intention, path).second
                .sortedByDescending("Interest")
            componentTable.names.forEach { assertEquals(componentTable[it], desiredTable[it], it) }
        } catch (e: Exception) {
            e.printStackTrace()
            fail(e.message)
        }
    }

    @Test
    fun covidTest() {
        try {
            val componentTable: DataFrame = dataFrameOf("Component", "Interest", "Property")(
                "(cases, deaths)",  "-inf", "0.01x+11986.21"
            )
            val intention: Explain = ExplainExecute.parse("with COVID-19 by month, country for " +
                    "country in ('United States Of America', 'Italy') and month in ('2020-11', '2020-12') " +
                    "explain cases")
            val desiredTable: DataFrame = ExplainExecute.execute(intention, path).second
                .sortedByDescending("Interest")
            componentTable.names.forEach { assertEquals(componentTable[it], desiredTable[it], it) }
        } catch (e: Exception) {
            e.printStackTrace()
            fail(e.message)
        }
    }

    /**
     * Test program execution time as the number of tuples processed increases
     */
    @Test
    @ExperimentalTime
    fun queriesExecutionTimeTest() {
        val jsonFileString = getJsonDataFromFile(Paths.get("").toAbsolutePath().toString() +
                "/src/main/resources/explain/explain-query.json")

        val gson = Gson()
        val listPersonType = object : TypeToken<List<ExplainQuery>>() {}.type
        val queries: List<ExplainQuery> = gson.fromJson(jsonFileString, listPersonType)

        val durations = mutableListOf<Duration>()

        queries.forEach { query ->
            val elapsed: Duration = measureTime {
                val intention: Explain = ExplainExecute.parse(query.query)
                ExplainExecute.execute(intention, path)
            }
            durations.add(query.id, elapsed)
        }
        print(durations)
    }
}