module com.example.frontend_emp_pass_slip {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.logging;
    requires org.postgresql.jdbc;
    requires com.github.librepdf.openpdf;
    requires java.desktop;
    requires com.dlsc.formsfx;

    opens com.example.frontend_emp_pass_slip to javafx.fxml;
    opens com.example.frontend_emp_pass_slip.controller to javafx.fxml;
    exports com.example.frontend_emp_pass_slip;
    exports com.example.frontend_emp_pass_slip.controller;
    exports backend.app;
    exports backend.auth;
    exports backend.db;
    exports backend.employee;
    exports backend.events;
    exports backend.logging;
    exports backend.passslip;
    exports backend.shared;
    exports backend.timein;
    exports backend.validation;
}
