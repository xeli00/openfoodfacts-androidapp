package openfoodfacts.github.scrachx.openfood.camera

import android.app.Application
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.Barcode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import openfoodfacts.github.scrachx.openfood.features.scan.WorkflowState

/**
 * View model for handling application workflow based on camera preview.
 */
class WorkflowModel(application: Application) : AndroidViewModel(application) {

    val workflowState: MutableSharedFlow<WorkflowState> = MutableSharedFlow()
    val detectedBarcode = MutableSharedFlow<Barcode>()

    var isCameraLive = false
        private set

    @MainThread
    fun setWorkflowState(state: WorkflowState) {
        viewModelScope.launch { workflowState.emit(state) }
    }

    fun setDetectedBarcode(barcode: Barcode) {
        viewModelScope.launch { detectedBarcode.emit(barcode) }
    }

    fun markCameraLive() {
        isCameraLive = true
    }

    fun markCameraFrozen() {
        isCameraLive = false
    }

}
