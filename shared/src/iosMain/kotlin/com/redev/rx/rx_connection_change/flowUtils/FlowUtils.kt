package com.redev.rx.rx_connection_change.flowUtils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


fun <T> Flow<T>.toFlowX(scope: CoroutineScope = MainScope()): FlowX<T> {
    return FlowX(this, scope)
}

fun <T> toFlowX(flow: Flow<T>, scope: CoroutineScope?): FlowX<T> {
    return FlowX(flow, scope ?: MainScope())
}

class FlowX<T>(
    private val originalFlow: Flow<T>,
    private val scope: CoroutineScope = MainScope()
) : Flow<T> {

    private var job: Job? = null

    override suspend fun collect(collector: FlowCollector<T>) {
        originalFlow.collect(collector)
    }


    fun subscribe(
        onEach: (T) -> Unit,
        onCompletion: (Throwable?) -> Unit = {}
    ) {
        if (job?.isActive == true) return

        job = scope.launch {
            originalFlow
                .onEach { onEach(it) }
                .catch { e -> onCompletion(e) }
                .onCompletion { cause -> onCompletion(cause) }
                .collect()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}