package org.jahia.services.usermanager.ldap.cache;

import org.jahia.modules.external.users.Member;

import java.io.Serializable;
import java.util.List;

/**
 * @author kevan
 */
public class LDAPGroupCacheEntry implements Serializable{
    private static final long serialVersionUID = -3585067276227107907L;

    private List<Member> members = null;
    private Boolean exist = false;
    private String name;

    public LDAPGroupCacheEntry(String name) {
        this.name = name;
    }

    public List<Member> getMembers() {
        return members;
    }

    public void setMembers(List<Member> members) {
        this.members = members;
    }

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
}
