package io.github.wlezzar.jsql

import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import io.github.wlezzar.jsql.sql.JsonFilterableTable
import io.github.wlezzar.jsql.sql.JsonTable
import io.github.wlezzar.jsql.sql.StdinSource
import io.github.wlezzar.jsql.sql.cached
import io.github.wlezzar.jsql.sql.json
import io.github.wlezzar.jsql.sql.toJson
import io.github.wlezzar.jsql.sql.toPrettyPrintedString
import org.apache.calcite.avatica.util.Casing
import org.apache.calcite.config.CalciteConnectionProperty
import org.apache.calcite.jdbc.CalciteConnection
import org.apache.calcite.schema.Table
import org.apache.calcite.schema.impl.ScalarFunctionImpl
import java.io.File
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.text.SimpleDateFormat
import java.util.*
import kotlin.reflect.jvm.javaMethod

object Functions {
    @JvmStatic
    fun parseDate(date: String, format: String): java.sql.Timestamp =
        java.sql.Timestamp.from(SimpleDateFormat(format).parse(date).toInstant())
}

fun <T> connection(table: Table, name: String = "DATA", block: CalciteConnection.() -> T): T {
    val info = Properties().apply {
        setProperty(CalciteConnectionProperty.FUN.camelName(), "mysql,postgresql")
        setProperty(CalciteConnectionProperty.CASE_SENSITIVE.camelName(), "false")
        setProperty(CalciteConnectionProperty.QUOTED_CASING.camelName(), Casing.UNCHANGED.name)
        setProperty(CalciteConnectionProperty.UNQUOTED_CASING.camelName(), Casing.UNCHANGED.name)
    }

    val calcite = DriverManager
        .getConnection("jdbc:calcite:", info)
        .unwrap(CalciteConnection::class.java)
        .apply {
            rootSchema.add("PARSE_DATE", ScalarFunctionImpl.create(Functions::parseDate.javaMethod))
            rootSchema.add(name, table)
        }

    return calcite.use { it.block() }

}

fun sql(table: Table, lowerOutputKeys: Boolean, block: Statement.() -> ResultSet): List<ObjectNode> =
    connection(table) {
        createStatement().use {
            it.block().use { results -> results.toJson(lowerOutputKeys).toList() }
        }
    }

data class JSqlContext(val output: Format, val lowerKeys: Boolean, val streaming: Boolean, val limit: Int?)

class JSqlCommandLine : CliktCommand(help = "JSON sql command line client") {

    private val output: Format
        by option("-o", "--output", help = "Output format")
            .choice(
                "json" to Format.Json,
                "table" to Format.Table
            )
            .default(Format.Table)

    private val streaming: Boolean by option("-s", "--streaming").flag(default = false)
    private val limit: Int? by option("--take", help = "Take only a limited number of rows").int()

    private val lowerKeys: Boolean by option("--lower-keys").flag(default = true)

    override fun run() {
        currentContext.obj = JSqlContext(output, lowerKeys, streaming, limit)
    }
}

class DescribeSchema : CliktCommand(name = "describe", help = "Describe schema") {
    private val ctx by requireObject<JSqlContext>()

    override fun run() {
        val table = JsonFilterableTable(JsonTable(StdinSource(ctx.streaming, null)))
        val results: List<ObjectNode> = connection(table) {
            val fields = table.getRowType(typeFactory).toPrettyPrintedString()

            listOf(json.createObjectNode().put("fields", fields))
        }
        ctx.output.format(results) { echo(it) }
    }
}

class Query : CliktCommand(name = "query", help = "Describe schema") {
    private val ctx by requireObject<JSqlContext>()
    private val script: File?
        by option("--file", help = "Run a sql file")
            .file(mustExist = true, canBeFile = true, mustBeReadable = true)


    private val sql by argument("sql", help = "SQL query").optional()

    override fun run() {
        val query = when {
            script != null -> script!!.readText(Charsets.UTF_8)
            sql != null -> sql!!
            else -> throw IllegalArgumentException("No sql to run...")
        }

        val table = JsonFilterableTable(JsonTable(StdinSource(ctx.streaming, ctx.limit).cached()))

        val response = sql(table, ctx.lowerKeys) { executeQuery(query) }
        ctx.output.format(response) { echo(it) }
    }
}

fun jsql() = JSqlCommandLine()
    .subcommands(
        DescribeSchema(),
        Query()
    )

fun main(args: Array<String>) = jsql().main(args)
