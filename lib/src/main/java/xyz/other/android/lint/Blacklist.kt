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

import com.android.tools.lint.detector.api.JavaContext
import com.android.utils.XmlUtils
import com.intellij.psi.PsiMethod
import org.w3c.dom.Element
import java.io.File
import java.util.Arrays
import java.util.HashSet
import java.util.regex.Pattern


const private val PROP_BLACKLIST_FILE = "lint.blacklist"
const private val DEFAULT_BLACKLIST_FILE = "tools/lint/blacklist.xml"

const private val TAG_ROOT = "blacklist"
const private val TAG_METHOD = "method"
const private val TAG_CONSTRUCTOR = "constructor"
const private val TAG_ANNOTATION = "annotation"
const private val TAG_BASE_CLASS = "base-class"
const private val TAG_JAVADOC = "javadoc"
const private val TAG_COMMENT = "#comment"
const private val TAG_TEXT = "#text"
const private val ATTR_CLASS = "class"
const private val ATTR_NAME = "name"
const private val ATTR_PARAMS = "params"
const private val ATTR_MESSAGE = "message"

class JavadocTag(private val name: String, val message: String) {
    override fun toString(): String {
        return "JavadocTag(message='$message')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JavadocTag

        if (name != other.name) return false
        if (message != other.message) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + message.hashCode()
        return result
    }
}

class Annotation(private val className: String, val message: String) {
    override fun toString(): String {
        return "Annotation(className='$className')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Annotation

        if (className != other.className) return false
        if (message != other.message) return false

        return true
    }

    override fun hashCode(): Int {
        var result = className.hashCode()
        result = 31 * result + message.hashCode()
        return result
    }
}

class BaseClass(private val className: String, val message: String) {
    override fun toString(): String {
        return "BaseClass(className='$className')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BaseClass

        if (className != other.className) return false
        if (message != other.message) return false

        return true
    }

    override fun hashCode(): Int {
        var result = className.hashCode()
        result = 31 * result + message.hashCode()
        return result
    }
}

class Method(
        private val name: String,
        private val className: String,
        private val formalParams: Array<String>?,
        val message: String?) {
    fun matches(context: JavaContext, method: PsiMethod): Boolean {
        return if (formalParams == null) true else context.evaluator.parametersMatch(method, *formalParams)
    }

    override fun toString(): String {
        return "Method(name='$name', className='$className', formalParams=${Arrays.toString(formalParams)})"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Method

        if (name != other.name) return false
        if (className != other.className) return false
        if (!Arrays.equals(formalParams, other.formalParams)) return false
        if (message != other.message) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + className.hashCode()
        result = 31 * result + (formalParams?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (message?.hashCode() ?: 0)
        return result
    }
}

/*
 *
 * Blacklist file syntax.
 *   The blacklist file is an XML file.  The root element is 'blacklist'.  Within the root
 *   there are four recognized tags.  Unrecognized tags and missing required attributes will cause
 *   lint to abort.  Unrecognized attributes will be ignored.
 *
 * Forbidden methods and constructors: 'method', or 'constructor'
 * - class (required):   The fully qualified name of the containing class (eg. 'java.lang.String').
 * - name (required):    The name of the method (eg. 'onCreate').  Not required and ignored for 'constructor'
 * - params (optional):  Method parameters. If absent, the rule matches any method named <name>
 *                       If present, a comma-separated lists of argument types.
 *                       (eg. 'java.util.Locale, java.lang.String').
 *                       An empty string describes method with no arguments
 * - message (optional): The message that will appear in the error report if the method is found.
 *
 * Note that blacklisted methods are allowed both in the class that declares them and its subclasses.
 * Disable the inspection for a particular method call by annotating the call with the comment:
 *   @Suppress("BlacklistedMethod").
 *
 * Forbidden base classes 'base-class'
 * - class: The fully qualified name of the class (eg. 'java.lang.ArrayList').
 * - message: The message that will appear in the error report if the class is extended.
 *
 * Forbidden annotations: 'annotation'
 * - class: The fully qualified name of the annotation (eg. 'org.jetbrains.annotations.NotNull').
 * - message: A message that will be added to the error report if the annotations is used.
 *
 * !!! FIXME : Didn't find a way to visit javadoc comments with a UastScanner.
 * Disallowed Javadoc tags: 'javadoc'
 * - name: The name of the tag, with of without @ (eg. '@author').
 * - message: A message that will be added to the error report whenever the method is found.
 */
class Blacklist(context: JavaContext) {
    init {
        try {
            loadBlackList(context, System.getProperty(PROP_BLACKLIST_FILE) ?: DEFAULT_BLACKLIST_FILE)
        } catch (e: Exception) {
            abort("Failed loading blacklist: ${e}")
            throw e
        }
    }

    /**
     * Return the corresponding blacklisted method/constructor or null if it is not blacklisted
     */
    fun getBlacklistedInvocation(context: JavaContext, method: PsiMethod): Method? {
        return lookupInvocation(
                context,
                method,
                if (method.isConstructor) blacklistedConstructors else blacklistedMethods)
    }

    /**
     * Return the corresponding blacklisted base class or null if it is not blacklisted
     */
    fun getBlacklistedBaseClasses(baseClasses: Set<String>): Set<BaseClass> {
        val blacklisted = HashSet(baseClasses)
        blacklisted.retainAll(blacklistedBaseClasses.keys)
        return HashSet(blacklisted.map { it -> blacklistedBaseClasses[it]!! })
    }

    /**
     * Return the corresponding blacklisted annotations or null if it is not blacklisted
     */
    fun getBlacklistedAnnotations(annotations: Set<String>): Set<Annotation> {
        val blacklisted = HashSet(annotations)
        blacklisted.retainAll(blacklistedAnnotations.keys)
        return HashSet(blacklisted.map { it -> blacklistedAnnotations[it]!! })
    }

    /**
     * Return the corresponding blacklisted javadoc tags or null if it is not blacklisted
     */
    fun getBlacklistedJavadocTags(tags: Set<String>): Set<JavadocTag> {
        val blacklisted = HashSet(tags)
        blacklisted.retainAll(blacklistedJavadocTags.keys)
        return HashSet(blacklisted.map { it -> blacklistedJavadocTags[it]!! })
    }

    private fun lookupInvocation(context: JavaContext, method: PsiMethod, methodMap: Map<String, Set<Method>>): Method? {
        return methodMap[method.name]?.firstOrNull { it.matches(context, method) }
    }

    // loader singleton
    companion object {
        private var loaded = false

        private val NO_ARGS = arrayOf<String>()
        private val ARGUMENT_SPLITTER = Pattern.compile("\\s*,\\s*")

        /*
         * A map of blacklisted methods indexed by their name.
         */
        private val blacklistedMethods = mutableMapOf<String, MutableSet<Method>>()

        /*
         * A map of blacklisted constructors indexed by class name.
         */
        private val blacklistedConstructors = mutableMapOf<String, MutableSet<Method>>()

        /*
         * A map of blacklisted annotations indexed by annotation class name.
         */
        private val blacklistedAnnotations = mutableMapOf<String, Annotation>()

        /*
         * A map of blacklisted classes indexed by class name.
         */
        private val blacklistedBaseClasses = mutableMapOf<String, BaseClass>()

        /*
         * A list of blacklisted javadoc tags.
         */
        private val blacklistedJavadocTags = mutableMapOf<String, JavadocTag>()

        fun loadBlackList(context: JavaContext, blacklistPath: String) {
            if (loaded) {
                return
            }

            val root = XmlUtils.parseDocument(context.client.readFile(File(blacklistPath)).toString(), false)
                    ?.firstChild
                    ?: abort("Failed reading file: ${blacklistPath}")

            if (TAG_ROOT != root.nodeName) {
                abort("Invalid root node: ${root.nodeName}")
            }

            val nodes = root.childNodes
            val count = nodes.length
            (0 until count)
                    .map { nodes.item(it) }
                    .forEach {
                        when (it.nodeName) {
                            TAG_COMMENT, TAG_TEXT -> {
                            }
                            TAG_METHOD -> {
                                parseMethodOrConstructor(it as Element, false, blacklistedMethods)
                            }
                            TAG_CONSTRUCTOR -> {
                                parseMethodOrConstructor(it as Element, true, blacklistedConstructors)
                            }
                            TAG_ANNOTATION -> {
                                parseAnnotation(it as Element, blacklistedAnnotations)
                            }
                            TAG_BASE_CLASS -> {
                                parseBaseClass(it as Element, blacklistedBaseClasses)
                            }
                            TAG_JAVADOC -> {
                                parseJavadocTag(it as Element, blacklistedJavadocTags)
                            }
                            else -> {
                                throw IllegalArgumentException("Unknown element type: " + it.nodeName)
                            }
                        }
                    }

            loaded = true
        }

        private fun parseMethodOrConstructor(element: Element, isCtor: Boolean, methods: MutableMap<String, MutableSet<Method>>) {
            val className = element.getAttribute(ATTR_CLASS)
            if (className.isEmpty()) {
                abort("Missing required attribute '$ATTR_CLASS'")
            }

            val methodName = if (isCtor) {
                className.substringAfterLast('.')
            } else {
                element.getAttribute(ATTR_NAME)
            }
            if (methodName.isEmpty()) {
                abort("Missing required attribute '$ATTR_NAME'")
            }

            val paramsAttr = element.getAttributeNode(ATTR_PARAMS)?.value
            val formalParams = if (paramsAttr == null) {
                null
            } else {
                if (paramsAttr.isEmpty()) NO_ARGS else ARGUMENT_SPLITTER.split(paramsAttr)
            }

            val message = element.getAttribute(ATTR_MESSAGE)

            var blacklistedMethods: MutableSet<Method>? = methods[methodName]
            if (blacklistedMethods == null) {
                blacklistedMethods = mutableSetOf()
                methods.put(methodName, blacklistedMethods)
            }
            blacklistedMethods.add(Method(methodName, className, formalParams, message))
        }

        private fun parseAnnotation(element: Element, annotationMap: MutableMap<String, Annotation>) {
            val className = element.getAttribute(ATTR_CLASS)
            if (className.isEmpty()) {
                abort("Missing required attribute '$ATTR_CLASS'")
            }

            val message = element.getAttribute(ATTR_MESSAGE)

            annotationMap.put(className, Annotation(className, message))
        }

        private fun parseBaseClass(element: Element, baseClassMap: MutableMap<String, BaseClass>) {
            val className = element.getAttribute(ATTR_CLASS)
            if (className.isEmpty()) {
                abort("Missing required attribute '$ATTR_CLASS'")
            }

            val message = element.getAttribute(ATTR_MESSAGE)

            baseClassMap.put(className, BaseClass(className, message))
        }

        private fun parseJavadocTag(element: Element, javadocTags: MutableMap<String, JavadocTag>) {
            val tagAttr = element.getAttribute(ATTR_NAME)
            if (tagAttr.isEmpty()) {
                abort("Missing required attribute '$ATTR_NAME'")
            }

            val tagName = if (!tagAttr.startsWith("@")) tagAttr else tagAttr.substring(1)

            val message = element.getAttribute(ATTR_MESSAGE)

            javadocTags.put(tagName, JavadocTag(tagName, message))
        }

        private fun abort(message: String, vararg args: Any): Nothing {
            throw IllegalArgumentException(
                    "Failed parsing blacklist configuration file: "
                            + if (args.isNotEmpty()) message else String.format(message, *args))
        }
    }
}
