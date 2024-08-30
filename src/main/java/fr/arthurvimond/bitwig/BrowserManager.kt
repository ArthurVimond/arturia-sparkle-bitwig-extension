package fr.arthurvimond.bitwig

import com.bitwig.extension.controller.api.*
import fr.arthurvimond.bitwig.di.KoinQualifiers
import fr.arthurvimond.bitwig.extension.sendNote
import fr.arthurvimond.bitwig.state.State
import fr.arthurvimond.bitwig.util.Bitwig
import fr.arthurvimond.bitwig.util.Logger
import fr.arthurvimond.bitwig.util.Midi
import fr.arthurvimond.bitwig.util.Sparkle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class BrowserManager(
    private val midiManager: MidiManager,
    private val deviceManager: DeviceManager,
    private val state: State,
    private val noteInput: NoteInput,
    private val midiOut: MidiOut,
    private val drumPadBank: DrumPadBank,
    private val popupBrowser: PopupBrowser,
    private val logger: Logger,
) : KoinComponent {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val instrumentSelectorDevice: Device by inject(named(KoinQualifiers.InstrumentSelectorDevice))
    private val instrumentSelectorChainSelector: ChainSelector by inject(named(KoinQualifiers.InstrumentSelectorChainSelector))

    init {

        popupBrowser.exists().markInterested()

        // Browser Project led feedback
        scope.launch {
            state.isBrowsingInstrumentSelector.collect {
                midiOut.sendNote(Sparkle.Note.BrowserProject, isOn = it)
            }
        }

        // Note input
        scope.launch {
            midiManager.noteOn.collect {
                when (it.note) {
                    Sparkle.Note.BrowserProject -> {
                        val instrumentSelectorDeviceExists = instrumentSelectorDevice.exists().get()
                        val popupBrowserExists = popupBrowser.exists().get()
                        if (instrumentSelectorDeviceExists && !popupBrowserExists) {
                            state.toggleInstrumentSelectorBrowsing()
                        }
                    }

                    Sparkle.Note.BrowserKit -> {
                        if (state.isBrowsingInstrumentSelector.value) return@collect
                        if (!popupBrowser.exists().get()) {
                            deviceManager.findDrumMachineDevice().replaceDeviceInsertionPoint().browse()
                        } else {
                            popupBrowser.cancel()
                        }
                    }

                    Sparkle.Note.BrowserInstrument -> {
                        if (state.isBrowsingInstrumentSelector.value) return@collect
                        val firstPadDevices = deviceManager.findFirstPadDevices()
                        val selectedPad = state.selectedPad.value % drumPadBank.sizeOfBank
                        val firstPadDevice = firstPadDevices[selectedPad]
                        val hasInstrument = firstPadDevice.exists().get()
                        if (hasInstrument) {
                            if (!popupBrowser.exists().get()) {
                                firstPadDevice.replaceDeviceInsertionPoint().browse()
                            } else {
                                popupBrowser.cancel()
                            }
                        } else {
                            val drumPadItems = deviceManager.findDrumPadItems()
                            val drumPadItem = drumPadItems[selectedPad]
                            drumPadItem.insertionPoint().insertBitwigDevice(Bitwig.Instrument.Sampler)

                            deviceManager.setSamplerDefaultValues()

                            // NB: Delay needed
                            delay(50)
                            firstPadDevice.replaceDeviceInsertionPoint().browse()
                        }
                    }

                    Sparkle.Note.BrowserDialPush -> {
                        popupBrowser.commit()
                        deviceManager.setSamplerDefaultValues()

                        val selectedPad = state.selectedPad.value % drumPadBank.sizeOfBank
                        val filterDeviceExists = deviceManager.filterDeviceExists(selectedPad)
                        if (!filterDeviceExists) {
                            val drumPadItems = deviceManager.findDrumPadItems()
                            val drumPadItem = drumPadItems[selectedPad]
                            drumPadItem.insertionPoint().insertBitwigDevice(Bitwig.AudioEffect.Filter)
                            deviceManager.setFilterDefaultValues()
                        }
                    }
                }
            }
        }

        // CC input
        scope.launch {
            midiManager.cc.collect {
                when (it.cc) {
                    Sparkle.CC.BrowserDial -> {
                        when (it.value) {
                            Midi.CC.Minus -> {
                                if (state.isBrowsingInstrumentSelector.value) {
                                    instrumentSelectorChainSelector.activeChainIndex().inc(-1)
                                } else {
                                    popupBrowser.selectPreviousFile()
                                }
                            }

                            Midi.CC.Plus -> {
                                if (state.isBrowsingInstrumentSelector.value) {
                                    instrumentSelectorChainSelector.activeChainIndex().inc(1)
                                } else {
                                    popupBrowser.selectNextFile()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}