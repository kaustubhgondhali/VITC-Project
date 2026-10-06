package com.vitc.dto.response;

/**
 * Public, credential-free view of the active gateway so the storefront knows
 * whether to open a real provider checkout or the mock simulation.
 */
public record GatewayStatusResponse(String gateway, String mode, boolean live) {
}
