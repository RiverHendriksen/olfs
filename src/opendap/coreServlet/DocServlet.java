/*
 * /////////////////////////////////////////////////////////////////////////////
 * // This file is part of the "Hyrax Data Server" project.
 * //
 * //
 * // Copyright (c) 2013 OPeNDAP, Inc.
 * // Author: Nathan David Potter  <ndp@opendap.org>
 * //
 * // This library is free software; you can redistribute it and/or
 * // modify it under the terms of the GNU Lesser General Public
 * // License as published by the Free Software Foundation; either
 * // version 2.1 of the License, or (at your option) any later version.
 * //
 * // This library is distributed in the hope that it will be useful,
 * // but WITHOUT ANY WARRANTY; without even the implied warranty of
 * // MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * // Lesser General Public License for more details.
 * //
 * // You should have received a copy of the GNU Lesser General Public
 * // License along with this library; if not, write to the Free Software
 * // Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
 * //
 * // You can contact OPeNDAP, Inc. at PO Box 112, Saunderstown, RI. 02874-0112.
 * /////////////////////////////////////////////////////////////////////////////
 */

package opendap.coreServlet;


import opendap.http.error.NotFound;
import opendap.io.HyraxStringEncoding;
import opendap.logging.LogUtil;
import org.slf4j.Logger;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This mini servlet provides access to distributed or, if it exists, persistent documentation in the
 * content directory.
 *
 */
public class DocServlet extends HttpServlet {


    private String documentsDirectory;


    private Logger log;


    private AtomicInteger reqNumber;


    public void init() {

        reqNumber = new AtomicInteger(0);

        String dir = ServletUtil.getConfigPath(this) + "docs";

        File f = new File(dir);

        if (f.exists() && f.isDirectory())
            documentsDirectory = dir;
        else {

            documentsDirectory = this.getServletContext().getRealPath("docs");

        }


        log = org.slf4j.LoggerFactory.getLogger(getClass());

        log.info("documentsDirectory: " + documentsDirectory);


    }


    public long getLastModified(HttpServletRequest req) {

        long lmt = -1;


        String name = Scrub.fileName(getName(req));


        if(name!=null) {
            File f = new File(name);
            if (f.exists())
                lmt = f.lastModified();

        }
        //log.debug("getLastModified() - Tomcat requested lastModified for: " + name + " Returning: " + new Date(lmt));

        return lmt;


    }


    private boolean redirect(HttpServletRequest req, HttpServletResponse res) throws IOException {

        if (req.getPathInfo() == null) {
            res.sendRedirect(Scrub.urlContent(req.getRequestURI() + "/index.html"));
            log.debug("Sent redirect to make the web page work!");
            return true;
        }
        return false;
    }


    private String getName(HttpServletRequest req) {

        String name = req.getPathInfo();

        if (name == null)
            name = "/";

        if (name.endsWith("/"))
            name += "index.html";

        name = documentsDirectory + name;
        return name;
    }


    public void doGet(HttpServletRequest request, HttpServletResponse response) {

        int request_status = HttpServletResponse.SC_OK;


        try {
            String contextPath = ServletUtil.getContextPath(this);
            String servletName = "/" + this.getServletName();

            LogUtil.logServerAccessStart(request, "HyraxAccess", "HTTP-GET", Integer.toString(reqNumber.incrementAndGet()));

            if (!redirect(request, response)) {

                String name = Scrub.fileName(getName(request));

                log.debug("DocServlet - The client requested this: " + name);

                if(name == null){
                    throw new NotFound("Unable to locate: "+name);
                }

                File f = new File(name);

                if (f.exists()) {
                    log.debug("   Requested item exists.");
                    if (f.isFile()) {
                        log.debug("   It's a file...");


                        String suffix = null;
                        if (name.lastIndexOf("/") < name.lastIndexOf(".")) {
                            suffix = name.substring(name.lastIndexOf('.') + 1);
                        }

                        String mType = null;
                        if (suffix != null) {
                            mType = MimeTypes.getMimeType(suffix);
                            if (mType != null)
                                response.setContentType(mType);
                            log.debug("   MIME type: " + mType + "  ");
                        }

                        // Gah! Don't do a setStatus()!!!! Doing so breaks the HTTP status value for <error-page>
                        // declarations in the web.xml file.
                        //response.setStatus(HttpServletResponse.SC_OK);

                        log.debug("   Sending.");
                        if (mType != null)
                            response.setContentType(mType);


                        ServletOutputStream sos  = response.getOutputStream();


                        if (mType != null && mType.startsWith("text/")) {

                            String docString = readFileAsString(f);

                            log.debug("read file " + f.getAbsolutePath() + " into a String.");


                            docString = docString.replace("<CONTEXT_PATH />", contextPath);
                            docString = docString.replace("<SERVLET_NAME />", servletName);


                            sos.println(docString);
                        } else {

                            FileInputStream fis = new FileInputStream(f);

                            try {
                                byte buff[] = new byte[8192];
                                int rc;
                                boolean doneReading = false;
                                while (!doneReading) {
                                    rc = fis.read(buff);
                                    if (rc < 0) {
                                        doneReading = true;
                                    } else if (rc > 0) {
                                        sos.write(buff, 0, rc);
                                    }

                                }
                            } finally {

                                if (fis != null)
                                    fis.close();

                                if (sos != null)
                                    sos.flush();
                            }
                        }

                    } else if (f.isDirectory()) {
                        log.debug("   Requested directory exists.");
                        response.sendRedirect(Scrub.completeURL(request.getRequestURL().toString())+"/");


                    }
                    else {
                        String msg = "Unable to determine type of requested item: "+f.getName();
                        log.error("doGet() - {}",msg);
                        throw new NotFound(msg);
                    }


                } else {
                    throw new NotFound("Failed to locate resource: "+name);
                }

            } else {

                // throw  new OPeNDAPException(HttpServletResponse.SC_NOT_FOUND, "Failed to locate resource: "+name);
            }

        } catch (Throwable t) {
            try {
                request_status = OPeNDAPException.anyExceptionHandler(t, this,  response);
            } catch (Throwable t2) {
                try {
                    log.error("BAD THINGS HAPPENED!", t2);
                } catch (Throwable t3) {
                    // Never mind we can't manage anything sensible at this point....
                }
            }
        }
        finally {
            LogUtil.logServerAccessEnd(request_status, "HyraxAccess");
        }
    }


    public static String readFileAsString(File file) throws IOException {

        Scanner scanner = new Scanner(file,  HyraxStringEncoding.getCharset().name());
        StringBuilder stringBuilder = new StringBuilder();

        try {
            while (scanner.hasNextLine()) {
                stringBuilder.append(scanner.nextLine()).append("\n");
            }
        } finally {
            scanner.close();
        }
        return stringBuilder.toString();
    }

    public static String readFileAsString(String pathname) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        Scanner scanner = new Scanner(new File(pathname),  HyraxStringEncoding.getCharset().name());

        try {
            while (scanner.hasNextLine()) {
                stringBuilder.append(scanner.nextLine()).append("\n");
            }
        } finally {
            scanner.close();
        }
        return stringBuilder.toString();
    }


}
