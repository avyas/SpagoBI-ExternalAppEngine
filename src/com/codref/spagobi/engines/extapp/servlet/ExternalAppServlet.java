
/**

External Application Engine for SpagoBI

Copyright (C) 2011 Davide Dal Farra
Copyright (C) 2005-2008 Engineering Ingegneria Informatica S.p.A.

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

 **/

package com.codref.spagobi.engines.extapp.servlet; 

import it.eng.spago.security.IEngUserProfile;
import it.eng.spagobi.services.content.bo.Content;
import it.eng.spagobi.services.proxy.ContentServiceProxy;
import it.eng.spagobi.utilities.callbacks.audit.AuditAccessUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;

import java.net.URI;
import java.net.URISyntaxException;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.List;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import sun.misc.BASE64Decoder;


public class ExternalAppServlet extends HttpServlet {
  // useful constants
  private static final String ATTR_BASE = "base";
  private static final String ATTR_PATH = "path";
  private static final String ATTR_METHOD = "method";
  private static final String POST = "POST";
  private static final String GET = "GET";
  private static final String ATTR_NAME = "name";
  private static final String ATTR_MAPTO = "mapTo";
	
	private static transient Logger logger = Logger.getLogger(ExternalAppServlet.class);
	private static String CONNECTION_NAME="connectionName";
	private static String QUERY="query";
	private static String DOCUMENT_ID="document";
	private static String USER_ID="user_id";
	  
	
    public void init(ServletConfig config) throws ServletException {
    	super.init(config);
    	logger.debug("Initializing SpagoBI ExternalApp Engine...");
    }
    
    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    	logger.debug("IN service");
    	//get the document
    	HttpSession session = request.getSession();
    	logger.debug("documentId IN Session:"+(String)session.getAttribute(DOCUMENT_ID));
    	// USER PROFILE
    	String documentId = (String) request.getParameter(DOCUMENT_ID);
    	if (documentId==null){
    	    documentId=(String)session.getAttribute(DOCUMENT_ID);
    	    logger.debug("documentId From Session:"+documentId);
    	}
    	logger.debug("documentId:"+documentId);
    	
    	//get userprofile
    	IEngUserProfile profile = (IEngUserProfile) session.getAttribute(IEngUserProfile.ENG_USER_PROFILE);
    	logger.debug("profile from session: " + profile);
    	
    	// AUDIT UPDATE
    	String auditId = request.getParameter("SPAGOBI_AUDIT_ID");
    	AuditAccessUtils auditAccessUtils = (AuditAccessUtils) request.getSession().getAttribute("SPAGOBI_AUDIT_UTILS");
    	if (auditAccessUtils != null)
    	    auditAccessUtils.updateAudit(session,(String) profile.getUserUniqueIdentifier(), auditId, new Long(System
    		    .currentTimeMillis()), null, "EXECUTION_STARTED", null, null);


      
    	try {

        byte[] xml = getDocument(session, profile, documentId);
        final Document document = loadXMLFrom(new ByteArrayInputStream(xml));

        List <String> definedApplications = new ArrayList<String>();
        String htmlOutput = "";
        
        NodeList applications = document.getElementsByTagName("Application");
        for (int i = 0; i < applications.getLength(); i++) {
          String getParams = "";        
          List <NameValuePair> postParams = new ArrayList <NameValuePair>();
          List <String> removedParams = new ArrayList<String>();
          HashMap <String, String> swapParameters = new HashMap<String, String>();
          HashMap <String, String> swappedParameters = new HashMap<String, String>();
          
          Node application = applications.item(i);
          if (application.getNodeType() == Node.ELEMENT_NODE) {
            Element aElement = (Element) application;
            String applicationName = aElement.getAttribute(ATTR_NAME);
            
            // verify once execution
            if (aElement.getAttribute("execOnce").equals("yes")) {
              ArrayList<String> extAppEngineSession = (ArrayList<String>) session.getAttribute("CODREF_EXTAPPENGINE");
              if (extAppEngineSession != null && extAppEngineSession.contains(documentId + "_" + applicationName)) {
                logger.debug("once only!");
                continue;
              } else {
                logger.debug("once only, but first time.");
                if (extAppEngineSession == null)
                  extAppEngineSession = new ArrayList<String>();
                extAppEngineSession.add(documentId + "_" + applicationName);
                session.setAttribute("CODREF_EXTAPPENGINE", extAppEngineSession);
              }
            }
            
            // parameters coming from user profile
            NodeList nList = aElement.getElementsByTagName("ProfileParameter");
            for (int temp = 0; temp < nList.getLength(); temp++) {
               Node nNode = nList.item(temp);
               if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                  Element eElement = (Element) nNode;
                  if (eElement.getAttribute(ATTR_METHOD).equals(GET))
                    try {
                      getParams += eElement.getAttribute(ATTR_NAME) + "=" + profile.getUserAttribute(eElement.getAttribute(ATTR_MAPTO)).toString() + "&";                    
                    } catch (Exception e1) {
                      getParams += eElement.getAttribute(ATTR_NAME) + "=" + "&";
                      logger.warn("Parameter " + profile.getUserAttribute(eElement.getAttribute(ATTR_MAPTO)).toString() + " not found on profile");
                    }
                  else if (eElement.getAttribute(ATTR_METHOD).equals(POST))
                    try {
                      postParams.add(new NameValuePair(eElement.getAttribute(ATTR_NAME), profile.getUserAttribute(eElement.getAttribute(ATTR_MAPTO)).toString()));
                    } catch (Exception e1) {
                      postParams.add(new NameValuePair(eElement.getAttribute(ATTR_NAME), ""));
                      logger.warn("Parameter " + profile.getUserAttribute(eElement.getAttribute(ATTR_MAPTO)).toString() + " not found on profile");
                    }
               }
            } 

            // parameters specified inside XML
            nList = aElement.getElementsByTagName("StaticParameter");
            for (int temp = 0; temp < nList.getLength(); temp++) {
               Node nNode = nList.item(temp);
               if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                  Element eElement = (Element) nNode;
                  if (eElement.getAttribute(ATTR_METHOD).equals(GET))
                    getParams += eElement.getAttribute(ATTR_NAME) + "=" + eElement.getChildNodes().item(0).getNodeValue() + "&";
                  else if (eElement.getAttribute(ATTR_METHOD).equals(POST))
                    postParams.add(new NameValuePair(eElement.getAttribute(ATTR_NAME), eElement.getChildNodes().item(0).getNodeValue()));
               }
            }
            
            // paramaters to be removed
            nList = aElement.getElementsByTagName("RemoveParameter");
            for (int temp = 0; temp < nList.getLength(); temp++) {
               Node nNode = nList.item(temp);
               if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                  Element eElement = (Element) nNode;
                  removedParams.add(eElement.getAttribute(ATTR_NAME));
               }
            }
            
            
            // paramaters to be swapped
            nList = aElement.getElementsByTagName("SwapParameter");
            for (int temp = 0; temp < nList.getLength(); temp++) {
               Node nNode = nList.item(temp);
               if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                  Element eElement = (Element) nNode;
                  swapParameters.put(eElement.getAttribute(ATTR_NAME), eElement.getAttribute("swapWith"));
               }
            }             

            /* 
             * parameters configured inside SpagoBI document 
             * AND parameters used by SpagoBI for internal purposes 
             * (can be removed one by one acting on method cleanParamameters())
             */
            HashMap<String, String> urlParams = cleanParameters(request);        
            if(!urlParams.isEmpty()){
              for (Iterator it = urlParams.keySet().iterator(); it.hasNext(); ) {
                String key = (String)it.next();
                if (!removedParams.contains(key))
                  if (swapParameters.containsKey(key)) {
                    String swapWithParam = swapParameters.get(key);
                    getParams += key + "=" + request.getParameter(swapWithParam) + "&";
                    swappedParameters.put(swapWithParam, key);
                  } else if (swappedParameters.containsKey(key)) {
                    String swapWithParam = swappedParameters.get(key);
                    getParams += key + "=" + request.getParameter(swapWithParam) + "&";                    
                  } else
                    getParams += key + "=" + request.getParameter(key) + "&";
              }	        	
            }        
           

            String baseUrl = aElement.getAttribute(ATTR_BASE);
            String path = aElement.getAttribute(ATTR_PATH);
            String method = aElement.getAttribute(ATTR_METHOD);

            if (getParams.length() > 0) {
              getParams = "?" + getParams;
            }
            
            logger.debug("Application URL: " + baseUrl + path + getParams);
            
            if (method.equals(GET)) {
              logger.debug("Doing a GET request");
              if (i == applications.getLength() -1) {
                // do a normal browser redirect, last action
                htmlOutput += getRedirectScript(baseUrl + path + getParams);
              } else {
                definedApplications.add(applicationName);
                htmlOutput += getGetScript(applicationName, baseUrl + path + getParams);
              }
            } else if (method.equals(POST)) {
              logger.debug("Doing a POST request");
              definedApplications.add(applicationName);
              htmlOutput += getPostScript(applicationName, baseUrl + path + getParams, postParams);
            } else {
              logger.error("Unable to output result", new Exception("Method not implemented"));
            }            
          }
        }
        
        response.setContentType("text/html");
        htmlOutput = getHeader() + htmlOutput + getFooter(definedApplications);
        response.getOutputStream().write(htmlOutput.getBytes());
        response.getOutputStream().flush();
       
		} catch (Exception e1) {
      logger.error("Unable to output result", e1);
		    if (auditAccessUtils != null)
				auditAccessUtils.updateAudit(session,(String) profile.getUserUniqueIdentifier(), auditId, null, new Long(System
					.currentTimeMillis()), "EXECUTION_FAILED", e1.getMessage(), null);
		    return;
		}
    logger.debug("OUT");
  }
    
  private String getHeader() {
    return new StringBuilder()
            .append("<html><head>\n")
            .append("<script src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.4/jquery.min.js\" type=\"text/javascript\"></script>\n")
            .append("<script type=\"text/javascript\">\n")
            .append("$(document).ready(function() {\n")
            .toString();
  }
  
  private String getFooter(List <String> definedApplications) {
    StringBuilder footer =  new StringBuilder();
    StringBuilder iframes =  new StringBuilder();
    Iterator<String> iterator = definedApplications.iterator();
    while (iterator.hasNext()) {
      String applicationName = iterator.next();
      footer.append("});");
      iframes.append(String.format("<iframe style=\"display: none;\" width=\"0\" height=\"0\" name=\"%s\" id=\"%s\"></iframe>\n", applicationName, applicationName));
    }
            
    return footer.append("});")
            .append("</script></head><body>Please wait...\n")
            .append(iframes.toString())
            .append("</body></html>\n")
            .toString();
  }

  private String getPostScript(String appName, String actionUrl, List <NameValuePair> postParams) {
    StringBuilder html = new StringBuilder()
            .append(String.format("$('iframe#%s').contents().find('body').html('<form name=\"clientPostDataForm\" id=\"clientPostDataForm\" action=\"%s\" method=\"POST\">' + \n", appName, actionUrl));
    Iterator<NameValuePair> iterator = postParams.iterator();
    while (iterator.hasNext()) {
      NameValuePair tPair = iterator.next();
      html.append(String.format("'<input type=\"hidden\" name=\"%s\" value=\"%s\" />' + \n",tPair.getName(),tPair.getValue()));
    }
    html.append("'</form>'); \n")
            .append(String.format("$('#%s').contents().find('#clientPostDataForm').submit(); \n", appName))
            .append(String.format("$('iframe#%s').load(function() \n", appName))
            .append("{ \n");
            
    return html.toString();
  } 
  
  private String getGetScript(String appName, String actionUrl) {
    StringBuilder html = new StringBuilder()
            .append(String.format("$('iframe#%s').attr('src', \"%s\");\n", appName, actionUrl))
            .append(String.format("$('iframe#$s').load(function() \n", appName))
            .append("{\n");
            
    return html.toString();
  }    
  
    private String getRedirectScript(String actionUrl) {
    StringBuilder html = new StringBuilder()
            .append(String.format("document.location.href=\"%s\";\n", actionUrl));
            
    return html.toString();
  } 
    
  private String replaceLinks(String address, String content) throws URISyntaxException{
      //absolute URI used for change all relative links
      URI addressUri = new URI(address);
      //finds all link atributes (href, src, etc.)
      Pattern pattern = Pattern.compile("(href|src|action|background)=\"[^\"]*\"", Pattern.CASE_INSENSITIVE);
      Matcher m = pattern.matcher(content);
      //determines if the link is allready absolute
      Pattern absoluteLinkPattern = Pattern.compile("[a-z]+://.+");
      //buffer for result saving
      StringBuffer buffer = new StringBuffer();
      //position from where should next interation take content to append to buffer
      int lastEnd = 0;
      while(m.find()){
          //position of link in quotes
          int startPos = content.indexOf('"',m.start())+1;
          int endPos = m.end()-1;
          String link = content.substring(startPos,endPos);
          Matcher absoluteMatcher = absoluteLinkPattern.matcher(link);
          //is the link relative?
          if(!absoluteMatcher.find())
          {
              //create relative URL
              URI tmpUri = addressUri.resolve(link);
              //append the string between links
              buffer.append(content.substring(lastEnd,startPos-1));
              //append new link
              buffer.append(tmpUri.toString());
              lastEnd =endPos+1;
          }
      }
      //append the end of file
      buffer.append(content.substring(lastEnd));
      return buffer.toString();
  }
    
  private HashMap<String, String> flatten(Map<String, String[]> arrayMap){
    HashMap<String, String> r = new HashMap<String, String>();
    for (Map.Entry<String, String[]> entry: arrayMap.entrySet()){
      String[] value = entry.getValue();
      if (value !=null && value .length>0) r.put(entry.getKey(), value[0]);
    }
    return r;
  } 

    
    private HashMap<String, String> cleanParameters(HttpServletRequest request){
		//gets request parameters to execute query
		HashMap<String, String> parameters = flatten(request.getParameterMap());
		HashMap<String, String> parametersCleaned = new HashMap<String, String>();
		if(parameters.containsKey(QUERY)){    			
			parameters.remove(QUERY);
		}
		if(parameters.containsKey(DOCUMENT_ID)){    			
			parameters.remove(DOCUMENT_ID);
		}
		if(parameters.containsKey(CONNECTION_NAME)){    			
			parameters.remove(CONNECTION_NAME);
		}
		if(parameters.containsKey(USER_ID)){    			
			parameters.remove(USER_ID);
		}
        if(!parameters.isEmpty()){
        	for (Iterator it = parameters.keySet().iterator(); it.hasNext(); ) {
        		String key= (String)it.next();
        		parametersCleaned.put(key, request.getParameter(key));
        	}	        	
        }
    	return parametersCleaned;
    }
    
  public static Document loadXMLFrom(final InputStream is) throws SAXException, IOException {
    logger.debug("IN");
    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    DocumentBuilder builder = null;
    try {
      builder = factory.newDocumentBuilder();
    } catch (ParserConfigurationException t) {
      logger.error("Unable to get Document Builder", t);
    }
    assert builder != null;
    final Document doc = builder.parse(is);
    is.close();
    logger.debug("OUT");
    return doc;
  }    
    
  private byte[] getDocument(HttpSession session, IEngUserProfile profile, String documentId) {
    logger.debug("IN getDocument");
    ContentServiceProxy contentProxy = new ContentServiceProxy((String)profile.getUserUniqueIdentifier(),session);
    Content templateContent = contentProxy.readTemplate(documentId,new HashMap());
    InputStream is = null;
    byte[] byteContent = null;
    try {
      BASE64Decoder bASE64Decoder = new BASE64Decoder();
      byteContent = bASE64Decoder.decodeBuffer(templateContent.getContent());
      is = new java.io.ByteArrayInputStream(byteContent);
    }catch (Throwable t){
      logger.warn("Error on decompile",t); 
    }finally{
      try {
        is.close();
      } catch (IOException e) {
        logger.warn("Error on closing inputstream",e); 
      }
    }
    logger.debug("OUT getDocument");
    return byteContent;
  }
  
  private class NameValuePair {
    private String name, value;
    
    public NameValuePair(String name, String value) {
      this.name = name;
      this.value = value;
    }

    public String getName() {
      return name;
    };


    public String getValue() {
      return value;
    };
  }
  
}
