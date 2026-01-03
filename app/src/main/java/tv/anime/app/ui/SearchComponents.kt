package tv.anime.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

internal data class VoiceSearchUiState(
    val isListening: Boolean,
    val errorMessage: String?
)

internal class VoiceSearchHandle(
    val state: VoiceSearchUiState,
    val onMicClick: () -> Unit
)

/**
 * Encapsulates in-app speech recognition state + permission handling.
 *
 * Keeps SearchScreen.kt focused on composition and load-state rendering.
 */
@Composable
internal fun rememberVoiceSearchHandle(
    onSpokenQuery: (String) -> Unit
): VoiceSearchHandle {
    val context = LocalContext.current

    var isListening by remember { mutableStateOf(false) }
    var lastVoiceError by remember { mutableStateOf<String?>(null) }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(speechRecognizer) {
        onDispose { speechRecognizer?.destroy() }
    }

    fun applySpokenQuery(spoken: String) {
        val s = spoken.trim()
        if (s.isNotEmpty()) onSpokenQuery(s)
    }

    LaunchedEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }

            override fun onBeginningOfSpeech() {
                isListening = true
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                lastVoiceError = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing microphone permission"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    else -> "Voice error"
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                applySpokenQuery(matches?.firstOrNull().orEmpty())
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isListening = true
            lastVoiceError = null
            speechRecognizer?.startListening(buildRecognizerIntent())
        } else {
            lastVoiceError = "Microphone permission denied"
        }
    }

    val onMicClick = remember(speechRecognizer) {
        {
            lastVoiceError = null
            if (speechRecognizer == null) {
                lastVoiceError = "Speech recognition not available on this device"
                return@remember
            }

            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                isListening = true
                speechRecognizer.startListening(buildRecognizerIntent())
            }
        }
    }

    return VoiceSearchHandle(
        state = VoiceSearchUiState(isListening = isListening, errorMessage = lastVoiceError),
        onMicClick = onMicClick
    )
}

/**
 * TV-friendly search bar:
 * - Does not open the IME on focus
 * - Opens editing mode (IME) only on OK/ENTER
 */
@Composable
internal fun TvSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search anime…",
    shellFocusRequester: FocusRequester? = null
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    val editingRequester = remember { FocusRequester() }

    // Search bar has two modes:
    // - idle: focusable "shell" (no IME)
    // - editing: BasicTextField (IME shows)
    var isEditing by remember { mutableStateOf(false) }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            editingRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    val iconTint = Color.White
    val placeholderColor = Color(0xFFBDBDBD)
    val fieldBg = Color(0x00000000)
    val fieldBorderIdle = Color(0xFF3A3A3A)
    val fieldBorderFocused = Color.White

    var isSearchFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(56.dp)
            .fillMaxWidth()
            .onFocusChanged { isSearchFocused = it.isFocused }
            .border(
                width = if (isSearchFocused || isEditing) 2.dp else 1.dp,
                color = if (isSearchFocused || isEditing) fieldBorderFocused else fieldBorderIdle
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Use a Surface to apply background color cleanly.
        Surface(
            color = fieldBg,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.matchParentSize()
        ) {}

        if (!isEditing) {
            // Idle shell: focusable, no IME on focus.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (shellFocusRequester != null) Modifier.focusRequester(shellFocusRequester) else Modifier)
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        if (
                            e.type == KeyEventType.KeyDown &&
                            (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)
                        ) {
                            isEditing = true
                            true
                        } else {
                            false
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = iconTint)
                Spacer(Modifier.width(10.dp))

                val text = query.ifBlank { placeholder }
                Text(
                    text = text,
                    color = if (query.isBlank()) placeholderColor else Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                AnimatedVisibility(
                    visible = query.isNotBlank(),
                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
                ) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = iconTint)
                    }
                }
            }
        } else {
            // Editing: BasicTextField with explicit white styling.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { e ->
                        // Back exits editing without leaving the screen
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Back) {
                            isEditing = false
                            true
                        } else false
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = iconTint)
                Spacer(Modifier.width(10.dp))

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(editingRequester),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onSearch()
                            isEditing = false
                        }
                    ),
                    decorationBox = { inner ->
                        if (query.isBlank()) {
                            Text(
                                text = placeholder,
                                color = placeholderColor,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        inner()
                    }
                )

                AnimatedVisibility(
                    visible = query.isNotBlank(),
                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
                ) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = iconTint)
                    }
                }
            }
        }
    }
}

private fun buildRecognizerIntent(): Intent {
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        // Prefer on-device / built-in recognition when available.
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Search anime")
    }
}
