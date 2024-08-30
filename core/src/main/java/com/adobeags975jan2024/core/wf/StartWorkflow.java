package com.adobeags975jan2024.core.wf;/**
 * Created by Douglas Prevelige on 5/23/2024.
 * Non-production code for POC purposes only.
 */

import com.adobe.granite.workflow.exec.*;
import com.adobe.granite.workflow.*;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.adobe.granite.workflow.model.WorkflowModel;

import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.framework.Constants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import javax.jcr.Session;

@Component(service = WorkflowProcess.class, property = {"process.label=Start Workflow", "process.description=Description Value"})
public class StartWorkflow implements WorkflowProcess {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final String TYPE_JCR_PATH = "JCR_PATH";
    private static final String TYPE_URL = "URL";

    public void execute(WorkItem item, WorkflowSession wfsession, MetaDataMap args) throws WorkflowException {
        WorkflowData workflowData = item.getWorkflowData();
        String path = "";
        log.info("*****  WF Step StartWorkflow *********");
        if (workflowData.getPayloadType().equals(TYPE_JCR_PATH)) {
            path = workflowData.getPayload().toString();
            log.info("path: " + path);
        } else if (workflowData.getPayloadType().equals(TYPE_URL)) {
            String url = workflowData.getPayload().toString();
            String encPath = url.substring(url.indexOf("?itemid=") + "?itemid=".length(), url.lastIndexOf("&"));
            try {
                path = URLDecoder.decode(encPath, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                log.error("Error!", e);
            }
            log.info("url: " + url);
            log.info("path: " + path);
        }

        if (path.length() > 0) {
            Session session = wfsession.adaptTo(Session.class);
            ResourceResolver resourceResolver = wfsession.adaptTo(ResourceResolver.class);
            MetaDataMap metaDataMap = workflowData.getMetaDataMap();
            String payload = getCategorizedPath("wfpayload",path, args, metaDataMap);
            String model = getCategorizedPath("modelpath",path, args, metaDataMap);

            WorkflowData wfData = wfsession.newWorkflowData(TYPE_JCR_PATH, payload);
            WorkflowModel wfModel = wfsession.getModel(model);
            wfsession.startWorkflow(wfModel, wfData);

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
