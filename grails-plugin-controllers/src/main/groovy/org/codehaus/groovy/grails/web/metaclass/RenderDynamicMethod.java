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
package org.codehaus.groovy.grails.web.metaclass;

import grails.async.Promise;
import grails.converters.JSON;
import grails.util.GrailsWebUtil;
import grails.web.JSONBuilder;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.MissingMethodException;
import groovy.lang.Writable;
import groovy.text.Template;
import groovy.util.slurpersupport.GPathResult;
import groovy.xml.StreamingMarkupBuilder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import grails.util.GrailsStringUtils;
import org.codehaus.groovy.grails.io.support.GrailsIOUtils;
import org.codehaus.groovy.grails.io.support.GrailsResourceUtils;
import org.codehaus.groovy.grails.io.support.IOUtils;
import org.codehaus.groovy.grails.plugins.GrailsPlugin;
import org.codehaus.groovy.grails.plugins.GrailsPluginManager;
import org.grails.web.support.ResourceAwareTemplateEngine;
import org.codehaus.groovy.grails.web.converters.Converter;
import org.codehaus.groovy.grails.web.json.JSONElement;
import grails.web.mime.MimeType;
import grails.web.mime.MimeUtility;
import org.codehaus.groovy.grails.web.pages.GroovyPageTemplate;
import grails.web.util.GrailsApplicationAttributes;
import grails.web.http.HttpHeaders;
import org.grails.web.servlet.mvc.ActionResultTransformer;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.servlet.mvc.exceptions.ControllerExecutionException;
import org.codehaus.groovy.grails.web.servlet.view.GroovyPageView;
import org.codehaus.groovy.grails.web.sitemesh.GrailsLayoutDecoratorMapper;
import org.codehaus.groovy.grails.web.sitemesh.GrailsLayoutView;
import org.codehaus.groovy.grails.web.sitemesh.GroovyPageLayoutFinder;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

/**
 * Allows rendering of text, views, and templates to the response
 *
 * @author Graeme Rocher
 * @since 0.2
 */
@SuppressWarnings({"unchecked","rawtypes"})
public class RenderDynamicMethod extends AbstractDynamicMethodInvocation {
    public static final String METHOD_SIGNATURE = "render";
    public static final Pattern METHOD_PATTERN = Pattern.compile('^' + METHOD_SIGNATURE + '$');

    public static final String ARGUMENT_TEXT = "text";
    public static final String ARGUMENT_STATUS = "status";
    public static final String ARGUMENT_LAYOUT = "layout";
    public static final String ARGUMENT_CONTENT_TYPE = "contentType";
    public static final String ARGUMENT_ENCODING = "encoding";
    public static final String ARGUMENT_VIEW = "view";
    public static final String ARGUMENT_MODEL = "model";
    public static final String ARGUMENT_TEMPLATE = "template";
    public static final String ARGUMENT_CONTEXTPATH = "contextPath";
    public static final String ARGUMENT_BEAN = "bean";
    public static final String ARGUMENT_COLLECTION = "collection";
    public static final String ARGUMENT_BUILDER = "builder";
    public static final String ARGUMENT_VAR = "var";
    private static final String DEFAULT_ARGUMENT = "it";
    private static final String BUILDER_TYPE_JSON = "json";

    private static final String TEXT_HTML = "text/html";
    private static final String APPLICATION_XML = "application/xml";
    public static final String DISPOSITION_HEADER_PREFIX = "attachment;filename=";
    private String gspEncoding = DEFAULT_ENCODING;
    private static final String DEFAULT_ENCODING = "utf-8";
    private Object ARGUMENT_PLUGIN = "plugin";
    private static final String ARGUMENT_FILE = "file";
    private static final String ARGUMENT_FILE_NAME = "fileName";
    private MimeUtility mimeUtility;
    private Collection<ActionResultTransformer> actionResultTransformers;

    public RenderDynamicMethod() {
        super(METHOD_PATTERN);
    }

    public void setGspEncoding(String gspEncoding) {
        this.gspEncoding = gspEncoding;
    }

    @Override
    public Object invoke(Object target, String methodName, Object[] arguments) {
        if (arguments.length == 0) {
            throw new MissingMethodException(METHOD_SIGNATURE, target.getClass(), arguments);
        }

        GrailsWebRequest webRequest = (GrailsWebRequest) RequestContextHolder.currentRequestAttributes();
        HttpServletResponse response = webRequest.getCurrentResponse();

        boolean renderView = true;
        GroovyObject controller = (GroovyObject) target;
        
        String explicitSiteMeshLayout = null;
        
        final Object renderArgument = arguments[0];
        if (renderArgument instanceof Converter<?>) {
            renderView = renderConverter((Converter<?>)renderArgument, response);
        } else if (renderArgument instanceof Writable) {
            applyContentType(response, null, renderArgument);
            Writable writable = (Writable)renderArgument;
            renderView = renderWritable(writable, response);
        } else if (renderArgument instanceof CharSequence) {
            applyContentType(response, null, renderArgument);
            CharSequence text = (CharSequence)renderArgument;
            renderView = renderText(text, response);
        }
        else {
            final Object renderObject = arguments[arguments.length - 1];
            if (renderArgument instanceof Closure) {
                setContentType(response, TEXT_HTML, DEFAULT_ENCODING, true);
                Closure closure = (Closure) renderObject;
                renderView = renderMarkup(closure, response);
            }
            else if (renderArgument instanceof Map) {
                Map argMap = (Map) renderArgument;
                
                if (argMap.containsKey(ARGUMENT_LAYOUT)) {
                    explicitSiteMeshLayout = String.valueOf(argMap.get(ARGUMENT_LAYOUT));
                }

                boolean statusSet = false;
                if (argMap.containsKey(ARGUMENT_STATUS)) {
                    Object statusObj = argMap.get(ARGUMENT_STATUS);
                    if (statusObj != null) {
                        try {
                            final int statusCode = statusObj instanceof Number ? ((Number)statusObj).intValue() : Integer.parseInt(statusObj.toString());
                            response.setStatus(statusCode);
                            statusSet = true;
                        }
                        catch (NumberFormatException e) {
                            throw new ControllerExecutionException(
                                    "Argument [status] of method [render] must be a valid integer.");
                        }
                    }
                }
                
                if (renderObject instanceof Writable) {
                    Writable writable = (Writable) renderObject;
                    applyContentType(response, argMap, renderObject);
                    renderView = renderWritable(writable, response);
                } 
                else if (renderObject instanceof Closure) {
                    Closure callable = (Closure) renderObject;
                    applyContentType(response, argMap, renderObject);
                    if (BUILDER_TYPE_JSON.equals(argMap.get(ARGUMENT_BUILDER)) || isJSONResponse(response)) {
                        renderView = renderJSON(callable, response);
                    }
                    else {
                        renderView = renderMarkup(callable, response);
                    }
                }
                else if (renderObject instanceof CharSequence) {
                    applyContentType(response, argMap, renderObject);
                    CharSequence text = (CharSequence) renderObject;
                    renderView = renderText(text, response);
                }
                else if (argMap.containsKey(ARGUMENT_TEXT)) {
                    Object textArg = argMap.get(ARGUMENT_TEXT);
                    applyContentType(response, argMap, textArg);
                    if (textArg instanceof Writable) {
                        Writable writable = (Writable) textArg;
                        renderView = renderWritable(writable, response);
                    } else {                    
                        CharSequence text = (textArg instanceof CharSequence) ? ((CharSequence)textArg) : textArg.toString();
                        renderView = renderText(text, response);
                    }
                }
                else if (argMap.containsKey(ARGUMENT_VIEW)) {
                    renderView(webRequest, argMap, target, controller);
                }
                else if (argMap.containsKey(ARGUMENT_TEMPLATE)) {
                    applyContentType(response, argMap, null, false);
                    renderView = renderTemplate(target, controller, webRequest, argMap, explicitSiteMeshLayout);
                }
                else if (argMap.containsKey(ARGUMENT_FILE)) {
                    renderView = false;

                    Object o = argMap.get(ARGUMENT_FILE);
                    Object fnO = argMap.get(ARGUMENT_FILE_NAME);
                    String fileName = fnO != null ? fnO.toString() : ((o instanceof File) ? ((File)o).getName(): null );
                    if (o != null) {
                        boolean hasContentType = applyContentType(response, argMap, null, false);
                        if (fileName != null) {
                            if(!hasContentType) {
                                hasContentType = detectContentTypeFromFileName(webRequest, response, argMap, fileName);
                            }
                            if (fnO != null) {
                                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, DISPOSITION_HEADER_PREFIX + fileName);
                            }
                        }
                        if (!hasContentType) {
                            throw new ControllerExecutionException(
                                    "Argument [file] of render method specified without valid [contentType] argument");
                        }

                        InputStream input = null;
                        try {
                            if (o instanceof File) {
                                File f = (File) o;
                                input = GrailsIOUtils.openStream(f);
                            }
                            else if (o instanceof InputStream) {
                                input = (InputStream) o;
                            }
                            else if (o instanceof byte[]) {
                                input = new ByteArrayInputStream((byte[])o);
                            }
                            else {
                                input = GrailsIOUtils.openStream(new File(o.toString()));
                            }
                            IOUtils.copy(input, response.getOutputStream());
                        } catch (IOException e) {
                            throw new ControllerExecutionException(
                                    "I/O error copying file to response: " + e.getMessage(), e);

                        }
                        finally {
                            if (input != null) {
                                try {
                                    input.close();
                                } catch (IOException e) {
                                    // ignore
                                }
                            }
                        }
                    }
                }
                else if (statusSet) {
                    // GRAILS-6711 nothing to render, just setting status code, so don't render the map
                    renderView = false;
                }
                else {
                    Object object = renderArgument;
                    if (object instanceof JSONElement) {
                        renderView = renderJSON((JSONElement)object, response);
                    }
                    else{
                        try {
                            renderView = renderObject(object, response.getWriter());
                        }
                        catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
            else {
                throw new MissingMethodException(METHOD_SIGNATURE, target.getClass(), arguments);
            }
        }
        applySiteMeshLayout(webRequest.getCurrentRequest(), renderView, explicitSiteMeshLayout);
        webRequest.setRenderView(renderView);
        return null;
    }

    private void applySiteMeshLayout(HttpServletRequest request, boolean renderView, String explicitSiteMeshLayout) {
        if(explicitSiteMeshLayout == null && request.getAttribute(GrailsLayoutDecoratorMapper.LAYOUT_ATTRIBUTE) != null) {
            // layout has been set already
            return;
        }
        String siteMeshLayout = explicitSiteMeshLayout != null ? explicitSiteMeshLayout : (renderView ? null : GrailsLayoutDecoratorMapper.NONE_LAYOUT);
        if(siteMeshLayout != null) {
            request.setAttribute(GrailsLayoutDecoratorMapper.LAYOUT_ATTRIBUTE, siteMeshLayout);
        }
    }

    private boolean renderConverter(Converter<?> converter, HttpServletResponse response) {
        converter.render(response);
        return false;
    }

    private String resolveContentTypeBySourceType(final Object renderArgument, String defaultEncoding) {
        return renderArgument instanceof GPathResult ? APPLICATION_XML : defaultEncoding;
    }
    
    private boolean applyContentType(HttpServletResponse response, Map argMap, Object renderArgument) {
        return applyContentType(response, argMap, renderArgument, true);
    }

    private boolean applyContentType(HttpServletResponse response, Map argMap, Object renderArgument, boolean useDefault) {
        boolean contentTypeIsDefault = true;
        String contentType = resolveContentTypeBySourceType(renderArgument, useDefault ? TEXT_HTML : null);
        String encoding = DEFAULT_ENCODING;
        if (argMap != null) {
            if(argMap.containsKey(ARGUMENT_CONTENT_TYPE)) {
                contentType = argMap.get(ARGUMENT_CONTENT_TYPE).toString();
                contentTypeIsDefault = false;
            }
            if(argMap.containsKey(ARGUMENT_ENCODING)) {
                encoding = argMap.get(ARGUMENT_ENCODING).toString();
                contentTypeIsDefault = false;
            }
        }
        if(contentType != null) {
            setContentType(response, contentType, encoding, contentTypeIsDefault);
            return true;
        }
        return false;
    }

    private boolean renderJSON(JSONElement object, HttpServletResponse response) {
        response.setContentType(GrailsWebUtil.getContentType("application/json", DEFAULT_ENCODING));
        return renderWritable(object, response);
    }

    private boolean detectContentTypeFromFileName(GrailsWebRequest webRequest, HttpServletResponse response, Map argMap, String fileName) {
        MimeUtility mimeUtility = lookupMimeUtility(webRequest);
        if (mimeUtility != null) {
            MimeType mimeType = mimeUtility.getMimeTypeForExtension(GrailsStringUtils.getFilenameExtension(fileName));
            if (mimeType != null) {
                String contentType = mimeType.getName();
                Object encodingObj = argMap.get(ARGUMENT_ENCODING);
                String encoding = encodingObj != null ? encodingObj.toString() : DEFAULT_ENCODING;
                setContentType(response, contentType, encoding);
                return true;
            }
        }
        return false;
    }

    private MimeUtility lookupMimeUtility(GrailsWebRequest webRequest) {
        if (mimeUtility == null) {
            ApplicationContext applicationContext = webRequest.getApplicationContext();
            if (applicationContext != null) {
                mimeUtility = applicationContext.getBean("grailsMimeUtility", MimeUtility.class);
            }
        }
        return mimeUtility;
    }
    
    private boolean renderTemplate(Object target, GroovyObject controller, GrailsWebRequest webRequest,
            Map argMap, String explicitSiteMeshLayout) {
        boolean renderView;
        boolean hasModel = argMap.containsKey(ARGUMENT_MODEL);
        Object modelObject = null;
        if(hasModel) {
            modelObject = argMap.get(ARGUMENT_MODEL);
        }
        String templateName = argMap.get(ARGUMENT_TEMPLATE).toString();
        String contextPath = getContextPath(webRequest, argMap);

        String var = null;
        if (argMap.containsKey(ARGUMENT_VAR)) {
            var = String.valueOf(argMap.get(ARGUMENT_VAR));
        }

        // get the template uri
        String templateUri = webRequest.getAttributes().getTemplateURI(controller, templateName);

        // retrieve gsp engine
        ResourceAwareTemplateEngine engine = webRequest.getAttributes().getPagesTemplateEngine();
        try {
            Template t = engine.createTemplateForUri(new String[]{
                    GrailsResourceUtils.appendPiecesForUri(contextPath, templateUri),
                    GrailsResourceUtils.appendPiecesForUri(contextPath, "/grails-app/views/", templateUri)});

            if (t == null) {
                throw new ControllerExecutionException("Unable to load template for uri [" +
                        templateUri + "]. Template not found.");
            }

            if (t instanceof GroovyPageTemplate) {
                ((GroovyPageTemplate)t).setAllowSettingContentType(true);
            }
            
            GroovyPageView gspView = new GroovyPageView();
            gspView.setTemplate(t);
            try {
                gspView.afterPropertiesSet();
            } catch (Exception e) {
                throw new RuntimeException("Problem initializing view", e);
            }
            
            View view = gspView;            
            boolean renderWithLayout = (explicitSiteMeshLayout != null || webRequest.getCurrentRequest().getAttribute(GrailsLayoutDecoratorMapper.LAYOUT_ATTRIBUTE) != null);
            if(renderWithLayout) {
                applySiteMeshLayout(webRequest.getCurrentRequest(), false, explicitSiteMeshLayout);
                try {
                    GroovyPageLayoutFinder groovyPageLayoutFinder = webRequest.getApplicationContext().getBean("groovyPageLayoutFinder", GroovyPageLayoutFinder.class);
                    view = new GrailsLayoutView(groovyPageLayoutFinder, gspView);
                } catch (NoSuchBeanDefinitionException e) {
                    // ignore
                }
            }

            Map binding = new HashMap();

            if (argMap.containsKey(ARGUMENT_BEAN)) {
                Object bean = argMap.get(ARGUMENT_BEAN);
                if (hasModel) {
                    if (modelObject instanceof Map) {
                        setTemplateModel(webRequest, binding, (Map) modelObject);
                    }
                }
                renderTemplateForBean(webRequest, view, binding, bean, var);
            }
            else if (argMap.containsKey(ARGUMENT_COLLECTION)) {
                Object colObject = argMap.get(ARGUMENT_COLLECTION);
                if (hasModel) {
                    if (modelObject instanceof Map) {
                        setTemplateModel(webRequest, binding, (Map)modelObject);
                    }
                }
                renderTemplateForCollection(webRequest, view, binding, colObject, var);
            }
            else if (hasModel) {
                if (modelObject instanceof Map) {
                    setTemplateModel(webRequest, binding, (Map)modelObject);
                }
                renderViewForTemplate(webRequest, view, binding);
            }
            else {
                renderViewForTemplate(webRequest, view, binding);
            }
            renderView = false;
        }
        catch (GroovyRuntimeException gre) {
            throw new ControllerExecutionException("Error rendering template [" + templateName + "]: " + gre.getMessage(), gre);
        }
        catch (IOException ioex) {
            throw new ControllerExecutionException("I/O error executing render method for arguments [" + argMap + "]: " + ioex.getMessage(), ioex);
        }
        return renderView;
    }

    protected void renderViewForTemplate(GrailsWebRequest webRequest, View view, Map binding) {
        try {
            view.render(binding, webRequest.getCurrentRequest(), webRequest.getResponse());
        }
        catch (Exception e) {
            throw new ControllerExecutionException(e.getMessage(), e);
        }
    }

    protected Collection<ActionResultTransformer> getActionResultTransformers(GrailsWebRequest webRequest) {
        if (actionResultTransformers == null) {

            ApplicationContext applicationContext = webRequest.getApplicationContext();
            if (applicationContext != null) {
                actionResultTransformers = applicationContext.getBeansOfType(ActionResultTransformer.class).values();
            }
            if (actionResultTransformers == null) {
                actionResultTransformers = Collections.emptyList();
            }
        }

        return actionResultTransformers;
    }

    private void setTemplateModel(GrailsWebRequest webRequest, Map binding, Map modelObject) {
        Map modelMap = modelObject;
        webRequest.setAttribute(GrailsApplicationAttributes.TEMPLATE_MODEL, modelMap, RequestAttributes.SCOPE_REQUEST);
        binding.putAll(modelMap);
    }

    private String getContextPath(GrailsWebRequest webRequest, Map argMap) {
        Object cp = argMap.get(ARGUMENT_CONTEXTPATH);
        String contextPath = (cp != null ? cp.toString() : "");

        Object pluginName = argMap.get(ARGUMENT_PLUGIN);
        if (pluginName != null) {
            ApplicationContext applicationContext = webRequest.getApplicationContext();
            GrailsPluginManager pluginManager = (GrailsPluginManager) applicationContext.getBean(GrailsPluginManager.BEAN_NAME);
            GrailsPlugin plugin = pluginManager.getGrailsPlugin(pluginName.toString());
            if (plugin != null && !plugin.isBasePlugin()) contextPath = plugin.getPluginPath();
        }
        return contextPath;
    }

    private void setContentType(HttpServletResponse response, String contentType, String encoding) {
        setContentType(response, contentType, encoding, false);
    }

    private void setContentType(HttpServletResponse response, String contentType, String encoding, boolean contentTypeIsDefault) {
        if (!contentTypeIsDefault || response.getContentType()==null) {
            response.setContentType(GrailsWebUtil.getContentType(contentType, encoding));
        }
    }

    private boolean renderObject(Object object, Writer out) {
        boolean renderView;
        try {
            out.write(DefaultGroovyMethods.inspect(object));
            renderView = false;
        }
        catch (IOException e) {
            throw new ControllerExecutionException("I/O error obtaining response writer: " + e.getMessage(), e);
        }
        return renderView;
    }

    private void renderTemplateForCollection(GrailsWebRequest webRequest, View view, Map binding, Object colObject, String var) throws IOException {
        if (colObject instanceof Iterable) {
            Iterable c = (Iterable) colObject;
            for (Object o : c) {
                if (GrailsStringUtils.isBlank(var)) {
                    binding.put(DEFAULT_ARGUMENT, o);
                }
                else {
                    binding.put(var, o);
                }
                renderViewForTemplate(webRequest, view, binding);
            }
        }
        else {
            if (GrailsStringUtils.isBlank(var)) {
                binding.put(DEFAULT_ARGUMENT, colObject);
            }
            else {
                binding.put(var, colObject);
            }

            renderViewForTemplate(webRequest, view, binding);
        }
    }

    private void renderTemplateForBean(GrailsWebRequest webRequest, View view, Map binding, Object bean, String varName) throws IOException {
        if (GrailsStringUtils.isBlank(varName)) {
            binding.put(DEFAULT_ARGUMENT, bean);
        }
        else {
            binding.put(varName, bean);
        }
        renderViewForTemplate(webRequest, view, binding);
    }

    private void renderView(GrailsWebRequest webRequest, Map argMap, Object target, GroovyObject controller) {
        String viewName = argMap.get(ARGUMENT_VIEW).toString();
        String viewUri = webRequest.getAttributes().getNoSuffixViewURI((GroovyObject) target, viewName);
        String contextPath = getContextPath(webRequest, argMap);
        if(contextPath != null) {
            viewUri = contextPath + viewUri;
        }
        Object modelObject = argMap.get(ARGUMENT_MODEL);
        if (modelObject != null) {
            modelObject = argMap.get(ARGUMENT_MODEL);
            boolean isPromise = modelObject instanceof Promise;
            Collection<ActionResultTransformer> resultTransformers = getActionResultTransformers(webRequest);
            for (ActionResultTransformer resultTransformer : resultTransformers) {
                modelObject = resultTransformer.transformActionResult(webRequest,viewUri, modelObject);
            }
            if (isPromise) return;
        }
        
        applyContentType(webRequest.getCurrentResponse(), argMap, null);

        Map model;
        if (modelObject instanceof Map) {
            model = (Map) modelObject;
        }
        else {
            model = new HashMap();
        }

        controller.setProperty(ControllerDynamicMethods.MODEL_AND_VIEW_PROPERTY, new ModelAndView(viewUri, model));
    }

    private boolean renderJSON(Closure callable, HttpServletResponse response) {
        boolean renderView = true;
        JSONBuilder builder = new JSONBuilder();
        JSON json = builder.build(callable);
        json.render(response);
        renderView = false;
        return renderView;
    }

    private boolean renderMarkup(Closure closure, HttpServletResponse response) {
        StreamingMarkupBuilder b = new StreamingMarkupBuilder();
        b.setEncoding(response.getCharacterEncoding());
        Writable markup = (Writable) b.bind(closure);
        return renderWritable(markup, response);
    }

    private boolean renderText(CharSequence text, HttpServletResponse response) {
        try {
            PrintWriter writer = response.getWriter();
            return renderText(text, writer);
        }
        catch (IOException e) {
            throw new ControllerExecutionException(e.getMessage(), e);
        }
    }
    
    private boolean renderWritable(Writable writable, HttpServletResponse response) {
        try {
            PrintWriter writer = response.getWriter();
            writable.writeTo(writer);
            writer.flush();
        }
        catch (IOException e) {
            throw new ControllerExecutionException(e.getMessage(), e);
        }
        return false;
    }

    private boolean renderText(CharSequence text, Writer writer) {
        try {
            if (writer instanceof PrintWriter) {
                ((PrintWriter)writer).print(text);
            }
            else {
                writer.write(text.toString());
            }
            writer.flush();
            return false;
        }
        catch (IOException e) {
            throw new ControllerExecutionException(e.getMessage(), e);
        }
    }

    private boolean isJSONResponse(HttpServletResponse response) {
        String contentType = response.getContentType();
        return contentType != null && (contentType.indexOf("application/json") > -1 ||
               contentType.indexOf("text/json") > -1);
    }
}
