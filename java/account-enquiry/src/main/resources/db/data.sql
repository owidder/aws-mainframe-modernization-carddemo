-- Sample account data derived from app/data/ASCII/acctdata.txt
-- Field widths follow CVACT01Y.cpy (ACCT-ID 11, STATUS 1, BAL 12+2 packed, etc.)
-- Values are decoded from the ASCII export (sign overpunch removed → numeric)

MERGE INTO accounts (acct_id, active_status, curr_bal, credit_limit, cash_credit_limit,
                     open_date, expiration_date, reissue_date,
                     curr_cyc_credit, curr_cyc_debit, addr_zip, group_id)
KEY(acct_id) VALUES
  (1,  'Y', 1940.00,  20200.00,  10200.00, '2014-11-20', '2025-05-20', '2025-05-20', 0.00, 0.00, 'A000000000', NULL),
  (2,  'Y', 1580.00,  61300.00,  54480.00, '2013-06-19', '2024-08-11', '2024-08-11', 0.00, 0.00, 'A000000000', NULL),
  (3,  'Y', 1470.00,  49090.00,   5380.00, '2013-08-23', '2024-01-10', '2024-01-10', 0.00, 0.00, 'A000000000', NULL),
  (4,  'Y',  400.00,  35030.00,  27890.00, '2012-11-17', '2023-12-16', '2023-12-16', 0.00, 0.00, 'A000000000', NULL),
  (5,  'Y', 3450.00,  38190.00,  24300.00, '2012-10-03', '2025-03-09', '2025-03-09', 0.00, 0.00, 'A000000000', NULL),
  (6,  'N',  200.00,  15000.00,   5000.00, '2018-03-15', '2023-03-15', '2023-03-15', 0.00, 100.00, 'B000000001', 'GRP1'),
  (7,  'Y', 9999.99,  50000.00,  10000.00, '2019-07-01', '2026-07-01', '2024-07-01', 500.00, 200.00, 'C000000002', 'GRP2');
