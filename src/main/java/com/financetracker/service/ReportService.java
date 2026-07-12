package com.financetracker.service;

import com.financetracker.dto.ReportDTO;
import com.financetracker.model.Account;
import com.financetracker.model.Transaction;
import com.financetracker.model.TransactionType;
import com.financetracker.repository.AccountRepository;
import com.financetracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public ReportDTO getSummaryReport(UUID userId, LocalDate startDate, LocalDate endDate) {
        log.debug("Generating summary report for user: {} from {} to {}", userId, startDate, endDate);

        Instant start = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = endDate.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC);

        BigDecimal totalIncome = defaultIfNull(
                transactionRepository.getTotalByUserAndTypeAndDateRange(userId, TransactionType.CREDIT, start, end)
        );

        BigDecimal totalExpenses = defaultIfNull(
                transactionRepository.getTotalByUserAndTypeAndDateRange(userId, TransactionType.DEBIT, start, end)
        );

        return ReportDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .reportType("SUMMARY")
                .totalIncome(totalIncome)
                .totalExpenses(totalExpenses)
                .netSavings(totalIncome.subtract(totalExpenses))
                .expensesByCategory(getExpenseCategoryMap(userId, start, end))
                .incomeByCategory(Collections.emptyMap())
                .dailyTrends(getDailyTrends(userId, startDate, endDate))
                .topExpenseCategories(getTopExpenseCategories(userId, startDate, endDate))
                .balanceByAccount(getBalanceByAccount(userId))
                .build();
    }

    @Transactional(readOnly = true)
    public ReportDTO getIncomeExpenseReport(UUID userId, LocalDate startDate, LocalDate endDate) {
        log.debug("Generating income expense report for user: {} from {} to {}", userId, startDate, endDate);

        Instant start = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = endDate.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC);

        BigDecimal totalIncome = defaultIfNull(
                transactionRepository.getTotalByUserAndTypeAndDateRange(userId, TransactionType.CREDIT, start, end)
        );

        BigDecimal totalExpenses = defaultIfNull(
                transactionRepository.getTotalByUserAndTypeAndDateRange(userId, TransactionType.DEBIT, start, end)
        );

        return ReportDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .reportType("INCOME_EXPENSE")
                .totalIncome(totalIncome)
                .totalExpenses(totalExpenses)
                .netSavings(totalIncome.subtract(totalExpenses))
                .dailyTrends(getDailyTrends(userId, startDate, endDate))
                .build();
    }

    @Transactional(readOnly = true)
    public ReportDTO getCategoryBreakdownReport(UUID userId, LocalDate startDate, LocalDate endDate) {
        log.debug("Generating category breakdown report for user: {} from {} to {}", userId, startDate, endDate);

        Instant start = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = endDate.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC);

        BigDecimal totalIncome = defaultIfNull(
                transactionRepository.getTotalByUserAndTypeAndDateRange(userId, TransactionType.CREDIT, start, end)
        );

        BigDecimal totalExpenses = defaultIfNull(
                transactionRepository.getTotalByUserAndTypeAndDateRange(userId, TransactionType.DEBIT, start, end)
        );

        return ReportDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .reportType("CATEGORY_BREAKDOWN")
                .totalIncome(totalIncome)
                .totalExpenses(totalExpenses)
                .netSavings(totalIncome.subtract(totalExpenses))
                .expensesByCategory(getExpenseCategoryMap(userId, start, end))
                .topExpenseCategories(getTopExpenseCategories(userId, startDate, endDate))
                .build();
    }

    @Transactional(readOnly = true)
    public ReportDTO getAccountBalanceReport(UUID userId) {
        log.debug("Generating account balance report for user: {}", userId);

        return ReportDTO.builder()
                .reportType("ACCOUNT_BALANCES")
                .balanceByAccount(getBalanceByAccount(userId))
                .build();
    }

    private Map<String, BigDecimal> getExpenseCategoryMap(UUID userId, Instant start, Instant end) {
        List<Object[]> results = transactionRepository.getCategoryWiseExpensesByUser(userId, start, end);

        Map<String, BigDecimal> categoryMap = new LinkedHashMap<>();
        for (Object[] row : results) {
            String categoryName = (String) row[0];
            BigDecimal amount = defaultIfNull((BigDecimal) row[1]);
            categoryMap.put(categoryName, amount);
        }
        return categoryMap;
    }

    private List<ReportDTO.DailyTrendDTO> getDailyTrends(UUID userId, LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = transactionRepository.findByAccountUserId(userId);

        Map<LocalDate, List<Transaction>> groupedByDate = transactions.stream()
                .filter(t -> {
                    LocalDate txnDate = t.getTransactionDate().atZone(ZoneOffset.UTC).toLocalDate();
                    return !txnDate.isBefore(startDate) && !txnDate.isAfter(endDate);
                })
                .collect(Collectors.groupingBy(
                        t -> t.getTransactionDate().atZone(ZoneOffset.UTC).toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        List<ReportDTO.DailyTrendDTO> trends = new ArrayList<>();

        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            List<Transaction> dayTransactions = groupedByDate.getOrDefault(current, Collections.emptyList());

            BigDecimal income = dayTransactions.stream()
                    .filter(t -> t.getTransactionType() == TransactionType.CREDIT)
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal expenses = dayTransactions.stream()
                    .filter(t -> t.getTransactionType() == TransactionType.DEBIT)
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            trends.add(ReportDTO.DailyTrendDTO.builder()
                    .date(current)
                    .income(income)
                    .expenses(expenses)
                    .net(income.subtract(expenses))
                    .build());

            current = current.plusDays(1);
        }

        return trends;
    }

    private List<ReportDTO.CategorySummaryDTO> getTopExpenseCategories(UUID userId, LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = transactionRepository.findByAccountUserId(userId);

        List<Transaction> expenseTransactions = transactions.stream()
                .filter(t -> t.getTransactionType() == TransactionType.DEBIT)
                .filter(t -> {
                    LocalDate txnDate = t.getTransactionDate().atZone(ZoneOffset.UTC).toLocalDate();
                    return !txnDate.isBefore(startDate) && !txnDate.isAfter(endDate);
                })
                .toList();

        BigDecimal totalExpenses = expenseTransactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, List<Transaction>> groupedByCategory = expenseTransactions.stream()
                .collect(Collectors.groupingBy(t -> t.getCategory().getName()));

        return groupedByCategory.entrySet().stream()
                .map(entry -> {
                    String categoryName = entry.getKey();
                    List<Transaction> categoryTransactions = entry.getValue();

                    BigDecimal amount = categoryTransactions.stream()
                            .map(Transaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    double percentage = BigDecimal.ZERO.compareTo(totalExpenses) == 0
                            ? 0.0
                            : amount.multiply(BigDecimal.valueOf(100))
                                    .divide(totalExpenses, 2, RoundingMode.HALF_UP)
                                    .doubleValue();

                    return ReportDTO.CategorySummaryDTO.builder()
                            .categoryName(categoryName)
                            .amount(amount)
                            .transactionCount(categoryTransactions.size())
                            .percentage(percentage)
                            .build();
                })
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
                .limit(5)
                .toList();
    }

    private Map<String, BigDecimal> getBalanceByAccount(UUID userId) {
        List<Account> accounts = accountRepository.findByUserIdAndIsActiveOrderByAccountNameAsc(userId, true);

        Map<String, BigDecimal> balances = new LinkedHashMap<>();
        for (Account account : accounts) {
            balances.put(
                    account.getAccountName(),
                    defaultIfNull(account.getCurrentBalance())
            );
        }
        return balances;
    }

    private BigDecimal defaultIfNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
