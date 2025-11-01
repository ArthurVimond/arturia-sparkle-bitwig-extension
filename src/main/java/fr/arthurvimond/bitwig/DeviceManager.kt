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
import java.util.*

class DeviceManager(
    private val host: ControllerHost,
    private val state: State,
    private val drumPadBank: DrumPadBank,
    private val midiOut: MidiOut,
    private val logger: Logger,
) : KoinComponent {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val samplerDeviceMatcher: DeviceMatcher by inject(named(KoinQualifiers.SamplerDeviceMatcher))

    // V0 drums
    private val v0DrumPartDeviceIds: List<UUID> = listOf(
        Bitwig.Instrument.V0Kick,
        Bitwig.Instrument.V0ZapKick,
        Bitwig.Instrument.V0Snare,
        Bitwig.Instrument.V0Hat,
        Bitwig.Instrument.V0Cymbal,
        Bitwig.Instrument.V0Tom,
    )

    // V1 drums
    private val v1DrumPartDeviceIds: List<UUID> = listOf(
        Bitwig.Instrument.V1Kick,
        Bitwig.Instrument.V1Snare,
        Bitwig.Instrument.V1Clap,
        Bitwig.Instrument.V1Hat,
        Bitwig.Instrument.V1Tom,
        Bitwig.Instrument.V1Cowbell,
    )

    // V8 drums
    private val v8DrumPartDeviceIds: List<UUID> = listOf(
        Bitwig.Instrument.V8Kick,
        Bitwig.Instrument.V8Snare,
        Bitwig.Instrument.V8Clap,
        Bitwig.Instrument.V8Hat,
        Bitwig.Instrument.V8Maracas,
        Bitwig.Instrument.V8Cymbal,
        Bitwig.Instrument.V8Tom,
        Bitwig.Instrument.V8Claves,
        Bitwig.Instrument.V8Cowbell,
    )

    // V9 drums
    private val v9DrumPartDeviceIds: List<UUID> = listOf(
        Bitwig.Instrument.V9Kick,
        Bitwig.Instrument.V9Snare,
        Bitwig.Instrument.V9Clap,
        Bitwig.Instrument.V9Tom,
        Bitwig.Instrument.V9HatClosed,
        Bitwig.Instrument.V9HatOpen,
        Bitwig.Instrument.V9Ride,
        Bitwig.Instrument.V9Crash,
        Bitwig.Instrument.V9Rimshot,
    )

    // V0 drums
    private val v0DrumPartDeviceMatchers: Map<UUID, DeviceMatcher> = v0DrumPartDeviceIds
        .mapIndexed { index, deviceId -> deviceId to getDeviceMatcher(deviceId) }
        .toMap()

    // V1 drums
    private val v1DrumPartDeviceMatchers: Map<UUID, DeviceMatcher> = v1DrumPartDeviceIds
        .mapIndexed { index, deviceId -> deviceId to getDeviceMatcher(deviceId) }
        .toMap()

    // V8 drums
    private val v8DrumPartDeviceMatchers: Map<UUID, DeviceMatcher> = v8DrumPartDeviceIds
        .mapIndexed { index, deviceId -> deviceId to getDeviceMatcher(deviceId) }
        .toMap()

    // V9 drums
    private val v9DrumPartDeviceMatchers: Map<UUID, DeviceMatcher> = v9DrumPartDeviceIds
        .mapIndexed { index, deviceId -> deviceId to getDeviceMatcher(deviceId) }
        .toMap()

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
    private val instrumentSelectorChainSelector: ChainSelector by inject(named(KoinQualifiers.InstrumentSelectorChainSelector))
    private val nestedDrumMachineDevice: Device by inject(named(KoinQualifiers.InstrumentSelectorActiveChainDevice))

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
        getDeviceForDrumPad(drumPad = item, deviceMatcher = samplerDeviceMatcher)
    }

    // Nested in InstrumentSelector
    private val nestedSamplerDevices: List<Device> = nestedDrumPadItems.map { item ->
        getDeviceForDrumPad(drumPad = item, deviceMatcher = samplerDeviceMatcher)
    }

    // V1 drums
    private val nestedV0DrumPartDevices: List<Map<UUID, Device>> = nestedDrumPadItems.map { item ->
        v0DrumPartDeviceMatchers.mapValues { (deviceId, deviceMatcher) ->
            getDeviceForDrumPad(drumPad = item, deviceMatcher = deviceMatcher)
        }
    }

    // V1 drums
    private val nestedV1DrumPartDevices: List<Map<UUID, Device>> = nestedDrumPadItems.map { item ->
        v1DrumPartDeviceMatchers.mapValues { (deviceId, deviceMatcher) ->
            getDeviceForDrumPad(drumPad = item, deviceMatcher = deviceMatcher)
        }
    }

    // V8 drums
    private val nestedV8DrumPartDevices: List<Map<UUID, Device>> = nestedDrumPadItems.map { item ->
        v8DrumPartDeviceMatchers.mapValues { (deviceId, deviceMatcher) ->
            getDeviceForDrumPad(drumPad = item, deviceMatcher = deviceMatcher)
        }
    }

    // V9 drums
    private val nestedV9DrumPartDevices: List<Map<UUID, Device>> = nestedDrumPadItems.map { item ->
        v9DrumPartDeviceMatchers.mapValues { (deviceId, deviceMatcher) ->
            getDeviceForDrumPad(drumPad = item, deviceMatcher = deviceMatcher)
        }
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
        getDeviceForDrumPad(drumPad = item, deviceMatcher = filterDeviceMatcher)
    }

    // V0 drums
    private val rootV0DrumPartDevices: List<Map<UUID, Device>> = rootDrumPadItems.map { item ->
        v0DrumPartDeviceMatchers.mapValues { (deviceId, deviceMatcher) ->
            getDeviceForDrumPad(drumPad = item, deviceMatcher = deviceMatcher)
        }
    }

    // V1 drums
    private val rootV1DrumPartDevices: List<Map<UUID, Device>> = rootDrumPadItems.map { item ->
        v1DrumPartDeviceMatchers.mapValues { (deviceId, deviceMatcher) ->
            getDeviceForDrumPad(drumPad = item, deviceMatcher = deviceMatcher)
        }
    }

    // V8 drums
    private val rootV8DrumPartDevices: List<Map<UUID, Device>> = rootDrumPadItems.map { item ->
        v8DrumPartDeviceMatchers.mapValues { (deviceId, deviceMatcher) ->
            getDeviceForDrumPad(drumPad = item, deviceMatcher = deviceMatcher)
        }
    }

    // V9 drums
    private val rootV9DrumPartDevices: List<Map<UUID, Device>> = rootDrumPadItems.map { item ->
        v9DrumPartDeviceMatchers.mapValues { (deviceId, deviceMatcher) ->
            getDeviceForDrumPad(drumPad = item, deviceMatcher = deviceMatcher)
        }
    }

    // Filter
    private val rootFilterDevices: List<Device> = rootDrumPadItems.map { item ->
        getDeviceForDrumPad(drumPad = item, deviceMatcher = filterDeviceMatcher)
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

    // V0 drums
    private val rootV0DrumPartDecayParams: List<Map<UUID, Parameter>> = rootV0DrumPartDevices.map { map ->
        map.mapValues { (deviceId, device) -> device.toV0DrumDecayParam(deviceId) }
    }

    // V1 drums
    private val rootV1DrumPartDecayParams: List<Map<UUID, Parameter>> = rootV1DrumPartDevices.map { map ->
        map.mapValues { (deviceId, device) -> device.toV1DrumDecayParam(deviceId) }
    }

    // V8 drums
    private val rootV8DrumPartDecayParams: List<Map<UUID, Parameter>> = rootV8DrumPartDevices.map { map ->
        map.mapValues { (deviceId, device) -> device.toV8DrumDecayParam(deviceId) }
    }

    // V9 drums
    private val rootV9DrumPartDecayParams: List<Map<UUID, Parameter>> = rootV9DrumPartDevices.map { map ->
        map.mapValues { (deviceId, device) -> device.toV9DrumDecayParam(deviceId) }
    }

    // Filter
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

    // V0 drums
    private val nestedV0DrumPartDecayParams: List<Map<UUID, Parameter>> = nestedV0DrumPartDevices.map { map ->
        map.mapValues { (deviceId, device) -> device.toV0DrumDecayParam(deviceId) }
    }

    // V1 drums
    private val nestedV1DrumPartDecayParams: List<Map<UUID, Parameter>> = nestedV1DrumPartDevices.map { map ->
        map.mapValues { (deviceId, device) -> device.toV1DrumDecayParam(deviceId) }
    }

    // V8 drums
    private val nestedV8DrumPartDecayParams: List<Map<UUID, Parameter>> = nestedV8DrumPartDevices.map { map ->
        map.mapValues { (deviceId, device) -> device.toV8DrumDecayParam(deviceId) }
    }

    // V9 drums
    private val nestedV9DrumPartDecayParams: List<Map<UUID, Parameter>> = nestedV9DrumPartDevices.map { map ->
        map.mapValues { (deviceId, device) -> device.toV9DrumDecayParam(deviceId) }
    }

    // Filter
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
            instrumentSelectorChainSelector.activeChain().selectInEditor()
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
            nestedFilterDevices[index].exists().get()
        } else {
            rootFilterDevices[index].exists().get()
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

            // V0 drums
            val v0DrumPartDecayParamValue: SettableRangedValue? = nestedV0DrumPartDecayParams[selectedPad]
                .filter { (_, param) -> param.exists().get() }
                .map { it.value.value() }
                .firstOrNull()

            // V1 drums
            val v1DrumPartDecayParamValue: SettableRangedValue? = nestedV1DrumPartDecayParams[selectedPad]
                .filter { (_, param) -> param.exists().get() }
                .map { it.value.value() }
                .firstOrNull()

            // V8 drums
            val v8DrumPartDecayParamValue: SettableRangedValue? = nestedV8DrumPartDecayParams[selectedPad]
                .filter { (_, param) -> param.exists().get() }
                .map { it.value.value() }
                .firstOrNull()

            // V9 drums
            val v9DrumPartDecayParamValue: SettableRangedValue? = nestedV9DrumPartDecayParams[selectedPad]
                .filter { (_, param) -> param.exists().get() }
                .map { it.value.value() }
                .firstOrNull()

            val value: SettableRangedValue = when {
                v0DrumPartDecayParamValue != null -> v0DrumPartDecayParamValue
                v1DrumPartDecayParamValue != null -> v1DrumPartDecayParamValue
                v8DrumPartDecayParamValue != null -> v8DrumPartDecayParamValue
                v9DrumPartDecayParamValue != null -> v9DrumPartDecayParamValue
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

            // V0 drums
            val v0DrumPartDecayParamValue: SettableRangedValue? = rootV0DrumPartDecayParams[selectedPad]
                .filter { (_, param) -> param.exists().get() }
                .map { it.value.value() }
                .firstOrNull()

            // V1 drums
            val v1DrumPartDecayParamValue: SettableRangedValue? = rootV1DrumPartDecayParams[selectedPad]
                .filter { (_, param) -> param.exists().get() }
                .map { it.value.value() }
                .firstOrNull()

            // V8 drums
            val v8DrumPartDecayParamValue: SettableRangedValue? = rootV8DrumPartDecayParams[selectedPad]
                .filter { (_, param) -> param.exists().get() }
                .map { it.value.value() }
                .firstOrNull()

            // V9 drums
            val v9DrumPartDecayParamValue: SettableRangedValue? = rootV9DrumPartDecayParams[selectedPad]
                .filter { (_, param) -> param.exists().get() }
                .map { it.value.value() }
                .firstOrNull()

            val value: SettableRangedValue = when {
                v0DrumPartDecayParamValue != null -> v0DrumPartDecayParamValue
                v1DrumPartDecayParamValue != null -> v1DrumPartDecayParamValue
                v8DrumPartDecayParamValue != null -> v8DrumPartDecayParamValue
                v9DrumPartDecayParamValue != null -> v9DrumPartDecayParamValue
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

    private fun getDeviceMatcher(deviceId: UUID): DeviceMatcher = host.createBitwigDeviceMatcher(deviceId)

    private fun getDeviceForDrumPad(drumPad: DrumPad, deviceMatcher: DeviceMatcher): Device {
        val deviceBank = drumPad.createDeviceBank(1)
        deviceBank.setDeviceMatcher(deviceMatcher)
        val device = deviceBank.getItemAt(0)
        device.exists().markInterested()
        return device
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

    private fun Device.toV0DrumDecayParam(deviceId: UUID): Parameter {
        val paramId = if (deviceId == Bitwig.Instrument.V0ZapKick) "SWEEP_DECAY" else "DECAY"
        val specificDevice = createSpecificBitwigDevice(deviceId)
        val parameter = specificDevice.createParameter(paramId)
        parameter.exists().markInterested()
        return parameter
    }

    private fun Device.toV1DrumDecayParam(deviceId: UUID): Parameter {
        val paramId = when (deviceId) {
            Bitwig.Instrument.V1Snare -> "OSC_1_DECAY"
            Bitwig.Instrument.V1Cowbell -> "AEG_DECAY"
            else -> "DECAY"
        }
        val specificDevice = createSpecificBitwigDevice(deviceId)
        val parameter = specificDevice.createParameter(paramId)
        parameter.exists().markInterested()
        return parameter
    }

    private fun Device.toV8DrumDecayParam(deviceId: UUID): Parameter {
        val specificDevice = createSpecificBitwigDevice(deviceId)
        val parameter = specificDevice.createParameter("DECAY")
        parameter.exists().markInterested()
        return parameter
    }

    private fun Device.toV9DrumDecayParam(deviceId: UUID): Parameter {
        val specificDevice = createSpecificBitwigDevice(deviceId)
        val parameter = specificDevice.createParameter("DECAY")
        parameter.exists().markInterested()
        return parameter
    }

    // Filter
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