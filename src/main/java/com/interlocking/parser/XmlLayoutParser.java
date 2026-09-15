package com.interlocking.parser;

import com.interlocking.exception.LayoutParseException;
import com.interlocking.model.LayoutGraph;
import com.interlocking.model.Markerboard;
import com.interlocking.model.Neighbor;
import com.interlocking.model.TrackSection;
import com.interlocking.model.TrackSectionType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and validates a legal XML railway layout (FR-01/02/03/18), building the
 * in-memory LayoutGraph.
 *
 * <p>The supported format is the interlocking-tool export style, e.g.:
 * <pre>{@code
 * <xmi:XMI xmlns:xmi="http://www.omg.org/spec/XMI/2.4.1">
 *   <interlocking id="...">
 *     <network id="...">
 *       <trackSection id="533" length="100" type="linear">
 *         <neighbor ref="PM01U" side="up"/>
 *       </trackSection>
 *       <markerboard id="LU11" mounted="up" track="533" distance="20.0"/>
 *     </network>
 *   </interlocking>
 * </xmi:XMI>
 * }</pre>
 *
 * <p>Namespace prefixes on the wrapper elements (xmi:XMI, xmi:Documentation) are
 * ignored by parsing without namespace awareness, since every layout-bearing
 * element (trackSection, neighbor, markerboard) is unprefixed. The file is
 * validated structurally and referentially here rather than against a formal XSD:
 * well-formedness is enforced by the XML parser itself, and required
 * elements/attributes plus cross-references (neighbor/markerboard targets must
 * exist) are checked explicitly so a malformed or inconsistent file is rejected
 * with a clear reason and nothing partial is ever built (AC-13).
 */
@Component
public class XmlLayoutParser {

    public LayoutGraph parse(InputStream xmlInput) {
        Document document = parseXml(xmlInput);
        return buildGraph(document);
    }

    private Document parseXml(InputStream xmlInput) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            // Harden against XXE: no external DTDs/entities are needed for this format.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            List<String> problems = new ArrayList<>();
            builder.setErrorHandler(collectingErrorHandler(problems));

            Document document = builder.parse(new InputSource(xmlInput));
            if (!problems.isEmpty()) {
                throw new LayoutParseException("Layout XML is not well-formed: " + String.join("; ", problems));
            }
            return document;
        } catch (LayoutParseException e) {
            throw e;
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new LayoutParseException("Layout XML is not well-formed or is not a legal layout file: " + e.getMessage(), e);
        }
    }

    private ErrorHandler collectingErrorHandler(List<String> problems) {
        return new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) {
                // warnings do not fail the load
            }

            @Override
            public void error(SAXParseException exception) {
                problems.add(describe(exception));
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                problems.add(describe(exception));
                throw exception;
            }

            private String describe(SAXParseException exception) {
                return "line " + exception.getLineNumber() + ": " + exception.getMessage();
            }
        };
    }

    private LayoutGraph buildGraph(Document document) {
        Map<String, TrackSection> trackSections = new LinkedHashMap<>();
        Map<String, Markerboard> markerboards = new LinkedHashMap<>();

        List<Element> trackSectionElements = elements(document, "trackSection");
        if (trackSectionElements.isEmpty()) {
            throw new LayoutParseException("Layout does not contain any trackSection elements");
        }

        for (Element el : trackSectionElements) {
            String id = requireAttribute(el, "id", "trackSection");
            if (trackSections.containsKey(id)) {
                throw new LayoutParseException("Duplicate trackSection id: " + id);
            }
            double length = parseDouble(el.getAttribute("length"), "trackSection '" + id + "' length", 0.0);
            TrackSectionType type = TrackSectionType.fromXml(el.getAttribute("type"));
            List<Neighbor> neighbors = parseNeighbors(el, id);
            trackSections.put(id, new TrackSection(id, length, type, neighbors));
        }

        for (Element el : elements(document, "markerboard")) {
            String id = requireAttribute(el, "id", "markerboard");
            if (markerboards.containsKey(id)) {
                throw new LayoutParseException("Duplicate markerboard id: " + id);
            }
            String trackSectionId = requireAttribute(el, "track", "markerboard");
            String mounted = el.getAttribute("mounted");
            double distance = parseDouble(el.getAttribute("distance"), "markerboard '" + id + "' distance", 0.0);
            markerboards.put(id, new Markerboard(id, trackSectionId, mounted, distance));
        }

        validateReferentialIntegrity(trackSections, markerboards);

        return new LayoutGraph(trackSections, markerboards);
    }

    private List<Neighbor> parseNeighbors(Element trackSectionElement, String trackSectionId) {
        List<Neighbor> neighbors = new ArrayList<>();
        for (Element neighborEl : directChildElements(trackSectionElement, "neighbor")) {
            String ref = requireAttribute(neighborEl, "ref", "neighbor of trackSection '" + trackSectionId + "'");
            String side = neighborEl.getAttribute("side");
            neighbors.add(new Neighbor(ref, side == null ? "" : side));
        }
        return neighbors;
    }

    private void validateReferentialIntegrity(Map<String, TrackSection> trackSections,
                                               Map<String, Markerboard> markerboards) {
        for (TrackSection section : trackSections.values()) {
            for (String neighborId : section.neighborIds()) {
                if (!trackSections.containsKey(neighborId)) {
                    throw new LayoutParseException(
                            "trackSection '" + section.id() + "' references unknown neighbor '" + neighborId + "'");
                }
            }
        }
        for (Markerboard markerboard : markerboards.values()) {
            if (!trackSections.containsKey(markerboard.trackSectionId())) {
                throw new LayoutParseException(
                        "markerboard '" + markerboard.id() + "' references unknown trackSection '"
                                + markerboard.trackSectionId() + "'");
            }
        }
    }

    private List<Element> elements(Document document, String tagName) {
        NodeList nodeList = document.getElementsByTagName(tagName);
        List<Element> elements = new ArrayList<>(nodeList.getLength());
        for (int i = 0; i < nodeList.getLength(); i++) {
            elements.add((Element) nodeList.item(i));
        }
        return elements;
    }

    private List<Element> directChildElements(Element parent, String tagName) {
        List<Element> children = new ArrayList<>();
        NodeList childNodes = parent.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                children.add((Element) node);
            }
        }
        return children;
    }

    private String requireAttribute(Element element, String attributeName, String elementLabel) {
        String value = element.getAttribute(attributeName);
        if (value == null || value.isBlank()) {
            throw new LayoutParseException("Element '" + elementLabel + "' is missing required attribute '" + attributeName + "'");
        }
        return value.trim();
    }

    private double parseDouble(String rawValue, String label, double defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(rawValue.trim());
        } catch (NumberFormatException e) {
            throw new LayoutParseException("Invalid numeric value for " + label + ": '" + rawValue + "'");
        }
    }
}
