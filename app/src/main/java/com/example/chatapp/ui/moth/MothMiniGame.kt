package com.example.chatapp.ui.moth

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.chatapp.ui.components.PixelBackArrowIcon
import com.example.chatapp.ui.components.PixelTrashIcon
import com.example.chatapp.ui.components.UserAvatar
import com.example.chatapp.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private enum class FunTileItem(
    val label: String,
    val title: String,
    val accent: Color,
    val isGame: Boolean,
) {
    WORDLE("wordle", "wordle", SecureGreen, isGame = true),
    TOD("tod", "truth or dare", WaxSealRed, isGame = true),
    NOTES("notes", "notes", LanternAmber, isGame = false),
    SCREENTIME("screentime", "screentime", Color(0xFF70D6FF), isGame = false),
}

@Composable
private fun PixelFunTileLogo(
    item: FunTileItem,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(64.dp)) {
        val u = size.width / 16f
        when (item) {
            FunTileItem.WORDLE -> {
                val colors = listOf(
                    listOf(SecureGreen, MothDivider, LanternAmber),
                    listOf(LanternAmber, SecureGreen, MothDivider),
                    listOf(SecureGreen, SecureGreen, SecureGreen),
                )
                for (r in 0..2) {
                    for (c in 0..2) {
                        val x = (1.5f + c * 4.5f) * u
                        val y = (1.5f + r * 4.5f) * u
                        drawRect(colors[r][c], Offset(x, y), Size(3.6f * u, 3.6f * u))
                        drawRect(Color(0x55FFFFFF), Offset(x + 0.6f * u, y + 0.6f * u), Size(1.0f * u, 1.0f * u))
                    }
                }
            }
            FunTileItem.TOD -> {
                val glass = Color(0xFF82D8A7)
                val glassShade = Color(0xFF45A07A)
                val cork = LanternAmber
                val spark = WaxSealRed
                val dark = MothBgDark

                drawRect(glassShade, Offset(2.5f * u, 9.5f * u), Size(4.0f * u, 4.0f * u))
                drawRect(glass, Offset(3.0f * u, 9.0f * u), Size(3.5f * u, 3.5f * u))
                drawRect(glassShade, Offset(5.0f * u, 7.0f * u), Size(4.0f * u, 4.0f * u))
                drawRect(glass, Offset(5.5f * u, 6.5f * u), Size(3.5f * u, 3.5f * u))
                drawRect(Color(0x55FFFFFF), Offset(4.0f * u, 9.5f * u), Size(1.2f * u, 1.2f * u))
                drawRect(Color(0x55FFFFFF), Offset(6.2f * u, 7.2f * u), Size(1.2f * u, 1.2f * u))
                drawRect(glass, Offset(8.5f * u, 4.5f * u), Size(2.5f * u, 2.5f * u))
                drawRect(glassShade, Offset(10.0f * u, 3.5f * u), Size(2.0f * u, 2.0f * u))
                drawRect(cork, Offset(11.5f * u, 2.0f * u), Size(2.2f * u, 2.2f * u))
                drawRect(dark, Offset(4.8f * u, 9.5f * u), Size(1.0f * u, 1.0f * u))

                drawRect(cork, Offset(2.5f * u, 2.5f * u), Size(3.0f * u, 1.2f * u))
                drawRect(cork, Offset(1.5f * u, 3.7f * u), Size(1.2f * u, 2.2f * u))
                drawRect(cork, Offset(5.0f * u, 1.8f * u), Size(1.4f * u, 1.4f * u))

                drawRect(spark, Offset(10.5f * u, 12.3f * u), Size(3.0f * u, 1.2f * u))
                drawRect(spark, Offset(13.3f * u, 10.0f * u), Size(1.2f * u, 2.5f * u))
                drawRect(spark, Offset(9.5f * u, 12.8f * u), Size(1.4f * u, 1.4f * u))
            }
            FunTileItem.NOTES -> {
                // Pixel notepad with spiral spine, ruled lines & amber pencil
                val paper = Color(0xFFFFF4D6)
                val paperShade = Color(0xFFD9C7A3)
                val spine = Color(0xFFB8A9E8)
                val ink = MothBgDark
                drawRect(paperShade, Offset(2.2f * u, 1.8f * u), Size(10.5f * u, 12.8f * u))
                drawRect(paper, Offset(2.0f * u, 1.5f * u), Size(10.0f * u, 12.4f * u))
                // Top binding
                drawRect(spine, Offset(2.0f * u, 1.5f * u), Size(10.0f * u, 2.2f * u))
                drawRect(ink, Offset(3.4f * u, 5.2f * u), Size(6.6f * u, 1.1f * u))
                drawRect(ink, Offset(3.4f * u, 7.5f * u), Size(5.4f * u, 1.1f * u))
                drawRect(ink, Offset(3.4f * u, 9.8f * u), Size(4.2f * u, 1.1f * u))
                // Pixel amber pencil diagonally at bottom right
                drawRect(LanternAmber, Offset(10.0f * u, 10.2f * u), Size(4.2f * u, 1.8f * u))
                drawRect(WaxSealRed, Offset(13.4f * u, 10.2f * u), Size(1.2f * u, 1.8f * u))
                drawRect(ink, Offset(8.8f * u, 10.7f * u), Size(1.2f * u, 1.0f * u))
            }
            FunTileItem.SCREENTIME -> {
                // Pixel screen-time bar chart + hourglass accent
                val cyan = Color(0xFF70D6FF)
                val green = SecureGreen
                val amber = LanternAmber
                val frame = TheirBubbleBorder
                drawRect(MothBgDark, Offset(1.5f * u, 2.0f * u), Size(13.0f * u, 11.5f * u))
                drawRect(frame, Offset(1.5f * u, 12.5f * u), Size(13.0f * u, 1.4f * u))
                // Bars
                drawRect(cyan, Offset(2.8f * u, 7.0f * u), Size(2.4f * u, 5.5f * u))
                drawRect(amber, Offset(6.6f * u, 4.0f * u), Size(2.4f * u, 8.5f * u))
                drawRect(green, Offset(10.4f * u, 5.5f * u), Size(2.4f * u, 7.0f * u))
                // Highlights
                drawRect(Color(0x66FFFFFF), Offset(2.8f * u, 7.0f * u), Size(2.4f * u, 1.0f * u))
                drawRect(Color(0x66FFFFFF), Offset(6.6f * u, 4.0f * u), Size(2.4f * u, 1.0f * u))
                drawRect(Color(0x66FFFFFF), Offset(10.4f * u, 5.5f * u), Size(2.4f * u, 1.0f * u))
            }
        }
    }
}

@Composable
fun MothMiniGameView(
    state: MothState,
    onOpenChat: (String) -> Unit,
) {
    var activeItem by remember { mutableStateOf<FunTileItem?>(null) }
    var notesBackInterceptor by remember { mutableStateOf<(() -> Unit)?>(null) }

    BackHandler(enabled = activeItem != null) {
        if (activeItem == FunTileItem.NOTES) {
            notesBackInterceptor?.invoke() ?: run {
                state.closeSharedNotes()
                activeItem = null
            }
        } else {
            activeItem = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MothBgDark)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("games_hub"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val current = activeItem
        if (current == null) {
            Text(
                text = "fun",
                fontFamily = PixelifySans,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = LanternAmber,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Category 1: games (kept intact)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "games",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MutedText,
                    )
                    val gamesList = FunTileItem.entries.filter { it.isGame }
                    gamesList.chunked(2).forEach { rowGames ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowGames.forEach { item ->
                                FunTileSquareCard(
                                    item = item,
                                    onClick = { activeItem = item },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowGames.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Category 2: activities (shared notes & screentime)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "activities",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MutedText,
                    )
                    val activitiesList = FunTileItem.entries.filter { !it.isGame }
                    activitiesList.chunked(2).forEach { rowActs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowActs.forEach { item ->
                                FunTileSquareCard(
                                    item = item,
                                    onClick = {
                                        if (item == FunTileItem.SCREENTIME) {
                                            state.refreshMyScreentimeAndPublish()
                                        }
                                        activeItem = item
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowActs.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SteppedBox(
                    modifier = Modifier
                        .size(38.dp)
                        .clickable {
                            if (current == FunTileItem.NOTES) {
                                notesBackInterceptor?.invoke() ?: run {
                                    state.closeSharedNotes()
                                    activeItem = null
                                }
                            } else {
                                activeItem = null
                            }
                        }
                        .testTag("game_back_button"),
                    backgroundColor = MothHeaderBg,
                    borderColor = LanternAmber,
                    cutSize = 3.dp,
                    borderWidth = 1.dp,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PixelBackArrowIcon(tint = LanternAmber, modifier = Modifier.size(22.dp))
                    }
                }
                Text(
                    text = current.title,
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = LanternAmber,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (current) {
                    FunTileItem.WORDLE -> WordleGame()
                    FunTileItem.TOD -> TruthOrDareGame(
                        state = state,
                        onOpenChat = { target ->
                            activeItem = null
                            onOpenChat(target)
                        },
                    )
                    FunTileItem.NOTES -> SharedNotesActivityView(
                        state = state,
                        onRegisterBackInterceptor = { handler -> notesBackInterceptor = handler },
                        onExitToHub = { activeItem = null },
                    )
                    FunTileItem.SCREENTIME -> ScreentimeActivityView(state = state)
                }
            }
        }
    }
}

@Composable
private fun FunTileSquareCard(
    item: FunTileItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SteppedBox(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .testTag("game_tab_${item.label}"),
        backgroundColor = MothHeaderBg,
        borderColor = item.accent,
        cutSize = 5.dp,
        borderWidth = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PixelFunTileLogo(item = item)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = item.title,
                fontFamily = PixelifySans,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TheirBubbleText,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
// 2. TOD (Online Discord-Bot Style + Quick Play)
// =========================================================================

private data class TodPrompt(
    val type: String,
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
    "Send a voice note or text saying 'the lantern has landed' to the last person you texted.",
    "Draw the weirdest sticker you can in under 15 seconds and send it to a friend.",
    "Let the next player pick a word you cannot say for the next 3 rounds.",
    "Speak only in questions for the next two minutes.",
    "Do your best impression of a movie villain right now.",
    "Show the last photo you saved to your gallery.",
    "Hold a straight face while everyone tries to make you laugh for 20 seconds.",
    "Type a glowing 2-sentence review of your favorite snack and send it in a chat.",
    "Describe your day using only 4 words and no punctuation.",
    "Tell a completely made-up fun fact as convincingly as possible.",
)

@Composable
private fun TruthOrDareGame(
    state: MothState,
    onOpenChat: (String) -> Unit,
) {
    val playableChats = remember(state.conversations) {
        state.conversations.filter { it.isGroup || !it.name.equals("support", ignoreCase = true) }
    }
    var summoningTarget by remember { mutableStateOf<String?>(null) }

    var players by remember { mutableStateOf(listOf("player 1", "player 2")) }
    var currentPlayerIdx by remember { mutableIntStateOf(0) }
    var newPlayerInput by remember { mutableStateOf("") }
    var activePrompt by remember { mutableStateOf<TodPrompt?>(null) }
    var completedCount by remember { mutableIntStateOf(0) }
    var skippedCount by remember { mutableIntStateOf(0) }

    val usedTruths = remember { mutableStateListOf<String>() }
    val usedDares = remember { mutableStateListOf<String>() }

    fun drawPrompt(type: String) {
        val pool = if (type == "TRUTH") DEFAULT_TRUTHS else DEFAULT_DARES
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
        // Summon TOD Bot into any chat (play together online like Discord TOD bot)
        SteppedBox(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tod_online_summon_card"),
            backgroundColor = MothHeaderBg,
            borderColor = SecureGreen,
            cutSize = 4.dp,
            borderWidth = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .background(SecureGreen)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "BOT",
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MothBgDark,
                        )
                    }
                    Text(
                        text = "summon TOD bot into chat",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = LanternAmber,
                    )
                }
                Text(
                    text = "pick a chat to summon the live truth or dare bot for both players (you can remove it anytime in chat)",
                    fontFamily = PixelifySans,
                    fontSize = 11.sp,
                    color = MutedText,
                    lineHeight = 16.sp,
                )

                if (playableChats.isEmpty()) {
                    Text(
                        text = "start a chat with someone first to play TOD online",
                        fontFamily = PixelifySans,
                        fontSize = 12.sp,
                        color = PlaceholderText,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        playableChats.forEach { conv ->
                            val isBusy = summoningTarget == conv.id
                            SteppedBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isBusy) {
                                        summoningTarget = conv.id
                                        state.summonTodBot(conv) { targetName ->
                                            summoningTarget = null
                                            onOpenChat(targetName)
                                        }
                                    }
                                    .testTag("summon_tod_chat_${conv.name}"),
                                backgroundColor = MothBgDark,
                                borderColor = TheirBubbleBorder,
                                cutSize = 3.dp,
                                borderWidth = 1.dp,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (conv.isGroup) conv.name else "@${conv.name}",
                                        fontFamily = PixelifySans,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TheirBubbleText,
                                    )
                                    Text(
                                        text = if (isBusy) "summoning..." else "summon >",
                                        fontFamily = PixelifySans,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = SecureGreen,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Local quick play header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "quick play",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
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
                text = "random spin",
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
                    text = "done",
                    onClick = { nextTurn(didComplete = true) },
                    backgroundColor = SecureGreen,
                    borderColor = Color(0xFF4CA877),
                    textColor = MothBgDark,
                    modifier = Modifier.weight(1f),
                    cutSize = 4.dp,
                )
            }
        }
    }
}

// =========================================================================
// 3. SHARED NOTES (app > fun > notes > {user} > topic notes)
// =========================================================================

private enum class PendingNoteLeaveAction {
    TO_TOPICS,
    TO_USERS,
    TO_HUB,
}

@Composable
private fun SharedNotesActivityView(
    state: MothState,
    onRegisterBackInterceptor: ((() -> Unit)?) -> Unit,
    onExitToHub: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val peer = state.activeNotePeer
    var manualHandleInput by remember { mutableStateOf("") }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var isLookingUp by remember { mutableStateOf(false) }

    // Topic editor state: null = browsing topics list; "" = creating new topic; else = editing existing topic id
    var editingTopicId by remember(peer) { mutableStateOf<String?>(null) }
    var initialTopicTitle by remember(peer) { mutableStateOf("") }
    var initialTopicContent by remember(peer) { mutableStateOf("") }
    var draftTopicTitle by remember(peer) { mutableStateOf("") }
    var draftTopicContent by remember(peer) { mutableStateOf("") }
    var deleteConfirmTarget by remember(peer) { mutableStateOf<Pair<String, String>?>(null) }
    var pendingLeaveAction by remember(peer) { mutableStateOf<PendingNoteLeaveAction?>(null) }

    val hasUnsavedChanges = editingTopicId != null &&
        (draftTopicTitle != initialTopicTitle || draftTopicContent != initialTopicContent)

    fun performLeave(action: PendingNoteLeaveAction) {
        when (action) {
            PendingNoteLeaveAction.TO_TOPICS -> editingTopicId = null
            PendingNoteLeaveAction.TO_USERS -> {
                editingTopicId = null
                state.closeSharedNotes()
            }
            PendingNoteLeaveAction.TO_HUB -> {
                editingTopicId = null
                state.closeSharedNotes()
                onExitToHub()
            }
        }
    }

    fun requestLeave(action: PendingNoteLeaveAction) {
        if (hasUnsavedChanges) {
            pendingLeaveAction = action
        } else {
            performLeave(action)
        }
    }

    DisposableEffect(peer, editingTopicId, hasUnsavedChanges) {
        onRegisterBackInterceptor {
            when {
                deleteConfirmTarget != null -> deleteConfirmTarget = null
                pendingLeaveAction != null -> pendingLeaveAction = null
                editingTopicId != null -> requestLeave(PendingNoteLeaveAction.TO_TOPICS)
                peer != null -> performLeave(PendingNoteLeaveAction.TO_USERS)
                else -> performLeave(PendingNoteLeaveAction.TO_HUB)
            }
        }
        onDispose {
            onRegisterBackInterceptor(null)
        }
    }

    BackHandler {
        when {
            deleteConfirmTarget != null -> deleteConfirmTarget = null
            pendingLeaveAction != null -> pendingLeaveAction = null
            editingTopicId != null -> requestLeave(PendingNoteLeaveAction.TO_TOPICS)
            peer != null -> performLeave(PendingNoteLeaveAction.TO_USERS)
            else -> performLeave(PendingNoteLeaveAction.TO_HUB)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

    if (peer == null) {
        // Step 1: Pick a user (fun > notes > {user})
        val peerContacts = remember(state.conversations) {
            state.conversations.filter { !it.isGroup && !it.name.equals("support", ignoreCase = true) }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "choose a user to share topic notes with (both can read & edit)",
                fontFamily = PixelifySans,
                fontSize = 12.sp,
                color = MutedText,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SteppedBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    backgroundColor = MothHeaderBg,
                    borderColor = TheirBubbleBorder,
                    cutSize = 3.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (manualHandleInput.isEmpty()) {
                            Text(
                                text = "open notes with @username...",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = PlaceholderText,
                            )
                        }
                        BasicTextField(
                            value = manualHandleInput,
                            onValueChange = {
                                manualHandleInput = it
                                lookupError = null
                            },
                            textStyle = TextStyle(
                                fontFamily = PixelifySans,
                                fontSize = 13.sp,
                                color = TheirBubbleText,
                            ),
                            cursorBrush = SolidColor(LanternAmber),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("notes_username_input"),
                        )
                    }
                }
                SteppedButton(
                    text = if (isLookingUp) "..." else "open",
                    onClick = {
                        val clean = manualHandleInput.trim().removePrefix("@").lowercase()
                        if (clean.isBlank() || clean == state.currentUsername) {
                            lookupError = "enter another valid username"
                            return@SteppedButton
                        }
                        isLookingUp = true
                        lookupError = null
                        coroutineScope.launch {
                            val ok = state.verifyUserExists(clean)
                            isLookingUp = false
                            if (ok) {
                                manualHandleInput = ""
                                state.openSharedNotesWith(clean)
                            } else {
                                lookupError = "user @$clean not found"
                            }
                        }
                    },
                    backgroundColor = MyBubbleFill,
                    borderColor = MyBubbleBorder,
                    textColor = MyBubbleText,
                    cutSize = 3.dp,
                )
            }

            if (lookupError != null) {
                Text(
                    text = lookupError.orEmpty(),
                    fontFamily = PixelifySans,
                    fontSize = 11.sp,
                    color = WaxSealRed,
                )
            }

            if (peerContacts.isEmpty()) {
                SteppedBox(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MothHeaderBg,
                    borderColor = MothDivider,
                    cutSize = 3.dp,
                ) {
                    Text(
                        text = "no direct chats yet — type a username above to open shared notes",
                        fontFamily = PixelifySans,
                        fontSize = 12.sp,
                        color = MutedText,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    peerContacts.forEach { conv ->
                        SteppedBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { state.openSharedNotesWith(conv.name) }
                                .testTag("notes_user_${conv.name}"),
                            backgroundColor = MothHeaderBg,
                            borderColor = LanternAmber,
                            cutSize = 4.dp,
                            borderWidth = 1.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UserAvatar(
                                    avatarPath = conv.avatarLocalPath,
                                    color = conv.avatarColor,
                                    size = 34.dp,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "@${conv.name}",
                                    fontFamily = PixelifySans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TheirBubbleText,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "notes >",
                                    fontFamily = PixelifySans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = LanternAmber,
                                )
                            }
                        }
                    }
                }
            }
        }
    } else if (editingTopicId != null) {
        // Step 3: Read & Edit a Topic Note
        val existingTopic = state.sharedNoteTopics.firstOrNull { it.id == editingTopicId }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "notes > @$peer > ${if (editingTopicId.isNullOrBlank()) "new topic" else "edit"}",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MutedText,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "< topics",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = LanternAmber,
                    modifier = Modifier
                        .clickable { requestLeave(PendingNoteLeaveAction.TO_TOPICS) }
                        .padding(start = 8.dp),
                )
            }

            if (existingTopic != null && existingTopic.updatedBy.isNotBlank()) {
                Text(
                    text = "last edited by @${existingTopic.updatedBy}",
                    fontFamily = PixelifySans,
                    fontSize = 11.sp,
                    color = MutedText,
                )
            }

            SteppedBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                backgroundColor = MothHeaderBg,
                borderColor = LanternAmber,
                cutSize = 3.dp,
                borderWidth = 1.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (draftTopicTitle.isEmpty()) {
                        Text(
                            text = "topic title...",
                            fontFamily = PixelifySans,
                            fontSize = 14.sp,
                            color = PlaceholderText,
                        )
                    }
                    BasicTextField(
                        value = draftTopicTitle,
                        onValueChange = { draftTopicTitle = it },
                        textStyle = TextStyle(
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = LanternAmber,
                        ),
                        cursorBrush = SolidColor(LanternAmber),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_topic_title_input"),
                    )
                }
            }

            SteppedBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 380.dp),
                backgroundColor = MothHeaderBg,
                borderColor = TheirBubbleBorder,
                cutSize = 4.dp,
                borderWidth = 1.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 380.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    if (draftTopicContent.isEmpty()) {
                        Text(
                            text = "write your shared note here (both @${state.currentUsername} & @$peer can read and edit)...",
                            fontFamily = PixelifySans,
                            fontSize = 13.sp,
                            color = PlaceholderText,
                        )
                    }
                    BasicTextField(
                        value = draftTopicContent,
                        onValueChange = { draftTopicContent = it },
                        textStyle = TextStyle(
                            fontFamily = PixelifySans,
                            fontSize = 14.sp,
                            color = TheirBubbleText,
                            lineHeight = 20.sp,
                        ),
                        cursorBrush = SolidColor(LanternAmber),
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 176.dp)
                            .testTag("note_topic_body_input"),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!editingTopicId.isNullOrBlank()) {
                    SteppedButton(
                        text = "delete",
                        onClick = {
                            val id = editingTopicId ?: return@SteppedButton
                            val label = draftTopicTitle.trim().ifBlank { existingTopic?.title ?: "untitled topic" }
                            deleteConfirmTarget = id to label
                        },
                        backgroundColor = TheirBubbleFill,
                        borderColor = WaxSealRed,
                        textColor = WaxSealRed,
                        modifier = Modifier.weight(1f),
                        cutSize = 3.dp,
                    )
                }
                SteppedButton(
                    text = "save note",
                    onClick = {
                        state.saveSharedNoteTopic(
                            peerUsername = peer,
                            topicId = editingTopicId?.takeIf { it.isNotBlank() },
                            title = draftTopicTitle,
                            content = draftTopicContent,
                        ) {
                            editingTopicId = null
                        }
                    },
                    backgroundColor = MyBubbleFill,
                    borderColor = MyBubbleBorder,
                    textColor = MyBubbleText,
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("save_note_topic_button"),
                    cutSize = 3.dp,
                )
            }
        }
    } else {
        // Step 2: Topic list for selected user (fun > notes > {user} > topics)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "notes > @$peer",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = LanternAmber,
                    )
                    Text(
                        text = "both of you can read and edit topics",
                        fontFamily = PixelifySans,
                        fontSize = 11.sp,
                        color = MutedText,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SteppedButton(
                        text = "users",
                        onClick = { state.closeSharedNotes() },
                        backgroundColor = TheirBubbleFill,
                        borderColor = TheirBubbleBorder,
                        textColor = TheirBubbleText,
                        cutSize = 3.dp,
                    )
                    SteppedButton(
                        text = "+ topic",
                        onClick = {
                            initialTopicTitle = ""
                            initialTopicContent = ""
                            draftTopicTitle = ""
                            draftTopicContent = ""
                            editingTopicId = ""
                        },
                        backgroundColor = MyBubbleFill,
                        borderColor = MyBubbleBorder,
                        textColor = MyBubbleText,
                        cutSize = 3.dp,
                        modifier = Modifier.testTag("new_note_topic_button"),
                    )
                }
            }

            if (state.sharedNoteTopics.isEmpty()) {
                SteppedBox(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MothHeaderBg,
                    borderColor = MothDivider,
                    cutSize = 4.dp,
                ) {
                    Text(
                        text = "no topic notes with @$peer yet — tap + topic to create one",
                        fontFamily = PixelifySans,
                        fontSize = 12.sp,
                        color = MutedText,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.sharedNoteTopics.forEach { topic ->
                        SteppedBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    initialTopicTitle = topic.title
                                    initialTopicContent = topic.content
                                    draftTopicTitle = topic.title
                                    draftTopicContent = topic.content
                                    editingTopicId = topic.id
                                }
                                .testTag("note_topic_card_${topic.id}"),
                            backgroundColor = MothHeaderBg,
                            borderColor = TheirBubbleBorder,
                            cutSize = 4.dp,
                            borderWidth = 1.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = topic.title,
                                        fontFamily = PixelifySans,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = LanternAmber,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (topic.content.isNotBlank()) {
                                        Text(
                                            text = topic.content.replace('\n', ' '),
                                            fontFamily = PixelifySans,
                                            fontSize = 12.sp,
                                            color = TheirBubbleText,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    val timeStr = if (topic.updatedAt > 0L) {
                                        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                            .format(Date(topic.updatedAt)).lowercase()
                                    } else ""
                                    Text(
                                        text = "edited by @${topic.updatedBy}${if (timeStr.isNotBlank()) " • $timeStr" else ""}",
                                        fontFamily = PixelifySans,
                                        fontSize = 10.sp,
                                        color = MutedText,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clickable { deleteConfirmTarget = topic.id to topic.title }
                                        .testTag("delete_note_icon_${topic.id}"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PixelTrashIcon(tint = WaxSealRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        // Delete Confirmation Dialog
        deleteConfirmTarget?.let { (targetId, targetTitle) ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MothScrimStrong)
                    .clickable { deleteConfirmTarget = null }
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                SteppedBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) { }
                        .testTag("confirm_delete_note_dialog"),
                    backgroundColor = MothHeaderBg,
                    borderColor = WaxSealRed,
                    cutSize = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "delete note?",
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = WaxSealRed,
                        )
                        Text(
                            text = "delete \"$targetTitle\"? this removes it for both @${state.currentUsername} and @$peer.",
                            fontFamily = PixelifySans,
                            fontSize = 12.sp,
                            color = TheirBubbleText,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SteppedButton(
                                text = "cancel",
                                onClick = { deleteConfirmTarget = null },
                                backgroundColor = MothBgDark,
                                borderColor = TheirBubbleBorder,
                                textColor = MutedText,
                                modifier = Modifier.weight(1f).testTag("cancel_delete_note_button"),
                                cutSize = 3.dp,
                            )
                            SteppedButton(
                                text = "delete",
                                onClick = {
                                    if (peer != null) {
                                        state.deleteSharedNoteTopic(peer, targetId)
                                    }
                                    if (editingTopicId == targetId) {
                                        editingTopicId = null
                                    }
                                    deleteConfirmTarget = null
                                },
                                backgroundColor = WaxSealRed,
                                borderColor = WaxSealDarkEdge,
                                textColor = Color.White,
                                modifier = Modifier.weight(1f).testTag("confirm_delete_note_button"),
                                cutSize = 3.dp,
                            )
                        }
                    }
                }
            }
        }

        // Unsaved Changes Save Confirmation Dialog
        pendingLeaveAction?.let { leaveAction ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MothScrimStrong)
                    .clickable { pendingLeaveAction = null }
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                SteppedBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) { }
                        .testTag("unsaved_note_dialog"),
                    backgroundColor = MothHeaderBg,
                    borderColor = LanternAmber,
                    cutSize = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "save changes?",
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = LanternAmber,
                        )
                        Text(
                            text = "you have unsaved edits in this note. do you want to save before leaving?",
                            fontFamily = PixelifySans,
                            fontSize = 12.sp,
                            color = TheirBubbleText,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SteppedButton(
                                text = "discard",
                                onClick = {
                                    pendingLeaveAction = null
                                    performLeave(leaveAction)
                                },
                                backgroundColor = MothBgDark,
                                borderColor = WaxSealRed,
                                textColor = WaxSealRed,
                                modifier = Modifier.weight(1f).testTag("discard_note_changes_button"),
                                cutSize = 3.dp,
                            )
                            SteppedButton(
                                text = "save",
                                onClick = {
                                    if (peer != null) {
                                        state.saveSharedNoteTopic(
                                            peerUsername = peer,
                                            topicId = editingTopicId?.takeIf { it.isNotBlank() },
                                            title = draftTopicTitle,
                                            content = draftTopicContent,
                                        ) {
                                            pendingLeaveAction = null
                                            performLeave(leaveAction)
                                        }
                                    } else {
                                        pendingLeaveAction = null
                                        performLeave(leaveAction)
                                    }
                                },
                                backgroundColor = MyBubbleFill,
                                borderColor = MyBubbleBorder,
                                textColor = MyBubbleText,
                                modifier = Modifier.weight(1f).testTag("save_note_changes_button"),
                                cutSize = 3.dp,
                            )
                        }
                        SteppedButton(
                            text = "keep editing",
                            onClick = { pendingLeaveAction = null },
                            backgroundColor = TheirBubbleFill,
                            borderColor = TheirBubbleBorder,
                            textColor = TheirBubbleText,
                            modifier = Modifier.fillMaxWidth().testTag("keep_editing_note_button"),
                            cutSize = 3.dp,
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// 4. SCREENTIME (Peer permission-gated screen time sharing)
// =========================================================================

private fun formatMinutesLabel(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val hrs = safe / 60
    val mins = safe % 60
    return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
}

@Composable
private fun ScreentimeActivityView(state: MothState) {
    val context = LocalContext.current
    var peerHandleInput by remember { mutableStateOf("") }
    var requestError by remember { mutableStateOf<String?>(null) }
    val hasUsagePerm = remember(state.myScreentimeSummary.hasUsageAccess, state.myScreentimeSummary.updatedAt) {
        state.myScreentimeSummary.hasUsageAccess || state.hasSystemUsageStatsPermission()
    }

    LaunchedEffect(Unit) {
        state.refreshMyScreentimeAndPublish()
    }

    val pendingIncoming = remember(state.incomingScreentimeRequests) {
        state.incomingScreentimeRequests.filter { it.status == "PENDING" }
    }
    val approvedIncoming = remember(state.incomingScreentimeRequests) {
        state.incomingScreentimeRequests.filter { it.status == "APPROVED" }
    }
    val candidatePeers = remember(state.conversations, state.outgoingScreentimePermissions.keys.toList()) {
        val convNames = state.conversations
            .filter { !it.isGroup && !it.name.equals("support", ignoreCase = true) }
            .map { it.name.lowercase() }
        val permNames = state.outgoingScreentimePermissions.keys.map { it.lowercase() }
        (convNames + permNames).distinct().filter { it != state.currentUsername.lowercase() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // My Screentime Card
        val mine = state.myScreentimeSummary
        SteppedBox(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MothHeaderBg,
            borderColor = Color(0xFF70D6FF),
            cutSize = 4.dp,
            borderWidth = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "your screentime today",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF70D6FF),
                    )
                    SteppedButton(
                        text = "refresh",
                        onClick = {
                            state.refreshMyScreentimeAndPublish()
                        },
                        backgroundColor = MothBgDark,
                        borderColor = TheirBubbleBorder,
                        textColor = TheirBubbleText,
                        cutSize = 2.dp,
                    )
                }

                Text(
                    text = "${formatMinutesLabel(mine.todayMinutes)} total  •  Farlight: ${formatMinutesLabel(mine.farlightMinutes)}",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = LanternAmber,
                )
                if (mine.topAppsText.isNotBlank()) {
                    Text(
                        text = mine.topAppsText,
                        fontFamily = PixelifySans,
                        fontSize = 11.sp,
                        color = MutedText,
                    )
                }
                if (!hasUsagePerm) {
                    Text(
                        text = "grant usage access so full daily phone screentime syncs accurately",
                        fontFamily = PixelifySans,
                        fontSize = 11.sp,
                        color = LanternAmber,
                    )
                    SteppedButton(
                        text = "enable android usage access",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        backgroundColor = MyBubbleFill,
                        borderColor = MyBubbleBorder,
                        textColor = MyBubbleText,
                        modifier = Modifier.fillMaxWidth(),
                        cutSize = 3.dp,
                    )
                }
            }
        }

        // Incoming Permission Requests
        if (pendingIncoming.isNotEmpty() || approvedIncoming.isNotEmpty()) {
            SteppedBox(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MothHeaderBg,
                borderColor = LanternAmber,
                cutSize = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "screentime access requests",
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = LanternAmber,
                    )
                    pendingIncoming.forEach { req ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "@${req.requester} wants to view",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = TheirBubbleText,
                                modifier = Modifier.weight(1f),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SteppedButton(
                                    text = "deny",
                                    onClick = { state.respondScreentimeRequest(req.requester, false) },
                                    backgroundColor = MothBgDark,
                                    borderColor = WaxSealRed,
                                    textColor = WaxSealRed,
                                    cutSize = 2.dp,
                                )
                                SteppedButton(
                                    text = "allow",
                                    onClick = { state.respondScreentimeRequest(req.requester, true) },
                                    backgroundColor = SecureGreen,
                                    borderColor = Color(0xFF4CA877),
                                    textColor = MothBgDark,
                                    cutSize = 2.dp,
                                    modifier = Modifier.testTag("allow_screentime_${req.requester}"),
                                )
                            }
                        }
                    }
                    approvedIncoming.forEach { req ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "@${req.requester} can view yours",
                                fontFamily = PixelifySans,
                                fontSize = 11.sp,
                                color = SecureGreen,
                            )
                            Text(
                                text = "revoke",
                                fontFamily = PixelifySans,
                                fontSize = 11.sp,
                                color = WaxSealRed,
                                modifier = Modifier
                                    .clickable { state.respondScreentimeRequest(req.requester, false) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        // Check Another User's Screentime
        Text(
            text = "check another user's screentime",
            fontFamily = PixelifySans,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = LanternAmber,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SteppedBox(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                backgroundColor = MothHeaderBg,
                borderColor = TheirBubbleBorder,
                cutSize = 3.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (peerHandleInput.isEmpty()) {
                        Text(
                            text = "username to check...",
                            fontFamily = PixelifySans,
                            fontSize = 12.sp,
                            color = PlaceholderText,
                        )
                    }
                    BasicTextField(
                        value = peerHandleInput,
                        onValueChange = {
                            peerHandleInput = it
                            requestError = null
                        },
                        textStyle = TextStyle(
                            fontFamily = PixelifySans,
                            fontSize = 13.sp,
                            color = TheirBubbleText,
                        ),
                        cursorBrush = SolidColor(LanternAmber),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("screentime_peer_input"),
                    )
                }
            }
            SteppedButton(
                text = "request",
                onClick = {
                    val clean = peerHandleInput.trim().removePrefix("@").lowercase()
                    if (clean.isBlank()) {
                        requestError = "enter username"
                        return@SteppedButton
                    }
                    requestError = null
                    state.requestPeerScreentimePermission(clean) { err -> requestError = err }
                    peerHandleInput = ""
                },
                backgroundColor = MyBubbleFill,
                borderColor = MyBubbleBorder,
                textColor = MyBubbleText,
                cutSize = 3.dp,
            )
        }

        if (requestError != null) {
            Text(
                text = requestError.orEmpty(),
                fontFamily = PixelifySans,
                fontSize = 11.sp,
                color = WaxSealRed,
            )
        }

        if (candidatePeers.isEmpty()) {
            SteppedBox(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MothHeaderBg,
                borderColor = MothDivider,
                cutSize = 3.dp,
            ) {
                Text(
                    text = "enter a username above or chat with someone to request screentime access",
                    fontFamily = PixelifySans,
                    fontSize = 12.sp,
                    color = MutedText,
                    modifier = Modifier.padding(14.dp),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                candidatePeers.forEach { peer ->
                    val perm = state.outgoingScreentimePermissions[peer]
                    val peerStat = state.peerScreentimeSummaries[peer]
                    SteppedBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("screentime_peer_card_$peer"),
                        backgroundColor = MothHeaderBg,
                        borderColor = when (perm?.status) {
                            "APPROVED" -> SecureGreen
                            "PENDING" -> LanternAmber
                            "DENIED" -> WaxSealRed
                            else -> TheirBubbleBorder
                        },
                        cutSize = 4.dp,
                        borderWidth = 1.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "@$peer",
                                    fontFamily = PixelifySans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TheirBubbleText,
                                )
                                when (perm?.status) {
                                    "APPROVED" -> {
                                        Text(
                                            text = "approved",
                                            fontFamily = PixelifySans,
                                            fontSize = 11.sp,
                                            color = SecureGreen,
                                        )
                                    }
                                    "PENDING" -> {
                                        Text(
                                            text = "pending",
                                            fontFamily = PixelifySans,
                                            fontSize = 11.sp,
                                            color = LanternAmber,
                                        )
                                    }
                                    "DENIED" -> {
                                        SteppedButton(
                                            text = "ask again",
                                            onClick = {
                                                state.requestPeerScreentimePermission(peer) { requestError = it }
                                            },
                                            backgroundColor = MothBgDark,
                                            borderColor = WaxSealRed,
                                            textColor = WaxSealRed,
                                            cutSize = 2.dp,
                                        )
                                    }
                                    else -> {
                                        SteppedButton(
                                            text = "request permission",
                                            onClick = {
                                                state.requestPeerScreentimePermission(peer) { requestError = it }
                                            },
                                            backgroundColor = MyBubbleFill,
                                            borderColor = MyBubbleBorder,
                                            textColor = MyBubbleText,
                                            cutSize = 2.dp,
                                            modifier = Modifier.testTag("request_screentime_$peer"),
                                        )
                                    }
                                }
                            }

                            when (perm?.status) {
                                null -> {
                                    Text(
                                        text = "requires @$peer's first-time permission",
                                        fontFamily = PixelifySans,
                                        fontSize = 11.sp,
                                        color = MutedText,
                                    )
                                }
                                "PENDING" -> {
                                    Text(
                                        text = "waiting for @$peer to approve your request...",
                                        fontFamily = PixelifySans,
                                        fontSize = 11.sp,
                                        color = MutedText,
                                    )
                                }
                                "DENIED" -> {
                                    Text(
                                        text = "@$peer declined screentime access",
                                        fontFamily = PixelifySans,
                                        fontSize = 11.sp,
                                        color = WaxSealRed,
                                    )
                                }
                                "APPROVED" -> {
                                    if (peerStat == null) {
                                        Text(
                                            text = "waiting for @$peer to sync today's screentime...",
                                            fontFamily = PixelifySans,
                                            fontSize = 11.sp,
                                            color = MutedText,
                                        )
                                    } else {
                                        Text(
                                            text = "${formatMinutesLabel(peerStat.todayMinutes)} total  •  Farlight: ${formatMinutesLabel(peerStat.farlightMinutes)}",
                                            fontFamily = PixelifySans,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = LanternAmber,
                                        )
                                        if (peerStat.topAppsText.isNotBlank()) {
                                            Text(
                                                text = peerStat.topAppsText,
                                                fontFamily = PixelifySans,
                                                fontSize = 11.sp,
                                                color = TheirBubbleText,
                                            )
                                        }
                                        if (peerStat.updatedAt > 0L) {
                                            val updatedLabel = SimpleDateFormat("h:mm a", Locale.getDefault())
                                                .format(Date(peerStat.updatedAt)).lowercase()
                                            Text(
                                                text = "updated $updatedLabel",
                                                fontFamily = PixelifySans,
                                                fontSize = 10.sp,
                                                color = MutedText,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
