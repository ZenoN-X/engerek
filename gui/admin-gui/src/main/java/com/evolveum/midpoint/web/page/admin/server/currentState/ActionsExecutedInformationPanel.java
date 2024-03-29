/*
 * Copyright (c) 2010-2015 Evolveum
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

package com.evolveum.midpoint.web.page.admin.server.currentState;

import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.util.SimplePanel;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxFallbackLink;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;

import java.util.List;

/**
 * @author mederly
 */
public class ActionsExecutedInformationPanel extends SimplePanel<ActionsExecutedInformationDto> {

    private static final Trace LOGGER = TraceManager.getTrace(ActionsExecutedInformationPanel.class);

    private static final String ID_OBJECT_TABLE_LINES = "objectsTableLines";
    private static final String ID_OBJECT_TYPE = "objectType";
    private static final String ID_OPERATION = "operation";
    private static final String ID_CHANNEL = "channel";
    private static final String ID_SUCCESS_COUNT = "successCount";
    private static final String ID_LAST_SUCCESS_OBJECT = "lastSuccessObject";
    private static final String ID_LAST_SUCCESS_TIMESTAMP = "lastSuccessTimestamp";
    private static final String ID_FAILURE_COUNT = "failureCount";
    private static final String ID_SHOW_RESULTING_ACTIONS_ONLY_LABEL = "showResultingActionsOnlyLabel";
    private static final String ID_SHOW_RESULTING_ACTIONS_ONLY_LINK = "showResultingActionsOnlyLink";

    public ActionsExecutedInformationPanel(String id, IModel<ActionsExecutedInformationDto> model) {
        super(id, model);
    }

    boolean showResultingActionsOnly = true;

    @Override
    protected void initLayout() {

        ListView tableLines = new ListView<ActionsExecutedObjectsTableLineDto>(ID_OBJECT_TABLE_LINES,
                new AbstractReadOnlyModel<List<? extends ActionsExecutedObjectsTableLineDto>>() {
                    @Override
                    public List<? extends ActionsExecutedObjectsTableLineDto> getObject() {
                        if (showResultingActionsOnly) {
                            return getModelObject().getUniqueObjectsTableLines();
                        } else {
                            return getModelObject().getObjectsTableLines();
                        }
                    }
                }
        ) {
            protected void populateItem(final ListItem<ActionsExecutedObjectsTableLineDto> item) {
                item.add(new Label(ID_OBJECT_TYPE, new AbstractReadOnlyModel<String>() {
                    @Override
                    public String getObject() {
                        String key = item.getModelObject().getObjectTypeLocalizationKey();
                        if (key != null) {
                            return createStringResource(key).getString();
                        } else {
                            return item.getModelObject().getObjectType().getLocalPart();
                        }
                    }
                }));
                item.add(new Label(ID_OPERATION, new AbstractReadOnlyModel<String>() {
                    @Override
                    public String getObject() {
                        return createStringResource(item.getModelObject().getOperation()).getString();
                    }
                }));
                item.add(new Label(ID_CHANNEL, new AbstractReadOnlyModel<String>() {
                    @Override
                    public String getObject() {
                        String channel = item.getModelObject().getChannel();
                        if (channel != null && !channel.isEmpty()) {
                            String key = "Channel." + channel;
                            return createStringResource(key).getString();
                        } else {
                            return "";
                        }
                    }
                }));

                item.add(new Label(ID_SUCCESS_COUNT, new PropertyModel<String>(item.getModel(), ActionsExecutedObjectsTableLineDto.F_SUCCESS_COUNT)));
                item.add(new Label(ID_LAST_SUCCESS_OBJECT, new PropertyModel<String>(item.getModel(), ActionsExecutedObjectsTableLineDto.F_LAST_SUCCESS_OBJECT)));
                item.add(new Label(ID_LAST_SUCCESS_TIMESTAMP, new PropertyModel<String>(item.getModel(), ActionsExecutedObjectsTableLineDto.F_LAST_SUCCESS_TIMESTAMP)));
                item.add(new Label(ID_FAILURE_COUNT, new PropertyModel<String>(item.getModel(), ActionsExecutedObjectsTableLineDto.F_FAILURE_COUNT)));
            }
        };
        add(tableLines);

        add(new Label(ID_SHOW_RESULTING_ACTIONS_ONLY_LABEL, new AbstractReadOnlyModel<String>() {
            @Override
            public String getObject() {
                return showResultingActionsOnly ?
                        createStringResource("ActionsExecutedInformationPanel.showingResultingActionsOnly").getString() :
                        createStringResource("ActionsExecutedInformationPanel.showingAllActions").getString();
            }
        }));
        add(new AjaxFallbackLink<String>(ID_SHOW_RESULTING_ACTIONS_ONLY_LINK) {
            @Override
            public void onClick(AjaxRequestTarget ajaxRequestTarget) {
                showResultingActionsOnly = !showResultingActionsOnly;
                ajaxRequestTarget.add(this);
            }
        });
    }

    public boolean isShowResultingActionsOnly() {
        return showResultingActionsOnly;
    }

    public void setShowResultingActionsOnly(boolean showResultingActionsOnly) {
        this.showResultingActionsOnly = showResultingActionsOnly;
    }
}
