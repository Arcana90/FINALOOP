package com.example.frontend_emp_pass_slip.controller;

import backend.app.SessionManager;

public class AdminSettingsController extends BaseSettingsController {

    @Override
    protected String getCurrentUsername() {
        return SessionManager.getInstance().getCurrentUser();
    }

    @Override
    protected void setupProfileUI() {
        if (accountHeaderLabel != null) {
            accountHeaderLabel.setText("Administrator Account");
            nameLabel.setText("System Admin");
            roleLabel.setText("Administrator");
            departmentLabel.setText("HR / IT Division");
            accessLevelLabel.setText("Full System Access");
            usernameLabel.setText(getCurrentUsername());
        }
    }

    @Override
    protected void applySecurityRestrictions() {
        // Admin gets full access to activity log and DB backup
    }
}