@Data @Builder
public class TransactionDTO {
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    @NotNull(message = "Transaction type is required")
    private TransactionType transactionType;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
}