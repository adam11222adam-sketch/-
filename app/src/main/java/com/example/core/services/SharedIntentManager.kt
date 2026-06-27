package com.example.core.services

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SharedIntentManager {
    private val _sharedText = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sharedText = _sharedText.asSharedFlow()

    fun onTextReceived(text: String) {
        _sharedText.tryEmit(text)
    }
}
