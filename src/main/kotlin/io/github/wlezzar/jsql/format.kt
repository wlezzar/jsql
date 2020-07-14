package io.github.wlezzar.jsql

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.jakewharton.picnic.BorderStyle
import com.jakewharton.picnic.Table
import com.jakewharton.picnic.TextAlignment
import com.jakewharton.picnic.table
import io.github.wlezzar.jsql.sql.json

fun toPrettyPrintedTableRow(element: Any, level: Int): String = when {
    element is ObjectNode ->
        element
            .fields()
            .asSequence()
            .map { (key, value) -> "$key: $value" }
            .joinToString(separator = if (level <= 1) "\n" else "  ")

    element is ArrayNode -> when {
        level <= 1 -> element
            .asSequence()
            .map { toPrettyPrintedTableRow(it, level = level.inc()) }
            .joinToString(separator = "\n")
        else -> element.toString()
    }

    element is JsonNode && element.isTextual -> element.textValue()

    else -> element.toString()
}

fun JsonNode.toPrettyPrintedTable(level: Int = 0): String = toTable(
    this,
    level
).toString()

private fun toTable(input: JsonNode, level: Int = 0): Table = table {
    style {
        borderStyle = BorderStyle.Solid
    }

    cellStyle {
        border = true
        alignment = TextAlignment.MiddleLeft
        paddingRight = 1
        paddingLeft = 1
    }

    when (input) {
        is ObjectNode -> {
            val fields = input.fieldNames().asSequence().toList()

            header {
                row(*fields.toTypedArray())
            }
            row {
                fields.forEach {
                    cell(
                        toPrettyPrintedTableRow(
                            input[it],
                            level.inc()
                        )
                    )
                }
            }
        }

        is ArrayNode -> when {
            input.none() -> {
                /* do nothing */
            }

            input.first() is ObjectNode -> {
                val fields = input.first().fieldNames().asSequence().toList()
                header {
                    row(*fields.toTypedArray())
                }
                input.forEach { element ->
                    row {
                        fields.forEach { fieldName ->
                            cell(
                                toPrettyPrintedTableRow(
                                    element[fieldName],
                                    level.inc()
                                )
                            )
                        }
                    }
                }
            }

            else -> {
                // pretty print array of strings
                header { row("value") }
                input.forEach {
                    row(it.toString())
                }
            }
        }

        else -> {
            header { row("value") }
            row(input.toString())
        }
    }
}

private fun Any.toJsonString(): String = json.writeValueAsString(this)

enum class Format {
    Json, Raw, Table;

    private fun format(content: JsonNode, foreach: (String) -> Unit) = when (this) {
        Raw -> foreach(content.toJsonString())
        Json -> foreach(content.toJsonString())
        Table -> foreach(content.toPrettyPrintedTable())
    }

    fun format(records: Iterable<JsonNode>, foreach: (String) -> Unit) = when (this) {
        Raw -> records.forEach { it.toJsonString() }
        else -> {
            val collected = json.valueToTree<JsonNode>(records.toList())
            format(collected, foreach)
        }
    }
}
