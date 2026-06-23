package com.example.frontend_emp_pass_slip.backend;

import backend.app.DatabaseInitializer;
import backend.db.ConnectionPoolManager;
import backend.events.EventPublisher;
import backend.passslip.PassSlipController;
import backend.passslip.PassSlipRepository;
import backend.passslip.PassSlipService;
import backend.passslip.PassSlipValidator;
import backend.timein.ReturnStatusUpdater;
import backend.timein.TimeInController;
import backend.timein.TimeInService;
import backend.timein.TimeInValidator;

import java.sql.Connection;
import java.sql.SQLException;

public final class UiBackendContext {
    private static final String JDBC_URL = "jdbc:h2:mem:passslipdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String DB_USER = "sa";
    private static final String DB_PASS = "";
    private static final int POOL_SIZE = 5;

    private static UiBackendContext instance;

    private final PassSlipController passSlipController;
    private final TimeInController timeInController;

    private UiBackendContext() throws SQLException, InterruptedException {
        ConnectionPoolManager.initialize(JDBC_URL, DB_USER, DB_PASS, POOL_SIZE);
        ConnectionPoolManager pool = ConnectionPoolManager.getInstance();

        Connection connection = pool.acquire();
        try {
            DatabaseInitializer.initialize(connection);
        } finally {
            pool.release(connection);
        }

        EventPublisher eventPublisher = EventPublisher.getInstance();
        PassSlipRepository passSlipRepository = new PassSlipRepository(pool);
        PassSlipService passSlipService = new PassSlipService(
                new PassSlipValidator(),
                passSlipRepository,
                eventPublisher
        );
        this.passSlipController = new PassSlipController(passSlipService);

        TimeInService timeInService = new TimeInService(
                new TimeInValidator(pool),
                new ReturnStatusUpdater(pool),
                eventPublisher,
                pool
        );
        this.timeInController = new TimeInController(timeInService);
    }

    public static synchronized UiBackendContext getInstance() {
        if (instance == null) {
            try {
                instance = new UiBackendContext();
            } catch (SQLException e) {
                throw new IllegalStateException("Unable to start the pass slip backend.", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Pass slip backend startup was interrupted.", e);
            }
        }
        return instance;
    }

    public PassSlipController getPassSlipController() {
        return passSlipController;
    }

    public TimeInController getTimeInController() {
        return timeInController;
    }
}
