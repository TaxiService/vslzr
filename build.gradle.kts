import java.io.ByteArrayOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

// ---- Task impl (no deprecated exec) ----
abstract class PullDevicePrefs : DefaultTask() {
    @get:Inject abstract val execOps: ExecOperations

    @TaskAction
    fun run() {
        val pkg = "dev.taxi.vslzr"
        val outFile = project.file("app/src/main/res/xml/prefs_seed.xml")
        outFile.parentFile.mkdirs()

        val buf = ByteArrayOutputStream()
        val res = execOps.exec {
            commandLine(
                "adb", "shell", "run-as", pkg,
                "cat", "/data/data/$pkg/shared_prefs/vslzr_prefs.xml"
            )
            isIgnoreExitValue = true
            standardOutput = buf
        }
        if (res.exitValue == 0) {
            outFile.writeText(buf.toString(Charsets.UTF_8))
            println("prefs_seed.xml updated from device.")
        } else {
            println("WARN: could not pull device prefs. Is the debug app installed and a device connected?")
        }
    }
}

// ---- Register + hook before :app:installDebug when -PsyncPrefs=true ----
tasks.register<PullDevicePrefs>("pullDevicePrefs")

project(":app") {
    tasks.matching { it.name == "installDebug" }.configureEach {
        if (rootProject.hasProperty("syncPrefs")) {
            dependsOn(rootProject.tasks.named("pullDevicePrefs"))
        }
    }
}
