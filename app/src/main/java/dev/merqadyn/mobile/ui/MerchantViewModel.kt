package dev.merqadyn.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.merqadyn.mobile.data.MerchantRepository
import dev.merqadyn.mobile.data.MerchantSnapshot
import dev.merqadyn.mobile.data.ProductDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MerchantViewModel(private val repository: MerchantRepository) : ViewModel() {
    val snapshot: StateFlow<MerchantSnapshot> = repository.snapshot.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MerchantSnapshot(),
    )

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()

    init {
        refresh(silent = true)
    }

    fun refresh(silent: Boolean = false) = perform(silent) {
        repository.refresh()
        if (!silent) _notice.value = "Local records refreshed."
    }

    fun sync() = perform {
        repository.sync()
        _notice.value = "Sync complete."
    }

    fun adjustStock(productId: String, locationId: String, delta: Double, reason: String) = perform {
        repository.queueStockAdjustment(productId, locationId, delta, reason)
        _notice.value = "Stock change saved to the queue."
    }

    fun createProduct(draft: ProductDraft) = perform {
        repository.queueProduct(draft)
        _notice.value = "Product saved to the queue."
    }

    fun updateProduct(id: String, draft: ProductDraft) = perform {
        repository.queueProductUpdate(id, draft)
        _notice.value = "Product update saved to the queue."
    }

    fun retry(id: String) = perform {
        repository.retryMutation(id)
        _notice.value = "Change returned to the queue."
    }

    fun dismiss(id: String) = perform {
        repository.dismissMutation(id)
        _notice.value = "Change removed."
    }

    fun clearNotice() {
        _notice.value = null
    }

    private fun perform(silent: Boolean = false, action: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                action()
            } catch (error: Exception) {
                if (!silent) _notice.value = error.message ?: "The action could not be completed."
            } finally {
                _busy.value = false
            }
        }
    }
}

class MerchantViewModelFactory(private val repository: MerchantRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MerchantViewModel(repository) as T
}
