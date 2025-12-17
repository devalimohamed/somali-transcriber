package com.somtranscriber.calls.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDraftRequest(
        @NotBlank @Size(max = 5000) String noteText
) {
}
