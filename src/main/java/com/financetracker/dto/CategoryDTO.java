package com.financetracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {
    private Long id;

    @NotBlank(message = "Category name is required")
    @Size(min = 1, max = 50, message = "Category name must be between 1 and 50 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @Size(max = 50, message = "Icon cannot exceed 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]*$",
             message = "Icon identifier may only contain letters, digits, underscores and hyphens")
    private String icon;

    /** Must be a valid CSS hex colour (#RGB or #RRGGBB). */
    @Pattern(regexp = "^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$",
             message = "Color must be a valid hex value (#RGB or #RRGGBB)")
    private String color;

    private Instant createdAt;
    private Instant updatedAt;
}
