package com.piotrekwitkowski.libraryhce

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private lateinit var viewModel: AppViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AppViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state of isClonerActive is false`() = runTest {
        assertEquals(false, viewModel.isClonerActive.value)
    }

    @Test
    fun `setClonerActive updates the state flow correctly`() = runTest {
        viewModel.setClonerActive(true)
        assertEquals(true, viewModel.isClonerActive.value)
        
        viewModel.setClonerActive(false)
        assertEquals(false, viewModel.isClonerActive.value)
    }

    @Test
    fun `initial state of isPaymentEmulationActive is false`() = runTest {
        assertEquals(false, viewModel.isPaymentEmulationActive.value)
    }

    @Test
    fun `setPaymentEmulationActive updates the state flow correctly`() = runTest {
        viewModel.setPaymentEmulationActive(false)
        assertEquals(false, viewModel.isPaymentEmulationActive.value)
        
        viewModel.setPaymentEmulationActive(true)
        assertEquals(true, viewModel.isPaymentEmulationActive.value)
    }
}
