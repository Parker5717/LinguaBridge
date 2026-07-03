package com.linguabridge.app.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguabridge.app.LinguaBridgeApp
import com.linguabridge.app.R
import com.linguabridge.app.notifications.Notifications
import com.linguabridge.app.notifications.WordNotifications
import com.linguabridge.app.tts.TtsLanguage
import com.linguabridge.app.tts.VoiceStatus
import kotlinx.coroutines.launch

private const val TEST_EN_SENTENCE = "This is a test of the English voice."
private const val TEST_ZH_SENTENCE = "你好！我是你的中文老师。"

@Composable
fun SettingsScreen() {
    val app = LocalContext.current.applicationContext as LinguaBridgeApp
    val settingsRepository = app.container.settingsRepository
    val ttsManager = app.container.ttsManager
    val scope = rememberCoroutineScope()

    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val ttsState by ttsManager.state.collectAsStateWithLifecycle()

    val current = settings ?: return

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val context = LocalContext.current
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)

        // --- Notifications ----------------------------------------------------------------
        SettingsSection(stringResource(R.string.settings_section_notifications)) {
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    scope.launch {
                        settingsRepository.setWordNotificationsEnabled(true)
                        WordNotifications.scheduleNext(context)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_word_notifications),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.settings_word_notifications_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = current.wordNotificationsEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (Notifications.canPost(context)) {
                                scope.launch {
                                    settingsRepository.setWordNotificationsEnabled(true)
                                    WordNotifications.scheduleNext(context)
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            scope.launch {
                                settingsRepository.setWordNotificationsEnabled(false)
                                WordNotifications.cancel(context)
                            }
                        }
                    },
                )
            }
        }

        HorizontalDivider()

        // --- Speech ---------------------------------------------------------------------
        SettingsSection(stringResource(R.string.settings_section_speech)) {
            var rateSlider by remember(current.ttsRate) { mutableFloatStateOf(current.ttsRate) }
            Text(
                stringResource(R.string.settings_speech_rate, rateSlider),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = rateSlider,
                onValueChange = { rateSlider = it },
                onValueChangeFinished = {
                    scope.launch { settingsRepository.setTtsRate(rateSlider) }
                },
                valueRange = 0.7f..1.2f,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { ttsManager.speak(TEST_EN_SENTENCE, TtsLanguage.ENGLISH, rateSlider) }) {
                    Text(stringResource(R.string.settings_test_english))
                }
                OutlinedButton(onClick = { ttsManager.speak(TEST_ZH_SENTENCE, TtsLanguage.CHINESE, rateSlider) }) {
                    Text(stringResource(R.string.settings_test_chinese))
                }
            }
            Spacer(Modifier.height(12.dp))
            VoiceStatusLine(stringResource(R.string.settings_voice_english), ttsState.english)
            VoiceStatusLine(stringResource(R.string.settings_voice_chinese), ttsState.chinese)

            if (ttsState.chinese == VoiceStatus.MISSING_DATA || ttsState.chinese == VoiceStatus.UNSUPPORTED) {
                Spacer(Modifier.height(8.dp))
                ChineseVoiceWarningCard(onRecheck = ttsManager::refreshVoices)
            }
        }

        HorizontalDivider()

        // --- Study ------------------------------------------------------------------------
        SettingsSection(stringResource(R.string.settings_section_study)) {
            var newPerDay by remember(current.newCardsPerDay) { mutableIntStateOf(current.newCardsPerDay) }
            Text(
                stringResource(R.string.settings_new_cards_per_day, newPerDay),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = newPerDay.toFloat(),
                onValueChange = { newPerDay = it.toInt() },
                onValueChangeFinished = {
                    scope.launch { settingsRepository.setNewCardsPerDay(newPerDay) }
                },
                valueRange = 0f..100f,
                steps = 99,
            )

            Spacer(Modifier.height(12.dp))

            var maxReviews by remember(current.maxReviewsPerDay) { mutableIntStateOf(current.maxReviewsPerDay) }
            Text(
                stringResource(R.string.settings_max_reviews_per_day, maxReviews),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = maxReviews.toFloat(),
                onValueChange = { maxReviews = it.toInt() },
                onValueChangeFinished = {
                    scope.launch { settingsRepository.setMaxReviewsPerDay(maxReviews) }
                },
                valueRange = 10f..500f,
                steps = 48,
            )
        }

        HorizontalDivider()

        // --- Appearance ---------------------------------------------------------------------
        SettingsSection(stringResource(R.string.settings_section_appearance)) {
            ThemeOption(
                label = stringResource(R.string.settings_theme_system),
                selected = current.theme == "system",
                onClick = { scope.launch { settingsRepository.setTheme("system") } },
            )
            ThemeOption(
                label = stringResource(R.string.settings_theme_light),
                selected = current.theme == "light",
                onClick = { scope.launch { settingsRepository.setTheme("light") } },
            )
            ThemeOption(
                label = stringResource(R.string.settings_theme_dark),
                selected = current.theme == "dark",
                onClick = { scope.launch { settingsRepository.setTheme("dark") } },
            )
        }

        HorizontalDivider()

        // --- Language ---------------------------------------------------------------------
        SettingsSection(stringResource(R.string.settings_section_language)) {
            var appLocales by remember { mutableStateOf(AppCompatDelegate.getApplicationLocales()) }
            val currentTag = appLocales.toLanguageTags().substringBefore(',').ifEmpty { "" }

            LanguageOption(
                label = stringResource(R.string.settings_language_system),
                selected = currentTag.isEmpty(),
                onClick = {
                    val locales = LocaleListCompat.forLanguageTags("")
                    AppCompatDelegate.setApplicationLocales(locales)
                    appLocales = locales
                },
            )
            LanguageOption(
                label = stringResource(R.string.settings_language_english),
                selected = currentTag == "en",
                onClick = {
                    val locales = LocaleListCompat.forLanguageTags("en")
                    AppCompatDelegate.setApplicationLocales(locales)
                    appLocales = locales
                },
            )
            LanguageOption(
                label = stringResource(R.string.settings_language_russian),
                selected = currentTag == "ru",
                onClick = {
                    val locales = LocaleListCompat.forLanguageTags("ru")
                    AppCompatDelegate.setApplicationLocales(locales)
                    appLocales = locales
                },
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun VoiceStatusLine(label: String, status: VoiceStatus) {
    val (textRes, color) = when (status) {
        VoiceStatus.READY -> R.string.settings_voice_status_ready to MaterialTheme.colorScheme.primary
        VoiceStatus.MISSING_DATA -> R.string.settings_voice_status_missing to MaterialTheme.colorScheme.error
        VoiceStatus.UNSUPPORTED -> R.string.settings_voice_status_unsupported to MaterialTheme.colorScheme.error
        VoiceStatus.CHECKING -> R.string.settings_voice_status_checking to MaterialTheme.colorScheme.outline
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$label:", style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(textRes), style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun ChineseVoiceWarningCard(onRecheck: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    stringResource(R.string.settings_chinese_voice_warning_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                stringResource(R.string.settings_chinese_voice_warning_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            OutlinedButton(onClick = onRecheck) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.settings_recheck))
            }
        }
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
