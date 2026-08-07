package com.fiscobcos.wallet.proxy.web;

import com.fiscobcos.wallet.proxy.service.JsonRpcProxyService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JsonRpcProxyController {

    private final JsonRpcProxyService proxyService;

    public JsonRpcProxyController(JsonRpcProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @PostMapping(
            path = "/rpc/{group}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> proxy(@PathVariable String group, @RequestBody String body) {
        return ResponseEntity.ok(proxyService.proxy(group, body));
    }
}

