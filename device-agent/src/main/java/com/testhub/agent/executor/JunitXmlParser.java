package com.testhub.agent.executor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Parses JUnit XML produced by a script bundle. Every {@code <testcase>} becomes
 * one agent step with status OK/FAIL and elapsed time in milliseconds.
 */
@Component
public class JunitXmlParser {

    private static final Logger log = LoggerFactory.getLogger(JunitXmlParser.class);

    /** One parsed testcase. */
    public record TestCase(String className, String name, String status, long elapsedMs) {

        public String decision() {
            if (className == null || className.isBlank()) {
                return name == null ? "" : name;
            }
            return name == null || name.isBlank() ? className : className + "." + name;
        }

        public boolean failed() {
            return "FAIL".equals(status);
        }
    }

    public List<TestCase> parseFiles(List<Path> files) {
        List<Path> ordered = new ArrayList<>(files);
        ordered.sort(Comparator.naturalOrder());
        List<TestCase> result = new ArrayList<>();
        for (Path file : ordered) {
            try {
                result.addAll(parse(file));
            } catch (Exception e) {
                log.warn("cannot parse JUnit XML {}: {}", file, e.getMessage());
            }
        }
        return result;
    }

    public List<TestCase> parse(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file)) {
            return parseStream(in);
        }
    }

    public List<TestCase> parseXml(String xml) throws Exception {
        try (InputStream in = new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return parseStream(in);
        }
    }

    private List<TestCase> parseStream(InputStream in) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(in);

        List<TestCase> cases = new ArrayList<>();
        NodeList nodes = document.getElementsByTagName("testcase");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String className = element.getAttribute("classname");
            String name = element.getAttribute("name");
            String status = hasFailureOrError(element) ? "FAIL" : "OK";
            long elapsedMs = parseTimeMillis(element.getAttribute("time"));
            cases.add(new TestCase(className, name, status, elapsedMs));
        }
        return cases;
    }

    private boolean hasFailureOrError(Element testcase) {
        NodeList children = testcase.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String tag = child.getNodeName();
            if ("failure".equals(tag) || "error".equals(tag)) {
                return true;
            }
        }
        return false;
    }

    private long parseTimeMillis(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            double seconds = Double.parseDouble(value.trim());
            return Math.max(0L, Math.round(seconds * 1000.0));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
