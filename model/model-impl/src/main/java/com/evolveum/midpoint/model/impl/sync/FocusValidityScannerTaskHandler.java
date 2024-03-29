/*
 * Copyright (c) 2010-2014 Evolveum
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
package com.evolveum.midpoint.model.impl.sync;

import com.evolveum.midpoint.model.api.PolicyViolationException;
import com.evolveum.midpoint.model.impl.ModelConstants;
import com.evolveum.midpoint.model.impl.lens.Clockwork;
import com.evolveum.midpoint.model.impl.lens.ContextFactory;
import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.model.impl.util.AbstractScannerResultHandler;
import com.evolveum.midpoint.model.impl.util.AbstractScannerTaskHandler;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.builder.QueryBuilder;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.schema.result.OperationConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskRunResult;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.xml.datatype.XMLGregorianCalendar;

import static com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationType.F_VALID_FROM;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationType.F_VALID_TO;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType.F_ACTIVATION;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType.F_ASSIGNMENT;

/**
 * 
 * @author Radovan Semancik
 *
 */
@Component
public class FocusValidityScannerTaskHandler extends AbstractScannerTaskHandler<UserType, AbstractScannerResultHandler<UserType>> {

	// WARNING! This task handler is efficiently singleton!
	// It is a spring bean and it is supposed to handle all search task instances
	// Therefore it must not have task-specific fields. It can only contain fields specific to
	// all tasks of a specified type
	
	public static final String HANDLER_URI = ModelConstants.NS_SYNCHRONIZATION_TASK_PREFIX + "/focus-validation-scanner/handler-3";

	@Autowired(required = true)
	private ProvisioningService provisioningService;
	
	@Autowired(required = true)
	private ContextFactory contextFactory;
	
    @Autowired(required = true)
    private Clockwork clockwork;
        
	private static final transient Trace LOGGER = TraceManager.getTrace(FocusValidityScannerTaskHandler.class);

	public FocusValidityScannerTaskHandler() {
        super(UserType.class, "Focus validity scan", OperationConstants.FOCUS_VALIDITY_SCAN);
    }

	@PostConstruct
	private void initialize() {
		taskManager.registerHandler(HANDLER_URI, this);
	}

	@Override
	protected Class<? extends ObjectType> getType(Task task) {
		return UserType.class;
	}

	@Override
	protected ObjectQuery createQuery(AbstractScannerResultHandler<UserType> handler, TaskRunResult runResult, Task task, OperationResult opResult) throws SchemaException {
		ObjectQuery query = new ObjectQuery();
		ObjectFilter filter;
		PrismObjectDefinition<UserType> focusObjectDef = prismContext.getSchemaRegistry().findObjectDefinitionByCompileTimeClass(UserType.class);
		
		XMLGregorianCalendar lastScanTimestamp = handler.getLastScanTimestamp();
		XMLGregorianCalendar thisScanTimestamp = handler.getThisScanTimestamp();
		if (lastScanTimestamp == null) {
			filter = QueryBuilder.queryFor(FocusType.class, prismContext)
					.item(F_ACTIVATION, F_VALID_FROM).le(thisScanTimestamp)
					.or().item(F_ACTIVATION, F_VALID_TO).le(thisScanTimestamp)
					.or().exists(F_ASSIGNMENT)
						.block()
							.item(AssignmentType.F_ACTIVATION, F_VALID_FROM).le(thisScanTimestamp)
							.or().item(AssignmentType.F_ACTIVATION, F_VALID_TO).le(thisScanTimestamp)
						.endBlock()
					.buildFilter();
		} else {
			filter = QueryBuilder.queryFor(FocusType.class, prismContext)
					.item(F_ACTIVATION, F_VALID_FROM).gt(lastScanTimestamp)
						.and().item(F_ACTIVATION, F_VALID_FROM).le(thisScanTimestamp)
					.or().item(F_ACTIVATION, F_VALID_TO).gt(lastScanTimestamp)
						.and().item(F_ACTIVATION, F_VALID_TO).le(thisScanTimestamp)
					.or().exists(F_ASSIGNMENT)
						.block()
							.item(AssignmentType.F_ACTIVATION, F_VALID_FROM).gt(lastScanTimestamp)
								.and().item(AssignmentType.F_ACTIVATION, F_VALID_FROM).le(thisScanTimestamp)
							.or().item(AssignmentType.F_ACTIVATION, F_VALID_TO).gt(lastScanTimestamp)
								.and().item(AssignmentType.F_ACTIVATION, F_VALID_TO).le(thisScanTimestamp)
						.endBlock()
					.buildFilter();
		}
		
		query.setFilter(filter);
		return query;
	}
	
	@Override
	protected AbstractScannerResultHandler<UserType> createHandler(TaskRunResult runResult, final Task coordinatorTask,
			OperationResult opResult) {
		
		AbstractScannerResultHandler<UserType> handler = new AbstractScannerResultHandler<UserType>(
				coordinatorTask, FocusValidityScannerTaskHandler.class.getName(), "recompute", "recompute task", taskManager) {
			@Override
			protected boolean handleObject(PrismObject<UserType> user, Task workerTask, OperationResult result) throws CommonException {
				recomputeUser(user, workerTask, result);
				return true;
			}
		};
        handler.setStopOnError(false);
		return handler;
	}

	private void recomputeUser(PrismObject<UserType> user, Task workerTask, OperationResult result) throws SchemaException,
			ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ObjectAlreadyExistsException, 
			ConfigurationException, PolicyViolationException, SecurityViolationException {
		LOGGER.trace("Recomputing user {}", user);

		LensContext<UserType> syncContext = contextFactory.createRecomputeContext(user, workerTask, result);
		LOGGER.trace("Recomputing of user {}: context:\n{}", user, syncContext.debugDump());
		clockwork.run(syncContext, workerTask, result);
		LOGGER.trace("Recomputing of user {}: {}", user, result.getStatus());
	}
	
}
