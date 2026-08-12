package com.financetracker.service;

import com.financetracker.dto.ReportDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ReportEmailService {

    private final TemplateEngine templateEngine;
    private final RestClient restClient;

    @Value("${mail.report.from}")
    private String from;

    @Value("${resend.api.key}")
    private String resendApiKey;

    public ReportEmailService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .build();
    }

    /**
     * Renders the report-email Thymeleaf template and sends it via Resend API.
     * All chart/insight logic is computed here so the template only does display.
     */
    @Async
    public void sendReportAsync(String toEmail, ReportDTO report) {
        log.info("Sending {} report email to {}", report.getReportType(), toEmail);
        try {
            Context ctx = buildContext(report);
            String html = templateEngine.process("report-email", ctx);

            Map<String, Object> body = Map.of(
                    "from", from,
                    "to", List.of(toEmail),
                    "subject", buildSubject(report),
                    "html", html
            );

            ResponseEntity<String> response = restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + resendApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode().is2xxSuccessful()) {
                log.info("Report email sent successfully to {}", toEmail);
            } else {
                log.error("Resend API returned unexpected status {} for email to {}: {}",
                        response.getStatusCode(), toEmail, response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to send report email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    // ── context builder ──────────────────────────────────────────────────────────

    private Context buildContext(ReportDTO report) {
        Context ctx = new Context();
        ctx.setVariable("report", report);

        // ── savings rate insight ─────────────────────────────────────────────────
        if (report.getTotalIncome() != null
                && report.getTotalIncome().compareTo(BigDecimal.ZERO) > 0
                && report.getNetSavings() != null) {

            BigDecimal rate = report.getNetSavings()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(report.getTotalIncome(), 1, RoundingMode.HALF_UP);
            ctx.setVariable("savingsRate", rate.toPlainString());

            String savingsInsight;
            if (rate.compareTo(BigDecimal.valueOf(20)) >= 0) {
                savingsInsight = "Great job! You saved " + rate.toPlainString()
                        + "% of your income this period. Aim to keep this above 20%.";
            } else if (rate.compareTo(BigDecimal.ZERO) >= 0) {
                savingsInsight = "You saved " + rate.toPlainString()
                        + "% of your income. Try to push this toward 20% by trimming discretionary spend.";
            } else {
                savingsInsight = "Expenses exceeded income by " + rate.abs().toPlainString()
                        + "%. Review your top spending categories below.";
            }
            ctx.setVariable("savingsInsight", savingsInsight);
        }

        // ── top category insight ─────────────────────────────────────────────────
        if (report.getTopExpenseCategories() != null && !report.getTopExpenseCategories().isEmpty()) {
            ReportDTO.CategorySummaryDTO top = report.getTopExpenseCategories().get(0);
            String topCatInsight = "Your highest spend was in " + top.getCategoryName()
                    + " \u2014 \u20b9" + fmt(top.getAmount())
                    + " (" + top.getPercentage() + "% of all expenses). "
                    + (top.getPercentage() > 40
                        ? "This category is unusually dominant. Consider setting a budget cap."
                        : "This looks balanced relative to total spending.");
            ctx.setVariable("topCatInsight", topCatInsight);
        }

        // ── account count ────────────────────────────────────────────────────────
        if (report.getBalanceByAccount() != null) {
            ctx.setVariable("accountCount", report.getBalanceByAccount().size());
        }

        // ── SVG chart data (pre-computed pixel coordinates) ──────────────────────
        if (report.getDailyTrends() != null && !report.getDailyTrends().isEmpty()) {
            ctx.setVariable("chartPoints", buildChartPoints(report.getDailyTrends()));
            ctx.setVariable("chartYMax", computeYMax(report.getDailyTrends()));
            ctx.setVariable("chartYMid", computeYMid(report.getDailyTrends()));
            int n = report.getDailyTrends().size();
            ctx.setVariable("trendFirst", report.getDailyTrends().get(0).getDate().toString());
            ctx.setVariable("trendMid",   n > 2 ? report.getDailyTrends().get(n / 2).getDate().toString() : null);
            ctx.setVariable("trendLast",  report.getDailyTrends().get(n - 1).getDate().toString());
        }

        // ── formatted totals ─────────────────────────────────────────────────────
        if (report.getTotalIncome()   != null) ctx.setVariable("fmtIncome",   fmt(report.getTotalIncome()));
        if (report.getTotalExpenses() != null) ctx.setVariable("fmtExpenses", fmt(report.getTotalExpenses()));
        if (report.getNetSavings()    != null) ctx.setVariable("fmtSavings",  fmt(report.getNetSavings()));
        if (report.getNetSavings()    != null) ctx.setVariable("savingsPositive",
                report.getNetSavings().compareTo(BigDecimal.ZERO) >= 0);

        // ── formatted category amounts ───────────────────────────────────────────
        if (report.getTopExpenseCategories() != null) {
            List<String[]> fmtCats = new ArrayList<>();
            for (ReportDTO.CategorySummaryDTO c : report.getTopExpenseCategories()) {
                // [0]=name [1]=amount [2]=txnCount [3]=percentage [4]=barWidth(capped 0-100)
                double barWidth = Math.min(100.0, Math.max(0.0, c.getPercentage()));
                fmtCats.add(new String[]{
                        c.getCategoryName(),
                        fmt(c.getAmount()),
                        String.valueOf(c.getTransactionCount()),
                        c.getPercentage() + "%",
                        String.format("%.1f", barWidth) + "%"
                });
            }
            ctx.setVariable("fmtCats", fmtCats);
        }

        // ── formatted account balances ───────────────────────────────────────────
        if (report.getBalanceByAccount() != null) {
            List<String[]> fmtAccounts = new ArrayList<>();
            for (Map.Entry<String, BigDecimal> e : report.getBalanceByAccount().entrySet()) {
                boolean positive = e.getValue() != null && e.getValue().compareTo(BigDecimal.ZERO) >= 0;
                fmtAccounts.add(new String[]{
                        e.getKey(),
                        "\u20b9" + fmt(e.getValue()),
                        positive ? "badge-income" : "badge-expense"
                });
            }
            ctx.setVariable("fmtAccounts", fmtAccounts);
        }

        return ctx;
    }

    // ── chart helpers ─────────────────────────────────────────────────────────────

    private static final int CHART_W  = 540;
    private static final int CHART_H  = 120;
    private static final int MARGIN_L = 40;
    private static final int MARGIN_T = 20;
    // bottom of plot area in px (top gridline y=MARGIN_T, bottom y=MARGIN_T+CHART_H)
    private static final int Y_BOTTOM = MARGIN_T + CHART_H;   // 140

    /** Returns a String[2]: [0]=incomePoints  [1]=expensePoints  (SVG polyline "x,y ..." format) */
    private String[] buildChartPoints(List<ReportDTO.DailyTrendDTO> trends) {
        BigDecimal safeMax = safeMax(trends);
        int n = trends.size();
        StringBuilder income  = new StringBuilder();
        StringBuilder expense = new StringBuilder();

        for (int i = 0; i < n; i++) {
            ReportDTO.DailyTrendDTO t = trends.get(i);
            // Use floating-point division so spacing is uniform across all n points
            double xFrac = (n > 1) ? (double) i / (n - 1) : 0.0;
            int x = MARGIN_L + (int) Math.round(xFrac * CHART_W);

            int yIncome  = Y_BOTTOM - scale(t.getIncome(),   safeMax);
            int yExpense = Y_BOTTOM - scale(t.getExpenses(), safeMax);

            if (i > 0) { income.append(" "); expense.append(" "); }
            income.append(x).append(",").append(yIncome);
            expense.append(x).append(",").append(yExpense);
        }
        return new String[]{ income.toString(), expense.toString() };
    }

    private String computeYMax(List<ReportDTO.DailyTrendDTO> trends) {
        return fmt(safeMax(trends));
    }

    private String computeYMid(List<ReportDTO.DailyTrendDTO> trends) {
        BigDecimal max = safeMax(trends);
        return fmt(max.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
    }

    /**
     * Returns the largest income or expense value across all trend days,
     * with a minimum of 1 to avoid division-by-zero when all values are 0.
     * Adds a 10% headroom so the tallest data point never touches the top gridline.
     */
    private BigDecimal safeMax(List<ReportDTO.DailyTrendDTO> trends) {
        BigDecimal max = BigDecimal.ZERO;
        for (ReportDTO.DailyTrendDTO t : trends) {
            if (t.getIncome()   != null && t.getIncome().compareTo(max)   > 0) max = t.getIncome();
            if (t.getExpenses() != null && t.getExpenses().compareTo(max) > 0) max = t.getExpenses();
        }
        if (max.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ONE;
        // 10% headroom
        return max.multiply(BigDecimal.valueOf(1.1)).setScale(2, RoundingMode.CEILING);
    }

    private int scale(BigDecimal value, BigDecimal max) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) return 0;
        return value.multiply(BigDecimal.valueOf(CHART_H))
                .divide(max, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private String fmt(BigDecimal v) {
        if (v == null) return "0.00";
        return String.format("%,.2f", v);
    }

    private String buildSubject(ReportDTO report) {
        if (report.getStartDate() != null && report.getEndDate() != null) {
            return String.format("Finance Report: %s (%s \u2192 %s)",
                    report.getReportType(), report.getStartDate(), report.getEndDate());
        }
        return "Finance Report: " + report.getReportType();
    }
}
