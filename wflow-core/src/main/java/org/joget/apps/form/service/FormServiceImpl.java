package org.joget.apps.form.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringEscapeUtils;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.AbstractSubForm;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.model.FormStoreBinder;
import org.joget.commons.util.FileLimitException;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.FileStore;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.ResourceBundleUtil;
import org.joget.commons.util.StringUtil;
import org.joget.commons.util.UuidGenerator;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import io.proximax.connection.BlockchainNetworkConnection;
import io.proximax.connection.ConnectionConfig;
import io.proximax.connection.HttpProtocol;
import io.proximax.connection.IpfsConnection;
import io.proximax.model.BlockchainNetworkType;
import io.proximax.model.ProximaxDataModel;
import io.proximax.upload.UploadParameter;
import io.proximax.upload.UploadParameterBuilder;
import io.proximax.upload.UploadResult;
import io.proximax.upload.Uploader;


@Service("formService")
public class FormServiceImpl implements FormService {


    /**
     * Use case to generate HTML from a JSON element definition.
     * @param json
     * @return
     */
    @Override
    public String previewElement(String json) {
        return previewElement(json, true);
    }

    /**
     * Use case to generate HTML from a JSON element definition.
     * @param json
     * @param includeMetaData true to include metadata required for use in the form builder.
     * @return
     */
    @Override
    public String previewElement(String json, boolean includeMetaData) {
        Element element = createElementFromJson(StringUtil.decryptContent(json), !includeMetaData);
        FormData formData = new FormData();
        formData.addFormResult(PREVIEW_MODE, "true");
        
        formData = retrieveFormDataFromRequest(formData, WorkflowUtil.getHttpServletRequest());
        
        String html = "";
        try {
            formData = executeFormOptionsBinders(element, formData);
        } catch (Exception ex) {
            LogUtil.error(FormService.class.getName(), ex, "Error executing form option binders");
        }
        try {
            html = generateElementDesignerHtml(element, formData, includeMetaData);
        } catch (Exception ex) {
            LogUtil.error(FormService.class.getName(), ex, "Error generating element html");
        }
        return html;
    }

    /**
     * Creates an element object from a JSON definition
     * @param formJson
     * @return
     */
    @Override
    public Element createElementFromJson(String elementJson) {
        return createElementFromJson(elementJson, true);
    }

    /**
     * Creates an element object from a JSON definition
     * @param formJson
     * @param processHashVariable
     * @return
     */
    @Override
    public Element createElementFromJson(String elementJson, boolean processHashVariable) {
        try {
            String processedJson = elementJson;
            // process hash variable
            if (processHashVariable) {
                processedJson = AppUtil.processHashVariable(elementJson, null, StringUtil.TYPE_JSON, null);
            }
            
            processedJson = processedJson.replaceAll("\\\"\\{\\}\\\"", "{}");

            // instantiate element
            Element element = FormUtil.parseElementFromJson(processedJson);
            return element;
        } catch (Exception ex) {
            LogUtil.error(FormService.class.getName(), ex, null);
            throw new RuntimeException(ex);
        }
    }

    /**
     * Generates HTML for the form element
     * @param element
     * @param formData
     * @return
     */
    @Override
    public String generateElementHtml(Element element, FormData formData) {
        String html = element.render(formData, false);
        return html;
    }

    /**
     * Generates error HTML for the form element
     * @param element
     * @param formData
     * @return
     */
    @Override
    public String generateElementErrorHtml(Element element, FormData formData) {
        Map dataModel = FormUtil.generateDefaultTemplateDataModel(element, formData);
        String html = element.renderErrorTemplate(formData, dataModel);
        return html;
    }

    /**
     * Generates HTML for the form element to be used in the Form Builder
     * @param element
     * @param formData
     * @return
     */
    @Override
    public String generateElementDesignerHtml(Element element, FormData formData, boolean includeMetaData) {
        String html = element.render(formData, includeMetaData);
        return html;
    }

    /**
     * Generates the JSON definition for the specified form element
     * @param element
     * @return
     */
    @Override
    public String generateElementJson(Element element) {
        String json = null;
        try {
            json = FormUtil.generateElementJson(element);
        } catch (Exception ex) {
            LogUtil.error(FormService.class.getName(), ex, "Error generating JSON for element");
        }
        return json;
    }

    /**
     * Use case to load and view a form, with data loaded
     * @param form
     * @param primaryKeyValue
     * @return
     */
    @Override
    public String viewForm(Form form, String primaryKeyValue) {
        FormData formData = new FormData();
        formData.setPrimaryKeyValue(primaryKeyValue);
        String html = generateElementHtml(form, formData);
        return html;
    }

    /**
     * Use case to view a form from its JSON definition, with data loaded
     * @param formJson
     * @param primaryKeyValue
     * @return
     */
    @Override
    public String viewFormFromJson(String formJson, String primaryKeyValue) {
        FormData formData = new FormData();
        formData.setPrimaryKeyValue(primaryKeyValue);
        Form form = loadFormFromJson(formJson, formData);
        String html = generateElementHtml(form, formData);
        return html;
    }

    /**
     * Load a form from its JSON definition, with data loaded.
     * @param formJson
     * @param formData
     * @return
     */
    @Override
    public Form loadFormFromJson(String formJson, FormData formData) {
        Form form = (Form) createElementFromJson(formJson);
        form = loadFormData(form, formData);
        return form;
    }

    /**
     * Main method to load a form with data loaded.
     * @param form
     * @param formData
     * @return
     */
    @Override
    public Form loadFormData(Form form, FormData formData) {
        // set foreign key values
        Set<String> readOnlyForeignKeySet = new HashSet<String>();
        if (formData != null) {
            Map requestParams = new HashMap(formData.getRequestParams());
            for (Iterator i = requestParams.keySet().iterator(); i.hasNext();) {
                String paramName = (String) i.next();
                if (paramName.startsWith(PREFIX_FOREIGN_KEY) || paramName.startsWith(PREFIX_FOREIGN_KEY_EDITABLE)) {
                    String foreignKey = paramName.startsWith(PREFIX_FOREIGN_KEY) ? paramName.substring(PREFIX_FOREIGN_KEY.length()) : paramName.substring(PREFIX_FOREIGN_KEY_EDITABLE.length());
                    boolean editable = paramName.startsWith(PREFIX_FOREIGN_KEY_EDITABLE);
                    String[] values = (String[]) requestParams.get(paramName);

                    if (values != null) {
                        formData.addRequestParameterValues(foreignKey, values);
                        if (!editable) {
                            readOnlyForeignKeySet.add(foreignKey);
                            form.setFormMeta(paramName, values);
                        }
                    }
                }
            }
        }

        formData = executeFormOptionsBinders(form, formData);
        formData = executeFormLoadBinders(form, formData);

        // make foreign keys read-only
        for (String foreignKey : readOnlyForeignKeySet) {
            Element el = FormUtil.findElement(foreignKey, form, formData);
            if (el != null) {
                FormUtil.setReadOnlyProperty(el);
            }
        }
        return form;
    }

    /**
     * Process form submission
     * @param form
     * @param formData
     * @param ignoreValidation
     * @return
     */
    @Override
    @Transactional
    public FormData submitForm(Form form, FormData formData, boolean ignoreValidation) {
        FormData updatedFormData = formData;
        LogUtil.info("SYDNEY SYDNEY","SYDNEY SYDNEY");
        

		// TODO Auto-generated method stub
		try {

    		System.out.print("\nConnecting to Blockchain Network bctestnet2.xpxsirius.io:3000...");
            BlockchainNetworkConnection blockChain = new BlockchainNetworkConnection(
                    BlockchainNetworkType.TEST_NET, "bctestnet2.xpxsirius.io", 3000, HttpProtocol.HTTP);
            System.out.println("done!");
            System.out.print("\nConnecting to IPFS 127.0.0.1:5001...");
            IpfsConnection connection = new IpfsConnection("127.0.0.1", 5001);
            System.out.println("done!");
            System.out.print("\nExecuting \"ConnectionConfig.createWithLocalIpfsConnection(blockChain, connection);\"...");
            ConnectionConfig config = ConnectionConfig.createWithLocalIpfsConnection(blockChain, connection);
            System.out.println("done!");
            System.out.print("\nExecuting \"UploadParameter.createForFileUpload(file, privatekey)\"");
//            String req =  updatedFormData.getRequestParameter("sydneytextfield");
//            Files.write(
//            	      Paths.get("C:\\Users\\sydney\\Desktop\\freelance\\proximax\\build2.xml"), 
//            	      test.getBytes(), 
//            	      StandardOpenOption.APPEND);
//            
            UploadParameter up =  UploadParameter.createForStringUpload("approve", "74707FB82A47362461EE7B5689BBD0228F4E43349D1514F794CB925E0765FEC4")
            		.withNemKeysPrivacy("74707FB82A47362461EE7B5689BBD0228F4E43349D1514F794CB925E0765FEC4","9699E0E847DC021EED224CE38E66B154E924B11527634BB66007BB2EA7106560")
            		.build();
//            UploadParameter up = UploadParameter.createForFileUpload(new File("C:\\Users\\sydney\\Desktop\\freelance\\proximax\\build2.xml"),
//                    "74707FB82A47362461EE7B5689BBD0228F4E43349D1514F794CB925E0765FEC4")
//                    .withNemKeysPrivacy(
//                            "74707FB82A47362461EE7B5689BBD0228F4E43349D1514F794CB925E0765FEC4",
//                            "9699E0E847DC021EED224CE38E66B154E924B11527634BB66007BB2EA7106560")
//                    .build();
            System.out.println("done!");
            System.out.print("\nPerforming uploads....");
            UploadParameterBuilder builder = new UploadParameterBuilder(up.getData(),
                    "74707FB82A47362461EE7B5689BBD0228F4E43349D1514F794CB925E0765FEC4");
            UploadParameter uploadParameter = builder.build();
            Uploader upload = new Uploader(config);
            UploadResult result = upload.upload(uploadParameter);
            String ipfsHash = result.getTransactionHash();
            System.out.println("done!");
            LogUtil.info("SYDNEY","TRANSACTION HASH: " + ipfsHash);
            ProximaxDataModel pdModel = result.getData();
            System.out.println("Data Hash: " + pdModel.getDataHash());
            System.out.println("Digest: " + pdModel.getDigest());
        	
            String[] temp  = {ipfsHash};
            formData.addRequestParameterValues("resultValue",temp);
            
            String[] temp2  = {pdModel.getDataHash()};
            formData.addRequestParameterValues("resultValue2",temp2);
        	
    	}catch(Exception e) {
    		   LogUtil.error("SYDNEY ERROR",e,e.getMessage());
    		   formData.addFormError("resultValue", e.getMessage());
    	}
        
		
	  
        
        
        updatedFormData = FormUtil.executeElementFormatDataForValidation(form, formData);
        if (!ignoreValidation) {
            updatedFormData = validateFormData(form, formData);
        } else {
            updatedFormData.clearFormErrors();
        }
        Map<String, String> errors = updatedFormData.getFormErrors();
        if (!updatedFormData.getStay() && (errors == null || errors.isEmpty())) {
            // generate primary key if necessary
            Element primaryElement = FormUtil.findElement(FormUtil.PROPERTY_ID, form, formData);
            if (primaryElement != null) {
                //format data to generate id
                FormUtil.executeElementFormatData(primaryElement, formData);
            }
            
            String primaryKeyValue = form.getPrimaryKeyValue(updatedFormData);
            if (primaryKeyValue == null || primaryKeyValue.trim().length() == 0) {
                // no primary key value specified, generate new primary key value
                primaryKeyValue = UuidGenerator.getInstance().getUuid();
                updatedFormData.setPrimaryKeyValue(primaryKeyValue);
                
                //set to request param
                formData.addRequestParameterValues(FormUtil.PROPERTY_ID, new String[]{primaryKeyValue});
            }
            // no errors, save form data
            updatedFormData = executeFormStoreBinders(form, updatedFormData);
        }
        
   
        return updatedFormData;
    }
    
    /**
     * Store the data of a form field element
     * @param form
     * @param element
     * @param formData
     * @return 
     */
    @Override
    public FormData storeElementData(Form form, Element element, FormData formData) {
        return recursiveExecuteFormStoreBinders(form, element, formData);
    }

    /**
     * Validates form data submitted for a specific form
     * @param form
     * @param formData
     * @return
     */
    @Override
    public FormData validateFormData(Form form, FormData formData) {
        FormUtil.executeValidators(form, formData);
        
        //set all remaining privious submission error to error map
        for (String id : formData.getPreviousFormErrors().keySet()) {
            formData.addFormError(id, formData.getPreviousFormError(id));
        }
        
        //set all file error to error map
        for (String id : formData.getFileErrors().keySet()) {
            formData.addFormError(id, formData.getFileError(id));
        }
        
        return formData;
    }

    /**
     * Retrieves form data submitted via a HTTP servlet request
     * @param request
     * @return
     */
    @Override
    public FormData retrieveFormDataFromRequest(FormData formData, HttpServletRequest request) {
        if (formData == null) {
            formData = new FormData();
        }
        // handle standard parameters
        Enumeration<String> e = request.getParameterNames();
        while (e.hasMoreElements()) {
            String paramName = e.nextElement();
            paramName = StringEscapeUtils.escapeHtml(paramName);
            String[] values = request.getParameterValues(paramName);
            formData.addRequestParameterValues(paramName, values);
        }
        
        handleFiles(formData);
        handleErrors(formData);
        
        return formData;
    }

    /**
     * Retrieves form data submitted via a HTTP servlet request parameters map
     * @param formData
     * @param requestMap
     * @return 
     */
    @Override
    public FormData retrieveFormDataFromRequestMap(FormData formData, Map requestMap) {
        if (formData == null) {
            formData = new FormData();
        }
        // handle standard parameters
        for (String key : (Set<String>) requestMap.keySet()) {
            Object paramValue = requestMap.get(key);
            if (paramValue != null) {
                Class type = paramValue.getClass();
                if (type.isArray()) {
                    formData.addRequestParameterValues(key, (String[]) paramValue);
                } else {
                    formData.addRequestParameterValues(key, new String[]{paramValue.toString()});
                }
            } else {
                formData.addRequestParameterValues(key, new String[]{""});
            }
        }
        
        handleFiles(formData);
        handleErrors(formData);
        
        return formData;
    }
    
    private void handleErrors (FormData formData) {
        if (formData.getPreviousFormErrors().isEmpty() && formData.getRequestParameter(FormUtil.FORM_ERRORS_PARAM) != null) {
            try {
                String json = formData.getRequestParameter(FormUtil.FORM_ERRORS_PARAM);
                JSONObject obj = new JSONObject(json);
                JSONObject errors = obj.getJSONObject("errors");
                if (errors != null) {
                    Iterator keys = errors.keys();
                    while (keys.hasNext()) {
                        String key = (String) keys.next();
                        formData.addPreviousFormError(key, (String) errors.getString(key));
                    }
                }
            } catch (Exception e) {}
        }
    }
    
    private void handleFiles (FormData formData) {
        try {
            // handle multipart files
            Map<String, MultipartFile[]> fileMap = FileStore.getFileMap();
            if (fileMap != null) {
                for (String paramName : fileMap.keySet()) {
                    try {
                        MultipartFile[] files = FileStore.getFiles(paramName);
                        List<String> paths = new ArrayList<String>();
                        for (MultipartFile file : files) {
                            if (file != null && file.getOriginalFilename() != null && !file.getOriginalFilename().isEmpty()) {
                                String path = FileManager.storeFile(file);
                                paths.add(path);
                            }
                        }
                        if (!paths.isEmpty()) {
                            formData.addRequestParameterValues(paramName, paths.toArray(new String[]{}));
                        }
                    } catch (FileLimitException ex) {
                        formData.addFileError(paramName, ResourceBundleUtil.getMessage("general.error.fileSizeTooLarge", new Object[]{FileStore.getFileSizeLimit()}));
                    }
                }
            }
            
            Collection<String> errorList = FileStore.getFileErrorList();
            if (errorList != null && !errorList.isEmpty()) {
                for (String paramName : errorList) {
                    formData.addFileError(paramName, ResourceBundleUtil.getMessage("general.error.fileSizeTooLarge", new Object[]{FileStore.getFileSizeLimit()}));
                }
            }
        } finally {
            FileStore.clear();
        }
    }

    /**
     * Invokes actions (e.g. buttons) in the form
     * @param form
     * @param formData
     * @return
     */
    @Override
    public FormData executeFormActions(Form form, FormData formData) {
        FormData updatedFormData = formData;
        updatedFormData = FormUtil.executeActions(form, form, formData);
        return updatedFormData;
    }

    /**
     * Preloads data for an element, e.g. field options, etc. by calling all option binders in the element.
     * @param element
     * @param formData
     * @return
     */
    @Override
    public FormData executeFormOptionsBinders(Element element, FormData formData) {
        // create new form data if necessary
        if (formData == null) {
            formData = new FormData();
        }

        // find and call all option binders in the form
        formData = FormUtil.executeOptionBinders(element, formData);
        return formData;
    }

    /**
     * Loads data for a specific row into an element by calling all load binders in the element.
     * @param element
     * @param formData
     * @return
     */
    @Override
    public FormData executeFormLoadBinders(Element element, FormData formData) {
        // create new form data if necessary
        if (formData == null) {
            formData = new FormData();
        }

        // find and call all option binders in the form
        formData = FormUtil.executeLoadBinders(element, formData);
        return formData;
    }

    /**
     * Executes store binders for a form
     * @param form
     * @param formData
     * @return
     */
    @Override
    public FormData executeFormStoreBinders(Form form, FormData formData) {

        // get formatted data from all elements
        formData = FormUtil.executeElementFormatData(form, formData);

        //recursively execute FormStoreBinders
        formData = recursiveExecuteFormStoreBinders(form, form, formData);

        return formData;
    }
    
    /**
     * Recursively executes all the store binders in a form
     * @param form
     * @param element
     * @param formData
     * @return 
     */
    @Override
    public FormData recursiveExecuteFormStoreBinders(Form form, Element element, FormData formData) {
        if (!Boolean.parseBoolean(element.getPropertyString(FormUtil.PROPERTY_READONLY)) && element.isAuthorize(formData)) {

            //load child element store binder to store before the main form
            Collection<Element> children = element.getChildren(formData);
            if (children != null) {
                for (Element child : children) {
                    formData = recursiveExecuteFormStoreBinders(form, child, formData);
                }
            }

            //if store binder exist && element is not readonly, run it
            FormStoreBinder binder = element.getStoreBinder();
            if (!(element instanceof AbstractSubForm) && binder != null) {
                FormRowSet rowSet = formData.getStoreBinderData(element.getStoreBinder());

                // execute binder
                FormRowSet binderResult = binder.store(element, rowSet, formData);
                formData.setStoreBinderData(binder, binderResult);
            }
        }

        return formData;
    }

    /**
     * Used to retrieves the Form HTML 
     * @param form
     * @param formData
     * @return 
     */
    @Override
    public String retrieveFormHtml(Form form, FormData formData) {
        String formHtml = null;
        if (form != null) {
            if (formData == null) {
                formData = new FormData();
            }
            formHtml = generateElementHtml(form, formData);
        }
        return formHtml;
    }

    /**
     * Used to retrieves the Form HTML when there is errors in form
     * @param form
     * @param formData
     * @return 
     */
    @Override
    public String retrieveFormErrorHtml(Form form, FormData formData) {
        String formHtml = null;
        if (form != null) {
            if (formData == null) {
                formData = new FormData();
            }
            formHtml = generateElementErrorHtml(form, formData);
        }
        return formHtml;
    }
}