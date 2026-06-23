package backend.shared;

/**
 * Central registry for compile-time configuration constants used across all modules.
 * Never instantiated — all members are static final.
 */
public final class ApplicationConstants {

    private ApplicationConstants() {}

    // ── Database ────────────────────────────────────────────────────────────────
    public static final String DB_URL                    = "jdbc:sqlite:passmgr.db";
    public static final int    DB_POOL_SIZE              = 5;
    public static final int    DB_CONNECTION_TIMEOUT_SEC = 10;

    // ── Authentication ──────────────────────────────────────────────────────────
    /** BCrypt cost factor used when hashing the administrator password. */
    public static final int    AUTH_BCRYPT_ROUNDS        = 12;
    /** Minutes of inactivity before the session is automatically locked. */
    public static final int    SESSION_IDLE_TIMEOUT_MIN  = 15;
    /** Fixed length of the generated session token (hex chars). */
    public static final int    SESSION_TOKEN_LENGTH      = 64;

    // ── Validation — Admin credentials ─────────────────────────────────────────
    public static final int    AUTH_USERNAME_MIN_LEN     = 3;
    public static final int    AUTH_USERNAME_MAX_LEN     = 32;
    public static final int    AUTH_PASSWORD_MIN_LEN     = 8;
    public static final int    AUTH_PASSWORD_MAX_LEN     = 128;

    // ── Validation — Employee fields ────────────────────────────────────────────
    /** Strict Employee-ID pattern: 2 uppercase letters followed by 4 digits (e.g. "EX0042"). */
    public static final String EMPLOYEE_ID_PATTERN       = "^[A-Z]{2}\\d{4}$";
    public static final int    EMPLOYEE_NAME_MIN_LEN     = 2;
    public static final int    EMPLOYEE_NAME_MAX_LEN     = 100;
    public static final int    EMPLOYEE_DEPT_MAX_LEN     = 60;

    // ── Activity Logging ────────────────────────────────────────────────────────
    public static final String LOG_TABLE_NAME            = "activity_log";
    public static final String LOG_EVENT_LOGIN           = "ADMIN_LOGIN";
    public static final String LOG_EVENT_LOGOUT          = "ADMIN_LOGOUT";
    public static final String LOG_EVENT_SESSION_LOCK    = "SESSION_LOCK";
    public static final String LOG_EVENT_EMP_CREATE      = "EMPLOYEE_CREATE";
    public static final String LOG_EVENT_EMP_UPDATE      = "EMPLOYEE_UPDATE";
    public static final String LOG_EVENT_EMP_ARCHIVE     = "EMPLOYEE_ARCHIVE";

    // ── UI / FX ─────────────────────────────────────────────────────────────────
    public static final String FXML_LOGIN                = "/fxml/login.fxml";
    public static final String FXML_LOCK                 = "/fxml/lock.fxml";
    public static final String FXML_DASHBOARD            = "/fxml/dashboard.fxml";
    public static final String FXML_EMPLOYEE             = "/fxml/employee.fxml";
}
