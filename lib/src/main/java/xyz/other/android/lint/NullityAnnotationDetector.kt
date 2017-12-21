/*
 * Copyright 2017 Post Social Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xyz.other.android.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import java.util.Arrays


private const val ANNOTATION_NON_NULL = "android.support.annotation.NonNull"
private const val ANNOTATION_NULLABLE = "android.support.annotation.Nullable"

private val IMPLEMENTATION = Implementation(
        NullityAnnotationDetector::class.java, Scope.JAVA_FILE_SCOPE)

internal val MISSING_NULLITY_ANNOTATION = Issue.create(
        "MissingNullityAnnotation",
        "Missing nullity annotation",
        "Finds method and field declarations that are missing nullity annotations:\n"
                + " - Method parameters of a reference type.\n"
                + " - Methods with a return value of a reference type.\n"
                + " - Non-final fields.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        IMPLEMENTATION)

internal val UNNECESSARY_NULLITY_ANNOTATION = Issue.create(
        "UnnecessaryNullityAnnotation",
        "Unnecessary nullity annotation",
        "Finds method and field declarations that have unneeded nullity annotations:\n"
                + " - Method parameters of a primitive type.\n"
                + " - Methods with a return value of a primitive type.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        IMPLEMENTATION)

internal val NON_FINAL_METHOD_PARAMETER = Issue.create(
        "NonFinalMethodParameter",
        "Method parameters must be final",
        "Finds method parameters that are not final.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        IMPLEMENTATION)

/*
 * Verify that all large mutable things with values (methods, fields, parameters)
 * are explicitly annotated Nullable ar NonNull, using the Android Support annotations.
 *
 * Final fields are not checked for nullability annotations
 * If you create a synonym for @{code null} you deserve what you get.
 */
class NullityAnnotationDetector : Detector(), Detector.UastScanner {
    override fun getApplicableUastTypes(): MutableList<Class<out UElement>> {
        return Arrays.asList(UField::class.java, UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return NullityAnnotationChecker(context)
    }
}

private class NullityAnnotationChecker(private val context: JavaContext) : UElementHandler() {

    override fun visitField(field: UField?) {
        if (field == null) {
            return
        }

        checkNullityAnnotations(field, field.type, field.isFinal, field.annotations)
    }

    override fun visitMethod(method: UMethod?) {
        if (method == null) {
            return
        }

        checkNullityAnnotations(method, method.returnType, method.isFinal, method.annotations)

        val finalRequired = method.containingClass?.isInterface == false
        method.uastParameters.forEach { param -> checkParameter(param, finalRequired) }
    }

    private fun checkParameter(param: UParameter, finalRequired: Boolean) {
        checkNullityAnnotations(param, param.type, param.isFinal, param.annotations)

        if (!finalRequired || param.isFinal) {
            return
        }

        val uParam = param as UElement
        context.report(
                NON_FINAL_METHOD_PARAMETER,
                uParam,
                context.getLocation(uParam),
                NON_FINAL_METHOD_PARAMETER.getBriefDescription(TextFormat.TEXT))
    }

    private fun checkNullityAnnotations(
            node: UElement,
            type: PsiType?,
            isFinal: Boolean,
            annotations: List<UAnnotation>?) {
        if ((type == null) || (type is PsiPrimitiveType)) {
            enforceNullityAnnotations(false, annotations, node)
        } else if (!isFinal) {
            enforceNullityAnnotations(true, annotations, node)
        }
    }

    private fun enforceNullityAnnotations(required: Boolean, annotations: List<UAnnotation>?, node: UElement) {
        val found = annotations?.firstOrNull { it ->
            val name = it.qualifiedName
            name == ANNOTATION_NON_NULL || name == ANNOTATION_NULLABLE
        }

        val issue = when {
            (required && (found == null)) -> MISSING_NULLITY_ANNOTATION
            (!required && (found != null)) -> UNNECESSARY_NULLITY_ANNOTATION
            else -> null
        }

        if (issue != null) {
            context.report(issue, node, context.getLocation(node), issue.getBriefDescription(TextFormat.TEXT))
        }
    }
}
