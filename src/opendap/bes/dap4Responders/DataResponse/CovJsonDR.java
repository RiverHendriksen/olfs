/*
 * /////////////////////////////////////////////////////////////////////////////
 * // This file is part of the "Hyrax Data Server" project.
 * //
 * //
 * // Copyright (c) 2018 OPeNDAP, Inc.
 * // Author: Corey Hemphill <hemphilc@oregonstate.edu>
 * // Author: River Hendriksen <hendriri@oregonstate.edu>
 * // Author: Riley Rimer <rrimer@oregonstate.edu>
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

package opendap.bes.dap4Responders.DataResponse;

import opendap.bes.Version;
import opendap.bes.dap2Responders.BesApi;
import opendap.bes.dap4Responders.Dap4Responder;
import opendap.bes.dap4Responders.MediaType;
import opendap.coreServlet.OPeNDAPException;
import opendap.coreServlet.ReqInfo;
import opendap.coreServlet.RequestCache;
import opendap.coreServlet.Scrub;
import opendap.dap.User;
import opendap.dap4.QueryParameters;
import opendap.http.mediaTypes.Json;
import opendap.http.mediaTypes.CovJson;
import org.slf4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

/**
 * Created by IntelliJ IDEA.
 * User: ndp
 * Date: 1/16/13
 * Time: 4:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class CovJsonDR extends Dap4Responder {


    private Logger log;
    private static String defaultRequestSuffix = ".covjson";



    public CovJsonDR(String sysPath, BesApi besApi, boolean addFileoutTypeSuffixToDownloadFilename) {
        this(sysPath, null, defaultRequestSuffix, besApi, addFileoutTypeSuffixToDownloadFilename);
    }

    public CovJsonDR(String sysPath, String pathPrefix, BesApi besApi, boolean addFileoutTypeSuffixToDownloadFilename) {
        this(sysPath, pathPrefix, defaultRequestSuffix, besApi, addFileoutTypeSuffixToDownloadFilename);
    }

    public CovJsonDR(String sysPath, String pathPrefix, String requestSuffixRegex, BesApi besApi, boolean addFileoutTypeSuffixToDownloadFilename) {
        super(sysPath, pathPrefix, requestSuffixRegex, besApi);
        log = org.slf4j.LoggerFactory.getLogger(this.getClass());
        /*
        * NOTE
        * some of this needs to be updated eventually
        * -Riley
        */
        addTypeSuffixToDownloadFilename(addFileoutTypeSuffixToDownloadFilename);//
        setServiceRoleId("http://services.opendap.org/dap4/data/covjson");//
        setServiceTitle("COVJSON Data Response");//
        setServiceDescription("COVJSON representation of the DAP4 Data Response object.");//
        setServiceDescriptionLink("http://docs.opendap.org/index.php/DAP4:_Specification_Volume_2#DAP4:_Data_Response");//

        setNormativeMediaType(new CovJson(getRequestSuffix()));//

        log.debug("Using RequestSuffix:              '{}'", getRequestSuffix());
        log.debug("Using CombinedRequestSuffixRegex: '{}'", getCombinedRequestSuffixRegex());

    }


    public boolean isDataResponder(){ return true; }
    public boolean isMetadataResponder(){ return false; }





    public void sendNormativeRepresentation(HttpServletRequest request, HttpServletResponse response) throws Exception {

        String requestedResourceId = ReqInfo.getLocalUrl(request);
        QueryParameters qp = new QueryParameters(request);

        String resourceID = getResourceId(requestedResourceId, false);


        BesApi besApi = getBesApi();

        log.debug("Sending {} for dataset: {}",getServiceTitle(),resourceID);

        response.setHeader("Content-Disposition", " attachment; filename=\"" +getDownloadFileName(resourceID)+"\"");

        Version.setOpendapMimeHeaders(request, response, besApi);

        MediaType responseMediaType =  getNormativeMediaType();

        // Stash the Media type in case there's an error. That way the error handler will know how to encode the error.
        RequestCache.put(OPeNDAPException.ERROR_RESPONSE_MEDIA_TYPE_KEY, responseMediaType);

        response.setContentType(responseMediaType.getMimeType());

        Version.setOpendapMimeHeaders(request, response, besApi);

        response.setHeader("Content-Description", getNormativeMediaType().getMimeType());


        User user = new User(request);


        OutputStream os = response.getOutputStream();

        besApi.writeDap4DataAsCovJson(
            resourceID,
            qp,
            user.getMaxResponseSize(),
            os);



        os.flush();
        log.debug("Sent {}",getServiceTitle());



    }



}
