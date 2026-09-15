-- 2026-09-15 — Didar (AI ekibi) entegrasyon rehberi REVIZE_2026-09-09 ile hizalanma.
--
-- AI Agent yaniti artik yalnizca (solution_uniqueid, solution, status) degil; rehberin §7'sindeki
-- genisletilmis sozlesmeyi tasiyor. Denetim/hata ayiklama icin asagidaki uc alan ai_process'e
-- eklenir. Mevcut V1 DEGISTIRILMEZ (EP CLAUDE.md §9 madde 7: "semayi yeni Flyway dosyasiyla degistir").
--
-- ⚠️ Kapsam notu: bu alanlar CPB'nin KENDI tablosundadir. Didar'in rehberi §7'de bunlari
-- "AI_ACTION_INBOX" basligi altinda listeliyor, ancak ai_action_inbox EP↔CPB arasindaki KUYRUK
-- tablosudur, AI'in yanit semasi degil (akis: AI yaniti → CPB donusturur → ai_action_inbox). Bu
-- yuzden statusResult/errorCode/transactionId gibi AI-seviyesi alanlar EP'nin paylasilan tablosuna
-- DEGIL, CPB'nin ai_process'ine yazilir — EP semasina hic dokunulmaz.

ALTER TABLE ai_process ADD COLUMN status_result  VARCHAR(20);   -- SUCCESS | PARTIAL | FAILURE (AI'in bildirdigi)
ALTER TABLE ai_process ADD COLUMN error_code     VARCHAR(50);   -- yalnizca statusResult=FAILURE iken dolu
ALTER TABLE ai_process ADD COLUMN transaction_id VARCHAR(100);  -- AI tarafindaki denetim kimligi

COMMENT ON COLUMN ai_process.status_result  IS 'AI Agent yanitindaki statusResult (rehber §7)';
COMMENT ON COLUMN ai_process.error_code     IS 'AI Agent yanitindaki errorCode (rehber §8) — FAILURE icin';
COMMENT ON COLUMN ai_process.transaction_id IS 'AI Agent yanitindaki transactionId (rehber §7)';
