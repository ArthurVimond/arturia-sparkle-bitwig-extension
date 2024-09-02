package fr.arthurvimond.bitwig

import com.bitwig.extension.api.PlatformType
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList
import com.bitwig.extension.controller.ControllerExtensionDefinition
import com.bitwig.extension.controller.api.ControllerHost
import java.util.*

class ArturiaSparkleExtensionDefinition : ControllerExtensionDefinition() {
    override fun getName(): String {
        return "Arturia SparkLE"
    }

    override fun getAuthor(): String {
        return "Arthur Vimond"
    }

    override fun getVersion(): String {
        return "0.1.0"
    }

    override fun getId(): UUID {
        return UUID.fromString("59bb5882-9462-4c47-a3c7-f0b1a65b1056")
    }

    override fun getHardwareVendor(): String {
        return "Arturia"
    }

    override fun getHardwareModel(): String {
        return "SparkLE"
    }

    override fun getRequiredAPIVersion(): Int {
        return 16
    }

    override fun getNumMidiInPorts(): Int {
        return 1
    }

    override fun getNumMidiOutPorts(): Int {
        return 1
    }

    override fun listAutoDetectionMidiPortNames(list: AutoDetectionMidiPortNamesList, platformType: PlatformType) {
        val inputNames = mutableListOf("SparkLE")
        val outputNames = mutableListOf("SparkLE")
        list.add(inputNames.toTypedArray(), outputNames.toTypedArray())
    }

    override fun createInstance(host: ControllerHost): ArturiaSparkleExtension {
        return ArturiaSparkleExtension(this, host)
    }
}