package org.jahia.services.usermanager.ldap.cache;

import org.jahia.services.usermanager.JahiaUser;

import java.io.Serializable;

/**
 * @author kevan
 */
public class LDAPUserCacheEntry implements Serializable{
    private static final long serialVersionUID = -1432235243384204528L;

    private Boolean exist = false;
    private String name;
    private JahiaUser user;

    public LDAPUserCacheEntry(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getExist() {
        return exist;
    }

    public void setExist(Boolean exist) {
        this.exist = exist;
    }

    public JahiaUser getUser() {
        return user;
    }

    public void setUser(JahiaUser user) {
        this.user = user;
    }
}
