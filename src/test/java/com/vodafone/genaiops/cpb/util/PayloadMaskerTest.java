package com.vodafone.genaiops.cpb.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PayloadMaskerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void icIceGecmisPiiAlanlariMaskelenir() {
        String json = """
                {
                  "ticket": {
                    "title": "Iade bakiyeme yansimadi",
                    "phoneNumber": "905906020100",
                    "customer": { "fullName": "Ahmet Yilmaz", "identityNumber": "55555555550" },
                    "assignee": { "fullName": "GenAI Ops Test", "username": "srvc.vpaitst" }
                  }
                }
                """;

        String masked = PayloadMasker.maskJson(json, objectMapper);

        assertThat(masked).doesNotContain("905906020100", "Ahmet Yilmaz", "55555555550", "GenAI Ops Test");
        // Is icerigi ve PII olmayan teknik alanlar KORUNUR (aksi halde denetim izi ise yaramaz).
        assertThat(masked).contains("Iade bakiyeme yansimadi", "srvc.vpaitst");
    }

    @Test
    void dizilerinIcindekiAlanlarDaMaskelenir() {
        String json = """
                {"comments":[{"author":"x","fullName":"Gizli Kisi"},{"text":"normal yorum"}]}
                """;

        String masked = PayloadMasker.maskJson(json, objectMapper);

        assertThat(masked).doesNotContain("Gizli Kisi");
        assertThat(masked).contains("normal yorum");
    }

    @Test
    void kimlikBilgisiAlanlariDaMaskelenir() {
        String json = """
                {"authorization":"Bearer abc.def","apiKey":"super-secret","msisdn":"905551112233"}
                """;

        String masked = PayloadMasker.maskJson(json, objectMapper);

        assertThat(masked).doesNotContain("Bearer abc.def", "super-secret", "905551112233");
    }

    @Test
    void bozukJsonIcinGecerliBirJsonLiteralDoner_hamIcerikSIZDIRILMAZ() {
        String masked = PayloadMasker.maskJson("{bozuk 905906020100", objectMapper);

        assertThat(masked).doesNotContain("905906020100");
        assertThat(masked).startsWith("\"").endsWith("\"");
    }

    @Test
    void bosVeyaNullGirdiAynenDoner() {
        assertThat(PayloadMasker.maskJson((String) null, objectMapper)).isNull();
        assertThat(PayloadMasker.maskJson("", objectMapper)).isEmpty();
    }

    @Test
    void jsonNodeUzerindenMaskelemeOrijinalDugumuBOZMAZ() throws Exception {
        var node = objectMapper.readTree("""
                {"ticket":{"phoneNumber":"905906020100"}}
                """);

        String masked = PayloadMasker.maskJson(node, objectMapper);

        assertThat(masked).doesNotContain("905906020100");
        // ⚠️ Kritik: AI Agent'a giden GERCEK govde bu dugumden serialize edilir — maskeleme onu
        // degistirirse AI, Oracle sorgusu icin MSISDN'i alamaz.
        assertThat(node.path("ticket").path("phoneNumber").asText()).isEqualTo("905906020100");
    }
}
