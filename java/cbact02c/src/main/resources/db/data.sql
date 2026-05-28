-- Seed data derived from app/data/ASCII/carddata.txt (first 10 of 50 records)
-- Fixed-width layout: CARD-NUM(0-15) | CARD-ACCT-ID(16-26) | CARD-CVV-CD(27-29)
--                     CARD-EMBOSSED-NAME(30-79) | CARD-EXPIRAION-DATE(80-89) | CARD-ACTIVE-STATUS(90)
--                     FILLER(91-149)
--
-- MERGE used so repeated application starts remain idempotent.

MERGE INTO card_records KEY(card_num) VALUES ('0500024453765740', 50,  '747', 'Aniya Von',           '2023-03-09', 'Y');
MERGE INTO card_records KEY(card_num) VALUES ('0683586198171516', 27,  '567', 'Ward Jones',          '2025-07-13', 'Y');
MERGE INTO card_records KEY(card_num) VALUES ('0923877193247330',  2,  '028', 'Enrico Rosenbaum',    '2024-08-11', 'Y');
MERGE INTO card_records KEY(card_num) VALUES ('0927987108636232', 20,  '003', 'Carter Veum',         '2024-03-13', 'Y');
MERGE INTO card_records KEY(card_num) VALUES ('0982496213629795', 12,  '075', 'Maci Robel',          '2023-07-07', 'Y');
MERGE INTO card_records KEY(card_num) VALUES ('1014086565224350', 44,  '640', 'Irving Emard',        '2024-01-17', 'Y');
MERGE INTO card_records KEY(card_num) VALUES ('1142167692878931', 37,  '625', 'Shany Walker',        '2023-10-24', 'Y');
MERGE INTO card_records KEY(card_num) VALUES ('1561409106491600', 35,  '031', 'Angelica Dach',       '2025-09-23', 'Y');
MERGE INTO card_records KEY(card_num) VALUES ('2745303720002090', 39,  '033', 'Aliyah Berge',        '2025-09-08', 'Y');
MERGE INTO card_records KEY(card_num) VALUES ('2760836797107565', 24,  '859', 'Stefanie Dickinson',  '2025-02-11', 'Y');
