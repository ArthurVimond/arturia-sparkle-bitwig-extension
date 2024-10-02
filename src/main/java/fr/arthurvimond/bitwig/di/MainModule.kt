package fr.arthurvimond.bitwig.di

import com.bitwig.extension.controller.api.*
import fr.arthurvimond.bitwig.*
import fr.arthurvimond.bitwig.state.State
import fr.arthurvimond.bitwig.util.Bitwig
import fr.arthurvimond.bitwig.util.Logger
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun mainModule(host: ControllerHost) = module {
    single { host }
    single { host.getMidiInPort(0) }
    single { host.getMidiOutPort(0) }
    single { host.createHardwareSurface() }
    single { host.createTransport() }
    single { get<MidiIn>().createNoteInput("Pads", "??????") }
    single { Logger { host.println(it) } }
    singleOf(::State)
    singleOf(::MidiManager)
    singleOf(::DeviceManager)
    singleOf(::TransportManager)
    singleOf(::PadsManager)
    singleOf(::PadControlsManager)
    singleOf(::StepsManager)
    singleOf(::BrowserManager)
    singleOf(::ParametersManager)

    single { host.createCursorTrack(3, 16) }
    single { get<CursorTrack>().clipLauncherSlotBank() }
    single { get<CursorTrack>().createLauncherCursorClip(16, 128) }
    single { host.createPopupBrowser() }
    single { host.createGroove() }

    // DrumMachine
    single<Device>(named(KoinQualifiers.DrumMachineDevice)) {
        val drumMachineDeviceMatcher = host.createBitwigDeviceMatcher(Bitwig.Instrument.DrumMachine)
        val drumDeviceBank = get<CursorTrack>().createDeviceBank(1)
        drumDeviceBank.setDeviceMatcher(drumMachineDeviceMatcher)
        drumDeviceBank.getItemAt(0)
    }
    // InstrumentSelector
    single<Device>(named(KoinQualifiers.InstrumentSelectorDevice)) {
        val instrumentSelectorDeviceMatcher = host.createBitwigDeviceMatcher(Bitwig.Instrument.InstrumentSelector)
        val instrumentSelectorDeviceBank = get<CursorTrack>().createDeviceBank(1)
        instrumentSelectorDeviceBank.setDeviceMatcher(instrumentSelectorDeviceMatcher)
        instrumentSelectorDeviceBank.getItemAt(0)
    }
    single<ChainSelector>(named(KoinQualifiers.InstrumentSelectorChainSelector)) {
        get<Device>(named(KoinQualifiers.InstrumentSelectorDevice)).createChainSelector()
    }
    single<Device>(named(KoinQualifiers.InstrumentSelectorActiveChainDevice)) {
        get<ChainSelector>(named(KoinQualifiers.InstrumentSelectorChainSelector))
            .activeChain().createDeviceBank(1).getDevice(0)
    }

    // DrumPadBank
    single<DrumPadBank> { get<Device>(named(KoinQualifiers.DrumMachineDevice)).createDrumPadBank(8) }
    // E-Kick
    single<DeviceMatcher>(named(KoinQualifiers.EKickDeviceMatcher)) {
        host.createBitwigDeviceMatcher(Bitwig.Instrument.EKick)
    }
    // E-Snare
    single<DeviceMatcher>(named(KoinQualifiers.ESnareDeviceMatcher)) {
        host.createBitwigDeviceMatcher(Bitwig.Instrument.ESnare)
    }
    // E-Clap
    single<DeviceMatcher>(named(KoinQualifiers.EClapDeviceMatcher)) {
        host.createBitwigDeviceMatcher(Bitwig.Instrument.EClap)
    }
    // E-Hat
    single<DeviceMatcher>(named(KoinQualifiers.EHatDeviceMatcher)) {
        host.createBitwigDeviceMatcher(Bitwig.Instrument.EHat)
    }
    // Sampler
    single<DeviceMatcher>(named(KoinQualifiers.SamplerDeviceMatcher)) {
        host.createBitwigDeviceMatcher(Bitwig.Instrument.Sampler)
    }

    // Audio effects

    // Filter
    single<DeviceMatcher>(named(KoinQualifiers.FilterDeviceMatcher)) {
        host.createBitwigDeviceMatcher(Bitwig.AudioEffect.Filter)
    }
}

object KoinQualifiers {

    // Instruments
    const val DrumMachineDevice = "DrumMachineDevice"
    const val InstrumentSelectorDevice = "InstrumentSelectorDevice"
    const val InstrumentSelectorChainSelector = "InstrumentSelectorChainSelector"
    const val InstrumentSelectorActiveChainDevice = "InstrumentSelectorCursorDevice"
    const val SamplerDeviceMatcher = "SamplerDeviceMatcher"
    const val EKickDeviceMatcher = "EKickDeviceMatcher"
    const val ESnareDeviceMatcher = "ESnareDeviceMatcher"
    const val EClapDeviceMatcher = "EClapDeviceMatcher"
    const val EHatDeviceMatcher = "EHatDeviceMatcher"

    // Audio effects
    const val FilterDeviceMatcher = "FilterDeviceMatcher"
}