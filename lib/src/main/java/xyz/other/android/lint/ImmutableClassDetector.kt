/*
 *  Copyright (c) 2017 Post Social Inc
 *     All Rights Reserved.
 *     Post Social Inc Confidential and Proprietary.
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
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import java.util.Arrays


private val IMPLEMENTATION = Implementation(
        ImmutableClassDetector::class.java, Scope.JAVA_FILE_SCOPE)

internal val IMMUTABLE_CLASS = Issue.create(
        "ImmutableClass",
        "A class annotated as @Immutable must not contain non-final, non-transient fields",
        "Finds fields in @Immutable class that are mutable or non-transient",
        Category.CORRECTNESS,
        8,
        Severity.ERROR,
        IMPLEMENTATION)

class ImmutableClassDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): MutableList<Class<out UElement>> {
        return Arrays.asList(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return ImmutableClassChecker(context)
    }
}

private class ImmutableClassChecker(private val context: JavaContext) : UElementHandler() {
    override fun visitClass(node: UClass) {
        if (!markedImmutable(node.annotations)) {
            return
        }

        val declarations = node.uastDeclarations
        if (declarations.isEmpty()) {
            return
        }

        // I suspect that there is a better way to do this:
        // It would be better to let the scanner climb the tree...
        for (declaration in declarations) {
            val field = declaration as? UField
            val modifiers = field?.modifierList ?: continue
            if (!modifiers.hasExplicitModifier("final") && !modifiers.hasExplicitModifier("transient")) {
                context.report(
                        IMMUTABLE_CLASS,
                        field,
                        context.getLocation(field),
                        IMMUTABLE_CLASS.getBriefDescription(TextFormat.TEXT))
            }
        }
    }

    private fun markedImmutable(annotations: List<UAnnotation>?): Boolean {
        return annotations!!.any { it.qualifiedName?.endsWith(".Immutable") ?: false }
    }
}
