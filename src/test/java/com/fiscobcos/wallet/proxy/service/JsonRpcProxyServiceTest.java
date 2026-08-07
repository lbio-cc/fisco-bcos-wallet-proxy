package com.fiscobcos.wallet.proxy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fiscobcos.wallet.proxy.config.ProxyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.fisco.bcos.sdk.v3.client.ClientImpl;
import org.fisco.bcos.sdk.v3.client.exceptions.ClientException;
import org.fisco.bcos.sdk.v3.client.protocol.request.JsonRpcRequest;
import org.fisco.bcos.sdk.v3.contract.precompiled.balance.BalanceService;
import org.fisco.bcos.sdk.v3.model.JsonRpcResponse;
import org.fisco.bcos.sdk.v3.transaction.model.exception.ContractException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class JsonRpcProxyServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final FiscoClientRegistry registry = Mockito.mock(FiscoClientRegistry.class);
    private JsonRpcProxyService service;

    @BeforeEach
    void setUp() {
        reset(registry);
        ProxyProperties.Group group = new ProxyProperties.Group();
        group.setGroupId("chainGroup0");

        ProxyProperties properties = new ProxyProperties();
        properties.setAllowedMethods(new LinkedHashSet<>(List.of("getBlockNumber")));
        properties.setGroups(Map.of("group0", group));
        service = new JsonRpcProxyService(mapper, properties, registry);
    }

    @Test
    void rejectsMethodOutsideWhitelistBeforeOpeningSdkConnection() {
        JsonNode response =
                service.proxy(
                        "group0",
                        """
                        {"jsonrpc":"2.0","id":1,"method":"sendTransaction","params":[]}
                        """);

        assertThat(response.at("/error/code").asInt()).isEqualTo(-32601);
        assertThat(response.at("/id").asInt()).isEqualTo(1);
        verifyNoInteractions(registry);
    }

    @Test
    void rejectsGroupOutsideWhitelistBeforeOpeningSdkConnection() {
        JsonNode response =
                service.proxy(
                        "group1",
                        """
                        {"jsonrpc":"2.0","id":"req-1","method":"getBlockNumber","params":[]}
                        """);

        assertThat(response.at("/error/code").asInt()).isEqualTo(-32602);
        assertThat(response.at("/id").asText()).isEqualTo("req-1");
        verifyNoInteractions(registry);
    }

    @Test
    void usesConfiguredGroupIdInsteadOfUrlGroupForUpstreamRpc() {
        ClientImpl client = Mockito.mock(ClientImpl.class);
        JsonRpcResponse<Object> upstream = new JsonRpcResponse<>();
        upstream.setResult(42);
        when(registry.get("group0")).thenReturn(client);
        when(client.callRemoteMethod(
                        eq("chainGroup0"),
                        eq(""),
                        any(JsonRpcRequest.class),
                        any()))
                .thenReturn(upstream);

        JsonNode response =
                service.proxy(
                        "group0",
                        """
                        {"jsonrpc":"2.0","id":7,"method":"getBlockNumber","params":[]}
                        """);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<JsonRpcRequest> requestCaptor = ArgumentCaptor.forClass(JsonRpcRequest.class);
        verify(client)
                .callRemoteMethod(
                        eq("chainGroup0"), eq(""), requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().getParams()).containsExactly("chainGroup0", "");
        assertThat(response.at("/result").asInt()).isEqualTo(42);
        assertThat(response.at("/id").asInt()).isEqualTo(7);
    }

    @Test
    void usesSdkBalanceServiceForGetBalance() throws Exception {
        ProxyProperties.Group group = new ProxyProperties.Group();
        group.setGroupId("chainGroup0");

        ProxyProperties properties = new ProxyProperties();
        properties.setAllowedMethods(new LinkedHashSet<>(List.of("getBalance")));
        properties.setGroups(Map.of("group0", group));
        JsonRpcProxyService balanceProxy =
                new JsonRpcProxyService(mapper, properties, registry);

        BalanceService balanceService = Mockito.mock(BalanceService.class);
        when(registry.getBalanceService("group0")).thenReturn(balanceService);
        when(balanceService.getBalance("0x1234")).thenReturn(new BigInteger("1000000000000000000"));

        JsonNode response =
                balanceProxy.proxy(
                        "group0",
                        """
                        {"jsonrpc":"2.0","id":8,"method":"getBalance","params":["0x1234"]}
                        """);

        verify(registry).getBalanceService("group0");
        verify(balanceService).getBalance("0x1234");
        verify(registry, org.mockito.Mockito.never()).get("group0");
        assertThat(response.at("/result").bigIntegerValue())
                .isEqualTo(new BigInteger("1000000000000000000"));
        assertThat(response.at("/id").asInt()).isEqualTo(8);
    }

    @Test
    void rejectsInvalidGetBalanceParamsBeforeOpeningSdkConnection() {
        ProxyProperties.Group group = new ProxyProperties.Group();
        group.setGroupId("chainGroup0");

        ProxyProperties properties = new ProxyProperties();
        properties.setAllowedMethods(new LinkedHashSet<>(List.of("getBalance")));
        properties.setGroups(Map.of("group0", group));
        JsonRpcProxyService balanceProxy =
                new JsonRpcProxyService(mapper, properties, registry);

        JsonNode response =
                balanceProxy.proxy(
                        "group0",
                        """
                        {"jsonrpc":"2.0","id":9,"method":"getBalance","params":[]}
                        """);

        assertThat(response.at("/error/code").asInt()).isEqualTo(-32602);
        verifyNoInteractions(registry);
    }

    @Test
    void rejectsMalformedJson() {
        JsonNode response = service.proxy("group0", "{");

        assertThat(response.at("/error/code").asInt()).isEqualTo(-32700);
        assertThat(response.get("id").isNull()).isTrue();
        verifyNoInteractions(registry);
    }

    @Test
    void appliesWhitelistToEveryBatchItem() {
        JsonNode response =
                service.proxy(
                        "group0",
                        """
                        [
                          {"jsonrpc":"2.0","id":1,"method":"sendTransaction","params":[]},
                          {"jsonrpc":"2.0","id":2,"method":"unknown","params":[]}
                        ]
                        """);

        assertThat(response.isArray()).isTrue();
        assertThat(response.get(0).at("/error/code").asInt()).isEqualTo(-32601);
        assertThat(response.get(1).at("/error/code").asInt()).isEqualTo(-32601);
        verifyNoInteractions(registry);
    }

    @Test
    void groupMethodWhitelistOverridesGlobalWhitelist() {
        ProxyProperties.Group group = new ProxyProperties.Group();
        group.setGroupId("chainGroup0");
        group.setAllowedMethods(new LinkedHashSet<>(List.of("getBlockByNumber")));

        ProxyProperties properties = new ProxyProperties();
        properties.setAllowedMethods(new LinkedHashSet<>(List.of("getBlockNumber")));
        properties.setGroups(Map.of("group0", group));
        JsonRpcProxyService overriddenService =
                new JsonRpcProxyService(mapper, properties, registry);

        JsonNode response =
                overriddenService.proxy(
                        "group0",
                        """
                        {"jsonrpc":"2.0","id":1,"method":"getBlockNumber","params":[]}
                        """);

        assertThat(response.at("/error/code").asInt()).isEqualTo(-32601);
        verifyNoInteractions(registry);
    }

    @Test
    void rejectsEmptyBatchRequest() {
        JsonNode response = service.proxy("group0", "[]");

        assertThat(response.at("/error/code").asInt()).isEqualTo(-32600);
        verifyNoInteractions(registry);
    }

    @Test
    void rejectsBatchExceedingMaxBatchSize() {
        ProxyProperties.Group group = new ProxyProperties.Group();
        group.setGroupId("chainGroup0");

        ProxyProperties properties = new ProxyProperties();
        properties.setMaxBatchSize(1);
        properties.setAllowedMethods(new LinkedHashSet<>(List.of("getBlockNumber")));
        properties.setGroups(Map.of("group0", group));
        JsonRpcProxyService limitedService = new JsonRpcProxyService(mapper, properties, registry);

        JsonNode response =
                limitedService.proxy(
                        "group0",
                        """
                        [
                          {"jsonrpc":"2.0","id":1,"method":"getBlockNumber","params":[]},
                          {"jsonrpc":"2.0","id":2,"method":"getBlockNumber","params":[]}
                        ]
                        """);

        assertThat(response.at("/error/code").asInt()).isEqualTo(-32600);
        verifyNoInteractions(registry);
    }

    @Test
    void insertsGroupAndNodeParamsWhenMethodIsNotPassthrough() {
        ClientImpl client = Mockito.mock(ClientImpl.class);
        JsonRpcResponse<Object> upstream = new JsonRpcResponse<>();
        upstream.setResult(List.of("peer1"));
        when(registry.get("group0")).thenReturn(client);
        when(client.callRemoteMethod(eq("chainGroup0"), eq(""), any(JsonRpcRequest.class), any()))
                .thenReturn(upstream);

        ProxyProperties.Group group = new ProxyProperties.Group();
        group.setGroupId("chainGroup0");

        ProxyProperties properties = new ProxyProperties();
        properties.setAllowedMethods(new LinkedHashSet<>(List.of("getPeers")));
        properties.setPassthroughMethods(new LinkedHashSet<>(List.of("getPeers")));
        properties.setGroups(Map.of("group0", group));
        JsonRpcProxyService passthroughService =
                new JsonRpcProxyService(mapper, properties, registry);

        passthroughService.proxy(
                "group0",
                """
                {"jsonrpc":"2.0","id":1,"method":"getPeers","params":[]}
                """);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<JsonRpcRequest> requestCaptor = ArgumentCaptor.forClass(JsonRpcRequest.class);
        verify(client).callRemoteMethod(eq("chainGroup0"), eq(""), requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().getParams()).isEmpty();
    }

    @Test
    void mapsContractExceptionToJsonRpcErrorWithoutLeakingUpstreamMessage() throws Exception {
        BalanceService balanceService = Mockito.mock(BalanceService.class);
        when(registry.getBalanceService("group0")).thenReturn(balanceService);

        ProxyProperties.Group group = new ProxyProperties.Group();
        group.setGroupId("chainGroup0");

        ProxyProperties properties = new ProxyProperties();
        properties.setAllowedMethods(new LinkedHashSet<>(List.of("getBalance")));
        properties.setGroups(Map.of("group0", group));
        JsonRpcProxyService balanceProxy = new JsonRpcProxyService(mapper, properties, registry);

        when(balanceService.getBalance("0x1234"))
                .thenThrow(new ContractException("internal upstream detail", 12345));

        JsonNode response =
                balanceProxy.proxy(
                        "group0",
                        """
                        {"jsonrpc":"2.0","id":1,"method":"getBalance","params":["0x1234"]}
                        """);

        assertThat(response.at("/error/code").asInt()).isEqualTo(12345);
        assertThat(response.at("/error/message").asText())
                .isEqualTo("FISCO BCOS balance request failed");
    }

    @Test
    void mapsClientExceptionToJsonRpcErrorWithoutLeakingUpstreamMessage() {
        ClientImpl client = Mockito.mock(ClientImpl.class);
        when(registry.get("group0")).thenReturn(client);
        when(client.callRemoteMethod(eq("chainGroup0"), eq(""), any(JsonRpcRequest.class), any()))
                .thenThrow(new ClientException(54321, "internal upstream detail", "internal upstream detail"));

        JsonNode response =
                service.proxy(
                        "group0",
                        """
                        {"jsonrpc":"2.0","id":1,"method":"getBlockNumber","params":[]}
                        """);

        assertThat(response.at("/error/code").asInt()).isEqualTo(54321);
        assertThat(response.at("/error/message").asText())
                .isEqualTo("FISCO BCOS upstream request failed");
    }

    @Test
    void mapsUnexpectedRuntimeExceptionToUpstreamUnavailable() {
        ClientImpl client = Mockito.mock(ClientImpl.class);
        when(registry.get("group0")).thenReturn(client);
        when(client.callRemoteMethod(eq("chainGroup0"), eq(""), any(JsonRpcRequest.class), any()))
                .thenThrow(new RuntimeException("connection refused"));

        JsonNode response =
                service.proxy(
                        "group0",
                        """
                        {"jsonrpc":"2.0","id":1,"method":"getBlockNumber","params":[]}
                        """);

        assertThat(response.at("/error/code").asInt()).isEqualTo(-32098);
        assertThat(response.at("/error/message").asText())
                .isEqualTo("FISCO BCOS upstream unavailable");
    }
}

