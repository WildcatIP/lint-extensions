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
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import java.util.Arrays


private val IMPLEMENTATION = Implementation(
        BlacklistedAnnotationDetector::class.java, Scope.JAVA_FILE_SCOPE)

internal val BLACKLISTED_ANNOTATION = Issue.create(
        "BlacklistedAnnotation",
        "Use of a blacklisted annotation",
        "Annotations listed in the blacklist may not be used",
        Category.CORRECTNESS,
        8,
        Severity.ERROR,
        IMPLEMENTATION)

class BlacklistedAnnotationDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): MutableList<Class<out UElement>> {
        return Arrays.asList(UClass::class.java, UField::class.java, UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return BlacklistedAnnotationChecker(context, Blacklist(context))
    }
}

private class BlacklistedAnnotationChecker(private val context: JavaContext, private val blacklist: Blacklist)
    : UElementHandler() {
    override fun visitClass(node: UClass) {
        checkAnnotations(node)
    }

    override fun visitField(node: UField) {
        checkAnnotations(node)
    }

    override fun visitMethod(node: UMethod) {
        checkAnnotations(node)
        node.uastParameters.forEach { param -> checkAnnotations(param) }
    }

    private fun checkAnnotations(node: UAnnotated) {
        val annotations = node.annotations
        if (annotations.isEmpty()) {
            return
        }

        val blacklisted = blacklist.getBlacklistedAnnotations(HashSet(annotations.map { it.qualifiedName!! }))

        if (blacklisted.isEmpty()) {
            return
        }

        val messages = blacklisted.map { it.message }

        context.report(
                BLACKLISTED_ANNOTATION,
                node,
                context.getLocation(node as UElement),
                messages.joinToString("; "))
    }
}
