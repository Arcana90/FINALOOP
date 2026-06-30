package com.example.frontend_emp_pass_slip.service;

import backend.passslip.ReportEmployeeSummary;
import backend.passslip.WeeklyAwolRecord;
import backend.passslip.WeeklyBreakdownRecord;
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
import java.util.*;

public class MonthlyReportExporter {

    public static void exportToPdf(File file, String monthName,
                                   List<ReportEmployeeSummary> employeeSummaries,
                                   List<WeeklyAwolRecord> awolRecords,
                                   List<WeeklySlipDetailRecord> slipDetails) throws Exception {

        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(file));
        document.open();

        Font boldFont = new Font(Font.HELVETICA, 12, Font.BOLD);
        Font headerFont = new Font(Font.HELVETICA, 16, Font.BOLD);

        document.add(new Paragraph("MONTHLY PASS SLIP OPERATIONAL REPORT: " + monthName.toUpperCase(), headerFont));
        document.add(new Paragraph("Generated on: " + LocalDate.now() + "\n\n"));

        // --- 1. SUMMARY STATISTICS ---
        int totalSlips = slipDetails.size();
        // 🟢 ADDED DECLARATION HERE
        int official = 0, personal = 0, emergency = 0, approved = 0, rejected = 0;

        for (WeeklySlipDetailRecord slip : slipDetails) {
            String type = slip.getLeaveType().toLowerCase();
            if (type.contains("official")) official++;
            else if (type.contains("emergency")) emergency++;
            else personal++;

            if (slip.getStatus() != null) {
                String status = slip.getStatus().toLowerCase();
                // Count approved/returned/etc
                if (status.contains("approved") || status.contains("returned") || status.contains("excused") || status.contains("out")) {
                    approved++;
                }
                // 🟢 FIXED: Only count 'reject', ignore 'cancel'
                else if (status.contains("reject")) {
                    rejected++;
                }
            }
        }

        double approvalRate = (totalSlips == 0) ? 0 : ((double) approved / totalSlips) * 100;

        document.add(new Paragraph("SUMMARY STATISTICS", boldFont));
        document.add(new Paragraph("Total Number of Slips: " + totalSlips));
        document.add(new Paragraph("Official Pass Slip: " + official));
        document.add(new Paragraph("Personal Pass Slip: " + personal));
        document.add(new Paragraph("Emergency Pass Slip: " + emergency));
        document.add(new Paragraph("Approved Slips: " + approved));
        document.add(new Paragraph("Rejected Slips: " + rejected)); // Now works!
        document.add(new Paragraph(String.format("Approval Rate: %.2f%%", approvalRate)));
        document.add(new Paragraph("Total AWOL Instances: " + awolRecords.size() + "\n\n"));

        // --- 2. WEEKLY BREAKDOWN ---
        document.add(new Paragraph("WEEKLY BREAKDOWN", boldFont));
        List<WeeklyBreakdownRecord> weeklyData = calculateWeeklyBreakdown(slipDetails, awolRecords);

        PdfPTable weekTable = new PdfPTable(5);
        weekTable.setWidthPercentage(100);
        weekTable.addCell("Week");
        weekTable.addCell("Total Slips");
        weekTable.addCell("Approved");
        weekTable.addCell("Rejected");
        weekTable.addCell("AWOL");

        for (WeeklyBreakdownRecord w : weeklyData) {
            weekTable.addCell(w.getWeekName());
            weekTable.addCell(String.valueOf(w.getTotalSlips()));
            weekTable.addCell(String.valueOf(w.getApproved()));
            weekTable.addCell(String.valueOf(w.getRejected()));
            weekTable.addCell(String.valueOf(w.getAwol()));
        }
        document.add(weekTable);
        document.add(new Paragraph("\n"));
        // --- 3. EMPLOYEE SUMMARY ---
        document.add(new Paragraph("EMPLOYEE SUMMARY", boldFont));

        ReportEmployeeSummary mostSlips = employeeSummaries.stream().max(Comparator.comparingInt(ReportEmployeeSummary::getTotalCount)).orElse(null);
        ReportEmployeeSummary mostOfficial = employeeSummaries.stream().max(Comparator.comparingInt(ReportEmployeeSummary::getOfficialCount)).orElse(null);
        ReportEmployeeSummary mostPersonal = employeeSummaries.stream().max(Comparator.comparingInt(ReportEmployeeSummary::getPersonalCount)).orElse(null);
        ReportEmployeeSummary mostEmergency = employeeSummaries.stream().max(Comparator.comparingInt(ReportEmployeeSummary::getEmergencyCount)).orElse(null);

        document.add(new Paragraph("Most Pass Slips: " + (mostSlips != null && mostSlips.getTotalCount() > 0 ? mostSlips.getEmployeeName() + " (" + mostSlips.getTotalCount() + ")" : "N/A")));
        document.add(new Paragraph("Most Official Leaves: " + (mostOfficial != null && mostOfficial.getOfficialCount() > 0 ? mostOfficial.getEmployeeName() + " (" + mostOfficial.getOfficialCount() + ")" : "N/A")));
        document.add(new Paragraph("Most Personal Leaves: " + (mostPersonal != null && mostPersonal.getPersonalCount() > 0 ? mostPersonal.getEmployeeName() + " (" + mostPersonal.getPersonalCount() + ")" : "N/A")));
        document.add(new Paragraph("Most Emergency Leaves: " + (mostEmergency != null && mostEmergency.getEmergencyCount() > 0 ? mostEmergency.getEmployeeName() + " (" + mostEmergency.getEmergencyCount() + ")" : "N/A")));
        document.add(new Paragraph("\n"));

        // --- 4. MONTHLY HIGHLIGHTS ---
        document.add(new Paragraph("MONTHLY HIGHLIGHTS", boldFont));

        WeeklyBreakdownRecord maxApprovedWk = weeklyData.stream().max(Comparator.comparingInt(WeeklyBreakdownRecord::getApproved)).orElse(null);
        WeeklyBreakdownRecord maxSlipsWk = weeklyData.stream().max(Comparator.comparingInt(WeeklyBreakdownRecord::getTotalSlips)).orElse(null);
        WeeklyBreakdownRecord maxAwolWk = weeklyData.stream().max(Comparator.comparingInt(WeeklyBreakdownRecord::getAwol)).orElse(null);

        document.add(new Paragraph("Week with most approved slips: " + (maxApprovedWk != null && maxApprovedWk.getApproved() > 0 ? maxApprovedWk.getWeekName() : "N/A")));
        document.add(new Paragraph("Week with highest number of leaves: " + (maxSlipsWk != null && maxSlipsWk.getTotalSlips() > 0 ? maxSlipsWk.getWeekName() : "N/A")));
        document.add(new Paragraph("Week with most AWOL cases: " + (maxAwolWk != null && maxAwolWk.getAwol() > 0 ? maxAwolWk.getWeekName() : "N/A")));

        document.close();
    }

    public static void exportToCsv(File file, String monthName,
                                   List<ReportEmployeeSummary> employeeSummaries,
                                   List<WeeklyAwolRecord> awolRecords,
                                   List<WeeklySlipDetailRecord> slipDetails) throws Exception {

        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println("MONTHLY PASS SLIP OPERATIONAL REPORT: " + monthName.toUpperCase());
            writer.println("Generated on," + LocalDate.now());
            writer.println();

            int totalSlips = slipDetails.size();
            int official = 0, personal = 0, emergency = 0, approved = 0, rejected = 0; // Added rejected

            for (WeeklySlipDetailRecord slip : slipDetails) {
                String type = slip.getLeaveType().toLowerCase();
                if (type.contains("official")) official++;
                else if (type.contains("emergency")) emergency++;
                else personal++;

                if (slip.getStatus() != null) {
                    String stat = slip.getStatus().toLowerCase();
                    if (stat.contains("approved") || stat.contains("returned")) {
                        approved++;
                    } else if (stat.contains("reject")) {
                        rejected++; // Count rejected slips
                    }
                }
            }
            double approvalRate = (totalSlips == 0) ? 0 : ((double) approved / totalSlips) * 100;

            writer.println("SUMMARY STATISTICS");
            writer.println("Total Number of Slips," + totalSlips);
            writer.println("Official Leaves," + official);
            writer.println("Personal Leaves," + personal);
            writer.println("Emergency Leaves," + emergency);
            writer.println("Approved Slips," + approved);
            writer.println("Rejected Slips," + rejected); // Added this line
            writer.println(String.format("Approval Rate (%%),%.2f", approvalRate));
            writer.println("Total AWOL Instances," + awolRecords.size());
            writer.println();

            writer.println("WEEKLY BREAKDOWN");
            writer.println("Week,Total Slips,Approved,Rejected,AWOL");
            List<WeeklyBreakdownRecord> weeklyData = calculateWeeklyBreakdown(slipDetails, awolRecords);
            for (WeeklyBreakdownRecord w : weeklyData) {
                writer.println(w.getWeekName() + "," + w.getTotalSlips() + "," + w.getApproved() + "," + w.getRejected() + "," + w.getAwol());
            }
            writer.println();

            writer.println("EMPLOYEE SUMMARY");
            ReportEmployeeSummary mostSlips = employeeSummaries.stream().max(Comparator.comparingInt(ReportEmployeeSummary::getTotalCount)).orElse(null);
            ReportEmployeeSummary mostOfficial = employeeSummaries.stream().max(Comparator.comparingInt(ReportEmployeeSummary::getOfficialCount)).orElse(null);

            writer.println("Most Pass Slips," + (mostSlips != null && mostSlips.getTotalCount() > 0 ? mostSlips.getEmployeeName() : "N/A"));
            writer.println("Most Official Leaves," + (mostOfficial != null && mostOfficial.getOfficialCount() > 0 ? mostOfficial.getEmployeeName() : "N/A"));
        }
    }

    private static List<WeeklyBreakdownRecord> calculateWeeklyBreakdown(List<WeeklySlipDetailRecord> slips, List<WeeklyAwolRecord> awols) {
        int[] total = new int[4], appr = new int[4], rej = new int[4], awolCounts = new int[4];

        for (WeeklySlipDetailRecord slip : slips) {
            int weekIdx = getWeekIndex(slip.getDateIssued());
            total[weekIdx]++;
            if (slip.getStatus() != null) {
                // 🟢 UPDATED: Treat both 'Approved' and 'Returned' as approved
                if (slip.getStatus().toLowerCase().contains("approved") || slip.getStatus().toLowerCase().contains("returned")) {
                    appr[weekIdx]++;
                }
                if (slip.getStatus().toLowerCase().contains("rejected")) {
                    rej[weekIdx]++;
                }
            }
        }
        for (WeeklyAwolRecord awol : awols) {
            awolCounts[getWeekIndex(awol.getDateIssued())]++;
        }

        return Arrays.asList(
                new WeeklyBreakdownRecord("Week 1 (Days 1-7)", total[0], appr[0], rej[0], awolCounts[0]),
                new WeeklyBreakdownRecord("Week 2 (Days 8-14)", total[1], appr[1], rej[1], awolCounts[1]),
                new WeeklyBreakdownRecord("Week 3 (Days 15-21)", total[2], appr[2], rej[2], awolCounts[2]),
                new WeeklyBreakdownRecord("Week 4 (Days 22+)", total[3], appr[3], rej[3], awolCounts[3])
        );
    }

    private static int getWeekIndex(String dateStr) {
        try {
            int day = LocalDate.parse(dateStr.split(" ")[0], DateTimeFormatter.ofPattern("yyyy-MM-dd")).getDayOfMonth();
            if (day <= 7) return 0;
            if (day <= 14) return 1;
            if (day <= 21) return 2;
            return 3;
        } catch (Exception e) { return 0; }
    }
}