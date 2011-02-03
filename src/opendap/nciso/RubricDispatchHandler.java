package opendap.nciso;

import opendap.bes.BESDataSource;
import opendap.bes.BESError;
import opendap.bes.BesXmlAPI;
import opendap.bes.Version;
import opendap.coreServlet.*;
import opendap.xml.Transformer;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.transform.JDOMSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * DispatchHandler for Metadata Rubric responses from Hyrax
 */
public class RubricDispatchHandler implements opendap.coreServlet.DispatchHandler {



    private Logger log;
    private boolean initialized;

    private String _systemPath;

    private String rubricRequestPatternRegexString;
    private Pattern rubricRequestPattern;

    private Element _config;


    public RubricDispatchHandler(){
        log = LoggerFactory.getLogger(getClass());
    }




    public void init(HttpServlet servlet,Element config) throws Exception {

        if(initialized) return;

        _config = config;
        _systemPath = ServletUtil.getSystemPath(servlet,"");

        rubricRequestPatternRegexString = ".*\\.rubric";
        rubricRequestPattern = Pattern.compile(rubricRequestPatternRegexString, Pattern.CASE_INSENSITIVE);


        initialized = true;

    }

    public boolean requestCanBeHandled(HttpServletRequest request)
            throws Exception {
        return rubricDispatch(request, null, false);

    }


    public void handleRequest(HttpServletRequest request,
                              HttpServletResponse response)
            throws Exception {

       if(!rubricDispatch(request, response, true))
           log.debug("FileDispatch request failed inexplicably!");

    }


    /**
     * See the contract for this method in opendap.coreServlet.DispatchHandler
     * @param req The request for which we need to get a last modified date.
     * @return
     */
    public long getLastModified(HttpServletRequest req) {

        String name = ReqInfo.getRelativeUrl(req);

        log.debug("getLastModified(): Tomcat requesting getlastModified() for collection: " + name );


        try {
            DataSourceInfo dsi = new BESDataSource(name);
            log.debug("getLastModified(): Returning: " + new Date(dsi.lastModified()));

            return dsi.lastModified();
        }
        catch (Exception e) {
            log.debug("getLastModified(): Returning: -1");
            return -1;
        }


    }



    public void destroy() {
        log.info("Destroy complete.");

    }

    /**
     * Performs dispatching for rubric requests. ]
     *
     * @param request      The HttpServletRequest for this transaction.
     * @param response     The HttpServletResponse for this transaction
     * @param sendResponse If this is true a response will be sent. If it is
     *                     the request will only be evaluated to determine if a response can be
     *                     generated.
     * @return true if the request was serviced as a file request, false
     *         otherwise.
     * @throws Exception .
     */
    private boolean rubricDispatch(HttpServletRequest request,
                               HttpServletResponse response,
                               boolean sendResponse) throws Exception {


        String requestURL = request.getRequestURL().toString();

        boolean isrubricResponse = false;

        if(rubricRequestPattern.matcher(requestURL).matches())   {
            String relativeUrl = ReqInfo.getRelativeUrl(request);
            String dataSource = ReqInfo.getBesDataSourceID(relativeUrl);
            DataSourceInfo dsi = new BESDataSource(dataSource);

            if (dsi.sourceExists() && dsi.isDataset()) {
                isrubricResponse = true;
                if (sendResponse) {
                    sendrubricResponse(request,response);
                }
            }

        }

        return isrubricResponse;

    }


    /**
     * This method is responsible for sending rubric metadata responses to the client.
     * @param request
     * @param response
     * @throws Exception
     */
    private void sendrubricResponse(HttpServletRequest request,
                         HttpServletResponse response)
            throws Exception {



        // This first bit just collects a bunch of information about the request

        String relativeUrl = ReqInfo.getRelativeUrl(request);
        String dataSourceId = ReqInfo.getBesDataSourceID(relativeUrl);
        String constraintExpression = ReqInfo.getConstraintExpression(request);
        String requestSuffix = ReqInfo.getRequestSuffix(request);


        String xmlBase = request.getRequestURL().toString();
        int suffix_start = xmlBase.lastIndexOf("." + requestSuffix);
        xmlBase = xmlBase.substring(0, suffix_start);


        log.debug("Sending rubric Response() for dataset: " + dataSourceId);


        // Set up up the response header
        String accepts = request.getHeader("Accepts");

        if(accepts!=null && accepts.equalsIgnoreCase("application/rdf+xml"))
            response.setContentType("application/rdf+xml");
        else
            response.setContentType("text/html");

        Version.setOpendapMimeHeaders(request, response);
        response.setHeader("Content-Description", "text/html");


        ServletOutputStream os = response.getOutputStream();

        // Doing this insures that the DDX that
        String xdap_accept = "3.2";



        Document ddx = new Document();


        if(!BesXmlAPI.getDDXDocument(
                dataSourceId,
                constraintExpression,
                xdap_accept,
                xmlBase,
                ddx)){
            response.setHeader("Content-Description", "dap_error");

            BESError error = new BESError(ddx);
            error.sendErrorResponse(_systemPath,response);
        }
        else {

            ddx.getRootElement().setAttribute("dataset_id",dataSourceId);

            String currentDir = System.getProperty("user.dir");
            log.debug("Cached working directory: "+currentDir);


            String xslDir = _systemPath + "/nciso/xsl";


            log.debug("Changing working directory to "+ xslDir);
            System.setProperty("user.dir",xslDir);

            String xsltDocName = "OPeNDAPDDCount-HTML.xsl";


            // This Transformer class is an attemnpt at making the use of the saxon-9 API
            // a little simpler to use. It makes it easy to set input parameters for the stylesheet.
            // See the source code for opendap.xml.Transformer for more.
            Transformer transformer = new Transformer(xsltDocName);

            // Transform the BES  showCatalog response into a HTML page for the browser
            transformer.transform( new JDOMSource(ddx),os);




            os.flush();
            log.info("Sent Rubric version of DDX.");
            log.debug("Restoring working directory to "+ currentDir);
            System.setProperty("user.dir",currentDir);
        }




    }







}
