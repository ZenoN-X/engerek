/*
 * Copyright (c) 2010-2015 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.icf.dummy.resource;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.evolveum.midpoint.util.exception.SystemException;

import org.apache.commons.lang.StringUtils;

import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

/**
 * Resource for use with dummy ICF connector.
 * 
 * This is a simple Java object that pretends to be a resource. It has accounts and
 * account schema. It has operations to manipulate accounts, execute scripts and so on
 * almost like a real resource. The purpose is to simulate a real resource with a very 
 * little overhead.
 * 
 * The resource is a singleton, therefore the resource instance can be shared by
 * the connector and the test code. The usual story is like this:
 * 
 * 1) test class fetches first instance of the resource (getInstance). This will cause
 * loading of the resource class in the test (parent) classloader.
 * 
 * 2) test class configures the connector (e.g. schema) usually by calling the populateWithDefaultSchema() method.
 * 
 * 3) test class initializes IDM. This will cause connector initialization. The connector will fetch
 * the instance of dummy resource. As it was loaded by the parent classloader, it will get the same instance
 * as the test class.
 * 
 * 4) test class invokes IDM operation. That will invoke connector and change the resource.
 * 
 * 5) test class will access resource directly to see if the operation went OK.
 * 
 * The dummy resource is a separate package (JAR) from the dummy connector. Connector has its own
 * classloader. If the resource would be the same package as connector, it will get loaded by the
 * connector classloader regardless whether it is already loaded by the parent classloader.
 * 
 * @author Radovan Semancik
 *
 */
public class DummyResource implements DebugDumpable {
	
	private static final Trace LOGGER = TraceManager.getTrace(DummyResource.class);

	private String instanceName;
	private Map<String,DummyObject> allObjects;
	private Map<String,DummyAccount> accounts;
	private Map<String,DummyGroup> groups;
	private Map<String,DummyPrivilege> privileges;
	private List<ScriptHistoryEntry> scriptHistory;
	private DummyObjectClass accountObjectClass;
	private DummyObjectClass groupObjectClass;
	private DummyObjectClass privilegeObjectClass;
	private DummySyncStyle syncStyle;
	private List<DummyDelta> deltas;
	private int latestSyncToken;
	private boolean tolerateDuplicateValues = false;
	private boolean generateDefaultValues = false;
	private boolean enforceUniqueName = true;
	private boolean enforceSchema = true;
	private boolean caseIgnoreId = false;
	private boolean caseIgnoreValues = false;
	private int connectionCount = 0;
	private int groupMembersReadCount = 0;
	private Collection<String> forbiddenNames;

	private BreakMode schemaBreakMode = BreakMode.NONE;
	private BreakMode getBreakMode = BreakMode.NONE;
	private BreakMode addBreakMode = BreakMode.NONE;
	private BreakMode modifyBreakMode = BreakMode.NONE;
	private BreakMode deleteBreakMode = BreakMode.NONE;

	private boolean generateAccountDescriptionOnCreate = false;		   // simulates volatile behavior (on create)
	private boolean generateAccountDescriptionOnUpdate = false;        // simulates volatile behavior (on update)

	// Following two properties are just copied from the connector
	// configuration and can be checked later. They are otherwise
	// completely useless.
	private String uselessString;
	private String uselessGuardedString;
	
	private static Map<String, DummyResource> instances = new HashMap<String, DummyResource>();
	
	DummyResource() {
		allObjects = Collections.synchronizedMap(new LinkedHashMap<String,DummyObject>());
		accounts = Collections.synchronizedMap(new LinkedHashMap<String, DummyAccount>());
		groups = Collections.synchronizedMap(new LinkedHashMap<String, DummyGroup>());
		privileges = Collections.synchronizedMap(new LinkedHashMap<String, DummyPrivilege>());
		scriptHistory = new ArrayList<ScriptHistoryEntry>();
		accountObjectClass = new DummyObjectClass();
		groupObjectClass = new DummyObjectClass();
		privilegeObjectClass = new DummyObjectClass();
		syncStyle = DummySyncStyle.NONE;
		deltas = new ArrayList<DummyDelta>();
		latestSyncToken = 0;
	}
	
	/**
	 * Clears everything, just like the resouce was just created.
	 */
	public void reset() {
		allObjects.clear();
		accounts.clear();
		groups.clear();
		privileges.clear();
		scriptHistory.clear();
		accountObjectClass = new DummyObjectClass();
		groupObjectClass = new DummyObjectClass();
		privilegeObjectClass = new DummyObjectClass();
		syncStyle = DummySyncStyle.NONE;
		deltas.clear();
		latestSyncToken = 0;
		resetBreakMode();
	}
	
	public static DummyResource getInstance() {
		return getInstance(null);
	}
	
	public static DummyResource getInstance(String instanceName) {
		DummyResource instance = instances.get(instanceName);
		if (instance == null) {
			instance = new DummyResource();
			instance.setInstanceName(instanceName);
			instances.put(instanceName, instance);
		}
		return instance;
	}
	
	public String getInstanceName() {
		return instanceName;
	}

	public void setInstanceName(String instanceName) {
		this.instanceName = instanceName;
	}

	public boolean isTolerateDuplicateValues() {
		return tolerateDuplicateValues;
	}

	public void setTolerateDuplicateValues(boolean tolerateDuplicateValues) {
		this.tolerateDuplicateValues = tolerateDuplicateValues;
	}

	public boolean isGenerateDefaultValues() {
		return generateDefaultValues;
	}
	
	public void setGenerateDefaultValues(boolean generateDefaultValues) {
		this.generateDefaultValues = generateDefaultValues;
	}
	
	public boolean isEnforceUniqueName() {
		return enforceUniqueName;
	}

	public void setEnforceUniqueName(boolean enforceUniqueName) {
		this.enforceUniqueName = enforceUniqueName;
	}

	public boolean isEnforceSchema() {
		return enforceSchema;
	}

	public void setEnforceSchema(boolean enforceSchema) {
		this.enforceSchema = enforceSchema;
	}

	public BreakMode getSchemaBreakMode() {
		return schemaBreakMode;
	}

	public void setSchemaBreakMode(BreakMode schemaBreakMode) {
		this.schemaBreakMode = schemaBreakMode;
	}

	public BreakMode getAddBreakMode() {
		return addBreakMode;
	}

	public void setAddBreakMode(BreakMode addBreakMode) {
		this.addBreakMode = addBreakMode;
	}
	
	public BreakMode getGetBreakMode() {
		return getBreakMode;
	}

	public void setGetBreakMode(BreakMode getBreakMode) {
		this.getBreakMode = getBreakMode;
	}

	public BreakMode getModifyBreakMode() {
		return modifyBreakMode;
	}

	public void setModifyBreakMode(BreakMode modifyBreakMode) {
		this.modifyBreakMode = modifyBreakMode;
	}

	public BreakMode getDeleteBreakMode() {
		return deleteBreakMode;
	}

	public void setDeleteBreakMode(BreakMode deleteBreakMode) {
		this.deleteBreakMode = deleteBreakMode;
	}

	public void setBreakMode(BreakMode breakMode) {
		this.schemaBreakMode = breakMode;
		this.addBreakMode = breakMode;
		this.getBreakMode = breakMode;
		this.modifyBreakMode = breakMode;
		this.deleteBreakMode = breakMode;
	}
	
	public void resetBreakMode() {
		setBreakMode(BreakMode.NONE);
	}

	public String getUselessString() {
		return uselessString;
	}

	public void setUselessString(String uselessString) {
		this.uselessString = uselessString;
	}

	public String getUselessGuardedString() {
		return uselessGuardedString;
	}

	public void setUselessGuardedString(String uselessGuardedString) {
		this.uselessGuardedString = uselessGuardedString;
	}

	public boolean isCaseIgnoreId() {
		return caseIgnoreId;
	}

	public void setCaseIgnoreId(boolean caseIgnoreId) {
		this.caseIgnoreId = caseIgnoreId;
	}

	public boolean isCaseIgnoreValues() {
		return caseIgnoreValues;
	}

	public void setCaseIgnoreValues(boolean caseIgnoreValues) {
		this.caseIgnoreValues = caseIgnoreValues;
	}

	public boolean isGenerateAccountDescriptionOnCreate() {
		return generateAccountDescriptionOnCreate;
	}

	public void setGenerateAccountDescriptionOnCreate(boolean generateAccountDescriptionOnCreate) {
		this.generateAccountDescriptionOnCreate = generateAccountDescriptionOnCreate;
	}

	public boolean isGenerateAccountDescriptionOnUpdate() {
		return generateAccountDescriptionOnUpdate;
	}

	public void setGenerateAccountDescriptionOnUpdate(boolean generateAccountDescriptionOnUpdate) {
		this.generateAccountDescriptionOnUpdate = generateAccountDescriptionOnUpdate;
	}

	public Collection<String> getForbiddenNames() {
		return forbiddenNames;
	}

	public void setForbiddenNames(Collection<String> forbiddenNames) {
		this.forbiddenNames = forbiddenNames;
	}

	public int getConnectionCount() {
		return connectionCount;
	}

	public synchronized void connect() {
		connectionCount++;
	}
	
	public synchronized void disconnect() {
		connectionCount--;
	}
	
	public void assertNoConnections() {
		assert connectionCount == 0 : "Dummy resource: "+connectionCount+" connections still open";
	}

	public int getGroupMembersReadCount() {
		return groupMembersReadCount;
	}

	public void setGroupMembersReadCount(int groupMembersReadCount) {
		this.groupMembersReadCount = groupMembersReadCount;
	}
	
	public void recordGroupMembersReadCount() {
		groupMembersReadCount++;
		traceOperation("groupMembersRead", groupMembersReadCount);
	}

	public DummyObjectClass getAccountObjectClass() throws ConnectException, FileNotFoundException {
		if (schemaBreakMode == BreakMode.NONE) {
			return accountObjectClass;
		} else if (schemaBreakMode == BreakMode.NETWORK) {
			throw new ConnectException("The schema is not available (simulated error)");
		} else if (schemaBreakMode == BreakMode.IO) {
			throw new FileNotFoundException("The schema file not found (simulated error)");
		} else if (schemaBreakMode == BreakMode.GENERIC) {
			// The connector will react with generic exception
			throw new IllegalArgumentException("Generic error fetching schema (simulated error)");
		} else if (schemaBreakMode == BreakMode.RUNTIME) {
			// The connector will just pass this up
			throw new IllegalStateException("Generic error fetching schema (simulated error)");
		} else if (schemaBreakMode == BreakMode.UNSUPPORTED) {
			throw new UnsupportedOperationException("Schema is not supported (simulated error)");
		} else {
			// This is a real error. Use this strange thing to make sure it passes up
			throw new RuntimeException("Unknown schema break mode "+schemaBreakMode);
		}
		
	}

	public DummyObjectClass getGroupObjectClass() {
		return groupObjectClass;
	}
	
	public DummyObjectClass getPrivilegeObjectClass() {
		return privilegeObjectClass;
	}

	public Collection<DummyAccount> listAccounts() throws ConnectException, FileNotFoundException {
		if (getBreakMode == BreakMode.NONE) {
			return accounts.values();
		} else if (schemaBreakMode == BreakMode.NETWORK) {
			throw new ConnectException("Network error (simulated error)");
		} else if (schemaBreakMode == BreakMode.IO) {
			throw new FileNotFoundException("IO error (simulated error)");
		} else if (schemaBreakMode == BreakMode.GENERIC) {
			// The connector will react with generic exception
			throw new IllegalArgumentException("Generic error (simulated error)");
		} else if (schemaBreakMode == BreakMode.RUNTIME) {
			// The connector will just pass this up
			throw new IllegalStateException("Generic error (simulated error)");
		} else if (schemaBreakMode == BreakMode.UNSUPPORTED) {
			throw new UnsupportedOperationException("Not supported (simulated error)");
		} else {
			// This is a real error. Use this strange thing to make sure it passes up
			throw new RuntimeException("Unknown schema break mode "+schemaBreakMode);
		}
	}
	
	private <T extends DummyObject> T getObjectByName(Map<String,T> map, String name) throws ConnectException, FileNotFoundException {
		if (!enforceUniqueName) {
			throw new IllegalStateException("Attempt to search object by name while resource is in non-unique name mode");
		}
		if (getBreakMode == BreakMode.NONE) {
			return map.get(normalize(name));
		} else if (schemaBreakMode == BreakMode.NETWORK) {
			throw new ConnectException("Network error (simulated error)");
		} else if (schemaBreakMode == BreakMode.IO) {
			throw new FileNotFoundException("IO error (simulated error)");
		} else if (schemaBreakMode == BreakMode.GENERIC) {
			// The connector will react with generic exception
			throw new IllegalArgumentException("Generic error (simulated error)");
		} else if (schemaBreakMode == BreakMode.RUNTIME) {
			// The connector will just pass this up
			throw new IllegalStateException("Generic error (simulated error)");
		} else if (schemaBreakMode == BreakMode.UNSUPPORTED) {
			throw new UnsupportedOperationException("Not supported (simulated error)");
		} else {
			// This is a real error. Use this strange thing to make sure it passes up
			throw new RuntimeException("Unknown schema break mode "+schemaBreakMode);
		}
	}
	
	public DummyAccount getAccountByUsername(String username) throws ConnectException, FileNotFoundException {
		return getObjectByName(accounts, username);
	}
	
	public DummyGroup getGroupByName(String name) throws ConnectException, FileNotFoundException {
		return getObjectByName(groups, name);
	}
	
	public DummyPrivilege getPrivilegeByName(String name) throws ConnectException, FileNotFoundException {
		return getObjectByName(privileges, name);
	}
	
	private <T extends DummyObject> T getObjectById(Class<T> expectedClass, String id) throws ConnectException, FileNotFoundException {
		if (getBreakMode == BreakMode.NONE) {
			DummyObject dummyObject = allObjects.get(id);
			if (dummyObject == null) {
				return null;
			}
			if (!expectedClass.isInstance(dummyObject)) {
				throw new IllegalStateException("Arrrr! Wanted "+expectedClass+" with ID "+id+" but got "+dummyObject+" instead");
			}
			return (T)dummyObject;
		} else if (schemaBreakMode == BreakMode.NETWORK) {
			throw new ConnectException("Network error (simulated error)");
		} else if (schemaBreakMode == BreakMode.IO) {
			throw new FileNotFoundException("IO error (simulated error)");
		} else if (schemaBreakMode == BreakMode.GENERIC) {
			// The connector will react with generic exception
			throw new IllegalArgumentException("Generic error (simulated error)");
		} else if (schemaBreakMode == BreakMode.RUNTIME) {
			// The connector will just pass this up
			throw new IllegalStateException("Generic error (simulated error)");
		} else if (schemaBreakMode == BreakMode.UNSUPPORTED) {
			throw new UnsupportedOperationException("Not supported (simulated error)");
		} else {
			// This is a real error. Use this strange thing to make sure it passes up
			throw new RuntimeException("Unknown schema break mode "+schemaBreakMode);
		}
	}
	
	public DummyAccount getAccountById(String id) throws ConnectException, FileNotFoundException {
		return getObjectById(DummyAccount.class, id);
	}
	
	public DummyGroup getGroupById(String id) throws ConnectException, FileNotFoundException {
		return getObjectById(DummyGroup.class, id);
	}
	
	public DummyPrivilege getPrivilegeById(String id) throws ConnectException, FileNotFoundException {
		return getObjectById(DummyPrivilege.class, id);
	}

	public Collection<DummyGroup> listGroups() throws ConnectException, FileNotFoundException {
		if (getBreakMode == BreakMode.NONE) {
			return groups.values();
		} else if (schemaBreakMode == BreakMode.NETWORK) {
			throw new ConnectException("Network error (simulated error)");
		} else if (schemaBreakMode == BreakMode.IO) {
			throw new FileNotFoundException("IO error (simulated error)");
		} else if (schemaBreakMode == BreakMode.GENERIC) {
			// The connector will react with generic exception
			throw new IllegalArgumentException("Generic error (simulated error)");
		} else if (schemaBreakMode == BreakMode.RUNTIME) {
			// The connector will just pass this up
			throw new IllegalStateException("Generic error (simulated error)");
		} else if (schemaBreakMode == BreakMode.UNSUPPORTED) {
			throw new UnsupportedOperationException("Not supported (simulated error)");
		} else {
			// This is a real error. Use this strange thing to make sure it passes up
			throw new RuntimeException("Unknown schema break mode "+schemaBreakMode);
		}
	}
	
	public Collection<DummyPrivilege> listPrivileges() throws ConnectException, FileNotFoundException {
		if (getBreakMode == BreakMode.NONE) {
			return privileges.values();
		} else if (schemaBreakMode == BreakMode.NETWORK) {
			throw new ConnectException("Network error (simulated error)");
		} else if (schemaBreakMode == BreakMode.IO) {
			throw new FileNotFoundException("IO error (simulated error)");
		} else if (schemaBreakMode == BreakMode.GENERIC) {
			// The connector will react with generic exception
			throw new IllegalArgumentException("Generic error (simulated error)");
		} else if (schemaBreakMode == BreakMode.RUNTIME) {
			// The connector will just pass this up
			throw new IllegalStateException("Generic error (simulated error)");
		} else if (schemaBreakMode == BreakMode.UNSUPPORTED) {
			throw new UnsupportedOperationException("Not supported (simulated error)");
		} else {
			// This is a real error. Use this strange thing to make sure it passes up
			throw new RuntimeException("Unknown schema break mode "+schemaBreakMode);
		}
	}
	
	private synchronized <T extends DummyObject> String addObject(Map<String,T> map, T newObject) throws ObjectAlreadyExistsException, ConnectException, FileNotFoundException, SchemaViolationException {
		if (addBreakMode == BreakMode.NONE) {
			// just go on
		} else if (addBreakMode == BreakMode.NETWORK) {
			throw new ConnectException("Network error during add (simulated error)");
		} else if (addBreakMode == BreakMode.IO) {
			throw new FileNotFoundException("IO error during add (simulated error)");
		} else if (addBreakMode == BreakMode.GENERIC) {
			// The connector will react with generic exception
			throw new IllegalArgumentException("Generic error during add (simulated error)");
		} else if (addBreakMode == BreakMode.RUNTIME) {
			// The connector will just pass this up
			throw new IllegalStateException("Generic rutime error during add (simulated error)");
		} else if (addBreakMode == BreakMode.UNSUPPORTED) {
			throw new UnsupportedOperationException("Unsupported operation: add (simulated error)");
		} else {
			// This is a real error. Use this strange thing to make sure it passes up
			throw new RuntimeException("Unknown break mode "+addBreakMode);
		}
		
		Class<? extends DummyObject> type = newObject.getClass();
		String normalName = normalize(newObject.getName());
		if (normalName != null && forbiddenNames != null && forbiddenNames.contains(normalName)) {
			throw new ObjectAlreadyExistsException(normalName + " is forbidden to use as an object name");
		}

		String newId = UUID.randomUUID().toString();
		newObject.setId(newId);
		if (allObjects.containsKey(newId)) {
			throw new IllegalStateException("The hell is frozen over. The impossible has happened. ID "+newId+" already exists ("+ type.getSimpleName()+" with identifier "+normalName+")");
		}
		
		//this is "resource-generated" attribute (used to simulate resource which generate by default attributes which we need to sync)
		if (generateDefaultValues){
//			int internalId = allObjects.size();
			newObject.addAttributeValue(DummyAccount.ATTR_INTERNAL_ID, new Random().nextInt());
		}
		
		
		String mapKey;
		if (enforceUniqueName) {
			mapKey = normalName;
		} else {
			mapKey = newId;
		}
		
		if (map.containsKey(mapKey)) {
			throw new ObjectAlreadyExistsException(type.getSimpleName()+" with name '"+normalName+"' already exists");
		}
		
		newObject.setResource(this);
		map.put(mapKey, newObject);
		allObjects.put(newId, newObject);
		
		if (syncStyle != DummySyncStyle.NONE) {
			int syncToken = nextSyncToken();
			DummyDelta delta = new DummyDelta(syncToken, type, newId, newObject.getName(), DummyDeltaType.ADD);
			deltas.add(delta);
		}
		
		return newObject.getName();
	}
	

	private synchronized <T extends DummyObject> void deleteObjectByName(Class<T> type, Map<String,T> map, String name) throws ObjectDoesNotExistException, ConnectException, FileNotFoundException {
		if (deleteBreakMode == BreakMode.NONE) {
			// go on
		} else if (deleteBreakMode == BreakMode.NETWORK) {
			throw new ConnectException("Network error (simulated error)");
		} else if (deleteBreakMode == BreakMode.IO) {
			throw new FileNotFoundException("IO error (simulated error)");
		} else if (deleteBreakMode == BreakMode.GENERIC) {
			// The connector will react with generic exception
			throw new IllegalArgumentException("Generic error (simulated error)");
		} else if (deleteBreakMode == BreakMode.RUNTIME) {
			// The connector will just pass this up
			throw new IllegalStateException("Generic error (simulated error)");
		} else if (deleteBreakMode == BreakMode.UNSUPPORTED) {
			throw new UnsupportedOperationException("Not supported (simulated error)");
		} else {
			// This is a real error. Use this strange thing to make sure it passes up
			throw new RuntimeException("Unknown schema break mode "+schemaBreakMode);
		}
		
		String normalName = normalize(name);
		T existingObject;
		
		if (!enforceUniqueName) {
			throw new IllegalStateException("Whoops! got into deleteObjectByName without enforceUniqueName");
		}
		
		if (map.containsKey(normalName)) {
			existingObject = map.get(normalName);
			map.remove(normalName);
			allObjects.remove(existingObject.getId());
		} else {
			throw new ObjectDoesNotExistException(type.getSimpleName()+" with name '"+normalName+"' does not exist");
		}
		
		if (syncStyle != DummySyncStyle.NONE) {
			int syncToken = nextSyncToken();
			DummyDelta delta = new DummyDelta(syncToken, type, existingObject.getId(), name, DummyDeltaType.DELETE);
			deltas.add(delta);
		}
	}
	
	public void deleteAccountById(String id) throws ConnectException, FileNotFoundException, ObjectDoesNotExistException {
		deleteObjectById(DummyAccount.class, accounts, id);
	}

	public void deleteGroupById(String id) throws ConnectException, FileNotFoundException, ObjectDoesNotExistException {
		deleteObjectById(DummyGroup.class, groups, id);
	}

	public void deletePrivilegeById(String id) throws ConnectException, FileNotFoundException, ObjectDoesNotExistException {
		deleteObjectById(DummyPrivilege.class, privileges, id);
	}

	private synchronized <T extends DummyObject> void deleteObjectById(Class<T> type, Map<String,T> map, String id) throws ObjectDoesNotExistException, ConnectException, FileNotFoundException {
		if (deleteBreakMode == BreakMode.NONE) {
			// go on
		} else if (deleteBreakMode == BreakMode.NETWORK) {
			throw new ConnectException("Network error (simulated error)");
		} else if (deleteBreakMode == BreakMode.IO) {
			throw new FileNotFoundException("IO error (simulated error)");
		} else if (deleteBreakMode == BreakMode.GENERIC) {
			// The connector will react with generic exception
			throw new IllegalArgumentException("Generic error (simulated error)");
		} else if (deleteBreakMode == BreakMode.RUNTIME) {
			// The connector will just pass this up
			throw new IllegalStateException("Generic error (simulated error)");
		} else if (deleteBreakMode == BreakMode.UNSUPPORTED) {
			throw new UnsupportedOperationException("Not supported (simulated error)");
		} else {
			// This is a real error. Use this strange thing to make sure it passes up
			throw new RuntimeException("Unknown schema break mode "+schemaBreakMode);
		}
		
		DummyObject object = allObjects.get(id);
		if (object == null) {
			throw new ObjectDoesNotExistException(type.getSimpleName()+" with id '"+id+"' does not exist");
		}
		if (!type.isInstance(object)) {
			throw new IllegalStateException("Arrrr! Wanted "+type+" with ID "+id+" but got "+object+" instead");
		}
		T existingObject = (T)object;
		String normalName = normalize(object.getName());
		
		allObjects.remove(id);
		
		String mapKey;
		if (enforceUniqueName) {
			mapKey = normalName;
		} else {
			mapKey = id;
		}
		
		if (map.containsKey(mapKey)) {
			map.remove(mapKey);
		} else {
			throw new ObjectDoesNotExistException(type.getSimpleName()+" with name '"+normalName+"' does not exist");
		}
		
		if (syncStyle != DummySyncStyle.NONE) {
			int syncToken = nextSyncToken();
			DummyDelta delta = new DummyDelta(syncToken, type, id, object.getName(), DummyDeltaType.DELETE);
			deltas.add(delta);
		}
	}

	private <T extends DummyObject> void renameObject(Class<T> type, Map<String,T> map, String id, String oldName, String newName) throws ObjectDoesNotExistException, ObjectAlreadyExistsException, ConnectException, FileNotFoundException {
		if (modifyBreakMode == BreakMode.NONE) {
			// go on
		} else if (modifyBreakMode == BreakMode.NETWORK) {
			throw new ConnectException("Network error (simulated error)");
		} else if (modifyBreakMode == BreakMode.IO) {
			throw new FileNotFoundException("IO error (simulated error)");
		} else if (modifyBreakMode == BreakMode.GENERIC) {
			// The connector will react with generic exception
			throw new IllegalArgumentException("Generic error (simulated error)");
		} else if (modifyBreakMode == BreakMode.RUNTIME) {
			// The connector will just pass this up
			throw new IllegalStateException("Generic error (simulated error)");
		} else if (modifyBreakMode == BreakMode.UNSUPPORTED) {
			throw new UnsupportedOperationException("Not supported (simulated error)");
		} else {
			// This is a real error. Use this strange thing to make sure it passes up
			throw new RuntimeException("Unknown schema break mode "+schemaBreakMode);
		}
		
		T existingObject;
		if (enforceUniqueName) {
			String normalOldName = normalize(oldName);
			String normalNewName = normalize(newName);
			existingObject = map.get(normalOldName);
			if (existingObject == null) {
				throw new ObjectDoesNotExistException("Cannot rename, "+type.getSimpleName()+" with username '"+normalOldName+"' does not exist");
			}
			if (map.containsKey(normalNewName)) {
				throw new ObjectAlreadyExistsException("Cannot rename, "+type.getSimpleName()+" with username '"+normalNewName+"' already exists");
			}
			map.put(normalNewName, existingObject);
			map.remove(normalOldName);
		} else {
			existingObject = (T) allObjects.get(id);
		}
		existingObject.setName(newName);
		if (existingObject instanceof DummyAccount) {
			changeDescriptionIfNeeded((DummyAccount) existingObject);
		}
	}
	
	public String addAccount(DummyAccount newAccount) throws ObjectAlreadyExistsException, ConnectException, FileNotFoundException, SchemaViolationException {
		if (generateAccountDescriptionOnCreate && newAccount.getAttributeValue(DummyAccount.ATTR_DESCRIPTION_NAME) == null) {
			newAccount.addAttributeValue(DummyAccount.ATTR_DESCRIPTION_NAME, "Description of " + newAccount.getName());
		}
		return addObject(accounts, newAccount);
	}
	
	public void deleteAccountByName(String id) throws ObjectDoesNotExistException, ConnectException, FileNotFoundException {
		deleteObjectByName(DummyAccount.class, accounts, id);
	}

	public void renameAccount(String id, String oldUsername, String newUsername) throws ObjectDoesNotExistException, ObjectAlreadyExistsException, ConnectException, FileNotFoundException, SchemaViolationException {
		renameObject(DummyAccount.class, accounts, id, oldUsername, newUsername);
		for (DummyGroup group : groups.values()) {
			if (group.containsMember(oldUsername)) {
				group.removeMember(oldUsername);
				group.addMember(newUsername);
			}
		}
	}

	public void changeDescriptionIfNeeded(DummyAccount account) {
		if (generateAccountDescriptionOnCreate) {
			try {
				account.replaceAttributeValue(DummyAccount.ATTR_DESCRIPTION_NAME, "Updated description of " + account.getName());
			} catch (SchemaViolationException|ConnectException|FileNotFoundException e) {
				throw new SystemException("Couldn't replace the 'description' attribute value", e);
			}
		}
	}

	public String addGroup(DummyGroup newGroup) throws ObjectAlreadyExistsException, ConnectException, FileNotFoundException, SchemaViolationException {
		return addObject(groups, newGroup);
	}
	
	public void deleteGroupByName(String id) throws ObjectDoesNotExistException, ConnectException, FileNotFoundException {
		deleteObjectByName(DummyGroup.class, groups, id);
	}

	public void renameGroup(String id, String oldName, String newName) throws ObjectDoesNotExistException, ObjectAlreadyExistsException, ConnectException, FileNotFoundException {
		renameObject(DummyGroup.class, groups, id, oldName, newName);
	}
	
	public String addPrivilege(DummyPrivilege newGroup) throws ObjectAlreadyExistsException, ConnectException, FileNotFoundException, SchemaViolationException {
		return addObject(privileges, newGroup);
	}
	
	public void deletePrivilegeByName(String id) throws ObjectDoesNotExistException, ConnectException, FileNotFoundException {
		deleteObjectByName(DummyPrivilege.class, privileges, id);
	}

	public void renamePrivilege(String id, String oldName, String newName) throws ObjectDoesNotExistException, ObjectAlreadyExistsException, ConnectException, FileNotFoundException {
		renameObject(DummyPrivilege.class, privileges, id, oldName, newName);
	}
	
	void recordModify(DummyObject dObject) {
		if (syncStyle != DummySyncStyle.NONE) {
			int syncToken = nextSyncToken();
			DummyDelta delta = new DummyDelta(syncToken, dObject.getClass(), dObject.getId(), dObject.getName(), DummyDeltaType.MODIFY);
			deltas.add(delta);
		}
	}

	/**
	 * Returns script history ordered chronologically (oldest first).
	 * @return script history
	 */
	public List<ScriptHistoryEntry> getScriptHistory() {
		return scriptHistory;
	}
	
	/**
	 * Clears the script history.
	 */
	public void purgeScriptHistory() {
		scriptHistory.clear();
	}
	
	/**
	 * Pretend to run script on the resource.
	 * The script is actually not executed, it is only recorded in the script history
	 * and can be fetched by getScriptHistory().
	 * 
	 * @param scriptCode code of the script
	 */
	public void runScript(String language, String scriptCode, Map<String, Object> params) {
		scriptHistory.add(new ScriptHistoryEntry(language, scriptCode, params));
	}
	
	/**
	 * Populates the resource with some kind of "default" schema. This is a schema that should suit
	 * majority of basic test cases.
	 */
	public void populateWithDefaultSchema() {
		accountObjectClass.clear();
		accountObjectClass.addAttributeDefinition(DummyAccount.ATTR_FULLNAME_NAME, String.class, true, false);
		accountObjectClass.addAttributeDefinition(DummyAccount.ATTR_INTERNAL_ID, String.class, false, false);
		accountObjectClass.addAttributeDefinition(DummyAccount.ATTR_DESCRIPTION_NAME, String.class, false, false);
		accountObjectClass.addAttributeDefinition(DummyAccount.ATTR_INTERESTS_NAME, String.class, false, true);
		accountObjectClass.addAttributeDefinition(DummyAccount.ATTR_PRIVILEGES_NAME, String.class, false, true);
		groupObjectClass.clear();
		groupObjectClass.addAttributeDefinition(DummyGroup.ATTR_MEMBERS_NAME, String.class, false, true);
		privilegeObjectClass.clear();
	}

	public DummySyncStyle getSyncStyle() {
		return syncStyle;
	}

	public void setSyncStyle(DummySyncStyle syncStyle) {
		this.syncStyle = syncStyle;
	}

	private synchronized int nextSyncToken() {
		return ++latestSyncToken;
	}

	public int getLatestSyncToken() {
		return latestSyncToken;
	}
	
	private String normalize(String id) {
		if (caseIgnoreId) {
			return StringUtils.lowerCase(id);
		} else {
			return id;
		}
	}

	
	public List<DummyDelta> getDeltasSince(int syncToken) {
		List<DummyDelta> result = new ArrayList<DummyDelta>();
		for (DummyDelta delta: deltas) {
			if (delta.getSyncToken() > syncToken) {
				result.add(delta);
			}
		}
		return result;
	}
	
	private void traceOperation(String opName, long counter) {
		LOGGER.info("MONITOR dummy '{}' {} ({})", instanceName, opName, counter);
		if (LOGGER.isDebugEnabled()) {
			StackTraceElement[] fullStack = Thread.currentThread().getStackTrace();
			String immediateClass = null;
			String immediateMethod = null;
			StringBuilder sb = new StringBuilder();
			for (StackTraceElement stackElement: fullStack) {
				if (stackElement.getClassName().equals(DummyResource.class.getName()) ||
						stackElement.getClassName().equals(Thread.class.getName())) {
					// skip our own calls
					continue;
				}
				if (immediateClass == null) {
					immediateClass = stackElement.getClassName();
					immediateMethod = stackElement.getMethodName();
				}
				sb.append(stackElement.toString());
				sb.append("\n");
			}
			LOGGER.debug("MONITOR dummy '{}' {} ({}): {} {}", new Object[]{instanceName, opName, counter, immediateClass, immediateMethod});
			LOGGER.trace("MONITOR dummy '{}' {} ({}):\n{}", new Object[]{instanceName, opName, counter, sb});
		}
	}

	
	@Override
	public String debugDump() {
		return debugDump(0);
	}
	
	@Override
	public String debugDump(int indent) {
		StringBuilder sb = new StringBuilder(toString());
		DebugUtil.indentDebugDump(sb, indent);
		sb.append("\nAccounts:");
		for (Entry<String, DummyAccount> entry: accounts.entrySet()) {
			sb.append("\n  ");
			sb.append(entry.getKey());
			sb.append(": ");
			sb.append(entry.getValue());
		}
		sb.append("\nGroups:");
		for (Entry<String, DummyGroup> entry: groups.entrySet()) {
			sb.append("\n  ");
			sb.append(entry.getKey());
			sb.append(": ");
			sb.append(entry.getValue());
		}
		sb.append("\nDeltas:");
		for (DummyDelta delta: deltas) {
			sb.append("\n  ");
			sb.append(delta);
		}
		sb.append("\nLatest token:").append(latestSyncToken);
		return sb.toString();
	}

	@Override
	public String toString() {
		return "DummyResource("+accounts.size()+" accounts, "+groups.size()+" groups)";
	}

}