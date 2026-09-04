# task.md — core-processing-backend (CPB) Görev Kırılımı

> **Tek teknik referans:** `genaiops-event-processor` reposundaki
> `docs/spec md/CPB_TASARIM_VE_GELISTIRME_PLANI_2026-09-02.md` (§X buradaki referanslar bu dokümanın
> bölüm numaralarıdır). **Çalışma kuralları:** `CLAUDE.md` — özellikle ⛔ kapsam sınırı (DCase/Kafka/
> Oracle/LLM kodu YAZILMAZ). Her görev tamamlandığında: küçük, kapsamı net commit.
> Definition of Done: kod + unit test + `mvn clean verify` yeşil + ilgili kill-switch kontrolü.

---

## Durum Özeti (2026-09-04)

İskelet + veri katmanı + dispatch işleme zinciri **uçtan uca Docker'da canlı doğrulandı** (gerçek
Postgres + EP'nin gerçek migration'ları + WireMock ile sahte AI Agent): claim (`SKIP LOCKED`) → AI
çağrısı → `ai_process`/`ai_interaction` kaydı → `ai_action_inbox` yazımı (gerçek TMF621 `note[]`
formatında) → `ai_dispatch` kapanışı → `audit_log`. İdempotency (aynı dispatch iki kez claim
edilirse ne `ai_action_inbox`'a çift kayıt ne de gereksiz ikinci bir AI çağrısı) test edilip
doğrulandı. **Henüz yapılmayan:** unit/entegrasyon testleri (C7), gerçek OCP deploy'u (C8),
Fortify/Mend/Sonar tarama entegrasyonu.

---

## EPIC C1 — Proje İskeleti

- [x] **C1.1 — pom.xml**: Java 21 / Spring Boot 3.3.4, `web`/`validation`/`data-jpa`/`actuator`,
  `resilience4j-spring-boot3` (2.2.0, pinlenmiş — EP'nin `spring-cloud-dependencies` BOM'u yok, bu
  yüzden versiyon açıkça verilmeli), `postgresql`, `flyway-core`+`flyway-database-postgresql`,
  `logstash-logback-encoder`, lombok; test: `spring-boot-starter-test`, testcontainers-postgresql,
  wiremock-standalone, awaitility. Jacoco %90 kapı (`verify`).
- [x] **C1.2 — Containerfile**: EP ile birebir aynı desen (UBI9, non-root 185), `EXPOSE 8083`.
- [x] **C1.3 — application.yml / application-local.yml / application-ocp.yml**: EP'nin 3 dosyalık
  profil deseni birebir. OCP profili **hiçbir varsayılan İÇERMEZ** (DB_HOST/PORT/NAME/USERNAME/
  PASSWORD zorunlu, eksikse fail-fast). `app.ai.*`/`app.dispatch.*`/`flow.*` `@ConfigurationProperties`
  record'ları.
- [x] **C1.4 — logback-spring.xml**: JSON structured log + MDC (dcaseTicketId/dispatchId/version/
  iteration/correlationId).
- [x] **C1.5 — k8s/**: `serviceaccount.yaml` (aynı `vodafone-githubtest` imagePullSecret),
  `service.yaml` (8083), `deployment.yaml` (envFrom: EP'nin `genaiops-event-processor-config`/
  `-secret` + CPB'nin kendi `core-processing-backend-config`/`-flow`/`-secret`, Kafka/Redis/MinIO
  volume'leri YOK), `hpa.yaml` (1→3), `configmap.yaml`/`configmap-flow.yaml`/`secret.yaml`
  (pipeline tarafından uygulanmaz — ilk kurulumda elle).
- [x] **C1.6 — .github/workflows/pipeline.yml**: EP'nin kök pipeline.yml'iyle birebir aynı yapı
  (ayrı repo olduğu için path filtresi yok). Fortify/Mend/Sonar reusable workflow'ları **bu repoda
  YOK** (EP reposunda yaşıyorlar) — bilerek eklenmedi, pipeline.yml içinde iki seçenek yorumlandı
  (dosyaları kopyala / cross-repo `uses:` — ikincisi doğrulanmadı).

## EPIC C2 — Veri Katmanı

- [x] **C2.1 — Flyway V1__create_cpb_tables.sql**: yalnızca `ai_process`+`ai_interaction` (kendi
  sequence'leri, ADR-08: `allocationSize=50`). `ticket`/`ticket_context`/`ai_dispatch`/
  `ai_action_inbox`/`audit_log` **BURADA OLUŞTURULMAZ** — EP'nin migration'ları zaten oluşturmuştur.
- [x] **C2.2 — `spring.flyway.table=flyway_schema_history_cpb`**: EP ile aynı şemada AYRI history
  tablosu — iki servis birbirinin migration geçmişini "kendi" sanıp çakışmasın diye.
  ⚠️ **Sahada bulunan gerçek sorun:** paylaşılan şema EP'nin tablolarıyla zaten dolu olduğu için
  Flyway `baselineOnMigrate=true` + `baselineVersion=0` OLMADAN "non-empty schema but no history
  table" hatası veriyordu; `baselineVersion` varsayılanı ("1") kullanılsaydı V1 migration'ı hiç
  ÇALIŞMAZDI (baseline'a dahil sayılıp atlanırdı) — `baseline-version: "0"` ile düzeltildi.
- [x] **C2.3 — Salt-okunur entity'ler**: `Ticket`, `TicketContext` (EP'nin `ep-frontend`'deki aynı
  deseni — plain `@Id`, `@GeneratedValue` YOK).
- [x] **C2.4 — `AiDispatch` entity**: okuma + `status`/`claimedBy`/`claimedAt`/`completedAt`/
  `errorMessage` güncelleme. `@GeneratedValue` YOK (CPB hiçbir zaman INSERT etmez).
- [x] **C2.5 — `AiActionInbox` entity**: CPB'nin INSERT ettiği tek EP tablosu.
  `@SequenceGenerator(sequenceName="ai_action_inbox_id_seq", allocationSize=50)` — **EP'nin
  sequence'iyle birebir aynı isim/allocationSize** (ADR-08 CPB bağlayıcı kuralı).
- [x] **C2.6 — `AiProcess`/`AiInteraction` entity'leri**: kendi sequence'leri, aynı `allocationSize=50`
  kuralı (kendi tabloları için de tutarlılık).
- [x] **C2.7 — `AuditLog` entity**: EP'nin paylaşılan `audit_log` tablosuna INSERT
  (`audit_log_id_seq`, `allocationSize=50` — EP'nin sequence'i, CPB tekrar oluşturmaz).
- [x] **C2.8 — Repository'ler**: `AiDispatchRepository.findPendingForUpdateSkipLocked` (native
  `FOR UPDATE SKIP LOCKED`), `findStaleClaimed` (claim-timeout reaper), `AiActionInboxRepository.
  findBySourceMessageId` (idempotency), `AiProcessRepository.findByDispatchIdAndIteration`.

## EPIC C3 — Dispatch Poller

- [x] **C3.1 — `DispatchClaimService.claimBatch()`**: `@Transactional`, `SKIP LOCKED` ile PENDING
  işleri claim eder (`app.dispatch.batch-size`, varsayılan 5) — **ek bir dağıtık kilide (Redis)
  gerek YOK**, Postgres'in kendi satır kilidi yeterli.
- [x] **C3.2 — Claim-timeout reaper**: `app.dispatch.claim-timeout-minutes` (varsayılan 15) süredir
  `CLAIMED`'de kalmış (worker çökmüş olabilir) işleri `PENDING`'e geri döndürür.
- [x] **C3.3 — `DispatchPollerScheduler`**: `@Scheduled` (2sn), `FlowGuard.isPollEnabled()` kontrolü,
  her claim edilen iş için `DispatchProcessingService.process()` çağrısı (bir işin hatası diğerlerini
  etkilemez — try/catch).

## EPIC C4 — AI Agent İstemcisi

- [x] **C4.1 — `AiAgentClient`/`AiAgentClientImpl`**: Spring `RestClient` (Feign DEĞİL — DCase'in
  aksine mTLS/özel truststore ihtiyacı olmadığı sürece gerek yok). **Manuel retry döngüsü**
  (Resilience4j'nin bildirim-tabanlı `@Retry`'si YERİNE bilerek) — her denemenin ham isteğini/
  yanıtını ayrı bir `AiCallResult` olarak döner, böylece HER deneme (başarısız olanlar dahil)
  `ai_interaction`'a kaydedilebilir ("AI ne yapmak istedi, ne döndü" — kullanıcı kararı, 2026-09-02).
- [x] **C4.2 — `ContextRequestMapper`**: `context_json`'dan (`JsonNode` ile) düz alanlar çıkarır
  (product/mainCategory/subCategory, title, description, msisdn, humanFeedback vb.) — bkz. tasarım
  planı §6.1.
- [x] **C4.3 — Timeout/retry konfigürasyonu**: `app.ai.connect-timeout-ms`/`read-timeout-seconds`/
  `max-attempts` (`SimpleClientHttpRequestFactory` üzerinden).

## EPIC C5 — Aksiyon Üretimi

- [x] **C5.1 — `ActionInboxWriter`**: `dcase_update_payload` üretimi (`note[]` — TMF621, gerçek
  DCase PATCH formatı; ⚠️ Didar'a önceden gönderilen `{"assigneeId","comment"}` örneği YANLIŞTI,
  bkz. tasarım planı §7.1). `requiresApproval`/`action_type` karar mantığı (§7.4): R4→her zaman
  `PROPOSE_RESOLUTION`/`true`; R5+AI `status=NO_ACTION_NEEDED`→`NO_ACTION_NEEDED`/`false`; aksi
  halde `PROPOSE_RESOLUTION`/`true`; max-iterations→`MAX_ITERATIONS_REACHED`/`false`.
- [x] **C5.2 — İdempotent `source_message_id`**: `UUID.nameUUIDFromBytes("dispatch:"+dispatchId)` —
  aynı dispatch tekrar işlenirse (§9.3 senaryosu) `findBySourceMessageId` mevcut kaydı bulur, çift
  yazma OLMAZ. **Sahada doğrulandı** (aynı dispatch iki kez claim edildi, tek `ai_action_inbox`
  satırı kaldı).
- [x] **C5.3 — İkinci bir idempotency katmanı (sahada bulunan gerçek gap, düzeltildi)**:
  `DispatchProcessingService`, bir dispatch/iteration için `ai_process` zaten SONUÇLANMIŞSA
  (`SUCCEEDED`/`FAILED`) AI Agent'ı **TEKRAR ÇAĞIRMAZ** — yalnızca dispatch'i o sonuca göre
  senkronlar. (İlk implementasyonda bu kontrol yoktu; reprocess senaryosunda gereksiz bir AI
  çağrısı yaptığı Docker simülasyonunda yakalandı ve düzeltildi.)
- [ ] **C5.4 — `relatedParty[role=assignee]` (açık madde, §14-S9)**: ticket'ı hangi insana geri
  atayacağımız (Hüseyin) bilgisi EP'nin `ticket` şemasında yok (`previous_human_assignee_id` alanı
  gerekiyor — EP tarafında küçük bir değişiklik). Şimdilik yalnızca `note[]` yazılıyor, assignee
  değişikliği İÇERMİYOR.

## EPIC C6 — Denetim İzi ve Gözlemlenebilirlik

- [x] **C6.1 — `AuditLogService`**: EP'nin paylaşılan `audit_log`'una `CPB_*` kategorileriyle yazar
  (`CPB_DISPATCH_CLAIMED`, `CPB_AI_CALL_SUCCEEDED`, `CPB_AI_CALL_FAILED`, `CPB_INBOX_WRITTEN`,
  `CPB_SKIPPED_KILL_SWITCH`, `CPB_MAX_ITERATIONS_REACHED`) — **ep-frontend dashboard'ında ek
  geliştirme olmadan görünür** (aynı tablo).
  ⚠️ `CPB_DISPATCH_CLAIMED` kategorisi tanımlı ama şu an hiçbir yerden çağrılmıyor (yalnızca
  `CLAIMED` sonrası claim log'u var, audit_log'a yazılmıyor) — küçük bir eksik, C7'de/sonraki bir
  turda eklenebilir.
- [x] **C6.2 — `ai_process`/`ai_interaction` kaydı**: her tur özeti + her HTTP denemesinin ham izi
  (istek/yanıt **kayıpsız**, kırpma YOK — kırpma yalnızca EP'nin `NoteTruncator`'ında).
- [ ] **C6.3 — Metrikler (Micrometer)**: `cpb_dispatch_claimed_total`,
  `cpb_ai_call_duration_seconds`, `cpb_ai_call_failures_total`, `cpb_inbox_written_total`,
  `cpb_pending_dispatch_gauge` — tasarım planı §11'de tanımlı, HENÜZ YAZILMADI.
- [ ] **C6.4 — MDC doldurma**: `logback-spring.xml`'deki MDC anahtarları tanımlı ama kod tarafında
  hiçbir yerde `MDC.put(...)` çağrılmıyor — loglar şu an structured JSON ama korelasyon alanları boş.

## EPIC C7 — Testler (HENÜZ BAŞLANMADI)

- [ ] **C7.1 — Unit testler**: `DispatchClaimService`, `ActionInboxWriter` (karar tablosu:
  R4/R5+NO_ACTION_NEEDED/R5+devam/max-iterations), `ContextRequestMapper` (context_json → istek
  alanları), `AiAgentClientImpl` (retry sayısı, her denemenin loglandığı), `FlowGuard`.
- [ ] **C7.2 — Entegrasyon testleri (Testcontainers PostgreSQL + WireMock)**: TC-C1 mutlu yol
  (dispatch→fetch→inbox), TC-C2 AI 5xx→retry→FAILED, TC-C3 timeout, TC-C4 claim-timeout reaper,
  TC-C5 arada `CANCELLED`/`OBSOLETE` olan dispatch atlanıyor, TC-C6 `source_message_id` çakışması
  idempotent, TC-C7 kill-switch'ler (`FLOW_AI_CALL_ENABLED=false` → dispatch PENDING'e döner),
  TC-C8 max-iterations kapanışı, TC-C9 zaten SUCCEEDED bir process tekrar AI çağırmıyor (C5.3'ün
  regresyon testi — **bu, gerçek sahada bulunan bir bug'ı temsil ediyor, mutlaka yazılmalı**).
- [ ] **C7.3 — Jacoco %90 kapısı**: `mvn clean verify` ile doğrulanmalı (şu an yalnızca `compile`
  denendi, hiç test yok — coverage %0).

## EPIC C8 — Uçtan Uca ve Deploy

- [x] **C8.1 — Lokal Docker simülasyonu**: gerçek Postgres (EP'nin V1-V4 migration'ları + CPB'nin
  V1'i aynı şemada) + WireMock (Didar'ın AI Agent'ı yerine) + CPB imajı — tam zincir (claim→AI
  çağrısı→ai_process/ai_interaction→ai_action_inbox→dispatch kapanışı→audit_log) ve idempotency
  canlı doğrulandı (2026-09-04).
- [ ] **C8.2 — EP ile birlikte tam simülasyon**: gerçek Kafka + gerçek EP + CPB + WireMock (DCase +
  AI Agent) — EP'nin R4'ü gerçekten `ai_dispatch` yazıp CPB'nin onu claim ettiği, sonucun EP'nin
  `ActionInboxPoller`'ı tarafından DCase'e (WireMock) PATCH edildiği tam döngü. (EP tarafında bu
  altyapı zaten kuruldu — bkz. EP reposundaki bu oturumun önceki turları — CPB eklenerek tekrar
  edilebilir.)
- [ ] **C8.3 — OCP TEST deploy'u**: `k8s/configmap.yaml`/`configmap-flow.yaml`/`secret.yaml` ilk
  kurulumda elle `oc apply`; `AI_AGENT_BASE_URL` gerçek (ya da AI ekibinin sağladığı bir test)
  adresle doldurulmalı; pipeline `development`/`main`'e push ile tetiklenir.
- [ ] **C8.4 — README**: çalıştırma/config/kill-switch özeti (EP'nin N3'üyle aynı boşluk, orada da
  hâlâ yazılmadı).

---

## Açık Sorular / Dış Bağımlılıklar (tasarım planından, tekrar hatırlatma)

Tasarım planı §14'teki 11 madde (7'si AI ekibine — Didar, 4'ü SD'ye — Fatma) hâlâ açık. En kritik
ikisi: **S1** (confirm-status/oturum kapatma zorunlu mu) ve **S3** (AI Agent kimlik doğrulama
mekanizması — `AI_AGENT_API_KEY` şu an yalnızca Bearer header varsayımıyla kodlandı, gerçek
mekanizma netleşince değişebilir).
