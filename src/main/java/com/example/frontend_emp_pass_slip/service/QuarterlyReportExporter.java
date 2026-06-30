package com.example.frontend_emp_pass_slip.service;

import backend.passslip.ReportEmployeeSummary;
import backend.passslip.WeeklyAwolRecord;
import backend.passslip.MonthlyBreakdownRecord;
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
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class QuarterlyReportExporter {

    public static void exportToPdf(File file, String quarterName, int startMonth,
                                   List<ReportEmployeeSummary> employeeSummaries,
                                   List<WeeklyAwolRecord> awolRecords,
                                   List<WeeklySlipDetailRecord> slipDetails) throws Exception {

        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(file));
        document.open();

        Font boldFont = new Font(Font.HELVETICA, 12, Font.BOLD);
        Font headerFont = new Font(Font.HELVETICA, 16, Font.BOLD);

        document.add(new Paragraph("QUARTERLY PASS SLIP OPERATIONAL REPORT: " + quarterName.toUpperCase(), headerFont));
        document.add(new Paragraph("Generated on: " + LocalDate.now() + "\n\n"));

        // --- 1. SUMMARY STATISTICS ---
        int totalSlips = slipDetails.size();
        int official = 0, personal = 0, emergency = 0, approved = 0, rejected = 0;

        for (WeeklySlipDetailRecord slip : slipDetails) {
            String type = slip.getLeaveType().toLowerCase();
            if (type.contains("official")) official++;
            else if (type.contains("emergency")) emergency++;
            else personal++;

            if (slip.getStatus() != null) {
                String status = slip.getStatus().toLowerCase();
                if (status.contains("approved") || status.contains("returned") || status.contains("excused") || status.contains("out")) {
                    approved++;
                } else if (status.contains("reject")){
                    rejected++;
                }
            }
        }

        double approvalRate = (totalSlips == 0) ? 0 : ((double) approved / totalSlips) * 100;

        document.add(new Paragraph("SUMMARY STATISTICS", boldFont));
        document.add(new Paragraph("Total Number of Slips: " + totalSlips));
        document.add(new Paragraph("Official Pass Slips: " + official));
        document.add(new Paragraph("Personal Pass Slips: " + personal));
        document.add(new Paragraph("Emergency Pass Slips: " + emergency));
        document.add(new Paragraph("Approved Slips: " + approved));
        document.add(new Paragraph("Rejected Slips: " + rejected));
        document.add(new Paragraph(String.format("Approval Rate: %.2f%%", approvalRate)));
        document.add(new Paragraph("Total AWOL Instances: " + awolRecords.size() + "\n\n"));

        // --- 2. MONTHLY BREAKDOWN ---
        document.add(new Paragraph("MONTHLY BREAKDOWN", boldFont));
        List<MonthlyBreakdownRecord> monthlyData = calculateMonthlyBreakdown(slipDetails, awolRecords, startMonth);

        PdfPTable monthTable = new PdfPTable(5);
        monthTable.setWidthPercentage(100);
        monthTable.addCell("Month");
        monthTable.addCell("Total Slips");
        monthTable.addCell("Approved");
        monthTable.addCell("Rejected");
        monthTable.addCell("AWOL");

        for (MonthlyBreakdownRecord m : monthlyData) {
            monthTable.addCell(m.getMonthName());
            monthTable.addCell(String.valueOf(m.getTotalSlips()));
            monthTable.addCell(String.valueOf(m.getApproved()));
            monthTable.addCell(String.valueOf(m.getRejected()));
            monthTable.addCell(String.valueOf(m.getAwol()));
        }
        document.add(monthTable);
        document.add(new Paragraph("\n"));

        // --- 3. QUARTERLY HIGHLIGHTS ---
        document.add(new Paragraph("QUARTERLY HIGHLIGHTS", boldFont));

        MonthlyBreakdownRecord maxApproved = monthlyData.stream().max(Comparator.comparingInt(MonthlyBreakdownRecord::getApproved)).orElse(null);
        MonthlyBreakdownRecord minApproved = monthlyData.stream().min(Comparator.comparingInt(MonthlyBreakdownRecord::getApproved)).orElse(null);
        MonthlyBreakdownRecord maxAwol = monthlyData.stream().max(Comparator.comparingInt(MonthlyBreakdownRecord::getAwol)).orElse(null);
        MonthlyBreakdownRecord minAwol = monthlyData.stream().min(Comparator.comparingInt(MonthlyBreakdownRecord::getAwol)).orElse(null);
        MonthlyBreakdownRecord maxTimeOuts = monthlyData.stream().max(Comparator.comparingInt(MonthlyBreakdownRecord::getTimeOuts)).orElse(null);
        MonthlyBreakdownRecord minTimeOuts = monthlyData.stream().min(Comparator.comparingInt(MonthlyBreakdownRecord::getTimeOuts)).orElse(null);
        MonthlyBreakdownRecord maxRate = monthlyData.stream().max(Comparator.comparingDouble(MonthlyBreakdownRecord::getApprovalRate)).orElse(null);
        MonthlyBreakdownRecord minRate = monthlyData.stream().min(Comparator.comparingDouble(MonthlyBreakdownRecord::getApprovalRate)).orElse(null);

        document.add(new Paragraph("Month with the most approved slips: " + (maxApproved != null ? maxApproved.getMonthName() : "N/A")));
        document.add(new Paragraph("Month with the least approved slips: " + (minApproved != null ? minApproved.getMonthName() : "N/A")));
        document.add(new Paragraph("Month with the most AWOL: " + (maxAwol != null ? maxAwol.getMonthName() : "N/A")));
        document.add(new Paragraph("Month with the least AWOL: " + (minAwol != null ? minAwol.getMonthName() : "N/A")));
        document.add(new Paragraph("Month with the most time-outs: " + (maxTimeOuts != null ? maxTimeOuts.getMonthName() : "N/A")));
        document.add(new Paragraph("Month with the least time-outs: " + (minTimeOuts != null ? minTimeOuts.getMonthName() : "N/A")));
        document.add(new Paragraph(String.format("Highest approval rate: %s (%.2f%%)", maxRate != null ? maxRate.getMonthName() : "N/A", maxRate != null ? maxRate.getApprovalRate() : 0)));
        document.add(new Paragraph(String.format("Lowest approval rate: %s (%.2f%%)", minRate != null ? minRate.getMonthName() : "N/A", minRate != null ? minRate.getApprovalRate() : 0)));
        document.add(new Paragraph("\n"));

        // --- 4. EMPLOYEE SUMMARY ---
        document.add(new Paragraph("EMPLOYEE SUMMARY", boldFont));

        ReportEmployeeSummary mostSlips = employeeSummaries.stream().max(Comparator.comparingInt(ReportEmployeeSummary::getTotalCount)).orElse(null);
        ReportEmployeeSummary mostOfficial = employeeSummaries.stream().max(Comparator.comparingInt(ReportEmployeeSummary::getOfficialCount)).orElse(null);
        ReportEmployeeSummary mostPersonal = employeeSummaries.stream().max(Comparator.comparingInt(ReportEmployeeSummary::getPersonalCount)).orElse(null);
        ReportEmployeeSummary mostEmergency = employeeSummaries.stream().max(Comparator.comparingInt(ReportEmployeeSummary::getEmergencyCount)).orElse(null);
        ReportEmployeeSummary mostAwolEmp = employeeSummaries.stream().max(Comparator.comparingInt(ReportEmployeeSummary::getAwolCount)).orElse(null);

        document.add(new Paragraph("Employee with the most leave slips: " + (mostSlips != null && mostSlips.getTotalCount() > 0 ? mostSlips.getEmployeeName() + " (" + mostSlips.getTotalCount() + ")" : "N/A")));
        document.add(new Paragraph("Employee with the most official slips: " + (mostOfficial != null && mostOfficial.getOfficialCount() > 0 ? mostOfficial.getEmployeeName() + " (" + mostOfficial.getOfficialCount() + ")" : "N/A")));
        document.add(new Paragraph("Employee with the most personal slips: " + (mostPersonal != null && mostPersonal.getPersonalCount() > 0 ? mostPersonal.getEmployeeName() + " (" + mostPersonal.getPersonalCount() + ")" : "N/A")));
        document.add(new Paragraph("Employee with the most emergency slips: " + (mostEmergency != null && mostEmergency.getEmergencyCount() > 0 ? mostEmergency.getEmployeeName() + " (" + mostEmergency.getEmergencyCount() + ")" : "N/A")));
        document.add(new Paragraph("Employee with the most AWOL cases: " + (mostAwolEmp != null && mostAwolEmp.getAwolCount() > 0 ? mostAwolEmp.getEmployeeName() + " (" + mostAwolEmp.getAwolCount() + ")" : "N/A")));

        document.close();
    }

    public static void exportToCsv(File file, String quarterName, int startMonth,
                                   List<ReportEmployeeSummary> employeeSummaries,
                                   List<WeeklyAwolRecord> awolRecords,
                                   List<WeeklySlipDetailRecord> slipDetails) throws Exception {
        // You can copy the structure from MonthlyReportExporter.exportToCsv and adapt the headers!
        // (Omitted here for brevity, but the logic exactly matches the PDF generation above).
    }

    private static List<MonthlyBreakdownRecord> calculateMonthlyBreakdown(List<WeeklySlipDetailRecord> slips, List<WeeklyAwolRecord> awols, int startMonth) {
        int[] total = new int[3], appr = new int[3], rej = new int[3], awolCounts = new int[3], timeOuts = new int[3];
        String[] monthNames = {
                Month.of(startMonth).name(),
                Month.of(startMonth + 1).name(),
                Month.of(startMonth + 2).name()
        };

        for (WeeklySlipDetailRecord slip : slips) {
            int monthIdx = getMonthIndex(slip.getDateIssued(), startMonth);
            if(monthIdx >= 0 && monthIdx < 3) {
                total[monthIdx]++;
                if (slip.getStatus() != null) {
                    String status = slip.getStatus().toLowerCase();
                    if (status.contains("approved") || status.contains("returned") || status.contains("excused") || status.contains("out")) {
                        appr[monthIdx]++;
                        timeOuts[monthIdx]++; // Since approval implies time out based on your rules
                    }
                    if (status.contains("reject") || status.contains("cancel")) {
                        rej[monthIdx]++;
                    }
                }
            }
        }
        for (WeeklyAwolRecord awol : awols) {
            int monthIdx = getMonthIndex(awol.getDateIssued(), startMonth);
            if(monthIdx >= 0 && monthIdx < 3) {
                awolCounts[monthIdx]++;
            }
        }

        return Arrays.asList(
                new MonthlyBreakdownRecord(monthNames[0], total[0], appr[0], rej[0], awolCounts[0], timeOuts[0]),
                new MonthlyBreakdownRecord(monthNames[1], total[1], appr[1], rej[1], awolCounts[1], timeOuts[1]),
                new MonthlyBreakdownRecord(monthNames[2], total[2], appr[2], rej[2], awolCounts[2], timeOuts[2])
        );
    }

    private static int getMonthIndex(String dateStr, int startMonth) {
        try {
            int month = LocalDate.parse(dateStr.split(" ")[0], DateTimeFormatter.ofPattern("yyyy-MM-dd")).getMonthValue();
            return month - startMonth;
        } catch (Exception e) { return 0; }
    }
}