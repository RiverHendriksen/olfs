/////////////////////////////////////////////////////////////////////////////
// This file is part of the "OPeNDAP 4 Data Server (aka Hyrax)" project.
//
//
// Copyright (c) 2010 OPeNDAP, Inc.
// Author: Nathan David Potter  <ndp@opendap.org>
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// You can contact OPeNDAP, Inc. at PO Box 112, Saunderstown, RI. 02874-0112.
/////////////////////////////////////////////////////////////////////////////
package opendap.wcs.v1_1_2;

import opendap.coreServlet.*;
import org.jdom.Element;
import org.slf4j.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;



/**
 * Stand alone servlet for WCS Services
 * User: ndp
 */
public class WcsServlet extends HttpServlet {


    private Logger log;
    private DispatchHandler wcsService = null;

    private FormHandler form = null;
    private PostHandler post = null;
    private SoapHandler soap = null;

    //private Document configDoc;


    public void init() throws ServletException {
        super.init();
        PerfLog.initLogging(this);
        log = org.slf4j.LoggerFactory.getLogger(getClass());


        
        wcsService = new DispatchHandler();
        form = new FormHandler();
        post = new PostHandler();
        soap = new SoapHandler();


        Element config  = new Element("config");
        Element prefix = new Element("prefix");

//        System.out.println(ServletUtil.probeServlet(this));

        ServletContext sc = this.getServletContext();

        
        prefix.setText(sc.getContextPath());
        config.addContent(prefix);

        try {
            prefix.setText("/");
            wcsService.init(this,config);
            prefix.setText("/form");
            form.init(this,config);
            prefix.setText("/post");
            post.init(this,config);
            prefix.setText("/soap");
            soap.init(this,config);
           
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }



    public void doGet(HttpServletRequest req, HttpServletResponse resp){
        try {
            wcsService.handleRequest(req, resp);
        }
        catch (Throwable t) {
            try {
                OPeNDAPException.anyExceptionHandler(t, resp);
            }
            catch(Throwable t2) {
            	try {
            		log.error("\n########################################################\n" +
                                "Request proccessing failed.\n" +
                                "Normal Exception handling failed.\n" +
                                "This is the last error log attempt for this request.\n" +
                                "########################################################\n", t2);
            	}
            	catch(Throwable t3){
                    // It's boned now.. Leave it be.
            	}
            }
        }
        finally{
            this.destroy();
        }
    }


    public void doPost(HttpServletRequest req, HttpServletResponse resp){
        try {

            if(post.requestCanBeHandled(req)){
                post.handleRequest(req,resp);
            }
            else if(soap.requestCanBeHandled(req)){
                soap.handleRequest(req,resp);
            }
            else if(form.requestCanBeHandled(req)){
                form.handleRequest(req,resp);
            }
            else {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            }

        }
        catch (Throwable t) {
            try {
                OPeNDAPException.anyExceptionHandler(t, resp);
            }
            catch(Throwable t2) {
            	try {
            		log.error("\n########################################################\n" +
                                "Request proccessing failed.\n" +
                                "Normal Exception handling failed.\n" +
                                "This is the last error log attempt for this request.\n" +
                                "########################################################\n", t2);
            	}
            	catch(Throwable t3){
                    // It's boned now.. Leave it be.
            	}
            }
        }
        finally{
            this.destroy();
        }
    }



}
