package com.easytest.engineering;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AtomsTest {

    @Test
    void httpAtomCallsServer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ping", exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            HttpAtom.Response response = HttpAtom.get("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/ping", Map.of(), 3000);
            assertThat(response.ok()).isTrue();
            assertThat(response.body()).contains("true");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void jdbcAtomQueriesH2() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:atomtest;DB_CLOSE_DELAY=-1", "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("create table home(id varchar(20), owner_id varchar(20))");
            statement.execute("insert into home values ('h1','u1')");
        }
        JdbcAtom.update("jdbc:h2:mem:atomtest;DB_CLOSE_DELAY=-1", "sa", "",
                "insert into home values (?, ?)", "h2", "u2");
        List<Map<String, Object>> rows = JdbcAtom.query("jdbc:h2:mem:atomtest;DB_CLOSE_DELAY=-1", "sa", "",
                "select id from home order by id");
        assertThat(rows).extracting(row -> row.get("ID")).containsExactly("h1", "h2");
    }

    @Test
    void rpcAtomUsesRegisteredInvoker() {
        RpcAtom.register((service, method, args, attachments) ->
                Map.of("service", service, "method", method));
        assertThat(RpcAtom.invoke("svc", "m", List.of(), Map.of()))
                .isEqualTo(Map.of("service", "svc", "method", "m"));
    }
}
