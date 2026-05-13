package com.teob.appnotationmobile

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.util.Xml
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.DecimalFormat
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private var projects = mutableListOf<TpProject>()
    private var project = TpProject()
    private var selectedStudentId: String? = null
    private var studentFilter = StudentFilter.ALL
    private var isDarkMode = false
    private val ui: UiPalette
        get() = if (isDarkMode) UiTheme.dark else UiTheme.light
    private val gradeFormat = DecimalFormat("0.0")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isDarkMode = ThemePrefs.loadDarkMode(this)
        projects = ProjectStore.loadAll(this).toMutableList()
        showHome()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                REQUEST_STUDENTS -> {
                    project.students = contentResolver.openInputStream(uri).useRequired { CsvStudentParser.parse(it) }
                    project.grades = project.grades.filterKeys { id -> project.students.any { it.id == id } }.toMutableMap()
                    persistAndShowSetup("Liste élèves importée")
                }

                REQUEST_GRID -> {
                    project.criteria = contentResolver.openInputStream(uri).useRequired { XlsxGridParser.parseCriteria(it) }
                    persistAndShowSetup("Grille de notation importée")
                }
            }
        } catch (error: Exception) {
            toast("Import impossible: ${error.message ?: "fichier invalide"}")
        }
    }

    private fun showTpSetup() {
        selectedStudentId = null
        var nameInput: EditText? = null
        setContentView(
            verticalRoot {
                addView(headerRow(
                    title = if (project.name.isBlank()) "Nouveau TP" else "Reglages TP",
                    leading = {
                    addView(homeButton {
                        project.name = nameInput?.text?.toString()?.trim().orEmpty()
                        saveCurrentProject()
                        showHome()
                    })
                    },
                    trailing = {
                        addView(themeToggleButton {
                            project.name = nameInput?.text?.toString()?.trim().orEmpty()
                            saveCurrentProject()
                            showTpSetup()
                        })
                    },
                ))
                addView(spacer(18))
                addView(label("Nom du TP"))
                nameInput = EditText(this@MainActivity).apply {
                    setSingleLine(true)
                    hint = "Ex: TP Acquisition capteur"
                    setText(project.name)
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    styleInput()
                }
                addView(nameInput, matchWrap())

                addView(infoLine("Eleves", "${project.students.size} importes"))
                addView(actionButton("Importer liste d'eleves CSV") {
                    project.name = nameInput?.text?.toString()?.trim().orEmpty()
                    pickFile(REQUEST_STUDENTS, "text/*")
                })

                addView(spacer(12))
                addView(infoLine("Grille", "${project.criteria.size} criteres"))
                addView(actionButton("Importer feuille de notation XLSX") {
                    project.name = nameInput?.text?.toString()?.trim().orEmpty()
                    pickFile(REQUEST_GRID, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                })
                if (project.criteria.isNotEmpty()) {
                    addView(spacer(8))
                    addView(actionButton("Modifier les ponderations") {
                        project.name = nameInput?.text?.toString()?.trim().orEmpty()
                        saveCurrentProject()
                        showWeightSettings()
                    })
                }

                addView(spacer(16))
                addView(actionButton("Ouvrir le TP") {
                    project.name = nameInput?.text?.toString()?.trim().orEmpty()
                    when {
                        project.name.isBlank() -> toast("Donne d'abord un nom au TP.")
                        project.students.isEmpty() -> toast("Importe ou charge une liste d'eleves.")
                        project.criteria.isEmpty() -> toast("Importe ou charge une grille de notation.")
                        else -> {
                            saveCurrentProject()
                            showStudentList()
                        }
                    }
                })

                if (projects.any { it.id == project.id }) {
                    addView(spacer(12))
                    addView(actionButton("Supprimer ce TP") {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Supprimer ce TP ?")
                            .setMessage("Le TP et ses notes locales seront supprimes de la liste.")
                            .setPositiveButton("Supprimer") { _, _ ->
                                ProjectStore.delete(this@MainActivity, project.id)
                                projects = ProjectStore.loadAll(this@MainActivity).toMutableList()
                                project = TpProject()
                                showHome()
                            }
                            .setNegativeButton("Annuler", null)
                            .show()
                    })
                }
            },
        )
    }

    private fun showHome() {
        selectedStudentId = null
        projects = ProjectStore.loadAll(this).toMutableList()
        setContentView(
            verticalRoot {
                addView(homeHeader())
                addView(spacer(18))
                addView(title("Vos TP"))
                if (projects.isEmpty()) {
                    addView(TextView(this@MainActivity).apply {
                        text = "Aucun TP pour le moment."
                        textSize = 16f
                        setTextColor(ui.muted)
                        setPadding(0, 0, 0, dp(12))
                    })
                }
                projects.forEach { existingProject ->
                    addView(projectRow(existingProject))
                }
                addView(spacer(10))
                addView(newProjectButton())
                addView(spacer(28))
                addView(homeLogo())
            },
        )
    }

    private fun homeHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = headerSurface()
            setPadding(dp(18), dp(16), dp(12), dp(16))

            addView(TextView(this@MainActivity).apply {
                text = "Notéa"
                textSize = 24f
                setTextColor(ui.headerText)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(themeToggleButton { showHome() })
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun homeLogo(): View {
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(8))
            runCatching {
                assets.open("logo.png").use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.let { bitmap ->
                addView(ImageView(this@MainActivity).apply {
                    setImageBitmap(bitmap)
                    adjustViewBounds = true
                    alpha = if (isDarkMode) 0.88f else 0.78f
                }, LinearLayout.LayoutParams(dp(132), ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
    }

    private fun projectRow(existingProject: TpProject): View {
        return Button(this).apply {
            val subtitle = "${existingProject.students.size} eleves · Moyenne ${averageLabel(existingProject)}"
            text = "${existingProject.name.ifBlank { "TP sans nom" }}\n$subtitle"
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setAllCaps(false)
            setTextColor(ui.text)
            background = cardSurface()
            stateListAnimator = null
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setOnClickListener {
                project = existingProject
                if (project.students.isEmpty() || project.criteria.isEmpty()) showTpSetup() else showStudentList()
            }
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dp(8)) }
        }
    }

    private fun newProjectButton(): View {
        return Button(this).apply {
            text = "Nouveau TP"
            textSize = 18f
            setAllCaps(false)
            setTextColor(ui.iconOnAccent)
            background = accentSurface()
            stateListAnimator = null
            setPadding(dp(14), dp(16), dp(14), dp(16))
            setOnClickListener {
                project = TpProject()
                showTpSetup()
            }
        }
    }

    private fun showWeightSettings() {
        val inputs = mutableMapOf<String, EditText>()
        setContentView(
            verticalRoot {
                addView(headerRow(
                    title = "Ponderations",
                    leading = {
                    addView(backButton {
                        saveWeights(inputs)
                        showTpSetup()
                    })
                    },
                    trailing = {
                        addView(themeToggleButton { showWeightSettings() })
                    },
                ))

                addView(TextView(this@MainActivity).apply {
                    text = "Ces valeurs sont utilisees pour calculer les notes et la moyenne du TP."
                    textSize = 15f
                    setTextColor(ui.muted)
                    setPadding(dp(2), dp(10), dp(2), dp(16))
                })

                groupedCriteria().forEach { (skill, criteria) ->
                    addView(skillHeader(skill))
                    criteria.forEach { criterion ->
                        addView(weightEditor(criterion, inputs))
                    }
                }

                addView(actionButton("Enregistrer") {
                    saveWeights(inputs)
                    toast("Ponderations enregistrees")
                    showTpSetup()
                })
            },
        )
    }

    private fun weightEditor(criterion: Criterion, inputs: MutableMap<String, EditText>): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardSurface()
            setPadding(dp(16), dp(14), dp(16), dp(16))
            addView(TextView(this@MainActivity).apply {
                text = criterion.skill
                textSize = 13f
                setTextColor(ui.primary)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = criterion.label
                textSize = 15f
                setTextColor(ui.text)
                setPadding(0, dp(3), 0, dp(6))
            })
            val input = EditText(this@MainActivity).apply {
                setSingleLine(true)
                hint = "Poids"
                setText(weightLabel(criterion.weight))
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                styleInput()
            }
            inputs[criterion.id] = input
            addView(input, matchWrap())
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dp(10)) }
        }
    }

    private fun saveWeights(inputs: Map<String, EditText>) {
        val parsedWeights = inputs.mapValues { (_, input) ->
            input.text.toString().replace(',', '.').toDoubleOrNull()
        }
        val invalid = parsedWeights.values.any { it == null || it < 0.0 }
        if (invalid) {
            toast("Certaines ponderations invalides ont ete ignorees.")
        }
        project.criteria.forEach { criterion ->
            val weight = parsedWeights[criterion.id]
            if (weight != null && weight >= 0.0) criterion.weight = weight
        }
        saveCurrentProject()
    }

    private fun showStudentList() {
        selectedStudentId = null
        setContentView(
            verticalRoot {
                addView(headerRow(
                    title = project.name,
                    leading = {
                    addView(homeButton {
                        saveCurrentProject()
                        showHome()
                    })
                    },
                    trailing = {
                        addView(themeToggleButton { showStudentList() })
                    addView(gearButton { showTpSetup() })
                    },
                ))

                addView(TextView(this@MainActivity).apply {
                    text = "Moyenne classe: ${averageLabel()}   |   ${gradedCount()} / ${project.students.size} notes"
                    textSize = 16f
                    setTextColor(ui.muted)
                    background = quietSurface()
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                }, matchWrap().apply {
                    setMargins(0, dp(10), 0, dp(12))
                })

                if (project.students.size >= 5) {
                    addView(filterControl())
                    addView(spacer(10))
                }

                filteredStudents().forEach { student ->
                    addView(studentRow(student))
                }
            },
        )
    }

    private fun filteredStudents(): List<Student> {
        return when (studentFilter) {
            StudentFilter.ALL -> project.students
            StudentFilter.TO_GRADE -> project.students.filter { computeScore(project.grades[it.id] ?: emptyMap()) == null }
            StudentFilter.GRADED -> project.students.filter { computeScore(project.grades[it.id] ?: emptyMap()) != null }
        }
    }

    private fun filterControl(): View {
        return row {
            listOf(
                StudentFilter.ALL to "Tous",
                StudentFilter.TO_GRADE to "A noter",
                StudentFilter.GRADED to "Notes",
            ).forEach { (filter, label) ->
                addView(segmentButton(label, studentFilter == filter) {
                    studentFilter = filter
                    showStudentList()
                })
            }
        }
    }

    private fun showStudentGrade(student: Student) {
        selectedStudentId = student.id
        val grades = project.grades.getOrPut(student.id) { mutableMapOf() }
        setContentView(
            verticalRoot {
                addView(headerRow(
                    title = student.name,
                    leading = {
                    addView(backButton {
                        saveCurrentProject()
                        showStudentList()
                    })
                    },
                    trailing = {
                        addView(themeToggleButton { showStudentGrade(student) })
                    },
                ))

                val scoreText = TextView(this@MainActivity).apply {
                    text = "Note: ${scoreLabel(student.id)}"
                    textSize = 22f
                    setTextColor(scoreColor(student.id))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    background = cardSurface()
                    setPadding(dp(14), dp(16), dp(14), dp(16))
                }
                addView(scoreText, matchWrap().apply {
                    setMargins(0, dp(12), 0, dp(12))
                })

                groupedCriteria().forEach { (skill, criteria) ->
                    addView(skillHeader(skill))
                    criteria.forEach { criterion ->
                        addView(criterionEditor(criterion, grades, scoreText))
                    }
                }
            },
        )
    }

    private fun criterionEditor(
        criterion: Criterion,
        grades: MutableMap<String, Int>,
        scoreText: TextView,
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardSurface()
            setPadding(dp(16), dp(14), dp(16), dp(16))
            addView(TextView(this@MainActivity).apply {
                text = "${criterion.label}\nPondération: ${weightLabel(criterion.weight)}"
                textSize = 16f
                setTextColor(ui.text)
                setPadding(0, dp(3), 0, dp(8))
            })
            addView(row {
                val options = listOf(-1 to "NE", 0 to "0", 1 to "1", 2 to "2", 3 to "3")
                options.forEach { (value, text) ->
                    addView(scoreButton(text, grades[criterion.id] == value) {
                        grades[criterion.id] = value
                        saveCurrentProject()
                        scoreText.text = "Note: ${scoreLabel(selectedStudentId)}"
                        scoreText.setTextColor(scoreColor(selectedStudentId))
                        showStudentGrade(project.students.first { it.id == selectedStudentId })
                    })
                }
            })
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dp(10)) }
        }
    }

    private fun studentRow(student: Student): View {
        return Button(this).apply {
            text = "${student.name}\n${scoreLabel(student.id)}"
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setAllCaps(false)
            setTextColor(ui.text)
            background = cardSurface()
            stateListAnimator = null
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { showStudentGrade(student) }
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dp(8)) }
        }
    }

    private fun pickFile(requestCode: Int, mimeType: String) {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(mimeType, "text/comma-separated-values", "*/*"))
            },
            requestCode,
        )
    }

    private fun persistAndShowSetup(message: String) {
        saveCurrentProject()
        toast(message)
        showTpSetup()
    }

    private fun saveCurrentProject() {
        ProjectStore.save(this, project)
        projects = ProjectStore.loadAll(this).toMutableList()
    }

    private fun scoreLabel(studentId: String?): String {
        val score = studentId?.let { computeScore(project.grades[it] ?: emptyMap()) }
        return score?.let { "${gradeFormat.format(it)} / 20" } ?: "A noter"
    }

    private fun scoreColor(studentId: String?): Int {
        val score = studentId?.let { computeScore(project.grades[it] ?: emptyMap()) } ?: return ui.muted
        return when {
            score < 8.0 -> ui.danger
            score < 13.0 -> ui.warning
            else -> ui.success
        }
    }

    private fun averageLabel(): String {
        return averageLabel(project)
    }

    private fun averageLabel(targetProject: TpProject): String {
        val scores = targetProject.students.mapNotNull { computeScore(targetProject, targetProject.grades[it.id] ?: emptyMap()) }
        if (scores.isEmpty()) return "-- / 20"
        return "${gradeFormat.format(scores.average())} / 20"
    }

    private fun gradedCount(): Int = project.students.count { computeScore(project.grades[it.id] ?: emptyMap()) != null }

    private fun computeScore(grades: Map<String, Int>): Double? {
        return computeScore(project, grades)
    }

    private fun computeScore(targetProject: TpProject, grades: Map<String, Int>): Double? {
        if (targetProject.criteria.isEmpty()) return null
        val evaluated = targetProject.criteria.mapNotNull { criterion ->
            val level = grades[criterion.id] ?: return@mapNotNull null
            if (level < 0) null else criterion to level
        }
        if (evaluated.isEmpty()) return null
        val missing = targetProject.criteria.any { criterion -> !grades.containsKey(criterion.id) }
        if (missing) return null
        val totalWeight = evaluated.sumOf { it.first.weight }
        if (totalWeight <= 0.0) return null
        return evaluated.sumOf { (criterion, level) -> (level / 3.0) * criterion.weight } / totalWeight * 20.0
    }

    private fun groupedCriteria(): List<Pair<String, List<Criterion>>> {
        return project.criteria
            .groupBy { it.skill }
            .map { (skill, criteria) -> skill to criteria }
    }

    private fun weightLabel(weight: Double): String {
        return if (weight % 1.0 == 0.0) weight.roundToInt().toString() else gradeFormat.format(weight)
    }

    private fun verticalRoot(content: LinearLayout.() -> Unit): ScrollView {
        return ScrollView(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(ui.backgroundTop, ui.backgroundMid, ui.backgroundBottom),
            )
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(18), dp(18), dp(24))
                content()
            })
        }
    }

    private fun row(content: LinearLayout.() -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            content()
        }
    }

    private fun headerRow(
        title: String,
        leading: LinearLayout.() -> Unit = {},
        trailing: LinearLayout.() -> Unit = {},
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val leadingGroup = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                leading()
            }
            addView(leadingGroup, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 20f
                setTextColor(ui.text)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                maxLines = 1
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val trailingGroup = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                trailing()
            }
            addView(trailingGroup, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            post {
                leadingGroup.layoutParams = leadingGroup.layoutParams.apply { width = leadingGroup.width }
                trailingGroup.layoutParams = trailingGroup.layoutParams.apply { width = trailingGroup.width }
            }
        }
    }

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 28f
        setTextColor(ui.text)
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dp(16))
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(ui.muted)
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dp(6))
    }

    private fun infoLine(label: String, value: String) = TextView(this).apply {
        text = "$label: $value"
        textSize = 15f
        setTextColor(ui.muted)
        setPadding(dp(2), dp(16), dp(2), dp(6))
    }

    private fun skillHeader(text: String) = TextView(this).apply {
        this.text = text
        textSize = 19f
        setTextColor(ui.primary)
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(dp(2), dp(18), dp(2), dp(8))
    }

    private fun actionButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 16f
        setAllCaps(false)
        setTextColor(ui.iconOnAccent)
        background = accentSurface()
        stateListAnimator = null
        minHeight = dp(50)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setOnClickListener { onClick() }
    }

    private fun smallButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 13f
        setAllCaps(false)
        setTextColor(ui.iconOnAccent)
        background = accentSurface()
        stateListAnimator = null
        setOnClickListener { onClick() }
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, dp(10), 0) }
    }

    private fun themeToggleButton(onThemeChanged: () -> Unit): View {
        return ImageButton(this).apply {
            background = quietSurface()
            setImageResource(if (isDarkMode) R.drawable.ic_sun else R.drawable.ic_moon)
            setColorFilter(ui.muted)
            contentDescription = if (isDarkMode) "Mode clair" else "Mode sombre"
            setOnClickListener {
                isDarkMode = !isDarkMode
                ThemePrefs.saveDarkMode(this@MainActivity, isDarkMode)
                onThemeChanged()
            }
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                setMargins(dp(8), 0, 0, 0)
            }
        }
    }

    private fun gearButton(onClick: () -> Unit) = ImageButton(this).apply {
        background = quietSurface()
        setImageResource(R.drawable.ic_settings)
        setColorFilter(ui.primary)
        contentDescription = "Reglages"
        setOnClickListener { onClick() }
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            setMargins(dp(10), 0, 0, 0)
        }
    }

    private fun backButton(onClick: () -> Unit) = ImageButton(this).apply {
        background = quietSurface()
        setImageResource(R.drawable.ic_back)
        setColorFilter(ui.primary)
        contentDescription = "Retour"
        setOnClickListener { onClick() }
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            setMargins(0, 0, dp(10), 0)
        }
    }

    private fun homeButton(onClick: () -> Unit) = ImageButton(this).apply {
        background = quietSurface()
        setImageResource(R.drawable.ic_home)
        setColorFilter(ui.primary)
        contentDescription = "Accueil"
        setOnClickListener { onClick() }
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            setMargins(0, 0, dp(10), 0)
        }
    }

    private fun scoreButton(text: String, selected: Boolean, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 14f
        setAllCaps(false)
        setTextColor(if (selected) ui.iconOnAccent else ui.text)
        background = if (selected) accentSurface() else glassSurface()
        stateListAnimator = null
        setOnClickListener { onClick() }
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            setMargins(dp(2), 0, dp(2), 0)
        }
    }

    private fun segmentButton(text: String, selected: Boolean, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 14f
        setAllCaps(false)
        setTextColor(if (selected) ui.iconOnAccent else ui.text)
        background = if (selected) accentSurface() else glassSurface()
        stateListAnimator = null
        setOnClickListener { onClick() }
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            setMargins(dp(3), 0, dp(3), 0)
        }
    }

    private fun spacer(heightDp: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun EditText.styleInput() {
        textSize = 16f
        setTextColor(ui.text)
        setHintTextColor(ui.muted)
        background = quietSurface()
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun cardSurface(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(ui.surface)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), ui.glassStroke)
        }
    }

    private fun quietSurface(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(ui.surfaceAlt)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), ui.glassStroke)
        }
    }

    private fun glassSurface(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(ui.glassTop)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), ui.glassStroke)
        }
    }

    private fun accentSurface(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(ui.primary)
            cornerRadius = dp(14).toFloat()
        }
    }

    private fun headerSurface(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(ui.header)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), ui.glassStroke)
        }
    }

    private fun headerChipSurface(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.argb(if (isDarkMode) 34 else 26, 255, 255, 255))
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), Color.argb(if (isDarkMode) 58 else 42, 255, 255, 255))
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
    ).roundToInt()

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQUEST_STUDENTS = 10
        private const val REQUEST_GRID = 11
    }
}

data class TpProject(
    var id: String = "tp-${System.currentTimeMillis()}",
    var name: String = "",
    var students: List<Student> = emptyList(),
    var criteria: List<Criterion> = emptyList(),
    var grades: MutableMap<String, MutableMap<String, Int>> = mutableMapOf(),
)

private fun TpProject.hasContent(): Boolean {
    return name.isNotBlank() || students.isNotEmpty() || criteria.isNotEmpty() || grades.isNotEmpty()
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
)

object CsvStudentParser {
    fun parse(input: InputStream): List<Student> {
        return input.bufferedReader(Charsets.UTF_8)
            .readLines()
            .map { it.trim().trim('"').trim() }
            .filter { it.isNotBlank() && !it.equals("Nom", ignoreCase = true) && !it.contains("Spé") }
            .mapIndexed { index, name -> Student("student-$index-${name.lowercase(Locale.ROOT).hashCode()}", name) }
    }
}

object XlsxGridParser {
    fun parseCriteria(input: InputStream): List<Criterion> {
        val entries = readZipEntries(input)
        val sharedStrings = parseSharedStrings(entries["xl/sharedStrings.xml"] ?: ByteArray(0))
        val sheet = entries["xl/worksheets/sheet2.xml"]
            ?: throw IllegalArgumentException("Feuille Grille introuvable")
        val cells = parseCells(sheet, sharedStrings)
        return (8..21).mapNotNull { row ->
            val label = cells["D$row"]?.trim().orEmpty()
            if (label.isBlank()) return@mapNotNull null
            val skill = cells["B$row"]?.trim().takeUnless { it.isNullOrBlank() }
                ?: when (row) {
                    in 8..9 -> "Analyser"
                    in 10..13 -> "Concevoir"
                    in 14..17 -> "Simuler"
                    else -> "Experimenter"
                }
            Criterion(
                id = "criterion-$row",
                skill = skill,
                label = label,
                weight = cells["M$row"]?.toDoubleOrNull() ?: 1.0,
            )
        }
    }

    private fun readZipEntries(input: InputStream): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = ByteArrayOutputStream()
                zip.copyTo(output)
                entries[entry.name] = output.toByteArray()
            }
        }
        return entries
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        if (bytes.isEmpty()) return emptyList()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        val strings = mutableListOf<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "si") {
                strings += readSharedString(parser)
            }
            event = parser.next()
        }
        return strings
    }

    private fun readSharedString(parser: XmlPullParser): String {
        val builder = StringBuilder()
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "t") builder.append(parser.nextText())
                }

                XmlPullParser.END_TAG -> if (parser.name == "si") return builder.toString()
            }
        }
    }

    private fun parseCells(bytes: ByteArray, sharedStrings: List<String>): Map<String, String> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        val cells = mutableMapOf<String, String>()
        var ref = ""
        var type = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "c") {
                ref = parser.getAttributeValue(null, "r").orEmpty()
                type = parser.getAttributeValue(null, "t").orEmpty()
            }
            if (event == XmlPullParser.START_TAG && parser.name == "v" && ref.isNotBlank()) {
                val raw = parser.nextText()
                cells[ref] = if (type == "s") sharedStrings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
            }
            event = parser.next()
        }
        return cells
    }
}

object ProjectStore {
    private const val PREFS = "tp-project"
    private const val KEY_PROJECTS = "projects"
    private const val KEY = "data"

    fun loadAll(activity: Activity): List<TpProject> {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        val rawProjects = prefs.getString(KEY_PROJECTS, null)
        if (rawProjects != null) {
            return runCatching {
                val array = JSONArray(rawProjects)
                (0 until array.length()).map { parseProject(array.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }

        val legacy = prefs.getString(KEY, null)
            ?.let { raw -> runCatching { parseProject(JSONObject(raw)) }.getOrNull() }
        return if (legacy != null && legacy.hasContent()) {
            saveAll(activity, listOf(legacy))
            listOf(legacy)
        } else {
            emptyList()
        }
    }

    fun save(activity: Activity, project: TpProject) {
        if (!project.hasContent()) return
        val projects = loadAll(activity).toMutableList()
        val index = projects.indexOfFirst { it.id == project.id }
        if (index >= 0) {
            projects[index] = project
        } else {
            projects += project
        }
        saveAll(activity, projects)
    }

    fun delete(activity: Activity, projectId: String) {
        saveAll(activity, loadAll(activity).filterNot { it.id == projectId })
    }

    private fun saveAll(activity: Activity, projects: List<TpProject>) {
        val array = JSONArray(projects.map { it.toJson() })
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROJECTS, array.toString())
            .apply()
    }

    private fun parseProject(json: JSONObject): TpProject {
        return TpProject(
            id = json.optString("id").ifBlank { "tp-${System.currentTimeMillis()}" },
            name = json.optString("name"),
            students = json.optJSONArray("students").toList { Student(getString("id"), getString("name")) },
            criteria = json.optJSONArray("criteria").toList {
                Criterion(getString("id"), getString("skill"), getString("label"), getDouble("weight"))
            },
            grades = json.optJSONObject("grades").toGradeMap(),
        )
    }

    private fun TpProject.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("students", JSONArray(students.map { JSONObject().put("id", it.id).put("name", it.name) }))
            .put(
                "criteria",
                JSONArray(criteria.map {
                    JSONObject()
                        .put("id", it.id)
                        .put("skill", it.skill)
                        .put("label", it.label)
                        .put("weight", it.weight)
                }),
            )
            .put("grades", JSONObject(grades.mapValues { JSONObject(it.value as Map<*, *>) }))
    }

    private fun TpProject.hasContent(): Boolean {
        return name.isNotBlank() || students.isNotEmpty() || criteria.isNotEmpty() || grades.isNotEmpty()
    }

    private fun JSONObject?.toGradeMap(): MutableMap<String, MutableMap<String, Int>> {
        val result = mutableMapOf<String, MutableMap<String, Int>>()
        if (this == null) return result
        keys().forEach { studentId ->
            val gradeObject = getJSONObject(studentId)
            result[studentId] = mutableMapOf<String, Int>().apply {
                gradeObject.keys().forEach { criterionId -> put(criterionId, gradeObject.getInt(criterionId)) }
            }
        }
        return result
    }
}

private inline fun <T> JSONArray?.toList(build: JSONObject.() -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).map { getJSONObject(it).build() }
}

private inline fun <T> InputStream?.useRequired(block: (InputStream) -> T): T {
    val stream = this ?: throw IllegalArgumentException("fichier illisible")
    return stream.use(block)
}
