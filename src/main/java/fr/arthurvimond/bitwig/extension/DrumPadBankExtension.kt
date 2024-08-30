package fr.arthurvimond.bitwig.extension

import com.bitwig.extension.controller.api.DrumPadBank

val DrumPadBank.isFirstBank: Boolean
    get() = scrollPosition().get() == 36