package com.fbytes.geojsontokml;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


// Written by ChatGPT
public class GeoJSONToKML {

    static List<String> skipKeys = List.of(new String[]{"LENGTH", "pid", "awmc_class", "TNODE_", "featuretyp", "PROVINCE_1", "en_name", "awmc_impor",
            "Lon", "Shape_Leng", "FNODE_", "RPOLY_", "awmc_mod", "LPOLY_", "Lat", "PROVINCE10"});

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java GeoJSONToKML <inputGeoJSONFilePath> <outputKMLFilePrefix> <fileSizeLimitInMB>");
            return;
        }

        String inputFilePath = args[0];
        String outputFilePrefix = args[1];
        long fileSizeLimit = Long.parseLong(args[2]) * 1024 * 1024; // Convert MB to bytes

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFilePath))) {
            StringBuilder jsonContent = new StringBuilder();
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                jsonContent.append(line).append("\n");
                lineNumber++;
            }

            JSONObject geojson;
            try {
                geojson = new JSONObject(new JSONTokener(jsonContent.toString()));
            } catch (JSONException e) {
                System.err.println("JSON Parsing error at line " + lineNumber + ": " + e.getMessage());
                return;
            }

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            JSONArray features = geojson.getJSONArray("features");
            int fileIndex = 1;
            long currentFileSize = 0;
            Document doc = createNewDocument(docBuilder);
            Element documentElement = (Element) doc.getDocumentElement().getFirstChild();

            // Add styles
            addStyles(doc, documentElement);



            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.getJSONObject(i);
                Element placemark = doc.createElement("Placemark");
                documentElement.appendChild(placemark);

                String name = feature.optJSONObject("properties").optString("Name", "Unnamed road");
                Element nameElement = doc.createElement("name");
                nameElement.appendChild(doc.createTextNode(name));
                placemark.appendChild(nameElement);

                StringBuilder descriptionBuilder = new StringBuilder();
                JSONObject properties = feature.getJSONObject("properties");
                Iterator<String> keys = properties.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (skipKeys.contains(key))
                        continue;
                    Object value = properties.get(key);
                    if (value != null && !value.toString().isEmpty() && !value.toString().equals("null")) {
                        descriptionBuilder.append(key).append(": ").append(value).append("\n");
                    }
                }

                Element description = doc.createElement("description");
                description.appendChild(doc.createTextNode(descriptionBuilder.toString()));
                placemark.appendChild(description);

                String styleUrl = "#commonRoadStyle";
                if (properties.has("Major_or_M") && !properties.isNull("Major_or_M") && properties.getLong("Major_or_M")==1) {
                    styleUrl = "#majorRoadStyle";
                }
                Element styleUrlElement = doc.createElement("styleUrl");
                styleUrlElement.appendChild(doc.createTextNode(styleUrl));
                placemark.appendChild(styleUrlElement);

                JSONObject geometry = feature.getJSONObject("geometry");
                String type = geometry.getString("type");

                if ("Point".equals(type)) {
                    createPoint(doc, placemark, geometry);
                } else if ("LineString".equals(type)) {
                    createLineString(doc, placemark, geometry);
                } else if ("Polygon".equals(type)) {
                    createPolygon(doc, placemark, geometry);
                } else if ("MultiPoint".equals(type)) {
                    createMultiPoint(doc, placemark, geometry);
                } else if ("MultiLineString".equals(type)) {
                    createMultiLineString(doc, placemark, geometry);
                } else if ("MultiPolygon".equals(type)) {
                    createMultiPolygon(doc, placemark, geometry);
                }

                // Check if file size exceeds the limit
                StringWriter writer = new StringWriter();
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                DOMSource source = new DOMSource(doc);
                StreamResult result = new StreamResult(writer);
                transformer.transform(source, result);
                long fileSize = writer.toString().getBytes().length;

                if (currentFileSize + fileSize > fileSizeLimit) {
                    saveKMLFile(doc, outputFilePrefix, fileIndex);
                    fileIndex++;
                    currentFileSize = 0;
                    doc = createNewDocument(docBuilder);
                    documentElement = (Element) doc.getDocumentElement().getFirstChild();
                    // Add styles to new document
                    addStyles(doc, documentElement);
                } else {
                    currentFileSize += fileSize;
                }
            }

            // Save the last KML file
            if (currentFileSize > 0) {
                saveKMLFile(doc, outputFilePrefix, fileIndex);
            }

            System.out.println("KML files created successfully!");

        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Document createNewDocument(DocumentBuilder docBuilder) throws Exception {
        Document doc = docBuilder.newDocument();
        Element kmlElement = doc.createElement("kml");
        kmlElement.setAttribute("xmlns", "http://www.opengis.net/kml/2.2");
        doc.appendChild(kmlElement);
        Element documentElement = doc.createElement("Document");
        kmlElement.appendChild(documentElement);
        return doc;
    }

    private static void saveKMLFile(Document doc, String outputFilePrefix, int fileIndex) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(outputFilePrefix + "_" + fileIndex + ".kml"));
        transformer.transform(source, result);
    }

    private static void addStyles(Document doc, Element documentElement) {
        Element style1 = doc.createElement("Style");
        style1.setAttribute("id", "commonRoadStyle");
        Element lineStyle1 = doc.createElement("LineStyle");
        Element width1 = doc.createElement("width");
        width1.appendChild(doc.createTextNode("1.2"));
        lineStyle1.appendChild(width1);
        style1.appendChild(lineStyle1);
        documentElement.appendChild(style1);

        Element style2 = doc.createElement("Style");
        style2.setAttribute("id", "majorRoadStyle");
        Element lineStyle2 = doc.createElement("LineStyle");
        Element width2 = doc.createElement("width");
        width2.appendChild(doc.createTextNode("1.5"));
        lineStyle2.appendChild(width2);
        style2.appendChild(lineStyle2);
        documentElement.appendChild(style2);
    }

    private static void createPoint(Document doc, Element placemark, JSONObject geometry) {
        Element point = doc.createElement("Point");
        placemark.appendChild(point);

        JSONArray coordinates = geometry.getJSONArray("coordinates");
        Element coord = doc.createElement("coordinates");
        coord.appendChild(doc.createTextNode(coordinates.getDouble(0) + "," + coordinates.getDouble(1)));
        point.appendChild(coord);
    }

    private static void createLineString(Document doc, Element placemark, JSONObject geometry) {
        Element lineString = doc.createElement("LineString");
        placemark.appendChild(lineString);

        JSONArray coordinates = geometry.getJSONArray("coordinates");
        Element coord = doc.createElement("coordinates");
        StringBuilder coordText = new StringBuilder();
        for (int i = 0; i < coordinates.length(); i++) {
            JSONArray point = coordinates.getJSONArray(i);
            coordText.append(point.getDouble(0)).append(",").append(point.getDouble(1)).append(" ");
        }
        coord.appendChild(doc.createTextNode(coordText.toString().trim()));
        lineString.appendChild(coord);
    }

    private static void createPolygon(Document doc, Element placemark, JSONObject geometry) {
        Element polygon = doc.createElement("Polygon");
        placemark.appendChild(polygon);

        JSONArray coordinates = geometry.getJSONArray("coordinates");
        for (int i = 0; i < coordinates.length(); i++) {
            Element linearRing = doc.createElement("LinearRing");
            Element coord = doc.createElement("coordinates");
            StringBuilder coordText = new StringBuilder();
            JSONArray ring = coordinates.getJSONArray(i);
            for (int j = 0; j < ring.length(); j++) {
                JSONArray point = ring.getJSONArray(j);
                coordText.append(point.getDouble(0)).append(",").append(point.getDouble(1)).append(" ");
            }
            coord.appendChild(doc.createTextNode(coordText.toString().trim()));
            linearRing.appendChild(coord);
            polygon.appendChild(linearRing);
        }
    }

    private static void createMultiPoint(Document doc, Element placemark, JSONObject geometry) {
        JSONArray coordinates = geometry.getJSONArray("coordinates");
        for (int i = 0; i < coordinates.length(); i++) {
            Element point = doc.createElement("Point");
            placemark.appendChild(point);

            JSONArray pointCoords = coordinates.getJSONArray(i);
            Element coord = doc.createElement("coordinates");
            coord.appendChild(doc.createTextNode(pointCoords.getDouble(0) + "," + pointCoords.getDouble(1)));
            point.appendChild(coord);
        }
    }

    private static void createMultiLineString(Document doc, Element placemark, JSONObject geometry) {
        JSONArray coordinates = geometry.getJSONArray("coordinates");
        for (int i = 0; i < coordinates.length(); i++) {
            Element lineString = doc.createElement("LineString");
            placemark.appendChild(lineString);

            JSONArray lineCoords = coordinates.getJSONArray(i);
            Element coord = doc.createElement("coordinates");
            StringBuilder coordText = new StringBuilder();
            for (int j = 0; j < lineCoords.length(); j++) {
                JSONArray point = lineCoords.getJSONArray(j);
                coordText.append(point.getDouble(0)).append(",").append(point.getDouble(1)).append(" ");
            }
            coord.appendChild(doc.createTextNode(coordText.toString().trim()));
            lineString.appendChild(coord);
        }
    }

    private static void createMultiPolygon(Document doc, Element placemark, JSONObject geometry) {
        JSONArray coordinates = geometry.getJSONArray("coordinates");
        for (int i = 0; i < coordinates.length(); i++) {
            Element polygon = doc.createElement("Polygon");
            placemark.appendChild(polygon);

            JSONArray polygonCoords = coordinates.getJSONArray(i);
            for (int j = 0; j < polygonCoords.length(); j++) {
                Element linearRing = doc.createElement("LinearRing");
                Element coord = doc.createElement("coordinates");
                StringBuilder coordText = new StringBuilder();
                JSONArray ring = polygonCoords.getJSONArray(j);
                for (int k = 0; k < ring.length(); k++) {
                    JSONArray point = ring.getJSONArray(k);
                    coordText.append(point.getDouble(0)).append(",").append(point.getDouble(1)).append(" ");
                }
                coord.appendChild(doc.createTextNode(coordText.toString().trim()));
                linearRing.appendChild(coord);
                polygon.appendChild(linearRing);
            }
        }
    }
}
