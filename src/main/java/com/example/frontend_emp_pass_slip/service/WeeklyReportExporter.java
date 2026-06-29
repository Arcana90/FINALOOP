package com.example.frontend_emp_pass_slip.service;

import backend.passslip.DailyActivitySummary;
import backend.passslip.ReportEmployeeSummary;
import backend.passslip.WeeklyAwolRecord;
import backend.passslip.WeeklySlipDetailRecord;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class WeeklyReportExporter {

    public static void exportToPdf(File file,
                                   List<DailyActivitySummary> dailySummaries,
                                   List<ReportEmployeeSummary> employeeSummaries,
                                   List<WeeklyAwolRecord> awolRecords,
                                   List<WeeklySlipDetailRecord> slipDetails) throws Exception {

        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(file));
        document.open();

        document.add(new Paragraph("WEEKLY PASS SLIP OPERATIONAL REPORT"));
        document.add(new Paragraph("Generated Summary for the Tracking Period"));
        document.add(new Paragraph("\n"));

        // --- 1. DYNAMIC DATA METRICS INTERPRETATION ---
        String topDay = "N/A";
        int maxSlips = -1;
        for (DailyActivitySummary d : dailySummaries) {
            int total = d.getOfficialCount() + d.getPersonalCount() + d.getEmergencyCount();
            if (total > maxSlips && total > 0) {
                maxSlips = total;
                topDay = d.getDayName();
            }
        }

        Map<String, Integer> awolDaysMap = new HashMap<>();
        for (WeeklyAwolRecord a : awolRecords) {
            // FIX: Successfully calling .getDate() instead of non-existent .getDateIssued()
            String dateStr = a.getDate() != null ? a.getDate() : "Unknown";
            awolDaysMap.put(dateStr, awolDaysMap.getOrDefault(dateStr, 0) + 1);
        }
        String topAwolDay = "None Recorded";
        int maxAwol = 0;
        for (Map.Entry<String, Integer> entry : awolDaysMap.entrySet()) {
            if (entry.getValue() > maxAwol) {
                maxAwol = entry.getValue();
                topAwolDay = entry.getKey();
            }
        }

        document.add(new Paragraph("1. SYSTEM PERFORMANCE METRICS"));
        document.add(new Paragraph("Day with Most Approved Slips: " + topDay));
        document.add(new Paragraph("Day with Most AWOL Cases: " + topAwolDay));
        document.add(new Paragraph("\n"));

        // --- 2. DAILY SUMMARY ACTIVITY TABLE ---
        document.add(new Paragraph("2. DAILY LOG ACTIVITY METRICS"));
        PdfPTable dailyTable = new PdfPTable(4);
        dailyTable.setWidthPercentage(100);
        dailyTable.addCell("Day Name");
        dailyTable.addCell("Official Slips");
        dailyTable.addCell("Personal Slips");
        dailyTable.addCell("Emergency Slips");

        for (DailyActivitySummary d : dailySummaries) {
            dailyTable.addCell(d.getDayName());
            dailyTable.addCell(String.valueOf(d.getOfficialCount()));
            dailyTable.addCell(String.valueOf(d.getPersonalCount()));
            dailyTable.addCell(String.valueOf(d.getEmergencyCount()));
        }
        document.add(dailyTable);
        document.add(new Paragraph("\n"));

        // --- 3. EMPLOYEE TOTAL RECORDS LOG ---
        document.add(new Paragraph("3. EMPLOYEE UTILITY ENGAGEMENT METRICS"));
        PdfPTable empTable = new PdfPTable(6);
        empTable.setWidthPercentage(100);
        empTable.addCell("ID");
        empTable.addCell("Employee Name");
        empTable.addCell("Official");
        empTable.addCell("Personal");
        empTable.addCell("Emergency");
        empTable.addCell("Approved Total");

        for (ReportEmployeeSummary emp : employeeSummaries) {
            empTable.addCell(emp.getEmployeeId());
            empTable.addCell(emp.getEmployeeName());
            empTable.addCell(String.valueOf(emp.getOfficialCount()));
            empTable.addCell(String.valueOf(emp.getPersonalCount()));
            empTable.addCell(String.valueOf(emp.getEmergencyCount()));
            empTable.addCell(String.valueOf(emp.getApprovedCount()));
        }
        document.add(empTable);
        document.add(new Paragraph("\n"));

        // --- 4. DETAILED PASS SLIP TRANSACTION LOG ---
        document.add(new Paragraph("4. DETAILED PASS SLIP TRANSACTION LOG"));
        PdfPTable detailsTable = new PdfPTable(4);
        detailsTable.setWidthPercentage(100);
        detailsTable.addCell("Log Reference");
        detailsTable.addCell("Employee");
        detailsTable.addCell("Classification Type");
        detailsTable.addCell("Transaction Status");

        for (WeeklySlipDetailRecord detail : slipDetails) {
            detailsTable.addCell(detail.getSlipId());
            detailsTable.addCell(detail.getEmployeeName());
            detailsTable.addCell(detail.getLeaveType());
            detailsTable.addCell(detail.getStatus());
        }
        document.add(detailsTable);

        document.close();
    }

    public static void exportToCsv(File file,
                                   List<DailyActivitySummary> dailySummaries,
                                   List<ReportEmployeeSummary> employeeSummaries,
                                   List<WeeklyAwolRecord> awolRecords,
                                   List<WeeklySlipDetailRecord> slipDetails) throws Exception {
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println("WEEKLY PASS SLIP OPERATIONAL REPORT");
            writer.println();

            writer.println("DAILY ACTIVITY REGISTRY");
            writer.println("Day Name,Official Slips,Personal Slips,Emergency Slips");
            for (DailyActivitySummary d : dailySummaries) {
                writer.println(d.getDayName() + "," + d.getOfficialCount() + "," + d.getPersonalCount() + "," + d.getEmergencyCount());
            }
            writer.println();

            writer.println("EMPLOYEE PERFORMANCE SCORES");
            writer.println("ID,Employee Name,Official Count,Personal Count,Emergency Count,Approved Total");
            for (ReportEmployeeSummary emp : employeeSummaries) {
                writer.println("\"" + emp.getEmployeeId() + "\",\"" + emp.getEmployeeName() + "\"," + emp.getOfficialCount() + "," + emp.getPersonalCount() + "," + emp.getEmergencyCount() + "," + emp.getApprovedCount());
            }
            writer.println();

            writer.println("DETAILED PASS SLIP TRANSACTION LOG");
            writer.println("Log Reference,Employee,Classification Type,Transaction Status");
            for (WeeklySlipDetailRecord detail : slipDetails) {
                writer.println("\"" + detail.getSlipId() + "\",\"" + detail.getEmployeeName() + "\",\"" + detail.getLeaveType() + "\",\"" + detail.getStatus() + "\"");
            }
        }
    }
}