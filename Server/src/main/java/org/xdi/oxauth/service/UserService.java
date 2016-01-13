/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.gluu.site.ldap.persistence.LdapEntryManager;
import org.jboss.seam.Component;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.AutoCreate;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Logger;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.log.Log;
import org.xdi.ldap.model.CustomAttribute;
import org.xdi.ldap.model.GluuStatus;
import org.xdi.oxauth.model.common.User;
import org.xdi.oxauth.model.config.ConfigurationFactory;
import org.xdi.oxauth.model.token.PersistentJwt;
import org.xdi.util.StringHelper;

import com.unboundid.ldap.sdk.Filter;

/**
 * Provides operations with users.
 *
 * @author Javier Rojas Blum Date: 11.30.2011
 */
@Scope(ScopeType.STATELESS)
@Name("userService")
@AutoCreate
public class UserService {

    @Logger
    private Log log;

    @In
    private LdapEntryManager ldapEntryManager;

    @In
    private AuthenticationService authenticationService;

    @In
    private InumService inumService;

    /**
     * Authenticate user
     *
     * @param userName The username
     * @return <code>true</code> if success, otherwise <code>false</code>
     */
    public boolean authenticate(String userName) {
        return authenticationService.authenticate(userName);
    }

    /**
     * Authenticate user
     *
     * @param userName The username
     * @param password The user's pasword
     * @return <code>true</code> if success, otherwise <code>false</code>
     */
    public boolean authenticate(String userName, String password) {
        return authenticationService.authenticate(userName, password);
    }

    /**
     * Authenticate user
     *
     * @param keyValue The value of authentication key
     * @param password The user's password
     * @param primaryKey Authentication key attribute name
     * @param localPrimaryKey Local authentication key attribute name
     * @return <code>true</code> if success, otherwise <code>false</code>
     */
    public boolean authenticate(String keyValue, String password, String primaryKey, String localPrimaryKey) {
        return authenticationService.authenticate(keyValue, password, primaryKey, localPrimaryKey);
    }

    /**
     * returns User by Dn
     *
     * @return User
     */
    public User getUserByDn(String dn) {
        return ldapEntryManager.find(User.class, dn);
    }

    /**
     * Lookup a user in LDAP using the default directory name (base DN).
     *
     * @param userId The user's identifier.
     * @param returnAttributes LDAP attributes to select.
     * @return Instance of <code>User</code>.
     */
	public User getUser(String userId, String... returnAttributes) {
		String directoryName = ConfigurationFactory.instance().getBaseDn().getPeople();
		
		return this.getUserInDirectory(userId, directoryName, returnAttributes);
	}

    /**
     * Lookup a user in LDAP under an alternate organization.
     *
     * @param userId The user's identifier.
     * @param orgId The identifier for the organization under which to search.
     * @param returnAttributes LDAP attributes to select.
     * @return Instance of <code>User</code>.
     */
	public User getUserForOrg(String userId, String orgId, String... returnAttributes) {
		log.debug("Getting user information from LDAP: userId = {0} under orgId {1}", userId, orgId);

		if (StringHelper.isEmpty(userId) ||
			StringHelper.isEmpty(orgId)) {
			
			return null;
		}

		// Replace the organization in the default directory name with the orgId
		// For example, need to replace	o=@!16AF.2902.8608.F8D1!0001!0E98.1F6C
		String originalDn = ConfigurationFactory.instance().getBaseDn().getPeople();
		String pattern = "o=@![0-9A-Z\\.!]{34}";
		String directoryName = originalDn.replaceAll(pattern, "o=" + orgId);
		log.debug("Search in directory name: " + directoryName);

		return getUserInDirectory(userId, directoryName, returnAttributes);
	}


	private User getUserInDirectory(String userId, String directoryName, String... returnAttributes) {
		log.debug("Getting user information from LDAP: userId = {0} under directory name {1}", userId, directoryName);

		if (StringHelper.isEmpty(userId) ||
			StringHelper.isEmpty(directoryName)) {
			
			return null;
		}

		Filter userUidFilter = Filter.createEqualityFilter("uid", userId);

		List<User> entries = ldapEntryManager.findEntries(directoryName, User.class, returnAttributes, userUidFilter);
		log.debug("Found {0} entries for user id = {1}", entries.size(), userId);

		if (entries.size() > 0) {
			return entries.get(0);
		} else {
			return null;
		}
	}
	

	public String getUserInum(User user) {
		if (user == null) {
			return null;
		}
		
		String inum = user.getAttribute("inum");

		return inum;
	}

	public String getUserInum(String userId) {
		User user = getUser(userId, "inum");

		return getUserInum(user);
	}

    public User updateUser(User user) {
		return ldapEntryManager.merge(user);
	}

    public User addDefaultUser(String uid) {
        String peopleBaseDN = ConfigurationFactory.instance().getBaseDn().getPeople();

        String inum = inumService.generatePeopleInum();

    	User user = new User();
        user.setDn("inum=" + inum + "," + peopleBaseDN);
    	user.setCustomAttributes(Arrays.asList(
    			new CustomAttribute("inum", inum),
    			new CustomAttribute("gluuStatus", GluuStatus.ACTIVE.getValue()),
				new CustomAttribute("displayName", "User " + uid + " added via oxAuth custom plugin")));
    	user.setUserId(uid); 
    	
		ldapEntryManager.persist(user);
		
		return getUser(uid);
	}
    
    public User addUser(User user, boolean active) {
        String peopleBaseDN = ConfigurationFactory.instance().getBaseDn().getPeople();

        String inum = inumService.generatePeopleInum();

        user.setDn("inum=" + inum + "," + peopleBaseDN);
        user.setAttribute("inum", inum);
        
        GluuStatus status = active ? GluuStatus.ACTIVE : GluuStatus.REGISTER;
        user.setAttribute("gluuStatus",  status.getValue());
		ldapEntryManager.persist(user);
		
		return getUserByDn(user.getDn());
	}


    public User getUserByAttribute(String attributeName, String attributeValue) {
        log.debug("Getting user information from LDAP: attributeName = '{0}', attributeValue = '{1}'", attributeName, attributeValue);

        User user = new User();
        user.setDn(ConfigurationFactory.instance().getBaseDn().getPeople());
        
        List<CustomAttribute> customAttributes =  new ArrayList<CustomAttribute>();
        customAttributes.add(new CustomAttribute(attributeName, attributeValue));

        user.setCustomAttributes(customAttributes);

        List<User> entries = ldapEntryManager.findEntries(user);
        log.debug("Found '{0}' entries", entries.size());

        if (entries.size() > 0) {
            return entries.get(0);
        } else {
            return null;
        }
    }

    public User addUserAttribute(String userId, String attributeName, String attributeValue) {
        log.debug("Getting user information from LDAP: attributeName = '{0}', attributeValue = '{1}'", attributeName, attributeValue);

        User user = getUser(userId);
        if (user == null) {
        	return null;
        }
        
        CustomAttribute customAttribute = getCustomAttribute(user, attributeName);
        if (customAttribute == null) {
        	customAttribute = new CustomAttribute(attributeName, attributeValue);
            user.getCustomAttributes().add(customAttribute);
        } else {
        	List<String> currentAttributeValues = customAttribute.getValues();

        	List<String> newAttributeValues = new ArrayList<String>();
        	newAttributeValues.addAll(currentAttributeValues);
        	
        	customAttribute.setValues(newAttributeValues);

        	if (!newAttributeValues.contains(attributeValue)) {
        		newAttributeValues.add(attributeValue);
        	}
        }

        return updateUser(user);
    }

    public User removeUserAttribute(String userId, String attributeName, String attributeValue) {
        log.debug("Getting user information from LDAP: attributeName = '{0}', attributeValue = '{1}'", attributeName, attributeValue);

        User user = getUser(userId);
        if (user == null) {
        	return null;
        }
        
        CustomAttribute customAttribute = getCustomAttribute(user, attributeName);
        if (customAttribute != null) {
        	List<String> currentAttributeValues = customAttribute.getValues();
        	if (currentAttributeValues.contains(attributeValue)) {

        		List<String> newAttributeValues = new ArrayList<String>();
            	newAttributeValues.addAll(currentAttributeValues);
        		newAttributeValues.remove(attributeValue);

        		customAttribute.setValues(newAttributeValues);

        		return updateUser(user);
        	}
        }

        return null;
    }

	public CustomAttribute getCustomAttribute(User user, String attributeName) {
		for (CustomAttribute customAttribute : user.getCustomAttributes()) {
			if (StringHelper.equalsIgnoreCase(attributeName, customAttribute.getName())) {
				return customAttribute;
			}
		}

		return null;
	}

	public void setCustomAttribute(User user, String attributeName, String attributeValue) {
		CustomAttribute customAttribute = getCustomAttribute(user, attributeName);
		
		if (customAttribute == null) {
			customAttribute = new CustomAttribute(attributeName);
			user.getCustomAttributes().add(customAttribute);
		}
		
		customAttribute.setValue(attributeValue);
	}

    // this method must be called only if app mode = MEMORY, in ldap case it's anyway persisted in ldap.
    public boolean saveLongLivedToken(String userId, PersistentJwt longLivedToken) {
        log.debug("Saving long-lived access token: userId = {0}", userId);
        boolean succeed = false;

        User user = getUser(userId);
        if (user != null) {
            int nTokens = 0;
            if (user.getOxAuthPersistentJwt() != null) {
                nTokens = user.getOxAuthPersistentJwt().length;
            }
            nTokens++;
            String[] persistentJwts = new String[nTokens];
            if (user.getOxAuthPersistentJwt() != null) {
                for (int i = 0; i < user.getOxAuthPersistentJwt().length; i++) {
                    persistentJwts[i] = user.getOxAuthPersistentJwt()[i];
                }
            }
            persistentJwts[nTokens - 1] = longLivedToken.toString();

            user.setOxAuthPersistentJwt(persistentJwts);
            ldapEntryManager.merge(user);
            succeed = true;
        }

        return succeed;
    }

    public List<User> getUsersWithPersistentJwts() {
        String baseDN = ConfigurationFactory.instance().getBaseDn().getPeople();
        Filter filter = Filter.createPresenceFilter("oxAuthPersistentJWT");

        return ldapEntryManager.findEntries(baseDN, User.class, filter);
    }

    public String getDnForUser(String inum) {
		String peopleDn = ConfigurationFactory.instance().getBaseDn().getPeople();
		if (StringHelper.isEmpty(inum)) {
			return peopleDn;
		}

		return String.format("inum=%s,%s", inum, peopleDn);
	}

    public static UserService instance() {
        return (UserService) Component.getInstance(UserService.class);
    }

}