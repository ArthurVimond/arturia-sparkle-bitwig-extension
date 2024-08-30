package fr.arthurvimond.bitwig

import com.bitwig.extension.controller.api.*
import fr.arthurvimond.bitwig.extension.sendNoteOff
import fr.arthurvimond.bitwig.extension.sendNoteOn
import fr.arthurvimond.bitwig.state.ParameterBank
import fr.arthurvimond.bitwig.state.State
import fr.arthurvimond.bitwig.util.Logger
import fr.arthurvimond.bitwig.util.Sparkle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

class ParametersManager(
    private val hardwareSurface: HardwareSurface,
    private val midiManager: MidiManager,
    private val deviceManager: DeviceManager,
    private val state: State,
    private val midiIn: MidiIn,
    private val midiOut: MidiOut,
    private val cursorTrack: CursorTrack,
    private val drumPadBank: DrumPadBank,
    private val logger: Logger,
) : KoinComponent {

    private val scope = CoroutineScope(Dispatchers.Default)

    init {

        scope.launch {
            midiManager.noteOn.collect {
                when (it.note) {
                    Sparkle.Note.LoopOn -> {

                    }
                }
            }
        }

        // Volume knob
        scope.launch {
            midiManager.cc.collect {
                when (it.cc) {
                    Sparkle.CC.Volume -> {
                        cursorTrack.volume().inc(it.value - 64, 128)
                    }
                }
            }
        }

        // Parameter bank note inputs
        scope.launch {
            midiManager.noteOn.collect {
                when (it.note) {
                    Sparkle.Note.InstrumentFilter -> state.setParameterBank(ParameterBank.Filter)
                    Sparkle.Note.Send1_2 -> state.setParameterBank(ParameterBank.Sends)
                    Sparkle.Note.Pan_Level -> state.setParameterBank(ParameterBank.PanLevel)
                }
            }
        }

        // Parameter bank led feedback
        scope.launch {
            state.parameterBank.collect {
                // Filter
                if (it == ParameterBank.Filter) {
                    midiOut.sendNoteOn(Sparkle.Note.InstrumentFilter)
                } else {
                    midiOut.sendNoteOff(Sparkle.Note.InstrumentFilter)
                }

                // Sends
                if (it == ParameterBank.Sends) {
                    midiOut.sendNoteOn(Sparkle.Note.Send1_2)
                } else {
                    midiOut.sendNoteOff(Sparkle.Note.Send1_2)
                }

                // Pan Level
                if (it == ParameterBank.PanLevel) {
                    midiOut.sendNoteOn(Sparkle.Note.Pan_Level)
                } else {
                    midiOut.sendNoteOff(Sparkle.Note.Pan_Level)
                }
            }
        }

        // Param knobs
        scope.launch {
            midiManager.cc.collect {
                when (it.cc) {
                    Sparkle.CC.Param1 -> {
                        when (state.parameterBank.value) {
                            ParameterBank.Filter -> deviceManager.setFilterType(it)
                            ParameterBank.Sends -> deviceManager.setSendLevel(sendIndex = 0, it)
                            ParameterBank.PanLevel -> deviceManager.setPan(it)
                        }
                    }

                    Sparkle.CC.Param2 -> {
                        when (state.parameterBank.value) {
                            ParameterBank.Filter -> deviceManager.setFilterResonance(it)
                            ParameterBank.Sends -> deviceManager.setSendLevel(sendIndex = 1, it)
                            ParameterBank.PanLevel -> deviceManager.setVolume(it)
                        }

                    }

                    Sparkle.CC.Param3 -> {
                        when (state.parameterBank.value) {
                            ParameterBank.Filter -> deviceManager.setFilterCutoff(it)
                            ParameterBank.Sends -> deviceManager.setSendLevel(sendIndex = 2, it)
                            ParameterBank.PanLevel -> deviceManager.setDecay(it)
                        }
                    }
                }
            }
        }
    }

}