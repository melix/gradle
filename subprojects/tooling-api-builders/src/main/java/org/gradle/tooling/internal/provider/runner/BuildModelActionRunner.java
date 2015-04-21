/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.ProjectState;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.testing.Test;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.id.UUIDGenerator;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.internal.provider.PayloadSerializer;
import org.gradle.tooling.internal.provider.TestConfiguration;
import org.gradle.tooling.model.internal.ProjectSensitiveToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.UnknownModelException;

import java.util.Arrays;

public class BuildModelActionRunner implements BuildActionRunner {
    private final UUIDGenerator generator = new UUIDGenerator();

    @Override
    public void run(BuildAction action, BuildController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return;
        }

        BuildModelAction buildModelAction = (BuildModelAction) action;
        GradleInternal gradle = buildController.getGradle();

        // register a TestListener that dispatches all test progress via the registered BuildEventConsumer instance,
        // this allows to send test progress events back to the DaemonClient (via short-cut)
        if (buildModelAction.isSendTestProgressEvents()) {
            BuildEventConsumer eventConsumer = gradle.getServices().get(BuildEventConsumer.class);
            gradle.addListener(new ClientForwardingTestListener(eventConsumer));
        }
        configureTestExecution(buildModelAction, gradle);

        if (buildModelAction.isRunTasks()) {
            buildController.run();
        } else {
            buildController.configure();
            // Currently need to force everything to be configured
            gradle.getServices().get(ProjectConfigurer.class).configureHierarchy(gradle.getRootProject());
            for (Project project : gradle.getRootProject().getAllprojects()) {
                ProjectInternal projectInternal = (ProjectInternal) project;
                projectInternal.getTasks().discoverTasks();
                projectInternal.bindAllModelRules();
            }
        }

        String modelName = buildModelAction.getModelName();
        ToolingModelBuilderRegistry builderRegistry = getToolingModelBuilderRegistry(gradle);
        ToolingModelBuilder builder;
        try {
            builder = builderRegistry.getBuilder(modelName);
        } catch (UnknownModelException e) {
            throw (InternalUnsupportedModelException) new InternalUnsupportedModelException().initCause(e);
        }

        Object result;
        if (builder instanceof ProjectSensitiveToolingModelBuilder) {
            result = ((ProjectSensitiveToolingModelBuilder) builder).buildAll(modelName, gradle.getDefaultProject(), true);
        } else {
            result = builder.buildAll(modelName, gradle.getDefaultProject());
        }

        PayloadSerializer payloadSerializer = gradle.getServices().get(PayloadSerializer.class);
        BuildActionResult buildActionResult = new BuildActionResult(payloadSerializer.serialize(result), null);
        buildController.setResult(buildActionResult);
    }

    private void configureTestExecution(BuildModelAction buildModelAction, GradleInternal gradle) {
        final TestConfiguration testConfiguration = buildModelAction.getTestConfiguration();

        // idea: generate a task that we will depend on test tasks with the given filter
        // question: what happens if all projects do not have a test task, or that those
        // projects do not match the filter? Can we add project path(s) to test configuration?

        final String taskName = "toolingTestExecution" + generator.generateId();
        if (testConfiguration != null) {
            gradle.addProjectEvaluationListener(new ProjectEvaluationListener() {
                @Override
                public void beforeEvaluate(Project project) {

                }

                @Override
                public void afterEvaluate(Project project, ProjectState state) {
                    Task toolingTestExecution = project.task(taskName);

                    TaskCollection<Test> testTaskCollection = project.getTasks().withType(Test.class);
                    String[] includePatterns = testConfiguration.getIncludePatterns();
                    for (Test test : testTaskCollection) {
                        toolingTestExecution.dependsOn(test);
                        test.getFilter().setIncludePatterns(includePatterns);
                    }
                }
            });
            buildModelAction.getStartParameter().setTaskNames(Arrays.asList(taskName));
        }
    }

    private ToolingModelBuilderRegistry getToolingModelBuilderRegistry(GradleInternal gradle) {
        return gradle.getDefaultProject().getServices().get(ToolingModelBuilderRegistry.class);
    }
}
