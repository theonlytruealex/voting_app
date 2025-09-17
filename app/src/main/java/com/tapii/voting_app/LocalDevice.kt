package com.tapii.voting_app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text

class LocalDevice : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        val subject = intent.getStringExtra("subject") ?: ""
        val options = intent.getStringArrayListExtra("options") ?: arrayListOf()
        val allowMultiple = intent.getBooleanExtra("allowMultiple", false)

        println("I am here")
        println("Subject: $subject");
        println("Options: $options");
        println("Multiple Selection: $allowMultiple");
    }
}