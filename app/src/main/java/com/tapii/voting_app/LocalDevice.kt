package com.tapii.voting_app

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.view.setPadding

class LocalDevice : ComponentActivity() {

    private lateinit var subjectText: TextView
    private lateinit var optionsContainer: LinearLayout
    private lateinit var castVoteButton: Button
    private lateinit var voterInfo: TextView

    private var subject: String = ""
    private var options: ArrayList<String> = arrayListOf()
    private var allowMultiple: Boolean = false
    private var vCount: Int = 2

    private var currentVoter = 1
    private lateinit var votes: MutableMap<String, Int>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.local_device)

        subjectText = findViewById(R.id.local_subject)
        optionsContainer = findViewById(R.id.local_options_container)
        castVoteButton = findViewById(R.id.cast_vote_button)
        voterInfo = findViewById(R.id.voter_info)

        subject = intent.getStringExtra("subject") ?: ""
        options = intent.getStringArrayListExtra("options") ?: arrayListOf()
        allowMultiple = intent.getBooleanExtra("allowMultiple", false)
        vCount = intent.getIntExtra("vCount", 2)

        votes = mutableMapOf()
        for (opt in options) {
            votes[opt] = 0
        }

        subjectText.text = subject
        updateVoterInfo()
        renderOptions()

        castVoteButton.setOnClickListener {
            recordVote()
        }
    }

    private fun renderOptions() {
        optionsContainer.removeAllViews()

        castVoteButton.isEnabled = false

        if (allowMultiple) {
            for (opt in options) {
                val cb = CheckBox(this).apply {
                    text = opt
                    textSize = 18f
                    setPadding(16)
                    setOnCheckedChangeListener { _, _ ->
                        updateButtonState()
                    }
                }
                optionsContainer.addView(cb)
            }
        } else {
            val rg = RadioGroup(this).apply {
                orientation = RadioGroup.VERTICAL
                setOnCheckedChangeListener { _, _ ->
                    updateButtonState()
                }
            }
            for (opt in options) {
                val rb = RadioButton(this).apply {
                    text = opt
                    textSize = 18f
                    setPadding(16)
                }
                rg.addView(rb)
            }
            optionsContainer.addView(rg)
        }
    }

    private fun updateButtonState() {
        val anySelected = if (allowMultiple) {
            (0 until optionsContainer.childCount).any { i ->
                val view = optionsContainer.getChildAt(i)
                view is CheckBox && view.isChecked
            }
        } else {
            val rg = optionsContainer.getChildAt(0) as RadioGroup
            rg.checkedRadioButtonId != -1
        }

        castVoteButton.isEnabled = anySelected
    }


    private fun recordVote() {
        val selected = mutableListOf<String>()


        if (allowMultiple) {
            for (i in 0 until optionsContainer.childCount) {
                val view = optionsContainer.getChildAt(i)
                if (view is CheckBox && view.isChecked) {
                    selected.add(view.text.toString())
                }
            }
        } else {
            val rg = optionsContainer.getChildAt(0) as RadioGroup
            val checkedId = rg.checkedRadioButtonId
            if (checkedId != -1) {
                val rb = findViewById<RadioButton>(checkedId)
                selected.add(rb.text.toString())
            }
        }

        if (selected.isEmpty()) {
            Toast.makeText(this, "Please select at least one option", Toast.LENGTH_SHORT).show()
            return
        }

        for (opt in selected) {
            votes[opt] = (votes[opt] ?: 0) + 1
        }

        if (currentVoter < vCount) {
            currentVoter++
            Toast.makeText(this, "Vote recorded. Next voter, please!", Toast.LENGTH_SHORT).show()
            updateVoterInfo()
            renderOptions()
        } else {
            showResults()
        }
    }

    private fun updateVoterInfo() {
        voterInfo.text = "Voter $currentVoter of $vCount"
    }

    private fun showResults() {
        val builder = StringBuilder("Voting Finished!\n\nResults:\n")
        for ((opt, count) in votes) {
            builder.append("$opt: $count\n")
        }
        Toast.makeText(this, builder.toString(), Toast.LENGTH_LONG).show()

        optionsContainer.removeAllViews()
        for ((opt, count) in votes) {
            val tv = TextView(this).apply {
                text = "$opt: $count"
                textSize = 18f
                setPadding(16)
            }
            optionsContainer.addView(tv)
        }

        castVoteButton.visibility = View.INVISIBLE
        voterInfo.text = "Voting Complete!"
    }
}
