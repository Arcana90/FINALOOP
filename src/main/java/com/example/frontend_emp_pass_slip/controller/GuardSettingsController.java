package com.example.frontend_emp_pass_slip.controller;

import backend.app.SessionManager;

public class GuardSettingsController extends BaseSettingsController {

    @Override
    protected String getCurrentUsername() {
        return SessionManager.getInstance().getCurrentUser();
    }

    @Override
    protected void setupProfileUI() {
        if (accountHeaderLabel != null) {
            accountHeaderLabel.setText("Guard Profile");
            nameLabel.setText("Duty Guard Officer");
            roleLabel.setText("Security Guard");
            departmentLabel.setText("Security Operations");
            accessLevelLabel.setText("Gate Access Only");
            usernameLabel.setText(getCurrentUsername());
        }
    }

    @Override
    protected void applySecurityRestrictions() {
        // Guards cannot backup the DB or view the full activity log
        if (backupDbBtn != null) {
            backupDbBtn.setDisable(true);
            backupDbBtn.setVisible(false);
        }
        if (activityLogBtn != null) {
            activityLogBtn.setDisable(true);
            activityLogBtn.setVisible(false);
        }
    }
}