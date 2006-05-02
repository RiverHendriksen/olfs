/////////////////////////////////////////////////////////////////////////////
// This file is part of the "Server4" project, a Java implementation of the
// OPeNDAP Data Access Protocol.
//
// Copyright (c) 2005 OPeNDAP, Inc.
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

package opendap.coreServlet;

import org.jdom.Element;
import org.jdom.Document;
import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.rmi.server.UID;
import java.io.*;
import java.util.Vector;

import opendap.soap.XMLNamespaces;

/**
 * Created by IntelliJ IDEA.
 * User: ndp
 * Date: Apr 27, 2006
 * Time: 9:19:54 PM
 * To change this template use File | Settings | File Templates.
 */
public class MultipartResponse {


    private Element soapEnvelope;
    private HttpServletResponse servResponse;
    private HttpServletRequest servRequest;
    private OpendapHttpDispatchHandler odh;
    private Vector<Attachment> attachments;
    private String mimeBoundary;
    private String startID;


    MultipartResponse(HttpServletRequest request, HttpServletResponse response, OpendapHttpDispatchHandler o){
        servResponse = response;
        servRequest = request;
        odh = o;
        attachments  = new Vector<Attachment>();
        mimeBoundary = getNewMimeBoundary();
        startID = getUidString();
        soapEnvelope = null;
    }

    public void addSoapBodyPart(Element e){
        soapEnvelope.getChild("Body",XMLNamespaces.getDefaultSoapEnvNamespace()).addContent(e);
    }

    public void setSoapEnvelope(Element se){
        soapEnvelope = (Element) se.clone();
    }


    public static String getNewMimeBoundary(){
        //Date date = new Date();
        return "----=_Part_0_"+getUidString();
    }

    public static String getUidString(){
        UID uid = new UID();

        byte[] val = uid.toString().getBytes();

        String suid  = "";
        int v;

        for (byte aVal : val) {
            v = aVal;
            suid += Integer.toHexString(v);
        }

        return suid;
    }




    public void send() throws IOException {
        System.out.println("Sending Response...");

        System.out.println("MIME Boundary: "+mimeBoundary);



        servResponse.setContentType("Multipart/related;  "+
                                "type=\"text/xml\";  "+
                                "start=\""+startID+"\";  "+
                                "boundary=\""+mimeBoundary+"\"");

        servResponse.setHeader("XDODS-Server", odh.getXDODSServerVersion());
        servResponse.setHeader("XOPeNDAP-Server", odh.getXOPeNDAPServerVersion());
        servResponse.setHeader("XDAP", odh.getXDAPVersion(servRequest));
        servResponse.setHeader("Content-Description", "OPeNDAP WebServices");

        ServletOutputStream os = servResponse.getOutputStream();

        writeSoapPart(os);

        for (Attachment a : attachments)
            a.write(mimeBoundary,os);


        closeMimeDoc(os);


    }

    private void writeSoapPart(ServletOutputStream sos) throws IOException {
        XMLOutputter xmlo = new XMLOutputter(Format.getPrettyFormat());

        sos.println("--"+mimeBoundary);
        sos.println("Content-Type: text/xml; charset=UTF-8");
        sos.println("Content-Transfer-Encoding: binary");
        sos.println("Content-Id: "+startID);
        sos.println();

        xmlo.output(new Document(soapEnvelope),sos);



    }


    public void addAttachment(String contentType, String contentId, InputStream is){

            attachments.add(new Attachment(contentType,contentId,is));
    }




    private void closeMimeDoc(ServletOutputStream sos) throws IOException {
        sos.println("--"+mimeBoundary+"--");
    }





}