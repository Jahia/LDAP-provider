/*
 * Copyright (C) 2002-2022 Jahia Solutions Group SA. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
