package com.tamdaptit.rentalmanagement.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VnPayIpnResponse(@JsonProperty("RspCode") String code, @JsonProperty("Message") String message) {
}
