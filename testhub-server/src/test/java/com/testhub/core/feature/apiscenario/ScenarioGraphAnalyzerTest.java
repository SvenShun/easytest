package com.testhub.core.feature.apiscenario;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioGraphAnalyzerTest {

    private final ScenarioGraphAnalyzer analyzer = new ScenarioGraphAnalyzer();

    @Test
    void projectsStepsAndVariableFlow() {
        String yaml = """
                name: flow
                variables:
                  username: tester
                datasets:
                  users:
                    - {user: tester, locale: zh}
                steps:
                  - name: login
                    type: http
                    method: POST
                    path: /login
                    body: {username: "${username}"}
                    extract: {token: $.token}
                  - name: query
                    type: sql
                    query: SELECT 1
                    extract: {n: "rows[0].n"}
                  - name: compose
                    type: groovy
                    script: vars.put("combo", vars.get("token") + vars.get("n"))
                  - name: iterate
                    type: loop
                    dataset: users
                    steps:
                      - name: use-row
                        type: assert
                        assertions:
                          - {type: VARIABLE, path: combo, operator: CONTAINS, expected: "${user}"}
                  - name: missing
                    type: assert
                    assertions:
                      - {type: VARIABLE, path: never, operator: EQ, expected: "${undefinedVar}"}
                """;
        var graph = analyzer.analyze(yaml);
        assertThat(graph.stepCount()).isEqualTo(6);
        assertThat(graph.warnings()).anyMatch(message -> message.contains("undefinedVar"));
        assertThat(graph.variables()).anyMatch(flow -> flow.name().equals("token")
                && flow.producer().equals("login"));
        assertThat(graph.variables()).anyMatch(flow -> flow.name().equals("combo")
                && flow.producer().equals("compose"));
        assertThat(graph.steps()).anyMatch(node -> node.type().equals("loop") && node.children().size() == 1);
    }

    @Test
    void handlesBeforeAfterAndInvalidYaml() {
        String yaml = """
                name: hooks
                steps:
                  - name: http
                    type: http
                    method: GET
                    path: /health
                    before:
                      - {name: prepare, type: set, variables: {marker: ok}}
                    after:
                      - {name: verify, type: assert, assertions: [{type: VARIABLE, path: marker, operator: EQ, expected: ok}]}
                """;
        var graph = analyzer.analyze(yaml);
        assertThat(graph.steps()).extracting(ApiScenarioDtos.GraphNode::name)
                .containsExactly("[前置] prepare", "http", "[后置] verify");
        assertThatThrownBy(() -> analyzer.analyze("steps: ["))
                .hasMessageContaining("YAML");
    }
}
