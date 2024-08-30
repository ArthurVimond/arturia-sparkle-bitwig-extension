package fr.arthurvimond.bitwig

import com.bitwig.extension.controller.api.*
import fr.arthurvimond.bitwig.di.KoinQualifiers
import fr.arthurvimond.bitwig.extension.sendNoteOff
import fr.arthurvimond.bitwig.extension.sendNoteOn
import fr.arthurvimond.bitwig.model.MidiCC
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

class DeviceManager(
    private val state: State,
    private val drumPadBank: DrumPadBank,
    private val midiOut: MidiOut,
    private val logger: Logger,
) : KoinComponent {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val samplerDeviceMatcher: DeviceMatcher by inject(named(KoinQualifiers.SamplerDeviceMatcher))
    private val eKickDeviceMatcher: DeviceMatcher by inject(named(KoinQualifiers.EKickDeviceMatcher))
    private val eSnareDeviceMatcher: DeviceMatcher by inject(named(KoinQualifiers.ESnareDeviceMatcher))
    private val eClapDeviceMatcher: DeviceMatcher by inject(named(KoinQualifiers.EClapDeviceMatcher))
    private val eHatDeviceMatcher: DeviceMatcher by inject(named(KoinQualifiers.EHatDeviceMatcher))

    private val filterDeviceMatcher: DeviceMatcher by inject(named(KoinQualifiers.FilterDeviceMatcher))

    // Root Drum Machine
    private val rootDrumMachineDevice: Device by inject(named(KoinQualifiers.DrumMachineDevice))
    private val rootDrumPadItems: List<DrumPad> =
        Sparkle.Note.Pads.mapIndexed { index, _ ->
            val item = drumPadBank.getItemAt(index)
            item.mute().markInterested()
            item.solo().markInterested()
            item
        }
    private val rootPadDeviceBanks: List<DeviceBank> = rootDrumPadItems.map {
        val deviceBank = it.createDeviceBank(1)
        deviceBank.setDeviceMatcher(samplerDeviceMatcher)
        deviceBank
    }
    private val rootFirstPadDevices: List<Device> = rootPadDeviceBanks.mapIndexed { index, bank ->
        val device = bank.getItemAt(0)
        device.exists().markInterested()
        device.presetName().markInterested()
        device
    }

    // Instrument Selector
    private val instrumentSelectorDevice: Device by inject(named(KoinQualifiers.InstrumentSelectorDevice))
    private val nestedDrumMachineDevice: Device by inject(named(KoinQualifiers.InstrumentSelectorChainCursorDevice))
    private val instrumentSelectorChainSelector: ChainSelector by inject(named(KoinQualifiers.InstrumentSelectorChainSelector))
    private val instrumentSelectorLayerBank: DeviceLayerBank = instrumentSelectorDevice.createLayerBank(32)

    val nestedDrumPadBank: DrumPadBank = nestedDrumMachineDevice.createDrumPadBank(8)
    private val nestedDrumPadItems: List<DrumPad> = Sparkle.Note.Pads
        .mapIndexed { index, _ ->
            val item = nestedDrumPadBank.getItemAt(index)
            item.mute().markInterested()
            item.solo().markInterested()
            item
        }
    private val nestedPadDeviceBanks: List<DeviceBank> = nestedDrumPadItems.map {
        val deviceBank = it.createDeviceBank(1)
        deviceBank.setDeviceMatcher(samplerDeviceMatcher)
        deviceBank
    }
    private val nestedFirstPadDevices: List<Device> = nestedPadDeviceBanks
        .map { bank ->
            val device = bank.getItemAt(0)
            device.exists().markInterested()
            device.presetName().markInterested()
            device
        }

    // Sampler
    private val rootSamplerModeParams: List<Parameter> = rootFirstPadDevices.map { it.toSamplerModeParam() }
    private val rootSamplerSustainParams: List<Parameter> = rootFirstPadDevices.map { it.toSamplerSustainParam() }

    private val nestedSamplerModeParams: List<Parameter> = nestedFirstPadDevices.map { it.toSamplerModeParam() }
    private val nestedSamplerSustainParams: List<Parameter> = nestedFirstPadDevices.map { it.toSamplerSustainParam() }

    // Top level Drum machine sampler device
    private val rootSamplerDevices: List<Device> = rootDrumPadItems.map { item ->
        val deviceBank = item.createDeviceBank(1)
        deviceBank.setDeviceMatcher(samplerDeviceMatcher)
        val device = deviceBank.getItemAt(0)
        device.exists().markInterested()
        device
    }

    // Nested in InstrumentSelector
    private val nestedSamplerDevices: List<Device> = nestedDrumPadItems.map { item ->
        val deviceBank = item.createDeviceBank(1)
        deviceBank.setDeviceMatcher(samplerDeviceMatcher)
        val device = deviceBank.getItemAt(0)
        device.exists().markInterested()
        device
    }
    private val nestedEKickDevices: List<Device> = nestedDrumPadItems.map { item ->
        val deviceBank = item.createDeviceBank(1)
        deviceBank.setDeviceMatcher(eKickDeviceMatcher)
        val device = deviceBank.getItemAt(0)
        device.exists().markInterested()
        device
    }

    private val nestedESnareDevices: List<Device> = nestedDrumPadItems.map { item ->
        val deviceBank = item.createDeviceBank(1)
        deviceBank.setDeviceMatcher(eSnareDeviceMatcher)
        val device = deviceBank.getItemAt(0)
        device.exists().markInterested()
        device
    }

    private val nestedEClapDevices: List<Device> = nestedDrumPadItems.map { item ->
        val deviceBank = item.createDeviceBank(1)
        deviceBank.setDeviceMatcher(eClapDeviceMatcher)
        val device = deviceBank.getItemAt(0)
        device.exists().markInterested()
        device
    }

    private val nestedEHatDevices: List<Device> = nestedDrumPadItems.map { item ->
        val deviceBank = item.createDeviceBank(1)
        deviceBank.setDeviceMatcher(eHatDeviceMatcher)
        val device = deviceBank.getItemAt(0)
        device.exists().markInterested()
        device
    }

    private val nestedSamplerDeviceControlPage: List<RemoteControlsPage> = nestedSamplerDevices.map { device ->
        device.exists().markInterested()
        val page = device.createCursorRemoteControlsPage(8)
        val param = page.getParameter(3)
        param.exists().markInterested()
        param.name().markInterested()
        page
    }

    private val nestedFilterDevices: List<Device> = nestedDrumPadItems.map { item ->
        val deviceBank = item.createDeviceBank(1)
        deviceBank.setDeviceMatcher(filterDeviceMatcher)
        val device = deviceBank.getItemAt(0)
        device.exists().markInterested()
        device
    }

    private val rootEKickDevices: List<Device> = rootDrumPadItems.map { item ->
        val deviceBank = item.createDeviceBank(1)
        deviceBank.setDeviceMatcher(eKickDeviceMatcher)
        val device = deviceBank.getItemAt(0)
        device.exists().markInterested()
        device
    }

    private val rootESnareDevices: List<Device> = rootDrumPadItems.map { item ->
        val deviceBank = item.createDeviceBank(1)
        deviceBank.setDeviceMatcher(eSnareDeviceMatcher)
        val device = deviceBank.getItemAt(0)
        device.exists().markInterested()
        device
    }

    private val rootEClapDevices: List<Device> = rootDrumPadItems.map { item ->
        val deviceBank = item.createDeviceBank(1)
        deviceBank.setDeviceMatcher(eClapDeviceMatcher)
        val device = deviceBank.getItemAt(0)
        device.exists().markInterested()
        device
    }

    private val rootEHatDevices: List<Device> = rootDrumPadItems.map { item ->
        val deviceBank = item.createDeviceBank(1)
        deviceBank.setDeviceMatcher(eHatDeviceMatcher)
        val device = deviceBank.getItemAt(0)
        device.exists().markInterested()
        device
    }

    private val rootFilterDevices: List<Device> = rootDrumPadItems.map { item ->
        val deviceBank = item.createDeviceBank(1)
        deviceBank.setDeviceMatcher(filterDeviceMatcher)
        val device = deviceBank.getItemAt(0)
        device.exists().markInterested()
        device
    }

    private val rootSamplerDeviceControlPage: List<RemoteControlsPage> = rootSamplerDevices.map { device ->
        device.exists().markInterested()
        val page = device.createCursorRemoteControlsPage(8)
        val param = page.getParameter(3)
        param.exists().markInterested()
        param.name().markInterested()
        page
    }

    // Root
    private val rootSamplerDecayParams: List<Parameter> = rootSamplerDevices.map { it.toSamplerDecayParam() }
    private val rootSamplerSpeedParams: List<Parameter> = rootSamplerDevices.map {
        val param = it.toSamplerSpeedParam()
        param.value().markInterested()
        param
    }
    private val rootEKickDecayParams: List<Parameter> = rootEKickDevices.map { it.toEKickDecayParam() }
    private val rootESnareDecayParams: List<Parameter> = rootESnareDevices.map { it.toESnareDecayParam() }
    private val rootEClapDecayParams: List<Parameter> = rootEClapDevices.map { it.toEClapDecayParam() }
    private val rootEHatDecayParams: List<Parameter> = rootEHatDevices.map { it.toEHatDecayParam() }

    private val rootFilterTypeParams: List<Parameter> = rootFilterDevices.map { it.toFilterTypeParam() }
    private val rootFilterCutoffParams: List<Parameter> = rootFilterDevices.map { it.toFilterCutoffParam() }
    private val rootFilterResonanceParams: List<Parameter> = rootFilterDevices.map { it.toFilterResonanceParam() }

    // Nested in InstrumentSelector
    private val nestedSamplerDecayParams: List<Parameter> = nestedSamplerDevices.map { it.toSamplerDecayParam() }
    private val nestedSamplerSpeedParams: List<Parameter> = nestedSamplerDevices.map {
        val param = it.toSamplerSpeedParam()
        param.value().markInterested()
        param
    }
    private val nestedEKickDecayParams: List<Parameter> = nestedEKickDevices.map { it.toEKickDecayParam() }
    private val nestedESnareDecayParams: List<Parameter> = nestedESnareDevices.map { it.toESnareDecayParam() }
    private val nestedEClapDecayParams: List<Parameter> = nestedEClapDevices.map { it.toEClapDecayParam() }
    private val nestedEHatDecayParams: List<Parameter> = nestedEHatDevices.map { it.toEHatDecayParam() }

    private val nestedFilterTypeParams: List<Parameter> = nestedFilterDevices.map { it.toFilterTypeParam() }
    private val nestedFilterCutoffParams: List<Parameter> = nestedFilterDevices.map { it.toFilterCutoffParam() }
    private val nestedFilterResonanceParams: List<Parameter> = nestedFilterDevices.map { it.toFilterResonanceParam() }

    private val isInstrumentSelector: Boolean
        get() = instrumentSelectorDevice.exists().get()

    init {

        nestedDrumMachineDevice.presetName().markInterested()
        instrumentSelectorDevice.exists().markInterested()

        instrumentSelectorChainSelector.activeChainIndex().markInterested()
        instrumentSelectorChainSelector.activeChainIndex().addValueObserver {
            // Select the corresponding device
            instrumentSelectorLayerBank.getItemAt(it).selectInEditor()
        }
    }

    fun findDrumMachineDevice(): Device {
        return if (isInstrumentSelector) {
            nestedDrumMachineDevice
        } else {
            rootDrumMachineDevice
        }
    }

    fun findFirstPadDevices(): List<Device> {
        return if (isInstrumentSelector) {
            nestedFirstPadDevices
        } else {
            rootFirstPadDevices
        }
    }

    fun findDrumPadItems(): List<DrumPad> {
        return if (isInstrumentSelector) {
            nestedDrumPadItems
        } else {
            rootDrumPadItems
        }
    }

    fun findDrumPadBank(): DrumPadBank {
        return if (isInstrumentSelector) {
            nestedDrumPadBank
        } else {
            drumPadBank
        }
    }

    fun findDrumPadItem(): DrumPad {
        val selectedPad = state.selectedPad.value % drumPadBank.sizeOfBank
        return findDrumPadBank().getItemAt(selectedPad)
    }

    fun filterDeviceExists(index: Int): Boolean {
        return if (isInstrumentSelector) {
            return nestedFilterDevices[index].exists().get()
        } else {
            return rootFilterDevices[index].exists().get()
        }
    }

    fun switchDrumPadBank(switched: Boolean) {
        if (switched) {
            drumPadBank.scrollPageForwards()
            nestedDrumPadBank.scrollPageForwards()
            midiOut.sendNoteOn(Sparkle.Note.PadBankSwitcher)
        } else {
            drumPadBank.scrollPageBackwards()
            nestedDrumPadBank.scrollPageBackwards()
            midiOut.sendNoteOff(Sparkle.Note.PadBankSwitcher)
        }
    }

    // Set default values for Mode and Sustain
    // NB: Need delay before settings default values after device creation
    fun setSamplerDefaultValues() {
        scope.launch {
            delay(200)
            val selectedPad = state.selectedPad.value % drumPadBank.sizeOfBank
            val modeParams = if (isInstrumentSelector) nestedSamplerModeParams else rootSamplerModeParams
            val sustainParams = if (isInstrumentSelector) nestedSamplerSustainParams else rootSamplerSustainParams
            modeParams[selectedPad].value().set(0.0) // Set ADSR Mode
            sustainParams[selectedPad].value().raw = 0.0 // Set Sustain to 0
        }
    }

    private fun setFilterLowPassType() {
        val selectedPad = state.selectedPad.value % drumPadBank.sizeOfBank
        val typeParams = if (isInstrumentSelector) nestedFilterTypeParams else rootFilterTypeParams
        val cutoffParams = if (isInstrumentSelector) nestedFilterCutoffParams else rootFilterCutoffParams
        val resonanceParams = if (isInstrumentSelector) nestedFilterResonanceParams else rootFilterResonanceParams
        typeParams[selectedPad].value().setImmediately(0.0)
        cutoffParams[selectedPad].value().setImmediately(1.0)
        resonanceParams[selectedPad].value().setImmediately(0.3)
    }

    private fun setFilterHighPassType() {
        val selectedPad = state.selectedPad.value % drumPadBank.sizeOfBank
        val typeParams = if (isInstrumentSelector) nestedFilterTypeParams else rootFilterTypeParams
        val cutoffParams = if (isInstrumentSelector) nestedFilterCutoffParams else rootFilterCutoffParams
        val resonanceParams = if (isInstrumentSelector) nestedFilterResonanceParams else rootFilterResonanceParams
        typeParams[selectedPad].value().setImmediately(0.5)
        cutoffParams[selectedPad].value().setImmediately(0.0)
        resonanceParams[selectedPad].value().setImmediately(0.3)
    }

    fun setFilterDefaultValues() {
        scope.launch {
            delay(200)
            val selectedPad = state.selectedPad.value % drumPadBank.sizeOfBank
            val cutoffParams = if (isInstrumentSelector) nestedFilterCutoffParams else rootFilterCutoffParams
            val resonanceParams = if (isInstrumentSelector) nestedFilterResonanceParams else rootFilterResonanceParams
            cutoffParams[selectedPad].value().setImmediately(1.0)
            resonanceParams[selectedPad].value().setImmediately(0.3)
        }
    }

    fun setPan(cc: MidiCC) {
        val drumPadItem = findDrumPadItem()
        drumPadItem.pan().value().inc(cc.value - 64, 128)
    }

    fun setVolume(cc: MidiCC) {
        val drumPadItem = findDrumPadItem()
        drumPadItem.volume().value().inc(cc.value - 64, 128)
    }

    fun setSendLevel(sendIndex: Int, cc: MidiCC) {
        val drumPadItem = findDrumPadItem()
        drumPadItem.sendBank().getItemAt(sendIndex).value().inc(cc.value - 64, 64)
    }

    fun setDecay(cc: MidiCC) {
        val selectedPad = state.selectedPad.value % drumPadBank.sizeOfBank

        // First check if RemoteControl Parameter "Release" (which has a param id of "V4") exists to act as a decay,
        // if not change Sampler real Decay parameter

        if (isInstrumentSelector) {
            // Nested drum machine
            val releaseParameter = nestedSamplerDeviceControlPage[selectedPad].getParameter(3)
            val releaseParamExists = releaseParameter.exists().get()
            val releaseParamName = releaseParameter.name().get()

            // Check for corresponding device
            val eKickDecayParam = nestedEKickDecayParams[selectedPad]
            val eSnareDecayParam = nestedESnareDecayParams[selectedPad]
            val eClapDecayParam = nestedEClapDecayParams[selectedPad]
            val eHatDecayParam = nestedEHatDecayParams[selectedPad]
            val isEKickDevice = eKickDecayParam.exists().get()
            val isESnareDevice = eSnareDecayParam.exists().get()
            val isEClapDevice = eClapDecayParam.exists().get()
            val isEHatDevice = eHatDecayParam.exists().get()
            val value: SettableRangedValue = when {
                isEKickDevice -> eKickDecayParam.value()
                isESnareDevice -> eSnareDecayParam.value()
                isEClapDevice -> eClapDecayParam.value()
                isEHatDevice -> eHatDecayParam.value()
                else -> nestedSamplerDecayParams[selectedPad].value()
            }
            value.inc(cc.value - 64, 64)

            // Also change Release param (additionally to Decay)
            if (releaseParamExists && (releaseParamName == "Release" || releaseParamName == "Decay")) {
                releaseParameter.value().inc(cc.value - 64, 64)
            }

        } else {
            // Root drum machine
            val releaseParameter = rootSamplerDeviceControlPage[selectedPad].getParameter(3)
            val releaseParamExists = releaseParameter.exists().get()
            val releaseParamName = releaseParameter.name().get()

            // Check for corresponding device
            val eKickDecayParam = rootEKickDecayParams[selectedPad]
            val eSnareDecayParam = rootESnareDecayParams[selectedPad]
            val eClapDecayParam = rootEClapDecayParams[selectedPad]
            val eHatDecayParam = rootEHatDecayParams[selectedPad]
            val isEKickDevice = eKickDecayParam.exists().get()
            val isESnareDevice = eSnareDecayParam.exists().get()
            val isEClapDevice = eClapDecayParam.exists().get()
            val isEHatDevice = eHatDecayParam.exists().get()
            val value: SettableRangedValue = when {
                isEKickDevice -> eKickDecayParam.value()
                isESnareDevice -> eSnareDecayParam.value()
                isEClapDevice -> eClapDecayParam.value()
                isEHatDevice -> eHatDecayParam.value()
                else -> rootSamplerDecayParams[selectedPad].value()
            }
            value.inc(cc.value - 64, 64)

            // Also change Release param (additionally to Decay)
            if (releaseParamExists && (releaseParamName == "Release" || releaseParamName == "Decay")) {
                releaseParameter.value().inc(cc.value - 64, 64)
            }
        }
    }

    fun setSamplerTransposition(semitone: Int) {
        val selectedPad = state.selectedPad.value % drumPadBank.sizeOfBank
        val speedParam = if (isInstrumentSelector) {
            nestedSamplerSpeedParams[selectedPad]
        } else {
            rootSamplerSpeedParams[selectedPad]
        }
        val value = Bitwig.SamplerSpeedMap[semitone] ?: return
        speedParam.value().setImmediately(value)
    }

    fun setFilterType(cc: MidiCC) {
        when (cc.value) {
            Midi.CC.Minus -> setFilterLowPassType()
            Midi.CC.Plus -> setFilterHighPassType()
        }
    }

    fun setFilterCutoff(cc: MidiCC) {
        val selectedPad = state.selectedPad.value % drumPadBank.sizeOfBank
        val params = if (isInstrumentSelector) nestedFilterCutoffParams else rootFilterCutoffParams
        params[selectedPad].value().inc(cc.value - 64, 64)
    }

    fun setFilterResonance(cc: MidiCC) {
        val selectedPad = state.selectedPad.value % drumPadBank.sizeOfBank
        val params = if (isInstrumentSelector) nestedFilterResonanceParams else rootFilterResonanceParams
        params[selectedPad].value().inc(cc.value - 64, 64)
    }

    private fun Device.toSamplerModeParam(): Parameter {
        val specificSamplerDevice = createSpecificBitwigDevice(Bitwig.Instrument.Sampler)
        return specificSamplerDevice.createParameter("MODE")
    }

    private fun Device.toSamplerSustainParam(): Parameter {
        val specificSamplerDevice = createSpecificBitwigDevice(Bitwig.Instrument.Sampler)
        return specificSamplerDevice.createParameter("AMP_SUSTAIN_LEVEL")
    }

    private fun Device.toSamplerDecayParam(): Parameter {
        val specificSamplerDevice = createSpecificBitwigDevice(Bitwig.Instrument.Sampler)
        return specificSamplerDevice.createParameter("AMP_DECAY_TIME")
    }

    private fun Device.toSamplerSpeedParam(): Parameter {
        val specificSamplerDevice = createSpecificBitwigDevice(Bitwig.Instrument.Sampler)
        return specificSamplerDevice.createParameter("SPEED")
    }

    private fun Device.toEKickDecayParam(): Parameter {
        val specificDevice = createSpecificBitwigDevice(Bitwig.Instrument.EKick)
        val parameter = specificDevice.createParameter("DECAY")
        parameter.exists().markInterested()
        return parameter
    }

    private fun Device.toESnareDecayParam(): Parameter {
        val specificDevice = createSpecificBitwigDevice(Bitwig.Instrument.ESnare)
        val parameter = specificDevice.createParameter("OSC_1_DECAY")
        parameter.exists().markInterested()
        return parameter
    }

    private fun Device.toEClapDecayParam(): Parameter {
        val specificDevice = createSpecificBitwigDevice(Bitwig.Instrument.EClap)
        val parameter = specificDevice.createParameter("DECAY")
        parameter.exists().markInterested()
        return parameter
    }

    private fun Device.toEHatDecayParam(): Parameter {
        val specificDevice = createSpecificBitwigDevice(Bitwig.Instrument.EHat)
        val parameter = specificDevice.createParameter("DECAY")
        parameter.exists().markInterested()
        return parameter
    }

    private fun Device.toFilterTypeParam(): Parameter {
        val specificDevice = createSpecificBitwigDevice(Bitwig.AudioEffect.Filter)
        val parameter = specificDevice.createParameter("FILTER_TYPE")
        parameter.exists().markInterested()
        return parameter
    }

    private fun Device.toFilterCutoffParam(): Parameter {
        val specificDevice = createSpecificBitwigDevice(Bitwig.AudioEffect.Filter)
        val parameter = specificDevice.createParameter("CUTOFF")
        parameter.exists().markInterested()
        return parameter
    }

    private fun Device.toFilterResonanceParam(): Parameter {
        val specificDevice = createSpecificBitwigDevice(Bitwig.AudioEffect.Filter)
        val parameter = specificDevice.createParameter("RESONANCE")
        parameter.exists().markInterested()
        return parameter
    }
}