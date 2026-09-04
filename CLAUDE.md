# CLAUDE.md — core-processing-backend (CPB)

Bu dosya, Claude Code'un bu repoda çalışırken uyması gereken rehberdir. **Kodlamaya başlamadan önce
`task.md` (görev kırılımı) ve `genaiops-event-processor` reposundaki
`docs/spec md/CPB_TASARIM_VE_GELISTIRME_PLANI_2026-09-02.md` (TEK TEKNİK REFERANS) dosyalarını oku.**

> O doküman bu repoda YOK (genaiops-event-processor reposunda) — yerel bir kopyası varsa oradan,
> yoksa kullanıcıdan isteyin. Bu CLAUDE.md, o dokümanın "çalışma kuralları" özetidir; çelişkide
> **tasarım planı kazanır**.

---

## 1. Proje Nedir

`core-processing-backend` (CPB), GenAI Ops platformunun üç bileşeninden biridir:

| Servis | Sahibi | Bu repo mu? |
|---|---|---|
| **EP** (Event Processor) | VEPAS (biz) | Hayır — `genaiops-event-processor` reposu |
| **CPB** (Core Processing Backend) | VEPAS (biz) | **Evet — bu repo** |
| **AI Agent** | AI ekibi (Didar) | Hayır — ayrı, henüz bizim case'imiz için geliştirilmedi |

**CPB tek cümleyle:** EP'nin `ai_dispatch` kuyruğundan iş alır (paylaşılan PostgreSQL, `FOR UPDATE
SKIP LOCKED`), AI Agent'a HTTP ile bağlanır, dönen öneriyi `ai_action_inbox`'a yazar. Arada olan
biteni (`ai_process`/`ai_interaction`) kendi tablolarında denetim amaçlı saklar.

---

## 2. ⛔ KAPSAM SINIRI — EN ÖNEMLİ KURAL

Bu serviste **KESİNLİKLE** şunlar **YAZILMAZ**:

- ❌ **DCase'e hiçbir çağrı** — ne okur ne yazar. DCase'le konuşan **tek** servis EP'dir.
- ❌ **Kafka dinleme/yazma** — EP↔CPB entegrasyonu yalnızca paylaşılan PostgreSQL (K2).
- ❌ **Oracle veritabanlarına bağlantı** — müşteri verisini AI Agent kendi çeker.
- ❌ **LLM'in doğrudan çağrılması, prompt üretimi, RAG** — bunların hepsi AI Agent'ın (Didar) işi.
  CPB yalnızca AI Agent'ın **kendi REST API'sini** (fetch/confirm-status) konuşur.
- ❌ **Gerçek bir iş aksiyonunun (Oracle update, iade işlemi vb.) uygulanması** — Faz 2'de AI
  yalnızca öneri üretir, gerçek aksiyonu her zaman bir insan uygular (bkz. tasarım planı §2).
- ❌ **`confirm-status` endpoint'inin çağrılması** — kod hazır bırakılır ama
  `FLOW_AI_CONFIRM_ENABLED=false` (varsayılan) ile kapalıdır; Faz 3'te açılır.
- ❌ **EP'nin şemasını değiştirme** — `ticket`, `ticket_context`, `ai_dispatch`, `ai_action_inbox`,
  `audit_log` tabloları EP'nin migration'larıyla oluşturulur. CPB bunları **oluşturmaz**,
  yalnızca okur (`ticket`, `ticket_context`), günceller (`ai_dispatch.status`) veya ekler
  (`ai_action_inbox`, `audit_log`).

Bu serviste **YAZILIR**:

- ✅ `ai_dispatch` kuyruğunu yoklama (`SKIP LOCKED` claim, claim-timeout reaper)
- ✅ AI Agent'a HTTP çağrısı (retry + timeout, her denemenin izini tutma)
- ✅ `ai_action_inbox`'a öneri/kapanış aksiyonu yazma
- ✅ `ai_process` / `ai_interaction` (kendi tabloları — denetim izi)
- ✅ Kill-switch'lere saygı gösterme

> Şüphede `CPB_TASARIM_VE_GELISTIRME_PLANI_2026-09-02.md` §4'teki (YAPAR/YAPMAZ) tabloya bak.

---

## 3. Teknoloji Yığını (EP ile birebir aynı — değiştirme)

Java 21 · Spring Boot 3.3.x · Spring Data JPA (PostgreSQL) · Spring RestClient (AI Agent çağrısı,
Feign DEĞİL) · Resilience4j · Flyway · Micrometer/Prometheus · Testcontainers + WireMock · Maven ·
Containerfile (UBI9) · OpenShift · GitHub Enterprise Actions.

- **groupId:** `com.vodafone.genaiops` (EP ile aynı)
- **Java package:** `com.vodafone.genaiops.cpb`
- **Build:** `mvn clean verify`
- **Kafka/Redis/MinIO YOK** — CPB bunların hiçbirine bağlanmaz.

---

## 4. Veri Sözleşmesi Özeti

- **EP → CPB:** `ai_dispatch` (PENDING → CLAIMED → COMPLETED/FAILED), `ticket_context.context_json`.
- **CPB → AI Agent:** `POST {AI_AGENT_BASE_URL}{fetch-path}` — bkz. tasarım planı §6.1 alan listesi.
- **CPB → EP:** `ai_action_inbox` INSERT (`source_message_id` UNIQUE = idempotency anahtarı,
  `version` = R7 girdisi — **claim edilen dispatch'in versiyonu aynen kullanılır**).
- **CPB'nin kendi tabloları:** `ai_process` (tur özeti), `ai_interaction` (ham istek/yanıt, her
  deneme). Flyway migration'ı yalnızca bunları oluşturur — `flyway.table=flyway_schema_history_cpb`
  (EP ile aynı şemada AYRI history tablosu, çakışmasın diye).

**ADR-08 (EP reposundan, burada da geçerli):** iç PK'ler `BIGINT` + explicit `SEQUENCE`
(`allocationSize=50`). `ai_action_inbox` ve `audit_log`'a INSERT ederken kullanılan sequence'ler
(`ai_action_inbox_id_seq`, `audit_log_id_seq`) **EP'nin migration'ında oluşturulmuştur — burada
TEKRAR OLUŞTURULMAZ**, yalnızca aynı isim + `allocationSize=50` ile referans verilir.

---

## 5. Kill-Switch'ler

ConfigMap `core-processing-backend-flow`'dan gelir:

- `FLOW_ENABLED` (master), `FLOW_CPB_POLL_ENABLED`, `FLOW_AI_CALL_ENABLED`,
  `FLOW_INBOX_WRITE_ENABLED`, `FLOW_AI_CONFIRM_ENABLED` (Faz 3'e kadar `false`).
- Efektif değer her zaman `FLOW_ENABLED && FLOW_X`. Pod açılışında okunur — değişiklik sonrası
  `oc rollout restart` gerekir.
- **⚠️ AI Agent URL'i gerçek olana kadar (Didar ekibi, bkz. §1 tablosu) ilk TEST OCP deploy'unda
  `FLOW_AI_CALL_ENABLED=false` verilmesi ÖNERİLİR:** liveness/readiness (`/actuator/health/*`) AI
  Agent'a hiç dokunmaz (yalnızca DB sağlığına bakar), yani pod `AI_AGENT_BASE_URL` placeholder/sahte
  olsa bile SORUNSUZ ayağa kalkar — bu bayrak yalnızca gereksiz retry/log gürültüsünü (her dispatch'te
  3 deneme × Resilience4j) önlemek içindir, bir ön koşul DEĞİLDİR. Gerçek URL gelince configmap'i
  güncelleyip `oc rollout restart` yeterli.

---

## 6. Cross-Cutting Zorunluluklar

- **Idempotency:** `ai_action_inbox.source_message_id` deterministik üretilir
  (`UUID.nameUUIDFromBytes("dispatch:" + dispatchId)`) — aynı dispatch iki kez işlenirse ikinci
  yazma denemesi mevcut kaydı bulur, çift kayıt oluşmaz.
- **Eşzamanlılık:** `FOR UPDATE SKIP LOCKED` — ek bir dağıtık kilide (Redis) gerek YOK.
- **Denetim izi:** her AI çağrısı (`ai_interaction`) VE her önemli karar (`audit_log`, EP'nin
  paylaşılan tablosuna `CPB_*` kategorileriyle) kaydedilir.
- **Uzun metin:** AI'ın döndüğü tam metin DB'ye kayıpsız yazılır — kırpma yalnızca EP'nin DCase'e
  yazarken yaptığı `NoteTruncator` adımında olur, CPB'de KIRPMA YOK.
- **Logging:** structured JSON + MDC (`dcaseTicketId`/`dispatchId`/`version`/`iteration`/
  `correlationId`).

---

## 7. Çalışma Kuralları (Claude Code için)

1. **Önce oku:** `task.md` + (varsa yerel kopyası) `CPB_TASARIM_VE_GELISTIRME_PLANI_2026-09-02.md`.
2. **Kapsam dışına çıkma:** DCase/Kafka/Oracle/LLM kodu yazma (bkz. §2).
3. **Test:** her serviste unit; TC-C1..C9 (tasarım planı §13) entegrasyon (Testcontainers: PG,
   AI Agent = WireMock). Hedef %90 coverage. **2026-09-04'te gerçek Docker soketiyle koşturulup
   doğrulandı** (53/53 yeşil) — CI'da hâlâ `-Dtest='!*IntegrationTest'` ile dışarıda bırakılıyor
   (self-hosted runner'da Docker soketi yok, EP'deki aynı kısıt); tam kapı yerel `mvn clean verify`.
4. **Migration:** yalnızca `ai_process`/`ai_interaction` — EP'nin tablolarına ASLA migration yazma.
5. **Secrets:** repoya gerçek parola/API key yazma; `${...}` placeholder + GitHub Secrets.
6. **Kill-switch:** yeni bir dış-etki (AI çağrısı, inbox yazma) eklerken ilgili bayrağı kontrol et.
7. **Commit'ler küçük ve kapsamı net olsun.**

---

## 8. Komutlar

```bash
mvn clean verify                 # build + test
mvn spring-boot:run              # lokal çalıştırma (env/config gerekli, application-local.yml)
podman build -t core-processing-backend -f Containerfile .
oc apply -f k8s/                 # OCP deploy (CD pipeline yapar — configmap/flow/secret HARİÇ, elle)
```

---

## 9. Yapı

```
task.md          → görev kırılımı + güncel durum özeti
CLAUDE.md        → bu dosya
README.md        → proje tanıtımı, build/run/test/deploy how-to
docs/deployment-runbook.html → adım adım ilk-kurulum + OCP deploy + smoke doğrulama kılavuzu
                     (EP'nin runbook'uyla aynı format) — GHES clone'dan CI/CD tetiklemesine kadar
src/…            → kod (com.vodafone.genaiops.cpb)
k8s/…             → OCP manifest'leri (serviceaccount/service/deployment/hpa otomatik;
                     configmap/configmap-flow/secret İLK KURULUMDA ELLE)
.github/workflows/pipeline.yml → CI/CD (build-test-scan + build-and-deploy)
.github/workflows/{mend,fortify,sonar}.yml → EP'den birebir kopyalanan organizasyon-paylaşımlı
                     reusable workflow'lar (2026-09-04) — repo-agnostik, repo adını kendiliğinden
                     türetir; pipeline.yml'deki mend-scan/fortify-scan/sonar-scan job'ları bunları çağırır
```
