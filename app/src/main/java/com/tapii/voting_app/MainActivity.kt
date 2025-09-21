package com.tapii.voting_app

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible

class MainActivity : ComponentActivity() {

    private lateinit var optionsContainer: LinearLayout
    private lateinit var voteSubject: EditText
    private lateinit var multipleSelectionSwitch: Switch
    private lateinit var choicesGroup: RadioGroup
    private lateinit var continueButton: Button
    private lateinit var addOptionButton: Button
    private lateinit var voterCount: EditText

    private var optionCount = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind views
        optionsContainer = findViewById(R.id.options_container)
        voteSubject = findViewById(R.id.vote_subject_txt)
        multipleSelectionSwitch = findViewById(R.id.multiple_selection_switch)
        choicesGroup = findViewById(R.id.choices_group)
        continueButton = findViewById(R.id.continue_button)
        addOptionButton = findViewById(R.id.add_option_button)
        voterCount = findViewById(R.id.voter_count)

        addOptionButton.setOnClickListener {
            optionCount++
            val newOption = EditText(this).apply {
                hint = "Option $optionCount"
                setBackgroundResource(R.drawable.edittext_background)
                setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    48.dpToPx()
                ).apply {
                    bottomMargin = 8.dpToPx()
                }
            }
            optionsContainer.addView(newOption, optionsContainer.childCount - 1)
            addWatcher(newOption)
            validateForm()
        }

        voteSubject.addTextChangedListener(formWatcher)

        for (i in 0 until optionsContainer.childCount) {
            val view = optionsContainer.getChildAt(i)
            if (view is EditText) {
                addWatcher(view)
            }
        }

        // Also validate when a RadioButton is selected
        choicesGroup.setOnCheckedChangeListener { _, _ ->
            validateForm()
        }

        voterCount.addTextChangedListener(formWatcher)

        continueButton.setOnClickListener {
            val data = collectFormData()
            if (data.networkChoice.compareTo("LAN") == 0) {
                println("Subject: ${data.subject}")
                println("Options: ${data.options}")
                println("Multiple Selection: ${data.allowMultiple}")
                println("Network Choice: ${data.networkChoice}")
            } else {
                val intent = Intent(this@MainActivity, LocalDevice::class.java).apply {
                    putExtra("subject", data.subject)
                    putStringArrayListExtra("options", ArrayList(data.options))
                    putExtra("allowMultiple", data.allowMultiple)
                    putExtra("vCount", data.vCount)
                }
                startActivity(intent)
            }
        }

        validateForm()
    }

    private val formWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            validateForm()
        }
        override fun afterTextChanged(s: Editable?) {}
    }

    private fun addWatcher(editText: EditText) {
        editText.addTextChangedListener(formWatcher)
    }

    private fun validateForm() {
        val subjectFilled = voteSubject.text.toString().trim().isNotEmpty()

        var filledOptions = 0
        for (i in 0 until optionsContainer.childCount) {
            val view = optionsContainer.getChildAt(i)
            if (view is EditText && view.text.toString().trim().isNotEmpty()) {
                filledOptions++
            }
        }

        val radioSelected = choicesGroup.checkedRadioButtonId != -1
        if (radioSelected) {
            val selectedText = findViewById<RadioButton>(choicesGroup.checkedRadioButtonId).text.toString()
            if (selectedText != "LAN") {
                voterCount.visibility = View.VISIBLE
            } else {
                voterCount.visibility = View.INVISIBLE
            }
        }
        continueButton.isEnabled = subjectFilled && filledOptions >= 2 && radioSelected && ((!voterCount.isVisible) || voterCount.text.toString().trim().isNotEmpty())
    }

    private fun collectFormData(): FormData {
        val subjectText = voteSubject.text.toString().trim()

        val options = mutableListOf<String>()
        for (i in 0 until optionsContainer.childCount) {
            val view = optionsContainer.getChildAt(i)
            if (view is EditText) {
                val txt = view.text.toString().trim()
                if (txt.isNotEmpty()) {
                    options.add(txt)
                }
            }
        }

        val allowMultiple = multipleSelectionSwitch.isChecked

        val selectedId = choicesGroup.checkedRadioButtonId
        val selectedText = if (selectedId != -1) {
            findViewById<RadioButton>(selectedId).text.toString()
        } else {
            ""
        }
        var vCount = -1
        if (voterCount.isVisible) {
            vCount = voterCount.text.toString().trim().toInt()
        }

        return FormData(
            subject = subjectText,
            options = options,
            allowMultiple = allowMultiple,
            networkChoice = selectedText,
            vCount = vCount
        )
    }

    fun Int.dpToPx(): Int =
        (this * Resources.getSystem().displayMetrics.density).toInt()
}

data class FormData(
    val subject: String,
    val options: List<String>,
    val allowMultiple: Boolean,
    val networkChoice: String,
    val vCount: Int
)
