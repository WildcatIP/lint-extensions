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
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import java.util.Collections


private val IMPLEMENTATION = Implementation(
        BlacklistedClassDetector::class.java, Scope.JAVA_FILE_SCOPE)

internal val BLACKLISTED_BASE_CLASS = Issue.create(
        "BlacklistedClass",
        "Use of a blacklisted class",
        "Classes listed in the blacklist may not be used",
        Category.CORRECTNESS,
        8,
        Severity.ERROR,
        IMPLEMENTATION)

class BlacklistedClassDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): MutableList<Class<out UElement>> {
        return Collections.singletonList(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return BlacklistedClassChecker(context, Blacklist(context))
    }
}

private class BlacklistedClassChecker(private val context: JavaContext, private val blacklist: Blacklist)
    : UElementHandler() {

    override fun visitClass(klass: UClass?) {
        val superTypes = klass?.uastSuperTypes
        if ((superTypes == null) || superTypes.isEmpty()) {
            return
        }

        val blacklisted = blacklist.getBlacklistedBaseClasses(HashSet(superTypes.map { it.getQualifiedName()!! }))

        if (blacklisted.isEmpty()) {
            return
        }

        val messages = blacklisted.map { it.message }

        context.report(
                BLACKLISTED_BASE_CLASS,
                klass,
                context.getLocation(klass as UElement),
                messages.joinToString("; "))
    }
}
