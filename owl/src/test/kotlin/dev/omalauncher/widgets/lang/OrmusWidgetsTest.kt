package dev.omalauncher.widgets.lang

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The widgets only the Ormus build ships. LangTest.bundledWidgetsCompile reads the upstream folder. */
class OrmusWidgetsTest {
    /** The Ormus build seeds these on home: one that does not compile would show an error tile there. */
    @Test fun ormusWidgetsCompile() {
        val dir = java.io.File("../app/src/main/assets/ormus/widgets")
        val files = dir.walk().filter { it.extension == "owl" }.toList()
        assertTrue("no widgets found in ${dir.absolutePath}", files.size > 5)
        for (f in files) {
            try {
                Compiler.compile(f.readText())
            } catch (e: CompileError) {
                fail("${f.name}: ${Source(f.readText()).format(e)}")
            }
        }
    }
}
