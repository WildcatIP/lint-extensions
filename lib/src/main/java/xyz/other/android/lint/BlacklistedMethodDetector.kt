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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.getContainingClass
import java.util.Collections


private val IMPLEMENTATION = Implementation(
        BlacklistedMethodDetector::class.java, Scope.JAVA_FILE_SCOPE)

internal val BLACKLISTED_METHOD = Issue.create(
        "BlacklistedMethod",
        "Use of a blacklisted method",
        "Methods listed in the blacklist may not be used, except in the defining class and its subclasses",
        Category.CORRECTNESS,
        8,
        Severity.ERROR,
        IMPLEMENTATION)

internal val BLACKLISTED_CONSTRUCTOR = Issue.create(
        "BlacklistedConstructor",
        "Use of a blacklisted constructor",
        "Constructors listed in the blacklist may not be used, except in the defining class and its subclasses",
        Category.CORRECTNESS,
        8,
        Severity.ERROR,
        IMPLEMENTATION)

class BlacklistedMethodDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): MutableList<Class<out UElement>> {
        return Collections.singletonList(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return BlacklistedMethodChecker(context, Blacklist(context))
    }
}

private class BlacklistedMethodChecker(private val context: JavaContext, private val blacklist: Blacklist)
    : UElementHandler() {

    override fun visitCallExpression(call: UCallExpression?) {
        val method = call?.resolve() ?: return

        // Allow blacklisted methods in their own class and subclasses
        val callingClass = call.getContainingClass() ?: return
        val definingClass = method.containingClass?.qualifiedName ?: return
        if (context.evaluator.extendsClass(callingClass, definingClass, false)) {
            return
        }

        val forbiddenMethod = blacklist.getBlacklistedInvocation(context, method) ?: return

        context.report(
                if (method.isConstructor) BLACKLISTED_CONSTRUCTOR else BLACKLISTED_METHOD,
                call,
                context.getLocation(call),
                forbiddenMethod.message ?: "Use of ${method.name} is not allowed.")
    }
}
