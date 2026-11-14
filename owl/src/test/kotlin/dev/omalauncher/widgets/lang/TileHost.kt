package dev.omalauncher.widgets.lang

import org.junit.Assert.assertTrue

/**
 * A made-up launcher for an Ormus tile test: the widget's program on a feed [f] (the http source with `status`), the
 * clock [now], the tile's size, and what a person does to it: a tap on the key that reads so, a submit in the field that
 * asks so, a trigger the engine fires (`on x`). What the widget asked the launcher to do is in [actions].
 */
internal class TileHost(
    private val program: Program,
    f: Value,
    private val height: Int? = 600,
    private val width: Int? = 360,
    now: Double = 1_794_166_000.0,
) : Host {
    private val slots = Array<Value>(program.globals.size) { Null }

    /** A top-level let once read, kept until a slot changes: the engine keeps its lets per frame the same way. */
    private val frame = HashMap<Int, Value>()

    /** What the tile asked the launcher to do, as "name args"; a `run` is "run <command>". */
    val actions = ArrayList<String>()

    init {
        program.globals.forEachIndexed { i, g -> if (g is Global.State) slots[i] = g.init(Env.root(this)) }
        slots[slot("f")] = f
        if (program.globals.any { it.name == "now" }) slots[slot("now")] = Num(now)
        if (program.globals.any { it.name == "tick" }) slots[slot("tick")] = Num(now)
    }

    fun slot(name: String) = program.globals.indexOfFirst { it.name == name }.also { assertTrue("no global $name", it >= 0) }

    fun let(name: String): Value = budget.run { read(slot(name)) }

    fun text(name: String) = (let(name) as Str).v

    fun set(
        name: String,
        value: Value,
    ) = write(slot(name), value)

    /** The `on x` trigger whose `x` is the given global, fired as the engine fires it: the new value is written first. */
    fun fire(
        global: String,
        value: Value,
        old: Value = Null,
    ) {
        write(slot(global), value)
        val on = program.triggers.first { it.kind == TriggerKind.ON && it.expr(Env.root(this)) == value }
        on.action.run(Env.root(this), listOf(value, old))
    }

    override val budget = Budget()

    override fun read(slot: Int): Value = (program.globals[slot] as? Global.Let)?.value?.let { v -> frame.getOrPut(slot) { v.invoke(Env.root(this)) } } ?: slots[slot]

    override fun write(
        slot: Int,
        value: Value,
    ) {
        slots[slot] = value
        frame.clear()
    }

    override fun theme(name: String) = null

    /** What `time` reads in a handler: the seconds since the tile started drawing, set by the test. */
    var clock = 0.0

    override fun time() = clock

    override fun tile(name: String): Value =
        when {
            name == "tileHeight" && height != null -> Num(height.toDouble())
            name == "tileWidth" && width != null -> Num(width.toDouble())
            name == "screenWidth" -> Num(360.0)
            else -> Null
        }

    override fun act(
        name: String,
        args: List<Value>,
    ) {
        actions += "$name ${args.joinToString()}"
    }

    override fun runInto(
        command: String,
        slot: Int,
    ) {
        actions += "run $command"
    }

    /** Taps the one element with an onTap whose Texts read [word] (all of them joined with a space), and returns what that asked for. */
    fun tap(
        nodes: List<WNode>,
        word: String,
    ): List<String> {
        actions.clear()
        var hit = 0
        budget.run { visit(nodes, Env.root(this)) { n, env -> if (n.handlers["onTap"] != null && lines(listOf(n), env).joinToString(" ") == word) { hit++; n.handlers["onTap"]!!.run.invoke(env, emptyList()) } } }
        assertTrue("$hit elements to tap read \"$word\"", hit >= 1)
        return actions.toList()
    }

    /** The color the first Text reading [text] has, as the renderer would be told (a theme name or a color), or null with no such Text. */
    fun colorOfText(
        nodes: List<WNode>,
        text: String,
    ): String? {
        var out: String? = null
        budget.run {
            visit(nodes, Env.root(this)) { n, env ->
                if (out == null && n.kind == "Text" && ((n.props["text"]?.invoke(env) as? Str)?.v ?: "") == text) out = n.props["color"]?.invoke(env)?.toString() ?: "default"
            }
        }
        return out
    }

    /** Whether a key (an element with an onTap) reads [word]. */
    fun hasKey(
        nodes: List<WNode>,
        word: String,
    ): Boolean {
        var hit = false
        budget.run { visit(nodes, Env.root(this)) { n, env -> if (n.handlers["onTap"] != null && lines(listOf(n), env).joinToString(" ") == word) hit = true } }
        return hit
    }

    /** Submits [value] in every Input whose placeholder starts with [placeholder]; how many there were. */
    fun submit(
        nodes: List<WNode>,
        placeholder: String,
        value: String,
    ): Int {
        actions.clear()
        var hit = 0
        budget.run {
            visit(nodes, Env.root(this)) { n, env ->
                if (n.kind == "Input" && ((n.props["placeholder"]?.invoke(env) as? Str)?.v ?: "").startsWith(placeholder)) {
                    hit++
                    n.handlers["onSubmit"]!!.run.invoke(env, listOf(Str(value)))
                }
            }
        }
        return hit
    }

    /** Types [value] in every Input whose placeholder starts with [placeholder] (its onChange); how many there were. */
    fun change(
        nodes: List<WNode>,
        placeholder: String,
        value: String,
    ): Int {
        var hit = 0
        budget.run {
            visit(nodes, Env.root(this)) { n, env ->
                if (n.kind == "Input" && ((n.props["placeholder"]?.invoke(env) as? Str)?.v ?: "").startsWith(placeholder)) {
                    hit++
                    n.handlers["onChange"]!!.run.invoke(env, listOf(Str(value)))
                }
            }
        }
        return hit
    }

    /** How many Inputs the nodes show. */
    fun inputs(nodes: List<WNode>): Int {
        var n = 0
        budget.run { visit(nodes, Env.root(this)) { e, _ -> if (e.kind == "Input") n++ } }
        return n
    }

    private fun visit(
        nodes: List<WNode>,
        env: Env,
        each: (WNode.Element, Env) -> Unit,
    ) {
        for (n in nodes) {
            when (n) {
                is WNode.Element -> {
                    each(n, env)
                    visit(n.children, env, each)
                }
                is WNode.If -> visit(if (n.cond(env).truthy) n.then else n.orElse, env, each)
                is WNode.For -> Builtins.iterable(n.list(env)).forEachIndexed { i, v -> visit(n.body, env.child(v, Num(i.toDouble())), each) }
                is WNode.Lets -> visit(n.children, env.lets(n.values), each)
                is WNode.Use -> visit(n.body, Env(this, Env.root(this), Array(n.args.size) { i -> { n.args[i](env) } }), each)
                is WNode.Draw -> {}
            }
        }
    }

    /** Every Text, in order, as the renderer walks it. */
    fun lines(
        nodes: List<WNode>,
        env: Env = Env.root(this),
    ): List<String> = ArrayList<String>().also { budget.run { walk(nodes, env, it) } }

    private fun walk(
        nodes: List<WNode>,
        env: Env,
        out: MutableList<String>,
    ) {
        for (n in nodes) {
            when (n) {
                is WNode.Element -> {
                    if (n.kind == "Text") out += n.props["text"]?.invoke(env)?.let { (it as? Str)?.v ?: "$it" } ?: ""
                    walk(n.children, env, out)
                }
                is WNode.If -> walk(if (n.cond(env).truthy) n.then else n.orElse, env, out)
                is WNode.For -> Builtins.iterable(n.list(env)).forEachIndexed { i, v -> walk(n.body, env.child(v, Num(i.toDouble())), out) }
                is WNode.Lets -> walk(n.children, env.lets(n.values), out)
                is WNode.Use -> walk(n.body, Env(this, Env.root(this), Array(n.args.size) { i -> { n.args[i](env) } }), out)
                is WNode.Draw -> {}
            }
        }
    }
}
