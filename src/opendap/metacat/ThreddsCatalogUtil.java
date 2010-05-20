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

package opendap.metacat;

import org.jdom.input.SAXBuilder;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.filter.ElementFilter;
import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import java.io.PrintStream;
import java.io.IOException;
import java.io.StringReader;

import java.util.EmptyStackException;
import java.util.Enumeration;
import java.util.Stack;
import java.util.Vector;
import java.util.HashMap;
import java.util.Iterator;

import java.net.URL;
import java.net.MalformedURLException;

import opendap.metacat.ResponseCachePostgres;
import opendap.namespaces.THREDDS;
import opendap.namespaces.XLINK;

/**
 * Created by IntelliJ IDEA. User: ndp Date: Dec 1, 2009 Time: 7:37:51 AM To
 * change this template use File | Settings | File Templates.
 */
public class ThreddsCatalogUtil {

	private boolean writeToCache = false;
	private boolean readFromCache = false;
	
	private XMLOutputter xmlo = null;
	
	private ResponseCachePostgres TCCache = null;
	
	private Logger log = LoggerFactory.getLogger(ThreddsCatalogUtil.class);

	/**
	 * Constructor. This constructor gives the finest control over the caching
	 * operations performed. Because some sites use lots of catalogs, it might
	 * require lots of space to cache the entire catalog. However, it would
	 * still be nice to know about (or avoid) loops!
	 * 
	 * @param writeToCache
	 *            True if caching should be used
	 * @param namePrefix
	 *            The name of the cache files
	 */
	public ThreddsCatalogUtil(boolean  writeToCache, String namePrefix, boolean readFromCache) {
		xmlo = new XMLOutputter(Format.getPrettyFormat());

		if (writeToCache || readFromCache) {
			log.debug("Configuring caching in ThreddsCatalogUtil.");
			
			this.writeToCache = writeToCache;
			this.readFromCache = readFromCache;
			
			TCCache = new ResponseCachePostgres(namePrefix + "TC", "thredds_responses");
		}
	}
	
	/**
	 * Implements a modified depth-first traversal of a thredds catalog. The
	 * catalog is treated as a tree-like structure, but since it is really a
	 * directed graph, catalog URLs are cached and the same URL is neither
	 * returned or 'crawled' twice. The traversal is like a depth-first
	 * traversal, but instead of recurring all the way to the leaf nodes of the
	 * pseudo-tree, it descends as it returns nodes. The seed URL is used to
	 * initialize the stack of child nodes and then nextElement() both returns
	 * the top node and visits that node, pushing all of its URLs onto the
	 * stack. This limits the HTTP accesses to at most one-per nextElement call.
	 * 
	 * @note If constructed to use a cache (the default), catalog URLs will be
	 *       cached along with the catalog responses.
	 * 
	 * @author jimg
	 * 
	 */
	public class threddsCrawlerEnumeration implements Enumeration<String> {

		// This holds catalogs not yet seen by the user of the Enumeration
		private Stack<String> childURLs;

		threddsCrawlerEnumeration(String catalogURL) throws Exception {
			childURLs = new Stack<String>();
	    	childURLs.push(catalogURL);
		}
		
		private void recur(String catalogURL) {
			Vector<String> URLs = getCatalogRefURLs(catalogURL);
			if (URLs != null) {
				for (String URL : URLs) {
					if (writeToCache && !TCCache.isVisited(URL)) {
						TCCache.setLastVisited(URL, 1);
						childURLs.push(URL);
					}
					else {	
						childURLs.push(URL);
					}
				}
			}
		}
		
		@Override
		public boolean hasMoreElements() {
			return !childURLs.isEmpty();
		}

		@Override
		public String nextElement() {
			try {
				String child = childURLs.pop();
				recur(child);
				return child;
			}
			catch (EmptyStackException e) {
				return null;
			}
			
		}
	}

	public static enum SERVICE {

		ALL,

		ADDE {
			public String toString() {
				return "ADDE";
			}
		},
		DODS {
			public String toString() {
				return "DODS";
			}
		},
		OPeNDAP {
			public String toString() {
				return "OPeNDAP";
			}
		},
		OPeNDAP_G {
			public String toString() {
				return "OPeNDAP-G";
			}
		},
		HTTPServer {
			public String toString() {
				return "HTTPServer";
			}
		},
		GridFTP {
			public String toString() {
				return "GridFTP";
			}
		},
		File {
			public String toString() {
				return "File";
			}
		},
		LAS {
			public String toString() {
				return "LAS";
			}
		},
		WMS {
			public String toString() {
				return "WMS";
			}
		},
		WFS {
			public String toString() {
				return "WFS";
			}
		},
		WCS {
			public String toString() {
				return "WCS";
			}
		},
		WSDL {
			public String toString() {
				return "WSDL";
			}
		},
		WebForm {
			public String toString() {
				return "WebForm";
			}
		},
		Catalog {
			public String toString() {
				return "Catalog";
			}
		},
		QueryCapability {
			public String toString() {
				return "QueryCapability";
			}
		},
		Resolver {
			public String toString() {
				return "Resolver";
			}
		},
		Compound {
			public String toString() {
				return "Compound";
			}
		}
	}
	
	/**
	 * Return all of the DDX urls to data sources referenced by the given
	 * thredds catalog. The thredds catalog is referenced using a URL which
	 * either be accessed or read from a the cache, depending on how the
	 * instance of TCU was built.
	 * 
	 * @param catalogUrlString The THREDDS catalog to access
	 * @return A Vector of strings, each element a DDX URL.
	 */
	public Vector<String> getDDXUrls(String catalogUrlString) {

		Vector<String> datasetUrls = getDataAccessURLs(catalogUrlString,SERVICE.OPeNDAP);
		String url;

		for (int i = 0; i < datasetUrls.size(); i++) {
			url = datasetUrls.get(i);
			log.debug("Found DAP dataset URL: " + url);
			datasetUrls.set(i, url + ".ddx");
		}

		return datasetUrls;

	}

	/**
	 * Returns all of the THREDDS catalog URLs in the passed catalog element.
	 * 
	 * @param catalogUrlString
	 *            The URL from where the catalog was retrieved.
	 * @param catalog
	 *            The root element (the catalog element) in a THREDDS catalog
	 *            document.
	 * @return A vector of fully qualified URL Strings each of which points to a
	 *         THREDDS catalog document.
	 */
	private Vector<String> getCatalogRefURLs(String catalogUrlString, Element catalog) {

		Vector<String> catalogURLs = new Vector<String>();

		try {
			String href;
			String newCatalogURL;
			Element catalogRef;

			Iterator i = catalog.getDescendants(new ElementFilter("catalogRef", THREDDS.NS));
			while (i.hasNext()) {
				catalogRef = (Element) i.next();
				href = catalogRef.getAttributeValue("href", XLINK.NS);

				newCatalogURL = getCatalogURL(catalogUrlString, href);

				catalogURLs.add(newCatalogURL);
			}

		}
		catch (MalformedURLException e) {
			log.error("Malformed URL Exception: " + catalogUrlString + " msg: "
					+ e.getMessage());
		}

		return catalogURLs;

	}

    /**
     * Returns all of the THREDDS catalog URLs contained in the THREDDS catalog
     * located at the passed URL.
     *
     * @param catalogUrlString
     *            The URL from where the catalog was retrieved.
     * @return A vector of fully qualified URL Strings each of which points to a
     *         THREDDS catalog document. If the catalog returned by
     *         dereferencing <code>catalogUrlString</code> is 'bad' (e.g., the
     *         server returns a 404 response), then the Vector<Sting> result
     *         will be empty.
     */
    public Vector<String> getCatalogRefURLs(String catalogUrlString) {

        Vector<String> catalogURLs = new Vector<String>();

        Element catalog = getDocumentRoot(catalogUrlString);
        if (catalog != null)
            catalogURLs = getCatalogRefURLs(catalogUrlString, catalog);

        return catalogURLs;
    }

	/**
	 * Crawl a thredds catalog. This implements a modified depth-first traversal
	 * of the 'tree' of catalogs with 'topCatalog' as the root. In reality, a
	 * thredds catalog is a directed graph but the Enumeration returned is smart
	 * enough to avoid loops, so the resulting traversal has a tree-like feel.
	 * The algorithm is like a depth-first traversal but it has been modified so
	 * that HTTP accesses are limited to one per call to nextElement(). When
	 * nextElement is called, its value (call it 'C') is both returned and
	 * crawled so that subsequent calls return the children of 'C'. A real
	 * depth-first traversal would descend all the way to the leaf nodes -
	 * thredds catalogs that contain only references to data set and not other
	 * catalogs.
	 * 
	 * @note If this instance of TCU is built with readFromCache true, then 
	 * the Enumeration will read catalogs from the cache and not the network.
	 * If a referenced catalog is not in the cache, then an attempt will be
	 * made to read it from the network.
	 * 
	 * @param topCatalog
	 *            The THREDDS catalog that will serve as the root node
	 * @return An Enumeration of Strings that will visit all of the catalogs in
	 *         that tree
	 * @throws Exception Thrown if the cache cannot be configured
	 */
	public Enumeration<String> getCatalogEnumeration(String topCatalog) throws Exception {
		return new threddsCrawlerEnumeration(topCatalog);
	}

	/**
	 * Get access to all of the THREDDS Catalogs in the cache. Note that these
	 * URLs are returned in a random order, not the order in which they were
	 * added to the cache. Also note that they are not the actual URLs in the
	 * Postgres cache, but instead those URLs saved in the 'Visited' cache
	 * which is a separate collection of URLs maintained to eliminate looping
	 * during a crawl.
	 * 
	 * @return An Enumeration of the THREDDS Catalog URLs crawled so far.
	 */
	public Enumeration<String> getCachedCatalogEnumeration() {
		return TCCache.getLastVisitedKeys();
	}

	/**
	 * Return the THREDDS catalog associated with the given URL from the 
	 * local cache.
	 * 
	 * @param url Find this THREDDS catalog
	 * @return The THREDDS catalog
	 */
	public String getCachedCatalog(String url) {
		return TCCache.getCachedResponse(url);
	}
	
	/**
	 * Save the 'visited' cache. This is actually a ConcurrentHashMap and 
	 * holds the URL and the last time the URL was accesses\d.
	 * 
	 * @throws Exception
	 */
	public void saveCatalogCache() throws Exception {
		TCCache.saveState();
	}
		
	private String getServerUrlString(URL url) {

		String baseURL = null;

		String protocol = url.getProtocol();

		if (protocol.equalsIgnoreCase("file")) {
			log.debug("Protocol is FILE.");

		} else if (protocol.equalsIgnoreCase("http")) {
			log.debug("Protcol is HTTP.");

			String host = url.getHost();
			int port = url.getPort();

			baseURL = protocol + "://" + host;

			if (port != -1)
				baseURL += ":" + port;
		}

		log.debug("ServerURL: " + baseURL);

		return baseURL;

	}

	private String getServerUrlString(String url) throws MalformedURLException {

		URL u = new URL(url);

		return getServerUrlString(u);

	}

	private Vector<String> getDataAccessURLs(String catalogUrlString,
			Element catalog, SERVICE service) {

		Vector<String> serviceURLs = new Vector<String>();

		try {

			URL catalogURL = new URL(catalogUrlString);
			String serverURL = getServerUrlString(catalogURL);

			HashMap<String, Element> services = collectServices(catalog,
					service);

			Element dataset;
			Iterator i = catalog.getChildren(THREDDS.DATASET, THREDDS.NS).iterator();
			while (i.hasNext()) {
				dataset = (Element) i.next();
				collectDatasetAccessUrls(dataset, services, null, serverURL,
						serviceURLs);
			}

			log.debug("#### Accumulated " + serviceURLs.size()
					+ " access URLs.");
		}
		catch (Exception e) {
			log.error("Unable to load THREDDS catalog: " + catalogUrlString
					+ " msg: " + e.getMessage());
			e.printStackTrace();
		}

		return serviceURLs;
	}
	
	/**
	 * Returns a vector of data access URIs from The THREDDS catalog located at
	 * the URL contained in the passed parameter String
	 * <code>catalogUrlString</code>.
	 * 
	 * @param catalogUrlString
	 *            The THREDDS catalog to crawl.
	 * @param service
	 *            The SERVICE whose data access URLs you wish to get.
	 * @return The vector of data access URLs. If the catalog returned by
	 *         dereferencing <code>catalogUrlString</code> is 'bad' (e.g., the
	 *         server returns a 404 response), then the Vector<Sting> result
	 *         will be empty.
	 */
	
	public Vector<String> getDataAccessURLs(String catalogUrlString, SERVICE service) {

		Vector<String> serviceURLs = new Vector<String>();

		Element catalog = getDocumentRoot(catalogUrlString);
		if (catalog != null)
			serviceURLs = getDataAccessURLs(catalogUrlString, catalog, service);

		return serviceURLs;
	}

	/**
	 * Returns the root Element of the XML document located at the URL contained
	 * in the passed parameter <code>docUrlString</code>
	 * 
	 * @param docUrlString
	 *            The URL of the document to retrieve
	 * @return The Document
	 */
	private Element getDocumentRoot(String docUrlString) {

		Element docRoot = null;

		Document doc = getDocument(docUrlString);
		if (doc != null) {
			docRoot = doc.getRootElement();
		}
		return docRoot;

	}

	/**
	 * Returns the Document object for the XML document located at the URL
	 * contained in the passed parameter String <code>docUrlString</code>.
	 * 
	 * @note This is the point in the class where a response is (possibly)
	 * cached or read from a cache.
	 * 
	 * @param docUrlString
	 *            The URL of the document to retrieve.
	 * @return The Document
	 */
	private Document getDocument(String docUrlString) {

		Document doc = null;
		
		try {
			SAXBuilder sb = new SAXBuilder();
			if (readFromCache) {
				log.debug("Read " + docUrlString + " from the the cache.");
				
				String docString = TCCache.getCachedResponse(docUrlString);

				log.debug("Document:" + docString);
				
				doc = sb.build(new StringReader(docString));
			}
			else {
				URL docUrl = new URL(docUrlString);

				log.debug("Retrieving XML Document: " + docUrlString);

				doc = sb.build(docUrl);
				log.debug("Loaded XML Document: \n" + xmlo.outputString(doc));
				if (writeToCache) {
					TCCache.setCachedResponse(docUrlString, xmlo.outputString(doc));
				}
			}
		}
		catch (MalformedURLException e) {
			log.error("MalformedURLException (" + docUrlString + "): " + e.getMessage());
		}
		catch (IOException e) {
			log.error("IOException (" + docUrlString + "): " + e.getMessage());
		}
		catch (JDOMException e) {
			log.error("JDOMException (" + docUrlString + "): " + e.getMessage());
		}
		
		return doc;

	}

	private String getCatalogURL(String catalogUrlString, String href)
			throws MalformedURLException {

		if (href.startsWith("/")) {
			href = getServerUrlString(catalogUrlString) + href;
		} 
		else if (!href.startsWith("http://")) {
			String s = catalogUrlString.substring(0, catalogUrlString.lastIndexOf("/"));
			if (!s.endsWith("/"))
				s += "/";
			href = s + href;
		}
		
		log.debug("Built THREDDS catalog  URL:'" + href + "'");
		return href;
	}

	private HashMap<String, Element> collectServices(Element threddsCatalog) {
		HashMap<String, Element> services = new HashMap<String, Element>();

		Iterator i = threddsCatalog.getDescendants(new ElementFilter(
				THREDDS.SERVICE, THREDDS.NS));

		Element srvcElem;
		while (i.hasNext()) {
			srvcElem = (Element) i.next();
			services.put(srvcElem.getAttributeValue("name"), srvcElem);
		}

		return services;

	}

	private HashMap<String, Element> collectServices(Element threddsCatalog, SERVICE s) {

		HashMap<String, Element> services = collectServices(threddsCatalog);
		HashMap<String, Element> childSrvcs;

		// If they aren't asking for everything...
		if (s != SERVICE.ALL) {
			Element service;
			Vector<String> taggedForRemoval = new Vector<String>();

			for (String serviceName : services.keySet()) {
				service = services.get(serviceName);
				if (service.getAttributeValue("serviceType").equalsIgnoreCase(
						SERVICE.Compound.toString())) {
					childSrvcs = collectServices(service, s);
					if (childSrvcs.isEmpty()) {
						taggedForRemoval.add(serviceName);
					}
				} else if (!service.getAttributeValue("serviceType")
						.equalsIgnoreCase(s.toString())) {
					taggedForRemoval.add(serviceName);
				}

			}

			for (String serviceName : taggedForRemoval) {
				services.remove(serviceName);
			}
		}
		return services;
	}

	private void collectDatasetAccessUrls(Element dataset,
			HashMap<String, Element> services, String inheritedServiceName,
			String baseServerURL, Vector<String> datasetURLs) {

		String urlPath;
		String serviceName;
		String s;
		Element metadata, dset, access;

		log.debug("inheritedServiceName: " + inheritedServiceName);

		serviceName = dataset.getAttributeValue("serviceName");
		urlPath = dataset.getAttributeValue("urlPath");
		metadata = dataset.getChild("metadata", THREDDS.NS);

		if (metadata != null
				&& metadata.getAttributeValue("inherited").equalsIgnoreCase(
						"true")) {
			log.debug("Found inherited metadata");
			s = metadata.getChildText("serviceName", THREDDS.NS);
			if (s != null) {
				inheritedServiceName = s;
				log.debug("Updated inheritedServiceName to: "
						+ inheritedServiceName);
			}

		}

		if (urlPath != null) {
			log.debug("<dataset> has urlPath atttribute: " + urlPath);

			if (serviceName == null) {
				log.debug("<dataset> missing serviceName atttribute. Checking for child element...");
				serviceName = dataset.getChildText("serviceName", THREDDS.NS);
			}
			if (serviceName == null) {
				log.debug("<dataset> missing serviceName childElement. Checking for inherited serviceName...");
				serviceName = inheritedServiceName;
			}

			if (serviceName != null) {
				log.debug("<dataset> has serviceName: " + serviceName);
				datasetURLs.addAll(getAccessURLs(urlPath, serviceName,
						services, baseServerURL));

			}
		}

		Iterator i = dataset.getChildren("access", THREDDS.NS).iterator();
		while (i.hasNext()) {
			access = (Element) i.next();
			datasetURLs.addAll(getAccessURLs(access, services, baseServerURL));
		}

		i = dataset.getChildren(THREDDS.DATASET, THREDDS.NS).iterator();

		while (i.hasNext()) {
			dset = (Element) i.next();
			collectDatasetAccessUrls(dset, services, inheritedServiceName,
					baseServerURL, datasetURLs);
		}

	}

	private Vector<String> getAccessURLs(Element access,
			HashMap<String, Element> services, String baseServerURL) {
		String serviceName = access.getAttributeValue("serviceName");
		String urlPath = access.getAttributeValue("urlPath");

		return getAccessURLs(urlPath, serviceName, services, baseServerURL);

	}

	private Vector<String> getAccessURLs(String urlPath, String serviceName,
			HashMap<String, Element> services, String baseServerURL) {

		Vector<String> accessURLs = new Vector<String>();
		String access, base, serviceType, sname;
		Element srvc;

		Element service = services.get(serviceName);

		if (service != null) {
			serviceType = service.getAttributeValue("serviceType");

			if (serviceType.equalsIgnoreCase("Compound")) {
				Iterator i = service.getChildren("service", THREDDS.NS).iterator();
				while (i.hasNext()) {
					srvc = (Element) i.next();
					sname = srvc.getAttributeValue("name");
					Vector<String> v = getAccessURLs(urlPath, sname, services,
							baseServerURL);
					accessURLs.addAll(v);
				}

			} else {
				base = service.getAttributeValue("base");
				access = baseServerURL + base + urlPath;
				accessURLs.add(access);
				log.debug("####  Found access URL: " + access);

			}
		}

		return accessURLs;
	}
}