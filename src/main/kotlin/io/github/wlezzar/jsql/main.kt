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
import com.github.ajalt.mordant.TermColors
import io.github.wlezzar.jsql.sql.JsonFilterableTable
import io.github.wlezzar.jsql.sql.JsonTable
import io.github.wlezzar.jsql.sql.StdinSource
import io.github.wlezzar.jsql.sql.cached
import io.github.wlezzar.jsql.sql.connection
import io.github.wlezzar.jsql.sql.json
import io.github.wlezzar.jsql.sql.sql
import io.github.wlezzar.jsql.sql.toPrettyPrintedString
import java.io.File
import kotlin.system.exitProcess

data class JSqlContext(val output: Format, val streaming: Boolean, val limit: Int?)

class JSqlCommandLine : CliktCommand(help = "JSON sql command line client") {

    private val output: Format
        by option("-o", "--output", help = "Output format (default: Table)")
            .choice(
                "json" to Format.Json,
                "table" to Format.Table
            )
            .default(Format.Table)

    private val streaming: Boolean
        by option("-s", "--streaming", help = "Expect json data line by line")
            .flag(default = false)

    private val limit: Int? by option("--take", help = "Take only a limited number of rows").int()

    override fun run() {
        currentContext.obj = JSqlContext(output, streaming, limit)
    }
}

class DescribeSchema : CliktCommand(name = "describe", help = "Describe the parsed schema") {
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

class Query : CliktCommand(name = "query", help = "Run a sql query over the json data") {
    private val ctx by requireObject<JSqlContext>()
    private val script: File?
        by option("--script", help = "Run a query from a sql file")
            .file(mustExist = true, canBeFile = true, mustBeReadable = true)


    private val sql by argument("sql", help = "SQL query").optional()

    override fun run() {
        val query = when {
            script != null -> script!!.readText(Charsets.UTF_8)
            sql != null -> sql!!
            else -> throw IllegalArgumentException("No sql to run...")
        }

        val table = JsonFilterableTable(JsonTable(StdinSource(ctx.streaming, ctx.limit).cached()))

        val response = sql(table) { executeQuery(query) }
        ctx.output.format(response) { echo(it) }
    }
}

fun jsql() = JSqlCommandLine().subcommands(
    DescribeSchema(),
    Query()
)

private fun printErr(err: Throwable) {
    System.err.println(formatError(err))
    if (System.getenv("JSQL_STACKTRACE") == "1") {
        err.printStackTrace(System.err)
    }
}

private fun formatError(err: Throwable, level: Int = 0): String = with(TermColors()) {
    val error = buildString {
        append("""${red("error:")} ${err.message}""")
        val cause = err.cause
        if (cause != null) {
            appendln()
            appendln(red("cause:"))
            append(formatError(cause, level = level + 1))
        }
    }

    error.prependIndent(indent = " ".repeat(2 * level))
}

fun main(args: Array<String>) {
    val exitCode =
        jsql()
            .runCatching { main(args) }
            .onFailure(::printErr)
            .fold(onSuccess = { 0 }, onFailure = { 1 })

    exitProcess(exitCode)
}