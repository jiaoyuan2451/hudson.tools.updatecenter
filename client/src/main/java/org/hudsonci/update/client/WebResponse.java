/*******************************************************************************
 *
 * Copyright (c) 2011 Oracle Corporation.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: 
 *
 *    Winston Prakash
 *     
 *******************************************************************************/

package org.hudsonci.update.client;

/**
 * Response from server after the WebRequest is executed
 * @author Winston Prakash
 */
public class WebResponse {

    private final int responseCode;
    private String responseMessage; 
    private String response;

    public WebResponse(int code, String message) {
        this.responseCode = code;
        this.responseMessage = message.trim();
    }
    public int getResponseCode() {
        return responseCode;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage.trim();
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response.trim();
    }

    public String getErrorMessage(){
        // Check if any error message is send via HTTP reponse body
        if ((response != null) || "".equals(response)){
            return response;
        }
        // Ok got none, check if any response message obtianed from header
        if ((responseMessage != null) || "".equals(responseMessage)){
            return responseMessage;
        }
        // Return generic error message
        return "Could not connect to the server.";
    }
}
