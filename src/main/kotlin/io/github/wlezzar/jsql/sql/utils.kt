package io.github.wlezzar.jsql.sql

import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.calcite.avatica.util.Casing
import org.apache.calcite.config.CalciteConnectionProperty
import org.apache.calcite.jdbc.CalciteConnection
import org.apache.calcite.schema.Table
import org.apache.calcite.schema.impl.ScalarFunctionImpl
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

fun <T> connection(table: Table, name: String = "main", block: CalciteConnection.() -> T): T {
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

fun sql(table: Table, alias: String = "main", block: Statement.() -> ResultSet): List<ObjectNode> =
    connection(table, alias) {
        createStatement().use {
            it.block().use { results -> results.toJson().toList() }
        }
    }
