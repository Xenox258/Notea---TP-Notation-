package com.teob.appnotationmobile

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.util.TypedValue
import android.util.Xml
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
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
import java.io.OutputStream
import java.text.DecimalFormat
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private var projects = mutableListOf<TpProject>()
    private var project = TpProject()
    private var selectedStudentId: String? = null
    private var pairEditMode = false
    private var pendingStudentListScrollY: Int? = null
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
            restoreProjectPendingImport()
            when (requestCode) {
                REQUEST_STUDENTS -> {
                    project.students = contentResolver.openInputStream(uri).useRequired { CsvStudentParser.parse(it) }
                    project.grades = project.grades.filterKeys { id -> project.students.any { it.id == id } }.toMutableMap()
                    persistAndShowSetup("Liste élèves importée")
                }

                REQUEST_GRID -> {
                    val bytes = contentResolver.openInputStream(uri).useRequired { it.readBytes() }
                    val grid = XlsxGridParser.parse(bytes)
                    project.criteria = grid.criteria
                    project.gridKind = grid.kind
                    project.gridTemplateBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    persistAndShowSetup("Grille de notation importée")
                }

                REQUEST_EXPORT_GRID -> {
                    exportFilledGrid(uri)
                }
            }
        } catch (error: Exception) {
            toast("Operation impossible: ${error.message ?: "fichier invalide"}")
        }
    }

    private fun showTpSetup() {
        selectedStudentId = null
        pairEditMode = false
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
                addView(row {
                    addView(iconActionButton(
                        iconRes = R.drawable.ic_file_import,
                        description = "Importer liste d'eleves CSV",
                    ) {
                        project.name = nameInput?.text?.toString()?.trim().orEmpty()
                        pickFile(REQUEST_STUDENTS, "text/*")
                    })
                    addView(iconActionButton(
                        iconRes = R.drawable.ic_person_add,
                        description = "Ajouter un eleve",
                    ) {
                        project.name = nameInput?.text?.toString()?.trim().orEmpty()
                        showAddStudentDialog { showTpSetup() }
                    })
                    addView(iconActionButton(
                        iconRes = R.drawable.ic_person_remove,
                        description = "Enlever un eleve",
                        enabled = project.students.isNotEmpty(),
                    ) {
                        project.name = nameInput?.text?.toString()?.trim().orEmpty()
                        showRemoveStudentDialog { showTpSetup() }
                    })
                })

                if (project.pairings.isNotEmpty()) {
                    addView(spacer(12))
                    addView(actionButton("Reset les binomes") {
                        project.name = nameInput?.text?.toString()?.trim().orEmpty()
                        unlinkAllStudents()
                        selectedStudentId = null
                        pairEditMode = false
                        saveCurrentProject()
                        toast("Binomes reinitialises")
                        showTpSetup()
                    })
                }

                addView(spacer(12))
                addView(infoLine("Grille", "${project.criteria.size} criteres"))
                addView(row {
                    addView(iconActionButton(
                        iconRes = R.drawable.ic_file_import,
                        description = "Importer feuille de notation XLSX",
                    ) {
                        project.name = nameInput?.text?.toString()?.trim().orEmpty()
                        pickFile(REQUEST_GRID, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    })
                    addView(iconActionButton(
                        iconRes = R.drawable.ic_file_export,
                        description = "Exporter grille remplie",
                        enabled = project.criteria.isNotEmpty(),
                    ) {
                        project.name = nameInput?.text?.toString()?.trim().orEmpty()
                        saveCurrentProject()
                        showExportCandidateDialog()
                    })
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
        if (!pairEditMode) selectedStudentId = null
        val scrollY = pendingStudentListScrollY
        pendingStudentListScrollY = null
        val root = verticalRoot {
                addView(headerRow(
                    title = project.name,
                    leading = {
                    addView(homeButton {
                        saveCurrentProject()
                        showHome()
                    })
                    addView(infoButton {
                        showCompetencyDescriptions()
                    })
                    },
                    trailing = {
                        addView(themeToggleButton { showStudentList() })
                    addView(gearButton { showTpSetup() })
                    },
                ))

                addView(recapView(), matchWrap().apply {
                    setMargins(0, dp(10), 0, dp(12))
                })

                if (project.students.size >= 5) {
                    addView(filterControl())
                    addView(spacer(10))
                }

                if (project.students.size >= 2) {
                    addView(actionButton(pairModeActionLabel()) {
                        when {
                            !project.pairMode -> {
                                project.pairMode = true
                                pairEditMode = true
                            }
                            pairEditMode -> {
                                pairEditMode = false
                                selectedStudentId = null
                            }
                            else -> pairEditMode = true
                        }
                        saveCurrentProject()
                        pendingStudentListScrollY = currentScrollY()
                        showStudentList()
                    })
                }
                addView(spacer(12))

                if (pairEditMode) {
                    addView(TextView(this@MainActivity).apply {
                        text = selectedStudentId
                            ?.let { "Selectionne le deuxieme eleve du binome." }
                            ?: "Selectionne deux eleves pour creer un binome. Utilise la croix pour defaire un lien."
                        textSize = 15f
                        setTextColor(ui.muted)
                        setPadding(dp(2), 0, dp(2), dp(10))
                    })
                }

                studentDisplayGroups().forEach { group ->
                    addView(studentGroupView(group))
                }
            }
        setContentView(root)
        scrollY?.let { y -> root.post { root.scrollTo(0, y) } }
    }

    private fun filteredStudents(): List<Student> {
        return when (studentFilter) {
            StudentFilter.ALL -> project.students
            StudentFilter.TO_GRADE -> project.students.filter { scoreForStudent(it.id) == null }
            StudentFilter.GRADED -> project.students.filter { scoreForStudent(it.id) != null }
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
        val grades = project.grades.getOrPut(gradeOwnerId(student.id)) { mutableMapOf() }
        val root = verticalRoot {
                addView(headerRow(
                    title = gradeTitle(student),
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
            }
        setContentView(root)
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
                val buttons = mutableListOf<Pair<Int, Button>>()
                fun refreshButtons() {
                    buttons.forEach { (value, button) ->
                        val selected = grades[criterion.id] == value
                        button.setTextColor(if (selected) ui.iconOnAccent else ui.text)
                        button.background = if (selected) accentSurface() else glassSurface()
                    }
                }
                options.forEach { (value, text) ->
                    val button = scoreButton(text, grades[criterion.id] == value) {
                        grades[criterion.id] = value
                        saveCurrentProject()
                        scoreText.text = "Note: ${scoreLabel(selectedStudentId)}"
                        scoreText.setTextColor(scoreColor(selectedStudentId))
                        refreshButtons()
                    }
                    buttons += value to button
                    addView(button)
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
        val selected = pairEditMode && selectedStudentId == student.id
        return Button(this).apply {
            text = "${student.name}\n${studentSubtitle(student)}"
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setAllCaps(false)
            setTextColor(if (selected) ui.iconOnAccent else ui.text)
            background = if (selected) accentSurface() else cardSurface()
            stateListAnimator = null
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { handleStudentClick(student) }
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dp(8)) }
        }
    }

    private fun pickFile(requestCode: Int, mimeType: String) {
        prepareProjectForImport()
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

    private fun prepareProjectForImport() {
        ActiveProjectPrefs.save(this, project.id)
        saveCurrentProject()
    }

    private fun restoreProjectPendingImport() {
        val activeProjectId = ActiveProjectPrefs.load(this) ?: return
        val storedProject = ProjectStore.loadAll(this).firstOrNull { it.id == activeProjectId }
        if (storedProject != null) project = storedProject
    }

    private fun saveCurrentProject() {
        ProjectStore.save(this, project)
        projects = ProjectStore.loadAll(this).toMutableList()
    }

    private fun currentScrollY(): Int {
        val content = findViewById<ViewGroup>(android.R.id.content)
        return (content.getChildAt(0) as? ScrollView)?.scrollY ?: 0
    }

    private fun scoreLabel(studentId: String?): String {
        val score = studentId?.let { scoreForStudent(it) }
        return score?.let { "${gradeFormat.format(it)} / 20" } ?: "A noter"
    }

    private fun scoreColor(studentId: String?): Int {
        val score = studentId?.let { scoreForStudent(it) } ?: return ui.muted
        return when {
            score < 8.0 -> ui.danger
            score < 13.0 -> ui.warning
            else -> ui.success
        }
    }

    private fun scoreColor(score: Double?): Int {
        score ?: return ui.muted
        return when {
            score < 8.0 -> ui.danger
            score < 13.0 -> ui.warning
            else -> ui.success
        }
    }

    private fun recapView(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = quietSurface()
            setPadding(dp(14), dp(12), dp(14), dp(12))
            if (!project.pairMode) {
                addView(recapLine("Moyenne classe", averageLabel(), scoreColor(averageScore(project))))
                addView(TextView(this@MainActivity).apply {
                    text = "${gradedCount()} / ${project.students.size} notes"
                    textSize = 15f
                    setTextColor(ui.muted)
                    setPadding(0, dp(4), 0, 0)
                })
            } else {
                val groups = pairGroups(project)
                addView(recapLine("Moyenne binomes", averageLabel(), scoreColor(averageScore(project))))
                addView(TextView(this@MainActivity).apply {
                    text = "${gradedCount()} / ${groups.size} groupes"
                    textSize = 15f
                    setTextColor(ui.muted)
                    setPadding(0, dp(4), 0, dp(6))
                })
                groups.forEach { group ->
                    val score = computeScore(project, gradesForStudent(project, group.first().id))
                    val label = group.joinToString(" + ") { familyName(it.name) }
                    addView(recapLine(label, scoreLabel(score), scoreColor(score)))
                }
            }
        }
    }

    private fun showCompetencyDescriptions() {
        if (project.criteria.none { it.descriptors.isNotEmpty() }) {
            toast("Aucun descripteur trouve dans la grille.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Descripteurs des competences")
            .setView(ScrollView(this).apply {
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    val padding = dp(18)
                    setPadding(padding, dp(12), padding, dp(4))
                    groupedCriteria().forEach { (skill, criteria) ->
                        addView(TextView(this@MainActivity).apply {
                            text = skill
                            textSize = 18f
                            setTextColor(ui.primary)
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setPadding(0, dp(10), 0, dp(6))
                        })
                        criteria.forEach { criterion ->
                            if (criterion.descriptors.isNotEmpty()) addView(descriptorBlock(criterion))
                        }
                    }
                })
            })
            .setPositiveButton("Fermer", null)
            .show()
    }

    private fun descriptorBlock(criterion: Criterion): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(10))
            addView(TextView(this@MainActivity).apply {
                text = criterion.label
                textSize = 15f
                setTextColor(ui.text)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            listOf(0, 1, 2, 3).forEach { level ->
                val description = criterion.descriptors[level].orEmpty()
                if (description.isNotBlank()) {
                    addView(TextView(this@MainActivity).apply {
                        text = "$level - $description"
                        textSize = 14f
                        setTextColor(ui.muted)
                        setPadding(0, dp(3), 0, 0)
                    })
                }
            }
        }
    }

    private fun showExportCandidateDialog() {
        val groups = pairGroups(project)
        if (groups.isEmpty()) {
            toast("Aucun eleve a exporter.")
            return
        }
        if (project.gridTemplateBase64.isBlank()) {
            toast("Reimporte la grille pour activer l'export XLSX.")
            return
        }
        val labels = groups.map { exportGroupLabel(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Exporter pour")
            .setItems(labels) { _, index ->
                val group = groups.getOrNull(index) ?: return@setItems
                val ownerId = gradeOwnerId(group.first().id)
                PendingExportPrefs.save(this, project.id, ownerId, labels[index])
                createExportDocument(labels[index])
            }
            .show()
    }

    private fun createExportDocument(label: String) {
        val cleanName = label
            .replace(Regex("[^A-Za-z0-9 _-]"), "")
            .trim()
            .ifBlank { "candidat" }
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_TITLE, "${project.name.ifBlank { "TP" }} - $cleanName.xlsx")
            },
            REQUEST_EXPORT_GRID,
        )
    }

    private fun exportFilledGrid(uri: Uri) {
        val pending = PendingExportPrefs.load(this)
            ?: throw IllegalArgumentException("export non prepare")
        val storedProject = ProjectStore.loadAll(this).firstOrNull { it.id == pending.projectId }
        if (storedProject != null) project = storedProject
        val template = Base64.decode(project.gridTemplateBase64, Base64.DEFAULT)
        val grades = project.grades[pending.ownerId] ?: emptyMap()
        val output = XlsxGridExporter.fill(project, template, pending.label, grades)
        contentResolver.openOutputStream(uri).useRequired { it.write(output) }
        toast("Grille exportee")
    }

    private fun exportGroupLabel(group: List<Student>): String {
        return group.joinToString(" + ") { it.name }
    }

    private fun recapLine(label: String, score: String, color: Int): View {
        return row {
            addView(TextView(this@MainActivity).apply {
                text = "$label :"
                textSize = 16f
                setTextColor(ui.muted)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@MainActivity).apply {
                text = score
                textSize = 16f
                setTextColor(color)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.END
            })
        }
    }

    private fun handleStudentClick(student: Student) {
        if (!pairEditMode) {
            showStudentGrade(student)
            return
        }
        val firstId = selectedStudentId
        when {
            firstId == null -> {
                selectedStudentId = student.id
                pendingStudentListScrollY = currentScrollY()
                showStudentList()
            }
            firstId == student.id -> {
                selectedStudentId = null
                pendingStudentListScrollY = currentScrollY()
                showStudentList()
            }
            else -> {
                linkStudents(firstId, student.id)
                selectedStudentId = null
                saveCurrentProject()
                pendingStudentListScrollY = currentScrollY()
                showStudentList()
            }
        }
    }

    private fun pairModeActionLabel(): String {
        return when {
            !project.pairMode -> "Mode binome"
            pairEditMode -> "Terminer les binomes"
            else -> "Mode binome"
        }
    }

    private fun studentDisplayGroups(): List<List<Student>> {
        val visibleIds = filteredStudents().map { it.id }.toSet()
        return if (project.pairMode) {
            pairGroups(project).filter { group -> group.any { it.id in visibleIds } }
        } else {
            filteredStudents().map { listOf(it) }
        }
    }

    private fun studentGroupView(group: List<Student>): View {
        return if (project.pairMode && group.size == 2) {
            pairGroupView(group[0], group[1])
        } else {
            studentRow(group.first())
        }
    }

    private fun pairGroupView(first: Student, second: Student): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            background = cardSurface()
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(TextView(this@MainActivity).apply {
                    text = "["
                    textSize = 58f
                    gravity = Gravity.CENTER
                    setTextColor(ui.primary)
                }, LinearLayout.LayoutParams(dp(30), dp(112)))
                if (pairEditMode) {
                    addView(TextView(this@MainActivity).apply {
                        text = "\u2715"
                        textSize = 18f
                        includeFontPadding = false
                        gravity = Gravity.CENTER
                        setTextColor(ui.iconOnAccent)
                        background = accentSurface()
                        setOnClickListener {
                            unlinkStudent(first.id)
                            selectedStudentId = null
                            saveCurrentProject()
                            pendingStudentListScrollY = currentScrollY()
                            showStudentList()
                        }
                    }, LinearLayout.LayoutParams(dp(30), dp(30)))
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(studentPairButton(first))
                addView(spacer(6))
                addView(studentPairButton(second))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(8), 0, 0, 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = scoreLabel(first.id)
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(scoreColor(first.id))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dp(10), 0, 0, 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(98)))
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dp(8)) }
        }
    }

    private fun studentPairButton(student: Student): View {
        val selected = pairEditMode && selectedStudentId == student.id
        return Button(this).apply {
            text = student.name
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setAllCaps(false)
            setTextColor(if (selected) ui.iconOnAccent else ui.text)
            background = if (selected) accentSurface() else quietSurface()
            stateListAnimator = null
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener { handleStudentClick(student) }
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun showAddStudentDialog(onDone: () -> Unit) {
        val input = EditText(this).apply {
            setSingleLine(true)
            hint = "Nom de l'eleve"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            styleInput()
        }
        AlertDialog.Builder(this)
            .setTitle("Ajouter un eleve")
            .setView(input)
            .setPositiveButton("Ajouter") { _, _ ->
                val name = cleanStudentName(input.text.toString())
                if (name.isBlank()) {
                    toast("Nom invalide.")
                    return@setPositiveButton
                }
                project.students = project.students + Student(uniqueStudentId(name), name)
                saveCurrentProject()
                onDone()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showRemoveStudentDialog(onDone: () -> Unit) {
        if (project.students.isEmpty()) {
            toast("Aucun eleve a enlever.")
            return
        }
        val labels = project.students.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Enlever un eleve")
            .setItems(labels) { _, index ->
                val student = project.students.getOrNull(index) ?: return@setItems
                AlertDialog.Builder(this)
                    .setTitle("Enlever ${student.name} ?")
                    .setMessage("Ses notes locales seront supprimees.")
                    .setPositiveButton("Enlever") { _, _ ->
                        removeStudent(student)
                        saveCurrentProject()
                        onDone()
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun averageLabel(): String {
        return averageLabel(project)
    }

    private fun averageLabel(targetProject: TpProject): String {
        return scoreLabel(averageScore(targetProject))
    }

    private fun averageScore(targetProject: TpProject): Double? {
        val scores = scoreGroups(targetProject).mapNotNull { (_, grades) -> computeScore(targetProject, grades) }
        return scores.takeIf { it.isNotEmpty() }?.average()
    }

    private fun gradedCount(): Int = scoreGroups(project).count { (_, grades) -> computeScore(project, grades) != null }

    private fun scoreForStudent(studentId: String): Double? {
        return computeScore(project, gradesForStudent(project, studentId))
    }

    private fun scoreLabel(score: Double?): String {
        return score?.let { "${gradeFormat.format(it)} / 20" } ?: "-- / 20"
    }

    private fun recapText(): String {
        if (!project.pairMode) {
            return "Moyenne classe: ${averageLabel()}   |   ${gradedCount()} / ${project.students.size} notes"
        }
        val groups = pairGroups(project)
        val lines = groups.joinToString("\n") { group ->
            val label = group.joinToString(" + ") { familyName(it.name) }
            val score = computeScore(project, gradesForStudent(project, group.first().id))
                ?.let { "${gradeFormat.format(it)} / 20" } ?: "A noter"
            "$label: $score"
        }
        return "Moyenne binomes: ${averageLabel()}   |   ${gradedCount()} / ${groups.size} groupes\n$lines"
    }

    private fun studentSubtitle(student: Student): String {
        val partner = partnerOf(student.id)
        return if (project.pairMode && partner != null) {
            "Binome avec ${partner.name} · ${scoreLabel(student.id)}"
        } else {
            scoreLabel(student.id)
        }
    }

    private fun gradeTitle(student: Student): String {
        val partner = partnerOf(student.id)
        return if (project.pairMode && partner != null) "${student.name} + ${partner.name}" else student.name
    }

    private fun familyName(fullName: String): String {
        val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return fullName
        val uppercasePrefix = parts.takeWhile { part ->
            part.any { it.isLetter() } && part == part.uppercase(Locale.ROOT)
        }
        return uppercasePrefix.takeIf { it.isNotEmpty() }?.joinToString(" ") ?: parts.first()
    }

    private fun partnerOf(studentId: String): Student? {
        val partnerId = project.pairings[studentId] ?: return null
        return project.students.firstOrNull { it.id == partnerId }
    }

    private fun linkStudents(firstId: String, secondId: String) {
        if (firstId == secondId) return
        unlinkStudent(firstId)
        unlinkStudent(secondId)
        project.pairings[firstId] = secondId
        project.pairings[secondId] = firstId

        val ownerId = canonicalPairId(firstId, secondId)
        val firstGrades = project.grades[firstId]
        val secondGrades = project.grades[secondId]
        val sharedGrades = firstGrades ?: secondGrades
        if (sharedGrades != null) {
            project.grades[ownerId] = sharedGrades.toMutableMap()
        }
        if (ownerId != firstId) project.grades.remove(firstId)
        if (ownerId != secondId) project.grades.remove(secondId)
    }

    private fun unlinkStudent(studentId: String) {
        val partnerId = project.pairings[studentId] ?: return
        val ownerId = canonicalPairId(studentId, partnerId)
        val sharedGrades = project.grades[ownerId]?.toMutableMap()
        if (sharedGrades != null) {
            project.grades.putIfAbsent(studentId, sharedGrades.toMutableMap())
            project.grades.putIfAbsent(partnerId, sharedGrades.toMutableMap())
        }
        project.pairings.remove(studentId)
        project.pairings.remove(partnerId)
    }

    private fun unlinkAllStudents() {
        project.pairings.keys.toList().forEach { studentId -> unlinkStudent(studentId) }
    }

    private fun removeStudent(student: Student) {
        val partnerId = project.pairings[student.id]
        val pairOwnerId = partnerId?.let { canonicalPairId(student.id, it) }
        unlinkStudent(student.id)
        project.students = project.students.filterNot { it.id == student.id }
        project.grades.remove(student.id)
        pairOwnerId?.let { project.grades.remove(it) }
        project.pairings.remove(student.id)
        project.pairings.entries.removeAll { it.value == student.id }
        if (project.students.size < 2) {
            project.pairMode = false
            pairEditMode = false
            selectedStudentId = null
        }
        toast("Eleve enleve")
    }

    private fun gradeOwnerId(studentId: String): String {
        val partnerId = project.pairings[studentId]
        return if (project.pairMode && partnerId != null) canonicalPairId(studentId, partnerId) else studentId
    }

    private fun gradeOwnerId(targetProject: TpProject, studentId: String): String {
        val partnerId = targetProject.pairings[studentId]
        return if (targetProject.pairMode && partnerId != null) canonicalPairId(studentId, partnerId) else studentId
    }

    private fun gradesForStudent(targetProject: TpProject, studentId: String): Map<String, Int> {
        return targetProject.grades[gradeOwnerId(targetProject, studentId)]
            ?: targetProject.grades[studentId]
            ?: emptyMap()
    }

    private fun scoreGroups(targetProject: TpProject): List<Pair<List<Student>, Map<String, Int>>> {
        return pairGroups(targetProject).map { group -> group to gradesForStudent(targetProject, group.first().id) }
    }

    private fun pairGroups(targetProject: TpProject): List<List<Student>> {
        val seen = mutableSetOf<String>()
        return targetProject.students.mapNotNull { student ->
            if (!seen.add(student.id)) return@mapNotNull null
            val partner = targetProject.pairings[student.id]
                ?.let { partnerId -> targetProject.students.firstOrNull { it.id == partnerId } }
            if (targetProject.pairMode && partner != null && seen.add(partner.id)) {
                listOf(student, partner)
            } else {
                listOf(student)
            }
        }
    }

    private fun canonicalPairId(firstId: String, secondId: String): String {
        return listOf(firstId, secondId).sorted().joinToString("+")
    }

    private fun uniqueStudentId(name: String): String {
        val base = "student-${System.currentTimeMillis()}-${name.lowercase(Locale.ROOT).hashCode()}"
        var candidate = base
        var suffix = 1
        val existingIds = project.students.map { it.id }.toSet()
        while (candidate in existingIds) {
            candidate = "$base-$suffix"
            suffix += 1
        }
        return candidate
    }

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
            .groupBy { normalizeHeader(it.skill) }
            .map { (_, criteria) -> preferredSkillLabel(criteria) to criteria }
    }

    private fun preferredSkillLabel(criteria: List<Criterion>): String {
        return criteria.firstOrNull { hasAccent(it.skill) }?.skill
            ?: criteria.firstOrNull()?.skill
            ?: ""
    }

    private fun hasAccent(text: String): Boolean {
        return Normalizer.normalize(text, Normalizer.Form.NFD).any { char ->
            Character.getType(char) == Character.NON_SPACING_MARK.toInt()
        }
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
            clipToPadding = false

            val baseHorizontalPadding = dp(18)
            val baseTopPadding = dp(18)
            val baseBottomPadding = dp(24)
            val contentView = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(baseHorizontalPadding, baseTopPadding, baseHorizontalPadding, baseBottomPadding)
                content()
            }

            setOnApplyWindowInsetsListener { _, insets ->
                val (systemTopInset, systemBottomInset) = systemBarVerticalInsets(insets)
                contentView.setPadding(
                    baseHorizontalPadding,
                    baseTopPadding + systemTopInset,
                    baseHorizontalPadding,
                    baseBottomPadding + systemBottomInset,
                )
                insets
            }

            addView(contentView)
        }
    }

    private fun systemBarVerticalInsets(insets: WindowInsets): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            return bars.top to bars.bottom
        }
        return legacySystemBarVerticalInsets(insets)
    }

    @Suppress("DEPRECATION")
    private fun legacySystemBarVerticalInsets(insets: WindowInsets): Pair<Int, Int> {
        return insets.systemWindowInsetTop to insets.systemWindowInsetBottom
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

    private fun iconActionButton(
        iconRes: Int,
        description: String,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) = ImageButton(this).apply {
        background = if (enabled) accentSurface() else quietSurface()
        setImageResource(iconRes)
        setColorFilter(if (enabled) ui.iconOnAccent else ui.muted)
        contentDescription = description
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.55f
        setOnClickListener { onClick() }
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            setMargins(dp(3), 0, dp(3), 0)
        }
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

    private fun infoButton(onClick: () -> Unit) = ImageButton(this).apply {
        background = quietSurface()
        setImageResource(R.drawable.ic_info)
        setColorFilter(ui.primary)
        contentDescription = "Descripteurs des competences"
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
        private const val REQUEST_EXPORT_GRID = 12
    }
}

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

private fun TpProject.hasContent(): Boolean {
    return name.isNotBlank() || students.isNotEmpty() || criteria.isNotEmpty() || grades.isNotEmpty() || pairings.isNotEmpty() || gridTemplateBase64.isNotBlank()
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

object CsvStudentParser {
    fun parse(input: InputStream): List<Student> {
        val bytes = input.readBytes()
        val utf8 = bytes.toString(Charsets.UTF_8)
        val content = if (utf8.contains('\uFFFD')) bytes.toString(Charsets.ISO_8859_1) else utf8
        return content
            .lineSequence()
            .mapNotNull { rawLine -> extractName(rawLine) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .mapIndexed { index, name -> Student("student-$index-${name.lowercase(Locale.ROOT).hashCode()}", name) }
            .toList()
    }

    private fun extractName(rawLine: String): String? {
        val line = rawLine.trim().trimStart('\uFEFF')
        if (line.isBlank()) return null
        val separator = detectSeparator(line)
        val columns = splitCsvLine(line, separator).map { cleanStudentName(it) }
        val lowerColumns = columns.map { normalizeHeader(it) }
        if (lowerColumns.any { it == "nom" || it == "prenom" || it == "name" || it.startsWith("sp") }) return null
        val textColumns = columns.filter { candidate ->
            candidate.any { it.isLetter() } && !candidate.contains("@")
        }
        val name = textColumns.firstOrNull { it.contains(" ") } ?: textColumns.take(2).joinToString(" ")
        return name.takeIf { it.isNotBlank() }
    }

    private fun detectSeparator(line: String): Char {
        return listOf(';', ',', '\t')
            .maxByOrNull { separator -> line.count { it == separator } }
            ?.takeIf { line.contains(it) }
            ?: ';'
    }

    private fun splitCsvLine(line: String, separator: Char): List<String> {
        val columns = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> quoted = !quoted
                char == separator && !quoted -> {
                    columns += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        columns += current.toString()
        return columns
    }
}

private fun normalizeHeader(raw: String): String {
    return Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
}

private fun cleanStudentName(raw: String): String {
    return raw
        .trim()
        .trim('"')
        .trim()
        .replace(Regex("\\s+"), " ")
        .trim(';', ',', '\t', ' ')
}

object XlsxGridParser {
    fun parse(bytes: ByteArray): GridImport {
        val entries = readZipEntries(ByteArrayInputStream(bytes))
        val sharedStrings = parseSharedStrings(entries["xl/sharedStrings.xml"] ?: ByteArray(0))
        val sheets = workbookSheets(entries)
        val grilleSheet = entries[sheets["Grille"] ?: "xl/worksheets/sheet2.xml"]
        if (grilleSheet == null) return parseEtlv(entries, sharedStrings, sheets)
        val descriptors = entries[sheets["Descripteurs"] ?: "xl/worksheets/sheet3.xml"]
            ?.let { parseDescriptors(it, sharedStrings) }
            .orEmpty()
        val cells = parseCells(grilleSheet, sharedStrings)
        val criteria = (8..21).mapNotNull { row ->
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
                descriptors = descriptors[normalizeHeader(label)] ?: descriptors["criterion-$row"].orEmpty(),
            )
        }
        return GridImport(criteria, GridKind.EP_2I2D)
    }

    private fun parseEtlv(
        entries: Map<String, ByteArray>,
        sharedStrings: List<String>,
        sheets: Map<String, String>,
    ): GridImport {
        val sheet = entries[sheets["Candidat 1"] ?: "xl/worksheets/sheet1.xml"]
            ?: throw IllegalArgumentException("Feuille de notation introuvable")
        val cells = parseCells(sheet, sharedStrings)
        val criteria = (1..80).mapNotNull { row ->
            val label = cells["A$row"]?.trim().orEmpty()
            val weight = cells["J$row"]?.toDoubleOrNull() ?: return@mapNotNull null
            if (!label.contains(" - ") || weight <= 0.0) return@mapNotNull null
            val code = label.substringBefore(" - ").trim()
            Criterion(
                id = "criterion-$row",
                skill = code,
                label = label,
                weight = weight,
            )
        }
        if (criteria.isEmpty()) throw IllegalArgumentException("Aucun critere reconnu")
        return GridImport(criteria, GridKind.ETLV)
    }

    private fun workbookSheets(entries: Map<String, ByteArray>): Map<String, String> {
        val workbook = entries["xl/workbook.xml"] ?: return emptyMap()
        val relationships = entries["xl/_rels/workbook.xml.rels"] ?: return emptyMap()
        val targetsById = workbookRelationships(relationships)
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(workbook), null)
        val sheets = mutableMapOf<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name").orEmpty()
                val id = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id").orEmpty()
                val target = targetsById[id].orEmpty()
                if (name.isNotBlank() && target.isNotBlank()) {
                    sheets[name] = if (target.startsWith("xl/")) target else "xl/$target"
                }
            }
            event = parser.next()
        }
        return sheets
    }

    private fun workbookRelationships(bytes: ByteArray): Map<String, String> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        val relationships = mutableMapOf<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id").orEmpty()
                val target = parser.getAttributeValue(null, "Target").orEmpty()
                if (id.isNotBlank() && target.isNotBlank()) relationships[id] = target
            }
            event = parser.next()
        }
        return relationships
    }

    private fun parseDescriptors(bytes: ByteArray, sharedStrings: List<String>): Map<String, Map<Int, String>> {
        val cells = parseCells(bytes, sharedStrings)
        val result = mutableMapOf<String, Map<Int, String>>()
        (1..80).forEach { row ->
            val label = cells["C$row"]?.trim().orEmpty()
            if (label.isBlank() || normalizeHeader(label).startsWith("criteres d'evaluation")) return@forEach
            val levels = mapOf(
                0 to cells["D$row"].orEmpty().trim(),
                1 to cells["E$row"].orEmpty().trim(),
                2 to cells["F$row"].orEmpty().trim(),
                3 to cells["G$row"].orEmpty().trim(),
            ).filterValues { it.isNotBlank() }
            if (levels.isNotEmpty()) {
                result[normalizeHeader(label)] = levels
                result["criterion-${row + 3}"] = levels
            }
        }
        return result
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

object XlsxGridExporter {
    fun fill(
        project: TpProject,
        template: ByteArray,
        candidateLabel: String,
        grades: Map<String, Int>,
    ): ByteArray {
        val entries = readZipEntries(template)
        val sheetPath = when (project.gridKind) {
            GridKind.ETLV -> "xl/worksheets/sheet1.xml"
            else -> "xl/worksheets/sheet2.xml"
        }
        val sheet = entries[sheetPath]?.toString(Charsets.UTF_8)
            ?: throw IllegalArgumentException("Feuille export introuvable")
        entries[sheetPath] = when (project.gridKind) {
            GridKind.ETLV -> fillEtlvSheet(sheet, project, candidateLabel, grades)
            else -> fillEpSheet(sheet, project, candidateLabel, grades)
        }.toByteArray(Charsets.UTF_8)
        prepareWorkbookForRecalculation(entries)
        return writeZipEntries(entries)
    }

    private fun fillEpSheet(
        sheet: String,
        project: TpProject,
        candidateLabel: String,
        grades: Map<String, Int>,
    ): String {
        var xml = sheet
        xml = upsertCell(xml, "B28", candidateLabel, text = true)
        xml = upsertCell(xml, "B27", exportDateLabel(), text = true)
        xml = upsertCell(xml, "D28", project.name, text = true)
        project.criteria.forEach { criterion ->
            val row = criterion.rowNumber() ?: return@forEach
            listOf("E", "F", "G", "H", "I").forEach { col -> xml = upsertCell(xml, "$col$row", "") }
            val level = grades[criterion.id] ?: return@forEach
            val col = when (level) {
                -1 -> "E"
                0 -> "F"
                1 -> "G"
                2 -> "H"
                3 -> "I"
                else -> null
            } ?: return@forEach
            xml = upsertCell(xml, "$col$row", "x", text = true)
        }
        return xml
    }

    private fun fillEtlvSheet(
        sheet: String,
        project: TpProject,
        candidateLabel: String,
        grades: Map<String, Int>,
    ): String {
        var xml = sheet
        xml = upsertCell(xml, "B6", etlvLastNames(candidateLabel), text = true)
        xml = upsertCell(xml, "B7", etlvFirstNames(candidateLabel), text = true)
        xml = upsertCell(xml, "B24", exportDateLabel(), text = true)
        xml = upsertCell(xml, "D24", project.name, text = true)
        project.criteria.forEach { criterion ->
            val row = criterion.rowNumber() ?: return@forEach
            listOf("E", "F", "G", "H").forEach { col -> xml = upsertCell(xml, "$col$row", "") }
            val level = grades[criterion.id] ?: return@forEach
            val col = when (level.coerceIn(0, 3)) {
                0 -> "E"
                1 -> "F"
                2 -> "G"
                else -> "H"
            }
            xml = upsertCell(xml, "$col$row", "X", text = true)
        }
        return xml
    }

    private fun Criterion.rowNumber(): Int? {
        return id.substringAfter("criterion-", "").toIntOrNull()
    }

    private fun exportDateLabel(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date())
    }

    private fun etlvLastNames(candidateLabel: String): String {
        return candidateLabel.split(" + ")
            .map { name -> exportFamilyName(name) }
            .joinToString(" + ")
    }

    private fun etlvFirstNames(candidateLabel: String): String {
        return candidateLabel.split(" + ")
            .map { name -> exportGivenNames(name) }
            .filter { it.isNotBlank() }
            .joinToString(" + ")
    }

    private fun exportFamilyName(fullName: String): String {
        val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return fullName.trim()
        val uppercasePrefix = parts.takeWhile { part ->
            part.any { it.isLetter() } && part == part.uppercase(Locale.ROOT)
        }
        return uppercasePrefix.takeIf { it.isNotEmpty() }?.joinToString(" ") ?: parts.first()
    }

    private fun exportGivenNames(fullName: String): String {
        val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size <= 1) return ""
        val familyParts = parts.takeWhile { part ->
            part.any { it.isLetter() } && part == part.uppercase(Locale.ROOT)
        }.takeIf { it.isNotEmpty() } ?: parts.take(1)
        return parts.drop(familyParts.size).joinToString(" ")
    }

    private fun upsertCell(xml: String, ref: String, value: String, text: Boolean = false): String {
        val row = ref.dropWhile { it.isLetter() }.toIntOrNull() ?: return xml
        val cellRegex = cellRegex(ref)
        val existingCell = cellRegex.find(xml)
        if (existingCell != null) {
            val cell = cellXml(ref, value, text, existingCell.value)
            return xml.replaceRange(existingCell.range, cell)
        }

        val cell = cellXml(ref, value, text)
        val rowRegex = Regex("(<row\\b[^>]*\\br=\"$row\"[^>]*>)(.*?)(</row>)", RegexOption.DOT_MATCHES_ALL)
        val rowMatch = rowRegex.find(xml)
        if (rowMatch != null) {
            val content = insertCellInOrder(rowMatch.groupValues[2], cell, ref)
            return xml.replaceRange(rowMatch.range, rowMatch.groupValues[1] + content + rowMatch.groupValues[3])
        }
        val newRow = "<row r=\"$row\">$cell</row>"
        return xml.replace("</sheetData>", "$newRow</sheetData>")
    }

    private fun cellRegex(ref: String): Regex {
        return Regex(
            "<c\\b(?=[^>]*\\br=\"$ref\")[^>]*/>|<c\\b(?=[^>]*\\br=\"$ref\")[^>]*>.*?</c>",
            RegexOption.DOT_MATCHES_ALL,
        )
    }

    private fun insertCellInOrder(rowContent: String, cell: String, ref: String): String {
        val targetColumn = columnIndex(ref)
        val cellRegex = Regex("<c\\b[^>]*\\br=\"([A-Z]+)\\d+\"[^>]*/>|<c\\b[^>]*\\br=\"([A-Z]+)\\d+\"[^>]*>.*?</c>", RegexOption.DOT_MATCHES_ALL)
        val insertBefore = cellRegex.findAll(rowContent).firstOrNull { match ->
            val column = match.groupValues[1].ifBlank { match.groupValues[2] }
            columnIndex(column) > targetColumn
        }
        return if (insertBefore != null) {
            rowContent.substring(0, insertBefore.range.first) + cell + rowContent.substring(insertBefore.range.first)
        } else {
            rowContent + cell
        }
    }

    private fun columnIndex(ref: String): Int {
        return ref.takeWhile { it.isLetter() }.fold(0) { total, char ->
            total * 26 + (char.uppercaseChar() - 'A' + 1)
        }
    }

    private fun cellXml(ref: String, value: String, text: Boolean, existingCell: String? = null): String {
        val attrs = cellAttributes(ref, existingCell)
        if (value.isBlank()) return "<c $attrs/>"
        return if (text) {
            "<c $attrs t=\"str\"><v>${escapeXml(value)}</v></c>"
        } else {
            "<c $attrs><v>$value</v></c>"
        }
    }

    private fun cellAttributes(ref: String, existingCell: String?): String {
        val rawAttrs = existingCell
            ?.let { Regex("<c\\b([^>]*)").find(it)?.groupValues?.getOrNull(1) }
            .orEmpty()
        val style = Regex("\\bs=\"[^\"]*\"").find(rawAttrs)?.value
        return listOfNotNull("r=\"$ref\"", style).joinToString(" ")
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun prepareWorkbookForRecalculation(entries: MutableMap<String, ByteArray>) {
        entries.remove("xl/calcChain.xml")
        entries["[Content_Types].xml"] = entries["[Content_Types].xml"]
            ?.toString(Charsets.UTF_8)
            ?.replace(Regex("<Override\\b[^>]*PartName=\"/xl/calcChain.xml\"[^>]*/>"), "")
            ?.toByteArray(Charsets.UTF_8)
            ?: return
        entries["xl/_rels/workbook.xml.rels"] = entries["xl/_rels/workbook.xml.rels"]
            ?.toString(Charsets.UTF_8)
            ?.replace(Regex("<Relationship\\b[^>]*Type=\"[^\"]*/calcChain\"[^>]*/>"), "")
            ?.toByteArray(Charsets.UTF_8)
            ?: return
        entries["xl/workbook.xml"] = entries["xl/workbook.xml"]
            ?.toString(Charsets.UTF_8)
            ?.let { workbook ->
                val calcPr = "<calcPr calcMode=\"auto\" fullCalcOnLoad=\"1\" forceFullCalc=\"1\"/>"
                val calcRegex = Regex("<calcPr\\b[^>]*/>|<calcPr\\b[^>]*>.*?</calcPr>", RegexOption.DOT_MATCHES_ALL)
                if (calcRegex.containsMatchIn(workbook)) {
                    workbook.replace(calcRegex, calcPr)
                } else {
                    workbook.replace("</workbook>", "$calcPr</workbook>")
                }
            }
            ?.toByteArray(Charsets.UTF_8)
            ?: return
    }

    private fun readZipEntries(bytes: ByteArray): MutableMap<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = ByteArrayOutputStream()
                zip.copyTo(output)
                entries[entry.name] = output.toByteArray()
            }
        }
        return entries
    }

    private fun writeZipEntries(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
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
                Criterion(
                    getString("id"),
                    getString("skill"),
                    getString("label"),
                    getDouble("weight"),
                    optJSONObject("descriptors").toIntStringMap(),
                )
            },
            grades = json.optJSONObject("grades").toGradeMap(),
            pairMode = json.optBoolean("pairMode", false),
            pairings = json.optJSONObject("pairings").toStringMap(),
            gridKind = json.optString("gridKind", GridKind.EP_2I2D),
            gridTemplateBase64 = json.optString("gridTemplateBase64"),
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
                        .put("descriptors", JSONObject(it.descriptors.mapKeys { entry -> entry.key.toString() }))
                }),
            )
            .put("grades", JSONObject(grades.mapValues { JSONObject(it.value as Map<*, *>) }))
            .put("pairMode", pairMode)
            .put("pairings", JSONObject(pairings as Map<*, *>))
            .put("gridKind", gridKind)
            .put("gridTemplateBase64", gridTemplateBase64)
    }

    private fun TpProject.hasContent(): Boolean {
        return name.isNotBlank() || students.isNotEmpty() || criteria.isNotEmpty() || grades.isNotEmpty() || pairings.isNotEmpty() || gridTemplateBase64.isNotBlank()
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

    private fun JSONObject?.toStringMap(): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        if (this == null) return result
        keys().forEach { key -> result[key] = optString(key) }
        return result
    }

    private fun JSONObject?.toIntStringMap(): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        if (this == null) return result
        keys().forEach { key ->
            key.toIntOrNull()?.let { result[it] = optString(key) }
        }
        return result
    }
}

object ActiveProjectPrefs {
    private const val PREFS = "tp-project"
    private const val KEY_ACTIVE_PROJECT_ID = "active-project-id"

    fun save(activity: Activity, projectId: String) {
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_PROJECT_ID, projectId)
            .apply()
    }

    fun load(activity: Activity): String? {
        return activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .getString(KEY_ACTIVE_PROJECT_ID, null)
    }
}

data class PendingExport(
    val projectId: String,
    val ownerId: String,
    val label: String,
)

object PendingExportPrefs {
    private const val PREFS = "tp-project"
    private const val KEY_PROJECT_ID = "export-project-id"
    private const val KEY_OWNER_ID = "export-owner-id"
    private const val KEY_LABEL = "export-label"

    fun save(activity: Activity, projectId: String, ownerId: String, label: String) {
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROJECT_ID, projectId)
            .putString(KEY_OWNER_ID, ownerId)
            .putString(KEY_LABEL, label)
            .apply()
    }

    fun load(activity: Activity): PendingExport? {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        val projectId = prefs.getString(KEY_PROJECT_ID, null) ?: return null
        val ownerId = prefs.getString(KEY_OWNER_ID, null) ?: return null
        val label = prefs.getString(KEY_LABEL, null) ?: return null
        return PendingExport(projectId, ownerId, label)
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

private inline fun <T> OutputStream?.useRequired(block: (OutputStream) -> T): T {
    val stream = this ?: throw IllegalArgumentException("fichier illisible")
    return stream.use(block)
}
