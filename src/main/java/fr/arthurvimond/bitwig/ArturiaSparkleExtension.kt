package fr.arthurvimond.bitwig

import com.bitwig.extension.controller.ControllerExtension
import com.bitwig.extension.controller.api.ControllerHost
import com.bitwig.extension.controller.api.MidiOut
import com.bitwig.extension.controller.api.NoteInput
import fr.arthurvimond.bitwig.di.mainModule
import fr.arthurvimond.bitwig.extension.turnOffAllLeds
import fr.arthurvimond.bitwig.util.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

class ArturiaSparkleExtension(private val definition: ArturiaSparkleExtensionDefinition, host: ControllerHost) :
    ControllerExtension(definition, host), KoinComponent {

    private val midiOut by inject<MidiOut>()
    private val noteInput by inject<NoteInput>()
    private val logger by inject<Logger>()

    override fun init() {
        host.showPopupNotification("Arturia SparkLE Initialized")

        startKoin {
            modules(mainModule(host))
        }

        // Needed for the input CC to be recorded as automation
        noteInput.setShouldConsumeEvents(false)

        val transportManager by inject<TransportManager>()
        val stepsManager by inject<StepsManager>()
        val padsManager by inject<PadsManager>()
        val padControlsManager by inject<PadControlsManager>()
        val browserManager by inject<BrowserManager>()
        val parametersManager by inject<ParametersManager>()

        // NB: Needed to instantiate them
        transportManager
        stepsManager
        padsManager
        padControlsManager
        browserManager
        parametersManager
    }

    override fun exit() {
        host.showPopupNotification("Arturia SparkLE Exited")
        midiOut.turnOffAllLeds()
        stopKoin()
    }

    override fun flush() {
    }
}