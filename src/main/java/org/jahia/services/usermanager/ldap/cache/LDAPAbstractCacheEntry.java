package org.jahia.services.usermanager.ldap.cache;

import java.io.Serializable;
import java.util.List;

/**
 * @author kevan
 */
public abstract class LDAPAbstractCacheEntry implements Serializable{
    private static final long serialVersionUID = -6551768615414069547L;

    private Boolean exist = false;
    private String name;
    private String dn;
    private List<String> memberships;

    public Boolean getExist() {
        return exist;
    }

    public void setExist(Boolean exist) {
        this.exist = exist;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getMemberships() {
        return memberships;
    }

    public void setMemberships(List<String> memberships) {
        this.memberships = memberships;
    }

    public String getDn() {
        return dn;
    }

    public void setDn(String dn) {
        this.dn = dn;
    }
}
