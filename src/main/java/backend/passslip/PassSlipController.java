package backend.passslip;

import backend.passslip.PassSlipService.IssuanceResult;
import backend.passslip.PassSlipService.IssuanceResult.Outcome;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller mediating between JavaFX UI nodes and the {@link PassSlipService}.
 *
 * <p>Receives raw string parameters from form fields, delegates all business logic
 * to the service layer, and returns structured results for the UI to render.
 * This controller holds no state and performs no domain logic itself.</p>
 *
 * <p>JavaFX {@code @FXML} event handlers on the layout controller class should
 * call {@link #handleIssuePassSlip(String, String, String)} and branch on the
 * returned {@link IssuanceResult} to update the UI accordingly.</p>
 */
public class PassSlipController {

    private static final Logger LOGGER = Logger.getLogger(PassSlipController.class.getName());

    private final PassSlipService passSlipService;

    /**
     * Constructs the PassSlipController with its service dependency.
     *
     * @param passSlipService The service to delegate issuance logic to. Must not be null.
     */
    public PassSlipController(PassSlipService passSlipService) {
        if (passSlipService == null) {
            throw new IllegalArgumentException("PassSlipService must not be null.");
        }
        this.passSlipService = passSlipService;
    }

    /**
     * Handles a pass slip issuance request originating from a JavaFX UI interaction.
     *
     * <p>This method is intended to be called from a JavaFX Application Thread event handler
     * (e.g., a button's {@code onAction} handler). It delegates to the service and returns
     * the result for the caller to use in updating the UI. Any unexpected runtime exception
     * is caught and wrapped into a system-error result to prevent unhandled crashes in the UI thread.</p>
     *
     * <p>Example usage in a JavaFX FXML controller:
     * <pre>{@code
     * @FXML
     * private void onIssueButtonClicked(ActionEvent event) {
     *     String employeeId = employeeIdField.getText().trim();
     *     String destination = destinationField.getText().trim();
     *     String reason = reasonField.getText().trim();
     *
     *     IssuanceResult result = passSlipController.handleIssuePassSlip(employeeId, destination, reason);
     *
     *     switch (result.getOutcome()) {
     *         case SUCCESS -> showSuccessAlert(result.getCreatedSlip());
     *         case VALIDATION_FAILURE -> showValidationErrors(result.getViolations());
     *         case BLOCKED -> showBlockedAlert(result.getErrorMessage());
     *         case SYSTEM_ERROR -> showSystemErrorAlert(result.getErrorMessage());
     *     }
     * }
     * }</pre>
     * </p>
     *
     * @param employeeId  Raw employee ID string from the UI form field.
     * @param destination Raw destination string from the UI form field.
     * @param reason      Raw reason string from the UI form field.
     * @return An {@link IssuanceResult} containing the outcome and all relevant data for UI rendering.
     */
    public IssuanceResult handleIssuePassSlip(String employeeId,
                                               String destination,
                                               String reason) {
        LOGGER.info(String.format(
                "PassSlipController received issuance request: employee=[%s], destination=[%s].",
                employeeId, destination
        ));

        try {
            IssuanceResult result = passSlipService.issuePassSlip(employeeId, destination, reason);

            if (result.isSuccess()) {
                LOGGER.info(String.format(
                        "Issuance succeeded: slip=[%s] for employee=[%s].",
                        result.getCreatedSlip().getSlipId(), employeeId
                ));
            } else {
                LOGGER.warning(String.format(
                        "Issuance did not succeed for employee=[%s]: outcome=[%s].",
                        employeeId, result.getOutcome()
                ));
            }

            return result;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                    "Unexpected error in PassSlipController for employee [" + employeeId + "].", e);
            return IssuanceResult.systemError(
                    "An unexpected internal error occurred. Please contact the administrator. " +
                    "Details: " + e.getMessage()
            );
        }
    }
}
