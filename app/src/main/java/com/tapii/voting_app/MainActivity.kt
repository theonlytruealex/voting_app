package com.tapii.voting_app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tapii.voting_app.ui.theme.Voting_appTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Voting_appTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        TwoButtonsScreen { selected ->
                            navController.navigate("second/${Uri.encode(selected)}")
                        }
                    }
                    composable(
                        "second/{firstOption}",
                        arguments = listOf(navArgument("firstOption") { type = NavType.StringType })
                    ) { backStack ->
                        val firstOption = backStack.arguments?.getString("firstOption") ?: ""
                        SetVotingParams(firstOption) { f, n, t ->
                            navController.navigate("third/${Uri.encode(f)}/$n/${Uri.encode(t)}")
                        }
                    }
                    composable(
                        "third/{firstOption}/{number}/{text}",
                        arguments = listOf(
                            navArgument("firstOption") { type = NavType.StringType },
                            navArgument("number") { type = NavType.IntType },
                            navArgument("text") { type = NavType.StringType }
                        )
                    ) { backStack ->
                        val first = backStack.arguments?.getString("firstOption") ?: ""
                        val number = backStack.arguments?.getString("number")?.toIntOrNull() ?: 0
                        val text = backStack.arguments?.getString("text") ?: ""
                        VotingStart(first, number, text)
                    }
                }
            }
        }
    }
}

@Composable
fun TwoButtonsScreen(onContinue: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Button(onClick = { println("To be Decided") }, modifier = Modifier.fillMaxWidth(0.7f)) {
                Text("LAN Voting", modifier = Modifier.padding(vertical = 8.dp))
            }

            Box {
                Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(0.7f)) {
                    Text("Same Device Voting", modifier = Modifier.padding(vertical = 8.dp))
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    listOf("Option 1", "Option 2", "Option 3").forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedOption = option
                                expanded = false
                            }
                        )
                    }
                }
            }

            if (selectedOption != null) {
                Button(onClick = { onContinue(selectedOption!!) }, modifier = Modifier.fillMaxWidth(0.6f)) {
                    Text("Continue ➡")
                }
            }
        }
    }
}

@Composable
fun SetVotingParams(firstOption: String, onNext: (String, Int, String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selectedNumber by remember { mutableStateOf<Int?>(null) }
    var text by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
        ) {
            Box {
                Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(0.7f)) {
                    Text(if (selectedNumber != null) selectedNumber.toString() else "Choose a number")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    (2..20).forEach { n ->
                        DropdownMenuItem(
                            text = { Text(n.toString()) },
                            onClick = {
                                selectedNumber = n
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Enter text") },
                modifier = Modifier.fillMaxWidth(0.7f)
            )

            if (selectedNumber != null && text.isNotBlank()) {
                Button(
                    onClick = { onNext(firstOption, selectedNumber!!, text) },
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Text("Next ➡")
                }
            }
        }
    }
}

@Composable
fun VotingStart(firstOption: String, number: Int, text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("First screen choice: $firstOption")
            Text("Number: $number")
            Text("Text: $text")
        }
    }
}
