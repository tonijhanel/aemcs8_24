package com.adobeags975jan2024.core.wf;/**
 * Created by Douglas Prevelige on 5/22/2024.
 * Non-production code for POC purposes only.
 */

import com.adobe.aemfd.docmanager.Document;
import com.adobe.granite.asset.api.Asset;
import com.adobe.granite.asset.api.AssetManager;
import com.adobe.granite.workflow.exec.*;
import com.adobe.granite.workflow.*;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.adobe.granite.workflow.model.VariableTemplate;
import com.adobe.granite.workflow.model.WorkflowModel;

import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component(service = WorkflowProcess.class, property = {"process.label=DocVarToAsset", "process.description=Description Value"})
public class DocVarToAsset implements WorkflowProcess {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final String TYPE_JCR_PATH = "JCR_PATH";
    private static final String TYPE_URL = "URL";

    public void execute(WorkItem item, WorkflowSession wfsession, MetaDataMap args) throws WorkflowException {
        WorkflowData workflowData = item.getWorkflowData();
        String path = "";
        log.info("*****  WF Step DocVarToAsset *********");
        if (workflowData.getPayloadType().equals(TYPE_JCR_PATH)) {
            path = workflowData.getPayload().toString();
            log.info("path: " + path);
        }
        WorkflowModel wfModel = item.getWorkflow().getWorkflowModel();
        Map<String, VariableTemplate> variables = wfModel.getVariableTemplates();
        for (String varName : variables.keySet()) {
            VariableTemplate var = variables.get(varName);
            log.info("Variable: " + varName + " Type: " + var.getType());
        }
        for (String key : args.keySet()) {
            log.info("Key: " + key + "  Value: " + args.get(key, ""));
        }
        MetaDataMap metaDataMap = workflowData.getMetaDataMap();
        String assetFolder = getCategorizedPath("assetfolder", path, args, metaDataMap);
        String assetName = getCategorizedPath("assetname", path, args, metaDataMap);
        if (assetName.length() == 0) {
            assetName = "asset";
        }
        InputStream is = null;
        String mimeType = "application/pdf";

        String assetVar = args.get("assetvar", String.class);
        if (assetVar != null) {
            if (assetVar.startsWith("VARIABLE:")) {
                assetVar = assetVar.substring("VARIABLE:".length());
            }
            if (variables.containsKey(assetVar)) {
                VariableTemplate var = variables.get(assetVar);
                log.info("Variable: " + assetVar + " Type: " + var.getType());
                if (var.getType().equalsIgnoreCase("org.w3c.dom.Document")) {
                    org.w3c.dom.Document xmlDocument = metaDataMap.get(assetVar, org.w3c.dom.Document.class);
                    try {
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        Source xmlSource = new DOMSource(xmlDocument);
                        Result outputTarget = new StreamResult(outputStream);
                        TransformerFactory.newInstance().newTransformer().transform(xmlSource, outputTarget);
                        is = new ByteArrayInputStream(outputStream.toByteArray());
                        if (!assetName.toLowerCase(Locale.ROOT).endsWith(".xml") || !assetName.toLowerCase(Locale.ROOT).endsWith(".xdp")) {
                            assetName = assetName + ".xml";
                        }
                        mimeType = "application/xml";
                    } catch (TransformerException e) {
                        throw new RuntimeException(e);
                    }
                } else if (var.getType().equalsIgnoreCase("com.adobe.aemfd.docmanager.Document")) {
                    Document pdfdoc = metaDataMap.get(assetVar, Document.class);
                    if (pdfdoc != null) {
                        try {
                            is = pdfdoc.getInputStream();
                            if (!assetName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                                assetName = assetName + ".pdf";
                            }
                        } catch (IOException e) {
                            log.error("Error!", e);
                        }
                    }
                }
            }
            if (is != null) {
                ResourceResolver resolver = wfsession.adaptTo(ResourceResolver.class);
                AssetManager assetManager = resolver.adaptTo(AssetManager.class);
                try {
                    String assetPath = assetFolder + "/" + assetName;
                    log.info("Asset Path: " + assetPath);
                    Asset newAsset;
                    if (!assetManager.assetExists(assetPath)) {
                        newAsset = assetManager.createAsset(assetPath);
                    } else {
                        newAsset = assetManager.getAsset(assetPath);
                    }

                    Map<String, Object> map = new HashMap<String, Object>();
                    map.put("jcr:mimeType", mimeType);
                    newAsset.setRendition("original", is, map);
                    String saveTo = args.get("saveto", "");
                    if (saveTo.startsWith("VARIABLE:")) {
                        saveTo = saveTo.substring("VARIABLE:".length());
                    }
                    log.info("Save To: " + saveTo);
                    if (saveTo.length() > 0) {
                        MetaDataMap wfMetaData = item.getWorkflow().getWorkflowData().getMetaDataMap();
                        wfMetaData.put(saveTo, assetPath);
                    }
                    resolver.commit();

                } catch (PersistenceException e) {
                    log.error("Error creating asset", e);
                }
            }
        }
    }

    String getCategorizedPath(String key, String path, MetaDataMap args, MetaDataMap wfMetaData) {
        String value = args.get(key, "");
        if (value.startsWith("ABSOLUTE_PATH:")) {
            value = value.substring("ABSOLUTE_PATH:".length());
        }
        if (value.startsWith("VARIABLE:")) {
            value = value.substring("VARIABLE:".length());
            value = wfMetaData.get(value, java.lang.String.class);
        }
        if (value.startsWith("RELATIVE_PLOAD:")) {
            value = value.substring("RELATIVE_PLOAD:".length());
            value = path + "/" + value;
        }
        if (value.startsWith("WF_METADATA:")) {
            value = value.substring("WF_METADATA:".length());
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        log.info("Value: " + value);
        return value;
    }
}
