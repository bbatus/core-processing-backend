# task.md — core-processing-backend (CPB) Görev Kırılımı

> **Tek teknik referans:** `genaiops-event-processor` reposundaki
> `docs/spec md/CPB_TASARIM_VE_GELISTIRME_PLANI_2026-09-02.md` (§X buradaki referanslar bu dokümanın
> bölüm numaralarıdır). **Çalışma kuralları:** `CLAUDE.md` — özellikle ⛔ kapsam sınırı (DCase/Kafka/
> Oracle/LLM kodu YAZILMAZ). Her görev tamamlandığında: küçük, kapsamı net commit.
> Definition of Done: kod + unit test + `mvn clean verify` yeşil + ilgili kill-switch kontrolü.

---

## Durum Özeti (2026-09-04, güncellendi)

**Gerçek OCP deploy'u hariç** planlanan hemen hemen her şey tamamlandı:

- C1-C6 tamamlandı (iskelet, veri katmanı, poller, AI istemcisi, aksiyon üretimi — `relatedParty`
  dahil, MDC, Micrometer metrikleri).
- C7.1 (unit testler) tamamlandı: **49/49 yeşil**, davranış içeren TÜM sınıflar kapsandı
  (`FlowGuardTest`, `DispatchClaimServiceTest`, `DispatchProcessingServiceTest` — C5.3 regresyonu
  dahil, `ActionInboxWriterTest`, `ContextRequestMapperTest`, `AiAgentClientImplTest`,
  `DispatchPollerSchedulerTest`, `CpbMetricsTest`, `AuditLogServiceTest`, `AiAgentClientConfigTest`).
- **C7.2 (Testcontainers entegrasyon testleri) — 2026-09-04'te gerçek Docker ile ÇALIŞTIRILDI VE
  DOĞRULANDI.** İlk denemede `docker.sock`'u mount etmeden nested bir container'da çalıştırılmaya
  çalışıldığı için "Docker environment yok" hatası alınmıştı (yanlış teşhis: Ryuk erişilemezliği
  sanılmıştı, gerçek sebep hiç Docker soketi verilmemesiydi). Düzeltme: `docker.sock` mount edildi +
  `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` verildi (Docker Desktop for Mac'te sibling
  container'ların yayınladığı portlara bridge-gateway IP'siyle değil bu şekilde ulaşılabiliyor).
  Bu, gerçek bir test-izolasyonu hatasını ortaya çıkardı: `DispatchProcessingIntegrationTest`'in 3
  metodu sınıf-genelinde paylaşılan TEK bir Postgres container + gerçek arka plan scheduler
  kullanıyordu ama testler arası DB temizliği yoktu — `findAll()` ile global sayım yapan
  assertion'lar önceki test metodundan kalan satırları da görüyordu. `AbstractIntegrationTest`'e
  `@BeforeEach cleanDatabaseBeforeEachTest()` eklendi (ai_action_inbox/ai_interaction/ai_process/
  ai_dispatch/ticket_context/ticket/audit_log DELETE). Sonuç: **53/53 test yeşil** (49 unit + 4
  Testcontainers: TC-C1/C2/C6 + `KillSwitchIntegrationTest`), jacoco kapısı geçti. CI'da hâlâ
  `-Dtest='!*IntegrationTest'` ile dışarıda bırakılıyor (EP'deki aynı gerekçe: self-hosted runner'da
  Docker soketi yok) — ama artık "yazıldı ama hiç çalışmadı" değil, "yerel Docker'da kanıtlandı,
  CI'da bilinçli olarak atlanıyor" durumu.
- **C8.1** (CPB tek başına Docker simülasyonu) — tamamlandı, önceki turda.
- **C8.2** (EP + CPB birlikte, gerçek Kafka + gerçek Postgres + 2 WireMock) — **tamamlandı ve tam
  Faz 2 döngüsü ilk kez uçtan uca kanıtlandı**: Kafka event → EP R3/R4 → `ai_dispatch(PENDING)` →
  CPB claim → AI Agent çağrısı → `ai_action_inbox` (TMF621 `note[]`) → EP'nin
  `ActionInboxPoller`'ı → R7 versiyon kontrolü → DCase PATCH → `WAITING_APPROVAL` → (E7 yorum) →
  R5 → CPB 2. tur (iteration=2) → AI `NO_ACTION_NEEDED` → `TICKET.status=COMPLETED`. İki servisin
  `audit_log` kayıtları (EP'nin `RULE_EVALUATION`/`DISPATCH_CREATED`/`ACTION_APPLIED` + CPB'nin
  `CPB_*`) aynı tabloda, doğru sırayla, birlikte doğrulandı.
- **C8.3** (gerçek OCP deploy'u) — **kullanıcı kararıyla bu turun kapsamı dışında bırakıldı**,
  yapılmadı.
- **C9 — SonarQube kod kalitesi (yerel tarama + tam remediasyon, 2026-09-04)**: yerel SonarQube
  Community Edition (Docker, `sonar-net`) ile tarandı, 16 bulgu (1 HIGH/S1192 tekrarlı literal, 1
  MEDIUM/S6213 `record` adı Java 16+ kısıtlı tanımlayıcı, 8 INFO/S8688 `LocalDateTime.now()`→
  `now(ZoneOffset.UTC)`, 6 LOW test-hijyeni/S8924/S5853/S1128) tek tek çözüldü. Yeniden tarama
  sonucu: **0 bug, 0 vulnerability, 0 code smell, coverage %92.1, Quality Gate OK (tüm rating'ler
  A/1.0)**.
- **C10 — Fortify/Mend/Sonar CI'ya bağlandı (2026-09-04)**: EP'nin 3 organizasyon-paylaşımlı reusable
  workflow dosyası (`mend.yml`/`fortify.yml`/`sonar.yml` — tamamen repo-agnostik, `github.event.
  repository.name`'den kendi kendine repo adını türetiyor) bu repoya da birebir kopyalandı;
  `pipeline.yml`'e EP ile aynı yapıda `mend-scan`/`fortify-scan`/`sonar-scan` job'ları eklendi
  (`build-test-scan` ile paralel, `fortify_blocker="0"`/`sonar_blocker="false"` — EP'deki aynı
  2026-08-05 kararı). Artık placeholder değil, gerçek pipeline job'ları — ilk çalıştırma sonucu
  (organizasyon runner'larında gerçekten geçip geçmediği) push sonrası GHES'te görülecek.

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
- [x] **C5.4 — `relatedParty[role=assignee]` (§14-S9, artık ÇÖZÜLDÜ, 2026-09-04)**: EP'ye
  `ticket.previous_human_assignee_id`/`_name` alanları eklendi (EP reposu, commit `d2a7c37`, V5
  migration) — `AssigneeDetectorRule` (R4), assignee'yi AI hesabıyla üzerine yazmadan önce bu
  alanları event'in gerçek `prevAssignee`'sinden doldurur. CPB'nin `Ticket` entity'si bu alanları
  okur, `ActionInboxWriter` `requiresApproval=true` VE `previousHumanAssigneeId` doluysa
  `relatedParty[role=assignee]`'yi payload'a ekler. Unit test + entegrasyon senaryosunda
  doğrulandı. **Not:** şu an aktif olan GEÇİCİ R4 koşulu (EP commit `af5fcec`) yalnızca
  `prevAssignee==null` iken tetiklendiği için bu alan pratikte hâlâ boş kalıyor — hedef R4 koşulu
  geri açılınca (DCase hesabı oluşunca) gerçek değer taşımaya başlayacak.

## EPIC C6 — Denetim İzi ve Gözlemlenebilirlik

- [x] **C6.1 — `AuditLogService`**: EP'nin paylaşılan `audit_log`'una `CPB_*` kategorileriyle yazar
  (`CPB_DISPATCH_CLAIMED`, `CPB_AI_CALL_SUCCEEDED`, `CPB_AI_CALL_FAILED`, `CPB_INBOX_WRITTEN`,
  `CPB_SKIPPED_KILL_SWITCH`, `CPB_MAX_ITERATIONS_REACHED`) — **ep-frontend dashboard'ında ek
  geliştirme olmadan görünür** (aynı tablo).
  ⚠️ ~~`CPB_DISPATCH_CLAIMED` hiçbir yerden çağrılmıyor~~ — **düzeltildi**: `DispatchProcessingService`
  ticket'i okuduğu anda artık bu kategoriyle audit_log'a yazıyor.
- [x] **C6.2 — `ai_process`/`ai_interaction` kaydı**: her tur özeti + her HTTP denemesinin ham izi
  (istek/yanıt **kayıpsız**, kırpma YOK — kırpma yalnızca EP'nin `NoteTruncator`'ında).
- [x] **C6.3 — Metrikler (Micrometer)**: `CpbMetrics` — `cpb_dispatch_claimed_total`,
  `cpb_ai_call_duration_seconds`, `cpb_ai_call_failures_total`, `cpb_inbox_written_total`,
  `cpb_pending_dispatch` (gauge, `countByStatus` ile — **`FOR UPDATE SKIP LOCKED` sorgusuyla DEĞİL**,
  ilk taslakta bu hata vardı, satır kilitleyeceği için düzeltildi). `/actuator/prometheus`'tan
  otomatik export edilir.
- [x] **C6.4 — MDC doldurma**: `DispatchProcessingService.process()` artık `dispatchId`/
  `correlationId`'yi hemen, `dcaseTicketId`/`version`/`iteration`'ı elde edilir edilmez `MDC.put`
  ile doldurur, `finally` bloğunda temizler.

## EPIC C7 — Testler

- [x] **C7.1 — Unit testler, TÜM davranış içeren sınıflar (49/49 yeşil, 2026-09-04 tamamlandı)**:
  `FlowGuardTest`, `DispatchClaimServiceTest`, `DispatchProcessingServiceTest` (mutlu yol,
  kill-switch, AI hata, max-iterations, **C5.3 regresyon testi**), `ActionInboxWriterTest` (karar
  tablosu + `relatedParty` var/yok + idempotency), `ContextRequestMapperTest`,
  `AiAgentClientImplTest` (gerçek WireMock ile retry), `DispatchPollerSchedulerTest` (poll-kapalı
  kısayolu, bir işin hatasının diğerlerini engellemediği), `CpbMetricsTest` (sayaç/timer/gauge —
  gauge'un `countByStatus` kullandığını, `SKIP LOCKED` sorgusunu KULLANMADIĞINI doğrular),
  `AuditLogServiceTest` (JSON serileştirme başarısız olursa patlamıyor), `AiAgentClientConfigTest`
  (Authorization header apiKey'e göre eklenip eklenmediği). Entity/dto/enum/config-record'lar
  (davranış içermiyor, EP'nin kendi konvansiyonuyla tutarlı) ve `CoreProcessingBackendApplication`
  (pom.xml jacoco excludes'unda zaten hariç) bilerek test edilmedi.
- [x] **C7.2 — Entegrasyon testleri (Testcontainers PostgreSQL + WireMock) — KOD YAZILDI**:
  `AbstractIntegrationTest` + `DispatchProcessingIntegrationTest` (TC-C1 mutlu yol, TC-C2 AI
  sürekli 5xx→FAILED, TC-C6 idempotent reprocessing) + `KillSwitchIntegrationTest` (TC-C7).
  ⚠️ **Bu oturumun sandbox'ında ÇALIŞTIRILAMADI**: Docker-outside-of-Docker denendi
  (`docker.sock` mount edilebiliyor, proje dizini bind-mount edilemiyor — sandbox kısıtı), ama
  Testcontainers'ın Ryuk sidecar'ına ağ erişimi kurulamadı (`Could not connect to Ryuk at
  localhost:PORT`) — container-içi JVM'in, host daemon'un başlattığı sibling container'ların
  portlarına erişememesi. **EP'nin kendi CI'sinin aynı sebeple** ("bu self-hosted runner'da Docker
  soketi yok") entegrasyon testlerini atladığı kısıtın birebir aynısı. Kullanıcının kendi
  teriminalinde (gerçek Docker Desktop, bind-mount kısıtı yok) ya da Docker soketi olan bir CI
  runner'da `mvn clean verify` ile sorunsuz çalışması beklenir — test edilmedi ama kod EP'nin
  kendi entegrasyon test desenini birebir izliyor.
- [ ] **C7.3 — Jacoco %90 kapısı**: `mvn clean test` (yalnızca unit) ile ölçülmedi — entegrasyon
  testleri de dahil olmadan gerçek coverage oranı bilinmiyor; `mvn clean verify`'ın kullanıcının
  kendi ortamında çalıştırılması gerekiyor.

## EPIC C8 — Uçtan Uca ve Deploy

- [x] **C8.1 — Lokal Docker simülasyonu**: gerçek Postgres (EP'nin V1-V4 migration'ları + CPB'nin
  V1'i aynı şemada) + WireMock (Didar'ın AI Agent'ı yerine) + CPB imajı — tam zincir (claim→AI
  çağrısı→ai_process/ai_interaction→ai_action_inbox→dispatch kapanışı→audit_log) ve idempotency
  canlı doğrulandı (2026-09-04).
- [x] **C8.2 — EP ile birlikte tam simülasyon (2026-09-04, TAMAMLANDI) — bu projenin en önemli
  doğrulaması**: gerçek Kafka (KRaft tek node) + gerçek EP (V1-V5 migration'ları) + gerçek CPB +
  2 WireMock (DCase + AI Agent) aynı ağda. Gerçek fixture event'leri (`src/test/resources/events/
  5,6,7.json`, EP reposundan) Kafka'ya basıldı:
  - E5→E6: EP R3→R4, `ai_dispatch(PENDING)` yazıldı → **CPB claim etti** → AI Agent'a gerçek HTTP
    çağrısı → `ai_action_inbox` (TMF621 `note[]`, `requiresApproval=true`) → **EP'nin
    `ActionInboxPoller`'ı** aldı, R7 versiyon kontrolü geçti, gerçek DCase PATCH çağrısı (WireMock,
    200) → `APPLIED` → `TICKET.status=WAITING_APPROVAL`. Tüm bunlar **~8 saniyede, tamamen
    otomatik**.
  - E7 (yorum, WAITING_APPROVAL iken): EP R5 → yeni context/dispatch (v2) → CPB 2. tur
    (`iteration=2`) → AI Agent bu kez `NO_ACTION_NEEDED` döndü (WireMock stub'ı değiştirilerek
    test edildi) → `ai_action_inbox` (`requiresApproval=false`) → EP uyguladı →
    `TICKET.status=COMPLETED`.
  - EP'nin (`RULE_EVALUATION`/`DISPATCH_CREATED`/`ACTION_APPLIED`) ve CPB'nin (`CPB_DISPATCH_CLAIMED`/
    `CPB_AI_CALL_SUCCEEDED`/`CPB_INBOX_WRITTEN`) `audit_log` kayıtları **aynı tabloda, doğru
    kronolojik sırayla** birlikte doğrulandı — iki bağımsız mikroservisin paylaşılan Postgres
    üzerinden (K2) gerçekten çalıştığının ilk somut kanıtı.
- [ ] **C8.3 — OCP TEST deploy'u — KULLANICI KARARIYLA BU TURUN KAPSAMI DIŞINDA** (2026-09-04):
  "gerçek ocp deployu hariç her şeyi yapmamız lazım" — `k8s/configmap.yaml`/`configmap-flow.yaml`/
  `secret.yaml` ilk kurulumda elle `oc apply`; `AI_AGENT_BASE_URL` gerçek (ya da AI ekibinin
  sağladığı bir test) adresle doldurulmalı; pipeline `development`/`main`'e push ile tetiklenir.
- [ ] **C8.4 — README**: çalıştırma/config/kill-switch özeti (EP'nin N3'üyle aynı boşluk, orada da
  hâlâ yazılmadı).

---

## Açık Sorular / Dış Bağımlılıklar (tasarım planından, tekrar hatırlatma)

Tasarım planı §14'teki 11 madde (7'si AI ekibine — Didar, 4'ü SD'ye — Fatma) hâlâ açık. En kritik
ikisi: **S1** (confirm-status/oturum kapatma zorunlu mu) ve **S3** (AI Agent kimlik doğrulama
mekanizması — `AI_AGENT_API_KEY` şu an yalnızca Bearer header varsayımıyla kodlandı, gerçek
mekanizma netleşince değişebilir).
