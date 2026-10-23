package com.testhub.agent.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JunitXmlParserTest {

    @TempDir
    Path tempDir;

    private final JunitXmlParser parser = new JunitXmlParser();

    @Test
    void parsesSuccessfulAndFailedTestCases() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="LoginTest" tests="3" failures="1" errors="1" time="2.75">
                  <testcase classname="com.testhub.LoginTest" name="testLogin" time="1.5"/>
                  <testcase classname="com.testhub.LoginTest" name="testBadPassword" time="0.25">
                    <failure message="expected home">boom</failure>
                  </testcase>
                  <testcase classname="com.testhub.SearchTest" name="testSearch" time="1">
                    <error message="crash"/>
                  </testcase>
                </testsuite>
                """;

        List<JunitXmlParser.TestCase> cases = parser.parseXml(xml);

        assertEquals(3, cases.size());
        assertEquals("com.testhub.LoginTest.testLogin", cases.get(0).decision());
        assertEquals("OK", cases.get(0).status());
        assertEquals(1500L, cases.get(0).elapsedMs());
        assertFalse(cases.get(0).failed());

        assertEquals("com.testhub.LoginTest.testBadPassword", cases.get(1).decision());
        assertEquals("FAIL", cases.get(1).status());
        assertEquals(250L, cases.get(1).elapsedMs());
        assertTrue(cases.get(1).failed());

        assertEquals("FAIL", cases.get(2).status());
        assertEquals(1000L, cases.get(2).elapsedMs());
    }

    @Test
    void parsesXmlFile() throws Exception {
        Path file = tempDir.resolve("TEST-demo.xml");
        Files.writeString(file, """
                <testsuite>
                  <testcase classname="Demo" name="ok" time="0.1"/>
                </testsuite>
                """, StandardCharsets.UTF_8);

        List<JunitXmlParser.TestCase> cases = parser.parseFiles(List.of(file));
        assertEquals(1, cases.size());
        assertEquals("Demo.ok", cases.get(0).decision());
        assertEquals(100L, cases.get(0).elapsedMs());
    }

    @Test
    void returnsEmptyForNonJunitXml() throws Exception {
        List<JunitXmlParser.TestCase> cases = parser.parseXml("<root><child/></root>");
        assertTrue(cases.isEmpty());
    }
}
