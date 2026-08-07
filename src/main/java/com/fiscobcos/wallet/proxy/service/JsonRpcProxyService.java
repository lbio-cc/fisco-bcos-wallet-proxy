package com.fiscobcos.wallet.proxy.service;

import static com.fiscobcos.wallet.proxy.model.JsonRpcErrors.INVALID_PARAMS;
import static com.fiscobcos.wallet.proxy.model.JsonRpcErrors.INVALID_REQUEST;
import static com.fiscobcos.wallet.proxy.model.JsonRpcErrors.METHOD_NOT_FOUND;
import static com.fiscobcos.wallet.proxy.model.JsonRpcErrors.PARSE_ERROR;
import static com.fiscobcos.wallet.proxy.model.JsonRpcErrors.UPSTREAM_UNAVAILABLE;

import com.fiscobcos.wallet.proxy.config.ProxyProperties;
import com.fiscobcos.wallet.proxy.model.JsonRpcErrors;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.fisco.bcos.sdk.v3.client.ClientImpl;
import org.fisco.bcos.sdk.v3.client.exceptions.ClientException;
import org.fisco.bcos.sdk.v3.client.protocol.request.JsonRpcRequest;
import org.fisco.bcos.sdk.v3.contract.precompiled.balance.BalanceService;
import org.fisco.bcos.sdk.v3.model.JsonRpcResponse;
import org.fisco.bcos.sdk.v3.transaction.model.exception.ContractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JsonRpcProxyService {

    private static final Logger log = LoggerFactory.getLogger(JsonRpcProxyService.class);

    private final ObjectMapper mapper;
    private final ProxyProperties properties;
    private final FiscoClientRegistry clientRegistry;

    public JsonRpcProxyService(
            ObjectMapper mapper,
            ProxyProperties properties,
            FiscoClientRegistry clientRegistry) {
        this.mapper = mapper;
        this.properties = properties;
        this.clientRegistry = clientRegistry;
    }

    public JsonNode proxy(String group, String body) {
        final JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return JsonRpcErrors.response(mapper, null, PARSE_ERROR, "Parse error");
        }

        if (root == null) {
            return JsonRpcErrors.response(mapper, null, INVALID_REQUEST, "Invalid Request");
        }
        if (root.isArray()) {
            return proxyBatch(group, root);
        }
        return proxyOne(group, root);
    }

    private JsonNode proxyBatch(String group, JsonNode root) {
        if (root.isEmpty() || root.size() > properties.getMaxBatchSize()) {
            return JsonRpcErrors.response(
                    mapper, null, INVALID_REQUEST, "Invalid or oversized batch request");
        }

        ArrayNode responses = mapper.createArrayNode();
        for (JsonNode request : root) {
            responses.add(proxyOne(group, request));
        }
        return responses;
    }

    private JsonNode proxyOne(String group, JsonNode request) {
        JsonNode id = request.isObject() ? request.get("id") : null;
        if (!isValidRequest(request)) {
            return JsonRpcErrors.response(mapper, id, INVALID_REQUEST, "Invalid Request");
        }

        String method = request.get("method").textValue();
        ProxyProperties.Group groupConfig;
        try {
            groupConfig = properties.requireGroup(group);
        } catch (IllegalStateException e) {
            return JsonRpcErrors.response(mapper, id, INVALID_PARAMS, "Group not configured");
        }

        if (!groupConfig.isEnabled()) {
            return JsonRpcErrors.response(
                    mapper, id, UPSTREAM_UNAVAILABLE, "Proxy group is disabled");
        }
        String groupId = groupConfig.getGroupId();
        if (groupId == null || groupId.isBlank()) {
            log.error("Group ID is empty for proxy.groups.{}.group-id", group);
            return JsonRpcErrors.response(
                    mapper, id, UPSTREAM_UNAVAILABLE, "Proxy group ID is not configured");
        }
        if (!properties.allowedMethodsFor(groupConfig).contains(method)) {
            return JsonRpcErrors.response(mapper, id, METHOD_NOT_FOUND, "Method not allowed");
        }

        ArrayNode params = (ArrayNode) request.get("params");
        try {
            if ("getBalance".equals(method)) {
                if (params.size() != 1
                        || !params.get(0).isTextual()
                        || params.get(0).textValue().isBlank()) {
                    return JsonRpcErrors.response(
                            mapper, id, INVALID_PARAMS, "getBalance expects one address parameter");
                }
                BalanceService balanceService = clientRegistry.getBalanceService(group);
                return successResponse(id, balanceService.getBalance(params.get(0).textValue()));
            }

            ClientImpl client = clientRegistry.get(group);
            List<Object> paramValues = new ArrayList<>(params.size() + 2);
            if (!properties.passthroughMethodsFor(groupConfig).contains(method)) {
                // FISCO native RPC expects group and target node before the public method params.
                paramValues.add(groupId);
                paramValues.add("");
            }
            params.forEach(node -> paramValues.add(mapper.convertValue(node, Object.class)));
            JsonRpcRequest<Object> upstreamRequest = new JsonRpcRequest<>(method, paramValues);

            @SuppressWarnings("unchecked")
            JsonRpcResponse<Object> upstreamResponse =
                    client.callRemoteMethod(
                            groupId,
                            "",
                            upstreamRequest,
                            (Class<JsonRpcResponse<Object>>) (Class<?>) JsonRpcResponse.class);

            return successResponse(id, upstreamResponse.getResult());
        } catch (ContractException e) {
            log.warn(
                    "FISCO balance call failed, route={}, groupId={}, gm={}, method={}, code={},"
                            + " message={}",
                    group,
                    groupId,
                    groupConfig.isGm(),
                    method,
                    e.getErrorCode(),
                    e.getMessage());
            int code = e.getErrorCode() == 0 ? UPSTREAM_UNAVAILABLE : e.getErrorCode();
            return JsonRpcErrors.response(mapper, id, code, "FISCO BCOS balance request failed");
        } catch (ClientException e) {
            log.warn(
                    "FISCO RPC call failed, route={}, groupId={}, gm={}, method={}, code={},"
                            + " message={}",
                    group,
                    groupId,
                    groupConfig.isGm(),
                    method,
                    e.getErrorCode(),
                    e.getErrorMessage());
            int code = e.getErrorCode() == 0 ? UPSTREAM_UNAVAILABLE : e.getErrorCode();
            return JsonRpcErrors.response(mapper, id, code, "FISCO BCOS upstream request failed");
        } catch (RuntimeException e) {
            log.error(
                    "FISCO RPC transport unavailable, route={}, groupId={}, gm={}, method={}",
                    group,
                    groupId,
                    groupConfig.isGm(),
                    method,
                    e);
            return JsonRpcErrors.response(
                    mapper, id, UPSTREAM_UNAVAILABLE, "FISCO BCOS upstream unavailable");
        }
    }

    private ObjectNode successResponse(JsonNode id, Object result) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", mapper.valueToTree(result));
        return response;
    }

    private boolean isValidRequest(JsonNode request) {
        if (!request.isObject()
                || !request.path("jsonrpc").isTextual()
                || !"2.0".equals(request.path("jsonrpc").textValue())
                || !request.path("method").isTextual()
                || request.path("method").textValue().isBlank()
                || request.path("method").textValue().length() > 128
                || !request.has("id")
                || !isValidId(request.get("id"))) {
            return false;
        }
        return request.has("params") && request.get("params").isArray();
    }

    private boolean isValidId(JsonNode id) {
        return id != null && (id.isTextual() || id.isIntegralNumber() || id.isNull());
    }
}

