package io.github.wlezzar.jsql.sql

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import org.apache.calcite.rel.type.RelDataType
import org.apache.calcite.rel.type.RelDataTypeFactory
import org.apache.calcite.rel.type.RelRecordType
import org.apache.calcite.sql.type.ArraySqlType
import org.apache.calcite.sql.type.BasicSqlType
import org.apache.calcite.sql.type.SqlTypeName
import java.sql.ResultSet
import java.util.*

/**
 * Transforms a [ResultSet] row into a [JsonNode].
 */
fun resultSetItemToJsonNode(item: Any?): JsonNode = when (item) {
    null -> NullNode.instance

    is java.sql.Struct -> json.createArrayNode().apply {
        item.attributes.forEach { add(resultSetItemToJsonNode(it)) }
    }

    is java.sql.Array -> json.createArrayNode().apply {
        when (val array = item.array) {
            is Array<*> -> array.forEach { add(resultSetItemToJsonNode(it)) }
            is DoubleArray -> array.forEach { add(resultSetItemToJsonNode(it)) }
            is IntArray -> array.forEach { add(resultSetItemToJsonNode(it)) }
            is LongArray -> array.forEach { add(resultSetItemToJsonNode(it)) }
            is ShortArray -> array.forEach { add(resultSetItemToJsonNode(it)) }
            is ByteArray -> array.forEach { add(resultSetItemToJsonNode(it)) }
            is FloatArray -> array.forEach { add(resultSetItemToJsonNode(it)) }
            is BooleanArray -> array.forEach { add(resultSetItemToJsonNode(it)) }
            is CharArray -> array.forEach { add(resultSetItemToJsonNode(it)) }
            else -> throw IllegalArgumentException("java.sql.Array is supposed to return a java array")
        }
    }

    is Number -> DoubleNode(item.toDouble())

    else -> TextNode(item.toString())
}

fun ResultSet.toJson(): Sequence<ObjectNode> = sequence {
    while (next()) {
        val row = json.createObjectNode().apply {
            for (i in 1..metaData.columnCount) {
                set<JsonNode>(
                    metaData.getColumnLabel(i),
                    resultSetItemToJsonNode(getObject(i))
                )
            }
        }
        yield(row)
    }
}

fun JsonNode.getFieldCaseInsensitive(name: String) =
    fields().asSequence().find { it.key.toLowerCase() == name.toLowerCase() }?.value

/**
 * Converts a json field into a sql field.
 */
fun convertJsonElement(row: JsonNode, type: RelDataType, failOnError: Boolean): Any? = when (type) {
    is RelRecordType -> type.fieldList
        .asSequence()
        .map { field ->
            if (field.name.toUpperCase() == "_ROW_UUID") "${UUID.randomUUID()}"
            else row.getFieldCaseInsensitive(field.name)?.let { convertJsonElement(it, field.type, failOnError) }
        }
        .toList()
        .toTypedArray()

    is ArraySqlType ->
        when {
            row is ArrayNode -> row.asSequence()
                .map { convertJsonElement(it, type.componentType, failOnError) }
                .toList()

            failOnError -> throw IllegalArgumentException("expecting an array element ($type) but got : $row")
            else -> null
        }

    is BasicSqlType -> when (type.sqlTypeName) {
        SqlTypeName.VARCHAR -> when {
            row.isTextual -> row.textValue()
            failOnError -> throw IllegalArgumentException("expecting an string element ($type) but got : $row")
            else -> null
        }
        SqlTypeName.BOOLEAN -> when {
            row.isBoolean -> row.booleanValue()
            failOnError -> throw IllegalArgumentException("expecting a boolean element ($type) but got : $row")
            else -> null
        }
        SqlTypeName.DOUBLE -> when {
            row.isNumber -> row.numberValue().toDouble()
            failOnError -> throw IllegalArgumentException("expecting a number element ($type) but got : $row")
            else -> null
        }
        SqlTypeName.NULL -> row.toString()
        else -> throw IllegalArgumentException("unexpected sql type name : ${type.sqlTypeName}")
    }

    else -> throw IllegalArgumentException("unexpected sql type : $type")
}

/**
 * Converts a json row into a sql row.
 */
fun convertJsonRow(row: JsonNode, type: RelDataType, failOnError: Boolean): Array<out Any?> = when {
    row is ObjectNode -> convertJsonElement(row, type, failOnError) as Array<*>
    else ->
        type.getField("VALUE", false, false)
            ?.type
            ?.let { arrayOf(convertJsonElement(row, it, failOnError)) }
            ?: throw IllegalArgumentException("expected field 'VALUE' in : $type (encountered non object row : $row)")
}

/**
 * Deduce the SQL field type of a json field.
 */
fun RelDataTypeFactory.deduceElementType(row: JsonNode, level: Int): RelDataType = when {
    row.isNull -> createUnknownType()
    row.isTextual -> nullable(createSqlType(SqlTypeName.VARCHAR))
    row.isBoolean -> nullable(createSqlType(SqlTypeName.BOOLEAN))
    row.isNumber -> nullable(createSqlType(SqlTypeName.DOUBLE))
    row is ObjectNode -> when {
        else -> row.fields()
            .asSequence()
            .map { (k, v) -> /*k.toUpperCase()*/ k to deduceElementType(v, level + 1) }
            .fold(builder()) { builder, (name, type) -> builder.add(name, type) }
            .safeNullable(true)
            .build()
    }
    row is ArrayNode -> nullable(
        createArrayType(
            row.map { deduceElementType(it, level + 1) }.firstOrNull() ?: createUnknownType(),
            -1
        )
    )
    else -> throw IllegalArgumentException("unexpected case : $row")
}

/**
 * Deduce the SQL row type of a json row.
 */
fun RelDataTypeFactory.deduceRowType(data: List<JsonNode>): RelDataType = when {
    data.isEmpty() -> {
        builder().add("EMPTY_TABLE", createSqlType(SqlTypeName.BOOLEAN)).build()
    }
    else -> data
        .asSequence()
        .map { row ->
            builder()
                .apply {
                    add("_ROW_UUID", createSqlType(SqlTypeName.VARCHAR))
                    when (row) {
                        is ObjectNode -> for (field in deduceElementType(row, level = 1).fieldList) {
                            add(field)
                        }
                        else -> add("VALUE", deduceElementType(row, level = 1))
                    }
                }
                .build()
        }
        .reduce(this::merge)
}

fun intersection(left: Set<String>, right: Set<String>): List<String> = left.filter { it in right }

/**
 * Creates a SQL type that is a union of two SQL types.
 */
fun RelDataTypeFactory.merge(left: RelDataType, right: RelDataType): RelDataType = when {
    left.sqlTypeName == SqlTypeName.NULL -> right
    right.sqlTypeName == SqlTypeName.NULL -> left
    left.sqlTypeName != right.sqlTypeName -> throw IllegalArgumentException("cannot merge types : '$left' with '$right'")
    left.isStruct -> {
        val (leftNames, rightNames) = left.fieldNames.toSet() to right.fieldNames.toSet()
        val inBoth = intersection(leftNames, rightNames)
        val inLeftOnly = leftNames - rightNames
        val inRightOnly = rightNames - leftNames

        builder()
            .apply {
                inBoth.forEach {
                    add(it, merge(left.getField(it, false, false).type, right.getField(it, false, false).type))
                }
                inLeftOnly.forEach { add(it, left.getField(it, false, false).type) }
                inRightOnly.forEach { add(it, right.getField(it, false, false).type) }
            }
            .safeNullable(true)
            .build()
    }
    left is ArraySqlType -> nullable(createArrayType(merge(left.componentType, right.componentType), -1))
    else -> left
}

/**
 * Replaces a null type with a [replacement] type.
 *
 * This is useful when inferring the type of some json data where a field is always null in the analyzed sample. In
 * this case, the inferred type is [SqlTypeName.NULL] and this makes the field unusable. By replacing this type with an
 * alternate one (ex. [SqlTypeName.VARCHAR]), this makes the field usable again (possibly in the wrong type though).
 */
fun RelDataTypeFactory.replaceNullTypes(type: RelDataType, replacement: RelDataType): RelDataType = when {
    type.sqlTypeName == SqlTypeName.NULL -> replacement

    type.isStruct -> RelDataTypeFactory.Builder(this)
        .apply { type.fieldList.forEach { add(it.name, replaceNullTypes(it.type, replacement)) } }
        .safeNullable(true)
        .build()

    type is ArraySqlType -> createTypeWithNullability(
        createArrayType(replaceNullTypes(type.componentType, replacement), -1),
        true
    )

    else -> type
}

fun RelDataTypeFactory.nullable(type: RelDataType): RelDataType =
    if (type.isNullable) type else createTypeWithNullability(type, true)

fun RelDataTypeFactory.Builder.safeNullable(boolean: Boolean): RelDataTypeFactory.Builder =
    if (fieldCount > 0) nullable(boolean) else this

fun RelDataType.toPrettyPrintedString(indent: Int = 0, indentString: String = "    "): String = when {
    isStruct -> buildString {
        appendln("Record")
        for (field in fieldList) {
            val typeRepr = field.type.toPrettyPrintedString(indent + 1, indentString)
            appendln("${indentString.repeat(indent)} - ${field.name} : $typeRepr")
        }
    }

    this is ArraySqlType -> "Array of ${componentType.toPrettyPrintedString(indent, indentString)}"

    else -> toString().trim()
}
