/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.excel;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.diagram.converter.DiagramConverterResultDTO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFPivotTable;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class ExcelWriterTest {

  private static final String SPREADSHEET_NS =
      "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
  private static final String CHART_NS = "http://schemas.openxmlformats.org/drawingml/2006/chart";

  @Test
  void generatedReportsDoNotContainCachedResultsFromTheTemplate() throws Exception {
    List<List<DiagramConverterResultDTO>> inputs =
        List.of(
            List.of(),
            List.of(result("one.bpmn")),
            List.of(result("first.bpmn"), result("second.dmn")));

    for (List<DiagramConverterResultDTO> results : inputs) {
      byte[] report = generateReport(results);

      try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report))) {
        XSSFSheet analysisResults = workbook.getSheet("AnalysisResults");
        assertThat(analysisResults.getLastRowNum()).isEqualTo(results.size());
        for (int i = 0; i < results.size(); i++) {
          assertThat(analysisResults.getRow(i + 1).getCell(0).getStringCellValue())
              .isEqualTo(results.get(i).getFilename());
        }

        int pivotCount = 0;
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
          XSSFSheet sheet = workbook.getSheetAt(i);
          for (XSSFPivotTable pivot : sheet.getPivotTables()) {
            pivotCount++;
            CellRangeAddress area =
                CellRangeAddress.valueOf(pivot.getCTPivotTableDefinition().getLocation().getRef());
            int firstDataRow =
                area.getFirstRow()
                    + Math.toIntExact(
                        pivot.getCTPivotTableDefinition().getLocation().getFirstDataRow());
            for (int rowIndex = firstDataRow; rowIndex <= area.getLastRow(); rowIndex++) {
              Row row = sheet.getRow(rowIndex);
              if (row != null) {
                for (int column = area.getFirstColumn(); column <= area.getLastColumn(); column++) {
                  assertThat(row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                      .as("%s!%s", sheet.getSheetName(), new CellReference(rowIndex, column))
                      .isNull();
                }
              }
            }
          }
        }
        assertThat(pivotCount).isEqualTo(5);
      }

      assertNoSerializedPivotOrChartData(report);
    }
  }

  @Test
  void reportExplainsWhyPivotTablesNeedEditingEnabled() throws Exception {
    try (XSSFWorkbook workbook =
        new XSSFWorkbook(new ByteArrayInputStream(generateReport(List.of())))) {
      assertThat(workbook.getSheet("AnalysisSummary").getRow(1).getCell(0).getStringCellValue())
          .contains("pivot tables", "Enable Editing", "AnalysisResults");
      assertThat(workbook.getSheet("AnalysisSummary").getMergedRegions())
          .anyMatch(range -> range.isInRange(1, 0) && range.isInRange(1, 26));
      assertThat(workbook.getSheet("PivotTable").getRow(1).getCell(0).getStringCellValue())
          .contains("pivot table", "Enable Editing");
    }
  }

  private static byte[] generateReport(List<DiagramConverterResultDTO> results) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    new ExcelWriter().writeResultsToExcel(results, output);
    return output.toByteArray();
  }

  private static DiagramConverterResultDTO result(String filename) {
    return new DiagramConverterResultDTO(
        filename,
        "Task",
        "task",
        "serviceTask",
        "TASK",
        "message-id",
        "Example",
        "https://example.com");
  }

  private static void assertNoSerializedPivotOrChartData(byte[] report) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

    int cacheDefinitions = 0;
    int cacheRecords = 0;
    int charts = 0;
    int pivotTables = 0;
    int sharedStringCount = -1;
    Set<Integer> referencedStrings = new HashSet<>();
    try (ZipInputStream entries = new ZipInputStream(new ByteArrayInputStream(report))) {
      ZipEntry entry;
      while ((entry = entries.getNextEntry()) != null) {
        String name = entry.getName();
        if (name.startsWith("xl/pivotCache/pivotCacheDefinition") && name.endsWith(".xml")) {
          cacheDefinitions++;
          Document xml =
              factory.newDocumentBuilder().parse(new ByteArrayInputStream(entries.readAllBytes()));
          Element definition = xml.getDocumentElement();
          assertThat(definition.getAttribute("recordCount")).as(name).isEqualTo("0");
          assertThat(definition.getAttribute("refreshOnLoad")).as(name).isEqualTo("1");
          NodeList fields = definition.getElementsByTagNameNS(SPREADSHEET_NS, "sharedItems");
          for (int i = 0; i < fields.getLength(); i++) {
            Node items = fields.item(i);
            for (Node item = items.getFirstChild(); item != null; item = item.getNextSibling()) {
              if (item instanceof Element value) {
                assertThat(value.getLocalName()).as(name).isEqualTo("m");
              }
            }
          }
        } else if (name.startsWith("xl/pivotCache/pivotCacheRecords") && name.endsWith(".xml")) {
          cacheRecords++;
          Document xml =
              factory.newDocumentBuilder().parse(new ByteArrayInputStream(entries.readAllBytes()));
          assertThat(xml.getDocumentElement().getAttribute("count")).as(name).isEqualTo("0");
          assertThat(xml.getElementsByTagNameNS(SPREADSHEET_NS, "r").getLength()).as(name).isZero();
        } else if (name.startsWith("xl/charts/chart") && name.endsWith(".xml")) {
          charts++;
          Document xml =
              factory.newDocumentBuilder().parse(new ByteArrayInputStream(entries.readAllBytes()));
          assertThat(xml.getElementsByTagNameNS(CHART_NS, "pt").getLength()).as(name).isZero();
          NodeList counts = xml.getElementsByTagNameNS(CHART_NS, "ptCount");
          for (int i = 0; i < counts.getLength(); i++) {
            assertThat(((Element) counts.item(i)).getAttribute("val")).as(name).isEqualTo("0");
          }
        } else if (name.startsWith("xl/pivotTables/pivotTable") && name.endsWith(".xml")) {
          pivotTables++;
          Document xml =
              factory.newDocumentBuilder().parse(new ByteArrayInputStream(entries.readAllBytes()));
          NodeList rowItems = xml.getElementsByTagNameNS(SPREADSHEET_NS, "rowItems");
          assertThat(rowItems.getLength()).as(name).isEqualTo(1);
          Element rowItemList = (Element) rowItems.item(0);
          assertThat(rowItemList.getAttribute("count")).as(name).isEqualTo("1");
          NodeList totalRows = rowItemList.getElementsByTagNameNS(SPREADSHEET_NS, "i");
          assertThat(totalRows.getLength()).as(name).isEqualTo(1);
          assertThat(((Element) totalRows.item(0)).getAttribute("t")).as(name).isEqualTo("grand");
          NodeList fields = xml.getElementsByTagNameNS(SPREADSHEET_NS, "pivotField");
          for (int i = 0; i < fields.getLength(); i++) {
            Element field = (Element) fields.item(i);
            if ("axisRow".equals(field.getAttribute("axis"))) {
              NodeList items = field.getElementsByTagNameNS(SPREADSHEET_NS, "items");
              assertThat(items.getLength()).as("%s field %d", name, i).isEqualTo(1);
              Element itemList = (Element) items.item(0);
              assertThat(itemList.getAttribute("count")).as(name).isEqualTo("1");
              NodeList defaults = itemList.getElementsByTagNameNS(SPREADSHEET_NS, "item");
              assertThat(defaults.getLength()).as(name).isEqualTo(1);
              assertThat(((Element) defaults.item(0)).getAttribute("t"))
                  .as(name)
                  .isEqualTo("default");
            }
          }
        } else if (name.equals("xl/sharedStrings.xml")) {
          Document xml =
              factory.newDocumentBuilder().parse(new ByteArrayInputStream(entries.readAllBytes()));
          sharedStringCount = xml.getElementsByTagNameNS(SPREADSHEET_NS, "si").getLength();
        } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
          Document xml =
              factory.newDocumentBuilder().parse(new ByteArrayInputStream(entries.readAllBytes()));
          NodeList cells = xml.getElementsByTagNameNS(SPREADSHEET_NS, "c");
          for (int i = 0; i < cells.getLength(); i++) {
            Element cell = (Element) cells.item(i);
            if ("s".equals(cell.getAttribute("t"))) {
              NodeList values = cell.getElementsByTagNameNS(SPREADSHEET_NS, "v");
              assertThat(values.getLength()).as("%s!%s", name, cell.getAttribute("r")).isEqualTo(1);
              referencedStrings.add(Integer.parseInt(values.item(0).getTextContent()));
            }
          }
        }
      }
    }
    assertThat(cacheDefinitions).isEqualTo(1);
    assertThat(cacheRecords).isEqualTo(cacheDefinitions);
    assertThat(charts).isEqualTo(3);
    assertThat(pivotTables).isEqualTo(5);
    assertThat(sharedStringCount).isPositive();
    assertThat(referencedStrings)
        .containsExactlyInAnyOrderElementsOf(
            IntStream.range(0, sharedStringCount).boxed().toList());
  }
}
