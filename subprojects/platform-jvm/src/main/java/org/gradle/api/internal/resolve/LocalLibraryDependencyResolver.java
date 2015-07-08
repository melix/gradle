/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.internal.resolve;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.*;
import org.gradle.api.Named;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.artifacts.component.LibraryComponentSelector;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetaData;
import org.gradle.internal.component.model.*;
import org.gradle.internal.reflect.ClassDetails;
import org.gradle.internal.reflect.ClassInspector;
import org.gradle.internal.reflect.PropertyDetails;
import org.gradle.internal.resolve.ArtifactResolveException;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData;
import org.gradle.language.base.internal.resolve.LibraryResolveException;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.LibrarySpec;
import org.gradle.platform.base.Variant;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

// TODO: This should really live in platform-base, however we need to inject the library requirements at some
// point, and for now it is hardcoded to JVM libraries
public class LocalLibraryDependencyResolver implements DependencyToComponentIdResolver, ComponentMetaDataResolver, ArtifactResolver {
    private static final Comparator<JavaPlatform> JAVA_PLATFORM_COMPARATOR = new Comparator<JavaPlatform>() {
        @Override
        public int compare(JavaPlatform o1, JavaPlatform o2) {
            return o1.getTargetCompatibility().compareTo(o2.getTargetCompatibility());
        }
    };
    private static final Comparator<JvmBinarySpec> JVM_BINARY_SPEC_COMPARATOR = new Comparator<JvmBinarySpec>() {
        @Override
        public int compare(JvmBinarySpec o1, JvmBinarySpec o2) {
            return o1.getDisplayName().compareTo(o2.getDisplayName());
        }
    };

    private final ProjectModelResolver projectModelResolver;
    private final JarBinarySpec targetBinary;

    public LocalLibraryDependencyResolver(ProjectModelResolver projectModelResolver, JarBinarySpec targetBinary) {
        this.projectModelResolver = projectModelResolver;
        this.targetBinary = targetBinary;
    }

    // todo: replace with something using ModelSchema
    private static Map<String, String> extractVariants(JarBinarySpec spec) {
        Map<String,String> variants = Maps.newHashMap();
        Class<? extends JarBinarySpec> specClass = spec.getClass();
        Set<Class<?>> interfaces = ClassInspector.inspect(specClass).getSuperTypes();
        for (Class<?> intf : interfaces) {
            ClassDetails details = ClassInspector.inspect(intf);
            Collection<? extends PropertyDetails> properties = details.getProperties();
            for (PropertyDetails property : properties) {
                List<Method> getters = property.getGetters();
                for (Method getter : getters) {
                    if (getter.getAnnotation(Variant.class)!=null) {
                        extractVariant(variants, spec, property.getName(), getter);
                    }
                }
            }
        }

        return variants;
    }

    private static void extractVariant(Map<String, String> variants, JarBinarySpec spec, String name, Method method) {
        Object result;
        try {
            result = method.invoke(spec);
        } catch (IllegalAccessException e) {
            result = null;
        } catch (InvocationTargetException e) {
            result = null;
        }
        if (result instanceof String) {
            variants.put(name, (String) result);
        } else if (result instanceof Named) {
            variants.put(name, ((Named) result).getName());
        }
    }

    @Override
    public void resolve(DependencyMetaData dependency, final BuildableComponentIdResolveResult result) {
        if (dependency.getSelector() instanceof LibraryComponentSelector) {
            LibraryComponentSelector selector = (LibraryComponentSelector) dependency.getSelector();
            final String selectorProjectPath = selector.getProjectPath();
            final String libraryName = selector.getLibraryName();
            LibraryResolutionResult resolutionResult = doResolve(selectorProjectPath, libraryName);
            LibrarySpec selectedLibrary = resolutionResult.getSelectedLibrary();
            if (selectedLibrary != null) {
                Collection<BinarySpec> allVariants = selectedLibrary.getBinaries().values();
                Collection<? extends BinarySpec> variants = filterBinaries(allVariants);
                if (!allVariants.isEmpty() && variants.isEmpty()) {
                    // no compatible variant found
                    result.failed(new ModuleVersionResolveException(selector, noCompatiblePlatformErrorMessage(libraryName, targetBinary.getTargetPlatform(), allVariants)));
                } else if (variants.size() > 1) {
                    result.failed(new ModuleVersionResolveException(selector, multipleBinariesForSameVariantErrorMessage(libraryName, targetBinary.getTargetPlatform(), variants)));
                } else {
                    JarBinarySpec jarBinary = (JarBinarySpec) variants.iterator().next();
                    DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
                    buildDependencies.add(jarBinary);

                    DefaultLibraryLocalComponentMetaData metaData = DefaultLibraryLocalComponentMetaData.newMetaData(jarBinary.getId(), buildDependencies);
                    metaData.addArtifacts(DefaultLibraryBinaryIdentifier.CONFIGURATION_NAME, Collections.singleton(createJarPublishArtifact(jarBinary)));

                    result.resolved(metaData);
                }
            }
            if (!result.hasResult()) {
                String message = prettyResolutionErrorMessage(selector, resolutionResult);
                ModuleVersionResolveException failure = new ModuleVersionResolveException(selector, new LibraryResolveException(message));
                result.failed(failure);
            }
        }
    }

    private PublishArtifact createJarPublishArtifact(JarBinarySpec jarBinarySpec) {
        return new LibraryPublishArtifact("jar", jarBinarySpec.getJarFile());
    }

    private Collection<? extends BinarySpec> filterBinaries(Collection<BinarySpec> values) {
        if (values.isEmpty()) {
            return values;
        }
        TreeMultimap<JavaPlatform, JvmBinarySpec> platformToBinary = TreeMultimap.create(JAVA_PLATFORM_COMPARATOR, JVM_BINARY_SPEC_COMPARATOR);
        Map<String, String> requestedVariants = extractVariants(targetBinary);
        Set<String> requestedVariantDimensions = requestedVariants.keySet();
        for (BinarySpec binarySpec : values) {
            if (binarySpec instanceof JarBinarySpec) {
                Map<String, String> binaryVariants = extractVariants((JarBinarySpec) binarySpec);
                Sets.SetView<String> comparableVariants = Sets.intersection(requestedVariantDimensions, binaryVariants.keySet());
                if (!comparableVariants.isEmpty()) {
                    JvmBinarySpec jvmSpec = (JarBinarySpec) binarySpec;
                    boolean matching = true;
                    for (String variant : comparableVariants) {
                        matching = matching
                            && ("javaPlatform".equals(variant) || Objects.equals(requestedVariants.get(variant), binaryVariants.get(variant)));
                    }
                    if (matching && jvmSpec.getTargetPlatform().getTargetCompatibility().compareTo(targetBinary.getTargetPlatform().getTargetCompatibility()) <= 0) {
                        platformToBinary.put(jvmSpec.getTargetPlatform(), jvmSpec);
                    }
                }
            }
        }
        if (platformToBinary.isEmpty()) {
            return Collections.emptyList();
        }
        JavaPlatform first = platformToBinary.keySet().last();
        return platformToBinary.get(first);
    }

    private LibraryResolutionResult doResolve(String projectPath,
                                              String libraryName) {
        try {
            ModelRegistry projectModel = projectModelResolver.resolveProjectModel(projectPath);
            ComponentSpecContainer components = projectModel.find(
                ModelPath.path("components"),
                ModelType.of(ComponentSpecContainer.class));
            if (components != null) {
                ModelMap<? extends LibrarySpec> libraries = components.withType(LibrarySpec.class);
                return LibraryResolutionResult.from(libraries.values(), libraryName);
            } else {
                return LibraryResolutionResult.empty();
            }
        } catch (UnknownProjectException e) {
            return LibraryResolutionResult.projectNotFound();
        }
    }

    private static String multipleBinariesForSameVariantErrorMessage(String libraryName, JavaPlatform javaPlatform, Collection<? extends BinarySpec> variants) {
        return String.format("Multiple binaries available for library '%s' (%s) : %s", libraryName, javaPlatform, variants);
    }

    private static String noCompatiblePlatformErrorMessage(String libraryName, JavaPlatform javaPlatform, Collection<BinarySpec> allVariants) {
        return String.format("Cannot find a compatible binary for library '%s' (%s). Available platforms: %s", libraryName, javaPlatform, Lists.transform(
            Lists.newArrayList(allVariants), new Function<BinarySpec, String>() {
                @Override
                public String apply(BinarySpec input) {
                    return input instanceof JvmBinarySpec ? ((JvmBinarySpec) input).getTargetPlatform().toString() : input.toString();
                }
            }
        ));
    }

    private static String prettyResolutionErrorMessage(
        LibraryComponentSelector selector,
        LibraryResolutionResult result) {
        boolean hasLibraries = result.hasLibraries();
        List<String> candidateLibraries = formatLibraryNames(result.getCandidateLibraries());
        String projectPath = selector.getProjectPath();
        String libraryName = selector.getLibraryName();

        StringBuilder sb = new StringBuilder("Project '").append(projectPath).append("'");
        if (libraryName == null || !hasLibraries) {
            if (result.isProjectNotFound()) {
                sb.append(" not found.");
            } else if (!hasLibraries) {
                sb.append(" doesn't define any library.");
            } else {
                sb.append(" contains more than one library. Please select one of ");
                Joiner.on(", ").appendTo(sb, candidateLibraries);
            }
        } else {
            LibrarySpec notMatchingRequirements = result.getNonMatchingLibrary();
            if (notMatchingRequirements != null) {
                sb.append(" contains a library named '").append(libraryName)
                  .append("' but it is not a ")
                  .append(JvmLibrarySpec.class.getSimpleName());
            } else {
                sb.append(" does not contain library '").append(libraryName).append("'. Did you want to use ");
                if (candidateLibraries.size() == 1) {
                    sb.append(candidateLibraries.get(0));
                } else {
                    sb.append("one of ");
                    Joiner.on(", ").appendTo(sb, candidateLibraries);
                }
                sb.append("?");
            }
        }
        return sb.toString();
    }

    private static List<String> formatLibraryNames(List<String> libs) {
        List<String> list = Lists.transform(libs, new Function<String, String>() {
            @Override
            public String apply(String input) {
                return String.format("'%s'", input);
            }
        });
        return Ordering.natural().sortedCopy(list);
    }

    @Override
    public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, BuildableComponentResolveResult result) {
        if (isLibrary(identifier)) {
            throw new RuntimeException("Not yet implemented");
        }
    }

    private boolean isLibrary(ComponentIdentifier identifier) {
        return identifier instanceof LibraryBinaryIdentifier;
    }

    @Override
    public void resolveModuleArtifacts(ComponentResolveMetaData component, ComponentUsage usage, BuildableArtifactSetResolveResult result) {
        ComponentIdentifier componentId = component.getComponentId();
        if (isLibrary(componentId)) {
            ConfigurationMetaData configuration = component.getConfiguration(usage.getConfigurationName());
            if (configuration!=null) {
                Set<ComponentArtifactMetaData> artifacts = configuration.getArtifacts();
                result.resolved(artifacts);
            }
            if (!result.hasResult()) {
                result.failed(new ArtifactResolveException(String.format("Unable to resolve artifact for %s", componentId)));
            }
        }
    }

    @Override
    public void resolveModuleArtifacts(ComponentResolveMetaData component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        if (isLibrary(component.getComponentId())) {
            result.resolved(Collections.<ComponentArtifactMetaData>emptyList());
        }
    }

    @Override
    public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        if (isLibrary(artifact.getComponentId())) {
            if (artifact instanceof PublishArtifactLocalArtifactMetaData) {
                result.resolved(((PublishArtifactLocalArtifactMetaData) artifact).getFile());
            } else {
                result.failed(new ArtifactResolveException("Unsupported artifact metadata type: " + artifact));
            }
        }
    }

    /**
     * Intermediate data structure used to store the result of a resolution and help at building an understandable error message in case resolution fails.
     */
    private static class LibraryResolutionResult {
        private static final LibraryResolutionResult EMPTY = new LibraryResolutionResult();
        private static final LibraryResolutionResult PROJECT_NOT_FOUND = new LibraryResolutionResult();
        private final Map<String, LibrarySpec> libsMatchingRequirements;
        private final Map<String, LibrarySpec> libsNotMatchingRequirements;

        private LibrarySpec selectedLibrary;
        private LibrarySpec nonMatchingLibrary;

        public static LibraryResolutionResult from(Collection<? extends LibrarySpec> libraries, String libraryName) {
            LibraryResolutionResult result = new LibraryResolutionResult();
            for (LibrarySpec librarySpec : libraries) {
                if (result.acceptLibrary(librarySpec)) {
                    result.libsMatchingRequirements.put(librarySpec.getName(), librarySpec);
                } else {
                    result.libsNotMatchingRequirements.put(librarySpec.getName(), librarySpec);
                }
            }
            result.resolve(libraryName);
            return result;
        }

        public static LibraryResolutionResult projectNotFound() {
            return PROJECT_NOT_FOUND;
        }

        public static LibraryResolutionResult empty() {
            return EMPTY;
        }

        private LibraryResolutionResult() {
            this.libsMatchingRequirements = Maps.newHashMap();
            this.libsNotMatchingRequirements = Maps.newHashMap();
        }

        private boolean acceptLibrary(LibrarySpec librarySpec) {
            // TODO: this should be parametrized, and provided in some way to the resolver
            // once this is done, can move to platform-base
            return !librarySpec.getBinaries().withType(JarBinarySpec.class).isEmpty();
        }

        private LibrarySpec getSingleMatchingLibrary() {
            if (libsMatchingRequirements.size() == 1) {
                return libsMatchingRequirements.values().iterator().next();
            }
            return null;
        }

        private void resolve(String libraryName) {
            if (libraryName == null) {
                LibrarySpec singleMatchingLibrary = getSingleMatchingLibrary();
                if (singleMatchingLibrary == null) {
                    return;
                }
                libraryName = singleMatchingLibrary.getName();
            }

            selectedLibrary = libsMatchingRequirements.get(libraryName);
            nonMatchingLibrary = libsNotMatchingRequirements.get(libraryName);
        }

        public boolean isProjectNotFound() {
            return PROJECT_NOT_FOUND == this;
        }

        public boolean hasLibraries() {
            return !libsMatchingRequirements.isEmpty() || !libsNotMatchingRequirements.isEmpty();
        }

        public LibrarySpec getSelectedLibrary() {
            return selectedLibrary;
        }

        public LibrarySpec getNonMatchingLibrary() {
            return nonMatchingLibrary;
        }

        public List<String> getCandidateLibraries() {
            return Lists.newArrayList(libsMatchingRequirements.keySet());
        }
    }

}
