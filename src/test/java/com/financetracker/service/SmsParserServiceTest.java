package com.financetracker.service;

import com.financetracker.dto.ParsedSmsResultDTO;
import com.financetracker.model.TransactionType;
import com.financetracker.util.AmountExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SmsParserServiceTest {

    @Spy
    private AmountExtractor amountExtractor;

    @InjectMocks
    private SmsParserService smsParserService;

    // -------------------------------------------------------------------------
    // HDFC debit
    // -------------------------------------------------------------------------

    @Test
    void testHdfcDebit_basic() {
        String sms = "Rs 500.00 debited from A/c XX1234 on 10-Mar-26 at SWIGGY. Avl Bal: Rs 25,000.00";
        ParsedSmsResultDTO result = smsParserService.parseSms(sms);

        assertNotNull(result, "Result must not be null");
        assertEquals(new BigDecimal("500.00"), result.getAmount());
        assertEquals(TransactionType.DEBIT, result.getTransactionType());
        assertNotNull(result.getTransactionDate(), "Transaction date must be parsed");
        assertNotNull(result.getAccountHint(), "Account hint must be extracted");
        assertTrue(result.getAccountHint().contains("1234"),
                "Account hint should contain last-4 digits");
    }

    @Test
    void testHdfcDebit_upi() {
        String sms = "Your A/c XX1234 is debited with Rs.1,500.00 on 10-Mar-26. Info: UPI/PAYTM/9876543210. Avl Bal Rs.23,500.00";
        ParsedSmsResultDTO result = smsParserService.parseSms(sms);

        assertNotNull(result);
        assertEquals(new BigDecimal("1500.00"), result.getAmount());
        assertEquals(TransactionType.DEBIT, result.getTransactionType());
        assertNotNull(result.getAccountHint());
        assertTrue(result.getAccountHint().contains("1234"));
    }

    @Test
    void testHdfcCredit() {
        String sms = "Rs 50,000.00 credited to A/c XX1234 on 10-Mar-26. Info: SALARY-MAR26. Avl Bal: Rs 75,000.00";
        ParsedSmsResultDTO result = smsParserService.parseSms(sms);

        assertNotNull(result);
        assertEquals(new BigDecimal("50000.00"), result.getAmount());
        assertEquals(TransactionType.CREDIT, result.getTransactionType());
        assertNotNull(result.getAccountHint());
        assertTrue(result.getAccountHint().contains("1234"));
    }

    // -------------------------------------------------------------------------
    // ICICI
    // -------------------------------------------------------------------------

    @Test
    void testIciciDebit() {
        String sms = "Dear Customer, Rs.2,000.00 debited from A/c XX5678 on 10-Mar-26 for AMAZON transaction. Available Balance: Rs.48,000.00";
        ParsedSmsResultDTO result = smsParserService.parseSms(sms);

        assertNotNull(result);
        assertEquals(new BigDecimal("2000.00"), result.getAmount());
        assertEquals(TransactionType.DEBIT, result.getTransactionType());
        assertNotNull(result.getAccountHint());
        assertTrue(result.getAccountHint().contains("5678"));
    }

    @Test
    void testIciciCredit() {
        String sms = "Dear Customer, Rs.25,000.00 credited to A/c XX5678 on 10-Mar-26. Info: NEFT-SALARY. Available Balance: Rs.73,000.00";
        ParsedSmsResultDTO result = smsParserService.parseSms(sms);

        assertNotNull(result);
        assertEquals(new BigDecimal("25000.00"), result.getAmount());
        assertEquals(TransactionType.CREDIT, result.getTransactionType());
    }

    // -------------------------------------------------------------------------
    // SBI
    // -------------------------------------------------------------------------

    @Test
    void testSbiDebit() {
        String sms = "SBI: Rs 1200 debited from A/c XX9012 on 10-Mar-26 to VPA merchant@paytm (UPI Ref No 123456789). If not done by you, call 1800111109";
        ParsedSmsResultDTO result = smsParserService.parseSms(sms);

        assertNotNull(result);
        assertEquals(new BigDecimal("1200"), result.getAmount());
        assertEquals(TransactionType.DEBIT, result.getTransactionType());
        assertNotNull(result.getAccountHint());
        assertTrue(result.getAccountHint().contains("9012"));
    }

    @Test
    void testSbiCredit() {
        String sms = "SBI: Rs 10000 credited to A/c XX9012 on 10-Mar-26 by UPI (UPI Ref No 123456789)";
        ParsedSmsResultDTO result = smsParserService.parseSms(sms);

        assertNotNull(result);
        assertEquals(new BigDecimal("10000"), result.getAmount());
        assertEquals(TransactionType.CREDIT, result.getTransactionType());
    }

    // -------------------------------------------------------------------------
    // Credit card
    // -------------------------------------------------------------------------

    @Test
    void testHdfcCreditCard_withAvailableLimit() {
        String sms = "Your HDFC Bank Credit Card XX7890 has been used for Rs.3,500.00 at AMAZON on 10-Mar-26. Avl Limit: Rs.46,500.00";
        ParsedSmsResultDTO result = smsParserService.parseSms(sms);

        assertNotNull(result);
        assertEquals(new BigDecimal("3500.00"), result.getAmount());
        assertEquals(TransactionType.DEBIT, result.getTransactionType());
        assertNotNull(result.getAccountHint());
        assertTrue(result.getAccountHint().contains("7890"));
        // Available limit should be extracted
        assertNotNull(result.getAvailableLimit(), "Available limit should be parsed for credit card SMS");
    }

    @Test
    void testIciciCreditCard() {
        String sms = "ICICI Bank Credit Card XX4567 used for Rs 2,000 on 10-Mar-26 at FLIPKART. Available credit limit Rs 48,000";
        ParsedSmsResultDTO result = smsParserService.parseSms(sms);

        assertNotNull(result);
        assertEquals(new BigDecimal("2000"), result.getAmount());
        assertEquals(TransactionType.DEBIT, result.getTransactionType());
        assertNotNull(result.getAccountHint());
        assertTrue(result.getAccountHint().contains("4567"));
    }

    // -------------------------------------------------------------------------
    // UPI
    // -------------------------------------------------------------------------

    @Test
    void testPhonePeUpi() {
        String sms = "You paid Rs 250 to UBER via PhonePe on 10-Mar-26. UPI Ref: 123456789";
        ParsedSmsResultDTO result = smsParserService.parseSms(sms);

        assertNotNull(result);
        assertEquals(new BigDecimal("250"), result.getAmount());
        assertEquals(TransactionType.DEBIT, result.getTransactionType());
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    void testUnparseable_returnsSomethingOrRequiresReview() {
        // Garbage input should not throw — parser must handle gracefully
        String sms = "Hello from your bank!";
        ParsedSmsResultDTO result = smsParserService.parseSms(sms);
        // Either null or requires manual review — must not throw
        if (result != null && result.getAmount() != null) {
            assertTrue(result.isRequiresManualReview(),
                    "Ambiguous SMS should require manual review");
        }
    }
}
