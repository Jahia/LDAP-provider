package org.jahia.services.usermanager.ldap.cache;

import org.jahia.services.usermanager.JahiaUser;

import java.io.Serializable;

/**
 * @author kevan
 */
public class LDAPUserCacheEntry extends LDAPAbstractCacheEntry implements Serializable{
    private static final long serialVersionUID = -1432235243384204528L;
    private JahiaUser user;

    public LDAPUserCacheEntry(String name) {
        setName(name);
    }

    public JahiaUser getUser() {
        return user;
    }

    public void setUser(JahiaUser user) {
        this.user = user;
    }
}
