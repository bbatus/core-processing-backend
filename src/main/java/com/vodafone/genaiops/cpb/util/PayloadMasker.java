package com.vodafone.genaiops.cpb.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * KVKK maskeleme — {@code ai_interaction.request_body}'ye ve loglara yazilmadan ONCE uygulanir.
 *
 * <p><b>Neden gerekli (2026-09-15, Didar entegrasyon rehberi §6/§9 + e-posta madde 8):</b> AI Agent'a
 * giden istek, RAG/Oracle sorgusu icin MSISDN'i ve musteri adini <em>ham</em> tasimak ZORUNDA (AI
 * Agent bu bilgiyle bizim Oracle'imizdan musteri durumunu cekiyor — mimarinin geregi, kaldirilamaz;
 * Guvenlik ekibi onayina bagli). Ancak ayni ham govdeyi kendi denetim tablomuza/loglarimiza da
 * yazmak KVKK riski yaratir. Bu yuzden ayrim nettir:</p>
 * <ul>
 *   <li><b>AI Agent'a giden HTTP govdesi:</b> ham (RestClient nesneyi kendisi serialize eder).</li>
 *   <li><b>{@code ai_interaction.request_body} + loglar:</b> bu sinifla maskelenmis
 *       ({@link com.vodafone.genaiops.cpb.client.AiAgentClientImpl} ham JSON'u hic uretmez).</li>
 * </ul>
 *
 * <p><b>Bilinen sinir:</b> yalnizca <em>yapisal</em> alanlar (asagidaki anahtarlar) maskelenir. AI'in
 * dondurdugu serbest metin ({@code solution}) icinde musteri adi gecerse bu sinif onu yakalayamaz —
 * serbest metin maskelemesi (NER) kapsam disi, bilincli bir kabul.</p>
 */
public final class PayloadMasker {

    private static final String MASK = "***";

    /** KVKK — kisisel veri (Didar rehberi §6 "Kisisel veri (KVKK) — maskelenmeli" satiri). */
    private static final Set<String> PII_KEYS = Set.of(
            "phonenumber", "msisdn", "fullname", "identitynumber", "email");

    /** Kimlik bilgisi — EP'nin kendi PayloadMasker'iyla ayni liste (savunma amacli). */
    private static final Set<String> SECRET_KEYS = Set.of(
            "authorization", "access_token", "accesstoken", "client_secret", "clientsecret",
            "password", "token", "refresh_token", "refreshtoken", "apikey", "api_key");

    private PayloadMasker() {
    }

    /**
     * JSON govdesindeki PII ve kimlik bilgisi alanlarini (ic ice her seviyede) maskeler. Parse
     * edilemeyen govde icin gecerli bir JSON string literal doner ({@code jsonb} kolonuna
     * yazilabilsin diye) — orijinal (maskelenmemis) icerik ASLA saklanmaz.
     */
    public static String maskJson(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            maskNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "\"" + MASK + "-masking-failed" + MASK + "\"";
        }
    }

    /** {@link JsonNode} uzerinde dogrudan maskeleme — govde zaten parse edilmisse fazladan tur atmaz. */
    public static String maskJson(JsonNode node, ObjectMapper objectMapper) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        try {
            JsonNode copy = node.deepCopy();
            maskNode(copy);
            return objectMapper.writeValueAsString(copy);
        } catch (Exception e) {
            return "\"" + MASK + "-masking-failed" + MASK + "\"";
        }
    }

    private static void maskNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> fieldNames = new ArrayList<>();
            obj.fieldNames().forEachRemaining(fieldNames::add);
            for (String name : fieldNames) {
                String key = name.toLowerCase(Locale.ROOT);
                if (PII_KEYS.contains(key) || SECRET_KEYS.contains(key)) {
                    obj.put(name, MASK);
                } else {
                    maskNode(obj.get(name));
                }
            }
        } else if (node.isArray()) {
            node.forEach(PayloadMasker::maskNode);
        }
    }
}
