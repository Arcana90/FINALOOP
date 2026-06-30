package com.example.frontend_emp_pass_slip.service;

import backend.passslip.DailyActivitySummary;
import backend.passslip.ReportEmployeeSummary;
import backend.passslip.WeeklyAwolRecord;
import backend.passslip.WeeklySlipDetailRecord;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WeeklyReportExporter {

    public static void exportToPdf(File file, List<DailyActivitySummary> dailySummaries,
                                   List<ReportEmployeeSummary> employeeSummaries,
                                   List<WeeklyAwolRecord> awolRecords,
                                   List<WeeklySlipDetailRecord> slipDetails) throws Exception {

        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(file));
        document.open();

        Font boldFont = new Font(Font.HELVETICA, 12, Font.BOLD);
        Font headerFont = new Font(Font.HELVETICA, 16, Font.BOLD);

        document.add(new Paragraph("WEEKLY PASS SLIP OPERATIONAL REPORT", headerFont));
        document.add(new Paragraph("Generated on: " + LocalDate.now() + "\n\n"));

        // --- 1. CALCULATE SUMMARY STATISTICS ---
        int totalSlips = slipDetails.size();
        int official = 0, personal = 0, emergency = 0, approved = 0;

        for (WeeklySlipDetailRecord slip : slipDetails) {
            String type = slip.getLeaveType().toLowerCase();
            if (type.contains("official")) official++;
            else if (type.contains("emergency")) emergency++;
            else personal++;

            if (slip.getStatus() != null && slip.getStatus().toLowerCase().contains("approved")) {
                approved++;
            }
        }

        double approvalRate = (totalSlips == 0) ? 0 : ((double) approved / totalSlips) * 100;
        int totalAwol = awolRecords.size();

        document.add(new Paragraph("SUMMARY STATISTICS", boldFont));
        document.add(new Paragraph("Total Number of Slips: " + totalSlips));
        document.add(new Paragraph("Official Pass Slips: " + official));
        document.add(new Paragraph("Personal Pass Slips: " + personal));
        document.add(new Paragraph("Emergency Pass Slips: " + emergency));
        document.add(new Paragraph("Approved Slips: " + approved));
        document.add(new Paragraph(String.format("Approval Rate: %.2f%%", approvalRate)));
        document.add(new Paragraph("Total AWOL Employees: " + totalAwol + "\n\n"));

        // --- 2. CALCULATE DAILY SUMMARY ---
        Map<String, Integer> slipsPerDay = new HashMap<>();
        Map<String, Integer> approvedPerDay = new HashMap<>();
        Map<String, Integer> awolPerDay = new HashMap<>();

        for (WeeklySlipDetailRecord slip : slipDetails) {
            String day = getDayOfWeek(slip.getDateIssued());
            slipsPerDay.put(day, slipsPerDay.getOrDefault(day, 0) + 1);
            if (slip.getStatus() != null && slip.getStatus().toLowerCase().contains("approved")) {
                approvedPerDay.put(day, approvedPerDay.getOrDefault(day, 0) + 1);
            }
        }

        for (WeeklyAwolRecord awol : awolRecords) {
            String day = getDayOfWeek(awol.getDateIssued());
            awolPerDay.put(day, awolPerDay.getOrDefault(day, 0) + 1);
        }

        String mostSlipsDay = getHighestDay(slipsPerDay);
        String leastSlipsDay = getLowestDay(slipsPerDay);
        String mostApprovedDay = getHighestDay(approvedPerDay);
        String mostAwolDay = getHighestDay(awolPerDay);

        document.add(new Paragraph("DAILY SUMMARY", boldFont));
        document.add(new Paragraph("Day with the most pass slips: " + mostSlipsDay));
        document.add(new Paragraph("Day with the least pass slips: " + leastSlipsDay));
        document.add(new Paragraph("Day with the most approved slips: " + mostApprovedDay));
        document.add(new Paragraph("Day with the most AWOL cases: " + mostAwolDay + "\n\n"));

        // --- 3. AWOL LIST TABLE ---
        document.add(new Paragraph("AWOL LIST", boldFont));

        if (awolRecords.isEmpty()) {
            document.add(new Paragraph("No AWOL records for this period."));
        } else {
            PdfPTable awolTable = new PdfPTable(4);
            awolTable.setWidthPercentage(100);
            awolTable.addCell("Employee ID");
            awolTable.addCell("Employee Name");
            awolTable.addCell("Department");
            awolTable.addCell("Date");

            for (WeeklyAwolRecord awol : awolRecords) {
                awolTable.addCell(awol.getEmployeeId());
                awolTable.addCell(awol.getName());
                awolTable.addCell(awol.getDepartment() != null ? awol.getDepartment() : "N/A");
                awolTable.addCell(awol.getDateIssued());
            }
            document.add(awolTable);
        }

        document.close();
    }

    public static void exportToCsv(File file, List<DailyActivitySummary> dailySummaries,
                                   List<ReportEmployeeSummary> employeeSummaries,
                                   List<WeeklyAwolRecord> awolRecords,
                                   List<WeeklySlipDetailRecord> slipDetails) throws Exception {

        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println("WEEKLY PASS SLIP OPERATIONAL REPORT");
            writer.println("Generated on," + LocalDate.now());
            writer.println();

            // --- 1. CALCULATE SUMMARY STATISTICS ---
            int totalSlips = slipDetails.size();
            int official = 0, personal = 0, emergency = 0, approved = 0;

            for (WeeklySlipDetailRecord slip : slipDetails) {
                    String type = slip.getLeaveType().toLowerCase();
                if (type.contains("official")) official++;
                else if (type.contains("emergency")) emergency++;
                else personal++;

                if (slip.getStatus() != null && slip.getStatus().toLowerCase().contains("approved")) {
                    approved++;
                }
            }
            double approvalRate = (totalSlips == 0) ? 0 : ((double) approved / totalSlips) * 100;

            writer.println("SUMMARY STATISTICS");
            writer.println("Total Number of Slips," + totalSlips);
            writer.println("Official Pass Slips," + official);
            writer.println("Personal Pass Slips," + personal);
            writer.println("Emergency Pass Slips," + emergency);
            writer.println("Approved Slips," + approved);
            writer.println(String.format("Approval Rate (%%),%.2f", approvalRate));
            writer.println("Total AWOL Employees," + awolRecords.size());
            writer.println();

            // --- 2. CALCULATE DAILY SUMMARY ---
            Map<String, Integer> slipsPerDay = new HashMap<>();
            Map<String, Integer> approvedPerDay = new HashMap<>();
            Map<String, Integer> awolPerDay = new HashMap<>();

            for (WeeklySlipDetailRecord slip : slipDetails) {
                String day = getDayOfWeek(slip.getDateIssued());
                slipsPerDay.put(day, slipsPerDay.getOrDefault(day, 0) + 1);
                if (slip.getStatus() != null && slip.getStatus().toLowerCase().contains("approved")) {
                    approvedPerDay.put(day, approvedPerDay.getOrDefault(day, 0) + 1);
                }
            }
            for (WeeklyAwolRecord awol : awolRecords) {
                String day = getDayOfWeek(awol.getDateIssued());
                awolPerDay.put(day, awolPerDay.getOrDefault(day, 0) + 1);
            }

            writer.println("DAILY SUMMARY");
            writer.println("Day with the most pass slips," + getHighestDay(slipsPerDay));
            writer.println("Day with the least pass slips," + getLowestDay(slipsPerDay));
            writer.println("Day with the most approved slips," + getHighestDay(approvedPerDay));
            writer.println("Day with the most AWOL cases," + getHighestDay(awolPerDay));
            writer.println();

            // --- 3. AWOL LIST TABLE ---
            writer.println("AWOL LIST");
            writer.println("Employee ID,Employee Name,Department,Date");

            for (WeeklyAwolRecord awol : awolRecords) {
                writer.println(String.format("\"%s\",\"%s\",\"%s\",\"%s\"",
                        awol.getEmployeeId(),
                        awol.getName(),
                        (awol.getDepartment() != null ? awol.getDepartment() : "N/A"),
                        awol.getDateIssued()));
            }
        }
    }

    // --- Helper Methods to calculate days ---

    private static String getDayOfWeek(String dateString) {
        if (dateString == null || dateString.isEmpty()) return "Unknown";
        try {
            // Assumes format like "2024-05-20" or "2024-05-20 14:30:00"
            String justDate = dateString.split(" ")[0];
            LocalDate date = LocalDate.parse(justDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            return date.getDayOfWeek().name().substring(0, 1).toUpperCase() + date.getDayOfWeek().name().substring(1).toLowerCase();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private static String getHighestDay(Map<String, Integer> map) {
        if (map.isEmpty()) return "N/A";
        return map.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
    }

    private static String getLowestDay(Map<String, Integer> map) {
        if (map.isEmpty()) return "N/A";
        return map.entrySet().stream().min(Map.Entry.comparingByValue()).get().getKey();
    }
}