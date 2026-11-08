package com.prism.music

import com.prism.music.connect.HttpRequest
import com.prism.music.connect.HttpResponse
import com.prism.music.connect.HttpServer
import com.prism.music.connect.Lan
import com.prism.music.data.innertube.SearchFilter
import com.prism.music.data.model.Song
import com.prism.music.data.model.SongItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.InetAddress
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Veloura's party queue: guests on the same Wi-Fi scan a QR code, open a small web page served from this phone, search
 * YouTube Music and add songs; Veloura mixes them in. Only this network can reach it (Prism Connect's [HttpServer]
 * answers local addresses only), and only with the party's random code in the address. Each phone may add a song
 * every [ADD_EVERY_MS]; the host sees every addition, can take it out, and can block a phone.
 */
class PartyQueue(private val c: AppContainer) {
    data class Added(val song: Song, val guest: String, val from: String, val at: Long)

    private val _added = MutableStateFlow<List<Added>>(emptyList())
    /** What guests added, newest first. */
    val added: StateFlow<List<Added>> = _added

    private val _link = MutableStateFlow<String?>(null)
    /** The address guests open, while the party's on. */
    val link: StateFlow<String?> = _link

    private var server: HttpServer? = null
    private var code = ""
    private val lastAdd = ConcurrentHashMap<String, Long>()
    private val blocked = ConcurrentHashMap.newKeySet<String>()
    /** Songs guests' searches found, by id, so an "add" is of a song the page showed. */
    private val found = ConcurrentHashMap<String, Song>()

    val running: Boolean get() = server?.running == true

    /** Starts the party (false when the phone isn't on a network or couldn't listen). */
    fun start(): Boolean {
        if (running) return true
        val address = Lan.ownAddress() ?: return false
        code = (1..6).map { "abcdefghjkmnpqrstuvwxyz23456789".random() }.joinToString("")
        val s = HttpServer("veloura-party", PORT) { req, res -> handle(req, res) }
        if (!s.start()) return false
        server = s
        _link.value = "http://$address:${s.port}/p/$code"
        return true
    }

    fun stop() {
        server?.stop()
        server = null
        _link.value = null
        found.clear()
    }

    fun block(from: String) {
        blocked += from
        _added.value = _added.value.filter { it.from != from }
    }

    fun forget(a: Added) { _added.value = _added.value - a }

    private fun handle(req: HttpRequest, res: HttpResponse) {
        // A web page elsewhere pointing its own name at this phone is refused (Prism Connect's rule).
        if (!Lan.hostIsLocalName(req.header("host"))) return res.text(403, "Not here")
        val base = "/p/$code"
        if (code.isEmpty() || !(req.path == base || req.path.startsWith("$base/"))) return res.text(404, "This party has ended.")
        val from = req.remote.hostAddress ?: "?"
        if (from in blocked) return res.text(403, "The host has stopped this phone adding songs.")
        when (req.path.removePrefix(base)) {
            "", "/" -> res.send(200, "text/html; charset=utf-8", page().toByteArray())
            "/search" -> {
                val q = req.query["q"]?.trim().orEmpty().take(100)
                if (q.isEmpty()) return res.json(200, "[]")
                val songs = runCatching {
                    runBlocking { c.ytm.search(q, SearchFilter.SONGS).items.filterIsInstance<SongItem>().map { it.song } }
                }.getOrDefault(emptyList()).filter { !it.isEpisode && !it.isLive }.take(15)
                songs.forEach { found[it.id] = it }
                res.json(200, buildJsonArray {
                    songs.forEach { s -> add(buildJsonObject { put("id", s.id); put("title", s.title); put("artist", s.artistText); put("thumb", s.thumbnail ?: "") }) }
                }.toString())
            }
            "/add" -> {
                if (req.method != "POST") return res.text(405, "POST")
                val form = req.bodyText.split('&').associate { kv -> kv.substringBefore('=') to URLDecoder.decode(kv.substringAfter('=', ""), "UTF-8") }
                val song = found[form["id"]] ?: return res.json(404, """{"error":"Search for the song again"}""")
                val now = System.currentTimeMillis()
                val wait = (lastAdd[from] ?: 0L) + ADD_EVERY_MS - now
                if (wait > 0) return res.json(429, """{"error":"Wait ${wait / 1000 + 1} s before adding another"}""")
                lastAdd[from] = now
                val guest = form["name"]?.trim()?.take(24)?.ifBlank { null } ?: "A guest"
                android.os.Handler(android.os.Looper.getMainLooper()).post { c.player.addToQueue(song) }
                _added.value = listOf(Added(song, guest, from, now)) + _added.value
                res.json(200, """{"ok":true}""")
            }
            "/queue" -> {
                val upNext = c.player.upNext.value.mapNotNull { c.player.queue.value.getOrNull(it)?.song }.take(10)
                val playing = c.player.currentSong.value
                res.json(200, buildJsonObject {
                    put("playing", playing?.let { "${it.title} · ${it.artistText}" } ?: "")
                    put("next", JsonArray(upNext.map { JsonPrimitive("${it.title} · ${it.artistText}") }))
                }.toString())
            }
            else -> res.text(404, "Not found")
        }
    }

    /** The guests' page: search, add, and what's playing and next. Plain HTML and a little script, no app needed. */
    private fun page() = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Veloura party</title><style>
body{margin:0;font-family:system-ui,sans-serif;background:#f6f9fe;color:#0b1b3f}
header{background:linear-gradient(135deg,#1d40ba,#2a0f63);color:#fff;padding:18px 16px}
h1{margin:0;font-size:22px;font-style:italic}small{opacity:.75}
main{padding:12px 16px}input,button{font-size:16px;border-radius:12px;border:1px solid #d5deea;padding:10px}
input{width:100%;box-sizing:border-box;margin-top:8px}button{background:#1e6bff;color:#fff;border:0;font-weight:700}
.row{display:flex;align-items:center;gap:10px;background:#fff;border-radius:14px;padding:8px;margin-top:8px}
.row img{width:48px;height:48px;border-radius:10px;object-fit:cover;background:#ddd}.t{flex:1;min-width:0}
.t b,.t span{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.t span{color:#66768c;font-size:13px}
#msg{margin-top:10px;color:#1e6bff;font-weight:600}#now{margin-top:14px;font-size:14px;color:#66768c}
</style></head><body><header><h1>Veloura</h1><small>Add a song to the party. It'll be mixed in.</small></header><main>
<input id="name" placeholder="Your name (optional)"><form id="f"><input id="q" placeholder="Search YouTube Music" autocomplete="off"></form>
<div id="msg"></div><div id="res"></div><div id="now"></div></main><script>
const base=location.pathname.replace(/\/$/,'');const n=document.getElementById('name');n.value=localStorage.getItem('vn')||'';
n.onchange=()=>localStorage.setItem('vn',n.value);const msg=t=>document.getElementById('msg').textContent=t;
const esc=s=>s.replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
document.getElementById('f').onsubmit=async e=>{e.preventDefault();msg('Searching…');
 const r=await fetch(base+'/search?q='+encodeURIComponent(document.getElementById('q').value));const list=await r.json();msg(list.length?'':'Nothing found');
 document.getElementById('res').innerHTML=list.map(s=>'<div class="row"><img src="'+esc(s.thumb)+'"><div class="t"><b>'+esc(s.title)+'</b><span>'+esc(s.artist)+'</span></div><button data-id="'+esc(s.id)+'">Add</button></div>').join('');
 document.querySelectorAll('[data-id]').forEach(b=>b.onclick=async()=>{const r=await fetch(base+'/add',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'id='+encodeURIComponent(b.dataset.id)+'&name='+encodeURIComponent(n.value)});
  const j=await r.json();msg(j.ok?'Added! Veloura will mix it in.':(j.error||'Couldn\'t add'));if(j.ok)b.textContent='Added';});};
async function q(){try{const j=await(await fetch(base+'/queue')).json();document.getElementById('now').innerHTML=(j.playing?'<b>Playing:</b> '+esc(j.playing):'')+(j.next.length?'<br><b>Up next:</b><br>'+j.next.map(esc).join('<br>'):'');}catch(e){}}
q();setInterval(q,8000);</script></body></html>"""

    companion object {
        const val PORT = 8765
        const val ADD_EVERY_MS = 20_000L
    }
}
