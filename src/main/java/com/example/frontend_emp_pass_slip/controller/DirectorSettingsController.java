package com.example.frontend_emp_pass_slip.controller;

import backend.app.SessionManager;

public class DirectorSettingsController extends BaseSettingsController {

    @Override
    protected String getCurrentUsername() {
        return SessionManager.getInstance().getCurrentUser();
    }

    @Override
    protected void setupProfileUI() {
        if (accountHeaderLabel != null) {
            accountHeaderLabel.setText("Director Profile");
            nameLabel.setText("Department Director");
            roleLabel.setText("Director");
            departmentLabel.setText("Management");
            accessLevelLabel.setText("Approval Rights");
            usernameLabel.setText(getCurrentUsername());
        }
    }

    @Override
    protected void applySecurityRestrictions() {
        // Directors cannot backup the database
        if (backupDbBtn != null) {
            backupDbBtn.setDisable(true);
            backupDbBtn.setVisible(false);
        }
    }
}