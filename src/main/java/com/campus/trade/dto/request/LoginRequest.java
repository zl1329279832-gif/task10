package com.campus.trade.dto.request;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class LoginRequest { @NotBlank(message="Username required") private String username; @NotBlank(message="Password required") private String password; }
