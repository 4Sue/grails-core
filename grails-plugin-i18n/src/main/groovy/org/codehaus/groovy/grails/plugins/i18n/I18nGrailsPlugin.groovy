/*
 * Copyright 2004-2005 the original author or authors.
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
package org.codehaus.groovy.grails.plugins.i18n

import grails.util.BuildSettingsHolder
import grails.util.Environment
import grails.util.GrailsUtil
import groovy.transform.CompileStatic

import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.grails.cli.logging.GrailsConsoleAntBuilder
import org.codehaus.groovy.grails.commons.GrailsStringUtils
import org.codehaus.groovy.grails.context.support.PluginAwareResourceBundleMessageSource
import org.codehaus.groovy.grails.context.support.ReloadableResourceBundleMessageSource
import org.codehaus.groovy.grails.plugins.GrailsPluginUtils
import org.codehaus.groovy.grails.web.context.GrailsConfigUtils
import org.codehaus.groovy.grails.web.i18n.ParamsAwareLocaleChangeInterceptor
import org.codehaus.groovy.grails.web.pages.GroovyPagesTemplateEngine
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.ContextResource
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.web.context.support.ServletContextResourcePatternResolver
import org.springframework.web.servlet.i18n.SessionLocaleResolver

/**
 * Configures Grails' internationalisation support.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
class I18nGrailsPlugin {

    private static LOG = LogFactory.getLog(this)

    String baseDir = "grails-app/i18n"
    String version = GrailsUtil.getGrailsVersion()
    String watchedResources = "file:./${baseDir}/**/*.properties".toString()

    def doWithSpring = {
        // find i18n resource bundles and resolve basenames
        Set baseNames = ['WEB-INF/grails-app/i18n/messages']

        if (Environment.isWarDeployed()) {
            servletContextResourceResolver(ServletContextResourcePatternResolver, ref('servletContext'))
        }

        messageSource(PluginAwareResourceBundleMessageSource) {
            basenames = baseNames.toArray()
            fallbackToSystemLocale = false
            pluginManager = manager
            if (Environment.current.isReloadEnabled() || GrailsConfigUtils.isConfigTrue(application, GroovyPagesTemplateEngine.CONFIG_PROPERTY_GSP_ENABLE_RELOAD)) {
                def cacheSecondsSetting = application?.flatConfig?.get('grails.i18n.cache.seconds')
                cacheSeconds = cacheSecondsSetting == null ? 5 : cacheSecondsSetting as Integer
                def fileCacheSecondsSetting = application?.flatConfig?.get('grails.i18n.filecache.seconds')
                fileCacheSeconds = fileCacheSecondsSetting == null ? 5 : fileCacheSecondsSetting as Integer
            }
            if (Environment.isWarDeployed()) {
                resourceResolver = ref('servletContextResourceResolver')
            }
        }

        localeResolver(SessionLocaleResolver)
        localeChangeInterceptor(ParamsAwareLocaleChangeInterceptor) {
            paramName = "lang"
            localeResolver = ref("localeResolver")
        }
    }



    def isChildOfFile(File child, File parent) {
        def currentFile = child.canonicalFile
        while(currentFile != null) {
            if (currentFile == parent) {
                return true
            }
            currentFile = currentFile.parentFile
        }
        return false
    }

    def relativePath(File relbase, File file) {
        def pathParts = []
        def currentFile = file
        while (currentFile != null && currentFile != relbase) {
            pathParts += currentFile.name
            currentFile = currentFile.parentFile
        }
        pathParts.reverse().join('/')
    }

    def findGrailsPluginDir(File propertiesFile) {
        File currentFile = propertiesFile.canonicalFile
        File previousFile = null
        while (currentFile != null) {
            if (currentFile.name == 'grails-appgrai' && previousFile?.name == 'i18n') {
                return currentFile.parentFile
            }
            previousFile = currentFile
            currentFile = currentFile.parentFile
        }
        null
    }

    def onChange = { event ->
        def ctx = event.ctx
        if (!ctx) {
            LOG.debug("Application context not found. Can't reload")
            return
        }

        def resourcesDir = BuildSettingsHolder.settings?.resourcesDir?.path
        if (resourcesDir && event.source instanceof Resource) {
            def eventFile = event.source.file.canonicalFile
            def nativeascii = event.application.config.grails.enable.native2ascii
            nativeascii = (nativeascii instanceof Boolean) ? nativeascii : true
            def ant = new GrailsConsoleAntBuilder()
            File appI18nDir = new File("./grails-app/i18n").canonicalFile
            if (isChildOfFile(eventFile, appI18nDir)) {
                String i18nDir = "${resourcesDir}/grails-app/i18n"

                def eventFileRelative = relativePath(appI18nDir, eventFile)

                if (nativeascii) {
                    ant.native2ascii(src:"./grails-app/i18n", dest:i18nDir,
                                     includes:eventFileRelative, encoding:"UTF-8")
                }
                else {
                    ant.copy(todir:i18nDir) {
                        fileset(dir:"./grails-app/i18n", includes:eventFileRelative)
                    }
                }
            } else {
                def pluginDir = findGrailsPluginDir(eventFile)

                if (pluginDir) {
                    def info = event.manager.userPlugins.find { plugin ->
                        plugin.pluginDir?.file?.canonicalFile == pluginDir
                    }

                    if (info) {
                        def pluginI18nDir = new File(pluginDir, "grails-app/i18n")
                        def eventFileRelative = relativePath(pluginI18nDir, eventFile)

                        def destDir = "${resourcesDir}/plugins/${info.fileSystemName}/grails-app/i18n"

                        ant.mkdir(dir: destDir)
                        if (nativeascii) {
                            ant.native2ascii(src: pluginI18nDir.absolutePath, dest: destDir,
                                             includes: eventFileRelative, encoding: "UTF-8")
                        } else {
                            ant.copy(todir:destDir) {
                                fileset(dir:pluginI18nDir.absolutePath, includes:eventFileRelative)
                            }
                        }
                    }
                }
            }
        }

        def messageSource = ctx.messageSource
        if (messageSource instanceof ReloadableResourceBundleMessageSource) {
            messageSource.clearCache()
        }
        else {
            LOG.warn "Bean messageSource is not an instance of ${ReloadableResourceBundleMessageSource.name}. Can't reload"
        }
    }
}
