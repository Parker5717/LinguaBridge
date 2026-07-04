package com.linguabridge.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.linguabridge.app.R
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.linguabridge.app.ui.decks.DecksScreen
import com.linguabridge.app.ui.dictionary.DictionaryScreen
import com.linguabridge.app.ui.library.DialogueScreen
import com.linguabridge.app.ui.library.HskGrammarScreen
import com.linguabridge.app.ui.library.LibraryScreen
import com.linguabridge.app.ui.library.ReaderScreen
import com.linguabridge.app.ui.navigation.TopDestination
import com.linguabridge.app.ui.practice.DictationScreen
import com.linguabridge.app.ui.practice.ListeningScreen
import com.linguabridge.app.ui.practice.PracticeScreen
import com.linguabridge.app.ui.quiz.PlacementScreen
import com.linguabridge.app.ui.quiz.QuizListScreen
import com.linguabridge.app.ui.quiz.QuizSessionScreen
import com.linguabridge.app.ui.review.ReviewScreen
import com.linguabridge.app.ui.settings.SettingsScreen
import com.linguabridge.app.ui.stats.StatsScreen
import com.linguabridge.app.ui.today.TodayScreen
import com.linguabridge.app.ui.wordgame.WordGameScreen

@Composable
fun LinguaBridgeRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopDestination.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.Today.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopDestination.Today.route) {
                TodayScreen(
                    onStartReview = { navController.navigate("review") },
                    onOpenDecks = { navController.navigate("decks") },
                )
            }
            composable("review") {
                SubScreen(stringResource(R.string.review_title), { navController.popBackStack() }) {
                    ReviewScreen(onFinished = { navController.popBackStack() })
                }
            }
            composable("decks") {
                SubScreen(stringResource(R.string.decks_title), { navController.popBackStack() }) {
                    DecksScreen()
                }
            }
            composable(TopDestination.Library.route) {
                LibraryScreen(
                    onOpenText = { textId -> navController.navigate("reader/$textId") },
                    onOpenDialogue = { dialogueId -> navController.navigate("dialogue/$dialogueId") },
                    onOpenHskGrammar = { navController.navigate("hskgrammar") },
                )
            }
            composable("hskgrammar") {
                SubScreen(stringResource(R.string.hskgrammar_title), { navController.popBackStack() }) {
                    HskGrammarScreen()
                }
            }
            composable(
                route = "reader/{textId}",
                arguments = listOf(navArgument("textId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val textId = backStackEntry.arguments?.getString("textId").orEmpty()
                ReaderScreen(textId = textId, onBack = { navController.popBackStack() })
            }
            composable(
                route = "dialogue/{dialogueId}",
                arguments = listOf(navArgument("dialogueId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val dialogueId = backStackEntry.arguments?.getString("dialogueId").orEmpty()
                DialogueScreen(dialogueId = dialogueId, onBack = { navController.popBackStack() })
            }
            composable(TopDestination.Practice.route) {
                PracticeScreen(
                    onOpenDictation = { navController.navigate("dictation") },
                    onOpenListening = { navController.navigate("listening") },
                    onOpenQuizzes = { navController.navigate("quizzes") },
                    onOpenDictionary = { navController.navigate("dictionary") },
                    onOpenWordGame = { navController.navigate("wordgame") },
                )
            }
            composable("dictionary") {
                SubScreen(stringResource(R.string.dictionary_title), { navController.popBackStack() }) {
                    DictionaryScreen()
                }
            }
            composable("wordgame") {
                SubScreen(stringResource(R.string.wordgame_title), { navController.popBackStack() }) {
                    WordGameScreen()
                }
            }
            // Dictation/listening/quiz/placement ship their own top bars, so
            // they are NOT wrapped in SubScreen (that would double the bar).
            composable("dictation") {
                DictationScreen(onBack = { navController.popBackStack() })
            }
            composable("listening") {
                ListeningScreen(onBack = { navController.popBackStack() })
            }
            composable("quizzes") {
                SubScreen(stringResource(R.string.quiz_list_title), { navController.popBackStack() }) {
                    QuizListScreen(
                        onOpenQuiz = { category -> navController.navigate("quiz/$category") },
                        onOpenPlacement = { navController.navigate("placement") },
                    )
                }
            }
            composable(
                route = "quiz/{category}",
                arguments = listOf(navArgument("category") { type = NavType.StringType }),
            ) { backStackEntry ->
                val category = backStackEntry.arguments?.getString("category").orEmpty()
                QuizSessionScreen(category = category, onBack = { navController.popBackStack() })
            }
            composable("placement") {
                PlacementScreen(onBack = { navController.popBackStack() })
            }
            composable(TopDestination.Stats.route) {
                StatsScreen()
            }
            composable(TopDestination.Settings.route) {
                SettingsScreen()
            }
        }
    }
}

/** Inner (non-tab) screens get a top bar with an explicit back arrow — the
 *  user should never depend on the system gesture alone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.review_back),
                    )
                }
            },
        )
        content()
    }
}
