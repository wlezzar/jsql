package io.github.wlezzar.jsql.sql

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.calcite.DataContext
import org.apache.calcite.linq4j.Enumerable
import org.apache.calcite.linq4j.Linq4j
import org.apache.calcite.rel.type.RelDataType
import org.apache.calcite.rel.type.RelDataTypeFactory
import org.apache.calcite.rex.RexNode
import org.apache.calcite.schema.FilterableTable
import org.apache.calcite.schema.ScannableTable
import org.apache.calcite.schema.impl.AbstractTable
import org.apache.calcite.sql.type.SqlTypeName
import java.io.File

val json: ObjectMapper = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

/**
 * Calcite table based on a json [Source].
 */
class JsonTable(private val source: Source) : AbstractTable(), ScannableTable {

    private var type: RelDataType? = null

    override fun getRowType(typeFactory: RelDataTypeFactory): RelDataType {
        if (type == null) {
            type = with(typeFactory) {
                val inferred = inferRowType(source.fetchStandardizedRows().take(50).toList())
                replaceNullTypes(inferred, replacement = createSqlType(SqlTypeName.VARCHAR))
            }
        }

        return type!!
    }

    override fun scan(root: DataContext): Enumerable<Array<out Any?>> {
        val type = type ?: throw IllegalStateException("type should be known...")
        val data = source
            .fetchStandardizedRows()
            .map { convertJsonRow(it, type, failOnError = false) }
            .toList()

        return Linq4j.asEnumerable(data)
    }

}

class JsonFilterableTable(private val table: JsonTable) : AbstractTable(), FilterableTable {
    override fun getRowType(typeFactory: RelDataTypeFactory): RelDataType = table.getRowType(typeFactory)
    override fun scan(root: DataContext, filters: MutableList<RexNode>?): Enumerable<Array<out Any?>> {
        return table.scan(root)
    }
}

/**
 * A source for json data. It provides a [fetch] method to fetch json data.
 *
 * The [fetch] method should be callable multiple times and provide the same results. If this requirement is not
 * possible to achieve (ex. fetching data from stdin), the source should be wrapped into a [CachedSource].
 */
interface Source {
    fun fetch(): Iterator<JsonNode>
}

/**
 * A [Source] implementation that fetches json data from a [data] list given as parameters.
 */
class InlineSource(private val data: List<JsonNode>) : Source {
    override fun fetch(): Iterator<JsonNode> = data.iterator()
}

/**
 * A [Source] wrapper that makes the [wrapped] source cached in memory.
 */
class CachedSource(private val wrapped: Source) : Source {
    private val cached: List<JsonNode> by lazy { wrapped.fetch().asSequence().toList() }
    override fun fetch(): Iterator<JsonNode> = cached.iterator()
}

/**
 * A [Source] implementation that fetches data from a file.
 */
class FileSource(private val file: File) : Source {
    override fun fetch(): Iterator<JsonNode> {
        val parsed = json.readTree(file)
        require(parsed is ArrayNode) { "data read from '$file' should be an array..." }
        return parsed.iterator()
    }
}

/**
 * A [Source] that fetches json data from stdin.
 */
class StdinSource(private val streaming: Boolean, private val limit: Int?) : Source {

    override fun fetch(): Iterator<JsonNode> {
        val parsed: ArrayNode =
            if (streaming) {
                System.`in`
                    .bufferedReader(Charsets.UTF_8)
                    .lineSequence()
                    .take(limit ?: 100)
                    .fold(initial = json.createArrayNode()) { acc, line -> acc.add(json.readTree(line)) }
            } else {
                val data = System.`in`.readBytes()
                val parsed = json.readTree(data)
                require(parsed is ArrayNode) { "data read from stdin should be an array: ${String(data).take(100)}" }
                parsed.let { if (limit != null) json.createArrayNode().addAll(parsed.take(limit)) else parsed }
            }

        return parsed.iterator()
    }
}

fun Source.cached() = CachedSource(this)

private fun Source.fetchStandardizedRows(): Sequence<ObjectNode> = fetch().asSequence().map { row ->
    val standardized = when (row) {
        is ObjectNode -> row
        else -> json.createObjectNode().set("value", row)
    }

    // Generate a fake field if the row is empty as this makes Calcite buggy
    if (standardized.isEmpty) {
        standardized.put("_EMPTY_ROW", "true")
    }

    standardized
}
