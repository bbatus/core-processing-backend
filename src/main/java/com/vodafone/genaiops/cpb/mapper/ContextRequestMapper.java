package com.vodafone.genaiops.cpb.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.vodafone.genaiops.cpb.dto.AiFetchRequest;
import com.vodafone.genaiops.cpb.entity.AiDispatch;
import com.vodafone.genaiops.cpb.entity.Ticket;
import com.vodafone.genaiops.cpb.entity.TicketContext;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * EP'nin ürettiği {@code ticket_context.context_json}'ını AI Agent'a gönderilecek düz alanlara
 * ayrıştırır (bkz. TASARIM_PLANI §5.2/§6.1). Şema büyürse yalnızca bu sınıf değişir.
 */
@RequiredArgsConstructor
@Component
public class ContextRequestMapper {

    private final ObjectMapper objectMapper;

    public AiFetchRequest toRequest(Ticket ticket, TicketContext ticketContext, AiDispatch dispatch, int iteration) {
        JsonNode root = readTree(ticketContext.getContextJson());
        JsonNode ticketNode = path(root, "ticket");
        JsonNode categoryNode = path(ticketNode, "category");
        JsonNode customerNode = path(ticketNode, "customer");
        JsonNode processingNode = path(root, "processing");
        String humanFeedback = textOrNull(processingNode, "humanFeedback");

        return new AiFetchRequest(
                ticket.getDcaseTicketId() != null ? ticket.getDcaseTicketId().toString() : null,
                ticket.getTicketNumber(),
                dispatch.getVersion(),
                iteration,
                dispatch.getTriggerRule() != null ? dispatch.getTriggerRule().name() : null,
                textOrNull(categoryNode, "product"),
                textOrNull(categoryNode, "mainCategory"),
                textOrNull(categoryNode, "subCategory"),
                textOrNull(ticketNode, "title"),
                textOrNull(ticketNode, "description"),
                textOrNull(ticketNode, "phoneNumber"),
                textOrNull(customerNode, "fullName"),
                textOrNull(ticketNode, "priority"),
                humanFeedback,
                comments(root),
                root);
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return MissingNode.getInstance();
        }
    }

    private JsonNode path(JsonNode node, String field) {
        return node == null ? MissingNode.getInstance() : node.path(field);
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private List<JsonNode> comments(JsonNode root) {
        List<JsonNode> result = new ArrayList<>();
        JsonNode comments = path(root, "comments");
        if (comments instanceof ArrayNode array) {
            array.forEach(result::add);
        }
        return result;
    }
}
