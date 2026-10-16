package gx.zelda.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Main for a view model test. [tearDown] stops each view model made through [track] and waits for
 * it before resetting Main, so no write or collector still on DataStore's threads resumes on Main
 * after the test and fails a later one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestMain(private val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()) {
    private val viewModels = mutableListOf<ViewModel>()

    fun <T : ViewModel> track(vm: T): T = vm.also { viewModels += it }

    fun setUp() = Dispatchers.setMain(dispatcher)

    fun tearDown() {
        runBlocking { viewModels.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        viewModels.clear()
        Dispatchers.resetMain()
    }
}
