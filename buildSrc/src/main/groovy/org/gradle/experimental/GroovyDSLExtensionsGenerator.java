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

package org.gradle.experimental;

import com.google.common.base.Joiner;
import com.google.common.io.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class GroovyDSLExtensionsGenerator extends DefaultTask {

    private FileCollection classes;
    private File outputDirectory;
    private String moduleName;
    private String moduleVersion;
    private List<String> additionalExtensions;

    @InputFiles
    public FileCollection getClasses() {
        return classes;
    }

    public void setClasses(FileCollection classes) {
        this.classes = classes;
    }

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @Input
    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    @Input
    public String getModuleVersion() {
        return moduleVersion;
    }

    public void setModuleVersion(String moduleVersion) {
        this.moduleVersion = moduleVersion;
    }

    @Input
    @Optional
    public List<String> getAdditionalExtensions() {
        return additionalExtensions;
    }

    public void setAdditionalExtensions(List<String> additionalExtensions) {
        this.additionalExtensions = additionalExtensions;
    }

    @TaskAction
    public void processClasses() throws IOException {
        List<String> extensionClasses = new ArrayList<String>();
        for (File file : classes) {
            if (file.getName().endsWith(".class")) {
                processClassFile(file, extensionClasses);
            }
        }
        if (additionalExtensions != null) {
            extensionClasses.addAll(additionalExtensions);
        }
        if (!extensionClasses.isEmpty()) {
            writeDescriptorFile(extensionClasses);
            System.out.println("Generated " + extensionClasses.size() + " extension classes");
        }
    }

    private void processClassFile(File file, List<String> extensionClasses) throws IOException {
        ClassReader cr = new ClassReader(new FileInputStream(file));
        DslVisitor dslVisitor = new DslVisitor(outputDirectory);
        cr.accept(dslVisitor, 0);
        if (dslVisitor.extensionClassName != null) {
            extensionClasses.add(dslVisitor.extensionClassName.replace('/', '.'));
        }
    }

    private void writeDescriptorFile(List<String> extensionClasses) throws IOException {
        File extensionDescriptorFile = new File(outputDirectory, "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule.static");
        Files.createParentDirs(extensionDescriptorFile);
        StringBuilder sb = new StringBuilder(64 + extensionClasses.size() * 128);
        sb.append("moduleName=").append(moduleName).append("\n");
        sb.append("moduleVersion=").append(moduleVersion).append("\n");
        sb.append("extensionClasses=");
        sb.append(Joiner.on(",").join(extensionClasses));
        sb.append("\n");
        Files.write(sb, extensionDescriptorFile, Charset.forName("UTF-8"));
    }

    private static class DslVisitor extends ClassVisitor {
        private static final Type ACTION_TYPE = Type.getType("Lorg/gradle/api/Action;");
        private static final Type CLOSURE_BACKED_ACTION_TYPE = Type.getType("Lorg/gradle/api/internal/ClosureBackedAction;");
        private static final Type CLOSURE_TYPE = Type.getType("Lgroovy/lang/Closure;");
        private static final String DELEGATES_TO_TYPE = "Lgroovy/lang/DelegatesTo;";
        private static final String DELEGATES_TO_TARGET_TYPE = "Lgroovy/lang/DelegatesTo$Target;";
        private final File outputDir;
        private boolean isCandidate;
        private int version;
        private Type originalType;
        private String extensionClassName;
        private ClassWriter extensionClassWriter;
        private boolean isInterface;

        public DslVisitor(File outputDir) {
            super(Opcodes.ASM5);
            this.outputDir = outputDir;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.isCandidate = Modifier.isPublic(access) && !name.toLowerCase().contains("internal");
            this.isInterface = (access & Opcodes.ACC_INTERFACE) > 0;
            this.extensionClassWriter = null;
            if (isCandidate) {
                this.originalType = Type.getType("L" + name + ";");
                this.extensionClassName = toExtensionClassName(name);
                this.version = version;
                if (signature != null) {
                    final AtomicBoolean hasTypeToken = new AtomicBoolean();
                    // extract generic type tokens
                    SignatureReader reader = new SignatureReader(signature);
                    reader.accept(new SignatureVisitor(Opcodes.ASM5) {

                        @Override
                        public void visitFormalTypeParameter(String name) {
                            super.visitFormalTypeParameter(name);
                            hasTypeToken.set(true);
                        }

                        @Override
                        public void visitTypeVariable(String name) {
                            super.visitTypeVariable(name);
                            hasTypeToken.set(true);
                        }
                    });
                    isCandidate = isCandidate && !hasTypeToken.get();
                }
            } else {
                this.originalType = null;
                this.extensionClassName = null;
                this.version = -1;
            }
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            if (extensionClassWriter != null) {
                extensionClassWriter.visitEnd();
                byte[] bytes = extensionClassWriter.toByteArray();
                String packageName = extensionClassName.substring(0, extensionClassName.lastIndexOf("/"));
                String simpleClassName = extensionClassName.substring(extensionClassName.lastIndexOf("/") + 1);
                File destDir = new File(outputDir, packageName);
                try {
                    File classFile = new File(destDir, simpleClassName + ".class");
                    Files.createParentDirs(classFile);
                    Files.write(bytes, classFile);
                    // CheckClassAdapter adapter = new CheckClassAdapter(null);
                    // ClassReader cr = new ClassReader(bytes);
                    // cr.accept(adapter, 0);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        private static String toExtensionClassName(String name) {
            return name + "GroovyExtension";
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if (!isCandidate || Modifier.isStatic(access) || "<init>".equals(name)) {
                return null;
            }
            Type[] argumentTypes = Type.getArgumentTypes(desc);
            if (argumentTypes.length >= 1) {
                Type argumentType = argumentTypes[argumentTypes.length - 1];
                if (argumentType.equals(ACTION_TYPE)) {
                    try {
                        addExtensionMethod(name, desc, signature);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            return null;
        }

        public void addExtensionMethod(String name, String desc, String signature) throws Exception {
            assertWriter();
            Type returnType = Type.getReturnType(desc);
            Type[] originalParameterTypes = Type.getArgumentTypes(desc);
            int numParams = originalParameterTypes.length + 1;
            // todo: make this rock solid, this is totally wrong!
            String delegateType = null;
            String genericTypeSig = null;
            int classTypeIndex = -1;
            if (signature != null) {
                if (signature.indexOf("(") > 0) {
                    genericTypeSig = signature.substring(0, signature.indexOf("(") + 1);
                    genericTypeSig = genericTypeSig + originalType.getDescriptor();
                    genericTypeSig = genericTypeSig + signature.substring(signature.indexOf("(") + 1, signature.lastIndexOf("Lorg/gradle/api/Action"));
                    genericTypeSig = genericTypeSig + "Lgroovy/lang/Closure;)";
                    genericTypeSig = genericTypeSig + signature.substring(signature.lastIndexOf(")") + 1);
                }
                ActionTypeSignatureVisitor sv = new ActionTypeSignatureVisitor();
                new SignatureReader(signature).accept(sv);
                delegateType = sv.actionTypeArg;
                classTypeIndex = sv.classTypeIndex;
            } else {
                System.out.println("Warn: method " + name + " on " + originalType + " doesn't have signature");
            }
            Type[] closurisedParameterTypes = new Type[numParams];
            System.arraycopy(originalParameterTypes, 0, closurisedParameterTypes, 1, numParams - 1);
            closurisedParameterTypes[numParams - 1] = CLOSURE_TYPE;
            closurisedParameterTypes[0] = originalType;

            String methodDescriptor = Type.getMethodDescriptor(returnType, closurisedParameterTypes);

            // GENERATE public <return type> <method>(Closure v) { return <method>(…, new ClosureBackedAction(v)); }
            MethodVisitor methodVisitor = extensionClassWriter.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, name, methodDescriptor, genericTypeSig, new String[0]);
            if (delegateType != null) {
                AnnotationVisitor av;
                if (classTypeIndex >= 0) {
                    av = methodVisitor.visitParameterAnnotation(classTypeIndex, DELEGATES_TO_TARGET_TYPE, true);
                    av.visitEnd();
                }
                av = methodVisitor.visitParameterAnnotation(numParams - 1, DELEGATES_TO_TYPE, true);
                if (classTypeIndex == -1) {
                    av.visit("type", delegateType);
                } else {
                    av.visit("genericTypeIndex", 0);
                }
                av.visit("strategy", 1); // 1 == Closure.DELEGATE_FIRST
                av.visitEnd();
            }
            methodVisitor.visitCode();

            // GENERATE <method>(…, new ClosureBackedAction(v));
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);

            for (int stackVar = 1; stackVar < numParams - 1; ++stackVar) {
                methodVisitor.visitVarInsn(closurisedParameterTypes[stackVar - 1].getOpcode(Opcodes.ILOAD), stackVar);
            }

            // GENERATE new ClosureBackedAction(v);
            methodVisitor.visitTypeInsn(Opcodes.NEW, CLOSURE_BACKED_ACTION_TYPE.getInternalName());
            methodVisitor.visitInsn(Opcodes.DUP);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, numParams - 1);
            String constuctorDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, CLOSURE_TYPE);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, CLOSURE_BACKED_ACTION_TYPE.getInternalName(), "<init>", constuctorDescriptor, false);


            methodDescriptor = Type.getMethodDescriptor(returnType, originalParameterTypes);
            methodVisitor.visitMethodInsn(isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, originalType.getInternalName(), name, methodDescriptor, isInterface);

            methodVisitor.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        private void assertWriter() {
            if (extensionClassWriter == null) {
                extensionClassWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);
                extensionClassWriter.visit(version, Opcodes.ACC_PUBLIC, extensionClassName, null, "java/lang/Object", null);
            }
        }

        private static class ActionTypeSignatureVisitor extends SignatureVisitor {
            int argNum = -1;
            String currentType;
            String actionTypeArg;
            String classTypeArg;
            int classTypeIndex = -1;

            public ActionTypeSignatureVisitor() {
                super(Opcodes.ASM5);
            }

            @Override
            public void visitFormalTypeParameter(String name) {
                super.visitFormalTypeParameter(name);
            }

            @Override
            public void visitTypeVariable(String name) {
                super.visitTypeVariable(name);
                remindType(name);
            }

            private void remindType(String name) {
                if ("org/gradle/api/Action".equals(currentType)) {
                    actionTypeArg = name.replace('/', '.');
                } else if ("java/lang/Class".equals(currentType)) {
                    classTypeArg = name.replace('/', '.');
                    classTypeIndex = argNum;
                }
            }

            @Override
            public SignatureVisitor visitParameterType() {
                argNum++;
                return super.visitParameterType();
            }

            @Override
            public void visitClassType(String name) {
                remindType(name);
                currentType = name;
                super.visitClassType(name);
            }


        }
    }


}
