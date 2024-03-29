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
package com.evolveum.midpoint.web.component.assignment;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ContainerDelta;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.path.ItemPathSegment;
import com.evolveum.midpoint.prism.query.InOidFilter;
import com.evolveum.midpoint.prism.query.NotFilter;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.dialog.ConfirmationDialog;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenu;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItemAction;
import com.evolveum.midpoint.web.component.util.SimplePanel;
import com.evolveum.midpoint.web.page.admin.users.component.*;
import com.evolveum.midpoint.web.page.admin.users.dto.UserDtoStatus;
import com.evolveum.midpoint.web.util.WebMiscUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxCheckBox;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import javax.xml.namespace.QName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author shood
 */
public class AssignmentTablePanel<T extends ObjectType> extends SimplePanel<List<AssignmentEditorDto>> {

	private static final Trace LOGGER = TraceManager.getTrace(AssignmentTablePanel.class);

	private static final String DOT_CLASS = AssignmentTablePanel.class.getName() + ".";
	private static final String OPERATION_LOAD_ASSIGNMENTS = DOT_CLASS + "loadAssignments";
	private static final String OPERATION_LOAD_ASSIGNMENT = DOT_CLASS + "loadAssignment";

	private static final String ID_ASSIGNMENTS = "assignments";
	private static final String ID_CHECK_ALL = "assignmentsCheckAll";
	private static final String ID_HEADER = "assignmentsHeader";
	private static final String ID_MENU = "assignmentsMenu";
	private static final String ID_LIST = "assignmentList";
	private static final String ID_ROW = "assignmentEditor";
	private static final String ID_MODAL_ASSIGN = "assignablePopup";
	private static final String ID_MODAL_ASSIGN_ORG = "assignableOrgPopup";

	private static final String ID_MODAL_DELETE_ASSIGNMENT = "deleteAssignmentPopup";

	AssignableSelectionPanel.Context assignableSelectionContext;
	AbstractAssignableSelectionPanel.Context assignableOrgSelectionContext;

	public AssignmentTablePanel(String id, IModel<String> label,
			IModel<List<AssignmentEditorDto>> assignmentModel) {
		super(id, assignmentModel);

		initPanelLayout(label);
	}

	public List<AssignmentType> getAssignmentTypeList() {
		return null;
	}

	public String getExcludeOid() {
		return null;
	}

	private IModel<List<AssignmentEditorDto>> getAssignmentModel() {
		return getModel();
	}

	private void initPanelLayout(IModel<String> labelText) {
		final WebMarkupContainer assignments = new WebMarkupContainer(ID_ASSIGNMENTS);
		assignments.setOutputMarkupId(true);
		add(assignments);

		Label label = new Label(ID_HEADER, labelText);
		assignments.add(label);

		InlineMenu assignmentMenu = new InlineMenu(ID_MENU, new Model((Serializable) createAssignmentMenu()));
		assignments.add(assignmentMenu);

		ListView<AssignmentEditorDto> list = new ListView<AssignmentEditorDto>(ID_LIST, getModel()) {

			@Override
			protected void populateItem(ListItem<AssignmentEditorDto> item) {
				AssignmentEditorPanel editor = new AssignmentEditorPanel(ID_ROW, item.getModel());
				item.add(editor);
			}
		};
		list.setOutputMarkupId(true);
		assignments.add(list);

		AjaxCheckBox checkAll = new AjaxCheckBox(ID_CHECK_ALL, new Model()) {

			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				List<AssignmentEditorDto> assignmentEditors = getAssignmentModel().getObject();

				for (AssignmentEditorDto dto : assignmentEditors) {
					dto.setSelected(this.getModelObject());
				}

				target.add(assignments);
			}
		};
		assignments.add(checkAll);

		initModalWindows();
	}

	private void initModalWindows() {

		ModalWindow assignWindowOrg = new ModalWindow(ID_MODAL_ASSIGN_ORG);
		assignableOrgSelectionContext = new AbstractAssignableSelectionPanel.Context(this) {

			@Override
			public AssignmentTablePanel getRealParent() {
				return WebMiscUtil.theSameForPage(AssignmentTablePanel.this, getCallingPageReference());
			}

			@Override
			protected void addPerformed(AjaxRequestTarget target, List<ObjectType> selected) {
				getRealParent().addSelectedAssignablePerformed(target, selected, ID_MODAL_ASSIGN_ORG);
			}

			@Override
			public ObjectQuery getProviderQuery() {
				String excludeOid = getRealParent().getExcludeOid();
				if (excludeOid == null) {
					return null;
				} else {
					ObjectQuery query = new ObjectQuery();
					List<String> oids = new ArrayList<>();
					oids.add(excludeOid);

					ObjectFilter oidFilter = InOidFilter.createInOid(oids);
					query.setFilter(NotFilter.createNot(oidFilter));
					return query;
				}
			}

			@Override
			protected void handlePartialError(OperationResult result) {
				getRealParent().handlePartialError(result);
			}
		};
		AssignableOrgSelectionPage.prepareDialog(assignWindowOrg, assignableOrgSelectionContext, this, "AssignmentTablePanel.modal.title.selectAssignment", ID_ASSIGNMENTS);
		add(assignWindowOrg);


		ModalWindow assignWindow = new ModalWindow(ID_MODAL_ASSIGN);
		assignableSelectionContext = new AssignableSelectionPanel.Context(this) {

			@Override
			public AssignmentTablePanel getRealParent() {
				return WebMiscUtil.theSameForPage(AssignmentTablePanel.this, getCallingPageReference());
			}

			@Override
			protected void addPerformed(AjaxRequestTarget target, List<ObjectType> selected) {
				getRealParent().addSelectedAssignablePerformed(target, selected, ID_MODAL_ASSIGN);
			}

			@Override
			public ObjectQuery getProviderQuery() {
				String excludeOid = getRealParent().getExcludeOid();
				if (excludeOid == null) {
					return null;
				} else {
					ObjectQuery query = new ObjectQuery();
					List<String> oids = new ArrayList<>();
					oids.add(excludeOid);

					ObjectFilter oidFilter = InOidFilter.createInOid(oids);
					query.setFilter(NotFilter.createNot(oidFilter));
					return query;
				}
			}

			@Override
			protected void handlePartialError(OperationResult result) {
				getRealParent().handlePartialError(result);
			}

			@Override
			public PrismObject<UserType> getUserDefinition() {
				try {
					return getRealParent().getPageBase().getSecurityEnforcer().getPrincipal().getUser().asPrismObject();
				} catch (SecurityViolationException e) {
					LOGGER.error("Could not retrieve logged user for security evaluation.", e);
				}
				return null;
			}
		};
		AssignableSelectionPage.prepareDialog(assignWindow, assignableSelectionContext, this, "AssignmentTablePanel.modal.title.selectAssignment", ID_ASSIGNMENTS);
		add(assignWindow);

		ModalWindow deleteDialog = new ConfirmationDialog(ID_MODAL_DELETE_ASSIGNMENT,
				createStringResource("AssignmentTablePanel.modal.title.confirmDeletion"),
				new AbstractReadOnlyModel<String>() {

					@Override
					public String getObject() {
						return createStringResource("AssignmentTablePanel.modal.message.delete",
								getSelectedAssignments().size()).getString();
					}
				}) {

			@Override
			public void yesPerformed(AjaxRequestTarget target) {
				close(target);
				deleteAssignmentConfirmedPerformed(target, getSelectedAssignments());
			}
		};
		add(deleteDialog);
	}

	private List<InlineMenuItem> createAssignmentMenu() {
		List<InlineMenuItem> items = new ArrayList<>();

		InlineMenuItem item = new InlineMenuItem(createStringResource("AssignmentTablePanel.menu.assign"),
				new InlineMenuItemAction() {

					@Override
					public void onClick(AjaxRequestTarget target) {
						showAssignablePopupPerformed(target, ResourceType.class, ResourceType.F_NAME);
					}
				});
		items.add(item);

		item = new InlineMenuItem(createStringResource("AssignmentTablePanel.menu.assignRole"),
				new InlineMenuItemAction() {

					@Override
					public void onClick(AjaxRequestTarget target) {
						showAssignablePopupPerformed(target, RoleType.class, RoleType.F_NAME);
					}
				});
		items.add(item);

		item = new InlineMenuItem(createStringResource("AssignmentTablePanel.menu.assignOrg"),
				new InlineMenuItemAction() {

					@Override
					public void onClick(AjaxRequestTarget target) {
						showAssignableOrgPopupPerformed(target);
					}
				});
		items.add(item);

		items.add(new InlineMenuItem());

		item = new InlineMenuItem(createStringResource("AssignmentTablePanel.menu.unassign"),
				new InlineMenuItemAction() {

					@Override
					public void onClick(AjaxRequestTarget target) {
						deleteAssignmentPerformed(target);
					}
				});
		items.add(item);
		
		item = new InlineMenuItem(createStringResource("AssignmentTablePanel.menu.showAllAssignments"),
				new InlineMenuItemAction() {

					@Override
					public void onClick(AjaxRequestTarget target) {
						showAllAssignments(target);
					}
				});
		items.add(item);

		return items;
	}

	protected void showAllAssignments(AjaxRequestTarget target){
		
	}
	
	private List<AssignmentEditorDto> getSelectedAssignments() {
		List<AssignmentEditorDto> selected = new ArrayList<>();

		List<AssignmentEditorDto> all = getAssignmentModel().getObject();

		for (AssignmentEditorDto dto : all) {
			if (dto.isSelected()) {
				selected.add(dto);
			}
		}

		return selected;
	}

	private void showModalWindow(String id, AjaxRequestTarget target) {
		ModalWindow window = (ModalWindow) get(id);
		window.show(target);
	}

	private void showAssignablePopupPerformed(AjaxRequestTarget target, Class<? extends ObjectType> type,
			QName searchParameter) {
		assignableSelectionContext.setType(type);
		assignableSelectionContext.setSearchParameter(searchParameter);
		showModalWindow(ID_MODAL_ASSIGN, target);
	}

	private void showAssignableOrgPopupPerformed(AjaxRequestTarget target) {
		assignableOrgSelectionContext.setType(OrgType.class);
		showModalWindow(ID_MODAL_ASSIGN_ORG, target);
	}

	private void deleteAssignmentPerformed(AjaxRequestTarget target) {
		List<AssignmentEditorDto> selected = getSelectedAssignments();

		if (selected.isEmpty()) {
			warn(getString("AssignmentTablePanel.message.noAssignmentSelected"));
			target.add(getPageBase().getFeedbackPanel());
			return;
		}

		showModalWindow(ID_MODAL_DELETE_ASSIGNMENT, target);
	}

	private void deleteAssignmentConfirmedPerformed(AjaxRequestTarget target,
			List<AssignmentEditorDto> toDelete) {
		List<AssignmentEditorDto> assignments = getAssignmentModel().getObject();

		for (AssignmentEditorDto assignment : toDelete) {
			if (UserDtoStatus.ADD.equals(assignment.getStatus())) {
				assignments.remove(assignment);
			} else {
				assignment.setStatus(UserDtoStatus.DELETE);
				assignment.setSelected(false);
			}
		}

		target.add(getPageBase().getFeedbackPanel(), get(ID_ASSIGNMENTS));
	}

	private void addSelectedAssignablePerformed(AjaxRequestTarget target, List<ObjectType> newAssignments,
			String popupId) {
		ModalWindow window = (ModalWindow) get(popupId);
		window.close(target);

		if (newAssignments.isEmpty()) {
			warn(getString("AssignmentTablePanel.message.noAssignmentSelected"));
			//target.add(getPageBase().getFeedbackPanel());
			return;
		}

		List<AssignmentEditorDto> assignments = getAssignmentModel().getObject();

		for (ObjectType object : newAssignments) {
			try {

				if (object instanceof ResourceType) {
					addSelectedResourceAssignPerformed((ResourceType) object);
					continue;
				}

				AssignmentEditorDtoType aType = AssignmentEditorDtoType.getType(object.getClass());

				ObjectReferenceType targetRef = new ObjectReferenceType();
				targetRef.setOid(object.getOid());
				targetRef.setType(aType.getQname());
				targetRef.setTargetName(object.getName());

				AssignmentType assignment = new AssignmentType();
				assignment.setTargetRef(targetRef);

				AssignmentEditorDto dto = new AssignmentEditorDto(UserDtoStatus.ADD, assignment,
						getPageBase());
				dto.setMinimized(false);
				dto.setShowEmpty(true);

				assignments.add(dto);
			} catch (Exception e) {
				error(getString("AssignmentTablePanel.message.couldntAssignObject", object.getName(),
						e.getMessage()));
				LoggingUtils.logException(LOGGER, "Couldn't assign object", e);
			}
		}

		//target.add(getPageBase().getFeedbackPanel(), get(ID_ASSIGNMENTS));
	}

	private void addSelectedResourceAssignPerformed(ResourceType resource) {
		AssignmentType assignment = new AssignmentType();
		ConstructionType construction = new ConstructionType();
		assignment.setConstruction(construction);

		try {
			getPageBase().getPrismContext().adopt(assignment, UserType.class,
					new ItemPath(UserType.F_ASSIGNMENT));
		} catch (SchemaException e) {
			error(getString("Could not create assignment", resource.getName(), e.getMessage()));
			LoggingUtils.logException(LOGGER, "Couldn't create assignment", e);
			return;
		}

		construction.setResource(resource);

		List<AssignmentEditorDto> assignments = getAssignmentModel().getObject();
		AssignmentEditorDto dto = new AssignmentEditorDto(UserDtoStatus.ADD, assignment, getPageBase());
		assignments.add(dto);

		dto.setMinimized(false);
		dto.setShowEmpty(true);
	}

	public void handleAssignmentsWhenAdd(PrismObject<T> object, PrismContainerDefinition assignmentDef,
			List<AssignmentType> objectAssignments) throws SchemaException {

		List<AssignmentEditorDto> assignments = getAssignmentModel().getObject();
		for (AssignmentEditorDto assDto : assignments) {
			if (!UserDtoStatus.ADD.equals(assDto.getStatus())) {
				warn(getString("AssignmentTablePanel.message.illegalAssignmentState", assDto.getStatus()));
				continue;
			}

			AssignmentType assignment = new AssignmentType();
			PrismContainerValue value = assDto.getNewValue(getPageBase().getPrismContext());
			assignment.setupContainerValue(value);
			value.applyDefinition(assignmentDef, false);
			objectAssignments.add(assignment.clone());

			// todo remove this block [lazyman] after model is updated - it has
			// to remove resource from accountConstruction
			removeResourceFromAccConstruction(assignment);
		}
	}

	public ContainerDelta handleAssignmentDeltas(ObjectDelta<T> userDelta, PrismContainerDefinition def,
			QName assignmentPath) throws SchemaException {
		ContainerDelta assDelta = new ContainerDelta(new ItemPath(), assignmentPath, def,
				def.getPrismContext()); // hoping that def contains a prism
										// context!

		// PrismObject<OrgType> org =
		// (PrismObject<OrgType>)getModel().getObject().getAssignmentParent();
		// PrismObjectDefinition orgDef = org.getDefinition();
		// PrismContainerDefinition assignmentDef =
		// def.findContainerDefinition(assignmentPath);

		List<AssignmentEditorDto> assignments = getAssignmentModel().getObject();
		for (AssignmentEditorDto assDto : assignments) {
			PrismContainerValue newValue = assDto.getNewValue(getPageBase().getPrismContext());
			switch (assDto.getStatus()) {
				case ADD:
					newValue.applyDefinition(def, false);
					assDelta.addValueToAdd(newValue.clone());
					break;
				case DELETE:
					PrismContainerValue oldValue = assDto.getOldValue();
					oldValue.applyDefinition(def);
					assDelta.addValueToDelete(oldValue.clone());
					break;
				case MODIFY:
					if (!assDto.isModified(getPageBase().getPrismContext())) {
						LOGGER.trace("Assignment '{}' not modified.", new Object[] { assDto.getName() });
						continue;
					}

					handleModifyAssignmentDelta(assDto, def, newValue, userDelta);
					break;
				default:
					warn(getString("pageUser.message.illegalAssignmentState", assDto.getStatus()));
			}
		}

		if (!assDelta.isEmpty()) {
			assDelta = userDelta.addModification(assDelta);
		}

		// todo remove this block [lazyman] after model is updated - it has to
		// remove resource from accountConstruction
		Collection<PrismContainerValue> values = assDelta.getValues(PrismContainerValue.class);
		for (PrismContainerValue value : values) {
			AssignmentType ass = new AssignmentType();
			ass.setupContainerValue(value);
			removeResourceFromAccConstruction(ass);
		}

		return assDelta;
	}

	private void handleModifyAssignmentDelta(AssignmentEditorDto assDto,
			PrismContainerDefinition assignmentDef, PrismContainerValue newValue, ObjectDelta<T> userDelta)
					throws SchemaException {
		LOGGER.debug("Handling modified assignment '{}', computing delta.",
				new Object[] { assDto.getName() });

		PrismValue oldValue = assDto.getOldValue();
		Collection<? extends ItemDelta> deltas = oldValue.diff(newValue);

		for (ItemDelta delta : deltas) {
			ItemPath deltaPath = delta.getPath().rest();
			ItemDefinition deltaDef = assignmentDef.findItemDefinition(deltaPath);

			delta.setParentPath(joinPath(oldValue.getPath(), delta.getPath().allExceptLast()));
			delta.applyDefinition(deltaDef);

			userDelta.addModification(delta);
		}
	}

	private ItemPath joinPath(ItemPath path, ItemPath deltaPath) {
		List<ItemPathSegment> newPath = new ArrayList<ItemPathSegment>();

		ItemPathSegment firstDeltaSegment = deltaPath != null ? deltaPath.first() : null;
		if (path != null) {
			for (ItemPathSegment seg : path.getSegments()) {
				if (seg.equivalent(firstDeltaSegment)) {
					break;
				}
				newPath.add(seg);
			}
		}
		if (deltaPath != null) {
			newPath.addAll(deltaPath.getSegments());
		}

		return new ItemPath(newPath);
	}

	/**
	 * remove this method after model is updated - it has to remove resource
	 * from accountConstruction
	 */
	@Deprecated
	private void removeResourceFromAccConstruction(AssignmentType assignment) {
		ConstructionType accConstruction = assignment.getConstruction();
		if (accConstruction == null || accConstruction.getResource() == null) {
			return;
		}

		ObjectReferenceType ref = new ObjectReferenceType();
		ref.setOid(assignment.getConstruction().getResource().getOid());
		ref.setType(ResourceType.COMPLEX_TYPE);
		assignment.getConstruction().setResourceRef(ref);
		assignment.getConstruction().setResource(null);
	}

	/**
	 * Override to provide handle operation for partial error during provider
	 * iterator operation.
	 */
	protected void handlePartialError(OperationResult result) {
	}
}
