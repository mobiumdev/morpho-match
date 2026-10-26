package dev.mobium.match

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Where the app is. */
sealed interface Screen {
    data object Home : Screen
    data class Board(val seed: Long) : Screen
    data class Sorting(val seed: Long) : Screen
    data class Duo(val player: String, val server: String) : Screen
}

/** What outlives a screen: the best score and the bugs that are on. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("match", Context.MODE_PRIVATE)
    var screen by mutableStateOf<Screen>(Screen.Home)
    var best by mutableIntStateOf(prefs.getInt("best", 0))
        private set
    var bugs by mutableStateOf(prefs.getStringSet("bugs", emptySet())!!.toSet())
        private set

    fun record(moves: Int) {
        if ("best-lost" in bugs) return
        if (best == 0 || moves < best) {
            best = moves
            prefs.edit().putInt("best", moves).apply()
        }
    }

    fun reset() {
        best = 0
        prefs.edit().remove("best").apply()
        screen = Screen.Home
    }

    fun setBugs(on: Set<String>, off: Set<String>) {
        bugs = (bugs - off) + on
        prefs.edit().putStringSet("bugs", bugs).apply()
    }
}

/**
 * morphomatch://play?seed=N, morphomatch://sort?seed=N, morphomatch://reset,
 * morphomatch://duo?player=A&server=http://10.0.2.2:8787 (a game of two),
 * and morphomatch://bugs?on=a,b&off=c (off=all turns every bug off). A link
 * naming something that is not a bug is refused whole.
 */
fun follow(uri: Uri, store: Store): String? {
    if (uri.scheme != "morphomatch") return null
    fun seed() = uri.getQueryParameter("seed")?.toLongOrNull() ?: System.nanoTime()
    return when (uri.host) {
        "play" -> { store.screen = Screen.Board(seed()); null }
        "sort" -> { store.screen = Screen.Sorting(seed()); null }
        "reset" -> { store.reset(); null }
        "duo" -> {
            val player = uri.getQueryParameter("player")
            val server = uri.getQueryParameter("server") ?: "http://10.0.2.2:8787"
            if (player != "A" && player != "B") "A duo link names player=A or player=B"
            else { store.screen = Screen.Duo(player, server.trimEnd('/')); null }
        }
        "bugs" -> {
            fun ids(p: String) = uri.getQueryParameter(p)?.split(',')?.map { it.trim() }
                ?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
            val on = ids("on")
            var off = ids("off")
            if ("all" in off) off = (off - "all") + BUG_IDS
            val unknown = (on + off) - BUG_IDS
            if (unknown.isNotEmpty()) "Not a bug: ${unknown.sorted().joinToString(", ")}"
            else { store.setBugs(on, off); null }
        }
        else -> "Not a link this app knows: $uri"
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var store: Store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        intent?.data?.let { follow(it, store) }
        setContent {
            MaterialTheme {
                // Android 15+ draws the app under the status and navigation bars; a
                // back button there sat under the status bar and took no tap.
                Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) { App(store) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { follow(it, store) }
    }
}

@Composable
fun App(store: Store) {
    when (val s = store.screen) {
        Screen.Home -> Home(store)
        is Screen.Board -> Board(store, s.seed)
        is Screen.Sorting -> Sorting(store, s.seed)
        is Screen.Duo -> DuoScreen(store, s.player, s.server)
    }
}

@Composable
fun Home(store: Store) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Text("Morpho Match", style = MaterialTheme.typography.headlineMedium)
        Text(if (store.best == 0) "No best yet" else "Best: ${store.best} moves", Modifier.testTag("best"))
        Button({ store.screen = Screen.Board(System.nanoTime()) }, Modifier.testTag("newGame")) { Text("New game") }
        Button({ store.screen = Screen.Sorting(System.nanoTime()) }, Modifier.testTag("sortLevel")) {
            Text("Sort by wingspan")
        }
    }
}

@Composable
private fun TopBar(store: Store, title: String) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton({ store.screen = Screen.Home }, Modifier.testTag("backBtn")
            .semantics { contentDescription = "Back" }) { Text("←", fontSize = 22.sp) }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun Board(store: Store, seed: Long) {
    val game = remember(seed) { Match(seed, store.bugs) { System.currentTimeMillis() } }
    var tick by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(0) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }

    // A pair that did not match stays up long enough to be seen.
    LaunchedEffect(tick) {
        if (game.busy) {
            delay(800)
            game.settle()
            tick++
        }
    }
    // The clock, once a second until the game says it has stopped.
    LaunchedEffect(seed) {
        while (true) {
            seconds = game.seconds().toInt()
            delay(250)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(store, "Morpho Match")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Moves: ${game.moves}", Modifier.testTag("moves"))
            Text("Pairs: ${game.matched.size / 2} of 8", Modifier.testTag("pairs"))
            Text("Time: ${seconds}s", Modifier.testTag("timer"))
        }
        Spacer(Modifier.height(8.dp))
        key(tick) {
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until 4) {
                            val i = row * 4 + col
                            Card(i, game.deck[i], up = i in game.faceUp || i in game.matched,
                                done = i in game.matched, Modifier.weight(1f)) {
                                val before = game.matched.size
                                if (game.flip(i)) {
                                    if (game.matched.size > before) tone.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                                    if (game.won) store.record(game.moves)
                                    tick++
                                }
                            }
                        }
                    }
                }
            }
        }
        if (game.won) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("You found all 8 pairs in ${game.moves} moves", Modifier.testTag("status"),
                    fontWeight = FontWeight.Bold)
                Button({ store.screen = Screen.Board(System.nanoTime()) }, Modifier.testTag("playAgain")) {
                    Text("Play again")
                }
            }
        }
    }
}

@Composable
private fun key(k: Int, content: @Composable () -> Unit) = androidx.compose.runtime.key(k) { content() }

/** A card, turned with a flip; face down it says only its place. */
@Composable
private fun Card(i: Int, s: Species, up: Boolean, done: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val turn by animateFloatAsState(if (up) 180f else 0f, tween(300), label = "flip")
    val showing = turn > 90f
    val said = when {
        done -> "Card ${i + 1}, ${s.name}, matched"
        up -> "Card ${i + 1}, ${s.name}"
        else -> "Card ${i + 1}, face down"
    }
    Box(modifier.aspectRatio(0.8f).graphicsLayer { rotationY = turn; cameraDistance = 12f * density }
        .clip(RoundedCornerShape(10.dp))
        .background(if (showing) Color(s.color).copy(alpha = if (done) 0.45f else 1f) else Color(0xFF263238))
        .testTag("card_$i").semantics { contentDescription = said }
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (showing) {
            Text(s.name, Modifier.graphicsLayer { rotationY = 180f }.padding(4.dp), color = Color.White,
                textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        } else {
            Text("?", color = Color.White, fontSize = 22.sp)
        }
    }
}

/**
 * The sort level. A card is dropped where it was carried: its own offset,
 * the sum of the drag's steps, decides the slot, as most apps write it — so
 * a drag that moves a card further than the finger drops it in the wrong
 * place, and the test says so.
 */
@Composable
fun Sorting(store: Store, seed: Long) {
    val sort = remember(seed) { Sort(seed, store.bugs) }
    var tick by remember { mutableIntStateOf(0) }
    val slotBounds = remember { mutableStateMapOf<Int, Rect>() }

    Column(Modifier.fillMaxSize()) {
        TopBar(store, "Sort by wingspan")
        Text(if (sort.solved) "Sorted!" else "Smallest wingspan on the left",
            Modifier.padding(16.dp).testTag("sortStatus"), fontWeight = FontWeight.Bold)
        key(tick) {
            Text("Slots", Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until 4) {
                    val s = sort.slots[i]
                    Box(Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFE0E0E0))
                        .onGloballyPositioned { slotBounds[i] = it.boundsInRoot() }
                        .testTag("slot_$i")
                        .semantics { contentDescription = "Slot ${i + 1}, ${s?.name ?: "empty"}" },
                        contentAlignment = Alignment.Center) {
                        if (s != null) SortCard(s, slotBounds) { at -> sort.drop(s, at); tick++ }
                    }
                }
            }
            Text("Tray", Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in sort.cards) {
                    Box(Modifier.weight(1f).aspectRatio(0.8f), contentAlignment = Alignment.Center) {
                        if (s in sort.inTray()) SortCard(s, slotBounds) { at -> sort.drop(s, at); tick++ }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortCard(s: Species, slots: Map<Int, Rect>, onDrop: (Int) -> Unit) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    var home by remember { mutableStateOf(Rect.Zero) }
    Box(Modifier.offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
        .fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color(s.color))
        .onGloballyPositioned { if (drag == Offset.Zero) home = it.boundsInRoot() }
        .testTag("sort_${s.id}").semantics { contentDescription = "${s.name}, ${s.wingspanCm} cm" }
        .pointerInput(s) {
            detectDragGestures(
                onDragEnd = {
                    val center = home.center + drag
                    slots.entries.firstOrNull { it.value.contains(center) }?.let { onDrop(it.key) }
                    drag = Offset.Zero
                },
                onDragCancel = { drag = Offset.Zero },
            ) { change, amount ->
                change.consume()
                drag += amount
            }
        }, contentAlignment = Alignment.Center) {
        Text("${s.name}\n${s.wingspanCm} cm", color = Color.White, textAlign = TextAlign.Center,
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
