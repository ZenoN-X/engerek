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
package com.evolveum.midpoint.web.page.admin.reports;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.dom.PrismDomProcessor;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.Holder;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.AjaxSubmitButton;
import com.evolveum.midpoint.web.component.TabbedPanel;
import com.evolveum.midpoint.web.component.util.LoadableModel;
import com.evolveum.midpoint.web.component.util.SimplePanel;
import com.evolveum.midpoint.web.page.admin.configuration.dto.ResourceItemDto;
import com.evolveum.midpoint.web.page.admin.dto.ObjectViewDto;
import com.evolveum.midpoint.web.page.admin.home.PageDashboard;
import com.evolveum.midpoint.web.page.admin.reports.component.*;
import com.evolveum.midpoint.web.page.admin.reports.dto.AuditReportDto;
import com.evolveum.midpoint.web.page.admin.reports.dto.ReconciliationReportDto;
import com.evolveum.midpoint.web.page.admin.reports.dto.ReportDto;
import com.evolveum.midpoint.web.page.admin.reports.dto.UserReportDto;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;
import com.evolveum.midpoint.web.util.WebMiscUtil;
import com.evolveum.midpoint.web.util.WebModelUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ExportType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ReportType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.RoleType;
import com.evolveum.prism.xml.ns._public.types_2.PolyStringType;
import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.tabs.AbstractTab;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.util.string.StringValue;

import javax.swing.text.html.ObjectView;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *  @author shood
 * */
public class PageReport<T extends Serializable> extends PageAdminReports{

    private static Trace LOGGER = TraceManager.getTrace(PageReport.class);

    private static final String DOT_CLASS = PageReport.class.getName() + ".";
    private static final String OPERATION_LOAD_RESOURCES = DOT_CLASS + "loadResources";
    private static final String OPERATION_LOAD_REPORT = DOT_CLASS + "loadReport";
    private static final String OPERATION_SAVE_REPORT = DOT_CLASS + "saveReport";
    private static final String OPERATION_RUN_REPORT = DOT_CLASS + "runReport";

    private static final String ID_MAIN_FORM = "mainForm";
    private static final String ID_TAB_PANEL = "tabPanel";
    private static final String ID_SAVE_RUN_BUTTON = "runSave";
    private static final String ID_SAVE_BUTTON = "save";
    private static final String ID_CANCEL_BUTTON = "cancel";

    private LoadableModel<ReportDto> model;
    IModel<List<ResourceItemDto>> resources = new Model();

    public PageReport(){
        model = new LoadableModel<ReportDto>(false) {

            @Override
            protected ReportDto load() {
                return loadReport();
            }
        };

        initLayout();
    }

    @Override
    protected IModel<String> createPageTitleModel(){
        return new LoadableModel<String>(false) {

            @Override
            protected String load() {
                return new StringResourceModel("pageReport.title", PageReport.this, null, null).getString();
            }
        };
    }

    private ReportDto loadReport(){
        StringValue reportOid = getPageParameters().get(OnePageParameterEncoder.PARAMETER);

        ReportDto dto = null;

        OperationResult result = new OperationResult(OPERATION_LOAD_REPORT);
        PrismObject<ReportType> prismReport = WebModelUtils.loadObject(ReportType.class, reportOid.toString(), result, this);

        try{
            ReportType report = prismReport.asObjectable();

            PrismDomProcessor domProcessor = getPrismContext().getPrismDomProcessor();
            String xml = domProcessor.serializeObjectToString(prismReport);
            dto = new ReportDto(report.getName().getNorm(), report.getDescription(), xml, report.getReportExport());
            result.recordSuccess();
        } catch (Exception e){
            result.recordFatalError("Couldn't load report from repository.", e);
            LoggingUtils.logException(LOGGER, "Couldn't load report from repository.", e);
        }

        return dto;
    }

    private void initLayout(){
        Form mainForm = new Form(ID_MAIN_FORM);
        add(mainForm);

        List<ITab> tabs = new ArrayList<ITab>();
        tabs.add(new AbstractTab(createStringResource("pageReport.tab.panelConfig")) {

            @Override
            public WebMarkupContainer getPanel(String panelId) {
                return initEditingPanel(panelId);
            }
        });

        tabs.add(new AbstractTab(createStringResource("pageReport.tab.aceEditor")) {

            @Override
            public WebMarkupContainer getPanel(String panelId) {
                return initAceEditorPanel(panelId);
            }
        });

        mainForm.add(new TabbedPanel(ID_TAB_PANEL, tabs));

        initButtons(mainForm);
    }

    private void initButtons(Form mainForm){
        AjaxSubmitButton saveAndRun = new AjaxSubmitButton(ID_SAVE_RUN_BUTTON, createStringResource("PageBase.button.saveAndRun")) {

            @Override
            protected void onError(AjaxRequestTarget target, Form<?> form) {
                target.add(getFeedbackPanel());
            }

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                onSaveAndRunPerformed(target);
            }
        };
        mainForm.add(saveAndRun);

        AjaxSubmitButton save = new AjaxSubmitButton(ID_SAVE_BUTTON, createStringResource("PageBase.button.save")) {

            @Override
            protected void onError(AjaxRequestTarget target, Form<?> form) {
                target.add(getFeedbackPanel());
            }

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                onSavePerformed(target);
            }
        };
        mainForm.add(save);

        AjaxSubmitButton cancel = new AjaxSubmitButton(ID_CANCEL_BUTTON, createStringResource("PageBase.button.cancel")) {

            @Override
            protected void onError(AjaxRequestTarget target, Form<?> form) {
                target.add(getFeedbackPanel());
            }

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                onCancelPerformed(target);
            }
        };
        mainForm.add(cancel);
    }

    private SimplePanel initAceEditorPanel(String panelId){

        return new AceEditorPanel<ReportDto>(panelId, model){

            @Override
            public IModel<ReportDto> getEditorModel(){
                return model;
            }

            @Override
            public String getExpression(){
                return ReportDto.F_XML;
            }

        };
    }

    private SimplePanel initEditingPanel(String panelId){
        //TODO - return dynamically generated editing panel for report
        SimplePanel editReportPanel;
        IModel editPanelModel = new Model();
        String reportType = getPageParameters().get("reportType").toString();

        if(ReportDto.Type.AUDIT.toString().equals(reportType)){
            editPanelModel.setObject(new AuditReportDto());
            editReportPanel = new AuditPopupPanel(panelId, editPanelModel);
        } else if(ReportDto.Type.RECONCILIATION.toString().equals(reportType)){
            editPanelModel.setObject(new ReconciliationReportDto());
            resources.setObject(loadResources());
            editReportPanel = new ReconciliationPopupPanel(panelId, editPanelModel, resources);
        } else if(ReportDto.Type.USERS.toString().equals(reportType)){
            editPanelModel.setObject(new UserReportDto());
            editReportPanel = new UserReportConfigPanel(panelId, editPanelModel);
        } else {
            editReportPanel = new DefaultReportPanel(panelId, model);
        }

        return editReportPanel;
    }

    private List<ResourceItemDto> loadResources() {
        List<ResourceItemDto> resources = new ArrayList<ResourceItemDto>();

        OperationResult result = new OperationResult(OPERATION_LOAD_RESOURCES);
        try {
            List<PrismObject<ResourceType>> objects = getModelService().searchObjects(ResourceType.class, null, null,
                    createSimpleTask(OPERATION_LOAD_RESOURCES), result);

            if (objects != null) {
                for (PrismObject<ResourceType> object : objects) {
                    resources.add(new ResourceItemDto(object.getOid(), WebMiscUtil.getName(object)));
                }
            }
        } catch (Exception ex) {
            LoggingUtils.logException(LOGGER, "Couldn't load resources", ex);
            result.recordFatalError("Couldn't load resources, reason: " + ex.getMessage(), ex);
        } finally {
            if (result.isUnknown()) {
                result.recomputeStatus();
            }
        }

        Collections.sort(resources);

        if (!WebMiscUtil.isSuccessOrHandledError(result)) {
            showResultInSession(result);
            throw new RestartResponseException(PageDashboard.class);
        }

        return resources;
    }

    protected void onSaveAndRunPerformed(AjaxRequestTarget target) {
        onSavePerformed(target);

        //TODO - add functionality to run report
    }

    //TODO - fix problems with validation
    protected void onSavePerformed(AjaxRequestTarget target) {
        ReportDto dto = model.getObject();

        if(StringUtils.isEmpty(dto.getXml())){
            error(getString("pageReport.message.emptyXml"));
            target.add(getFeedbackPanel());
            return;
        }

        OperationResult result = new OperationResult(OPERATION_SAVE_REPORT);
        Holder<PrismObject<ReportType>> objectHolder = new Holder<PrismObject<ReportType>>(null);
        validateObject(dto.getXml(), objectHolder, true, result);

        try{
            Task task = createSimpleTask(OPERATION_SAVE_REPORT);
            PrismObject<ReportType> newReport = objectHolder.getValue();

            PrismObject<ReportType> oldReport = dto.getObject();
            ObjectDelta<ReportType> delta = oldReport.diff(newReport);
            getModelService().executeChanges(WebMiscUtil.createDeltaCollection(delta), null, task, result);

        } catch (Exception e){
            result.recordFatalError("Couldn't save report.", e);
        }
        result.recomputeStatus();

        showResult(result);
        target.add(getFeedbackPanel());

        if(result.isSuccess()){
            showResultInSession(result);
            setResponsePage(PageReports.class);
        }
    }

    protected void onCancelPerformed(AjaxRequestTarget target) {
        setResponsePage(PageReports.class);
    }
}