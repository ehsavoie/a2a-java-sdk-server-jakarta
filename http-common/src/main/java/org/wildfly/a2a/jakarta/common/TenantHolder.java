package org.wildfly.a2a.jakarta.common;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class TenantHolder {
    private String tenant = "";

    public String getTenant() { return tenant; }
    public void setTenant(String tenant) { this.tenant = tenant; }
}
