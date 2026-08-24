-- =====================================================================
-- Seed data. Password for every account below is:  Passw0rd!2026
--
-- Hashes generated with lk.jiat.globaltrade.web.security.PasswordHasher:
--   PBKDF2-WithHmacSHA256, 210,000 iterations, 16-byte random salt,
--   256-bit derived key. Format:  iterations:saltBase64:hashBase64
--
-- Each hash carries its OWN salt, which is why five accounts sharing the
-- same password produce five completely different stored values - the
-- property that defeats rainbow-table attacks.
--
-- Regenerate for your own environment:
--   java -cp <classes> lk.jiat.globaltrade.web.security.PasswordHasher
-- =====================================================================
USE globaltrade;

INSERT INTO app_user (email, name, password_hash, vendor_code, enabled) VALUES
 ('admin@globaltrade.lk',       'System Administrator',
  '210000:OCd8xIwJ43M4yM9e9bwqDw==:uljbpFrDlHpuVBpr7G3wGoujTYHW7Spwzan1lhWulaM=', NULL, 1),
 ('coordinator@globaltrade.lk', 'Logistics Coordinator',
  '210000:jtNdJhfEgs/m+ujHNU3MnA==:w/GLG0IYAtg9qjPnIlfL32EPGt3zVBVx4/oPs3MeDf8=', NULL, 1),
 ('customs@globaltrade.lk',     'Customs Agent',
  '210000:4ZtGIk+WRNRzCx/xsLsVKw==:66G23X1FiBODYvFUAnBQkJLjbL9C/5GOP8FCORu3pFA=', NULL, 1),
 ('warehouse@globaltrade.lk',   'Warehouse Manager',
  '210000:QighjGHUxdz7GJUo6WffSQ==:hpG10UJPvkCVAnFKn4ia19fXZHYJjpemcKE5+iL/L9w=', NULL, 1),
 ('rep@acme-supplies.com',      'Vendor Representative',
  '210000:L9znav/MkNM7lcMRGK2/RQ==:Yjp0dEcrYuhRM1gSRrzYjr2mej46ZiVa5FFbX12ykNA=', 'ACME-001', 1);

INSERT INTO app_user_role (user_id, role)
  SELECT id, 'ADMIN'                 FROM app_user WHERE email='admin@globaltrade.lk';
INSERT INTO app_user_role (user_id, role)
  SELECT id, 'LOGISTICS_COORDINATOR' FROM app_user WHERE email='coordinator@globaltrade.lk';
INSERT INTO app_user_role (user_id, role)
  SELECT id, 'CUSTOMS_AGENT'         FROM app_user WHERE email='customs@globaltrade.lk';
INSERT INTO app_user_role (user_id, role)
  SELECT id, 'WAREHOUSE_MANAGER'     FROM app_user WHERE email='warehouse@globaltrade.lk';
INSERT INTO app_user_role (user_id, role)
  SELECT id, 'VENDOR_REP'            FROM app_user WHERE email='rep@acme-supplies.com';

-- ---------------------------------------------------------------------
INSERT INTO vendor (vendor_code, name, country_code, tax_id, contact_email, active) VALUES
 ('ACME-001',   'Acme Supplies Ltd',      'LK', 'LK123456789', 'rep@acme-supplies.com', 1),
 ('NORDIC-77',  'Nordic Freight AB',      'SE', 'SE998877665', 'ops@nordicfreight.se',  1),
 ('PACIFIC-12', 'Pacific Components Inc', 'SG', 'SG556677889', 'trade@pacificcomp.sg',  1);

INSERT INTO inventory_item (sku, warehouse_code, quantity_on_hand, quantity_reserved,
                            reorder_point, reorder_quantity) VALUES
 ('SKU-CONTAINER-20FT', 'CMB-WH1',  500,  40,  50, 200),
 ('SKU-PALLET-EUR',     'CMB-WH1', 1200, 150, 200, 500),
 ('SKU-REEFER-40FT',    'SIN-WH2',   80,  12,  20,  60),
 ('SKU-DRUM-200L',      'SIN-WH2',   15,   3,  25, 100);  -- deliberately below reorder point

-- Shipments spanning several states so every test scenario has data.
INSERT INTO shipment (tracking_number, origin_country, destination_country, carrier_code,
                      status, estimated_arrival, declared_value, vendor_id, created_at) VALUES
 ('GTLKGB0000001', 'LK', 'GB', 'MAERSK', 'IN_TRANSIT',
   DATE_ADD(NOW(), INTERVAL 5 DAY),  15000.00, 1, NOW()),
 ('GTSEUS0000002', 'SE', 'US', 'DHL',    'BOOKED',
   DATE_ADD(NOW(), INTERVAL 9 DAY),  42000.00, 2, NOW()),
 ('GTSGDE0000003', 'SG', 'DE', 'MSC',    'AT_CUSTOMS',
   DATE_ADD(NOW(), INTERVAL 2 DAY),   8750.00, 3, NOW()),
 ('GTLKFR0000004', 'LK', 'FR', 'MAERSK', 'IN_TRANSIT',
   DATE_SUB(NOW(), INTERVAL 1 DAY),   6300.00, 1, NOW());  -- deliberately OVERDUE

INSERT INTO customs_declaration (shipment_id, hs_code, status, filing_deadline, timer_scheduled)
  SELECT id, '8471300000', 'PENDING', DATE_ADD(NOW(), INTERVAL 20 HOUR), 0
  FROM shipment WHERE tracking_number = 'GTSGDE0000003';   -- due inside the 24h sweep window
