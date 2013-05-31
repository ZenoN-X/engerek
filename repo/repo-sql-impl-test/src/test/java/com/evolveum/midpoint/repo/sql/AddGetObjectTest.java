/*
 * Copyright (c) 2010-2013 Evolveum
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

package com.evolveum.midpoint.repo.sql;

import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.ReferenceDelta;
import com.evolveum.midpoint.repo.sql.type.XMLGregorianCalendarType;
import com.evolveum.midpoint.schema.processor.ResourceSchema;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.*;
import com.evolveum.prism.xml.ns._public.types_2.PolyStringType;
import org.hibernate.stat.Statistics;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import javax.xml.namespace.QName;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author lazyman
 */
@ContextConfiguration(locations = {"../../../../../ctx-test.xml"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AddGetObjectTest extends BaseSQLRepoTest {

    private static final Trace LOGGER = TraceManager.getTrace(AddGetObjectTest.class);

    @Test(enabled = false)
    public <T extends ObjectType> void perfTest() throws Exception {
        Statistics stats = getFactory().getStatistics();
        stats.setStatisticsEnabled(true);

        final File OBJECTS_FILE = new File("./src/test/resources/10k-users.xml");
        List<PrismObject<? extends Objectable>> elements = prismContext.getPrismDomProcessor().parseObjects(
                OBJECTS_FILE);

        long previousCycle = 0;
        long time = System.currentTimeMillis();
        for (int i = 0; i < elements.size(); i++) {
            if (i % 500 == 0) {
                LOGGER.info("Previous cycle time {}. Next cycle: {}", new Object[]{
                        (System.currentTimeMillis() - time - previousCycle), i});
                previousCycle = System.currentTimeMillis() - time;
            }

            PrismObject<T> object = (PrismObject<T>) elements.get(i);
            repositoryService.addObject(object, null, new OperationResult("add performance test"));
        }
        LOGGER.info("Time to add objects ({}): {}",
                new Object[]{elements.size(), (System.currentTimeMillis() - time)});

        stats.logSummary();
    }

    @Test
    public void addSameName() throws Exception {
        final File user = new File(FOLDER_BASIC, "objects-user.xml");
        addGetCompare(user);
        try {
            // WHEN
            addGetCompare(user);

            assert false : "Unexpected success";
        } catch (ObjectAlreadyExistsException e) {
            TestUtil.assertExceptionSanity(e);
        }
    }

    @Test
    public void addGetDSEESyncDoubleTest() throws Exception {
        final File OBJECTS_FILE = new File("./../../samples/dsee/odsee-localhost-advanced-sync.xml");
        if (!OBJECTS_FILE.exists()) {
            LOGGER.warn("skipping addGetDSEESyncDoubleTest, file {} not found.",
                    new Object[]{OBJECTS_FILE.getPath()});
            return;
        }
        addGetCompare(OBJECTS_FILE);
        try {
            // WHEN
            addGetCompare(OBJECTS_FILE);

            assert false : "Unexpected success";
        } catch (ObjectAlreadyExistsException e) {
            TestUtil.assertExceptionSanity(e);
        }
    }

    @Test
    public void simpleAddGetTest() throws Exception {
        LOGGER.info("===[ simpleAddGetTest ]===");
        final File OBJECTS_FILE = new File(FOLDER_BASIC, "objects.xml");
        addGetCompare(OBJECTS_FILE);
    }

    private void addGetCompare(File file) throws Exception {
        List<PrismObject<? extends Objectable>> elements = prismContext.getPrismDomProcessor().parseObjects(file);
        List<String> oids = new ArrayList<String>();

        OperationResult result = new OperationResult("Simple Add Get Test");
        long time = System.currentTimeMillis();
        for (int i = 0; i < elements.size(); i++) {
            PrismObject object = elements.get(i);
            LOGGER.info("Adding object {}, type {}", new Object[]{(i + 1),
                    object.getCompileTimeClass().getSimpleName()});
            oids.add(repositoryService.addObject(object, null, result));
        }
        LOGGER.info("Time to add objects ({}): {}", new Object[]{elements.size(),
                (System.currentTimeMillis() - time),});

        int count = 0;
        elements = prismContext.getPrismDomProcessor().parseObjects(file);
        for (int i = 0; i < elements.size(); i++) {
            try {
                PrismObject object = elements.get(i);
                object.asObjectable().setOid(oids.get(i));

                Class<? extends ObjectType> clazz = object.getCompileTimeClass();
                PrismObject<? extends ObjectType> newObject = repositoryService.getObject(clazz, oids.get(i), result);
                LOGGER.info("Old\n{}\nnew\n{}", new Object[]{object.debugDump(3), newObject.debugDump(3)});
                checkContainersSize(newObject, object);

                ObjectDelta delta = object.diff(newObject);
                if (delta == null) {
                    continue;
                }

                count += delta.getModifications().size();
                if (delta.getModifications().size() > 0) {
                    if (delta.getModifications().size() == 1) {
                        ItemDelta d = (ItemDelta) delta.getModifications().iterator().next();

                        if (ShadowType.F_DEAD.equals(d.getName())) {
                            count -= delta.getModifications().size();
                            continue;
                        }
                    }
                    LOGGER.error(">>> {} Found {} changes for {}\n{}", new Object[]{(i + 1),
                            delta.getModifications().size(), newObject.toString(), delta.debugDump(3)});
                    LOGGER.error("{}", prismContext.getPrismDomProcessor().serializeObjectToString(newObject));
                }
            } catch (Exception ex) {
                LOGGER.error("Exception occurred", ex);
                throw ex;
            }
        }

        AssertJUnit.assertEquals("Found changes during add/get test " + count, 0, count);
    }

    private Integer size(PrismContainerValue value) {
        if (value == null) {
            return null;
        }

        return value.getItems() != null ? value.getItems().size() : 0;
    }

    private void checkContainerValuesSize(QName parentName, PrismContainerValue newValue, PrismContainerValue oldValue) {
        LOGGER.info("Checking: " + parentName);
        AssertJUnit.assertEquals("Count doesn't match for '" + parentName + "'", size(oldValue), size(newValue));

        List<QName> checked = new ArrayList<QName>();

        for (Item item : (List<Item>) newValue.getItems()) {
            if (!(item instanceof PrismContainer)) {
                continue;
            }

            PrismContainer newContainer = (PrismContainer) item;
            PrismContainer oldContainer = oldValue.findContainer(newContainer.getName());
            AssertJUnit.assertNotNull("Container '" + newContainer.getName() + "' doesn't exist.", oldContainer);

            checkContainersSize(newContainer, oldContainer);
            checked.add(oldContainer.getName());
        }

        for (Item item : (List<Item>) oldValue.getItems()) {
            if (!(item instanceof PrismContainer) || checked.contains(item.getName())) {
                continue;
            }

            PrismContainer oldContainer = (PrismContainer) item;
            PrismContainer newContainer = newValue.findContainer(oldContainer.getName());
            checkContainersSize(newContainer, oldContainer);
        }
    }

    private void checkContainersSize(PrismContainer newContainer, PrismContainer oldContainer) {
        LOGGER.info("checkContainersSize {} new {}  old {}",

                new Object[]{newContainer.getName(), newContainer.size(), oldContainer.size()});
        AssertJUnit.assertEquals(newContainer.size(), oldContainer.size());

        List<Long> checked = new ArrayList<Long>();
        List<PrismContainerValue> newValues = newContainer.getValues();
        for (PrismContainerValue value : newValues) {
            PrismContainerValue oldValue = oldContainer.getValue(value.getId());

            checkContainerValuesSize(newContainer.getName(), value, oldValue);
            checked.add(value.getId());
        }

        List<PrismContainerValue> oldValues = oldContainer.getValues();
        for (PrismContainerValue value : oldValues) {
            if (checked.contains(value.getId())) {
                continue;
            }

            PrismContainerValue newValue = newContainer.getValue(value.getId());
            checkContainerValuesSize(newContainer.getName(), newValue, value);
        }
    }

    @Test
    public void addUserWithAssignmentExtension() throws Exception {
        LOGGER.info("===[ addUserWithAssignmentExtension ]===");
        File file = new File(FOLDER_BASIC, "user-assignment-extension.xml");
        List<PrismObject<? extends Objectable>> elements = prismContext.getPrismDomProcessor().parseObjects(file);

        OperationResult result = new OperationResult("ADD");
        String oid = repositoryService.addObject((PrismObject) elements.get(0), null, result);

        PrismObject<UserType> fileUser = (PrismObject<UserType>) prismContext.getPrismDomProcessor().parseObjects(file)
                .get(0);
        long id = 1;
        for (AssignmentType assignment : fileUser.asObjectable().getAssignment()) {
            assignment.setId(id);
            id++;
        }

        PrismObject<UserType> repoUser = repositoryService.getObject(UserType.class, oid, result);

        ObjectDelta<UserType> delta = fileUser.diff(repoUser);
        AssertJUnit.assertNotNull(delta);
        LOGGER.info("delta\n{}", new Object[]{delta.debugDump(3)});
        AssertJUnit.assertTrue(delta.isEmpty());
    }

    /**
     * Attempt to store full account in the repo and then get it out again. The
     * potential problem is that there are attributes that do not have a fixed
     * (static) definition.
     */
    @Test
    public void addGetFullAccount() throws Exception {
        LOGGER.info("===[ addGetFullAccount ]===");
        File file = new File(FOLDER_BASIC, "account-full.xml");
        PrismObject<ShadowType> fileAccount = prismContext.parseObject(new File(FOLDER_BASIC, "account-full.xml"));

        // apply appropriate schema
        PrismObject<ResourceType> resource = prismContext.parseObject(new File(FOLDER_BASIC, "resource-opendj.xml"));
        ResourceSchema resourceSchema = RefinedResourceSchema.getResourceSchema(resource, prismContext);
        ShadowUtil.applyResourceSchema(fileAccount, resourceSchema);

        OperationResult result = new OperationResult("ADD");
        String oid = repositoryService.addObject(fileAccount, null, result);

        PrismObject<ShadowType> repoAccount = repositoryService.getObject(ShadowType.class, oid, result);

        ObjectDelta<ShadowType> delta = fileAccount.diff(repoAccount);
        AssertJUnit.assertNotNull(delta);
        LOGGER.info("delta\n{}", new Object[]{delta.debugDump(3)});
        AssertJUnit.assertTrue(delta.isEmpty());
        ShadowType repoShadow = repoAccount.asObjectable();
        AssertJUnit.assertNotNull(repoShadow.getSynchronizationSituation());
        AssertJUnit.assertEquals(SynchronizationSituationType.LINKED, repoShadow.getSynchronizationSituation());
        AssertJUnit.assertNotNull(repoShadow.getSynchronizationSituationDescription());
        AssertJUnit.assertEquals(1, repoShadow.getSynchronizationSituationDescription().size());
        AssertJUnit.assertEquals(SynchronizationSituationType.LINKED, repoShadow.getSynchronizationSituationDescription().get(0).getSituation());
        AssertJUnit.assertEquals("syncChannel", repoShadow.getSynchronizationSituationDescription().get(0).getChannel());
    }

    @Test
    public void addGetSystemConfigFile() throws Exception {
        LOGGER.info("===[ addGetPasswordPolicy ]===");
        File file = new File(FOLDER_BASIC, "password-policy.xml");
        PrismObject<ValuePolicyType> filePasswordPolicy = prismContext.parseObject(new File(FOLDER_BASIC, "password-policy.xml"));

        OperationResult result = new OperationResult("ADD");
        String pwdPolicyOid = "00000000-0000-0000-0000-000000000003";
        String oid = repositoryService.addObject(filePasswordPolicy, null, result);
        AssertJUnit.assertNotNull(oid);
        AssertJUnit.assertEquals(pwdPolicyOid, oid);
        PrismObject<ValuePolicyType> repoPasswordPolicy = repositoryService.getObject(ValuePolicyType.class, oid, result);
        AssertJUnit.assertNotNull(repoPasswordPolicy);

        String systemCongigOid = "00000000-0000-0000-0000-000000000001";
        PrismObject<SystemConfigurationType> fileSystemConfig = prismContext.parseObject(new File(FOLDER_BASIC, "systemConfiguration.xml"));
        LOGGER.info("System config from file: {}", fileSystemConfig.dump());
        oid = repositoryService.addObject(fileSystemConfig, null, result);
        AssertJUnit.assertNotNull(oid);
        AssertJUnit.assertEquals(systemCongigOid, oid);

        PrismObject<SystemConfigurationType> repoSystemConfig = repositoryService.getObject(SystemConfigurationType.class, systemCongigOid, result);
//		AssertJUnit.assertNotNull("global password policy null", repoSystemConfig.asObjectable().getGlobalPasswordPolicy());
        LOGGER.info("System config from repo: {}", repoSystemConfig.dump());
        AssertJUnit.assertNull("global password policy not null", repoSystemConfig.asObjectable()
                .getGlobalPasswordPolicyRef());

        ReferenceDelta refDelta = ReferenceDelta.createModificationAdd(
                SystemConfigurationType.F_GLOBAL_PASSWORD_POLICY_REF, repoSystemConfig.getDefinition(),
                PrismReferenceValue.createFromTarget(repoPasswordPolicy));
        List<ReferenceDelta> refDeltas = new ArrayList<ReferenceDelta>();
        refDeltas.add(refDelta);
        repositoryService.modifyObject(SystemConfigurationType.class, systemCongigOid, refDeltas, result);
        repoSystemConfig = repositoryService.getObject(SystemConfigurationType.class, systemCongigOid, result);
        LOGGER.info("system config after modify: {}", repoSystemConfig.dump());
        AssertJUnit.assertNotNull("global password policy null", repoSystemConfig.asObjectable()
                .getGlobalPasswordPolicyRef());
        AssertJUnit.assertNull("default user template not null", repoSystemConfig.asObjectable()
                .getDefaultUserTemplateRef());

//        AssertJUnit.assertNotNull("org root ref is null.", repoSystemConfig.asObjectable().getOrgRootRef());
//        AssertJUnit.assertEquals(2, repoSystemConfig.asObjectable().getOrgRootRef().size());
//        List<ObjectReferenceType> orgRootRefs = repoSystemConfig.asObjectable().getOrgRootRef();
//        String[] refs = {"10000000-0000-0000-0000-000000000003", "20000000-0000-0000-0000-000000000003"};
//        for (String ref : refs) {
//            boolean found = false;
//            for (ObjectReferenceType orgRootRef : orgRootRefs) {
//                if (ref.equals(orgRootRef.getOid())) {
//                    found = true;
//                    break;
//                }
//            }
//            AssertJUnit.assertTrue(ref  + " was not found in org. root refs in system configuration.",found);
//        }
    }

    @Test
    public void addGetSyncDescription() throws Exception {
        PrismObjectDefinition accDef = prismContext.getSchemaRegistry()
                .findObjectDefinitionByCompileTimeClass(ShadowType.class);
        PrismObject<ShadowType> shadow = accDef.instantiate();
        final Date TIME = new Date();
        ShadowType shadowType = shadow.asObjectable();
        shadowType.setName(new PolyStringType("sync desc test"));
        SynchronizationSituationDescriptionType desc = new SynchronizationSituationDescriptionType();
        desc.setChannel("channel");
        desc.setSituation(SynchronizationSituationType.LINKED);
        desc.setTimestamp(XMLGregorianCalendarType.asXMLGregorianCalendar(TIME));
        shadowType.getSynchronizationSituationDescription().add(desc);

        OperationResult result = new OperationResult("sync desc test");
        String oid = repositoryService.addObject(shadowType.asPrismObject(), null, result);

        shadow = repositoryService.getObject(ShadowType.class, oid, result);
        shadowType = shadow.asObjectable();
        desc = shadowType.getSynchronizationSituationDescription().get(0);
        AssertJUnit.assertEquals("Times don't match", TIME, XMLGregorianCalendarType.asDate(desc.getTimestamp()));
    }
}
