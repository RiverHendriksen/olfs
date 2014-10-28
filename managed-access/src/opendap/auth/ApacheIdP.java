/*
 * /////////////////////////////////////////////////////////////////////////////
 * // This file is part of the "Hyrax Data Server" project.
 * //
 * //
 * // Copyright (c) 2014 OPeNDAP, Inc.
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

package opendap.auth;

import org.jdom.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URL;

/**
 * Created by ndp on 10/7/14.
 */
public class ApacheIdP extends IdProvider {


    public static final String DEFAULT_ID="apache";
    /**
     * Default service point for the mod_shib Logout
     */
    public static final String DEFAULT_LOGOUT_LOCATION = "/Logout";

    /**
     * Default service point for the mod_shib Login
     */
    public static final String DEFAULT_LOGIN_LOCATION = "/Login";


    /**
     * Service point for the mod_shib Login
     */
    protected String _loginLocation;


    /**
     * Service point for the mod_shib Login
     */
    protected String _logoutLocation;


    private Logger _log;


    public ApacheIdP(){
        super();
        _log = LoggerFactory.getLogger(this.getClass());

        setId(DEFAULT_ID);
        setDescription("Apache Identity Provider");

        _loginLocation = DEFAULT_LOGIN_LOCATION;
        _logoutLocation = DEFAULT_LOGOUT_LOCATION;
    }



    @Override
    public void init(Element config) throws ConfigurationException {

        super.init(config);

        Element e = config.getChild("login");
        if(e!=null){
            setLoginLocation(config.getTextTrim());
        }


        e = config.getChild("logout");
        if(e!=null){
            setLogoutLocation(config.getTextTrim());
        }


    }




    public void setLogoutLocation(String logoutLocation){  _logoutLocation =  logoutLocation; }
    public String getLogoutLocation(){ return _logoutLocation; }
    public void setLoginLocation(String loginLocation){  _loginLocation =  loginLocation; }
    public String getLoginLocation(){ return _loginLocation; }


    /**
     * @param request
     * @param response
     * @return True if login is complete and user profile has been added to session object. False otherwise.
     * @throws Exception
     */
    @Override
    public boolean doLogin(HttpServletRequest request, HttpServletResponse response) throws Exception {

        /**
         * Redirect the user back to the their original requested resource.
         */
        HttpSession session = request.getSession(false);
        String redirectUrl = request.getContextPath();


        if (request.getRemoteUser()==null) {

            // Hmmm... The user has not logged in.

            StringBuilder msg = new StringBuilder();
            msg.append("User should be logged in (and is not) in order to be able to access this page. This is probably the ")
                    .append("result of a failed Security configuration element in Apache."
                    );

            _log.error("doLogin() - OUCH! {}",msg.toString());
            throw new ConfigurationException(msg.toString());

        }
        else {
            // We have a user - for now we just try to bounce them back to IdFilter.ORIGINAL_REQUEST_URL

            _log.info("doLogin() - User has uid: {}",request.getRemoteUser());

            // TODO How do I reliably know if the user has been shib authenticated?   Do we care?
            // TODO How can we stash our self w our custom logout method without breaking another IdP?

            // Do they have a session?

            if(session==null) {
                _log.error("doLogin() - No current session, creating new session.");

                //Oddly not, ok make them one..
                session = request.getSession(true);

            }
            else {
                _log.info("doLogin() - User has Session. id: {}",session.getId());

                // Let's inspect the attributes.
                String s = (String) session.getAttribute("eppn");
                _log.info("doLogin() - HttpSession Attribute eppn: {}",s);

                s = (String) request.getAttribute("eppn");
                _log.info("doLogin() - HttpRequest Attribute eppn: {}",s);

                s = (String) request.getAttribute("affiliation");
                _log.info("doLogin() - HttpRequest Attribute affiliation: {}",s);

                s = (String) request.getAttribute("unscoped-affiliation");
                _log.info("doLogin() - HttpRequest Attribute unscoped-affiliation: {}",s);

                s = (String) request.getAttribute("targeted-id");
                _log.info("doLogin() - HttpRequest Attribute targeted-id: {}",s);



                _log.info(opendap.coreServlet.ServletUtil.probeRequest(null,request));



            }
            // We need to capture the original redirect url if there is one,
            // and then invalidate the session and then start a new one before we send them
            // off to authenticate.
            redirectUrl = (String) session.getAttribute(IdFilter.ORIGINAL_REQUEST_URL);

            if(redirectUrl==null){
                // Unset? Punt...
                redirectUrl = request.getContextPath();
            }

        }

        _log.info("doLogin(): redirecting to {}",redirectUrl);

        response.sendRedirect(redirectUrl);


        return true;
    }


    /**
     * Logs a user out.
     * This method simply terminates the local session and redirects the user back
     * to the home page.
     */
    public void doLogout(HttpServletRequest request, HttpServletResponse response)
	        throws IOException
    {
        HttpSession session = request.getSession(false);
        if( session != null )
        {
            session.invalidate();
        }

        response.sendRedirect(getLogoutLocation());
    }

}