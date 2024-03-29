/*
 * Copyright (c) 2010-2014 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.model.impl.scripting.actions;

import com.evolveum.midpoint.model.impl.scripting.Data;
import com.evolveum.midpoint.model.impl.scripting.ExecutionContext;
import com.evolveum.midpoint.model.api.ScriptExecutionException;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.schema.DeltaConvertor;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.model.scripting_3.ActionExpressionType;
import com.evolveum.midpoint.xml.ns._public.model.scripting_3.ActionParameterValueType;
import com.evolveum.prism.xml.ns._public.types_3.ChangeTypeType;
import com.evolveum.prism.xml.ns._public.types_3.ObjectDeltaType;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @author mederly
 */
@Component
public class ModifyExecutor extends BaseActionExecutor {

    private static final Trace LOGGER = TraceManager.getTrace(ModifyExecutor.class);

    private static final String NAME = "modify";
    private static final String PARAM_DELTA = "delta";

    @PostConstruct
    public void init() {
        scriptingExpressionEvaluator.registerActionExecutor(NAME, this);
    }

    @Override
    public Data execute(ActionExpressionType expression, Data input, ExecutionContext context, OperationResult result) throws ScriptExecutionException {

        boolean raw = getParamRaw(expression, input, context, result);

        ActionParameterValueType deltaParameterValue = expressionHelper.getArgument(expression.getParameter(), PARAM_DELTA, true, true, NAME);
        Data deltaData = expressionHelper.evaluateParameter(deltaParameterValue, input, context, result);

        for (Item item : input.getData()) {
            if (item instanceof PrismObject) {
                PrismObject<? extends ObjectType> prismObject = (PrismObject) item;
                ObjectType objectType = prismObject.asObjectable();
                long started = operationsHelper.recordStart(context, objectType);
                try {
                    operationsHelper.applyDelta(createDelta(objectType, deltaData), operationsHelper.createExecutionOptions(raw), context, result);
                    operationsHelper.recordEnd(context, objectType, started, null);
                } catch (Throwable ex) {
                    operationsHelper.recordEnd(context, objectType, started, ex);
                    throw ex;
                }
                context.println("Modified " + item.toString() + rawSuffix(raw));
            } else {
                throw new ScriptExecutionException("Item could not be modified, because it is not a PrismObject: " + item.toString());
            }
        }
        return Data.createEmpty();
    }

    private ObjectDelta createDelta(ObjectType objectType, Data deltaData) throws ScriptExecutionException {
        if (deltaData.getData().size() != 1) {
            throw new ScriptExecutionException("Expected exactly one delta to apply, found "  + deltaData.getData().size() + " instead.");
        }
        ObjectDeltaType deltaType = ((PrismProperty<ObjectDeltaType>) deltaData.getData().get(0)).getAnyRealValue();
        if (deltaType.getChangeType() == null) {
            deltaType.setChangeType(ChangeTypeType.MODIFY);
        }
        if (deltaType.getOid() == null && deltaType.getChangeType() != ChangeTypeType.ADD) {
            deltaType.setOid(objectType.getOid());
        }
        if (deltaType.getObjectType() == null) {
            if (objectType.asPrismObject().getDefinition() == null) {
                throw new ScriptExecutionException("No definition for prism object " + objectType);
            }
            deltaType.setObjectType(objectType.asPrismObject().getDefinition().getTypeName());
        }
        try {
            return DeltaConvertor.createObjectDelta(deltaType, prismContext);
        } catch (SchemaException e) {
            throw new ScriptExecutionException("Couldn't process delta due to schema exception", e);
        }
    }
}
