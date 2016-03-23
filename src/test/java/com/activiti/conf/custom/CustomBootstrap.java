/**
 * Copyright 2005-2015 Alfresco Software, Ltd. All rights reserved.
 * License rights for this program may be obtained from Alfresco Software, Ltd.
 * pursuant to a written agreement and any use of this program without such an
 * agreement is prohibited.
 */
package com.activiti.conf.custom;

import com.activiti.api.boot.BootstrapConfigurer;
import com.activiti.domain.editor.AppModelDefinition;
import com.activiti.domain.editor.Model;
import com.activiti.domain.idm.Capabilities;
import com.activiti.domain.idm.User;
import com.activiti.model.editor.AppDefinition;
import com.activiti.model.editor.kickstart.ChoiceContainer;
import com.activiti.model.editor.kickstart.ChoiceStepDefinition;
import com.activiti.model.editor.kickstart.FlowCondition;
import com.activiti.model.editor.kickstart.HumanStepDefinition;
import com.activiti.model.editor.kickstart.KickstartModelDefinition;
import com.activiti.model.editor.kickstart.StepDefinition;
import com.activiti.model.editor.kickstart.StepDefinitionParent;
import com.activiti.repository.editor.ModelRepository;
import com.activiti.repository.idm.UserRepository;
import com.activiti.service.api.DeploymentService;
import com.activiti.service.editor.KickstartModelService;
import com.activiti.service.editor.KickstartModelStorageHandler;
import com.activiti.service.editor.ModelInternalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Import sample processes
 *
 * @author Will Abson
 */
@Component
public class CustomBootstrap implements BootstrapConfigurer {

    private final Logger log = LoggerFactory.getLogger(CustomBootstrap.class);

    public void applicationContextInitialized(ApplicationContext applicationContext) {

        // Need to be done in a separate TX, otherwise the LDAP sync won't see the created app
        // Can't use @Transactional here, cause it seems not to be applied as wanted

        transactionTemplate.execute(new TransactionCallback<Void>() {

            public Void doInTransaction(TransactionStatus status) {
                List<Model> dataAppModels = modelRepository.findModelsByModelTypeAndReferenceIdOrNullReferenceId(Model.MODEL_TYPE_APP, 1L);
                if (CollectionUtils.isNotEmpty(dataAppModels)) {
                    log.info("The database already contains an app so skipping Custom App initialization");
                    return null;
                }

                User adminUser = null;
                List<User> userResults = userRepository.findUsersWithCapability(Capabilities.TENANT_MGMT);
                if (userResults.size() == 0) {
                    userResults = userRepository.findUsersWithCapability(Capabilities.TENANT_ADMIN);
                }
                if (userResults.size() > 0) {
                    adminUser = userResults.get(0);
                }

                if (adminUser == null) {
                    log.error("Could not find admin user so skipping Custom App initialization");
                    return null;
                }

                AppDefinition appDefinition = new AppDefinition();
                appDefinition.setIcon("glyphicon-file");
                appDefinition.setTheme("theme-8");

                List<AppModelDefinition> appModels = new ArrayList<AppModelDefinition>();

                List<ModelJsonAndStepIdRelation> modelRelationList = new ArrayList<ModelJsonAndStepIdRelation>();
                modelRelationList.add(new ModelJsonAndStepIdRelation("Order form", "form-models/begin-order-5001.json"));
                Model adhocModel = createProcessModelAndUpdateIds(modelRelationList, "Fruit order process", "process-models/order-process-5000.json", adminUser);
                appModels.add(createAppModelDefinition(adhocModel));

                appDefinition.setModels(appModels);

                Model appModel = new Model();
                appModel.setVersion(1);
                appModel.setName("Fruit Orders");
                appModel.setModelType(Model.MODEL_TYPE_APP);
                appModel.setCreated(new Date());
                appModel.setCreatedBy(adminUser);
                appModel.setDescription("Fruit Orders app");
                appModel.setLastUpdated(new Date());
                appModel.setLastUpdatedBy(adminUser);
                appModel.setTenantId(adminUser.getTenantId());

                try {
                    appModel.setModelEditorJson(objectMapper.writeValueAsString(appDefinition));
                } catch (Exception e) {
                    log.error("Error creating app definition", e);
                    return null;
                }
                modelRepository.save(appModel);

                modelService.createNewModelVersion(appModel, "Initial setup", adminUser);
                List<Long> modelIds = new ArrayList<Long>();
                modelIds.add(appModel.getId());
                deploymentService.deployAppDefinitions(modelIds, adminUser);

                return null;
            }
        });

    }

    @Autowired
    @Qualifier("activitiTransactionTemplate")
    protected TransactionTemplate transactionTemplate;

    @Autowired
    protected ModelInternalService modelService;

    @Autowired
    protected DeploymentService deploymentService;

    @Autowired
    protected KickstartModelService kickstartModelService;

    @Autowired
    protected KickstartModelStorageHandler storageHandler;

    @Autowired
    protected ModelRepository modelRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ObjectMapper objectMapper;

    protected Model createProcessModelAndUpdateIds(List<ModelJsonAndStepIdRelation> models, String processName, String processJsonFileName, User adminUser) {
        Map<Long, Model> modelIdMap = new HashMap<Long, Model>();
        for (ModelJsonAndStepIdRelation modelRelation : models) {
            Model formModel = createModelArtifact(modelRelation.getName(), modelRelation.getJsonFileName(), Model.MODEL_TYPE_FORM, adminUser);
            modelIdMap.put(modelRelation.getModelId(), formModel);
        }

        Model processModel;
        try {
            String processJson = getModelAsString(processJsonFileName);
            byte[] thumbnail = getThumbnailBytes(processJsonFileName);
            KickstartModelDefinition modelDefinition = kickstartModelService.getModelDefinition(processJson, true);
            if (modelDefinition.getStartForm() != null && modelIdMap.containsKey(modelDefinition.getStartForm().getId())) {
                modelDefinition.getStartForm().setId(modelIdMap.get(modelDefinition.getStartForm().getId()).getId());
            }
            updateFormReferences(modelDefinition.getSteps(), modelIdMap);
            processJson = storageHandler.getDefinitionStorageString(modelDefinition);
            processModel = createModelArtifactWithJson(processName, processJson, thumbnail, Model.MODEL_TYPE_KICKSTART, adminUser);
            for (Model formModel : modelIdMap.values()) {
                formModel.setReferenceId(processModel.getId());
                modelRepository.save(formModel);
            }
            return processModel;

        } catch (Exception e) {
            log.error("Error creating adhoc process model", e);
            return null;
        }
    }

    protected void updateFormReferences(List<StepDefinition> steps, Map<Long, Model> modelIdMap) {
        for (StepDefinition stepDefinition : steps) {
            if (stepDefinition instanceof HumanStepDefinition) {
                HumanStepDefinition humanStep = (HumanStepDefinition) stepDefinition;
                if (humanStep.getFormDefinition() != null && modelIdMap.containsKey(humanStep.getFormDefinition().getId())) {
                    humanStep.getFormDefinition().setId(modelIdMap.get(humanStep.getFormDefinition().getId()).getId());
                }

                if (CollectionUtils.isNotEmpty(humanStep.getOverdueSteps())) {
                    updateFormReferences(humanStep.getOverdueSteps(), modelIdMap);
                }

            } else if (stepDefinition instanceof StepDefinitionParent) {

                if (stepDefinition instanceof ChoiceStepDefinition) {
                    ChoiceStepDefinition choiceStep = (ChoiceStepDefinition) stepDefinition;
                    List<ChoiceContainer> choiceContainers = choiceStep.getChoices();
                    if (CollectionUtils.isNotEmpty(choiceContainers)) {
                        for (ChoiceContainer choiceContainer : choiceContainers) {
                            if (choiceContainer.getCondition() != null) {
                                FlowCondition condition = choiceContainer.getCondition();
                                if (condition.getOutcomeFormId() != null && modelIdMap.containsKey(Long.valueOf(condition.getOutcomeFormId()))) {
                                    condition.setOutcomeFormId(modelIdMap.get(Long.valueOf(condition.getOutcomeFormId())).getId().toString());
                                }
                            }
                        }
                    }
                }

                List<StepDefinition> childSteps = ((StepDefinitionParent) stepDefinition).getChildSteps();
                if (CollectionUtils.isNotEmpty(childSteps)) {
                    updateFormReferences(childSteps, modelIdMap);
                }
            }
        }
    }

    protected Model createModelArtifact(String name, String filename, int modelType, User adminUser) {
        try {
            String json = getModelAsString(filename);
            byte[] thumbnail = getThumbnailBytes(filename);
            return createModelArtifactWithJson(name, json, thumbnail, modelType, adminUser);
        } catch (Exception e) {
            log.error("Error creating model artifact definition " + filename, e);
            return null;
        }
    }

    protected String getModelAsString(String filename) throws IOException {
        return IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream(filename));
    }

    protected byte[] getThumbnailBytes(String filename) throws IOException {
        byte[] thumbnail = null;
        InputStream thumbnailInputStream = this.getClass().getClassLoader().getResourceAsStream(filename.replace(".json", ".png"));
        if (thumbnailInputStream != null) {
            thumbnail = IOUtils.toByteArray(thumbnailInputStream);
        }
        return thumbnail;
    }

    protected Model createModelArtifactWithJson(String name, String json, byte[] thumbnail, int modelType, User user) {
        Model model = new Model();
        model.setVersion(1);
        model.setName(name);
        model.setModelType(modelType);
        model.setCreated(new Date());
        model.setCreatedBy(user);
        model.setLastUpdated(new Date());
        model.setLastUpdatedBy(user);
        model.setModelEditorJson(json);
        model.setThumbnail(thumbnail);
        model.setTenantId(user.getTenantId());
        return modelService.saveModel(model);
    }

    protected AppModelDefinition createAppModelDefinition(Model model) {
        AppModelDefinition appModelDef = new AppModelDefinition();
        appModelDef.setId(model.getId());
        appModelDef.setName(model.getName());
        appModelDef.setModelType(model.getModelType());
        appModelDef.setCreatedBy(model.getCreatedBy().getId());
        appModelDef.setCreatedByFullName(model.getCreatedBy().getFullName());
        appModelDef.setLastUpdated(model.getLastUpdated());
        appModelDef.setLastUpdatedBy(model.getLastUpdatedBy().getId());
        appModelDef.setLastUpdatedByFullName(model.getLastUpdatedBy().getFullName());
        appModelDef.setVersion(model.getVersion());
        return appModelDef;
    }

    class ModelJsonAndStepIdRelation {

        protected String name;
        protected String jsonFileName;
        protected Long modelId;

        public ModelJsonAndStepIdRelation(String name, String jsonFileName) {
            this.name = name;
            this.jsonFileName = jsonFileName;
            readModelId();
        }

        public String getName() {
            return name;
        }

        public String getJsonFileName() {
            return jsonFileName;
        }

        public Long getModelId() {
            return modelId;
        }

        protected void readModelId() {
            String modelIdString = jsonFileName.substring(jsonFileName.lastIndexOf("-") + 1, jsonFileName.lastIndexOf("."));
            modelId = Long.valueOf(modelIdString);
        }
    }
}