package dev.merqadyn.mobile

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.merqadyn.mobile.ui.MerchantApp
import dev.merqadyn.mobile.ui.EnrollmentScreen
import dev.merqadyn.mobile.ui.MerchantViewModel
import dev.merqadyn.mobile.ui.MerchantViewModelFactory
import dev.merqadyn.mobile.ui.theme.MerqadynTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            val repository = (application as MerqadynApplication).container.repository
            val model: MerchantViewModel = viewModel(factory = MerchantViewModelFactory(repository))
            val snapshot by model.snapshot.collectAsStateWithLifecycle()
            val profile by model.connectionProfile.collectAsStateWithLifecycle()
            val busy by model.busy.collectAsStateWithLifecycle()
            val notice by model.notice.collectAsStateWithLifecycle()

            MerqadynTheme {
                if (profile == null) {
                    EnrollmentScreen(
                        busy = busy,
                        notice = notice,
                        onClearNotice = model::clearNotice,
                        onEnroll = model::enroll,
                    )
                } else {
                    MerchantApp(
                        snapshot = snapshot,
                        busy = busy,
                        notice = notice,
                        onClearNotice = model::clearNotice,
                        onRefresh = { model.refresh() },
                        onSync = model::sync,
                        onAdjustStock = model::adjustStock,
                        onCreateProduct = model::createProduct,
                        onUpdateProduct = model::updateProduct,
                        onRetry = model::retry,
                        onDismiss = model::dismiss,
                        onRemoveEnrollment = model::removeEnrollment,
                    )
                }
            }
        }
    }
}
