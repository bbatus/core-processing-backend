# core-processing-backend (CPB)

GenAI Ops platformunun üç bileşeninden biri. `genaiops-event-processor` (EP) reposunun hazırladığı
`ai_dispatch` kuyruğundan (paylaşılan PostgreSQL) iş alır, AI Agent'a (Didar ekibinin ayrı servisi)
HTTP ile bağlanır, dönen öneriyi `ai_action_inbox`'a yazar. EP bu kaydı alıp DCase'e uygular.

**Tek teknik referans:** `genaiops-event-processor` reposundaki
`docs/spec md/CPB_TASARIM_VE_GELISTIRME_PLANI_2026-09-02.md`. Çalışma kuralları ve kapsam sınırı
için `CLAUDE.md`, güncel durum/kalan işler için `task.md`.

## Mimari — tek cümleyle

```
EP  --(ai_dispatch INSERT, paylaşılan Postgres)-->  CPB
CPB --(FOR UPDATE SKIP LOCKED ile claim)-->          kendi işini alır
CPB --(HTTP POST /fetch)-->                          AI Agent (Didar, ayrı servis)
CPB --(ai_action_inbox INSERT)-->                    EP
EP  --(R7 versiyon kontrolü + DCase Update)-->        gerçek ticket'a yazar
```

CPB; DCase'e hiç bağlanmaz, Kafka dinlemez/yazmaz, Oracle'a bağlanmaz, LLM'i doğrudan çağırmaz,
gerçek bir iş aksiyonu uygulamaz (Faz 2'de AI yalnızca öneri üretir). Detay: `CLAUDE.md` §2.

## Gereksinimler

- Java 21, Maven 3.9+
- Lokal çalıştırma için: PostgreSQL 16 (EP'nin şemasını paylaşan bir DB) + AI Agent'ı taklit eden
  bir HTTP stub (WireMock, testlerde otomatik)

## Build & Test

```bash
mvn clean verify
```

Bu komut hem unit testleri hem de Testcontainers tabanlı entegrasyon testlerini (gerçek Postgres,
WireMock ile AI Agent simülasyonu) çalıştırır ve jacoco %90 satır-kapsama kapısını uygular.
Docker Desktop (veya erişilebilir bir Docker soketi) gerektirir.

CI'da (self-hosted GitHub Actions runner, Docker soketi yok) yalnızca unit testler koşar:

```bash
mvn clean test -Dtest='!*IntegrationTest'
```

Tam entegrasyon testi kapısı yereldir; 2026-09-04'te gerçek Docker ile doğrulandı (53/53 test yeşil).
Docker Desktop for Mac'te bir container içinden (Docker-outside-of-Docker) çalıştırırken şu ek
bayraklar gerekir:

```bash
docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  <image> mvn clean verify
```

(`docker.sock` mount edilmezse Testcontainers Docker'ı hiç bulamaz; `TESTCONTAINERS_HOST_OVERRIDE`
verilmezse Ryuk'un yayınladığı porta sibling container'dan ulaşılamaz.)

## Lokal çalıştırma

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

`application-local.yml` gerekli env değişkenlerini (DB bağlantısı, `AI_AGENT_BASE_URL` vb.) ister —
bkz. dosyanın kendisi. `application-ocp.yml` hiçbir varsayılan İÇERMEZ (fail-fast); bu profil yalnızca
OCP'de env/ConfigMap ile doldurulmuş olarak kullanılmalıdır.

## Kill-switch'ler

`core-processing-backend-flow` ConfigMap'inden gelir, pod açılışında okunur (değişiklik sonrası
`oc rollout restart` gerekir):

| Bayrak | Ne yapar |
|---|---|
| `FLOW_ENABLED` | Master anahtar — kapalıysa hiçbir şey çalışmaz |
| `FLOW_CPB_POLL_ENABLED` | `ai_dispatch` kuyruğu yoklanır mı |
| `FLOW_AI_CALL_ENABLED` | AI Agent'a HTTP çağrısı yapılır mı |
| `FLOW_INBOX_WRITE_ENABLED` | `ai_action_inbox`'a yazılır mı |
| `FLOW_AI_CONFIRM_ENABLED` | Faz 3'e kadar `false` — `confirm-status` endpoint'i çağrılmaz |

**AI Agent URL'i henüz gerçek değilse** (Didar ekibinin servisi henüz yok — bkz. `k8s/configmap.yaml`
içindeki `AI_AGENT_BASE_URL` placeholder'ı), pod yine de sorunsuz ayağa kalkar: liveness/readiness
yalnızca DB'ye bakar, AI Agent'a hiç dokunmaz. Gereksiz retry/log gürültüsünü önlemek için ilk TEST
deploy'unda `FLOW_AI_CALL_ENABLED=false` verilmesi önerilir; gerçek URL gelince configmap güncellenip
`oc rollout restart` yeterlidir.

## Deploy (OpenShift)

**Adım adım ilk-kurulum rehberi:** [docs/deployment-runbook.html](docs/deployment-runbook.html) —
GHES'e ilk clone/push'tan, CI/CD tetiklemesine, `oc apply` komutlarına ve smoke doğrulamaya kadar
tüm adımlar (EP'nin kendi runbook'uyla aynı format).

CI/CD (`​.github/workflows/pipeline.yml`) `development`/`main`/`v*` push'larında imajı build edip
OCP'ye deploy eder (ServiceAccount/Service/HPA/Deployment otomatik). **İlk kurulumda elle** (pipeline
asla dokunmaz, config drift/kill-switch riski):

```bash
oc apply -f k8s/configmap.yaml
oc apply -f k8s/configmap-flow.yaml
# k8s/.env.secret.example'ı kopyalayıp doldurduktan sonra:
envsubst < k8s/secret.yaml | oc apply -f -
```

Route yok — dış erişim gerekmiyor, servis yalnızca EP'nin de bulunduğu namespace içinden erişilir.

## CI/CD tarama entegrasyonları

`mend-scan`/`fortify-scan`/`sonar-scan` job'ları (`.github/workflows/{mend,fortify,sonar}.yml`,
EP'den birebir kopyalandı) `build-test-scan` ile paralel çalışır. `fortify_blocker="0"` /
`sonar_blocker="false"` — bulgular loglanır/SSC'ye yüklenir ama pipeline'ı henüz kırmaz (EP'deki aynı
2026-08-05 kararı).

## Güncel durum ve bilinen boşluklar

Detaylı ve güncel liste için `task.md`'deki "Durum Özeti" bölümüne bakın. Özet:

- Kod, unit+entegrasyon testler (53/53), Sonar (0 bulgu, %92.1 coverage), CI wiring — tamamlandı.
- **Gerçek AI Agent URL'i henüz yok** (Didar ekibinden bekleniyor) — bu olmadan CPB canlıda hiçbir
  öneri üretemez, sadece bekler; ama bu, servisin OCP'ye deploy edilmesine engel DEĞİLDİR.
- Gerçek OCP (test) deploy'u henüz yapılmadı (önceki bir turda kullanıcı kararıyla kapsam dışı
  bırakılmıştı) — kod/config hazır.
