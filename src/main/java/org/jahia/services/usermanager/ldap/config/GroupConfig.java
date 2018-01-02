/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2018 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.services.usermanager.ldap.config;

import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.springframework.ldap.support.LdapEncoder;
import org.springframework.ldap.support.LdapUtils;

import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;

/**
 * Group specific config provide by the ldap config file
 * @author kevan
 */
public class GroupConfig extends AbstractConfig{
    private boolean preload = false;
    private boolean dynamicEnabled = false;
    private String refferal = "ignore";
    private int adRangeStep = 0;
    private String searchName;
    private String searchAttribute = "cn";
    private String membersAttribute = "uniqueMember";
    private String dynamicSearchObjectclass = "groupOfURLs";
    private String dynamicMembersAttribute = "memberurl";

    public GroupConfig() {
    }

    public void handleDefaults() {
        if(StringUtils.isEmpty(getSearchObjectclass())){
            setSearchObjectclass("groupOfUniqueNames");
        }
        if(getSearchWildcardsAttributes().isEmpty()){
            setSearchWildcardsAttributes(Sets.newHashSet("cn", "description", "uniqueMember"));
        }
        if(getAttributesMapper().isEmpty()){
            getAttributesMapper().put("groupname", searchAttribute);
            getAttributesMapper().put("description", "description");
        }
    }

    public boolean isPreload() {
        return preload;
    }

    public void setPreload(boolean preload) {
        this.preload = preload;
    }

    public String getRefferal() {
        return refferal;
    }

    public void setRefferal(String refferal) {
        this.refferal = refferal;
    }

    public int getAdRangeStep() {
        return adRangeStep;
    }

    public void setAdRangeStep(int adRangeStep) {
        this.adRangeStep = adRangeStep;
    }

    public String getSearchName() {
        return searchName;
    }

    public void setSearchName(String searchName) {
        this.searchName = searchName;
    }

    public String getSearchAttribute() {
        return searchAttribute;
    }

    public void setSearchAttribute(String searchAttribute) {
        this.searchAttribute = searchAttribute;
    }

    public String getMembersAttribute() {
        return membersAttribute;
    }

    public void setMembersAttribute(String membersAttribute) {
        this.membersAttribute = membersAttribute;
    }

    public String getDynamicSearchObjectclass() {
        return dynamicSearchObjectclass;
    }

    public void setDynamicSearchObjectclass(String dynamicSearchObjectclass) {
        this.dynamicSearchObjectclass = dynamicSearchObjectclass;
    }

    public String getDynamicMembersAttribute() {
        return dynamicMembersAttribute;
    }

    public void setDynamicMembersAttribute(String dynamicMembersAttribute) {
        this.dynamicMembersAttribute = dynamicMembersAttribute;
    }

    public boolean isDynamicEnabled() {
        return dynamicEnabled;
    }

    public void setDynamicEnabled(boolean dynamicEnabled) {
        this.dynamicEnabled = dynamicEnabled;
    }

    public boolean isMinimalSettingsOk() {
        return StringUtils.isNotEmpty(getUrl()) && StringUtils.isNotEmpty(getSearchName());
    }

}
