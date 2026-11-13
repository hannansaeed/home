package com.example.chatapp.ui.moth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.ui.theme.*
import kotlin.random.Random

private enum class MiniGameTab(val label: String) {
    WORDLE("wordle"),
    TOD("tod"),
    UNRAVEL("unravel"),
}

@Composable
fun MothMiniGameView() {
    var activeGame by remember { mutableStateOf(MiniGameTab.WORDLE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MothBgDark)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("games_hub"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Stepped Game Switcher (Wordle, TOD, Unravel)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiniGameTab.entries.forEach { tab ->
                val selected = activeGame == tab
                SteppedButton(
                    text = tab.label,
                    onClick = { activeGame = tab },
                    backgroundColor = if (selected) MyBubbleFill else MothHeaderBg,
                    borderColor = if (selected) MyBubbleBorder else TheirBubbleBorder,
                    textColor = if (selected) MyBubbleText else TheirBubbleText,
                    cutSize = 3.dp,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("game_tab_${tab.label}"),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when (activeGame) {
                MiniGameTab.WORDLE -> WordleGame()
                MiniGameTab.TOD -> TruthOrDareGame()
                MiniGameTab.UNRAVEL -> UnravelGame()
            }
        }
    }
}

// =========================================================================
// 1. WORDLE
// =========================================================================

private enum class WordleTileState {
    EMPTY,
    TYPING,
    ABSENT,
    PRESENT,
    CORRECT,
}

private val WORDLE_WORDS = listOf(
    "LIGHT", "NIGHT", "SPARK", "EMBER", "FLAME", "LUNAR", "ORBIT", "CLOUD", "STORM", "FROST",
    "BLAZE", "SHINE", "GLEAM", "DREAM", "SLEEP", "QUIET", "FLARE",
    "PIXEL", "GHOST", "WITCH", "MAGIC", "CHARM", "CROWN", "SWORD", "CREST",
    "AMBER", "PEARL", "CORAL", "IVORY", "SLATE", "BRICK", "STONE", "RIVER", "OCEAN", "SHORE",
    "WAVES", "TRAIL", "TRAIT", "TRAIN", "BRAIN", "GRAIN", "STAIN", "CHAIN", "PLAIN",
    "CRANE", "FLINT", "GLINT", "PRINT", "MINTY", "SPICE", "SLICE", "PRICE", "TWICE", "VOICE",
    "HEART", "START", "SMART", "CHART", "APART", "MUSIC", "SOUND", "ROUND", "BOUND", "FOUND",
    "HOUND", "POUND", "MOUND", "WOUND", "BLOOM", "GLOOM", "BROOM", "ROOMY", "SPOON",
    "STARS", "COMET", "SOLAR", "PRISM", "PULSE", "WATCH", "MATCH", "CATCH", "LATCH", "PATCH",
    "CRISP", "WISPS", "SWIFT", "DRIFT", "SHIFT", "FROWN", "DROWN", "CLOWN",
    "BROWN", "SMILE", "WHILE", "STYLE", "THYME", "RHYME", "CHIME", "PRIME", "CRIME", "CLIMB",
    "BLINK", "DRINK", "THINK", "STINK", "BRINK", "PLUME", "FLUME", "CHESS", "QUEST",
    "FEAST", "BEAST", "LEAST", "YEAST", "ROAST", "COAST", "TOAST", "BOAST", "PLANT", "GIANT",
    "SLANT", "CHANT", "GRANT", "BRAVE", "GRAVE", "CRAVE", "SHAVE", "STAGE", "BLADE", "SHADE",
    "TRADE", "GRADE", "SPADE", "FLUTE", "CHUTE", "ROUTE", "YOUTH", "TRUTH", "SOUTH", "NORTH",
    "EARTH", "WORTH", "BIRTH", "MIRTH", "GIRTH", "THORN", "SCORN", "ACORN", "FROZE", "GLAZE",
    "CRAZE", "MAIZE", "PRIZE", "TREES", "VINES", "WINDS", "DUSKS", "DAWNS", "MOTHS",
).filter { it.length == 5 }.distinct()

private fun evaluateWordleGuess(guess: String, target: String): List<WordleTileState> {
    val result = MutableList(5) { WordleTileState.ABSENT }
    val remaining = IntArray(26)

    for (i in 0 until 5) {
        if (guess[i] == target[i]) {
            result[i] = WordleTileState.CORRECT
        } else {
            val idx = target[i] - 'A'
            if (idx in 0..25) remaining[idx]++
        }
    }

    for (i in 0 until 5) {
        if (result[i] == WordleTileState.CORRECT) continue
        val idx = guess[i] - 'A'
        if (idx in 0..25 && remaining[idx] > 0) {
            result[i] = WordleTileState.PRESENT
            remaining[idx]--
        }
    }
    return result
}

@Composable
private fun WordleGame() {
    var target by remember { mutableStateOf(WORDLE_WORDS.random()) }
    var guesses by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentGuess by remember { mutableStateOf("") }
    var streak by remember { mutableIntStateOf(0) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val won = guesses.lastOrNull() == target
    val lost = !won && guesses.size >= 6
    val gameOver = won || lost

    fun resetRound(resetStreak: Boolean = false) {
        if (resetStreak) streak = 0
        target = WORDLE_WORDS.random()
        guesses = emptyList()
        currentGuess = ""
        statusMsg = null
    }

    fun submitGuess() {
        if (gameOver) return
        if (currentGuess.length < 5) {
            statusMsg = "need 5 letters"
            return
        }
        val nextGuesses = guesses + currentGuess
        guesses = nextGuesses
        val submitted = currentGuess
        currentGuess = ""
        statusMsg = null

        if (submitted == target) {
            streak += 1
            statusMsg = "cracked in ${nextGuesses.size}/6!"
        } else if (nextGuesses.size >= 6) {
            streak = 0
            statusMsg = "word was $target"
        }
    }

    // Best known state per keyboard letter
    val keyStates = remember(guesses, target) {
        val map = mutableMapOf<Char, WordleTileState>()
        for (g in guesses) {
            val eval = evaluateWordleGuess(g, target)
            for (i in 0 until 5) {
                val ch = g[i]
                val prev = map[ch]
                val next = eval[i]
                val rank = { s: WordleTileState? ->
                    when (s) {
                        WordleTileState.CORRECT -> 3
                        WordleTileState.PRESENT -> 2
                        WordleTileState.ABSENT -> 1
                        else -> 0
                    }
                }
                if (rank(next) > rank(prev)) {
                    map[ch] = next
                }
            }
        }
        map
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "wordle",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = LanternAmber,
                )
                Text(
                    text = "guess the 5-letter word in 6 tries",
                    fontFamily = PixelifySans,
                    fontSize = 11.sp,
                    color = MutedText,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SteppedBox(
                    backgroundColor = TheirBubbleFill,
                    borderColor = TheirBubbleBorder,
                    cutSize = 3.dp,
                ) {
                    Text(
                        text = "streak: $streak",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = LanternAmber,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                SteppedButton(
                    text = "new",
                    onClick = { resetRound(resetStreak = !won && guesses.isNotEmpty() && !lost) },
                    backgroundColor = MothHeaderBg,
                    borderColor = TheirBubbleBorder,
                    textColor = TheirBubbleText,
                    cutSize = 3.dp,
                )
            }
        }

        if (statusMsg != null) {
            SteppedBox(
                backgroundColor = TheirBubbleFill,
                borderColor = if (won) SecureGreen else if (lost) WaxSealRed else LanternAmber,
                cutSize = 3.dp,
            ) {
                Text(
                    text = statusMsg ?: "",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (won) SecureGreen else if (lost) WaxSealRed else LanternAmber,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }

        // 6x5 Wordle Grid
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (rowIdx in 0 until 6) {
                val rowWord = when {
                    rowIdx < guesses.size -> guesses[rowIdx]
                    rowIdx == guesses.size -> currentGuess
                    else -> ""
                }
                val rowStates = if (rowIdx < guesses.size) {
                    evaluateWordleGuess(rowWord, target)
                } else {
                    List(5) { idx ->
                        if (idx < rowWord.length) WordleTileState.TYPING else WordleTileState.EMPTY
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (colIdx in 0 until 5) {
                        val letter = rowWord.getOrNull(colIdx)?.toString() ?: ""
                        val st = rowStates[colIdx]
                        val (bg, border, fg) = when (st) {
                            WordleTileState.CORRECT -> Triple(SecureGreen, Color(0xFF4CA877), MothBgDark)
                            WordleTileState.PRESENT -> Triple(LanternAmber, MyBubbleBorder, MyBubbleText)
                            WordleTileState.ABSENT -> Triple(MothDivider, Color(0xFF2B2455), MutedText)
                            WordleTileState.TYPING -> Triple(TheirBubbleFill, LanternAmber, TheirBubbleText)
                            WordleTileState.EMPTY -> Triple(MothHeaderBg, MothDivider, TheirBubbleText)
                        }

                        SteppedBox(
                            modifier = Modifier.size(46.dp),
                            backgroundColor = bg,
                            borderColor = border,
                            cutSize = 3.dp,
                            borderWidth = 2.dp,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = letter,
                                    fontFamily = PixelifySans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = fg,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (gameOver) {
            SteppedButton(
                text = "play next word",
                onClick = { resetRound() },
                backgroundColor = MyBubbleFill,
                borderColor = MyBubbleBorder,
                textColor = MyBubbleText,
                cutSize = 4.dp,
            )
        }

        // Pixel QWERTY Keyboard
        val keyboardRows = listOf(
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            listOf("ENTER", "Z", "X", "C", "V", "B", "N", "M", "⌫"),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            keyboardRows.forEach { rowKeys ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                ) {
                    rowKeys.forEach { key ->
                        val isAction = key == "ENTER" || key == "⌫"
                        val keyState = if (!isAction) keyStates[key[0]] else null
                        val (bg, border, fg) = when (keyState) {
                            WordleTileState.CORRECT -> Triple(SecureGreen, Color(0xFF4CA877), MothBgDark)
                            WordleTileState.PRESENT -> Triple(LanternAmber, MyBubbleBorder, MyBubbleText)
                            WordleTileState.ABSENT -> Triple(MothBgDark, MothDivider, PlaceholderText)
                            else -> Triple(
                                if (key == "ENTER") MyBubbleFill else MothHeaderBg,
                                if (key == "ENTER") MyBubbleBorder else TheirBubbleBorder,
                                if (key == "ENTER") MyBubbleText else TheirBubbleText,
                            )
                        }

                        SteppedBox(
                            modifier = Modifier
                                .height(40.dp)
                                .then(
                                    if (isAction) Modifier.weight(1.5f) else Modifier.weight(1f)
                                )
                                .clickable {
                                    if (gameOver) return@clickable
                                    when (key) {
                                        "ENTER" -> submitGuess()
                                        "⌫" -> {
                                            if (currentGuess.isNotEmpty()) {
                                                currentGuess = currentGuess.dropLast(1)
                                                statusMsg = null
                                            }
                                        }
                                        else -> {
                                            if (currentGuess.length < 5) {
                                                currentGuess += key
                                                statusMsg = null
                                            }
                                        }
                                    }
                                }
                                .testTag("wordle_key_$key"),
                            backgroundColor = bg,
                            borderColor = border,
                            cutSize = 2.dp,
                            borderWidth = 1.dp,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (key == "ENTER") "GO" else key,
                                    fontFamily = PixelifySans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (isAction) 11.sp else 13.sp,
                                    color = fg,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 2. TOD (Truth or Dare)
// =========================================================================

private data class TodPrompt(
    val type: String, // "TRUTH" or "DARE"
    val text: String,
)

private val DEFAULT_TRUTHS = listOf(
    "What is a secret hobby nobody in your contacts knows about?",
    "Who was the last person you searched for online?",
    "What message did you type out recently and delete before sending?",
    "What is the pettiest reason you stopped talking to someone?",
    "If everyone in this chat read your camera roll, which photo would be hardest to explain?",
    "What is one thing you pretend to understand just to fit in?",
    "Have you ever blamed someone else for something you definitely did?",
    "What is your most irrational 2am fear?",
    "Which friend do you think would survive the longest in a zombie apocalypse, and why?",
    "What is the weirdest dream you remember vividly?",
    "If you had to delete every app except three, which three stay?",
    "What compliment do you still think about years later?",
)

private val DEFAULT_DARES = listOf(
    "Send a voice note or text saying 'the moth has landed' to the last person you texted.",
    "Draw the weirdest sticker you can in under 15 seconds and send it to a friend.",
    "Let the next player pick a word you cannot say for the next 3 rounds.",
    "Speak only in questions for the next two minutes.",
    "Do your best impression of a movie villain right now.",
    "Show the last photo you saved to your gallery.",
    "Hold a straight face while everyone tries to make you laugh for 20 seconds.",
    "Type a glowing 2-sentence review of your favorite snack and send it in a chat.",
    "Change your lantern hue in settings for the rest of the night.",
    "Describe your day using only 4 words and no punctuation.",
    "Balance something light on your head until the next turn finishes.",
    "Tell a completely made-up fun fact as convincingly as possible.",
)

@Composable
private fun TruthOrDareGame() {
    var players by remember { mutableStateOf(listOf("player 1", "player 2")) }
    var currentPlayerIdx by remember { mutableIntStateOf(0) }
    var newPlayerInput by remember { mutableStateOf("") }
    var activePrompt by remember { mutableStateOf<TodPrompt?>(null) }
    var completedCount by remember { mutableIntStateOf(0) }
    var skippedCount by remember { mutableIntStateOf(0) }

    var customPromptText by remember { mutableStateOf("") }
    var customTruths by remember { mutableStateOf<List<String>>(emptyList()) }
    var customDares by remember { mutableStateOf<List<String>>(emptyList()) }

    val usedTruths = remember { mutableStateListOf<String>() }
    val usedDares = remember { mutableStateListOf<String>() }

    fun drawPrompt(type: String) {
        val pool = if (type == "TRUTH") DEFAULT_TRUTHS + customTruths else DEFAULT_DARES + customDares
        val used = if (type == "TRUTH") usedTruths else usedDares
        var candidates = pool.filterNot { it in used }
        if (candidates.isEmpty()) {
            used.clear()
            candidates = pool
        }
        val picked = candidates.random()
        used.add(picked)
        activePrompt = TodPrompt(type, picked)
    }

    fun nextTurn(didComplete: Boolean) {
        if (didComplete) completedCount++ else skippedCount++
        if (players.isNotEmpty()) {
            currentPlayerIdx = (currentPlayerIdx + 1) % players.size
        }
        activePrompt = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "truth or dare",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = LanternAmber,
                )
                Text(
                    text = "pick a card or spin fate",
                    fontFamily = PixelifySans,
                    fontSize = 11.sp,
                    color = MutedText,
                )
            }
            SteppedBox(
                backgroundColor = TheirBubbleFill,
                borderColor = TheirBubbleBorder,
                cutSize = 3.dp,
            ) {
                Text(
                    text = "done: $completedCount • skip: $skippedCount",
                    fontFamily = PixelifySans,
                    fontSize = 11.sp,
                    color = TheirBubbleText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        // Players Row
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                players.forEachIndexed { idx, name ->
                    val isCurrent = idx == (currentPlayerIdx % players.size.coerceAtLeast(1))
                    SteppedBox(
                        modifier = Modifier.clickable {
                            currentPlayerIdx = idx
                        },
                        backgroundColor = if (isCurrent) MyBubbleFill else MothHeaderBg,
                        borderColor = if (isCurrent) MyBubbleBorder else TheirBubbleBorder,
                        cutSize = 3.dp,
                        borderWidth = 1.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = name,
                                fontFamily = PixelifySans,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                color = if (isCurrent) MyBubbleText else TheirBubbleText,
                            )
                            if (players.size > 1) {
                                Text(
                                    text = "✕",
                                    fontSize = 10.sp,
                                    color = if (isCurrent) MyBubbleText else MutedText,
                                    modifier = Modifier.clickable {
                                        players = players.toMutableList().apply { removeAt(idx) }
                                        if (currentPlayerIdx >= players.size) currentPlayerIdx = 0
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Quick add player
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SteppedBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    backgroundColor = MothHeaderBg,
                    borderColor = MothDivider,
                    cutSize = 3.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (newPlayerInput.isEmpty()) {
                            Text(
                                text = "add player name...",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = PlaceholderText,
                            )
                        }
                        BasicTextField(
                            value = newPlayerInput,
                            onValueChange = { newPlayerInput = it },
                            textStyle = TextStyle(
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = TheirBubbleText,
                            ),
                            cursorBrush = SolidColor(LanternAmber),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                SteppedButton(
                    text = "+ player",
                    onClick = {
                        val clean = newPlayerInput.trim()
                        if (clean.isNotEmpty()) {
                            players = players + clean
                            newPlayerInput = ""
                        }
                    },
                    backgroundColor = TheirBubbleFill,
                    borderColor = TheirBubbleBorder,
                    textColor = TheirBubbleText,
                    cutSize = 3.dp,
                )
            }
        }

        // Current Turn Card
        val currentPlayer = players.getOrElse(currentPlayerIdx % players.size.coerceAtLeast(1)) { "player 1" }
        SteppedBox(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MothHeaderBg,
            borderColor = if (activePrompt?.type == "DARE") WaxSealRed else LanternAmber,
            cutSize = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "$currentPlayer's turn",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = LanternAmber,
                )

                val prompt = activePrompt
                if (prompt == null) {
                    Text(
                        text = "choose TRUTH, DARE, or let fate decide",
                        fontFamily = PixelifySans,
                        fontSize = 13.sp,
                        color = MutedText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                } else {
                    SteppedBox(
                        backgroundColor = if (prompt.type == "DARE") WaxSealRed else TheirBubbleFill,
                        borderColor = if (prompt.type == "DARE") WaxSealDarkEdge else TheirBubbleBorder,
                        cutSize = 3.dp,
                    ) {
                        Text(
                            text = prompt.type,
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }

                    Text(
                        text = prompt.text,
                        fontFamily = PixelifySans,
                        fontSize = 15.sp,
                        color = TheirBubbleText,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }

        // Action Buttons
        if (activePrompt == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SteppedButton(
                    text = "TRUTH",
                    onClick = { drawPrompt("TRUTH") },
                    backgroundColor = TheirBubbleFill,
                    borderColor = TheirBubbleBorder,
                    textColor = TheirBubbleText,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tod_truth_button"),
                    cutSize = 4.dp,
                )
                SteppedButton(
                    text = "DARE",
                    onClick = { drawPrompt("DARE") },
                    backgroundColor = WaxSealRed,
                    borderColor = WaxSealDarkEdge,
                    textColor = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tod_dare_button"),
                    cutSize = 4.dp,
                )
            }
            SteppedButton(
                text = "✦ RANDOM SPIN",
                onClick = {
                    drawPrompt(if (Random.nextBoolean()) "TRUTH" else "DARE")
                },
                backgroundColor = MyBubbleFill,
                borderColor = MyBubbleBorder,
                textColor = MyBubbleText,
                modifier = Modifier.fillMaxWidth(),
                cutSize = 4.dp,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SteppedButton(
                    text = "skip turn",
                    onClick = { nextTurn(didComplete = false) },
                    backgroundColor = TheirBubbleFill,
                    borderColor = MothDivider,
                    textColor = MutedText,
                    modifier = Modifier.weight(1f),
                    cutSize = 4.dp,
                )
                SteppedButton(
                    text = "done ✓",
                    onClick = { nextTurn(didComplete = true) },
                    backgroundColor = SecureGreen,
                    borderColor = Color(0xFF4CA877),
                    textColor = MothBgDark,
                    modifier = Modifier.weight(1f),
                    cutSize = 4.dp,
                )
            }
        }

        // Add custom Truth / Dare card
        SteppedBox(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MothHeaderBg,
            borderColor = MothDivider,
            cutSize = 3.dp,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "add your own card",
                    fontFamily = PixelifySans,
                    fontSize = 12.sp,
                    color = MutedText,
                )
                SteppedBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    backgroundColor = MothBgDark,
                    borderColor = TheirBubbleBorder,
                    cutSize = 3.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (customPromptText.isEmpty()) {
                            Text(
                                text = "write a custom truth or dare...",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = PlaceholderText,
                            )
                        }
                        BasicTextField(
                            value = customPromptText,
                            onValueChange = { customPromptText = it },
                            textStyle = TextStyle(
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = TheirBubbleText,
                            ),
                            cursorBrush = SolidColor(LanternAmber),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SteppedButton(
                        text = "+ truth",
                        onClick = {
                            if (customPromptText.isNotBlank()) {
                                customTruths = customTruths + customPromptText.trim()
                                customPromptText = ""
                            }
                        },
                        backgroundColor = TheirBubbleFill,
                        borderColor = TheirBubbleBorder,
                        textColor = TheirBubbleText,
                        modifier = Modifier.weight(1f),
                        cutSize = 3.dp,
                    )
                    SteppedButton(
                        text = "+ dare",
                        onClick = {
                            if (customPromptText.isNotBlank()) {
                                customDares = customDares + customPromptText.trim()
                                customPromptText = ""
                            }
                        },
                        backgroundColor = WaxSealRed,
                        borderColor = WaxSealDarkEdge,
                        textColor = Color.White,
                        modifier = Modifier.weight(1f),
                        cutSize = 3.dp,
                    )
                }
            }
        }
    }
}

// =========================================================================
// 3. UNRAVEL (Word Unscramble Puzzle)
// =========================================================================

private data class UnravelPuzzle(
    val word: String,
    val clue: String,
)

private val UNRAVEL_PUZZLES = listOf(
    UnravelPuzzle("LANTERN", "glows in the dark"),
    UnravelPuzzle("ECLIPSE", "moon hides the sun"),
    UnravelPuzzle("STARLIGHT", "twinkles across the sky"),
    UnravelPuzzle("COMPASS", "points north"),
    UnravelPuzzle("CIPHER", "secret message code"),
    UnravelPuzzle("THUNDER", "follows lightning"),
    UnravelPuzzle("FIREFLY", "glowing night bug"),
    UnravelPuzzle("HORIZON", "where sky meets earth"),
    UnravelPuzzle("WHISPER", "quiet spoken secret"),
    UnravelPuzzle("CRYSTAL", "clear geometric stone"),
    UnravelPuzzle("NEBULA", "cloud of star dust"),
    UnravelPuzzle("ORIGAMI", "folded paper art"),
    UnravelPuzzle("GRAVITY", "keeps feet on ground"),
    UnravelPuzzle("PHANTOM", "spooky night spirit"),
    UnravelPuzzle("VELVET", "soft midnight fabric"),
    UnravelPuzzle("MIRAGE", "desert optical illusion"),
    UnravelPuzzle("GALAXY", "billions of stars"),
    UnravelPuzzle("BEACON", "guiding tower light"),
    UnravelPuzzle("SHIMMER", "soft trembling glow"),
    UnravelPuzzle("MYSTERY", "unsolved puzzle"),
)

private fun scrambleDistinct(word: String): List<Pair<Int, Char>> {
    val base = word.mapIndexed { idx, c -> idx to c }
    var shuffled = base.shuffled()
    var attempts = 0
    while (shuffled.map { it.second }.joinToString("") == word && attempts < 10) {
        shuffled = base.shuffled()
        attempts++
    }
    return shuffled
}

@Composable
private fun UnravelGame() {
    var puzzle by remember { mutableStateOf(UNRAVEL_PUZZLES.random()) }
    var trayTiles by remember { mutableStateOf(scrambleDistinct(puzzle.word)) }
    // Slots hold index into trayTiles (or null if empty)
    var placedIndices by remember {
        mutableStateOf<List<Int?>>(List(puzzle.word.length) { null })
    }
    var score by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var solved by remember { mutableStateOf(false) }

    fun loadNewPuzzle() {
        val next = UNRAVEL_PUZZLES.filter { it.word != puzzle.word }.random()
        puzzle = next
        trayTiles = scrambleDistinct(next.word)
        placedIndices = List(next.word.length) { null }
        feedback = null
        solved = false
    }

    fun checkAnswer(currentSlots: List<Int?>) {
        if (currentSlots.any { it == null }) return
        val formed = currentSlots.mapNotNull { idx -> idx?.let { trayTiles[it].second } }.joinToString("")
        if (formed == puzzle.word) {
            solved = true
            score += 10
            streak += 1
            feedback = "unraveled! +10 pts"
        } else {
            streak = 0
            feedback = "not quite — try rearranging"
        }
    }

    fun tapTrayTile(trayIdx: Int) {
        if (solved || trayIdx in placedIndices) return
        val firstEmpty = placedIndices.indexOfFirst { it == null }
        if (firstEmpty == -1) return
        val nextSlots = placedIndices.toMutableList().apply { this[firstEmpty] = trayIdx }
        placedIndices = nextSlots
        feedback = null
        if (nextSlots.none { it == null }) {
            checkAnswer(nextSlots)
        }
    }

    fun tapAnswerSlot(slotIdx: Int) {
        if (solved || placedIndices[slotIdx] == null) return
        val nextSlots = placedIndices.toMutableList().apply { this[slotIdx] = null }
        placedIndices = nextSlots
        feedback = null
    }

    fun useHint() {
        if (solved) return
        val targetSlot = placedIndices.indices.firstOrNull { slotIdx ->
            val currentTile = placedIndices[slotIdx]?.let { trayTiles[it].second }
            currentTile != puzzle.word[slotIdx]
        } ?: return

        val neededChar = puzzle.word[targetSlot]
        // Find an unplaced tray tile matching neededChar (or steal from a wrong slot)
        val availableTrayIdx = trayTiles.indices.firstOrNull { idx ->
            trayTiles[idx].second == neededChar && idx !in placedIndices
        } ?: trayTiles.indices.firstOrNull { idx ->
            trayTiles[idx].second == neededChar
        } ?: return

        val nextSlots = placedIndices.toMutableList()
        val oldSlotOfTile = nextSlots.indexOf(availableTrayIdx)
        if (oldSlotOfTile != -1) nextSlots[oldSlotOfTile] = null
        nextSlots[targetSlot] = availableTrayIdx
        placedIndices = nextSlots
        feedback = "hint placed '${neededChar}'"
        if (nextSlots.none { it == null }) {
            checkAnswer(nextSlots)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "unravel",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = LanternAmber,
                )
                Text(
                    text = "unscramble the letters to solve the clue",
                    fontFamily = PixelifySans,
                    fontSize = 11.sp,
                    color = MutedText,
                )
            }
            SteppedBox(
                backgroundColor = TheirBubbleFill,
                borderColor = TheirBubbleBorder,
                cutSize = 3.dp,
            ) {
                Text(
                    text = "pts: $score • streak: $streak",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = LanternAmber,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        // Clue Box
        SteppedBox(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MothHeaderBg,
            borderColor = TheirBubbleBorder,
            cutSize = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "CLUE",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MutedText,
                )
                Text(
                    text = puzzle.clue,
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TheirBubbleText,
                )
            }
        }

        // Answer Slots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        ) {
            for (slotIdx in puzzle.word.indices) {
                val trayIdx = placedIndices[slotIdx]
                val charText = trayIdx?.let { trayTiles[it].second.toString() } ?: ""
                val bg = when {
                    solved -> SecureGreen
                    trayIdx != null -> MyBubbleFill
                    else -> MothBgDark
                }
                val border = when {
                    solved -> Color(0xFF4CA877)
                    trayIdx != null -> MyBubbleBorder
                    else -> TheirBubbleBorder
                }
                val fg = when {
                    solved -> MothBgDark
                    trayIdx != null -> MyBubbleText
                    else -> MutedText
                }

                SteppedBox(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { tapAnswerSlot(slotIdx) }
                        .testTag("unravel_slot_$slotIdx"),
                    backgroundColor = bg,
                    borderColor = border,
                    cutSize = 3.dp,
                    borderWidth = 2.dp,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = charText,
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = fg,
                        )
                    }
                }
            }
        }

        if (feedback != null) {
            Text(
                text = feedback ?: "",
                fontFamily = PixelifySans,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (solved) SecureGreen else LanternAmber,
            )
        }

        // Scrambled Letter Tray
        SteppedBox(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MothHeaderBg,
            borderColor = MothDivider,
            cutSize = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "tap letters to place • tap slot to remove",
                    fontFamily = PixelifySans,
                    fontSize = 11.sp,
                    color = MutedText,
                )

                val chunkSize = 5
                trayTiles.indices.chunked(chunkSize).forEach { rowIndices ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        rowIndices.forEach { trayIdx ->
                            val isUsed = trayIdx in placedIndices
                            SteppedBox(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clickable(enabled = !isUsed && !solved) {
                                        tapTrayTile(trayIdx)
                                    }
                                    .testTag("unravel_tray_$trayIdx"),
                                backgroundColor = if (isUsed) MothBgDark else TheirBubbleFill,
                                borderColor = if (isUsed) MothDivider else TheirBubbleBorder,
                                cutSize = 3.dp,
                                borderWidth = 2.dp,
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (isUsed) "" else trayTiles[trayIdx].second.toString(),
                                        fontFamily = PixelifySans,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = TheirBubbleText,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Game Controls
        if (solved) {
            SteppedButton(
                text = "next puzzle ➜",
                onClick = { loadNewPuzzle() },
                backgroundColor = MyBubbleFill,
                borderColor = MyBubbleBorder,
                textColor = MyBubbleText,
                modifier = Modifier.fillMaxWidth(),
                cutSize = 4.dp,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SteppedButton(
                    text = "clear",
                    onClick = {
                        placedIndices = List(puzzle.word.length) { null }
                        feedback = null
                    },
                    backgroundColor = TheirBubbleFill,
                    borderColor = MothDivider,
                    textColor = TheirBubbleText,
                    modifier = Modifier.weight(1f),
                    cutSize = 3.dp,
                )
                SteppedButton(
                    text = "hint",
                    onClick = { useHint() },
                    backgroundColor = TheirBubbleFill,
                    borderColor = LanternAmber,
                    textColor = LanternAmber,
                    modifier = Modifier.weight(1f),
                    cutSize = 3.dp,
                )
                SteppedButton(
                    text = "skip",
                    onClick = {
                        streak = 0
                        loadNewPuzzle()
                    },
                    backgroundColor = MothHeaderBg,
                    borderColor = WaxSealRed,
                    textColor = WaxSealRed,
                    modifier = Modifier.weight(1f),
                    cutSize = 3.dp,
                )
            }
        }
    }
}
