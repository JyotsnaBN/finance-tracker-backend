package com.financetracker.service;

import com.financetracker.dto.DashboardSummaryDTO;
import com.financetracker.model.Transaction;
import com.financetracker.model.TransactionType;
import com.financetracker.repository.TransactionRepository;
import com.financetracker.dto.SpendingTrendsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {
    private final TransactionRepository transactionRepository;
    
    @Transactional(readOnly = true)
    public DashboardSummaryDTO getDashboardSummary(UUID userId) {
        log.debug("Generating dashboard summary for user: {}", userId);
        
        YearMonth currentMonth = YearMonth.now();
        Instant currentMonthStart = currentMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant currentMonthEnd = currentMonth.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();
        
        YearMonth lastMonth = currentMonth.minusMonths(1);
        Instant lastMonthStart = lastMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant lastMonthEnd = lastMonth.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();
        
        DashboardSummaryDTO.MonthSummary currentMonthSummary = calculateMonthSummary(
            userId, currentMonthStart, currentMonthEnd);
        
        DashboardSummaryDTO.MonthSummary lastMonthSummary = calculateMonthSummary(
            userId, lastMonthStart, lastMonthEnd);
        
        DashboardSummaryDTO.Comparison comparison = calculateComparison(
            currentMonthSummary, lastMonthSummary);
        
        List<DashboardSummaryDTO.CategorySummary> topCategories = getTopCategories(
            userId, currentMonthStart, currentMonthEnd);
        
        List<DashboardSummaryDTO.RecentTransaction> recentTransactions = getRecentTransactions(userId);
        
        return DashboardSummaryDTO.builder()
            .currentMonth(currentMonthSummary)
            .lastMonth(lastMonthSummary)
            .comparison(comparison)
            .topCategories(topCategories)
            .recentTransactions(recentTransactions)
            .build();
    }
    
    private DashboardSummaryDTO.MonthSummary calculateMonthSummary(
            UUID userId, Instant start, Instant end) {
        
        BigDecimal income = transactionRepository.getTotalByUserAndTypeAndDateRange(
            userId, TransactionType.CREDIT, start, end);
        if (income == null) income = BigDecimal.ZERO;
        
        BigDecimal expenses = transactionRepository.getTotalByUserAndTypeAndDateRange(
            userId, TransactionType.DEBIT, start, end);
        if (expenses == null) expenses = BigDecimal.ZERO;
        
        BigDecimal savings = income.subtract(expenses);
        BigDecimal savingsRate = income.compareTo(BigDecimal.ZERO) > 0
            ? savings.divide(income, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;
        
        return DashboardSummaryDTO.MonthSummary.builder()
            .income(income)
            .expenses(expenses)
            .savings(savings)
            .savingsRate(savingsRate.setScale(2, RoundingMode.HALF_UP))
            .build();
    }
    
    private DashboardSummaryDTO.Comparison calculateComparison(
            DashboardSummaryDTO.MonthSummary current, DashboardSummaryDTO.MonthSummary last) {
        
        BigDecimal incomeChange = calculatePercentageChange(last.getIncome(), current.getIncome());
        BigDecimal expenseChange = calculatePercentageChange(last.getExpenses(), current.getExpenses());
        BigDecimal savingsChange = calculatePercentageChange(last.getSavings(), current.getSavings());
        
        return DashboardSummaryDTO.Comparison.builder()
            .incomeChange(incomeChange)
            .expenseChange(expenseChange)
            .savingsChange(savingsChange)
            .build();
    }
    
    private BigDecimal calculatePercentageChange(BigDecimal oldValue, BigDecimal newValue) {
        if (oldValue.compareTo(BigDecimal.ZERO) == 0) {
            return newValue.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return newValue.subtract(oldValue)
            .divide(oldValue, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }
    
    private List<DashboardSummaryDTO.CategorySummary> getTopCategories(
            UUID userId, Instant start, Instant end) {
        
        List<Object[]> categoryData = transactionRepository.getCategoryWiseExpensesByUser(
            userId, start, end);
        
        BigDecimal totalExpenses = categoryData.stream()
            .map(row -> (BigDecimal) row[1])
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return categoryData.stream()
            .map(row -> {
                String categoryName = (String) row[0];
                BigDecimal amount = (BigDecimal) row[1];
                BigDecimal percentage = totalExpenses.compareTo(BigDecimal.ZERO) > 0
                    ? amount.divide(totalExpenses, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
                
                return DashboardSummaryDTO.CategorySummary.builder()
                    .categoryName(categoryName)
                    .amount(amount)
                    .percentage(percentage.setScale(2, RoundingMode.HALF_UP))
                    .build();
            })
            .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
            .limit(5)
            .collect(Collectors.toList());
    }
    
    private List<DashboardSummaryDTO.RecentTransaction> getRecentTransactions(UUID userId) {
        List<Transaction> transactions = transactionRepository.findByAccountUserId(userId);
        
        return transactions.stream()
            .sorted((t1, t2) -> t2.getTransactionDate().compareTo(t1.getTransactionDate()))
            .limit(30)
            .map(t -> DashboardSummaryDTO.RecentTransaction.builder()
                .id(t.getId())
                .description(t.getDescription())
                .amount(t.getAmount())
                .type(t.getTransactionType().name())
                .categoryName(t.getCategory().getName())
                .date(t.getTransactionDate())
                .build())
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SpendingTrendsDTO getSpendingTrends(UUID userId, String period, UUID accountId) {
        log.debug("Generating spending trends for user: {}, period: {}", userId, period);
        
        List<SpendingTrendsDTO.TrendDataPoint> dataPoints = new ArrayList<>();
        int periodsToShow = switch (period.toUpperCase()) {
            case "WEEK" -> 12;
            case "QUARTER" -> 4;
            case "YEAR" -> 5;  
            default -> 12;     
        };
        
        for (int i = periodsToShow - 1; i >= 0; i--) {
            SpendingTrendsDTO.TrendDataPoint dataPoint = calculateTrendDataPoint(
                userId, period, i, accountId);
            dataPoints.add(dataPoint);
        }
        
        SpendingTrendsDTO.Averages averages = calculateAverages(dataPoints);
        
        return SpendingTrendsDTO.builder()
            .period(period)
            .data(dataPoints)
            .averages(averages)
            .build();
    }

    private SpendingTrendsDTO.TrendDataPoint calculateTrendDataPoint(
            UUID userId, String period, int periodsAgo, UUID accountId) {
        
        Instant start, end;
        String label;
        
        switch (period.toUpperCase()) {
            case "WEEK" -> {
                LocalDate weekStart = LocalDate.now().minusWeeks(periodsAgo).with(java.time.DayOfWeek.MONDAY);
                start = weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
                end = weekStart.plusDays(6).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();
                label = "Week of " + weekStart.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd"));
            }
            case "QUARTER" -> {
                YearMonth quarterStart = YearMonth.now().minusMonths(periodsAgo * 3L);
                start = quarterStart.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                end = quarterStart.plusMonths(2).atEndOfMonth().atTime(23, 59, 59)
                    .atZone(ZoneId.systemDefault()).toInstant();
                label = "Q" + ((quarterStart.getMonthValue() - 1) / 3 + 1) + " " + quarterStart.getYear();
            }
            case "YEAR" -> {
                int year = LocalDate.now().getYear() - periodsAgo;
                start = LocalDate.of(year, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                end = LocalDate.of(year, 12, 31).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();
                label = String.valueOf(year);
            }
            default -> {
                YearMonth month = YearMonth.now().minusMonths(periodsAgo);
                start = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                end = month.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();
                label = month.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy"));
            }
        }
        
        BigDecimal income = transactionRepository.getTotalByUserAndTypeAndDateRange(
            userId, TransactionType.CREDIT, start, end);
        if (income == null) income = BigDecimal.ZERO;
        
        BigDecimal expenses = transactionRepository.getTotalByUserAndTypeAndDateRange(
            userId, TransactionType.DEBIT, start, end);
        if (expenses == null) expenses = BigDecimal.ZERO;
        
        BigDecimal savings = income.subtract(expenses);
        
        return SpendingTrendsDTO.TrendDataPoint.builder()
            .label(label)
            .income(income)
            .expenses(expenses)
            .savings(savings)
            .build();
    }

    private SpendingTrendsDTO.Averages calculateAverages(List<SpendingTrendsDTO.TrendDataPoint> dataPoints) {
        if (dataPoints.isEmpty()) {
            return SpendingTrendsDTO.Averages.builder()
                .income(BigDecimal.ZERO)
                .expenses(BigDecimal.ZERO)
                .savings(BigDecimal.ZERO)
                .build();
        }
        
        BigDecimal totalIncome = dataPoints.stream()
            .map(SpendingTrendsDTO.TrendDataPoint::getIncome)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalExpenses = dataPoints.stream()
            .map(SpendingTrendsDTO.TrendDataPoint::getExpenses)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalSavings = dataPoints.stream()
            .map(SpendingTrendsDTO.TrendDataPoint::getSavings)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        int count = dataPoints.size();
        
        return SpendingTrendsDTO.Averages.builder()
            .income(totalIncome.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP))
            .expenses(totalExpenses.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP))
            .savings(totalSavings.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP))
            .build();
    }
}
