package fr.arthurvimond.bitwig.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class State {

    private val scope = CoroutineScope(Dispatchers.Default)

    val stepsMode: StateFlow<StepsMode>
        get() = _stepsMode.asStateFlow()
    private val _stepsMode: MutableStateFlow<StepsMode> = MutableStateFlow(StepsMode.Bank)

    val bank: StateFlow<Int>
        get() = _bank.asStateFlow()
    private val _bank: MutableStateFlow<Int> = MutableStateFlow(0)

    val padsMode: StateFlow<PadsMode>
        get() = _padsMode.asStateFlow()
    private val _padsMode: MutableStateFlow<PadsMode> = MutableStateFlow(PadsMode.Play)

    val parameterBank: StateFlow<ParameterBank>
        get() = _parameterBank.asStateFlow()
    private val _parameterBank: MutableStateFlow<ParameterBank> = MutableStateFlow(ParameterBank.PanLevel)

    val selectedPattern: StateFlow<Int>
        get() = _selectedPattern.asStateFlow()
    private val _selectedPattern: MutableStateFlow<Int> = MutableStateFlow(0)

    val playingPattern: StateFlow<Int>
        get() = _playingPattern.asStateFlow()
    private val _playingPattern: MutableStateFlow<Int> = MutableStateFlow(0)

    val clipPagePosition: StateFlow<Int>
        get() = _clipPagePosition.asStateFlow()
    private val _clipPagePosition: MutableStateFlow<Int> = MutableStateFlow(0)

    val selectedPad: StateFlow<Int>
        get() = _selectedPad.asStateFlow()
    private val _selectedPad: MutableStateFlow<Int> = MutableStateFlow(0)

    val isSelectPressed: StateFlow<Boolean>
        get() = _isSelectPressed.asStateFlow()
    private val _isSelectPressed: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val isPadBankSwitched: StateFlow<Boolean>
        get() = _isPadBankSwitched.asStateFlow()
    private val _isPadBankSwitched: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val isBrowsingInstrumentSelector: StateFlow<Boolean>
        get() = _isBrowsingInstrumentSelector.asStateFlow()
    private val _isBrowsingInstrumentSelector: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val isVelocityOff: StateFlow<Boolean>
        get() = _isVelocityOff.asStateFlow()
    private val _isVelocityOff: MutableStateFlow<Boolean> = MutableStateFlow(false)

    fun setStepsMode(stepsMode: StepsMode) {
        scope.launch { _stepsMode.emit(stepsMode) }
    }

    fun setBank(bank: Int) {
        scope.launch { _bank.emit(bank) }
    }

    fun setSelectedPattern(pattern: Int) {
        scope.launch { _selectedPattern.emit(pattern) }
    }

    fun setPlayingPattern(pattern: Int) {
        scope.launch { _playingPattern.emit(pattern) }
    }

    fun setParameterBank(bank: ParameterBank) {
        scope.launch { _parameterBank.emit(bank) }
    }

    fun previousClipPagePosition() {
        val currentPosition = _clipPagePosition.value
        if (currentPosition > 0) {
            setClipPagePosition(currentPosition - 1)
        }
    }

    fun nextClipPagePosition(loopLength: Double) {
        val currentPosition = _clipPagePosition.value
        if (currentPosition < loopLength / 4 - 1) {
            setClipPagePosition(currentPosition + 1)
        }
    }

    fun setClipPagePosition(position: Int) {
        if (position < 0) return
        scope.launch { _clipPagePosition.emit(position) }
    }

    fun setPadsMode(padsMode: PadsMode) {
        scope.launch { _padsMode.emit(padsMode) }
    }

    fun setIsSelectPressed(pressed: Boolean) {
        scope.launch { _isSelectPressed.emit(pressed) }
    }

    fun selectPad(pad: Int) {
        scope.launch { _selectedPad.emit(pad) }
    }

    fun setPadBankSwitched(switched: Boolean) {
        scope.launch { _isPadBankSwitched.emit(switched) }
    }

    fun switchPadBank() {
        scope.launch { _isPadBankSwitched.emit(!_isPadBankSwitched.value) }
    }

    fun toggleInstrumentSelectorBrowsing() {
        scope.launch { _isBrowsingInstrumentSelector.emit(!_isBrowsingInstrumentSelector.value) }
    }

    fun toggleVelocityOff() {
        scope.launch { _isVelocityOff.emit(!_isVelocityOff.value) }
    }

}