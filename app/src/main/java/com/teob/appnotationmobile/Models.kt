package com.teob.appnotationmobile

data class TpProject(
    var id: String = "tp-${System.currentTimeMillis()}",
    var name: String = "",
    var students: List<Student> = emptyList(),
    var criteria: List<Criterion> = emptyList(),
    var grades: MutableMap<String, MutableMap<String, Int>> = mutableMapOf(),
    var pairMode: Boolean = false,
    var pairings: MutableMap<String, String> = mutableMapOf(),
    var gridKind: String = GridKind.EP_2I2D,
    var gridTemplateBase64: String = "",
)

fun TpProject.hasContent(): Boolean {
    return name.isNotBlank() ||
        students.isNotEmpty() ||
        criteria.isNotEmpty() ||
        grades.isNotEmpty() ||
        pairings.isNotEmpty() ||
        gridTemplateBase64.isNotBlank()
}

enum class StudentFilter {
    ALL,
    TO_GRADE,
    GRADED,
}

data class Student(val id: String, val name: String)

data class Criterion(
    val id: String,
    val skill: String,
    val label: String,
    var weight: Double,
    val descriptors: Map<Int, String> = emptyMap(),
)

data class GridImport(
    val criteria: List<Criterion>,
    val kind: String,
)

object GridKind {
    const val EP_2I2D = "ep_2i2d"
    const val ETLV = "etlv"
}
