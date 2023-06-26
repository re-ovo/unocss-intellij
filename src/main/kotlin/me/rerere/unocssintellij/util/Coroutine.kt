package me.rerere.unocssintellij.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun ioCoroutineScope(): CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())