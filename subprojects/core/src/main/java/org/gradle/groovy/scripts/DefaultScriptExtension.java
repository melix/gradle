/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.groovy.scripts;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.lang.MissingPropertyException;
import org.gradle.api.Action;
import org.gradle.api.AntBuilder;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.PathValidation;
import org.gradle.api.Project;
import org.gradle.api.ProjectState;
import org.gradle.api.Task;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.FileTree;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.resources.ResourceHandler;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.WorkResult;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultScriptExtension {

    private static Project getDelegate(DefaultScript script) {
        return (Project) script.getScriptTarget();
    }

    public static String absoluteProjectPath(DefaultScript script, String path) {
        return getDelegate(script).absoluteProjectPath(path);
    }

    public static ResourceHandler getResources(DefaultScript script) {
        return getDelegate(script).getResources();
    }

    public static String relativeProjectPath(DefaultScript script, String path) {
        return getDelegate(script).relativeProjectPath(path);
    }

    public static ConfigurableFileTree fileTree(DefaultScript script, Object baseDir) {
        return getDelegate(script).fileTree(baseDir);
    }

    public static int getDepth(DefaultScript script) {
        return getDelegate(script).getDepth();
    }

    public static void afterEvaluate(DefaultScript script, Action<? super Project> action) {
        getDelegate(script).afterEvaluate(action);
    }

    public static Set<Task> getTasksByName(DefaultScript script, String name, boolean recursive) {
        return getDelegate(script).getTasksByName(name, recursive);
    }

    public static RepositoryHandler getRepositories(DefaultScript script) {
        return getDelegate(script).getRepositories();
    }

    public static Set<Project> getSubprojects(DefaultScript script) {
        return getDelegate(script).getSubprojects();
    }

    public static AntBuilder getAnt(DefaultScript script) {
        return getDelegate(script).getAnt();
    }

    public static Map<String, Project> getChildProjects(DefaultScript script) {
        return getDelegate(script).getChildProjects();
    }

    public static void setGroup(DefaultScript script, Object group) {
        getDelegate(script).setGroup(group);
    }

    public static void setBuildDir(DefaultScript script, Object path) {
        getDelegate(script).setBuildDir(path);
    }

    public static FileTree zipTree(DefaultScript script, Object zipPath) {
        return getDelegate(script).zipTree(zipPath);
    }

    public static Object property(DefaultScript script, String propertyName) throws MissingPropertyException {
        return getDelegate(script).property(propertyName);
    }

    public static Project getProject(DefaultScript script) {
        return getDelegate(script).getProject();
    }

    public static ConfigurableFileCollection files(DefaultScript script, Object... paths) {
        return getDelegate(script).files(paths);
    }

    public static <T> NamedDomainObjectContainer<T> container(DefaultScript script, Class<T> type, NamedDomainObjectFactory<T> factory) {
        return getDelegate(script).container(type, factory);
    }

    public static void setVersion(DefaultScript script, Object version) {
        getDelegate(script).setVersion(version);
    }

    public static AntBuilder createAntBuilder(DefaultScript script) {
        return getDelegate(script).createAntBuilder();
    }

    public static ConfigurationContainer getConfigurations(DefaultScript script) {
        return getDelegate(script).getConfigurations();
    }

    public static File getBuildFile(DefaultScript script) {
        return getDelegate(script).getBuildFile();
    }

    public static void configurations(DefaultScript script, Closure configureClosure) {
        getDelegate(script).configurations(configureClosure);
    }

    @Incubating
    public static SoftwareComponentContainer getComponents(DefaultScript script) {
        return getDelegate(script).getComponents();
    }

    public static String relativePath(DefaultScript script, Object path) {
        return getDelegate(script).relativePath(path);
    }

    public static boolean hasProperty(DefaultScript script, String propertyName) {
        return getDelegate(script).hasProperty(propertyName);
    }

    public static TaskContainer getTasks(DefaultScript script) {
        return getDelegate(script).getTasks();
    }

    public static List<String> getDefaultTasks(DefaultScript script) {
        return getDelegate(script).getDefaultTasks();
    }

    public static ProjectState getState(DefaultScript script) {
        return getDelegate(script).getState();
    }

    public static Project findProject(DefaultScript script, String path) {
        return getDelegate(script).findProject(path);
    }

    public static Map<String, ?> getProperties(DefaultScript script) {
        return getDelegate(script).getProperties();
    }

    public static void defaultTasks(DefaultScript script, String... defaultTasks) {
        getDelegate(script).defaultTasks(defaultTasks);
    }

    public static File file(DefaultScript script, Object path) {
        return getDelegate(script).file(path);
    }

    public static <T> NamedDomainObjectContainer<T> container(DefaultScript script, Class<T> type) {
        return getDelegate(script).container(type);
    }

    public static Project getRootProject(DefaultScript script) {
        return getDelegate(script).getRootProject();
    }

    public static ConfigurableFileTree fileTree(DefaultScript script, Map<String, ?> args) {
        return getDelegate(script).fileTree(args);
    }

    public static Gradle getGradle(DefaultScript script) {
        return getDelegate(script).getGradle();
    }

    public static PluginContainer getPlugins(DefaultScript script) {
        return getDelegate(script).getPlugins();
    }

    public static File mkdir(DefaultScript script, Object path) {
        return getDelegate(script).mkdir(path);
    }

    public static Object getVersion(DefaultScript script) {
        return getDelegate(script).getVersion();
    }

    public static ExecResult exec(DefaultScript script, Action<? super ExecSpec> action) {
        return getDelegate(script).exec(action);
    }

    public static void allprojects(DefaultScript script, @DelegatesTo(Project.class) Closure configureClosure) {
        getDelegate(script).allprojects(configureClosure);
    }

    public static URI uri(DefaultScript script, Object path) {
        return getDelegate(script).uri(path);
    }

    public static File getProjectDir(DefaultScript script) {
        return getDelegate(script).getProjectDir();
    }

    public static Map<Project, Set<Task>> getAllTasks(DefaultScript script, boolean recursive) {
        return getDelegate(script).getAllTasks(recursive);
    }

    public static CopySpec copySpec(DefaultScript script, Action<? super CopySpec> action) {
        return getDelegate(script).copySpec(action);
    }

    public static Task task(DefaultScript script, String name) throws InvalidUserDataException {
        return getDelegate(script).task(name);
    }

    public static void beforeEvaluate(DefaultScript script, @DelegatesTo(Project.class) Closure closure) {
        getDelegate(script).beforeEvaluate(closure);
    }

    public static Project evaluationDependsOn(DefaultScript script, String path) throws UnknownProjectException {
        return getDelegate(script).evaluationDependsOn(path);
    }

    public static AntBuilder ant(DefaultScript script, Closure configureClosure) {
        return getDelegate(script).ant(configureClosure);
    }

    @Incubating
    public static Object findProperty(DefaultScript script, String propertyName) {
        return getDelegate(script).findProperty(propertyName);
    }

    public static void setProperty(DefaultScript script, String name, Object value) throws MissingPropertyException {
        getDelegate(script).setProperty(name, value);
    }

    public static LoggingManager getLogging(DefaultScript script) {
        return getDelegate(script).getLogging();
    }

    public static WorkResult copy(DefaultScript script, @DelegatesTo(CopySpec.class) Closure closure) {
        return getDelegate(script).copy(closure);
    }

    public static ArtifactHandler getArtifacts(DefaultScript script) {
        return getDelegate(script).getArtifacts();
    }

    public static void evaluationDependsOnChildren(DefaultScript script) {
        getDelegate(script).evaluationDependsOnChildren();
    }

    public static ScriptHandler getBuildscript(DefaultScript script) {
        return getDelegate(script).getBuildscript();
    }

    public static File file(DefaultScript script, Object path, PathValidation validation) throws InvalidUserDataException {
        return getDelegate(script).file(path, validation);
    }

    public static Convention getConvention(DefaultScript script) {
        return getDelegate(script).getConvention();
    }

    public static String getName(DefaultScript script) {
        return getDelegate(script).getName();
    }

    public static Task task(DefaultScript script, Map<String, ?> args, String name, @DelegatesTo(Task.class) Closure configureClosure) {
        return getDelegate(script).task(args, name, configureClosure);
    }

    public static void dependencies(DefaultScript script, @DelegatesTo(value = DependencyHandler.class, strategy = Closure.DELEGATE_FIRST) Closure configureClosure) {
        getDelegate(script).dependencies(configureClosure);
    }

    public static Logger getLogger(DefaultScript script) {
        return getDelegate(script).getLogger();
    }

    public static <T> T configure(DefaultScript script, @DelegatesTo.Target T object, @DelegatesTo Closure configureClosure) {
        return (T) getDelegate(script).configure(object, configureClosure);
    }

    public static Project getParent(DefaultScript script) {
        return getDelegate(script).getParent();
    }

    public static String getDescription(DefaultScript script) {
        return getDelegate(script).getDescription();
    }

    public static File getRootDir(DefaultScript script) {
        return getDelegate(script).getRootDir();
    }

    public static void subprojects(DefaultScript script, Action<? super Project> action) {
        getDelegate(script).subprojects(action);
    }

    public static void setDescription(DefaultScript script, String description) {
        getDelegate(script).setDescription(description);
    }

    public static File getBuildDir(DefaultScript script) {
        return getDelegate(script).getBuildDir();
    }

    public static Project project(DefaultScript script, String path) throws UnknownProjectException {
        return getDelegate(script).project(path);
    }

    public static void beforeEvaluate(DefaultScript script, Action<? super Project> action) {
        getDelegate(script).beforeEvaluate(action);
    }

    public static WorkResult copy(DefaultScript script, Action<? super CopySpec> action) {
        return getDelegate(script).copy(action);
    }

    public static ConfigurableFileCollection files(DefaultScript script, Object paths, Closure configureClosure) {
        return getDelegate(script).files(paths, configureClosure);
    }

    public static String getPath(DefaultScript script) {
        return getDelegate(script).getPath();
    }

    public static Task task(DefaultScript script, Map<String, ?> args, String name) throws InvalidUserDataException {
        return getDelegate(script).task(args, name);
    }

    public static boolean delete(DefaultScript script, Object... paths) {
        return getDelegate(script).delete(paths);
    }

    public static void subprojects(DefaultScript script, @DelegatesTo(Project.class) Closure configureClosure) {
        getDelegate(script).subprojects(configureClosure);
    }

    public static void allprojects(DefaultScript script, Action<? super Project> action) {
        getDelegate(script).allprojects(action);
    }

    public static CopySpec copySpec(DefaultScript script, @DelegatesTo(CopySpec.class) Closure closure) {
        return getDelegate(script).copySpec(closure);
    }

    public static ExecResult javaexec(DefaultScript script, Action<? super JavaExecSpec> action) {
        return getDelegate(script).javaexec(action);
    }

    public static void setStatus(DefaultScript script, Object status) {
        getDelegate(script).setStatus(status);
    }

    public static void setDefaultTasks(DefaultScript script, List<String> defaultTasks) {
        getDelegate(script).setDefaultTasks(defaultTasks);
    }

    public static void artifacts(DefaultScript script, @DelegatesTo(ArtifactHandler.class) Closure configureClosure) {
        getDelegate(script).artifacts(configureClosure);
    }

    public static Set<Project> getAllprojects(DefaultScript script) {
        return getDelegate(script).getAllprojects();
    }

    @Incubating
    public static PluginManager getPluginManager(DefaultScript script) {
        return getDelegate(script).getPluginManager();
    }

    public static int depthCompare(DefaultScript script, Project otherProject) {
        return getDelegate(script).depthCompare(otherProject);
    }

    public static <T> Iterable<T> configure(DefaultScript script, Iterable<T> objects, Action<? super T> configureAction) {
        return getDelegate(script).configure(objects, configureAction);
    }

    public static ExtensionContainer getExtensions(DefaultScript script) {
        return getDelegate(script).getExtensions();
    }

    public static WorkResult delete(DefaultScript script, Action<? super DeleteSpec> action) {
        return getDelegate(script).delete(action);
    }

    public static ExecResult javaexec(DefaultScript script, @DelegatesTo(JavaExecSpec.class) Closure closure) {
        return getDelegate(script).javaexec(closure);
    }

    public static void repositories(DefaultScript script, @DelegatesTo(RepositoryHandler.class) Closure configureAction) {
        getDelegate(script).repositories(configureAction);
    }

    public static FileTree tarTree(DefaultScript script, Object tarPath) {
        return getDelegate(script).tarTree(tarPath);
    }

    public static CopySpec copySpec(DefaultScript script) {
        return getDelegate(script).copySpec();
    }

    public static Task task(DefaultScript script, String name, @DelegatesTo(value = AbstractTask.class, strategy = Closure.DELEGATE_FIRST) Closure configureClosure) {
        return getDelegate(script).task(name, configureClosure);
    }

    public static Object getGroup(DefaultScript script) {
        return getDelegate(script).getGroup();
    }

    public static DependencyHandler getDependencies(DefaultScript script) {
        return getDelegate(script).getDependencies();
    }

    public static Object getStatus(DefaultScript script) {
        return getDelegate(script).getStatus();
    }

    public static <T> T extension(DefaultScript script, Class<T> type) {
        return getDelegate(script).getExtensions().getByType(type);
    }

    public static <T> void extension(DefaultScript script, @DelegatesTo.Target Class<T> type, @DelegatesTo(genericTypeIndex = 0) Closure<?> configure) {
        getDelegate(script).getExtensions().configure(type, ClosureBackedAction.of(configure));
    }

    // these should probably live in a ProjectExtension
    public static <T> T extension(Project p, Class<T> type) {
        return p.getExtensions().getByType(type);
    }

    public static <T> void extension(Project p, @DelegatesTo.Target Class<T> type, @DelegatesTo(genericTypeIndex = 0) Closure<?> configure) {
        p.getExtensions().configure(type, ClosureBackedAction.of(configure));
    }

}
