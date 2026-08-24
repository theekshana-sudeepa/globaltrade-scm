-- =====================================================================
-- GlobalTrade Supply Chain Management - MySQL 8 schema
-- Storage engine InnoDB throughout: row-level locking and full ACID.
-- Isolation: READ COMMITTED is set per-connection by the datasource so that
-- long reporting reads do not hold gap locks against the shipment insert path.
-- =====================================================================
CREATE DATABASE IF NOT EXISTS globaltrade
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE globaltrade;

CREATE TABLE IF NOT EXISTS vendor (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    vendor_code       VARCHAR(32)  NOT NULL,
    name              VARCHAR(160) NOT NULL,
    country_code      CHAR(2)      NOT NULL,
    tax_id            VARCHAR(64),
    contact_email     VARCHAR(160),
    active            TINYINT(1)   NOT NULL DEFAULT 1,
    performance_score DOUBLE,
    last_evaluated_at DATETIME(6),
    version           BIGINT       NOT NULL DEFAULT 0,
    UNIQUE KEY ix_vendor_code (vendor_code),
    KEY ix_vendor_country (country_code)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS shipment (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tracking_number     VARCHAR(40) NOT NULL,
    origin_country      CHAR(2)     NOT NULL,
    destination_country CHAR(2)     NOT NULL,
    carrier_code        VARCHAR(16) NOT NULL,
    status              VARCHAR(24) NOT NULL,
    estimated_arrival   DATETIME(6),
    actual_arrival      DATETIME(6),
    declared_value      DECIMAL(15,2),
    vendor_id           BIGINT,
    created_at          DATETIME(6) NOT NULL,
    last_polled_at      DATETIME(6),
    version             BIGINT      NOT NULL DEFAULT 0,
    UNIQUE KEY ix_shipment_tracking (tracking_number),
    KEY ix_shipment_status (status),
    KEY ix_shipment_eta (estimated_arrival),
    KEY ix_shipment_vendor (vendor_id),
    -- covering index for the 5-minute tracking poll: the timer's hottest query
    KEY ix_shipment_poll (status, last_polled_at),
    CONSTRAINT fk_shipment_vendor FOREIGN KEY (vendor_id) REFERENCES vendor(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inventory_item (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku                 VARCHAR(48) NOT NULL,
    warehouse_code      VARCHAR(16) NOT NULL,
    quantity_on_hand    INT NOT NULL DEFAULT 0,
    quantity_reserved   INT NOT NULL DEFAULT 0,
    reorder_point       INT NOT NULL DEFAULT 10,
    reorder_quantity    INT NOT NULL DEFAULT 100,
    last_replenished_at DATETIME(6),
    version             BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY ix_inv_sku_wh (sku, warehouse_code),
    CONSTRAINT chk_inventory_non_negative CHECK (quantity_on_hand >= 0
                                             AND quantity_reserved >= 0
                                             AND quantity_reserved <= quantity_on_hand)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS customs_declaration (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    shipment_id     BIGINT      NOT NULL,
    hs_code         VARCHAR(16) NOT NULL,
    declaration_ref VARCHAR(48),
    status          VARCHAR(24) NOT NULL,
    filing_deadline DATETIME(6) NOT NULL,
    submitted_at    DATETIME(6),
    timer_scheduled TINYINT(1)  NOT NULL DEFAULT 0,
    version         BIGINT      NOT NULL DEFAULT 0,
    UNIQUE KEY ix_customs_shipment (shipment_id),
    KEY ix_customs_deadline (filing_deadline),
    KEY ix_customs_status (status),
    CONSTRAINT fk_customs_shipment FOREIGN KEY (shipment_id) REFERENCES shipment(id)
) ENGINE=InnoDB;

-- Append-only audit trail. No UPDATE or DELETE grant is issued to the app user
-- except through the retention job, which runs as a separate DB principal.
CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    correlation_id  CHAR(36)     NOT NULL,
    action          VARCHAR(64)  NOT NULL,
    target_type     VARCHAR(64),
    target_id       VARCHAR(64),
    principal_name  VARCHAR(128),
    principal_roles VARCHAR(256),
    source_module   VARCHAR(64),
    outcome         VARCHAR(16),
    duration_millis BIGINT,
    detail          TEXT,
    occurred_at     DATETIME(6)  NOT NULL,
    KEY ix_audit_time (occurred_at),
    KEY ix_audit_principal (principal_name),
    KEY ix_audit_correlation (correlation_id),
    KEY ix_audit_action (action)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS app_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(160) NOT NULL,
    name            VARCHAR(120) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    vendor_code     VARCHAR(32),
    enabled         TINYINT(1)   NOT NULL DEFAULT 1,
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    DATETIME(6),
    UNIQUE KEY ix_user_email (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS app_user_role (
    user_id BIGINT      NOT NULL,
    role    VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS refresh_token (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    token      VARCHAR(128) NOT NULL,
    username   VARCHAR(160) NOT NULL,
    expiry_at  DATETIME(6)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    UNIQUE KEY ix_rt_token (token),
    KEY ix_rt_username (username),
    KEY ix_rt_expiry (expiry_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS vendor_performance (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    vendor_id         BIGINT      NOT NULL,
    period_start      DATETIME(6) NOT NULL,
    period_end        DATETIME(6) NOT NULL,
    shipments_total   INT         NOT NULL,
    shipments_on_time INT         NOT NULL,
    on_time_rate      DOUBLE,
    computed_at       DATETIME(6) NOT NULL,
    KEY ix_vperf_vendor_period (vendor_id, period_start),
    CONSTRAINT fk_vperf_vendor FOREIGN KEY (vendor_id) REFERENCES vendor(id)
) ENGINE=InnoDB;
