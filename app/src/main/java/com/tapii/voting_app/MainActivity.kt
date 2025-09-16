package com.tapii.voting_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.widget.LinearLayout
import android.widget.Button
import android.widget.EditText



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val optionsContainer = findViewById<LinearLayout>(R.id.options_container)
        val addOptionButton = findViewById<Button>(R.id.add_option_button)

        var optionCount = 2

        addOptionButton.setOnClickListener {
            optionCount++
            val newOption = EditText(this)
            newOption.hint = "Option $optionCount"
            newOption.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            optionsContainer.addView(newOption, optionsContainer.childCount - 1)
        }
    }
}
