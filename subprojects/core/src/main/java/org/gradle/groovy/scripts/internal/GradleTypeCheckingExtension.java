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
package org.gradle.groovy.scripts.internal;

import com.google.common.collect.Maps;
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCall;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.stc.AbstractTypeCheckingExtension;
import org.codehaus.groovy.transform.stc.StaticTypeCheckingVisitor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GradleTypeCheckingExtension extends AbstractTypeCheckingExtension {
    private static final String METHOD_MISSING_EXTENSION_NAME = "methodMissingExtension";

    public GradleTypeCheckingExtension(StaticTypeCheckingVisitor typeCheckingVisitor) {
        super(typeCheckingVisitor);
    }

    private Map<MethodCallExpression, MethodCallExpression> methodMissingCalls = Maps.newHashMap();

    @Override
    public boolean beforeVisitClass(ClassNode node) {
        Map<MethodCallExpression, MethodCallExpression> oldCalls = this.methodMissingCalls;
        methodMissingCalls = Maps.newHashMap();
        try {
            return super.beforeVisitClass(node);
        } finally {
            methodMissingCalls = oldCalls;
        }
    }

    @Override
    public void afterVisitClass(ClassNode node) {
        new MethodMissingExtensionReplacer(context.getSource(), methodMissingCalls).visitClass(node);
        super.afterVisitClass(node);
    }

    @Override
    public List<MethodNode> handleMissingMethod(ClassNode receiver, String name, ArgumentListExpression argumentList, ClassNode[] argumentTypes, MethodCall call) {
        if (call instanceof MethodCallExpression) {
            MethodCallExpression mce = (MethodCallExpression) call;
            if (mce.isImplicitThis() && !METHOD_MISSING_EXTENSION_NAME.equals(name)) {
                ArgumentListExpression mmL = new ArgumentListExpression();
                mmL.addExpression(new ConstantExpression(name));
                for (Expression expression : argumentList) {
                    mmL.addExpression(expression);
                }
                MethodCallExpression methodMissing = new MethodCallExpression(((MethodCallExpression) call).getObjectExpression(),
                    METHOD_MISSING_EXTENSION_NAME,
                    mmL);
                typeCheckingVisitor.visitMethodCallExpression(methodMissing);
                MethodNode targetMethod = getTargetMethod(methodMissing);
                if (targetMethod != null) {
                    handled = true;
                    methodMissingCalls.put(mce, methodMissing);
                    return Collections.singletonList(targetMethod);
                }
            }
        }
        return super.handleMissingMethod(receiver, name, argumentList, argumentTypes, call);
    }

    private static class MethodMissingExtensionReplacer extends ClassCodeExpressionTransformer {

        private final SourceUnit sourceUnit;
        private final Map<MethodCallExpression, MethodCallExpression> replacements;

        private MethodMissingExtensionReplacer(SourceUnit sourceUnit, Map<MethodCallExpression, MethodCallExpression> replacements) {
            this.sourceUnit = sourceUnit;
            this.replacements = replacements;
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return sourceUnit;
        }

        public Expression transform(final Expression exp) {
            if (exp instanceof MethodCallExpression) {
                MethodCallExpression repl = replacements.get(exp);
                if (repl != null) {
                    return repl;
                }
            } else if (exp instanceof ClosureExpression) {
                ((ClosureExpression) exp).getCode().visit(this);
            }
            return super.transform(exp);
        }
    }
}
