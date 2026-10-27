package dev.mobium.match

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One card as the server tells this player: its species only while up or matched. */
data class DuoCard(val state: String, val species: String?)

/** The game as the server tells this player. */
data class DuoView(val version: Int, val turn: String, val scores: Map<String, Int>,
    val cards: List<DuoCard>, val over: Boolean)

private fun parse(json: String): DuoView {
    val o = JSONObject(json)
    val s = o.getJSONObject("scores")
    val cs = o.getJSONArray("cards")
    val cards = (0 until cs.length()).map {
        val c = cs.getJSONObject(it)
        DuoCard(c.getString("state"), c.optString("species").ifEmpty { null })
    }
    return DuoView(o.getInt("version"), o.getString("turn"), mapOf("A" to s.getInt("A"), "B" to s.getInt("B")),
        cards, o.getBoolean("over"))
}

/** A call to the duo server: its status and body, or the failure as a status of 0. */
private suspend fun call(method: String, url: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 2000
        c.readTimeout = 2000
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        code to body
    } catch (e: Exception) {
        0 to (e.message ?: "no answer")
    }
}

private const val UNREACHABLE = "The server did not answer"

private val names = SPECIES.associate { it.id to it }

/**
 * A game of two: the board is the server's, and this device is one player.
 * It reads the game every 300 ms; a flip is the server's to accept, and a
 * refusal — not your turn — is shown as the server gave it.
 */
@Composable
fun DuoScreen(store: Store, player: String, server: String) {
    var view by remember { mutableStateOf<DuoView?>(null) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(player, server) {
        while (true) {
            val (code, body) = call("GET", "$server/state?player=$player")
            if (code == 200) {
                val next = parse(body)
                // A message is about the board it was given on: a refusal goes
                // when the board changes, and "did not answer" when it answers.
                if (next.version != view?.version || message.startsWith(UNREACHABLE)) message = ""
                view = next
            } else message = "$UNREACHABLE: $body"
            delay(300)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("You are $player", Modifier.testTag("you"), fontWeight = FontWeight.Bold)
            Text("Turn: ${view?.turn ?: "…"}", Modifier.testTag("turn"))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("A: ${view?.scores?.get("A") ?: 0}", Modifier.testTag("scoreA"))
            Text("B: ${view?.scores?.get("B") ?: 0}", Modifier.testTag("scoreB"))
        }
        val over = view?.takeIf { it.over }
        if (over != null) {
            // In the message's row, not under the board: on a Quest panel the
            // board fills the height, and a line below it showed as a sliver.
            val a = over.scores["A"] ?: 0
            val b = over.scores["B"] ?: 0
            Text(if (a == b) "A draw, $a each" else "${if (a > b) "A" else "B"} wins, $a to $b",
                Modifier.padding(16.dp).testTag("duoOver"), fontWeight = FontWeight.Bold)
        } else Text(message, Modifier.padding(16.dp).testTag("duoMessage"))
        val v = view ?: return@Column
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0 until 4) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (col in 0 until 4) {
                        val i = row * 4 + col
                        val c = v.cards[i]
                        val s = c.species?.let { names[it] }
                        val said = when {
                            c.state == "matched" && s != null -> "Card ${i + 1}, ${s.name}, matched"
                            c.state == "up" && s != null -> "Card ${i + 1}, ${s.name}"
                            else -> "Card ${i + 1}, face down"
                        }
                        Box(Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(10.dp))
                            .background(if (s != null) Color(s.color).copy(alpha = if (c.state == "matched") 0.45f else 1f)
                                else Color(0xFF263238))
                            .testTag("duo_card_$i").semantics { contentDescription = said }
                            .clickable {
                                scope.launch {
                                    val (code, body) = call("POST", "$server/flip?player=$player&card=$i")
                                    message = when (code) {
                                        200 -> ""
                                        0 -> "$UNREACHABLE: $body"
                                        else -> runCatching { JSONObject(body).getString("error") }.getOrDefault(body)
                                    }
                                    if (code == 200) view = parse(body)
                                }
                            }, contentAlignment = Alignment.Center) {
                            Text(s?.name ?: "?", color = Color.White, textAlign = TextAlign.Center,
                                fontSize = if (s != null) 12.sp else 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
