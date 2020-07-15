package io.github.wlezzar

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.wlezzar.jsql.sql.json
import io.github.wlezzar.jsql.sql.sql
import io.github.wlezzar.jsql.sql.toJson
import org.apache.calcite.schema.Table

fun Table.query(q: String): List<ObjectNode> = sql(this) { executeQuery(q) }

fun String.toJsonNode(): JsonNode = json.readTree(this)