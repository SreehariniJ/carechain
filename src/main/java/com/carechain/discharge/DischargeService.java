package com.carechain.discharge;

import com.carechain.appointment.DoctorRepository;
import com.carechain.audit.AuditEvent;
import com.carechain.audit.AuditMetadata;
import com.carechain.audit.AuditTrailService;
import com.carechain.auth.UserRepository;
import com.carechain.bed.AdmissionRepository;
import com.carechain.bed.model.Admission;
import com.carechain.config.ApiErrorException;
import com.carechain.metrics.CareChainMetricsService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DischargeService {

    private final AdmissionRepository admissionRepository;
    private final DischargeSummaryRepository dischargeSummaryRepository;
    private final BillingProperties billingProperties;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final AuditTrailService auditTrailService;
    private final CareChainMetricsService metricsService;

    public DischargeService(AdmissionRepository admissionRepository,
                            DischargeSummaryRepository dischargeSummaryRepository,
                            BillingProperties billingProperties,
                            DoctorRepository doctorRepository,
                            UserRepository userRepository,
                            AuditTrailService auditTrailService,
                            CareChainMetricsService metricsService) {
        this.admissionRepository = admissionRepository;
        this.dischargeSummaryRepository = dischargeSummaryRepository;
        this.billingProperties = billingProperties;
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.auditTrailService = auditTrailService;
        this.metricsService = metricsService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN')")
    public List<Map<String, Object>> getRecentDischarges(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 25));
        List<Admission> admissions = admissionRepository.findRecentDischargedAdmissions(PageRequest.of(0, safeLimit));
        Map<Long, DischargeSummary> summariesByAdmissionId = dischargeSummaryRepository.findByAdmissionIdIn(
                        admissions.stream().map(Admission::getId).toList())
                .stream()
                .collect(Collectors.toMap(summary -> summary.getAdmission().getId(), Function.identity()));

        return admissions.stream().map(admission -> {
            BillingPreview billingPreview = buildBillingPreview(admission);
            return Map.<String, Object>of(
                    "admissionId", admission.getId(),
                    "patientName", admission.getPatient().getName() == null ? "N/A" : admission.getPatient().getName(),
                    "wardName", admission.getBed().getWard().getName(),
                    "wardType", admission.getBed().getWard().getType().name(),
                    "admittedAt", admission.getAdmittedAt().toString(),
                    "dischargedAt", admission.getDischargedAt().toString(),
                    "summaryReady", summariesByAdmissionId.containsKey(admission.getId()),
                    "totalAmount", billingPreview.totalAmount()
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN')")
    public Map<String, Object> getDischargeOverview(Long admissionId) {
        Admission admission = getDetailedDischargedAdmission(admissionId);
        BillingPreview billingPreview = buildBillingPreview(admission);
        DischargeSummary summary = dischargeSummaryRepository.findByAdmissionId(admissionId).orElse(null);

        return Map.of(
                "admissionId", admission.getId(),
                "patientName", admission.getPatient().getName() == null ? "N/A" : admission.getPatient().getName(),
                "patientEmail", admission.getPatient().getUser().getEmail(),
                "wardName", admission.getBed().getWard().getName(),
                "wardType", admission.getBed().getWard().getType().name(),
                "bedNumber", admission.getBed().getBedNumber(),
                "admittedAt", admission.getAdmittedAt().toString(),
                "dischargedAt", admission.getDischargedAt().toString(),
                "summary", summary == null ? Map.of() : Map.of(
                        "diagnosis", summary.getDiagnosis(),
                        "treatmentSummary", defaultText(summary.getTreatmentSummary()),
                        "dischargeInstructions", defaultText(summary.getDischargeInstructions()),
                        "medications", defaultText(summary.getMedications()),
                        "followUpDate", summary.getFollowUpDate() == null ? "" : summary.getFollowUpDate().toString(),
                        "attendingDoctorName", defaultText(summary.getAttendingDoctorName()),
                        "updatedAt", summary.getUpdatedAt() == null ? "" : summary.getUpdatedAt().toString()
                ),
                "billing", Map.of(
                        "stayDays", billingPreview.stayDays(),
                        "currencyCode", billingPreview.currencyCode(),
                        "dailyRate", billingPreview.dailyRate(),
                        "lineItems", billingPreview.lineItems().stream().map(item -> Map.of(
                                "label", item.label(),
                                "amount", item.amount()
                        )).toList(),
                        "totalAmount", billingPreview.totalAmount()
                )
        );
    }

    @Transactional
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN')")
    public DischargeSummary saveSummary(Long admissionId, DischargeSummaryRequest request, String actorEmail) {
        Admission admission = getDetailedDischargedAdmission(admissionId);
        DischargeSummary summary = dischargeSummaryRepository.findByAdmissionId(admissionId)
                .orElseGet(() -> DischargeSummary.builder().admission(admission).build());

        summary.setDiagnosis(request.getDiagnosis().trim());
        summary.setTreatmentSummary(normalizeNullable(request.getTreatmentSummary()));
        summary.setDischargeInstructions(normalizeNullable(request.getDischargeInstructions()));
        summary.setMedications(normalizeNullable(request.getMedications()));
        summary.setFollowUpDate(request.getFollowUpDate());
        summary.setAttendingDoctorName(resolveDoctorName(actorEmail));

        DischargeSummary savedSummary = dischargeSummaryRepository.save(summary);
        metricsService.recordDischargeSummarySaved(admission.getBed().getWard().getType().name());
        auditTrailService.record(AuditEvent.builder()
                .action("DISCHARGE_SUMMARY_SAVED")
                .resourceType("DISCHARGE")
                .resourceId(AuditMetadata.id(admissionId))
                .details(AuditMetadata.map(
                        "patientId", admission.getPatient().getId(),
                        "bedId", admission.getBed().getId(),
                        "wardType", admission.getBed().getWard().getType().name(),
                        "followUpDate", request.getFollowUpDate() == null ? null : request.getFollowUpDate().toString()))
                .build());
        return savedSummary;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN')")
    public BillingPreview getBillingPreview(Long admissionId) {
        return buildBillingPreview(getDetailedDischargedAdmission(admissionId));
    }

    @Transactional
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN')")
    public byte[] generateDischargePdf(Long admissionId, String actorEmail) {
        DischargeSummary summary = dischargeSummaryRepository.findDetailedByAdmissionId(admissionId)
                .orElseThrow(() -> ApiErrorException.conflict("Create a discharge summary before generating the PDF"));

        Admission admission = summary.getAdmission();
        BillingPreview billingPreview = buildBillingPreview(admission);

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 40, 40, 48, 48);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);

            Paragraph title = new Paragraph("CareChain Discharge Summary & Billing", titleFont);
            title.setSpacingAfter(8f);
            document.add(title);
            document.add(new Paragraph(
                    "Generated for " + admission.getPatient().getName() + " on " + LocalDate.now(),
                    smallFont));
            document.add(Chunk.NEWLINE);

            PdfPTable patientTable = new PdfPTable(new float[]{2f, 3.4f});
            patientTable.setWidthPercentage(100);
            addDetailRow(patientTable, "Patient", admission.getPatient().getName(), bodyFont);
            addDetailRow(patientTable, "Email", admission.getPatient().getUser().getEmail(), bodyFont);
            addDetailRow(patientTable, "Ward / Bed", admission.getBed().getWard().getName() + " / " + admission.getBed().getBedNumber(), bodyFont);
            addDetailRow(patientTable, "Admission Window", admission.getAdmittedAt().toLocalDate() + " to " + admission.getDischargedAt().toLocalDate(), bodyFont);
            addDetailRow(patientTable, "Attending Doctor", defaultText(summary.getAttendingDoctorName()), bodyFont);
            if (summary.getFollowUpDate() != null) {
                addDetailRow(patientTable, "Follow-up", summary.getFollowUpDate().toString(), bodyFont);
            }
            document.add(patientTable);
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Clinical Summary", sectionFont));
            document.add(new Paragraph("Diagnosis: " + summary.getDiagnosis(), bodyFont));
            document.add(new Paragraph("Treatment: " + defaultText(summary.getTreatmentSummary()), bodyFont));
            document.add(new Paragraph("Discharge Instructions: " + defaultText(summary.getDischargeInstructions()), bodyFont));
            document.add(new Paragraph("Medications: " + defaultText(summary.getMedications()), bodyFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Billing Summary", sectionFont));
            PdfPTable billingTable = new PdfPTable(new float[]{4f, 1.5f});
            billingTable.setWidthPercentage(100);
            addHeaderCell(billingTable, "Charge Item");
            addHeaderCell(billingTable, "Amount (" + billingPreview.currencyCode() + ")");
            for (BillingLineItem item : billingPreview.lineItems()) {
                addBodyCell(billingTable, item.label(), bodyFont);
                addBodyCell(billingTable, item.amount().setScale(2, RoundingMode.HALF_UP).toPlainString(), bodyFont);
            }
            addHeaderCell(billingTable, "Total");
            addHeaderCell(billingTable, billingPreview.totalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
            document.add(billingTable);

            document.close();
            metricsService.recordDischargePdfGenerated(admission.getBed().getWard().getType().name());
            auditTrailService.record(AuditEvent.builder()
                    .action("DISCHARGE_PDF_GENERATED")
                    .resourceType("DISCHARGE")
                    .resourceId(AuditMetadata.id(admissionId))
                    .details(AuditMetadata.map(
                            "patientId", admission.getPatient().getId(),
                            "generatedByEmail", actorEmail.toLowerCase(Locale.ROOT),
                            "totalAmount", billingPreview.totalAmount(),
                            "currencyCode", billingPreview.currencyCode()))
                    .build());
            return outputStream.toByteArray();
        } catch (DocumentException exception) {
            throw new IllegalStateException("Unable to generate discharge PDF", exception);
        }
    }

    private Admission getDetailedDischargedAdmission(Long admissionId) {
        Admission admission = admissionRepository.findDetailedById(admissionId)
                .orElseThrow(() -> ApiErrorException.notFound("Admission not found"));
        if (admission.getDischargedAt() == null) {
            throw ApiErrorException.conflict("Patient must be discharged before this workflow can continue");
        }
        return admission;
    }

    private BillingPreview buildBillingPreview(Admission admission) {
        long stayDays = Math.max(1, ChronoUnit.DAYS.between(
                admission.getAdmittedAt().toLocalDate(),
                admission.getDischargedAt().toLocalDate()) + 1);
        BigDecimal dailyRate = billingProperties.resolveDailyRate(admission.getBed().getWard().getType());
        List<BillingLineItem> lineItems = new ArrayList<>();
        lineItems.add(new BillingLineItem(
                admission.getBed().getWard().getType().name() + " bed charges (" + stayDays + " day" + (stayDays == 1 ? "" : "s") + ")",
                dailyRate.multiply(BigDecimal.valueOf(stayDays))));
        lineItems.add(new BillingLineItem("Consultation fee", billingProperties.getConsultationFee()));
        lineItems.add(new BillingLineItem("Discharge processing", billingProperties.getDischargeProcessingFee()));

        BigDecimal totalAmount = lineItems.stream()
                .map(BillingLineItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new BillingPreview(
                admission.getId(),
                stayDays,
                billingProperties.getCurrencyCode(),
                dailyRate,
                lineItems,
                totalAmount
        );
    }

    private String resolveDoctorName(String actorEmail) {
        return userRepository.findByEmail(actorEmail)
                .flatMap(user -> doctorRepository.findByUserId(user.getId()))
                .map(doctor -> doctor.getName() == null || doctor.getName().isBlank() ? actorEmail : doctor.getName())
                .orElse(actorEmail);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s{2,}", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private String defaultText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void addDetailRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(6f);
        labelCell.setBackgroundColor(new Color(245, 245, 245));
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(defaultText(value), font));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(6f);
        table.addCell(valueCell);
    }

    private void addHeaderCell(PdfPTable table, String value) {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        PdfPCell cell = new PdfPCell(new Phrase(value, headerFont));
        cell.setPadding(7f);
        cell.setBackgroundColor(new Color(235, 240, 246));
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setPadding(7f);
        table.addCell(cell);
    }
}
