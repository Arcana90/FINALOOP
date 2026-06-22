package backend.timein;

import backend.timein.TimeInService.TimeInResult;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller mediating between JavaFX UI table selections and the {@link TimeInService}.
 *
 * <p>Receives the selected pass slip ID from a UI table node, delegates all processing
 * to the service layer, and returns a structured result for the UI to render. This controller
 * holds no state and performs no domain logic itself.</p>
 *
 * <p>The JavaFX FXML controller class should call {@link #handleTimeIn(String)} from a
 * button handler or context menu action bound to the active table row selection, then
 * branch on the returned {@link TimeInResult} to update the UI accordingly.</p>
 */
public class TimeInController {

    private static final Logger LOGGER = Logger.getLogger(TimeInController.class.getName());

    private final TimeInService timeInService;

    /**
     * Constructs the TimeInController with its service dependency.
     *
     * @param timeInService The service to delegate Time-In processing to. Must not be null.
     */
    public TimeInController(TimeInService timeInService) {
        if (timeInService == null) {
            throw new IllegalArgumentException("TimeInService must not be null.");
        }
        this.timeInService = timeInService;
    }

    /**
     * Handles a Time-In action triggered by a UI table row selection.
     *
     * <p>This method is intended to be called from a JavaFX Application Thread event handler
     * (e.g., a "Record Return" button's {@code onAction} handler). The caller must extract
     * the {@code slipId} from the currently selected {@code TableView} row before invoking
     * this method.</p>
     *
     * <p>Any unexpected runtime exception is caught and wrapped into a system-error result
     * to prevent unhandled crashes in the UI thread.</p>
     *
     * <p>Example usage in a JavaFX FXML controller:
     * <pre>{@code
     * @FXML
     * private void onRecordReturnButtonClicked(ActionEvent event) {
     *     PassSlipDTO selectedSlip = activeSlipsTable.getSelectionModel().getSelectedItem();
     *
     *     if (selectedSlip == null) {
     *         showWarningAlert("No Selection", "Please select an active pass slip from the table.");
     *         return;
     *     }
     *
     *     TimeInResult result = timeInController.handleTimeIn(selectedSlip.getSlipId());
     *
     *     switch (result.getOutcome()) {
     *         case SUCCESS -> {
     *             showSuccessAlert(
     *                 "Employee returned: " + result.getEmployeeId() +
     *                 " | Duration: " + result.getTotalDuration()
     *             );
     *             refreshActiveSlipsTable();
     *         }
     *         case VALIDATION_FAILURE -> showWarningAlert("Cannot Process", result.getErrorMessage());
     *         case SYSTEM_ERROR -> showErrorAlert("System Error", result.getErrorMessage());
     *     }
     * }
     * }</pre>
     * </p>
     *
     * @param slipId The pass slip ID extracted from the selected table row. Must not be blank.
     * @return A {@link TimeInResult} containing the outcome and all relevant data for UI rendering.
     */
    public TimeInResult handleTimeIn(String slipId) {
        LOGGER.info(String.format(
                "TimeInController received Time-In request for slip [%s].", slipId
        ));

        if (slipId == null || slipId.isBlank()) {
            LOGGER.warning("TimeInController received a blank slipId. Request rejected.");
            return TimeInResult.validationFailure(
                    "No pass slip selected. Please select an active slip from the table."
            );
        }

        try {
            TimeInResult result = timeInService.processTimeIn(slipId);

            if (result.isSuccess()) {
                LOGGER.info(String.format(
                        "Time-In succeeded: slip=[%s], employee=[%s], duration=[%s].",
                        slipId, result.getEmployeeId(), result.getTotalDuration()
                ));
            } else {
                LOGGER.warning(String.format(
                        "Time-In did not succeed for slip=[%s]: outcome=[%s], message=[%s].",
                        slipId, result.getOutcome(), result.getErrorMessage()
                ));
            }

            return result;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                    "Unexpected error in TimeInController for slip [" + slipId + "].", e);
            return TimeInResult.systemError(
                    "An unexpected internal error occurred during Time-In processing. " +
                    "Please contact the administrator. Details: " + e.getMessage()
            );
        }
    }
}
