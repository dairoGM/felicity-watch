package com.dairoroberto.felicitywatch.ui.nav

import androidx.lifecycle.ViewModel
import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val credentialsStore: CredentialsStore
) : ViewModel() {

    private val _onboardingCompleted = MutableStateFlow(credentialsStore.hasFsolarCredentials())
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted

    fun completeOnboarding() {
        _onboardingCompleted.value = true
    }
}
