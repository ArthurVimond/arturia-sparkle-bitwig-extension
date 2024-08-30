package fr.arthurvimond.bitwig.util

class Logger(private val callback: (String) -> Unit) {

    fun d(message: String) {
        callback(message)
    }
}